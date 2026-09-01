package board.auth.domain;

public enum TokenRevocationReason {
    LOGOUT,
    REUSE_DETECTED,
    EXPIRED,
    MEMBER_NOT_FOUND
}
