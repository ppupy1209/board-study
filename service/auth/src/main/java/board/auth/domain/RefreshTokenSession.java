package board.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenSession {
    @Id
    @Column(length = 36)
    private String refreshTokenId;

    @Column(nullable = false, length = 36)
    private String familyId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 43)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefreshTokenStatus status;

    @Column(length = 36)
    private String replacedByTokenId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TokenRevocationReason revocationReason;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant rotatedAt;
    private Instant revokedAt;

    public static RefreshTokenSession issue(
            String familyId,
            Long memberId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        RefreshTokenSession session = new RefreshTokenSession();
        session.refreshTokenId = UUID.randomUUID().toString();
        session.familyId = familyId;
        session.memberId = memberId;
        session.tokenHash = tokenHash;
        session.status = RefreshTokenStatus.ACTIVE;
        session.issuedAt = issuedAt;
        session.expiresAt = expiresAt;
        return session;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isActive() {
        return status == RefreshTokenStatus.ACTIVE;
    }

    public boolean isRotated() {
        return status == RefreshTokenStatus.ROTATED;
    }

    public boolean isRevoked() {
        return status == RefreshTokenStatus.REVOKED;
    }

    public void rotateTo(String nextRefreshTokenId, Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("활성 Refresh Token만 회전할 수 있습니다.");
        }
        status = RefreshTokenStatus.ROTATED;
        replacedByTokenId = nextRefreshTokenId;
        rotatedAt = now;
    }

    public void revoke(TokenRevocationReason reason, Instant now) {
        status = RefreshTokenStatus.REVOKED;
        revocationReason = reason;
        revokedAt = now;
    }
}
