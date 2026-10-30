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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

/**
 * Data Transfer Object representing comprehensive market data for a crypto asset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketData {
    
    /**
     * The ID of the crypto asset
     */
    private UUID cryptoAssetId;
    
    /**
     * The symbol of the crypto asset (e.g., BTC, ETH)
     */
    private String assetSymbol;
    
    /**
     * The name of the crypto asset (e.g., Bitcoin, Ethereum)
     */
    private String assetName;
    
    /**
     * The current price of the crypto asset
     */
    private BigDecimal currentPrice;
    
    /**
     * The currency in which the price is quoted (e.g., USD, EUR)
     */
    private String quoteCurrency;
    
    /**
     * The timestamp of the market data
     */
    private LocalDateTime timestamp;
    
    /**
     * The market capitalization of the crypto asset
     */
    private BigDecimal marketCap;
    
    /**
     * The fully diluted market capitalization
     */
    private BigDecimal fullyDilutedMarketCap;
    
    /**
     * The 24-hour trading volume
     */
    private BigDecimal volume24h;
    
    /**
     * The circulating supply of the crypto asset
     */
    private BigDecimal circulatingSupply;
    
    /**
     * The total supply of the crypto asset
     */
    private BigDecimal totalSupply;
    
    /**
     * The maximum supply of the crypto asset (if applicable)
     */
    private BigDecimal maxSupply;
    
    /**
     * The all-time high price
     */
    private BigDecimal allTimeHigh;
    
    /**
     * The date of the all-time high
     */
    private LocalDateTime allTimeHighDate;
    
    /**
     * The all-time low price
     */
    private BigDecimal allTimeLow;
    
    /**
     * The date of the all-time low
     */
    private LocalDateTime allTimeLowDate;
    
    /**
     * Price change in various time periods (e.g., "1h", "24h", "7d", "30d")
     */
    private Map<String, BigDecimal> priceChange;
    
    /**
     * Price change percentage in various time periods (e.g., "1h", "24h", "7d", "30d")
     */
    private Map<String, Double> priceChangePercentage;
    
    /**
     * The source of the market data (e.g., "CoinGecko", "CoinMarketCap")
     */
    private String source;
    
    /**
     * The rank of the crypto asset by market cap
     */
    private Integer marketCapRank;
    
    /**
     * The current market sentiment (e.g., "Bullish", "Bearish", "Neutral")
     */
    private String marketSentiment;
    
    /**
     * The volatility index of the crypto asset
     */
    private Double volatilityIndex;
}