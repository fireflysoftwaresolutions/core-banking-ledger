package io.fireflysoftware.core.banking.ledger.web.controllers.funds;

import io.fireflysoftware.core.banking.ledger.core.services.funds.FundsReceiptService;
import io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds.FundsReceiptContracts.EvidenceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "LedgerFunds")
@SecurityRequirement(name = "fundsBearer")
@RestController
@RequestMapping("/api/v1/tenants/{tenant}/funds/reservations/{id}/monetary-receipts")
public final class FundsReceiptController {
    private final FundsReceiptService receipts;
    public FundsReceiptController(FundsReceiptService receipts) { this.receipts = receipts; }
    @Operation(operationId = "getFundsMonetaryReceipts")
    @GetMapping
    public Mono<EvidenceView> read(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenant, @PathVariable UUID id) {
        return receipts.read(FundsController.principal(jwt), tenant, id);
    }
}
