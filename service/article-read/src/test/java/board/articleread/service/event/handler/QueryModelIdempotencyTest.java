package board.articleread.service.event.handler;

import board.articleread.repository.ArticleQueryModel;
import board.articleread.repository.ArticleQueryModelRepository;
import board.common.event.Event;
import board.common.event.EventPayload;
import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.ArticleLikedEventPayload;
import board.common.event.payload.CommentCreatedEventPayload;
import board.common.event.payload.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Outbox 재시도는 Kafka 전송 성공을 확인하지 못한 경우 같은 이벤트를 다시 보낼 수 있다(at-least-once).
 * 따라서 Query Model이 같은 이벤트를 여러 번 받아도 결과가 중복 반영되지 않아야 한다.
 *
 * <p>이 프로젝트의 핸들러는 payload가 실어 보낸 <b>절대값</b>을 그대로 반영하고 증분 연산을 하지 않기 때문에
 * 별도 dedup 저장소 없이 멱등하다. 그 성질이 깨지지 않도록 테스트로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class QueryModelIdempotencyTest {

    private static final long ARTICLE_ID = 1L;

    @Mock
    private ArticleQueryModelRepository articleQueryModelRepository;

    @InjectMocks
    private ArticleLikedEventHandler articleLikedEventHandler;

    @InjectMocks
    private CommentCreatedEventHandler commentCreatedEventHandler;

    /** Event.of()는 Event&lt;EventPayload&gt;를 돌려주므로 핸들러의 구체 타입에 맞춰 캐스팅한다. */
    @SuppressWarnings("unchecked")
    private static <T extends EventPayload> Event<T> event(Long eventId, EventType type, EventPayload payload) {
        return (Event<T>) (Event<?>) Event.of(eventId, type, payload);
    }

    private ArticleQueryModel newQueryModel() {
        return ArticleQueryModel.create(
                ArticleCreatedEventPayload.builder()
                        .articleId(ARTICLE_ID)
                        .title("title")
                        .content("content")
                        .boardId(1L)
                        .writerId(1L)
                        .createdAt(LocalDateTime.now())
                        .modifiedAt(LocalDateTime.now())
                        .boardArticleCount(1L)
                        .build()
        );
    }

    @Test
    @DisplayName("같은 좋아요 이벤트를 두 번 소비해도 좋아요 수가 중복 반영되지 않는다")
    void likedEventIsIdempotent() {
        ArticleQueryModel queryModel = newQueryModel();
        when(articleQueryModelRepository.read(ARTICLE_ID)).thenReturn(Optional.of(queryModel));

        Event<ArticleLikedEventPayload> event = event(
                1L,
                EventType.ARTICLE_LIKED,
                ArticleLikedEventPayload.builder()
                        .articleLikeId(1L)
                        .articleId(ARTICLE_ID)
                        .userId(1L)
                        .createdAt(LocalDateTime.now())
                        .articleLikeCount(5L)
                        .build()
        );

        articleLikedEventHandler.handle(event);
        assertThat(queryModel.getArticleLikeCount()).isEqualTo(5L);

        // 재시도로 동일 이벤트를 다시 받는 상황
        articleLikedEventHandler.handle(event);
        assertThat(queryModel.getArticleLikeCount())
                .as("payload의 절대값을 반영하므로 두 번 적용해도 값이 그대로여야 한다")
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("같은 댓글 생성 이벤트를 두 번 소비해도 댓글 수가 중복 반영되지 않는다")
    void commentCreatedEventIsIdempotent() {
        ArticleQueryModel queryModel = newQueryModel();
        when(articleQueryModelRepository.read(ARTICLE_ID)).thenReturn(Optional.of(queryModel));

        Event<CommentCreatedEventPayload> event = event(
                2L,
                EventType.COMMENT_CREATED,
                CommentCreatedEventPayload.builder()
                        .commentId(1L)
                        .articleId(ARTICLE_ID)
                        .content("hi")
                        .writerId(1L)
                        .deleted(false)
                        .createdAt(LocalDateTime.now())
                        .articleCommentCount(3L)
                        .build()
        );

        commentCreatedEventHandler.handle(event);
        commentCreatedEventHandler.handle(event);

        assertThat(queryModel.getArticleCommentCount())
                .as("payload의 절대값을 반영하므로 두 번 적용해도 값이 그대로여야 한다")
                .isEqualTo(3L);
    }
}
