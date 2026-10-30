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


package io.fireflysoftware.core.banking.ledger.models.entities.core;

import io.fireflysoftware.core.banking.ledger.interfaces.enums.asset.AssetTypeEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionStatusEnum;
import io.fireflysoftware.core.banking.ledger.interfaces.enums.core.TransactionTypeEnum;
import io.fireflysoftware.core.banking.ledger.models.entities.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction")
public class Transaction extends BaseEntity {
    @Id
    @Column("transaction_id")
    private UUID transactionId;

    @Column("external_reference")
    private String externalReference;

    @Column("transaction_date")
    private LocalDateTime transactionDate;

    @Column("value_date")
    private LocalDateTime valueDate;

    @Column("transaction_type")
    private TransactionTypeEnum transactionType;

    @Column("transaction_status")
    private TransactionStatusEnum transactionStatus;

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("currency")
    private String currency;

    @Column("description")
    private String description;

    @Column("initiating_party")
    private String initiatingParty;

    /**
     * Reference to account ID in external account microservice.
     * This is a logical reference to an account managed in a separate microservice.
     */
    @Column("account_id")
    private UUID accountId;

    /**
     * Reference to account space ID in external account microservice.
     * This is a logical reference to an account space managed in a separate microservice.
     */
    @Column("account_space_id")
    private UUID accountSpaceId;

    /**
     * Reference to category ID in external master data microservice.
     * This is no longer a foreign key to an internal table but a logical reference
     * to a category managed in a separate microservice.
     */
    @Column("transaction_category_id")
    private UUID transactionCategoryId;

    @Column("branch_office_code")
    private String branchOfficeCode;

    @Column("initiating_party_identifier")
    private String initiatingPartyIdentifier;

    // Geotag fields
    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("location_name")
    private String locationName;

    @Column("country")
    private String country;

    @Column("city")
    private String city;

    @Column("postal_code")
    private String postalCode;

    // Relation fields
    @Column("related_transaction_id")
    private UUID relatedTransactionId;

    @Column("relation_type")
    private String relationType;

    @Column("request_id")
    private String requestId;

    @Column("batch_id")
    private String batchId;

    @Column("booking_date")
    private LocalDateTime bookingDate;

    @Version
    @Column("row_version")
    private Long rowVersion;

    @Column("managed_posting")
    private boolean managedPosting;

    // Compliance fields
    @Column("aml_risk_score")
    private Integer amlRiskScore;

    @Column("aml_screening_result")
    private String amlScreeningResult;

    @Column("aml_large_txn_flag")
    private Boolean amlLargeTxnFlag;

    @Column("sca_method")
    private String scaMethod;

    @Column("sca_result")
    private String scaResult;

    @Column("instant_flag")
    private Boolean instantFlag;

    @Column("confirmation_of_payee_result")
    private String confirmationOfPayeeResult;

    // Crypto and blockchain fields
    /**
     * Type of asset (FIAT, CRYPTOCURRENCY, TOKEN_SECURITY, etc.)
     * Defaults to FIAT for backward compatibility
     */
    @Column("asset_type")
    private AssetTypeEnum assetType = AssetTypeEnum.FIAT;

    /**
     * Reference to blockchain_network table for cryptocurrency and token transactions
     * Only populated when assetType is not FIAT
     */
    @Column("blockchain_network_id")
    private UUID blockchainNetworkId;

    /**
     * Transaction hash on the blockchain
     * Only populated for crypto transactions
     */
    @Column("blockchain_transaction_hash")
    private String blockchainTransactionHash;

    /**
     * Result of compliance checks specific to crypto transactions
     * E.g., "APPROVED", "REJECTED", "MANUAL_REVIEW"
     */
    @Column("crypto_compliance_check_result")
    private String cryptoComplianceCheckResult;

    /**
     * Risk score for crypto addresses involved in the transaction
     * Higher values indicate higher risk
     */
    @Column("crypto_address_risk_score")
    private Integer cryptoAddressRiskScore;

    /**
     * Source of the crypto transaction
     * E.g., "EXCHANGE", "WALLET", "SMART_CONTRACT"
     */
    @Column("crypto_transaction_source")
    private String cryptoTransactionSource;
}
