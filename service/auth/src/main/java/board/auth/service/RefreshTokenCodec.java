package board.auth.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshTokenCodec {
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedRefreshToken generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return new GeneratedRefreshToken(rawValue, hash(rawValue));
    }

    public String hash(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
