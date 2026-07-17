package board.articleread.client;

import board.articleread.cache.OptimizedCacheable;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewClient {
    private RestClient restClient;
    @Value("${endpoints.board-view-service.url}")
    private String viewServiceUrl;

    @PostConstruct
    public void initRestClient() {
        restClient = RestClient.create(viewServiceUrl);
    }

//    @Cacheable(key = "#articleId", value = "articleViewCount")
    @OptimizedCacheable(type = "articleViewCount", ttlSeconds = 1)
    public long count(Long articleId) {
        log.info("[ViewClient.count]: articleId={}", articleId);
        try {
            return restClient.get()
                    .uri("/v1/article-views/articles/{articleId}/count", articleId)
                    .retrieve()
                    .body(Long.class);
        } catch (Exception e) {
            log.error("[ViewClient.count] articleId = {}", articleId, e);
            return 0;
        }
    }


    /**
     * 여러 게시글의 조회수를 한 번에 가져온다.
     *
     * <p>건당 호출을 하면 목록 30건에 왕복이 30번 생긴다. 배치 endpoint로 1번에 끝낸다.
     * 실패하면 전부 0으로 채운 map을 돌려주고 목록 조회 자체는 살린다(기존 count()와 같은 정책).
     */
    public Map<Long, Long> countAll(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, Long> result = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/article-views/articles/counts")
                            .queryParam("articleIds", articleIds)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<Long, Long>>() {});
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            log.error("[ViewClient.countAll] articleIds size = {}", articleIds.size(), e);
            return Map.of();
        }
    }
}
