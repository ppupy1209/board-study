package board.media.api;

import board.media.domain.MediaAsset;
import board.media.domain.MediaStatus;
import board.media.domain.UploadMode;
import board.media.storage.ObjectStorage;

public record MediaResponse(
        String mediaId,
        Long articleId,
        String originalFilename,
        String contentType,
        long originalSize,
        Long thumbnailSize,
        Integer width,
        Integer height,
        MediaStatus status,
        UploadMode uploadMode,
        String originalUrl,
        String thumbnailUrl,
        String failureReason
) {
    public static MediaResponse from(MediaAsset asset, ObjectStorage storage) {
        return new MediaResponse(
                asset.getMediaId(),
                asset.getArticleId(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getOriginalSize(),
                asset.getThumbnailSize(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getStatus(),
                asset.getUploadMode(),
                storage.publicUrl(asset.getOriginalKey()),
                storage.publicUrl(asset.getThumbnailKey()),
                asset.getFailureReason()
        );
    }
}
