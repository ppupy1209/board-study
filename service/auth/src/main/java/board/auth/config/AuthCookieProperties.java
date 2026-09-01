package board.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.cookie")
public record AuthCookieProperties(
        String name,
        boolean secure,
        String sameSite,
        String path
) {
    public AuthCookieProperties {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Refresh Token 쿠키 이름이 필요합니다.");
        }
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Strict";
        }
        if (path == null || path.isBlank()) {
            path = "/v1/auth";
        }
    }
}
