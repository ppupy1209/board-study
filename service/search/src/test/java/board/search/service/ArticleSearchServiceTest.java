package board.search.service;

import board.search.document.ArticleDocument;
import board.search.repository.ElasticsearchArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleSearchServiceTest {
    private ElasticsearchArticleRepository repository;
    private ArticleSearchService service;

    @BeforeEach
    void setUp() {
        repository = mock(ElasticsearchArticleRepository.class);
        service = new ArticleSearchService(repository);
    }

    @Test
    void normalizesWhitespaceAndDelegatesToNoriSearch() {
        when(repository.search(1L, "서울 산책", 20)).thenReturn(List.of(new ArticleDocument()));

        assertEquals(1, service.search(1L, " 서울   산책 ", 20).size());
        verify(repository).search(1L, "서울 산책", 20);
    }

    @Test
    void rejectsInvalidQueryOrLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.search(1L, "가", 20));
        assertThrows(IllegalArgumentException.class, () -> service.search(1L, "검색", 0));
    }
}
