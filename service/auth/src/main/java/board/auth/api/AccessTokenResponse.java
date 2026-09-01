package board.auth.api;

public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static AccessTokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new AccessTokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
