package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import static org.assertj.core.api.Assertions.*;

import io.r2dbc.postgresql.api.ErrorDetails;
import io.r2dbc.postgresql.api.PostgresqlException;
import io.r2dbc.spi.R2dbcException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

class FundsNativeBusinessConflictTest {
    private static final List<String> REASONS = List.of(
            "RESERVATION_DEADLINE_ELAPSED", "RESERVATION_IS_TERMINAL",
            "ONLY_COMMITTED_FUNDS_CAN_REVERSE", "RESERVATION_EXPIRED", "RESERVATION_NOT_DUE",
            "FUNDS_ACCOUNT_BLOCKED", "REVERSAL_REQUIRES_AVAILABLE_RECIPIENT_FUNDS");

    private static final class NativeFailure extends R2dbcException implements PostgresqlException {
        private final ErrorDetails details;
        NativeFailure(String code, String message) {
            super(message == null ? "metadata message absent" : message, code, 0);
            details = ErrorDetails.fromCodeAndMessage(code, message);
        }
        public ErrorDetails getErrorDetails() { return details; }
    }

    @Test
    void exactAdmissionBusinessErrorsRetainConflictAndDomainReason() {
        for (String reason : REASONS) {
            Throwable result = FundsErrors.nativeBusinessConflict(new NativeFailure("23514", "FND_" + reason));
            assertThat(result).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode().value()).isEqualTo(409);
                assertThat(error.getReason()).isEqualTo(reason);
            });
        }
    }

    @Test
    void springAndReactiveWrappersDoNotHideActualDriverDetails() {
        var driver = new NativeFailure("23514", "FND_RESERVATION_EXPIRED");
        Throwable wrapped = new IllegalStateException("reactive wrapper",
                new DataIntegrityViolationException("spring wrapper", driver));
        assertThat(FundsErrors.nativeBusinessConflict(wrapped)).isInstanceOfSatisfying(
                ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void canonicalAndUnknownMessagesPassThroughUnchanged() {
        for (String message : List.of("FND_RECEIPT_INVALID_CANONICAL", "FND_RESERVATION_EXPIRED_OTHER", "other")) {
            Throwable error = new NativeFailure("23514", message);
            assertThat(FundsErrors.nativeBusinessConflict(error)).isSameAs(error);
        }
    }

    @Test
    void privilegeAndUnrelatedSqlStatesPassThroughEvenWithKnownMessage() {
        for (String code : List.of("42501", "23505", "08006", "P0001")) {
            Throwable error = new NativeFailure(code, "FND_RESERVATION_EXPIRED");
            assertThat(FundsErrors.nativeBusinessConflict(error)).isSameAs(error);
        }
    }

    @Test
    void actualDetailsFactoryAllowsNullMessageAndClassifierRetainsOriginalError() {
        var error = new NativeFailure("23514", null);
        assertThat(error.getErrorDetails().getMessage()).isNull();
        assertThat(FundsErrors.nativeBusinessConflict(error)).isSameAs(error);
    }

    @Test
    void plainTextCannotImpersonateDriverMetadata() {
        Throwable error = new DataIntegrityViolationException("23514 FND_RESERVATION_EXPIRED");
        assertThat(FundsErrors.nativeBusinessConflict(error)).isSameAs(error);
    }
}
