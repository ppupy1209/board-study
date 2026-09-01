package board.auth.service;

import java.time.Duration;

public record AuthSession(
        IssuedAccessToken accessToken,
        String refreshToken,
        Duration refreshTokenMaxAge,
        String familyId
) {
}
