package board.articleread.service;

import board.articleread.client.ArticleClient;
import board.articleread.client.CommentClient;
import board.articleread.client.LikeClient;
import board.articleread.client.ViewClient;
import board.articleread.repository.ArticleLookupLockRepository;
import board.articleread.repository.ArticleIdListRepository;
import board.articleread.repository.ArticleQueryModel;
import board.articleread.repository.ArticleQueryModelRepository;
import board.articleread.repository.BoardArticleCountRepository;
import board.articleread.repository.MissingArticleCacheRepository;
import board.articleread.service.event.handler.EventHandler;
import board.articleread.service.response.ArticleReadPageResponse;
import board.articleread.service.response.ArticleReadResponse;
import board.common.event.Event;
import board.common.event.EventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 조회 모델(Query Model) 기반 읽기 서비스.
 *
 * <h2>이 클래스가 원본 서비스를 부르는 횟수에 대해</h2>
 * 조회 모델은 Redis에 있고 {@code ARTICLE_CREATED} 등 이벤트로 채워진다. 즉 <b>완전한 복제본이 아니라
 * 핫셋 캐시</b>다. 조회 모델에 없는 게시글(마이그레이션·백필로 들어왔거나 오래돼 TTL이 지난 글)을 읽으면
 * 원본 서비스들을 호출해 즉석에서 만들어야 한다. 이때 호출 수를 관리하지 않으면
 * <b>조회 모델이 부하를 줄이는 게 아니라 늘린다.</b>
 *
 * <p>실제로 그런 상태였다. 목록 조회 1건이 내부 호출 <b>118건</b>을 유발하고 있었다.
 * 원인은 두 가지였다.
 * <ol>
 *   <li>원본 목록 조회가 이미 <b>본문까지 들어 있는</b> 게시글 30건을 돌려주는데,
 *       그걸 버리고 ID만 남긴 뒤 같은 글을 하나씩 다시 조회했다.</li>
 *   <li>댓글 수·좋아요 수·조회수를 게시글마다 건건이 조회했다(N+1).</li>
 * </ol>
 * 지금은 (1) 원본 목록의 본문을 그대로 쓰고 (2) 카운트는 배치 endpoint로 한 번에 가져온다.
 * 그 결과 목록 조회의 내부 호출이 <b>118건 → 4건</b>이 됐다.
 *
 * @see board.articleread.service.QueryModelMetrics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleReadService {
    private final ArticleClient articleClient;
    private final CommentClient commentClient;
    private final LikeClient likeClient;
    private final ViewClient viewClient;
    private final ArticleQueryModelRepository articleQueryModelRepository;
    private final ArticleIdListRepository articleIdListRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;
    private final QueryModelMetrics queryModelMetrics;
    private final MissingArticleCacheRepository missingArticleCacheRepository;
    private final ArticleLookupLockRepository articleLookupLockRepository;
    private final ArticleMissingCacheMetrics articleMissingCacheMetrics;

    /** 원본 병렬 호출 전용. common ForkJoinPool에 블로킹 호출을 얹지 않기 위해 분리했다. */
    private final ExecutorService articleReadFanoutExecutor;
    private final List<EventHandler> eventHandlers;

    private static final Duration QUERY_MODEL_TTL = Duration.ofDays(1);
    private static final Duration LOOKUP_WAIT_TIMEOUT = Duration.ofSeconds(2);
    private static final long LOOKUP_POLL_MILLIS = 10;

    public void handleEvent(Event<EventPayload> event) {
        for (EventHandler eventHandler : eventHandlers) {
            if (eventHandler.supports(event)) {
                eventHandler.handle(event);
            }
        }
    }

    // ────────────────────────────── 상세 조회 ──────────────────────────────

    public ArticleReadResponse read(Long articleId) {
        Optional<ArticleQueryModel> cached = articleQueryModelRepository.read(articleId);
        if (cached.isPresent()) {
            queryModelMetrics.hit(1);
            return withCurrentCounts(cached.get());
        }
        queryModelMetrics.miss(1);
        if (missingArticleCacheRepository.isMissing(articleId)) {
            articleMissingCacheMetrics.hit();
            throw articleNotFound(articleId);
        }

        ArticleQueryModel loaded = loadOnceOrWait(articleId)
                .orElseThrow(() -> articleNotFound(articleId));
        return withCurrentCounts(loaded);
    }

    private ArticleReadResponse withCurrentCounts(ArticleQueryModel queryModel) {
        Long articleId = queryModel.getArticleId();
        CompletableFuture<Long> commentCount =
                CompletableFuture.supplyAsync(() -> commentClient.count(articleId), articleReadFanoutExecutor);
        CompletableFuture<Long> likeCount =
                CompletableFuture.supplyAsync(() -> likeClient.count(articleId), articleReadFanoutExecutor);
        CompletableFuture<Long> viewCount =
                CompletableFuture.supplyAsync(() -> viewClient.count(articleId), articleReadFanoutExecutor);
        queryModelMetrics.originCall("comment", 1);
        queryModelMetrics.originCall("like", 1);
        queryModelMetrics.originCall("view", 1);
        return ArticleReadResponse.from(queryModel, commentCount.join(), likeCount.join(), viewCount.join());
    }

    /**
     * 같은 ID의 콜드 미스가 동시에 들어와도 한 요청만 원본을 확인한다.
     *
     * <p>잠금을 잡지 못한 요청은 짧게 대기하며 조회 모델 또는 부재 표시가 만들어지는지 확인한다.
     * 잠금 소유자의 원본 호출이 실패하면 잘못된 404를 만들지 않고 503으로 종료한다.
     */
    private Optional<ArticleQueryModel> loadOnceOrWait(Long articleId) {
        String lockOwner = UUID.randomUUID().toString();
        if (!articleLookupLockRepository.tryAcquire(articleId, lockOwner)) {
            articleMissingCacheMetrics.coalesced();
            return awaitLookupResult(articleId);
        }

        try {
            Optional<ArticleQueryModel> cached = articleQueryModelRepository.read(articleId);
            if (cached.isPresent()) {
                return cached;
            }
            if (missingArticleCacheRepository.isMissing(articleId)) {
                articleMissingCacheMetrics.hit();
                return Optional.empty();
            }

            Optional<ArticleQueryModel> loaded = fetch(articleId);
            if (loaded.isEmpty()) {
                missingArticleCacheRepository.markMissing(articleId);
                articleMissingCacheMetrics.stored();
            }
            return loaded;
        } finally {
            articleLookupLockRepository.release(articleId, lockOwner);
        }
    }

    private Optional<ArticleQueryModel> awaitLookupResult(Long articleId) {
        long deadline = System.nanoTime() + LOOKUP_WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(LOOKUP_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw lookupUnavailable(articleId);
            }

            Optional<ArticleQueryModel> cached = articleQueryModelRepository.read(articleId);
            if (cached.isPresent()) {
                return cached;
            }
            if (missingArticleCacheRepository.isMissing(articleId)) {
                articleMissingCacheMetrics.hit();
                return Optional.empty();
            }
        }
        throw lookupUnavailable(articleId);
    }

    /**
     * 조회 모델에 없는 게시글을 원본에서 만들어 온다.
     *
     * <p>게시글 존재 여부를 먼저 확인한다. 존재하지 않는 ID라면 댓글·좋아요 서비스를 호출하지 않고
     * 부재 캐시에 기록한다. 게시글이 확인된 뒤에는 서로 독립적인 댓글 수와 좋아요 수를 병렬 조회한다.
     */
    private Optional<ArticleQueryModel> fetch(Long articleId) {
        queryModelMetrics.originCall("article", 1);
        Optional<ArticleClient.ArticleResponse> article = articleClient.read(articleId);
        if (article.isEmpty()) {
            return Optional.empty();
        }

        CompletableFuture<Long> commentCountFuture =
                CompletableFuture.supplyAsync(() -> commentClient.count(articleId), articleReadFanoutExecutor);
        CompletableFuture<Long> likeCountFuture =
                CompletableFuture.supplyAsync(() -> likeClient.count(articleId), articleReadFanoutExecutor);
        queryModelMetrics.originCall("comment", 1);
        queryModelMetrics.originCall("like", 1);

        ArticleQueryModel articleQueryModel = ArticleQueryModel.create(
                article.get(),
                commentCountFuture.join(),
                likeCountFuture.join()
        );
        articleQueryModelRepository.create(articleQueryModel, QUERY_MODEL_TTL);
        return Optional.of(articleQueryModel);
    }

    private ResponseStatusException articleNotFound(Long articleId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: " + articleId);
    }

    private ResponseStatusException lookupUnavailable(Long articleId) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Article lookup timed out: " + articleId);
    }

    // ────────────────────────────── 목록 조회 ──────────────────────────────

    public ArticleReadPageResponse readAll(Long boardId, Long page, Long pageSize) {
        List<Long> articleIds = articleIdListRepository.readAll(boardId, (page - 1) * pageSize, pageSize);
        if (pageSize == articleIds.size()) {
            // Redis ID 목록이 이 구간을 갖고 있다. 조회 모델에서 읽고 없는 것만 채운다.
            return ArticleReadPageResponse.of(readAllByIds(articleIds), count(boardId));
        }
        // Redis ID 목록에 없는 구간(대부분 깊은 페이지)이다.
        // 원본 목록 응답에는 본문이 이미 들어 있으므로 그대로 쓴다. 예전에는 이걸 버리고 ID만 남겨
        // 같은 글을 30번 다시 조회했다.
        List<ArticleClient.ArticleResponse> articles = articleClient.readAll(boardId, page, pageSize).getArticles();
        queryModelMetrics.originCall("article", 1);
        queryModelMetrics.miss(articles.size());
        return ArticleReadPageResponse.of(toResponses(articles), count(boardId));
    }

    public List<ArticleReadResponse> readAllInfiniteScroll(Long boardId, Long lastArticleId, Long pageSize) {
        List<Long> articleIds = articleIdListRepository.readAllInfiniteScroll(boardId, lastArticleId, pageSize);
        if (pageSize == articleIds.size()) {
            return readAllByIds(articleIds);
        }
        List<ArticleClient.ArticleResponse> articles =
                articleClient.readAllInfiniteScroll(boardId, lastArticleId, pageSize);
        queryModelMetrics.originCall("article", 1);
        queryModelMetrics.miss(articles.size());
        return toResponses(articles);
    }

    /**
     * 원본 목록 응답(본문 포함)을 그대로 써서 응답을 만든다.
     *
     * <p>게시글 본문은 이미 손에 있으므로 다시 조회하지 않는다.
     * 남은 것은 댓글 수 / 좋아요 수 / 조회수뿐이고, 셋 다 배치로 한 번씩만 가져온다.
     * 따라서 목록 1건당 원본 호출은 <b>총 4회</b>(목록 1 + 배치 3)로 고정된다. 페이지 크기와 무관하다.
     */
    private List<ArticleReadResponse> toResponses(List<ArticleClient.ArticleResponse> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> articleIds = articles.stream().map(ArticleClient.ArticleResponse::getArticleId).toList();

        CompletableFuture<Map<Long, Long>> commentCounts =
                CompletableFuture.supplyAsync(() -> commentClient.countAll(articleIds), articleReadFanoutExecutor);
        CompletableFuture<Map<Long, Long>> likeCounts =
                CompletableFuture.supplyAsync(() -> likeClient.countAll(articleIds), articleReadFanoutExecutor);
        CompletableFuture<Map<Long, Long>> viewCounts =
                CompletableFuture.supplyAsync(() -> viewClient.countAll(articleIds), articleReadFanoutExecutor);

        queryModelMetrics.originCall("comment", 1);
        queryModelMetrics.originCall("like", 1);
        queryModelMetrics.originCall("view", 1);

        Map<Long, Long> comments = commentCounts.join();
        Map<Long, Long> likes = likeCounts.join();
        Map<Long, Long> views = viewCounts.join();

        List<ArticleReadResponse> responses = new ArrayList<>(articles.size());
        for (ArticleClient.ArticleResponse article : articles) {
            Long articleId = article.getArticleId();
            ArticleQueryModel queryModel = ArticleQueryModel.create(
                    article,
                    comments.getOrDefault(articleId, 0L),
                    likes.getOrDefault(articleId, 0L)
            );
            // 다음 조회를 위해 조회 모델을 채워 둔다. 원본 목록으로 이미 다 만들었으므로 추가 비용이 없다.
            articleQueryModelRepository.create(queryModel, QUERY_MODEL_TTL);
            responses.add(ArticleReadResponse.from(
                    queryModel,
                    comments.getOrDefault(articleId, 0L),
                    likes.getOrDefault(articleId, 0L),
                    views.getOrDefault(articleId, 0L)
            ));
        }
        return responses;
    }

    /**
     * ID 목록으로 응답을 만든다. 조회 모델에 있는 것은 그대로 쓰고, 없는 것만 원본에서 채운다.
     *
     * <p>조회수는 어차피 전 건에 필요하므로 배치로 한 번에 가져온다(예전에는 건당 30회였다).
     */
    private List<ArticleReadResponse> readAllByIds(List<Long> articleIds) {
        Map<Long, ArticleQueryModel> queryModels =
                new LinkedHashMap<>(articleQueryModelRepository.readAll(articleIds));

        List<Long> missing = articleIds.stream().filter(id -> !queryModels.containsKey(id)).toList();
        queryModelMetrics.hit(articleIds.size() - missing.size());
        queryModelMetrics.miss(missing.size());

        for (Long articleId : missing) {
            // 여기까지 오는 경우는 드물다. Redis ID 목록에 있는 글은 이벤트로 들어온 최근 글이라
            // 조회 모델도 함께 채워져 있기 때문이다.
            fetch(articleId).ifPresent(model -> queryModels.put(articleId, model));
        }

        List<Long> presentIds = articleIds.stream().filter(queryModels::containsKey).toList();
        Map<Long, Long> comments = commentClient.countAll(presentIds);
        Map<Long, Long> likes = likeClient.countAll(presentIds);
        Map<Long, Long> views = viewClient.countAll(presentIds);
        queryModelMetrics.originCall("comment", 1);
        queryModelMetrics.originCall("like", 1);
        queryModelMetrics.originCall("view", 1);

        return presentIds.stream()
                .map(queryModels::get)
                .filter(Objects::nonNull)
                .map(model -> ArticleReadResponse.from(
                        model,
                        comments.getOrDefault(model.getArticleId(), 0L),
                        likes.getOrDefault(model.getArticleId(), 0L),
                        views.getOrDefault(model.getArticleId(), 0L)
                ))
                .toList();
    }

    private Long count(Long boardId) {
        Long result = boardArticleCountRepository.read(boardId);
        if (result != null) {
            return result;
        }
        long count = articleClient.count(boardId);
        queryModelMetrics.originCall("article", 1);
        boardArticleCountRepository.createOrUpdate(boardId, count);
        return count;
    }
}
