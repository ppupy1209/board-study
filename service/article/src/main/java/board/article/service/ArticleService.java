package board.article.service;

import board.article.entity.Article;
import board.article.entity.ArticleWriter;
import board.article.entity.BoardArticleCount;
import board.article.entity.WriterType;
import board.article.repository.ArticleRepository;
import board.article.repository.ArticleWriterRepository;
import board.article.repository.BoardArticleCountRepository;
import board.article.service.request.ArticleCreateRequest;
import board.article.service.request.ArticleUpdateRequest;
import board.article.service.response.ArticlePageResponse;
import board.article.service.response.ArticleResponse;
import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.ArticleDeletedEventPayload;
import board.common.event.payload.ArticleUpdatedEventPayload;
import board.common.event.payload.EventType;
import board.common.outboxmessagerelay.OutboxEventPublisher;
import board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {
    private final Snowflake snowflake = new Snowflake();
    private final ArticleRepository articleRepository;
    private final ArticleWriterRepository articleWriterRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public ArticleResponse create(
            ArticleCreateRequest request,
            Long writerId,
            WriterType writerType,
            String writerNickname
    ) {
        Article article = articleRepository.save(
                Article.create(
                        snowflake.nextId(),
                        request.getTitle(),
                        request.getContent(),
                        request.getBoardId(),
                        writerId
                )
        );
        if (writerType == WriterType.MEMBER) {
            articleWriterRepository.save(ArticleWriter.member(article.getArticleId(), writerNickname));
        }
        boardArticleCountRepository.increaseOrCreate(request.getBoardId());

        outboxEventPublisher.publish(
                EventType.ARTICLE_CREATED,
                ArticleCreatedEventPayload.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .content(article.getContent())
                        .boardId(article.getBoardId())
                        .writerId(article.getWriterId())
                        .writerType(writerType.name())
                        .writerNickname(writerNickname)
                        .createdAt(article.getCreatedAt())
                        .modifiedAt(article.getModifiedAt())
                        .boardArticleCount(count(article.getBoardId()))
                        .build(),
                article.getBoardId()
        );

        return ArticleResponse.from(article, writerType, writerNickname);
    }

    @Transactional
    public ArticleResponse update(Long articleId, ArticleUpdateRequest request) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        ArticleWriter writer = articleWriterRepository.findById(articleId).orElse(null);
        article.update(request.getTitle(), request.getContent());

        outboxEventPublisher.publish(
                EventType.ARTICLE_UPDATED,
                ArticleUpdatedEventPayload.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .content(article.getContent())
                        .boardId(article.getBoardId())
                        .writerId(article.getWriterId())
                        .writerType(writer == null ? null : writer.getWriterType().name())
                        .writerNickname(writer == null ? null : writer.getWriterNickname())
                        .createdAt(article.getCreatedAt())
                        .modifiedAt(article.getModifiedAt())
                        .build(),
                article.getBoardId()
        );
        return ArticleResponse.from(article, writer);
    }

    public ArticleResponse read(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ArticleNotFoundException(articleId));
        return ArticleResponse.from(article, articleWriterRepository.findById(articleId).orElse(null));
    }

    @Transactional
    public void delete(Long articleId) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        articleRepository.delete(article);
        boardArticleCountRepository.decrease(article.getBoardId());

        outboxEventPublisher.publish(
                EventType.ARTICLE_DELETED,
                ArticleDeletedEventPayload.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .content(article.getContent())
                        .boardId(article.getBoardId())
                        .writerId(article.getWriterId())
                        .createdAt(article.getCreatedAt())
                        .modifiedAt(article.getModifiedAt())
                        .build(),
                article.getBoardId()
        );
    }

    public ArticlePageResponse readAll(Long boardId, Long page, Long pageSize) {
        List<Article> articles = articleRepository.findAll(boardId, (page - 1) * pageSize, pageSize);
        return ArticlePageResponse.of(
                toResponses(articles),
                articleRepository.count(
                        boardId,
                        PageLimitCalculator.calculatePageLimit(page, pageSize, 10L)
                )
        );
    }

    public List<ArticleResponse> readAllInfiniteScroll(Long boardId, Long pageSize, Long lastArticleId) {
        List<Article> articles = lastArticleId == null ?
                articleRepository.findAllInfiniteScroll(boardId, pageSize) :
                articleRepository.findAllInfiniteScroll(boardId, pageSize, lastArticleId);
        return toResponses(articles);
    }

    public Long count(Long boardId) {
        return boardArticleCountRepository.findById(boardId)
                .map(BoardArticleCount::getArticleCount)
                .orElse(0L);
    }

    private List<ArticleResponse> toResponses(List<Article> articles) {
        Map<Long, ArticleWriter> writers = articleWriterRepository.findAllById(
                        articles.stream().map(Article::getArticleId).toList()
                ).stream()
                .collect(Collectors.toMap(ArticleWriter::getArticleId, Function.identity()));
        return articles.stream()
                .map(article -> ArticleResponse.from(article, writers.get(article.getArticleId())))
                .toList();
    }
}
