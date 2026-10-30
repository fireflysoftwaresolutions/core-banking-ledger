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

import jakarta.validation.constraints.*;
import java.util.Map;

import java.util.UUID;
/**
 * Data Transfer Object for token minting transactions.
 * Extends BaseCryptoTransactionDTO with token minting-specific fields.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TokenMintDTO extends BaseCryptoTransactionDTO {
    
    /**
     * The recipient's blockchain address (where tokens will be minted to)
     */
    @NotBlank(message = "Recipient address is required")
    @Size(max = 100, message = "Recipient address cannot exceed 100 characters")
    private String recipientAddress;

    /**
     * The smart contract address of the token
     */
    @NotBlank(message = "Contract address is required")
    @Size(max = 100, message = "Contract address cannot exceed 100 characters")
    private String contractAddress;

    /**
     * The token ID for NFTs (null for fungible tokens)
     */
    @Size(max = 100, message = "Token ID cannot exceed 100 characters")
    private String tokenId;

    /**
     * The token standard (e.g., "ERC20", "ERC721", "ERC1155")
     */
    @NotBlank(message = "Token standard is required")
    @Pattern(regexp = "ERC20|ERC721|ERC1155|BEP20|BEP721|BEP1155", message = "Token standard must be one of: ERC20, ERC721, ERC1155, BEP20, BEP721, BEP1155")
    private String tokenStandard;

    /**
     * The URI for token metadata (primarily for NFTs)
     */
    @Size(max = 500, message = "Token URI cannot exceed 500 characters")
    private String tokenUri;

    /**
     * The royalty percentage for NFTs (e.g., 2.5 for 2.5%)
     */
    @DecimalMin(value = "0.0", message = "Royalty percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Royalty percentage cannot exceed 100")
    private Double royaltyPercentage;

    /**
     * The address that will receive royalties
     */
    @Size(max = 100, message = "Royalty recipient address cannot exceed 100 characters")
    private String royaltyRecipientAddress;
    
    /**
     * Additional properties for the token (primarily for NFTs)
     */
    private Map<String, Object> tokenProperties;

    /**
     * Flag indicating whether this is a batch mint (multiple tokens in one transaction)
     */
    private Boolean isBatchMint;

    /**
     * The number of tokens to mint (for fungible tokens or batch NFT minting)
     */
    @Min(value = 1, message = "Token quantity must be at least 1")
    private Long tokenQuantity;

    /**
     * The ID of the user who authorized the minting
     */
    @Size(max = 100, message = "Authorized by cannot exceed 100 characters")
    private String authorizedBy;
}