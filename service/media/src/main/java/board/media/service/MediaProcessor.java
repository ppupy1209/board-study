package board.media.service;

import board.media.domain.MediaAsset;
import board.media.domain.MediaStatus;
import board.media.event.MediaEventPublisher;
import board.media.event.MediaUploadedEvent;
import board.media.repository.MediaAssetRepository;
import board.media.storage.ObjectStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaProcessor {
    private final ObjectMapper objectMapper;
    private final MediaAssetRepository mediaAssetRepository;
    private final ObjectStorage objectStorage;
    private final MediaPolicy mediaPolicy;
    private final ThumbnailGenerator thumbnailGenerator;
    private final MediaMetrics mediaMetrics;

    @KafkaListener(
            topics = MediaEventPublisher.TOPIC,
            groupId = "modu-square-media-thumbnail"
    )
    public void process(String message) {
        MediaUploadedEvent event;
        try {
            event = objectMapper.readValue(message, MediaUploadedEvent.class);
        } catch (Exception exception) {
            log.error("이미지 처리 이벤트를 읽지 못했습니다. message={}", message, exception);
            return;
        }

        MediaAsset asset = mediaAssetRepository.findById(event.mediaId()).orElse(null);
        if (asset == null || asset.isReady() || asset.getStatus() != MediaStatus.PROCESSING) {
            return;
        }

        long start = System.nanoTime();
        try {
            byte[] original = objectStorage.read(asset.getOriginalKey());
            ThumbnailResult thumbnail = thumbnailGenerator.generate(original);
            String thumbnailKey = mediaPolicy.thumbnailKey(asset.getMediaId());
            objectStorage.upload(thumbnailKey, "image/webp", thumbnail.bytes());

            mediaAssetRepository.markReady(
                    asset.getMediaId(),
                    thumbnailKey,
                    thumbnail.bytes().length,
                    thumbnail.width(),
                    thumbnail.height(),
                    LocalDateTime.now()
            );
            mediaMetrics.recordThumbnail(original.length, thumbnail.bytes().length, System.nanoTime() - start);
            mediaMetrics.recordStatus(MediaStatus.READY);
        } catch (Exception exception) {
            String reason = exception.getMessage();
            if (reason == null || reason.isBlank()) {
                reason = "thumbnail processing failed";
            }
            mediaAssetRepository.markFailed(
                    asset.getMediaId(),
                    reason.substring(0, Math.min(reason.length(), 500)),
                    LocalDateTime.now()
            );
            mediaMetrics.recordStatus(MediaStatus.FAILED);
            log.error("WebP 썸네일 생성에 실패했습니다. mediaId={}", asset.getMediaId(), exception);
        }
    }
}
