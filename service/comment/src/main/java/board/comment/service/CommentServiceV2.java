package board.comment.service;

import board.comment.entity.ArticleCommentCount;
import board.comment.entity.Comment;
import board.comment.entity.CommentPath;
import board.comment.entity.CommentV2;
import board.comment.repository.ArticleCommentCountRepository;
import board.comment.repository.CommentRepositoryV2;
import board.comment.service.request.CommentCreateRequestV2;
import board.comment.service.response.CommentPageResponse;
import board.comment.service.response.CommentResponse;
import board.common.event.payload.CommentCreatedEventPayload;
import board.common.event.payload.CommentDeletedEventPayload;
import board.common.event.payload.EventType;
import board.common.outboxmessagerelay.OutboxEventPublisher;
import board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.function.Predicate.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceV2 {
    private final Snowflake snowflake = new Snowflake();
    private final CommentRepositoryV2 commentRepository;
    private final ArticleCommentCountRepository articleCommentCountRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final TransactionTemplate transactionTemplate;

    private static final int MAX_PATH_ALLOCATION_ATTEMPTS = 3;

    /**
     * 댓글을 만든다. path 충돌이 나면 다시 채번해 재시도한다.
     *
     * <p>path 채번은 "게시글 안의 현재 최대 path를 읽고 → 다음 값을 계산해 → INSERT" 순서인데
     * 읽기와 쓰기 사이에 잠금이 없다. 같은 게시글에 동시에 댓글이 들어오면 여러 트랜잭션이
     * 같은 최대 path를 읽고 같은 다음 path를 계산해, 한쪽이 unique 제약에 걸린다.
     * 충돌한 쪽은 최신 상태로 다시 채번하면 성공하므로 재시도로 흡수한다.
     *
     * <p>재시도는 반드시 실패한 트랜잭션 <b>바깥</b>에서 해야 한다. 롤백된 트랜잭션은 재사용할 수 없기 때문이다.
     * 그래서 이 메서드에는 @Transactional을 두지 않고 {@link TransactionTemplate}으로 시도마다 새 트랜잭션을 연다.
     * (같은 빈 안에서 @Transactional 메서드를 직접 호출하면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다.)
     */
    public CommentResponse create(CommentCreateRequestV2 request) {
        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_PATH_ALLOCATION_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> createOnce(request));
            } catch (DataIntegrityViolationException e) {
                lastFailure = e;
                log.warn("[CommentServiceV2.create] comment path 충돌. 재채번 후 재시도 {}/{}. articleId={}",
                        attempt, MAX_PATH_ALLOCATION_ATTEMPTS, request.getArticleId());
            }
        }
        log.error("[CommentServiceV2.create] path 채번을 {}회 시도했으나 계속 충돌했다. articleId={}",
                MAX_PATH_ALLOCATION_ATTEMPTS, request.getArticleId());
        throw lastFailure;
    }

    private CommentResponse createOnce(CommentCreateRequestV2 request) {
        // 댓글 수 증가를 맨 앞으로 옮겼다. 단순히 순서를 바꾼 게 아니라 잠금 지점을 만들기 위해서다.
        //
        // 이 원자적 upsert는 (article_id) 행에 배타 잠금을 걸고 커밋까지 유지한다.
        // 따라서 같은 게시글의 댓글 생성이 여기서 직렬화되고, 아래 path 채번
        // (findDescendantTopPath로 읽고 -> 다음 path 계산 -> INSERT)이 서로 경쟁하지 않는다.
        // 잠금은 게시글 단위라 다른 게시글의 댓글은 그대로 병렬로 처리된다.
        //
        // 이 순서 이전에는 30건 동시 댓글 중 10건만 성공하고 20건이 path 중복으로 실패했다.
        articleCommentCountRepository.increaseOrCreate(request.getArticleId());

        CommentV2 parent = findParent(request);
        CommentPath parentCommentPath = parent == null ? CommentPath.create("") : parent.getCommentPath();
        CommentV2 comment = commentRepository.save(
                CommentV2.create(
                        snowflake.nextId(),
                        request.getContent(),
                        request.getArticleId(),
                        request.getWriterId(),
                        parentCommentPath.createChildCommentPath(
                                commentRepository.findDescendantTopPath(request.getArticleId(), parentCommentPath.getPath())
                                        .orElse(null)
                        )
                )
        );

        outboxEventPublisher.publish(
                EventType.COMMENT_CREATED,
                CommentCreatedEventPayload.builder()
                        .commentId(comment.getCommentId())
                        .content(comment.getContent())
                        .articleId(comment.getArticleId())
                        .writerId(comment.getWriterId())
                        .deleted(comment.getDeleted())
                        .createdAt(comment.getCreatedAt())
                        .articleCommentCount(count(comment.getArticleId()))
                        .build(),
                comment.getArticleId()
        );

        return CommentResponse.from(comment);
    }

    public CommentResponse read(Long commentId) {
        return CommentResponse.from(commentRepository.findById(commentId).orElseThrow());
    }

    @Transactional
    public void delete(Long commentId) {
        commentRepository.findById(commentId)
                .filter(not(CommentV2::getDeleted))
                .ifPresent(comment -> {
                    if (hasChildren(comment)) {
                        comment.delete();
                    } else {
                        delete(comment);
                    }

                    outboxEventPublisher.publish(
                            EventType.COMMENT_DELETED,
                            CommentDeletedEventPayload.builder()
                                    .commentId(comment.getCommentId())
                                    .content(comment.getContent())
                                    .articleId(comment.getArticleId())
                                    .writerId(comment.getWriterId())
                                    .deleted(comment.getDeleted())
                                    .createdAt(comment.getCreatedAt())
                                    .articleCommentCount(count(comment.getArticleId()))
                                    .build(),
                            comment.getArticleId()
                    );
                });


    }

    private boolean hasChildren(CommentV2 comment) {
        return commentRepository.findDescendantTopPath(
                comment.getArticleId(),
                comment.getCommentPath().getPath()
        ).isPresent();
    }

    private void delete(CommentV2 comment) {
        commentRepository.delete(comment);
        articleCommentCountRepository.decrease(comment.getArticleId());
        if (!comment.isRoot()) {
            commentRepository.findByPath(comment.getCommentPath().getParentPath())
                    .filter(CommentV2::getDeleted)
                    .filter(not(this::hasChildren))
                    .ifPresent(this::delete);
        }
    }

    private CommentV2 findParent(CommentCreateRequestV2 request) {
        String parentPath = request.getParentPath();
        if (parentPath == null) {
            return null;
        }
        return commentRepository.findByPath(parentPath)
                .filter(not(CommentV2::getDeleted))
                .orElseThrow();
    }

    public CommentPageResponse readAll(Long articleId, Long page, Long pageSize) {
        return CommentPageResponse.of(
                commentRepository.findAll(articleId, (page - 1) * pageSize, pageSize).stream()
                        .map(CommentResponse::from)
                        .toList(),
                commentRepository.count(articleId, PageLimitCalculator.calculatePageLimit(page, pageSize, 10L))
        );
    }

    public List<CommentResponse> readAllInfiniteScroll(Long articleId, String lastPath, Long pageSize) {
        List<CommentV2> comments = lastPath == null ?
                commentRepository.findAllInfiniteScroll(articleId, pageSize) :
                commentRepository.findAllInfiniteScroll(articleId, lastPath, pageSize);

        return comments.stream()
                .map(CommentResponse::from)
                .toList();
    }

    /**
     * 여러 게시글의 댓글 수를 한 번에 읽는다 (PK IN 조회 1회).
     *
     * <p>목록 조회는 게시글 30건의 댓글 수를 한꺼번에 필요로 한다. 건당 조회를 30번 하면
     * article-read -> 이 서비스로 왕복이 30번 생긴다. 그 팬아웃을 없애기 위해 추가했다.
     * 행이 없는 게시글은 0으로 채워 반환한다.
     */
    public Map<Long, Long> countAll(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> found = articleCommentCountRepository.findAllById(articleIds).stream()
                .collect(Collectors.toMap(ArticleCommentCount::getArticleId, ArticleCommentCount::getCommentCount));
        return articleIds.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), id -> found.getOrDefault(id, 0L)));
    }

    public Long count(Long articleId) {
        return articleCommentCountRepository.findById(articleId)
                .map(ArticleCommentCount::getCommentCount)
                .orElse(0L);
    }
}
