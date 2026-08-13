package board.article.service;

import board.article.entity.Article;
import board.article.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleSearchServiceTest {
    private ArticleRepository repository;
    private ArticleSearchService service;

    @BeforeEach
    void setUp() {
        repository = mock(ArticleRepository.class);
        ArticleSearchMetrics metrics = mock(ArticleSearchMetrics.class);
        when(metrics.record(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<List<?>>>getArgument(1).get());
        service = new ArticleSearchService(repository, metrics);
    }

    @Test
    void likeWildcardCharactersAreEscaped() {
        when(repository.searchByLike(1L, "%100!%!!완료!_%", 20)).thenReturn(List.of());

        service.searchByLike(1L, "100%!완료_", 20);

        verify(repository).searchByLike(1L, "%100!%!!완료!_%", 20);
    }

    @Test
    void rejectsOneCharacterQueryAndOversizedLimit() {
        assertThrows(InvalidSearchRequestException.class, () -> service.searchByLike(1L, "가", 20));
        assertThrows(InvalidSearchRequestException.class, () -> service.searchByLike(1L, "검색", 101));
    }
}
