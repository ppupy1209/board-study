package board.media.service;

import board.media.api.MediaResponse;
import board.media.api.PresignUploadRequest;
import board.media.api.UploadTicketResponse;
import board.media.domain.MediaAsset;
import board.media.domain.MediaStatus;
import board.media.domain.UploadMode;
import board.media.event.MediaEventPublisher;
import board.media.repository.MediaAssetRepository;
import board.media.storage.ObjectMetadata;
import board.media.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaService {
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaPolicy mediaPolicy;
    private final ObjectStorage objectStorage;
    private final MediaEventPublisher eventPublisher;
    private final MediaMetrics mediaMetrics;

    @Value("${media.upload-url-expiry-minutes:10}")
    private int uploadUrlExpiryMinutes;

    @Transactional
    public UploadTicketResponse createDirectUpload(PresignUploadRequest request) {
        mediaPolicy.validate(request.fileName(), request.contentType(), request.sizeBytes());
        String safeFileName = mediaPolicy.safeFileName(request.fileName());
        String objectKey = mediaPolicy.newOriginalKey(request.contentType());
        MediaAsset asset = mediaAssetRepository.save(
                MediaAsset.pending(
                        safeFileName,
                        request.contentType(),
                        objectKey,
                        request.sizeBytes(),
                        UploadMode.DIRECT
                )
        );

        return new UploadTicketResponse(
                asset.getMediaId(),
                objectStorage.presignUpload(objectKey, uploadUrlExpiryMinutes),
                java.util.Map.of(
                        "Content-Type", request.contentType(),
                        "Cache-Control", objectStorage.immutableCacheControl()
                ),
                Instant.now().plus(uploadUrlExpiryMinutes, ChronoUnit.MINUTES)
        );
    }

    public MediaResponse completeDirectUpload(String mediaId) {
        MediaAsset asset = find(mediaId);
        if (!asset.canComplete()) {
            return response(asset);
        }

        ObjectMetadata metadata = objectStorage.stat(asset.getOriginalKey());
        if (metadata.size() != asset.getOriginalSize()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청한 크기와 업로드된 이미지 크기가 다릅니다.");
        }
        if (metadata.contentType() != null && !metadata.contentType().equals(asset.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청한 형식과 업로드된 이미지 형식이 다릅니다.");
        }

        asset.markProcessing();
        mediaAssetRepository.save(asset);
        mediaMetrics.recordUpload(UploadMode.DIRECT, metadata.size(), 0);
        mediaMetrics.recordStatus(MediaStatus.PROCESSING);
        eventPublisher.publishUploaded(asset.getMediaId());
        return response(asset);
    }

    public MediaResponse uploadThroughApplication(MultipartFile file) {
        String contentType = file.getContentType();
        mediaPolicy.validate(file.getOriginalFilename(), contentType, file.getSize());
        String objectKey = mediaPolicy.newOriginalKey(contentType);
        MediaAsset asset = mediaAssetRepository.save(
                MediaAsset.pending(
                        mediaPolicy.safeFileName(file.getOriginalFilename()),
                        contentType,
                        objectKey,
                        file.getSize(),
                        UploadMode.PROXY
                )
        );

        try {
            objectStorage.upload(objectKey, contentType, file.getBytes());
        } catch (Exception exception) {
            asset.markFailed(exception.getMessage());
            mediaAssetRepository.save(asset);
            throw new IllegalStateException("서버 경유 이미지 업로드에 실패했습니다.", exception);
        }

        asset.markProcessing();
        mediaAssetRepository.save(asset);
        mediaMetrics.recordUpload(UploadMode.PROXY, file.getSize(), file.getSize());
        mediaMetrics.recordStatus(MediaStatus.PROCESSING);
        eventPublisher.publishUploaded(asset.getMediaId());
        return response(asset);
    }

    @Transactional
    public List<MediaResponse> attach(Long articleId, List<String> mediaIds) {
        Set<String> distinctIds = mediaIds.stream().collect(Collectors.toSet());
        if (distinctIds.size() > MediaPolicy.MAX_ARTICLE_IMAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시글에는 이미지를 최대 5개까지 첨부할 수 있습니다.");
        }

        List<MediaAsset> assets = mediaAssetRepository.findAllByMediaIdIn(distinctIds);
        if (assets.size() != distinctIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부할 이미지 일부를 찾지 못했습니다.");
        }

        for (MediaAsset asset : assets) {
            if (asset.getArticleId() != null && !asset.getArticleId().equals(articleId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 게시글에 연결된 이미지입니다.");
            }
            asset.attach(articleId);
        }
        return mediaAssetRepository.saveAll(assets).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> readByArticle(Long articleId) {
        return mediaAssetRepository.findAllByArticleIdOrderByCreatedAt(articleId)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public MediaResponse read(String mediaId) {
        return response(find(mediaId));
    }

    public MediaResponse retry(String mediaId) {
        MediaAsset asset = find(mediaId);
        if (asset.getStatus() != MediaStatus.FAILED) {
            return response(asset);
        }
        asset.markProcessing();
        mediaAssetRepository.save(asset);
        mediaMetrics.recordStatus(MediaStatus.PROCESSING);
        eventPublisher.publishUploaded(asset.getMediaId());
        return response(asset);
    }

    @Transactional
    public void deleteUnattached(String mediaId) {
        MediaAsset asset = find(mediaId);
        if (asset.getArticleId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "게시글에 연결된 이미지는 여기서 삭제할 수 없습니다.");
        }
        objectStorage.delete(asset.getOriginalKey());
        objectStorage.delete(asset.getThumbnailKey());
        mediaAssetRepository.delete(asset);
    }

    @Transactional
    public void deleteByArticleId(Long articleId) {
        List<MediaAsset> assets = mediaAssetRepository.findAllByArticleIdOrderByCreatedAt(articleId);
        for (MediaAsset asset : assets) {
            objectStorage.delete(asset.getOriginalKey());
            objectStorage.delete(asset.getThumbnailKey());
        }
        mediaAssetRepository.deleteAll(assets);
    }
    @Scheduled(fixedDelayString = "${media.orphan-cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanupExpiredOrphans() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<MediaAsset> expired = mediaAssetRepository
                .findAllByArticleIdIsNullAndStatusInAndCreatedAtBefore(
                        List.of(MediaStatus.PENDING, MediaStatus.FAILED, MediaStatus.READY),
                        threshold
                );
        for (MediaAsset asset : expired) {
            objectStorage.delete(asset.getOriginalKey());
            objectStorage.delete(asset.getThumbnailKey());
            mediaAssetRepository.delete(asset);
            mediaMetrics.recordOrphanCleaned();
        }
    }

    private MediaAsset find(String mediaId) {
        return mediaAssetRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾지 못했습니다."));
    }

    private MediaResponse response(MediaAsset asset) {
        return MediaResponse.from(asset, objectStorage);
    }
}
