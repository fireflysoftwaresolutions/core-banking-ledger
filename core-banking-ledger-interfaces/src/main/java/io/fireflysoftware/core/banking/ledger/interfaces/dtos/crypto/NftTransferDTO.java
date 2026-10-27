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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.fireflyframework.annotations.ValidAmount;
import org.fireflyframework.annotations.ValidCurrencyCode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Data Transfer Object for NFT transfer transactions.
 * Extends BaseCryptoTransactionDTO with NFT transfer-specific fields.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NftTransferDTO extends BaseCryptoTransactionDTO {
    
    /**
     * The sender's blockchain address
     */
    @NotBlank(message = "Sender address is required")
    @Size(max = 100, message = "Sender address cannot exceed 100 characters")
    private String senderAddress;

    /**
     * The recipient's blockchain address
     */
    @NotBlank(message = "Recipient address is required")
    @Size(max = 100, message = "Recipient address cannot exceed 100 characters")
    private String recipientAddress;

    /**
     * The smart contract address of the NFT
     */
    @NotBlank(message = "Contract address is required")
    @Size(max = 100, message = "Contract address cannot exceed 100 characters")
    private String contractAddress;

    /**
     * The token ID of the NFT
     */
    @Size(max = 100, message = "Token ID cannot exceed 100 characters")
    private String tokenId;
    
    /**
     * List of token IDs for batch transfers (ERC-1155)
     */
    private List<String> tokenIds;
    
    /**
     * List of quantities for each token ID in batch transfers (ERC-1155)
     */
    private List<Long> quantities;
    
    /**
     * The token standard (e.g., "ERC721", "ERC1155")
     */
    @NotBlank(message = "Token standard is required")
    @Pattern(regexp = "ERC721|ERC1155|BEP721|BEP1155", message = "Token standard must be one of: ERC721, ERC1155, BEP721, BEP1155")
    private String tokenStandard;

    /**
     * The URI for token metadata
     */
    @Size(max = 500, message = "Token URI cannot exceed 500 characters")
    private String tokenUri;

    /**
     * Flag indicating whether this is a sale transaction
     */
    private Boolean isSale;

    /**
     * The sale price if this is a sale transaction
     */
    @ValidAmount
    private BigDecimal salePrice;

    /**
     * The currency of the sale price
     */
    @ValidCurrencyCode
    private String salePriceCurrency;
    
    /**
     * The marketplace where the sale occurred (if applicable)
     */
    @Size(max = 100, message = "Marketplace cannot exceed 100 characters")
    private String marketplace;

    /**
     * The marketplace fee percentage (if applicable)
     */
    @DecimalMin(value = "0.0", message = "Marketplace fee percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Marketplace fee percentage cannot exceed 100")
    private Double marketplaceFeePercentage;

    /**
     * The royalty fee percentage (if applicable)
     */
    @DecimalMin(value = "0.0", message = "Royalty fee percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Royalty fee percentage cannot exceed 100")
    private Double royaltyFeePercentage;

    /**
     * The address that received the royalty payment
     */
    @Size(max = 100, message = "Royalty recipient address cannot exceed 100 characters")
    private String royaltyRecipientAddress;
}