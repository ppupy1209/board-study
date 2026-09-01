package board.auth.api;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "email_already_registered", "이미 가입된 이메일입니다.", false),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid_credentials", "이메일 또는 비밀번호가 올바르지 않습니다.", false),
    REFRESH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "refresh_token_missing", "Refresh Token이 없습니다.", true),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "refresh_token_invalid", "유효하지 않은 Refresh Token입니다.", true),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "refresh_token_expired", "만료된 Refresh Token입니다.", true),
    REFRESH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "refresh_token_revoked", "폐기된 Refresh Token입니다.", true),
    REFRESH_TOKEN_REUSE_DETECTED(
            HttpStatus.UNAUTHORIZED,
            "refresh_token_reuse_detected",
            "Refresh Token 재사용이 감지되어 해당 세션을 모두 종료했습니다.",
            true
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
    private final boolean clearRefreshCookie;

    AuthErrorCode(HttpStatus status, String code, String message, boolean clearRefreshCookie) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.clearRefreshCookie = clearRefreshCookie;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public boolean clearRefreshCookie() {
        return clearRefreshCookie;
    }
}
