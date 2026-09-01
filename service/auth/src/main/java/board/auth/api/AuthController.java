package board.auth.api;

import board.auth.service.AuthCookieFactory;
import board.auth.service.AuthService;
import board.auth.service.AuthSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AuthCookieFactory cookieFactory;

    @PostMapping("/members")
    public ResponseEntity<MemberResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = "${auth.cookie.name:MODU_REFRESH}", required = false) String refreshToken
    ) {
        return tokenResponse(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${auth.cookie.name:MODU_REFRESH}", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshToken())
                .build();
    }

    @GetMapping("/me")
    public AuthenticatedMemberResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new AuthenticatedMemberResponse(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("sid")
        );
    }

    private ResponseEntity<AccessTokenResponse> tokenResponse(AuthSession session) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookieFactory.refreshToken(session.refreshToken(), session.refreshTokenMaxAge())
                )
                .body(AccessTokenResponse.bearer(
                        session.accessToken().value(),
                        session.accessToken().expiresInSeconds()
                ));
    }
}
