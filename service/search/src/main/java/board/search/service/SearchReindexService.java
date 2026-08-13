package board.search.service;

import board.search.document.ArticleDocument;
import board.search.repository.ElasticsearchArticleRepository;
import board.search.repository.ElasticsearchArticleRepository.ReindexCheckpoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SearchReindexService {
    private final ElasticsearchArticleRepository repository;
    private final RestClient articleClient;
    private final TaskExecutor taskExecutor;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ReindexStatus status = ReindexStatus.idle();

    public SearchReindexService(
            ElasticsearchArticleRepository repository,
            @Qualifier("articleRestClient") RestClient articleClient,
            @Qualifier("searchReindexTaskExecutor") TaskExecutor taskExecutor,
            @Value("${search.reindex-batch-size:5000}") int batchSize
    ) {
        this.repository = repository;
        this.articleClient = articleClient;
        this.taskExecutor = taskExecutor;
        this.batchSize = batchSize;
    }

    public ReindexStatus start(boolean reset) {
        if (!running.compareAndSet(false, true)) {
            return status;
        }
        status = new ReindexStatus("STARTING", 0, null, null, Instant.now().toString(), null, null);
        taskExecutor.execute(() -> run(reset));
        return status;
    }

    public ReindexStatus status() {
        return status;
    }

    private void run(boolean reset) {
        try {
            if (reset) {
                repository.recreateArticleIndex();
                repository.clearCheckpoint();
            }
            ReindexCheckpoint checkpoint = repository.readCheckpoint();
            long indexed = checkpoint.indexedCount();
            Long lastArticleId = checkpoint.lastArticleId();
            repository.prepareBulkLoad();
            status = progress("RUNNING", indexed, lastArticleId, null, status.startedAt(), null);

            while (true) {
                List<ArticleDocument> batch = readBatch(lastArticleId);
                if (batch.isEmpty()) {
                    break;
                }
                repository.bulkIndex(batch);
                indexed += batch.size();
                lastArticleId = batch.getLast().getArticleId();
                repository.saveCheckpoint(new ReindexCheckpoint(lastArticleId, indexed));
                status = progress("RUNNING", indexed, lastArticleId, null, status.startedAt(), null);
                if (batch.size() < batchSize) {
                    break;
                }
            }

            repository.finishBulkLoad();
            long actualDocuments = repository.documentCount();
            status = progress("COMPLETED", indexed, lastArticleId, actualDocuments,
                    status.startedAt(), Instant.now().toString());
        } catch (RuntimeException exception) {
            status = new ReindexStatus("FAILED", status.indexedCount(), status.lastArticleId(),
                    status.documentCount(), status.startedAt(), Instant.now().toString(), exception.getMessage());
            try {
                repository.finishBulkLoad();
            } catch (RuntimeException ignored) {
                // 원래 실패 원인을 유지한다.
            }
        } finally {
            running.set(false);
        }
    }

    private List<ArticleDocument> readBatch(Long lastArticleId) {
        ArticleDocument[] response = articleClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/v1/articles/infinite-scroll")
                            .queryParam("boardId", 1)
                            .queryParam("pageSize", batchSize);
                    if (lastArticleId != null) {
                        builder.queryParam("lastArticleId", lastArticleId);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(ArticleDocument[].class);
        return response == null ? List.of() : Arrays.asList(response);
    }

    private ReindexStatus progress(
            String state,
            long indexed,
            Long lastArticleId,
            Long documentCount,
            String startedAt,
            String finishedAt
    ) {
        return new ReindexStatus(state, indexed, lastArticleId, documentCount, startedAt, finishedAt, null);
    }

    public record ReindexStatus(
            String state,
            long indexedCount,
            Long lastArticleId,
            Long documentCount,
            String startedAt,
            String finishedAt,
            String error
    ) {
        static ReindexStatus idle() {
            return new ReindexStatus("IDLE", 0, null, null, null, null, null);
        }
    }
}
