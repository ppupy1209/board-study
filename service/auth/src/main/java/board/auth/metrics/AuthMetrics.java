package board.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuthMetrics {
    private final Map<String, Counter> refreshAttempts;
    private final Map<String, Counter> familyRevocations;
    private final Counter reuseDetected;

    public AuthMetrics(MeterRegistry registry) {
        refreshAttempts = Map.of(
                "success", refreshCounter(registry, "success"),
                "invalid", refreshCounter(registry, "invalid"),
                "expired", refreshCounter(registry, "expired"),
                "revoked", refreshCounter(registry, "revoked"),
                "reuse_detected", refreshCounter(registry, "reuse_detected")
        );
        familyRevocations = Map.of(
                "logout", revocationCounter(registry, "logout"),
                "reuse_detected", revocationCounter(registry, "reuse_detected"),
                "member_not_found", revocationCounter(registry, "member_not_found")
        );
        reuseDetected = Counter.builder("auth.refresh.reuse.detected")
                .description("폐기된 이전 Refresh Token이 다시 제출된 횟수")
                .register(registry);
    }

    public void recordRefresh(String result) {
        refreshAttempts.get(result).increment();
    }

    public void recordFamilyRevocation(String reason) {
        familyRevocations.get(reason).increment();
    }

    public void recordReuseDetected() {
        reuseDetected.increment();
    }

    private Counter refreshCounter(MeterRegistry registry, String result) {
        return Counter.builder("auth.refresh.attempts")
                .description("Refresh Token 갱신 시도 결과")
                .tag("result", result)
                .register(registry);
    }

    private Counter revocationCounter(MeterRegistry registry, String reason) {
        return Counter.builder("auth.token.family.revocations")
                .description("Refresh Token 패밀리 폐기 횟수")
                .tag("reason", reason)
                .register(registry);
    }
}
