package board.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAsset {
    @Id
    @Column(length = 36)
    private String mediaId;
    private Long articleId;
    private String originalFilename;
    private String contentType;
    private String originalKey;
    private String thumbnailKey;
    private Long originalSize;
    private Long thumbnailSize;
    private Integer width;
    private Integer height;

    @Enumerated(EnumType.STRING)
    private MediaStatus status;

    @Enumerated(EnumType.STRING)
    private UploadMode uploadMode;

    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime attachedAt;

    public static MediaAsset pending(
            String originalFilename,
            String contentType,
            String originalKey,
            long originalSize,
            UploadMode uploadMode
    ) {
        MediaAsset asset = new MediaAsset();
        asset.mediaId = UUID.randomUUID().toString();
        asset.originalFilename = originalFilename;
        asset.contentType = contentType;
        asset.originalKey = originalKey;
        asset.originalSize = originalSize;
        asset.status = MediaStatus.PENDING;
        asset.uploadMode = uploadMode;
        asset.createdAt = LocalDateTime.now();
        asset.updatedAt = asset.createdAt;
        return asset;
    }

    public void markProcessing() {
        status = MediaStatus.PROCESSING;
        failureReason = null;
        updatedAt = LocalDateTime.now();
    }

    public void markReady(String thumbnailKey, long thumbnailSize, int width, int height) {
        this.thumbnailKey = thumbnailKey;
        this.thumbnailSize = thumbnailSize;
        this.width = width;
        this.height = height;
        status = MediaStatus.READY;
        failureReason = null;
        updatedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        String fallback = "thumbnail processing failed";
        String message = reason == null || reason.isBlank() ? fallback : reason;
        status = MediaStatus.FAILED;
        failureReason = message.substring(0, Math.min(message.length(), 500));
        updatedAt = LocalDateTime.now();
    }

    public void attach(Long articleId) {
        this.articleId = articleId;
        this.attachedAt = LocalDateTime.now();
        this.updatedAt = attachedAt;
    }

    public boolean canComplete() {
        return status == MediaStatus.PENDING;
    }

    public boolean isReady() {
        return status == MediaStatus.READY;
    }
}
