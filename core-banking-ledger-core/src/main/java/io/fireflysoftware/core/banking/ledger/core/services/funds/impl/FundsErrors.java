package io.fireflysoftware.core.banking.ledger.core.services.funds.impl;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class FundsErrors {
    private FundsErrors() {}

    static Throwable nativeBusinessConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof io.r2dbc.postgresql.api.PostgresqlException postgres) {
                var details = postgres.getErrorDetails();
                if (!"23514".equals(details.getCode())) return error;
                String message = details.getMessage();
                if (message == null) return error;
                String reason = switch (message) {
                    case "FND_RESERVATION_DEADLINE_ELAPSED" -> "RESERVATION_DEADLINE_ELAPSED";
                    case "FND_RESERVATION_IS_TERMINAL" -> "RESERVATION_IS_TERMINAL";
                    case "FND_ONLY_COMMITTED_FUNDS_CAN_REVERSE" -> "ONLY_COMMITTED_FUNDS_CAN_REVERSE";
                    case "FND_RESERVATION_EXPIRED" -> "RESERVATION_EXPIRED";
                    case "FND_RESERVATION_NOT_DUE" -> "RESERVATION_NOT_DUE";
                    case "FND_FUNDS_ACCOUNT_BLOCKED" -> "FUNDS_ACCOUNT_BLOCKED";
                    case "FND_REVERSAL_REQUIRES_AVAILABLE_RECIPIENT_FUNDS" ->
                            "REVERSAL_REQUIRES_AVAILABLE_RECIPIENT_FUNDS";
                    default -> null;
                };
                return reason == null ? error : fail(409, reason);
            }
        }
        return error;
    }

    static ResponseStatusException fail(int status, String reason) {
        return new ResponseStatusException(HttpStatus.valueOf(status), reason);
    }

    static <T> reactor.core.publisher.Mono<T> conflict() {
        return reactor.core.publisher.Mono.error(fail(409, "FUNDS_NATIVE_WRITE_CONFLICT"));
    }
}
