package io.fireflysoftware.core.banking.ledger.interfaces.dtos.core;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class LedgerPostingHeaderDTO {
    public enum InitialStatus { PENDING, POSTED }

    @NotNull
    private UUID accountId;
    private UUID accountSpaceId;
    @NotNull
    private TransactionTypeEnum transactionType;
    @NotNull
    private InitialStatus transactionStatus;
    @NotNull
    private BigDecimal totalAmount;
    @NotNull
    private String currency;
    @Schema(description = "Optional. The core assigns the transaction time when omitted.")
    private LocalDateTime transactionDate;
    @Schema(description = "Optional. Defaults to the assigned transaction time.")
    private LocalDateTime valueDate;
    @Schema(description = "Optional. Defaults to the assigned transaction time.")
    private LocalDateTime bookingDate;
    private String externalReference;
    private String requestId;
    private String description;
}
