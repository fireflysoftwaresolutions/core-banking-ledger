/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.crypto;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.asset.AssetTypeEnum;
import org.fireflyframework.annotations.ValidAmount;
import org.fireflyframework.annotations.ValidCurrencyCode;
import org.fireflyframework.annotations.ValidDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.UUID;
/**
 * Base Data Transfer Object for crypto transactions.
 * Contains common fields for all types of crypto transactions.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseCryptoTransactionDTO {
    
    /**
     * The ID of the account
     */
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    /**
     * The ID of the account space
     */
    private UUID accountSpaceId;

    /**
     * The ID of the crypto asset
     */
    @NotNull(message = "Crypto asset ID is required")
    private UUID cryptoAssetId;

    /**
     * The symbol of the crypto asset (e.g., BTC, ETH)
     */
    @NotBlank(message = "Asset symbol is required")
    @Size(max = 10, message = "Asset symbol cannot exceed 10 characters")
    private String assetSymbol;

    /**
     * The type of asset (CRYPTOCURRENCY, TOKEN_SECURITY, etc.)
     */
    @NotNull(message = "Asset type is required")
    private AssetTypeEnum assetType;

    /**
     * The amount of the transaction
     */
    @NotNull(message = "Amount is required")
    @ValidAmount
    private BigDecimal amount;
    
    /**
     * The ID of the blockchain network
     */
    @NotNull(message = "Blockchain network ID is required")
    private UUID blockchainNetworkId;

    /**
     * The blockchain transaction hash (if already known)
     */
    @Size(max = 100, message = "Blockchain transaction hash cannot exceed 100 characters")
    private String blockchainTransactionHash;

    /**
     * The transaction date
     */
    @NotNull(message = "Transaction date is required")
    @ValidDateTime
    private LocalDateTime transactionDate;

    /**
     * The value date
     */
    @NotNull(message = "Value date is required")
    @ValidDateTime
    private LocalDateTime valueDate;

    /**
     * The description of the transaction
     */
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    /**
     * The gas price in the network's native currency (for Ethereum-based networks)
     */
    @DecimalMin(value = "0.0", message = "Gas price cannot be negative")
    private BigDecimal gasPrice;

    /**
     * The gas limit for the transaction (for Ethereum-based networks)
     */
    @Min(value = 0, message = "Gas limit cannot be negative")
    private Long gasLimit;

    /**
     * The transaction fee in the network's native currency
     */
    @ValidAmount
    private BigDecimal transactionFee;

    /**
     * The currency of the transaction fee (e.g., ETH for Ethereum)
     */
    @ValidCurrencyCode
    private String feeCurrency;

    /**
     * Additional data or notes about the transaction
     */
    @Size(max = 1000, message = "Memo cannot exceed 1000 characters")
    private String memo;

    /**
     * External reference for the transaction
     */
    @Size(max = 100, message = "External reference cannot exceed 100 characters")
    private String externalReference;
}