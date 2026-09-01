package board.auth.api;

import board.auth.domain.AuthMember;

public record MemberResponse(
        Long memberId,
        String email,
        String displayName
) {
    public static MemberResponse from(AuthMember member) {
        return new MemberResponse(member.getMemberId(), member.getEmail(), member.getDisplayName());
    }
}
