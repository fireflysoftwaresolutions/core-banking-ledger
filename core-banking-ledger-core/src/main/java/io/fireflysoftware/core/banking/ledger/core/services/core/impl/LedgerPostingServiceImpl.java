package io.fireflysoftware.core.banking.ledger.core.services.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.fireflysoftware.core.banking.ledger.core.mappers.core.TransactionMapper;
import io.fireflysoftware.core.banking.ledger.core.mappers.leg.TransactionLegMapper;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingIntent;
import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingHeaderDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingLegDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingRequestDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.core.Transaction;
import io.fireflysoftware.core.banking.ledger.models.entities.core.TransactionStatusHistory;
import io.fireflysoftware.core.banking.ledger.models.entities.funds.LedgerPostingClaim;
import io.fireflysoftware.core.banking.ledger.models.entities.leg.TransactionLeg;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.TransactionRepository;
import io.fireflysoftware.core.banking.ledger.models.repositories.core.TransactionStatusHistoryRepository;
import io.fireflysoftware.core.banking.ledger.models.repositories.funds.FundsRepositories;
import io.fireflysoftware.core.banking.ledger.models.repositories.leg.TransactionLegRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HexFormat;
import java.util.UUID;

@Service("ledgerPostingService")
@RequiredArgsConstructor
public class LedgerPostingServiceImpl implements LedgerPostingService {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final FundsRepositories funds;
    private final TransactionRepository transactions;
    private final TransactionLegRepository legs;
    private final TransactionStatusHistoryRepository history;
    private final TransactionMapper transactionMapper;
    private final TransactionLegMapper legMapper;

    @Transactional
    public Mono<LedgerPostingDTO> create(LedgerPostingRequestDTO request) {
        return createAggregate(request, null);
    }

    private Mono<LedgerPostingDTO> createAggregate(
            LedgerPostingRequestDTO input, UUID reversalOf) {
        return Mono.defer(
                () -> {
                    var request = LedgerPostingIntent.capture(input).request();
                    validate(request);
                    String fingerprint = fingerprint(request, reversalOf);
                    return requireFundsInput(request)
                            .then(funds.nativeOps().postingGate(request.getIdempotencyKey()))
                            .then(funds.postings().findById(request.getIdempotencyKey()))
                            .flatMap(
                                    claim -> {
                                        if (!fingerprint.equals(claim.getInputFingerprint())) {
                                            return Mono.error(
                                                    new ResponseStatusException(
                                                            HttpStatus.CONFLICT,
                                                            "Idempotency key already belongs to"
                                                                + " another posting input"));
                                        }
                                        if (claim.getTransactionId() == null) {
                                            return Mono.error(
                                                    new IllegalStateException(
                                                            "Committed posting claim has no"
                                                                + " transaction"));
                                        }
                                        return load(claim.getTransactionId(), true);
                                    })
                            .switchIfEmpty(
                                    Mono.defer(
                                            () ->
                                                    funds.writes()
                                                            .insertLedgerPostingClaim(
                                                                    LedgerPostingClaim.builder()
                                                                            .idempotencyKey(
                                                                                    request
                                                                                            .getIdempotencyKey())
                                                                            .inputFingerprint(
                                                                                    fingerprint)
                                                                            .build())
                                                            .onErrorMap(
                                                                    DuplicateKeyException.class,
                                                                    error ->
                                                                            new ResponseStatusException(
                                                                                    HttpStatus
                                                                                            .CONFLICT,
                                                                                    "Posting key"
                                                                                        + " was claimed"
                                                                                        + " concurrently",
                                                                                    error))
                                                            .then(persist(request, reversalOf))));
                });
    }

    @Transactional
    public Mono<LedgerPostingDTO> confirm(UUID id) {
        return transition(id, TransactionStatusEnum.POSTED);
    }

    @Transactional
    public Mono<LedgerPostingDTO> release(UUID id) {
        return transition(id, TransactionStatusEnum.REVERSED);
    }

