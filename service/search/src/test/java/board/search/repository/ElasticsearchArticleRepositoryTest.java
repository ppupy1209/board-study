package board.search.repository;

import board.search.document.ArticleDocument;
import board.search.service.SearchMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ElasticsearchArticleRepositoryTest {

    @Test
    void bulkRequestKeepsKoreanTextAsUtf8() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://elasticsearch");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ElasticsearchArticleRepository repository = new ElasticsearchArticleRepository(
                builder.build(), new ObjectMapper(), mock(SearchMetrics.class)
        );

        server.expect(once(), requestTo("http://elasticsearch/_bulk"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/x-ndjson;charset=UTF-8"))
                .andExpect(content().string(containsString("묶음 알림")))
                .andRespond(withSuccess("{\"errors\":false}", MediaType.APPLICATION_JSON));

        repository.bulkIndex(List.of(ArticleDocument.builder()
                .articleId(1L)
                .title("묶음 알림")
                .content("한국어 본문")
                .boardId(1L)
                .writerId(1L)
                .createdAt("2026-08-13T00:00:00")
                .modifiedAt("2026-08-13T00:00:00")
                .build()));

        server.verify();
    }
}
