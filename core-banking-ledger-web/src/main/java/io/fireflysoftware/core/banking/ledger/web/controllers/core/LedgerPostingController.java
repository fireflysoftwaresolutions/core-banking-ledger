package io.fireflysoftware.core.banking.ledger.web.controllers.core;

import io.fireflysoftware.core.banking.ledger.core.services.core.LedgerPostingService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerPostingRequestDTO;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.core.LedgerReversalRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger-postings")
@Tag(name = "Ledger Postings", description = "Atomic balanced ledger aggregates and durable input claims")
@RequiredArgsConstructor
public class LedgerPostingController {
    private final LedgerPostingService service;

    @PostMapping
    @Operation(operationId = "createLedgerPosting", summary = "Create or replay an atomic balanced ledger posting")
    public Mono<ResponseEntity<LedgerPostingDTO>> createLedgerPosting(@RequestBody LedgerPostingRequestDTO request) {
        return service.create(request).map(posting -> ResponseEntity.status(posting.isReplayed() ? 200 : 201).body(posting));
    }

    @PostMapping("/{transactionId}/confirm")
    @Operation(operationId = "confirmLedgerPosting", summary = "Confirm a pending posting or replay its confirmation")
    public Mono<LedgerPostingDTO> confirmLedgerPosting(@PathVariable UUID transactionId) {
        return service.confirm(transactionId);
    }

    @PostMapping("/{transactionId}/release")
    @Operation(operationId = "releaseLedgerPosting", summary = "Release a pending posting without erasing posted money")
    public Mono<LedgerPostingDTO> releaseLedgerPosting(@PathVariable UUID transactionId) {
        return service.release(transactionId);
    }

    @PostMapping("/{transactionId}/reversal")
    @Operation(operationId = "reverseLedgerPosting", summary = "Create or replay the original posting's persisted opposite legs")
    public Mono<ResponseEntity<LedgerPostingDTO>> reverseLedgerPosting(@PathVariable UUID transactionId,
                                                                      @RequestBody LedgerReversalRequestDTO request) {
        return service.reverse(transactionId, request.getIdempotencyKey())
                .map(posting -> ResponseEntity.status(posting.isReplayed() ? 200 : 201).body(posting));
    }
}
