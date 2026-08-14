package board.hotarticle.service;

import board.hotarticle.kafka.HotArticleEventPosition;
import board.hotarticle.kafka.HotArticleEventProcessingInProgressException;
import board.hotarticle.repository.HotArticleEventVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HotArticleEventVersionGuard {
    private final HotArticleEventVersionRepository versionRepository;

    public boolean runIfLatest(HotArticleEventPosition position, Long articleId, Runnable processing) {
        String owner = UUID.randomUUID().toString();
        if (!versionRepository.tryLock(position, articleId, owner)) {
            throw new HotArticleEventProcessingInProgressException(position, articleId);
        }

        try {
            Long latestOffset = versionRepository.readLatestOffset(position, articleId);
            if (latestOffset != null && latestOffset >= position.offset()) {
                return false;
            }

            processing.run();
            versionRepository.saveLatestOffset(position, articleId);
            return true;
        } finally {
            versionRepository.unlock(position, articleId, owner);
        }
    }
}
