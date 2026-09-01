package board.auth.api;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {
    private final AuthErrorCode errorCode;

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }
}
