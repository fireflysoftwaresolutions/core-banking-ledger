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


package io.fireflysoftware.core.banking.ledger.interfaces.enums.attachment;

/**
 * Enum representing the types of attachments that can be associated with transactions.
 */
public enum AttachmentTypeEnum {
    /**
     * Invoice document.
     */
    INVOICE,
    
    /**
     * Receipt document.
     */
    RECEIPT,
    
    /**
     * Contract document.
     */
    CONTRACT,
    
    /**
     * Identification document.
     */
    IDENTIFICATION,
    
    /**
     * Authorization document.
     */
    AUTHORIZATION,
    
    /**
     * Statement document.
     */
    STATEMENT,
    
    /**
     * Proof of payment document.
     */
    PROOF_OF_PAYMENT,
    
    /**
     * Supporting document for a transaction.
     */
    SUPPORTING_DOCUMENT,
    
    /**
     * Correspondence related to a transaction.
     */
    CORRESPONDENCE,
    
    /**
     * Other type of document.
     */
    OTHER
}
