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

package io.fireflysoftware.core.banking.ledger.interfaces.dtos.funds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FundsContracts {
    private FundsContracts() {}

    public enum Kind {
        CUSTOMER,
        BANK_CONTROL
    }

    public enum AccountState {
        BLOCKED,
        ACTIVE,
        CLOSED
    }

    public enum ReservationState {
        HELD,
        COMMITTED,
        RELEASED,
        EXPIRED,
        REVERSED
    }

    public enum Action {
        COMMIT,
        RELEASE,
        EXPIRE,
        REVERSE
    }

    public record Enrollment(UUID enrollmentId, UUID contractId, String currency, Kind kind) {}

    public record Account(
            UUID accountId,
            UUID enrollmentId,
            UUID contractId,
            String currency,
            int currencyScale,
            String currencyPolicy,
            Kind kind,
            AccountState state,
            BigDecimal booked,
            BigDecimal held,
            BigDecimal available,
            long version) {}

    public record Reserve(
            UUID operationId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            int ttlSeconds,
            @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = ExactDeadline.class)
                    @tools.jackson.databind.annotation.JsonDeserialize(using = ExactDeadline3.class)
                    Long notAfterEpochMillis) {
        public Reserve(
                UUID operationId,
                UUID sourceAccountId,
                UUID destinationAccountId,
                BigDecimal amount,
                String currency,
                int ttlSeconds) {
            this(
                    operationId,
                    sourceAccountId,
                    destinationAccountId,
                    amount,
                    currency,
                    ttlSeconds,
                    null);
        }
    }

    public static final class ExactDeadline
            extends com.fasterxml.jackson.databind.JsonDeserializer<Long> {
        @Override
        public Long deserialize(
                com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context)
                throws java.io.IOException {
            if (parser.currentToken() != com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)
                return (Long) context.handleUnexpectedToken(Long.class, parser);
            return parser.getLongValue();
        }
    }

    public static final class ExactDeadline3
            extends tools.jackson.databind.ValueDeserializer<Long> {
        @Override
        public Long deserialize(
                tools.jackson.core.JsonParser parser,
                tools.jackson.databind.DeserializationContext context) {
            if (parser.currentToken() != tools.jackson.core.JsonToken.VALUE_NUMBER_INT)
                return (Long) context.handleUnexpectedToken(Long.class, parser);
            return parser.getLongValue();
        }
    }

    public record Reservation(
            UUID reservationId,
            UUID operationId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            ReservationState state,
            Instant expiresAt,
            UUID postingId,
            UUID reversalId,
            long version) {}

    public enum ReconciliationOutcome {
        FOUND,
        ABSENT_CLOSED
    }

    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Reconciliation(
            ReconciliationOutcome outcome,
            UUID receiptId,
            Instant observedAt,
            Long notAfterEpochMillis,
            Reserve intent,
            Reservation reservation) {}

    public record ExpiryBatch(Integer limit) {}

    public record Command(UUID actionId, Action action) {}

    public record Control(UUID actionId, AccountState state) {}

    public record FundingReceipt(UUID operationId, UUID postingId) {}

    public record Funding(
            UUID operationId,
            UUID bankControlAccountId,
            UUID customerAccountId,
            BigDecimal amount,
            String currency) {}
}
