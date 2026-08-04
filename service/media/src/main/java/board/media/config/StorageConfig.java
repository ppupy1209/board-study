package board.media.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {
    @Bean
    @Qualifier("internalMinioClient")
    public MinioClient internalMinioClient(
            @Value("${media.storage.internal-endpoint}") String endpoint,
            @Value("${media.storage.access-key}") String accessKey,
            @Value("${media.storage.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region("us-east-1")
                .build();
    }

    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient(
            @Value("${media.storage.public-endpoint}") String endpoint,
            @Value("${media.storage.access-key}") String accessKey,
            @Value("${media.storage.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region("us-east-1")
                .build();
    }
}
