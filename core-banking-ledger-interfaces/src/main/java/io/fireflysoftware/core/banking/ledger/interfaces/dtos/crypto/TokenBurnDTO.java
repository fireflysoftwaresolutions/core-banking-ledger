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

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.*;

/**
 * Data Transfer Object for token burning transactions.
 * Extends BaseCryptoTransactionDTO with token burning-specific fields.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TokenBurnDTO extends BaseCryptoTransactionDTO {
    
    /**
     * The owner's blockchain address (from which tokens will be burned)
     */
    @NotBlank(message = "Owner address is required")
    @Size(max = 100, message = "Owner address cannot exceed 100 characters")
    private String ownerAddress;

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
     * Flag indicating whether this is a batch burn (multiple tokens in one transaction)
     */
    private Boolean isBatchBurn;

    /**
     * The number of tokens to burn (for fungible tokens or batch NFT burning)
     */
    @Min(value = 1, message = "Token quantity must be at least 1")
    private Long tokenQuantity;

    /**
     * The reason for burning the tokens
     */
    @Size(max = 500, message = "Burn reason cannot exceed 500 characters")
    private String burnReason;

    /**
     * The ID of the user who authorized the burning
     */
    @Size(max = 100, message = "Authorized by cannot exceed 100 characters")
    private String authorizedBy;

    /**
     * Flag indicating whether this burn is part of a redemption process
     */
    private Boolean isRedemption;

    /**
     * The redemption reference ID if this burn is part of a redemption process
     */
    @Size(max = 100, message = "Redemption reference cannot exceed 100 characters")
    private String redemptionReference;
}