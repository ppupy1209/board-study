package board.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ElasticsearchConfig {

    @Bean("elasticsearchRestClient")
    RestClient elasticsearchRestClient(@Value("${search.elasticsearch-url}") String elasticsearchUrl) {
        return RestClient.builder().baseUrl(elasticsearchUrl).build();
    }

    @Bean("articleRestClient")
    RestClient articleRestClient(@Value("${search.article-service-url}") String articleServiceUrl) {
        return RestClient.builder().baseUrl(articleServiceUrl).build();
    }
}
