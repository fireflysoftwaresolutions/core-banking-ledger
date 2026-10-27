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


package io.fireflysoftware.core.banking.ledger.interfaces.dtos.price;

import org.fireflyframework.annotations.ValidAmount;
import org.fireflyframework.annotations.ValidCurrencyCode;
import org.fireflyframework.annotations.ValidDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.UUID;
/**
 * Data Transfer Object representing a price update for a crypto asset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceUpdate {
    
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
     * The name of the crypto asset (e.g., Bitcoin, Ethereum)
     */
    @NotBlank(message = "Asset name is required")
    @Size(max = 100, message = "Asset name cannot exceed 100 characters")
    private String assetName;

    /**
     * The current price of the crypto asset
     */
    @NotNull(message = "Price is required")
    @ValidAmount
    private BigDecimal price;

    /**
     * The currency in which the price is quoted (e.g., USD, EUR)
     */
    @NotBlank(message = "Quote currency is required")
    @ValidCurrencyCode
    private String quoteCurrency;

    /**
     * The timestamp of the price update
     */
    @NotNull(message = "Timestamp is required")
    @ValidDateTime
    private LocalDateTime timestamp;

    /**
     * The price change in the last 24 hours
     */
    @ValidAmount
    private BigDecimal priceChange24h;
    
    /**
     * The price change percentage in the last 24 hours
     */
    private Double priceChangePercentage24h;
    
    /**
     * The source of the price data (e.g., "Coinbase", "Binance")
     */
    private String source;
}