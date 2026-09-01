package board.auth.repository;

import board.auth.domain.RefreshTokenSession;
import board.auth.domain.RefreshTokenStatus;
import board.auth.domain.TokenRevocationReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenSession token where token.tokenHash = :tokenHash")
    Optional<RefreshTokenSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);

    List<RefreshTokenSession> findAllByFamilyIdOrderByIssuedAt(String familyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshTokenSession token
               set token.status = :revokedStatus,
                   token.revocationReason = :reason,
                   token.revokedAt = :revokedAt
             where token.familyId = :familyId
               and token.status <> :revokedStatus
            """)
    int revokeFamily(
            @Param("familyId") String familyId,
            @Param("reason") TokenRevocationReason reason,
            @Param("revokedAt") Instant revokedAt,
            @Param("revokedStatus") RefreshTokenStatus revokedStatus
    );

    long countByFamilyIdAndStatus(String familyId, RefreshTokenStatus status);
}
