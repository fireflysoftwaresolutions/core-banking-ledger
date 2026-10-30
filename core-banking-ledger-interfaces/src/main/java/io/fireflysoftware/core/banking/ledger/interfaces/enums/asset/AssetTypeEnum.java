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


package io.fireflysoftware.core.banking.ledger.interfaces.enums.asset;

/**
 * Enumeration of asset types supported by the ledger system.
 * This enum corresponds to the asset_type_enum in the database.
 */
public enum AssetTypeEnum {
    /**
     * Traditional fiat currencies (e.g., USD, EUR, GBP)
     */
    FIAT,
    
    /**
     * Native cryptocurrencies (e.g., BTC, ETH)
     */
    CRYPTOCURRENCY,
    
    /**
     * Security tokens representing ownership in an asset
     */
    TOKEN_SECURITY,
    
    /**
     * Utility tokens providing access to a product or service
     */
    TOKEN_UTILITY,
    
    /**
     * Non-fungible tokens representing unique digital assets
     */
    TOKEN_NFT,
    
    /**
     * Stablecoins pegged to the value of another asset
     */
    TOKEN_STABLECOIN
}