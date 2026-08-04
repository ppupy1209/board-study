package board.media.service;

import board.media.domain.MediaStatus;
import board.media.domain.UploadMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class MediaMetrics {
    private final Map<UploadMode, Counter> storageUploadBytes = new EnumMap<>(UploadMode.class);
    private final Map<UploadMode, Counter> applicationUploadBytes = new EnumMap<>(UploadMode.class);
    private final Map<UploadMode, Counter> completedUploads = new EnumMap<>(UploadMode.class);
    private final Counter thumbnailInputBytes;
    private final Counter thumbnailOutputBytes;
    private final Counter orphanCleaned;
    private final Timer thumbnailProcessing;
    private final MeterRegistry meterRegistry;

    public MediaMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (UploadMode mode : UploadMode.values()) {
            String tag = mode.name().toLowerCase();
            storageUploadBytes.put(mode, Counter.builder("media.storage.upload.bytes")
                    .description("Object storage에 저장된 원본 이미지 바이트")
                    .tag("mode", tag)
                    .register(meterRegistry));
            applicationUploadBytes.put(mode, Counter.builder("media.application.upload.bytes")
                    .description("애플리케이션 서버가 직접 수신한 원본 이미지 바이트")
                    .tag("mode", tag)
                    .register(meterRegistry));
            completedUploads.put(mode, Counter.builder("media.upload.completed")
                    .tag("mode", tag)
                    .register(meterRegistry));
        }
        thumbnailInputBytes = Counter.builder("media.thumbnail.input.bytes").register(meterRegistry);
        thumbnailOutputBytes = Counter.builder("media.thumbnail.output.bytes").register(meterRegistry);
        orphanCleaned = Counter.builder("media.orphan.cleaned").register(meterRegistry);
        thumbnailProcessing = Timer.builder("media.thumbnail.processing")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(meterRegistry);
    }

    public void recordUpload(UploadMode mode, long storageBytes, long applicationBytes) {
        storageUploadBytes.get(mode).increment(storageBytes);
        applicationUploadBytes.get(mode).increment(applicationBytes);
        completedUploads.get(mode).increment();
    }

    public void recordThumbnail(long inputBytes, long outputBytes, long elapsedNanos) {
        thumbnailInputBytes.increment(inputBytes);
        thumbnailOutputBytes.increment(outputBytes);
        thumbnailProcessing.record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordStatus(MediaStatus status) {
        Counter.builder("media.status.transition")
                .tag("status", status.name().toLowerCase())
                .register(meterRegistry)
                .increment();
    }

    public void recordOrphanCleaned() {
        orphanCleaned.increment();
    }
}
