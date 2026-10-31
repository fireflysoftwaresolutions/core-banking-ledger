package io.fireflysoftware.core.banking.ledger.core.services.core;

import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingHeaderDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingLegDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingRequestDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Immutable call-local intent; legacy collaborators receive independent DTO projections. */
public final class LedgerPostingIntent {
    private final boolean present;
    private final String key;
    private final Header header;
    private final List<Leg> legs;

    private LedgerPostingIntent(boolean present, String key, Header header, List<Leg> legs) {
        this.present = present;
        this.key = key;
        this.header = header;
        this.legs = legs;
    }

    public static LedgerPostingIntent capture(LedgerPostingRequestDTO source) {
        if (source == null) return new LedgerPostingIntent(false, null, null, null);
        String key = source.getIdempotencyKey();
        var originalHeader = source.getTransaction();
        Header header = originalHeader == null ? null : Header.capture(originalHeader);
        var originalLegs = source.getLegs();
        List<Leg> legs = null;
        if (originalLegs != null) {
            if (originalLegs.size() > 1000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A posting requires an idempotency key, transaction and two to 1000 legs");
            }
            var captured = new ArrayList<Leg>();
            for (var leg : originalLegs) {
                if (captured.size() == 1000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A posting requires an idempotency key, transaction and two to 1000 legs");
                }
                captured.add(leg == null ? null : Leg.capture(leg));
            }
            legs = Collections.unmodifiableList(captured);
        }
        return new LedgerPostingIntent(true, key, header, legs);
    }

    public LedgerPostingRequestDTO request() {
        if (!present) return null;
        var projection = new LedgerPostingRequestDTO();
        projection.setIdempotencyKey(key);
        projection.setTransaction(header == null ? null : header.project());
        if (legs != null) {
            var values = new ArrayList<LedgerPostingLegDTO>();
            for (var leg : legs) values.add(leg == null ? null : leg.project());
            projection.setLegs(values);
        }
        return projection;
    }

    private record Header(UUID account, UUID space, TransactionTypeEnum type,
            LedgerPostingHeaderDTO.InitialStatus status, BigDecimal amount, String currency,
            LocalDateTime transactionDate, LocalDateTime valueDate, LocalDateTime bookingDate,
            String externalReference, String requestId, String description) {
        static Header capture(LedgerPostingHeaderDTO source) {
            return new Header(source.getAccountId(), source.getAccountSpaceId(),
                    source.getTransactionType(), source.getTransactionStatus(), source.getTotalAmount(),
                    source.getCurrency(), source.getTransactionDate(), source.getValueDate(),
                    source.getBookingDate(), source.getExternalReference(), source.getRequestId(),
                    source.getDescription());
        }
        LedgerPostingHeaderDTO project() {
            var value = new LedgerPostingHeaderDTO();
            value.setAccountId(account); value.setAccountSpaceId(space);
            value.setTransactionType(type); value.setTransactionStatus(status);
            value.setTotalAmount(amount); value.setCurrency(currency);
            value.setTransactionDate(transactionDate); value.setValueDate(valueDate);
            value.setBookingDate(bookingDate); value.setExternalReference(externalReference);
            value.setRequestId(requestId); value.setDescription(description);
            return value;
        }
    }

    private record Leg(UUID account, UUID space, String side, BigDecimal amount,
            String currency, String description) {
        static Leg capture(LedgerPostingLegDTO source) {
            return new Leg(source.getAccountId(), source.getAccountSpaceId(), source.getLegType(),
                    source.getAmount(), source.getCurrency(), source.getDescription());
        }
        LedgerPostingLegDTO project() {
            var value = new LedgerPostingLegDTO();
            value.setAccountId(account); value.setAccountSpaceId(space); value.setLegType(side);
            value.setAmount(amount); value.setCurrency(currency); value.setDescription(description);
            return value;
        }
    }
}
