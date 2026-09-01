package board.auth.service;

import board.auth.config.AuthCookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {
    private final AuthCookieProperties properties;

    public String refreshToken(String value, Duration maxAge) {
        return base(value)
                .maxAge(maxAge)
                .build()
                .toString();
    }

    public String clearRefreshToken() {
        return base("")
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }

    public String name() {
        return properties.name();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path());
    }
}
