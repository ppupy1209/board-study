package board.search.service;

import board.search.document.ArticleDocument;
import board.search.repository.ElasticsearchArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleSearchService {
    private final ElasticsearchArticleRepository repository;

    public List<ArticleDocument> search(Long boardId, String query, int limit) {
        String normalized = normalize(query);
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit은 1 이상 100 이하여야 합니다.");
        }
        return repository.search(boardId, normalized, limit);
    }

    private String normalize(String query) {
        if (query == null) {
            throw new IllegalArgumentException("검색어는 필수입니다.");
        }
        String normalized = query.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new IllegalArgumentException("검색어는 2자 이상 100자 이하여야 합니다.");
        }
        return normalized;
    }
}