    private Mono<LedgerPostingDTO> transition(UUID id, TransactionStatusEnum target) {
        return lockedPosting(id)
                .flatMap(
                        transaction -> {
                            if (transaction.getTransactionStatus() == target) {
                                return load(id, true);
                            }
                            if (target == TransactionStatusEnum.REVERSED
                                    && transaction.getTransactionStatus()
                                            == TransactionStatusEnum.POSTED) {
                                return transactions
                                        .findManagedPostedReversal(id)
                                        .flatMap(reversal -> load(id, true))
                                        .switchIfEmpty(
                                                Mono.error(
                                                        new ResponseStatusException(
                                                                HttpStatus.CONFLICT,
                                                                "Posted money requires an"
                                                                    + " authoritative linked"
                                                                    + " reversal")));
                            }
                            if (transaction.getTransactionStatus()
                                    != TransactionStatusEnum.PENDING) {
                                return Mono.error(
                                        new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "Only pending postings can be confirmed or"
                                                    + " released"));
                            }
                            transaction.setTransactionStatus(target);
                            TransactionStatusHistory event = new TransactionStatusHistory();
                            event.setTransactionId(id);
                            event.setStatusCode(target);
                            event.setStatusStartDatetime(LocalDateTime.now());
                            event.setReason(
                                    target == TransactionStatusEnum.POSTED
                                            ? "Posting confirmed"
                                            : "Pending posting released");
                            event.setRegulatedReportingFlag(false);
                            return transactions
                                    .save(transaction)
                                    .then(history.save(event))
                                    .then(load(id, false));
                        });
    }

    @Transactional
    public Mono<LedgerPostingDTO> reverse(UUID originalId, String key) {
        return lockedPosting(originalId)
                .flatMap(
                        original -> {
                            if (original.getTransactionStatus() != TransactionStatusEnum.POSTED
                                    || "REVERSAL".equals(original.getRelationType())) {
                                return Mono.error(
                                        new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "A reversal requires an original posted"
                                                    + " aggregate"));
                            }
                            return transactions
                                    .findManagedPostedReversal(originalId)
                                    .flatMap(
                                            existing -> {
                                                if (!existing.getExternalReference().equals(key)) {
                                                    return Mono.error(
                                                            new ResponseStatusException(
                                                                    HttpStatus.CONFLICT,
                                                                    "The original posting already"
                                                                        + " has a reversal"));
                                                }
                                                return load(existing.getTransactionId(), true);
                                            })
                                    .switchIfEmpty(
                                            Mono.defer(
                                                    () ->
                                                            legs.findAllByTransactionIdOrderByTransactionLegId(
                                                                            originalId)
                                                                    .collectList()
                                                                    .flatMap(
                                                                            persisted -> {
                                                                                LedgerPostingHeaderDTO
                                                                                        header =
                                                                                                new LedgerPostingHeaderDTO();
                                                                                header.setAccountId(
                                                                                        original
                                                                                                .getAccountId());
                                                                                header
                                                                                        .setAccountSpaceId(
                                                                                                original
                                                                                                        .getAccountSpaceId());
                                                                                header
                                                                                        .setTransactionType(
                                                                                                original
                                                                                                        .getTransactionType());
                                                                                header
                                                                                        .setTransactionStatus(
                                                                                                LedgerPostingHeaderDTO
                                                                                                        .InitialStatus
                                                                                                        .POSTED);
                                                                                header
                                                                                        .setTotalAmount(
                                                                                                original
                                                                                                        .getTotalAmount());
                                                                                header.setCurrency(
                                                                                        original
                                                                                                .getCurrency());
                                                                                LedgerPostingRequestDTO
                                                                                        request =
                                                                                                new LedgerPostingRequestDTO();
                                                                                request
                                                                                        .setIdempotencyKey(
                                                                                                key);
                                                                                request
                                                                                        .setTransaction(
                                                                                                header);
                                                                                request.setLegs(
                                                                                        persisted
                                                                                                .stream()
                                                                                                .map(
                                                                                                        leg -> {
                                                                                                            LedgerPostingLegDTO
                                                                                                                    opposite =
                                                                                                                            new LedgerPostingLegDTO();
                                                                                                            opposite
                                                                                                                    .setAccountId(
                                                                                                                            leg
                                                                                                                                    .getAccountId());
                                                                                                            opposite
                                                                                                                    .setAccountSpaceId(
                                                                                                                            leg
                                                                                                                                    .getAccountSpaceId());
                                                                                                            opposite
                                                                                                                    .setLegType(
                                                                                                                            "DEBIT"
                                                                                                                                            .equals(
                                                                                                                                                    leg
                                                                                                                                                            .getLegType())
                                                                                                                                    ? "CREDIT"
                                                                                                                                    : "DEBIT");
                                                                                                            opposite
                                                                                                                    .setAmount(
                                                                                                                            leg
                                                                                                                                    .getAmount());
                                                                                                            opposite
                                                                                                                    .setCurrency(
                                                                                                                            leg
                                                                                                                                    .getCurrency());
                                                                                                            return opposite;
                                                                                                        })
                                                                                                .toList());
                                                                                return createAggregate(
                                                                                        request,
                                                                                        originalId);
                                                                            })));
                        });
    }

    private Mono<Void> requireFundsInput(LedgerPostingRequestDTO request) {
        var accounts = new java.util.ArrayList<UUID>();
        accounts.add(request.getTransaction().getAccountId());
        request.getLegs().forEach(leg -> accounts.add(leg.getAccountId()));
        return Flux.fromIterable(
                        accounts.stream()
                                .distinct()
                                .sorted(java.util.Comparator.comparing(UUID::toString))
                                .toList())
                .concatMap(account -> funds.nativeOps().accountGate(account.toString()))
                .then(
                        funds.nativeOps()
                                .postingAccountsAllowed(
                                        accounts.toArray(UUID[]::new),
                                        request.getIdempotencyKey().startsWith("fnd:")))
                .filter(Boolean::booleanValue)
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "MANAGED_FUNDS_COMMAND_REQUIRED")))
                .then();
    }

    private Mono<Transaction> lockedPosting(UUID id) {
        return funds.nativeOps()
                .postingAllowed(id)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "MANAGED_FUNDS_COMMAND_REQUIRED")))
                .then(transactions.findByIdForUpdate(id))
                .switchIfEmpty(
                        Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Ledger posting was not found")))
                .flatMap(
                        transaction ->
                                transaction.isManagedPosting()
                                        ? Mono.just(transaction)
                                        : Mono.error(
                                                new ResponseStatusException(
                                                        HttpStatus.CONFLICT,
                                                        "The transaction is not a managed"
                                                            + " posting")));
    }

    private Mono<LedgerPostingDTO> persist(LedgerPostingRequestDTO request, UUID reversalOf) {
        LedgerPostingHeaderDTO header = request.getTransaction();
        Transaction transaction = new Transaction();
        transaction.setAccountId(header.getAccountId());
        transaction.setAccountSpaceId(header.getAccountSpaceId());
        transaction.setTransactionType(header.getTransactionType());
        transaction.setTransactionStatus(
                TransactionStatusEnum.valueOf(header.getTransactionStatus().name()));
        transaction.setTotalAmount(header.getTotalAmount());
        transaction.setCurrency(header.getCurrency());
        transaction.setDescription(header.getDescription());
        transaction.setRequestId(header.getRequestId());
        transaction.setTransactionDate(header.getTransactionDate());
        transaction.setValueDate(header.getValueDate());
        transaction.setBookingDate(header.getBookingDate());
        transaction.setRelatedTransactionId(reversalOf);
        if (reversalOf != null) transaction.setRelationType("REVERSAL");
        transaction.setManagedPosting(true);
        transaction.setExternalReference(request.getIdempotencyKey());
        LocalDateTime now = LocalDateTime.now();
        if (transaction.getTransactionDate() == null) transaction.setTransactionDate(now);
        if (transaction.getValueDate() == null)
            transaction.setValueDate(transaction.getTransactionDate());
        if (transaction.getBookingDate() == null)
            transaction.setBookingDate(transaction.getTransactionDate());
        return transactions
                .save(transaction)
                .flatMap(
                        saved -> {
                            TransactionStatusHistory initial = new TransactionStatusHistory();
                            initial.setTransactionId(saved.getTransactionId());
                            initial.setStatusCode(saved.getTransactionStatus());
                            initial.setStatusStartDatetime(LocalDateTime.now());
                            initial.setReason("Atomic ledger posting created");
                            initial.setRegulatedReportingFlag(false);
                            return history.save(initial)
                                    .thenMany(
                                            Flux.fromIterable(request.getLegs())
                                                    .concatMap(
                                                            input -> {
                                                                TransactionLeg leg =
                                                                        new TransactionLeg();
                                                                leg.setTransactionId(
                                                                        saved.getTransactionId());
                                                                leg.setAccountId(
                                                                        input.getAccountId());
                                                                leg.setAccountSpaceId(
                                                                        input.getAccountSpaceId());
                                                                leg.setLegType(input.getLegType());
                                                                leg.setAmount(input.getAmount());
                                                                leg.setCurrency(
                                                                        input.getCurrency());
                                                                leg.setDescription(
                                                                        input.getDescription());
                                                                leg.setValueDate(
                                                                        saved.getValueDate());
                                                                leg.setBookingDate(
                                                                        saved.getBookingDate());
                                                                return legs.save(leg);
                                                            }))
                                    .then(
                                            funds.writes()
                                                    .completePosting(
                                                            request.getIdempotencyKey(),
                                                            saved.getTransactionId())
                                                    .filter(count -> count == 1)
                                                    .switchIfEmpty(
                                                            Mono.error(
                                                                    new IllegalStateException(
                                                                            "Posting claim"
                                                                                + " completion was"
                                                                                + " not singular"))))
                                    .then(load(saved.getTransactionId(), false));
                        });
    }

    private Mono<LedgerPostingDTO> load(UUID id, boolean replayed) {
        return transactions
                .findById(id)
                .switchIfEmpty(
                        Mono.error(new IllegalStateException("Posting transaction is missing")))
                .flatMap(
                        transaction ->
                                legs.findAllByTransactionIdOrderByTransactionLegId(id)
                                        .map(legMapper::toDTO)
                                        .collectList()
                                        .map(
                                                details ->
                                                        new LedgerPostingDTO(
                                                                transactionMapper.toDTO(
                                                                        transaction),
                                                                details,
                                                                replayed)));
    }

    private static void validate(LedgerPostingRequestDTO request) {
        if (request == null
                || request.getIdempotencyKey() == null
                || request.getIdempotencyKey().isBlank()
                || request.getIdempotencyKey().length() > 100
                || request.getTransaction() == null
                || request.getLegs() == null
                || request.getLegs().size() < 2
                || request.getLegs().size() > 1000) {
            throw invalid(
                    "A posting requires an idempotency key, transaction and two to 1000 legs");
        }
        var header = request.getTransaction();
        if (header.getAccountId() == null
                || header.getTransactionType() == null
                || header.getCurrency() == null
                || header.getTransactionStatus() == null
                || (header.getExternalReference() != null
                        && !request.getIdempotencyKey().equals(header.getExternalReference()))) {
            throw invalid("Posting header identity, currency and initial status are required");
        }
        try {
            Currency.getInstance(header.getCurrency());
        } catch (IllegalArgumentException error) {
            throw invalid("Posting currency must be an ISO currency code");
        }
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (var leg : request.getLegs()) {
            if (leg == null
                    || leg.getAccountId() == null
                    || !header.getCurrency().equals(leg.getCurrency())
                    || !("DEBIT".equals(leg.getLegType()) || "CREDIT".equals(leg.getLegType()))) {
                throw invalid(
                        "Posting legs require account identity, currency and debit or credit side");
            }
            checkAmount(leg.getAmount());
            if ("DEBIT".equals(leg.getLegType())) {
                debit = debit.add(leg.getAmount());
            } else {
                credit = credit.add(leg.getAmount());
            }
        }
        checkAmount(header.getTotalAmount());
        if (debit.compareTo(credit) != 0 || debit.compareTo(header.getTotalAmount()) != 0) {
            throw invalid("Posting debit, credit and header totals must balance");
        }
        checkAmount(debit);
    }

    static void checkAmount(BigDecimal amount) {
        if (amount == null
                || amount.signum() <= 0
                || (long) amount.precision() - amount.scale() > 20
                || amount.stripTrailingZeros().scale() > 18) {
            throw invalid(
                    "Posting amounts must be positive and fit NUMERIC(38,18) without rounding");
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static String fingerprint(LedgerPostingRequestDTO request, UUID reversalOf) {
        try {
            ObjectNode input = JSON.valueToTree(request);
            input.put("reversalOf", reversalOf == null ? null : reversalOf.toString());
            JsonNode canonical = normalize(input);
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static JsonNode normalize(JsonNode node) {
        if (node.isNumber()) {
            return DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        }
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            var names = new java.util.ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, normalize(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            node.forEach(value -> result.add(normalize(value)));
            return result;
        }
        return node;
    }

    private record Claim(String fingerprint, UUID transactionId) {}
}
