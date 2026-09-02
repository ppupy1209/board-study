package board.auth;

import board.auth.api.AuthErrorCode;
import board.auth.api.AuthException;
import board.auth.api.LoginRequest;
import board.auth.api.RegisterRequest;
import board.auth.domain.RefreshTokenSession;
import board.auth.domain.RefreshTokenStatus;
import board.auth.domain.TokenRevocationReason;
import board.auth.repository.AuthMemberRepository;
import board.auth.repository.RefreshTokenSessionRepository;
import board.auth.service.AuthService;
import board.auth.service.AuthSession;
import board.auth.service.RefreshTokenCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthReplayIntegrationTest {
    private static final String REFRESH_COOKIE = "MODU_REFRESH";
    private static final String PASSWORD = "safe-password-1234";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuthService authService;

    @Autowired
    RefreshTokenCodec refreshTokenCodec;

    @Autowired
    RefreshTokenSessionRepository refreshTokenRepository;

    @Autowired
    AuthMemberRepository memberRepository;

    private ExecutorService executor;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        memberRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void 이전_Refresh_Token을_재사용하면_새_Token까지_패밀리_전체를_폐기한다() throws Exception {
        register("replay@modusquare.test");

        MvcResult login = login("replay@modusquare.test");
        String originalRefreshToken = refreshCookie(login);
        String accessToken = json(login).get("accessToken").asText();

        mockMvc.perform(get("/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("replay@modusquare.test"))
                .andExpect(jsonPath("$.displayName").value("modu-user"));

        MvcResult rotated = mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(refreshCookie(originalRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        String activeRefreshToken = refreshCookie(rotated);

        RefreshTokenSession original = refreshTokenRepository
                .findByTokenHash(refreshTokenCodec.hash(originalRefreshToken))
                .orElseThrow();
        assertThat(original.getStatus()).isEqualTo(RefreshTokenStatus.ROTATED);

        mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(refreshCookie(originalRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_reuse_detected"))
                .andExpect(header -> assertThat(
                        header.getResponse().getHeader(HttpHeaders.SET_COOKIE)
                ).contains("Max-Age=0"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(refreshCookie(activeRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_revoked"));

        List<RefreshTokenSession> family = refreshTokenRepository
                .findAllByFamilyIdOrderByIssuedAt(original.getFamilyId());
        assertThat(family).hasSize(2);
        assertThat(family)
                .allMatch(token -> token.getStatus() == RefreshTokenStatus.REVOKED)
                .allMatch(token -> token.getRevocationReason() == TokenRevocationReason.REUSE_DETECTED);
    }

    @Test
    void 로그아웃하면_탈취된_Refresh_Token으로_새_Access_Token을_발급할_수_없다() throws Exception {
        register("logout@modusquare.test");
        String stolenRefreshToken = refreshCookie(login("logout@modusquare.test"));

        mockMvc.perform(post("/v1/auth/logout")
                        .cookie(refreshCookie(stolenRefreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(header -> assertThat(
                        header.getResponse().getHeader(HttpHeaders.SET_COOKIE)
                ).contains("Max-Age=0"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(refreshCookie(stolenRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_revoked"));
    }

    @Test
    void 같은_Refresh_Token의_동시_갱신은_한_건만_성공하고_성공한_Token도_폐기한다() throws Exception {
        authService.register(new RegisterRequest(
                "race@modusquare.test",
                PASSWORD,
                "race-user"
        ));
        AuthSession login = authService.login(new LoginRequest("race@modusquare.test", PASSWORD));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<RefreshOutcome> first = executor.submit(() -> refreshAtOnce(login.refreshToken(), ready, start));
        Future<RefreshOutcome> second = executor.submit(() -> refreshAtOnce(login.refreshToken(), ready, start));

        ready.await();
        start.countDown();
        List<RefreshOutcome> outcomes = List.of(first.get(), second.get());

        assertThat(outcomes).filteredOn(RefreshOutcome::succeeded).hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> outcome.errorCode() == AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED)
                .hasSize(1);

        AuthSession issuedBeforeReuseDetection = outcomes.stream()
                .filter(RefreshOutcome::succeeded)
                .map(RefreshOutcome::session)
                .findFirst()
                .orElseThrow();
        assertThatThrownBy(() -> authService.refresh(issuedBeforeReuseDetection.refreshToken()))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_REVOKED)
                );

        assertThat(refreshTokenRepository.countByFamilyIdAndStatus(
                login.familyId(),
                RefreshTokenStatus.ACTIVE
        )).isZero();
    }

    private RefreshOutcome refreshAtOnce(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return RefreshOutcome.success(authService.refresh(refreshToken));
        } catch (AuthException exception) {
            return RefreshOutcome.failure(exception.getErrorCode());
        }
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/v1/auth/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                email,
                                PASSWORD,
                                "modu-user"
                        ))))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String refreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        String prefix = REFRESH_COOKIE + "=";
        int start = setCookie.indexOf(prefix);
        int end = setCookie.indexOf(';', start);
        return setCookie.substring(start + prefix.length(), end);
    }

    private Cookie refreshCookie(String value) {
        return new Cookie(REFRESH_COOKIE, value);
    }

    private record RefreshOutcome(AuthSession session, AuthErrorCode errorCode) {
        static RefreshOutcome success(AuthSession session) {
            return new RefreshOutcome(session, null);
        }

        static RefreshOutcome failure(AuthErrorCode errorCode) {
            return new RefreshOutcome(null, errorCode);
        }

        boolean succeeded() {
            return session != null;
        }
    }
}
