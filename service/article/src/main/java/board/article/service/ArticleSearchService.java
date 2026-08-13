package board.article.service;

import board.article.entity.Article;
import board.article.repository.ArticleRepository;
import board.article.service.response.ArticleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleSearchService {
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_RESULT_SIZE = 100;

    private final ArticleRepository articleRepository;
    private final ArticleSearchMetrics metrics;

    @Transactional(readOnly = true)
    public List<ArticleResponse> searchByLike(Long boardId, String query, int limit) {
        String normalized = normalize(query);
        int normalizedLimit = normalizeLimit(limit);
        String pattern = '%' + escapeLike(normalized) + '%';
        return metrics.record("like", () -> toResponses(
                articleRepository.searchByLike(boardId, pattern, normalizedLimit)
        ));
    }

    private String normalize(String query) {
        if (query == null) {
            throw new InvalidSearchRequestException("검색어는 필수입니다.");
        }
        String normalized = query.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_QUERY_LENGTH || normalized.length() > MAX_QUERY_LENGTH) {
            throw new InvalidSearchRequestException("검색어는 2자 이상 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private int normalizeLimit(int limit) {
        if (limit < 1 || limit > MAX_RESULT_SIZE) {
            throw new InvalidSearchRequestException("limit은 1 이상 100 이하여야 합니다.");
        }
        return limit;
    }

    private String escapeLike(String query) {
        return query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private List<ArticleResponse> toResponses(List<Article> articles) {
        return articles.stream().map(ArticleResponse::from).toList();
    }
}
