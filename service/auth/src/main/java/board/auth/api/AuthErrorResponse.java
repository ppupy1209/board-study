package board.auth.api;

import java.time.Instant;
import java.util.Map;

public record AuthErrorResponse(
        String code,
        String message,
        Instant timestamp,
        Map<String, String> fieldErrors
) {
    public static AuthErrorResponse of(String code, String message) {
        return new AuthErrorResponse(code, message, Instant.now(), Map.of());
    }

    public static AuthErrorResponse validation(Map<String, String> fieldErrors) {
        return new AuthErrorResponse(
                "validation_failed",
                "요청 값을 확인해 주세요.",
                Instant.now(),
                fieldErrors
        );
    }
}
