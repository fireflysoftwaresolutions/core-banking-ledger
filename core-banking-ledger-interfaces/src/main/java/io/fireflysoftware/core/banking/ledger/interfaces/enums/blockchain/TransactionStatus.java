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


package io.fireflysoftware.core.banking.ledger.interfaces.enums.blockchain;

/**
 * Enumeration of possible blockchain transaction statuses.
 */
public enum TransactionStatus {
    /**
     * Transaction has been submitted to the blockchain but not yet included in a block
     */
    PENDING,
    
    /**
     * Transaction has been included in a block but doesn't have enough confirmations yet
     */
    CONFIRMING,
    
    /**
     * Transaction has been confirmed with the required number of confirmations
     */
    CONFIRMED,
    
    /**
     * Transaction has failed (e.g., out of gas, reverted, etc.)
     */
    FAILED,
    
    /**
     * Transaction was dropped from the mempool (e.g., replaced by a transaction with higher gas price)
     */
    DROPPED,
    
    /**
     * Transaction status is unknown (e.g., transaction hash not found)
     */
    UNKNOWN
}