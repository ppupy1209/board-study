package board.auth.service;

import java.time.Instant;

public record IssuedAccessToken(
        String value,
        Instant expiresAt,
        long expiresInSeconds
) {
}
