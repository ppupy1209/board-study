package board.auth.service;

import board.auth.config.AuthProperties;
import board.auth.domain.AuthMember;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class AccessTokenIssuer {
    private final AuthProperties properties;
    private final JWSSigner signer;

    public AccessTokenIssuer(AuthProperties properties) {
        this.properties = properties;
        try {
            this.signer = new MACSigner(properties.secret().getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException exception) {
            throw new IllegalStateException("JWT 서명 키를 초기화하지 못했습니다.", exception);
        }
    }

    public IssuedAccessToken issue(AuthMember member, String familyId, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .subject(member.getMemberId().toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("email", member.getEmail())
                .claim("sid", familyId)
                .claim("scope", "member")
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException exception) {
            throw new IllegalStateException("Access Token을 서명하지 못했습니다.", exception);
        }
        return new IssuedAccessToken(
                jwt.serialize(),
                expiresAt,
                properties.accessTokenTtl().toSeconds()
        );
    }
}
