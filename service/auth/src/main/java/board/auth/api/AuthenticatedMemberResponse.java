package board.auth.api;

public record AuthenticatedMemberResponse(
        Long memberId,
        String email,
        String displayName,
        String sessionId
) {
}
