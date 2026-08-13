package board.search.repository;

import board.search.document.ArticleDocument;
import board.search.service.SearchMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class ElasticsearchArticleRepository {
    static final String INDEX_NAME = "modu-square-articles";
    private static final String META_INDEX_NAME = "modu-square-search-meta";

    private final RestClient elasticsearch;
    private final ObjectMapper objectMapper;
    private final SearchMetrics metrics;

    public ElasticsearchArticleRepository(
            @Qualifier("elasticsearchRestClient") RestClient elasticsearch,
            ObjectMapper objectMapper,
            SearchMetrics metrics
    ) {
        this.elasticsearch = elasticsearch;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @PostConstruct
    public void initialize() {
        ensureArticleIndex();
        ensureMetaIndex();
    }

    public void ensureArticleIndex() {
        if (exists(INDEX_NAME)) {
            return;
        }
        String body = """
                {
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0,
                    "refresh_interval": "5s",
                    "analysis": {
                      "analyzer": {
                        "korean": {
                          "type": "custom",
                          "tokenizer": "korean_nori",
                          "filter": ["lowercase"]
                        }
                      },
                      "tokenizer": {
                        "korean_nori": {
                          "type": "nori_tokenizer",
                          "decompound_mode": "mixed"
                        }
                      }
                    }
                  },
                  "mappings": {
                    "dynamic": "strict",
                    "properties": {
                      "articleId": { "type": "long" },
                      "title": { "type": "text", "analyzer": "korean" },
                      "content": { "type": "text", "analyzer": "korean" },
                      "boardId": { "type": "long" },
                      "writerId": { "type": "long" },
                      "createdAt": { "type": "date", "format": "strict_date_optional_time||yyyy-MM-dd'T'HH:mm:ss.SSSSSS" },
                      "modifiedAt": { "type": "date", "format": "strict_date_optional_time||yyyy-MM-dd'T'HH:mm:ss.SSSSSS" }
                    }
                  }
                }
                """;
        elasticsearch.put().uri("/" + INDEX_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void recreateArticleIndex() {
        if (exists(INDEX_NAME)) {
            elasticsearch.delete().uri("/" + INDEX_NAME).retrieve().toBodilessEntity();
        }
        ensureArticleIndex();
    }

    public void index(ArticleDocument article) {
        try {
            elasticsearch.put()
                    .uri("/{index}/_doc/{id}", INDEX_NAME, article.getArticleId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(article)
                    .retrieve()
                    .toBodilessEntity();
            metrics.recordIndex("upsert", "success", 1);
        } catch (RuntimeException exception) {
            metrics.recordIndex("upsert", "failed", 1);
            throw exception;
        }
    }

    public void delete(Long articleId) {
        try {
            elasticsearch.delete()
                    .uri("/{index}/_doc/{id}", INDEX_NAME, articleId)
                    .retrieve()
                    .toBodilessEntity();
            metrics.recordIndex("delete", "success", 1);
        } catch (HttpClientErrorException.NotFound ignored) {
            metrics.recordIndex("delete", "success", 1);
        } catch (RuntimeException exception) {
            metrics.recordIndex("delete", "failed", 1);
            throw exception;
        }
    }

    public void bulkIndex(List<ArticleDocument> articles) {
        if (articles.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder(articles.size() * 1200);
        try {
            for (ArticleDocument article : articles) {
                body.append(objectMapper.writeValueAsString(Map.of(
                        "index", Map.of("_index", INDEX_NAME, "_id", article.getArticleId())
                ))).append('\n');
                body.append(objectMapper.writeValueAsString(article)).append('\n');
            }
            JsonNode response = elasticsearch.post()
                    .uri("/_bulk")
                    // StringHttpMessageConverter의 application/* 기본 charset은 ISO-8859-1이다.
                    // charset을 생략하면 한국어가 '?'로 치환된 채 색인되므로 UTF-8을 명시한다.
                    .contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                    .body(body.toString())
                    .retrieve()
                    .body(JsonNode.class);
            if (response != null && response.path("errors").asBoolean()) {
                throw new IllegalStateException("Elasticsearch bulk indexing failed: " + firstBulkError(response));
            }
            metrics.recordIndex("bulk_upsert", "success", articles.size());
        } catch (JsonProcessingException exception) {
            metrics.recordIndex("bulk_upsert", "failed", articles.size());
            throw new IllegalStateException("검색 문서를 직렬화하지 못했습니다.", exception);
        } catch (RuntimeException exception) {
            metrics.recordIndex("bulk_upsert", "failed", articles.size());
            throw exception;
        }
    }

    public List<ArticleDocument> search(Long boardId, String query, int limit) {
        return metrics.recordQuery(() -> {
            Map<String, Object> request = Map.of(
                    "size", limit,
                    "track_total_hits", false,
                    "query", Map.of("bool", Map.of(
                            "filter", List.of(Map.of("term", Map.of("boardId", boardId))),
                            "must", List.of(Map.of("multi_match", Map.of(
                                    "query", query,
                                    "fields", List.of("title^2", "content"),
                                    "operator", "and"
                            )))
                    )),
                    "sort", List.of("_score", Map.of("articleId", "desc"))
            );
            JsonNode response = elasticsearch.post()
                    .uri("/{index}/_search", INDEX_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                return List.of();
            }
            metrics.recordBackendTook(response.path("took").asLong());
            List<ArticleDocument> results = new ArrayList<>();
            for (JsonNode hit : response.path("hits").path("hits")) {
                results.add(objectMapper.convertValue(hit.path("_source"), ArticleDocument.class));
            }
            return results;
        });
    }

    public void prepareBulkLoad() {
        updateRefreshInterval("-1");
    }

    public void finishBulkLoad() {
        updateRefreshInterval("5s");
        elasticsearch.post().uri("/{index}/_refresh", INDEX_NAME).retrieve().toBodilessEntity();
    }

    public ReindexCheckpoint readCheckpoint() {
        try {
            JsonNode response = elasticsearch.get()
                    .uri("/{index}/_doc/article-reindex", META_INDEX_NAME)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode source = response == null ? null : response.path("_source");
            if (source == null || source.isMissingNode()) {
                return ReindexCheckpoint.empty();
            }
            Long lastArticleId = source.path("lastArticleId").isNull()
                    ? null : source.path("lastArticleId").asLong();
            return new ReindexCheckpoint(lastArticleId, source.path("indexedCount").asLong());
        } catch (HttpClientErrorException.NotFound ignored) {
            return ReindexCheckpoint.empty();
        }
    }

    public void saveCheckpoint(ReindexCheckpoint checkpoint) {
        elasticsearch.put()
                .uri("/{index}/_doc/article-reindex", META_INDEX_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .body(checkpoint)
                .retrieve()
                .toBodilessEntity();
    }

    public void clearCheckpoint() {
        try {
            elasticsearch.delete()
                    .uri("/{index}/_doc/article-reindex", META_INDEX_NAME)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // 이미 비어 있으면 초기 상태와 같다.
        }
    }

    public long documentCount() {
        JsonNode response = elasticsearch.get()
                .uri("/{index}/_count", INDEX_NAME)
                .retrieve()
                .body(JsonNode.class);
        return response == null ? 0 : response.path("count").asLong();
    }

    private boolean exists(String index) {
        try {
            elasticsearch.get().uri("/" + index).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound ignored) {
            return false;
        }
    }

    private void ensureMetaIndex() {
        if (!exists(META_INDEX_NAME)) {
            elasticsearch.put().uri("/" + META_INDEX_NAME)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0)))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private void updateRefreshInterval(String interval) {
        elasticsearch.put().uri("/{index}/_settings", INDEX_NAME)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("index", Map.of("refresh_interval", interval)))
                .retrieve()
                .toBodilessEntity();
    }

    private String firstBulkError(JsonNode response) {
        for (JsonNode item : response.path("items")) {
            JsonNode error = item.path("index").path("error");
            if (!error.isMissingNode()) {
                return error.toString();
            }
        }
        return "unknown";
    }

    public record ReindexCheckpoint(Long lastArticleId, long indexedCount) {
        static ReindexCheckpoint empty() {
            return new ReindexCheckpoint(null, 0);
        }
    }
}
