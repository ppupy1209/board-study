package board.auth.service;

import board.auth.api.AuthErrorCode;
import board.auth.api.AuthException;
import board.auth.api.LoginRequest;
import board.auth.api.MemberResponse;
import board.auth.api.RegisterRequest;
import board.auth.config.AuthProperties;
import board.auth.domain.AuthMember;
import board.auth.domain.RefreshTokenSession;
import board.auth.domain.RefreshTokenStatus;
import board.auth.domain.TokenRevocationReason;
import board.auth.metrics.AuthMetrics;
import board.auth.repository.AuthMemberRepository;
import board.auth.repository.RefreshTokenSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthMemberRepository memberRepository;
    private final RefreshTokenSessionRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenCodec refreshTokenCodec;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthProperties properties;
    private final AuthMetrics metrics;
    private final Clock clock;

    @Transactional
    public MemberResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        AuthMember member = AuthMember.register(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                clock.instant()
        );
        try {
            return MemberResponse.from(memberRepository.saveAndFlush(member));
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }

    @Transactional
    public AuthSession login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AuthMember member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        Instant now = clock.instant();
        String familyId = UUID.randomUUID().toString();
        return issueSession(member, familyId, now);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthSession refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            metrics.recordRefresh("invalid");
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_MISSING);
        }

        String tokenHash = refreshTokenCodec.hash(rawRefreshToken);
        RefreshTokenSession current = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> {
                    metrics.recordRefresh("invalid");
                    return new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
                });

        Instant now = clock.instant();
        if (current.isRotated()) {
            revokeFamily(current.getFamilyId(), TokenRevocationReason.REUSE_DETECTED, now);
            metrics.recordRefresh("reuse_detected");
            metrics.recordReuseDetected();
            metrics.recordFamilyRevocation("reuse_detected");
            log.warn(
                    "Refresh Token 재사용 감지. familyId={}, memberId={}",
                    current.getFamilyId(),
                    current.getMemberId()
            );
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }
        if (current.isRevoked()) {
            metrics.recordRefresh("revoked");
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REVOKED);
        }
        if (current.isExpired(now)) {
            current.revoke(TokenRevocationReason.EXPIRED, now);
            metrics.recordRefresh("expired");
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        if (!current.isActive()) {
            metrics.recordRefresh("invalid");
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        AuthMember member = memberRepository.findById(current.getMemberId()).orElse(null);
        if (member == null) {
            revokeFamily(current.getFamilyId(), TokenRevocationReason.MEMBER_NOT_FOUND, now);
            metrics.recordRefresh("invalid");
            metrics.recordFamilyRevocation("member_not_found");
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        GeneratedRefreshToken nextToken = refreshTokenCodec.generate();
        RefreshTokenSession nextSession = RefreshTokenSession.issue(
                current.getFamilyId(),
                member.getMemberId(),
                nextToken.hash(),
                now,
                now.plus(properties.refreshTokenTtl())
        );
        current.rotateTo(nextSession.getRefreshTokenId(), now);
        refreshTokenRepository.save(nextSession);

        metrics.recordRefresh("success");
        return new AuthSession(
                accessTokenIssuer.issue(member, current.getFamilyId(), now),
                nextToken.rawValue(),
                properties.refreshTokenTtl(),
                current.getFamilyId()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(refreshTokenCodec.hash(rawRefreshToken))
                .ifPresent(token -> {
                    revokeFamily(token.getFamilyId(), TokenRevocationReason.LOGOUT, clock.instant());
                    metrics.recordFamilyRevocation("logout");
                });
    }

    private AuthSession issueSession(AuthMember member, String familyId, Instant now) {
        GeneratedRefreshToken refreshToken = refreshTokenCodec.generate();
        RefreshTokenSession refreshTokenSession = RefreshTokenSession.issue(
                familyId,
                member.getMemberId(),
                refreshToken.hash(),
                now,
                now.plus(properties.refreshTokenTtl())
        );
        refreshTokenRepository.save(refreshTokenSession);
        return new AuthSession(
                accessTokenIssuer.issue(member, familyId, now),
                refreshToken.rawValue(),
                Duration.between(now, refreshTokenSession.getExpiresAt()),
                familyId
        );
    }

    private void revokeFamily(String familyId, TokenRevocationReason reason, Instant now) {
        refreshTokenRepository.revokeFamily(
                familyId,
                reason,
                now,
                RefreshTokenStatus.REVOKED
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
