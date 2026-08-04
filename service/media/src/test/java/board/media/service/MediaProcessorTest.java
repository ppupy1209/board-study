package board.media.service;

import board.media.domain.MediaAsset;
import board.media.domain.UploadMode;
import board.media.event.MediaUploadedEvent;
import board.media.repository.MediaAssetRepository;
import board.media.storage.ObjectStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaProcessorTest {
    @Test
    void 변환_완료는_게시글_연결_필드를_덮어쓰지_않도록_처리_필드만_갱신한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        MediaPolicy policy = mock(MediaPolicy.class);
        ThumbnailGenerator generator = mock(ThumbnailGenerator.class);
        MediaMetrics metrics = mock(MediaMetrics.class);
        MediaProcessor processor = new MediaProcessor(objectMapper, repository, storage, policy, generator, metrics);

        MediaAsset asset = MediaAsset.pending("photo.png", "image/png", "originals/photo.png", 3, UploadMode.DIRECT);
        asset.markProcessing();
        byte[] original = new byte[]{1, 2, 3};
        byte[] webp = new byte[]{4, 5};

        when(repository.findById(asset.getMediaId())).thenReturn(Optional.of(asset));
        when(storage.read(asset.getOriginalKey())).thenReturn(original);
        when(generator.generate(original)).thenReturn(new ThumbnailResult(webp, 1200, 630));
        when(policy.thumbnailKey(asset.getMediaId())).thenReturn("thumbnails/photo.webp");
        when(repository.markReady(
                eq(asset.getMediaId()),
                eq("thumbnails/photo.webp"),
                eq(2L),
                eq(1200),
                eq(630),
                any(LocalDateTime.class)
        )).thenReturn(1);

        processor.process(objectMapper.writeValueAsString(new MediaUploadedEvent(asset.getMediaId())));

        verify(repository).markReady(
                eq(asset.getMediaId()),
                eq("thumbnails/photo.webp"),
                eq(2L),
                eq(1200),
                eq(630),
                any(LocalDateTime.class)
        );
        verify(repository, never()).save(asset);
    }
}