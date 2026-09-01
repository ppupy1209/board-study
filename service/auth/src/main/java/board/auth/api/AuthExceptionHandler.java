package board.auth.api;

import board.auth.service.AuthCookieFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class AuthExceptionHandler {
    private final AuthCookieFactory cookieFactory;

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<AuthErrorResponse> handleAuthException(AuthException exception) {
        AuthErrorCode errorCode = exception.getErrorCode();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(errorCode.status());
        if (errorCode.clearRefreshCookie()) {
            response.header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshToken());
        }
        return response.body(AuthErrorResponse.of(errorCode.code(), errorCode.message()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<AuthErrorResponse> handleValidation(BindException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );
        return ResponseEntity.badRequest().body(AuthErrorResponse.validation(fieldErrors));
    }
}
