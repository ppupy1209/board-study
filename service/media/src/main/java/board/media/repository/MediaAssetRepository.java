package board.media.repository;

import board.media.domain.MediaAsset;
import board.media.domain.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {
    List<MediaAsset> findAllByArticleIdOrderByCreatedAt(Long articleId);

    List<MediaAsset> findAllByMediaIdIn(Collection<String> mediaIds);

    List<MediaAsset> findAllByArticleIdIsNullAndStatusInAndCreatedAtBefore(
            Collection<MediaStatus> statuses,
            LocalDateTime threshold
    );

    @Modifying
    @Transactional
    @Query("""
            update MediaAsset media
               set media.thumbnailKey = :thumbnailKey,
                   media.thumbnailSize = :thumbnailSize,
                   media.width = :width,
                   media.height = :height,
                   media.status = board.media.domain.MediaStatus.READY,
                   media.failureReason = null,
                   media.updatedAt = :updatedAt
             where media.mediaId = :mediaId
               and media.status = board.media.domain.MediaStatus.PROCESSING
            """)
    int markReady(
            @Param("mediaId") String mediaId,
            @Param("thumbnailKey") String thumbnailKey,
            @Param("thumbnailSize") long thumbnailSize,
            @Param("width") int width,
            @Param("height") int height,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying
    @Transactional
    @Query("""
            update MediaAsset media
               set media.status = board.media.domain.MediaStatus.FAILED,
                   media.failureReason = :reason,
                   media.updatedAt = :updatedAt
             where media.mediaId = :mediaId
               and media.status = board.media.domain.MediaStatus.PROCESSING
            """)
    int markFailed(
            @Param("mediaId") String mediaId,
            @Param("reason") String reason,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
