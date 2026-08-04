package board.media.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ObjectStorage {
    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final MinioClient internalClient;
    private final MinioClient publicClient;
    private final String bucket;
    private final String publicEndpoint;

    public ObjectStorage(
            @Qualifier("internalMinioClient") MinioClient internalClient,
            @Qualifier("publicMinioClient") MinioClient publicClient,
            @Value("${media.storage.bucket}") String bucket,
            @Value("${media.storage.public-endpoint}") String publicEndpoint
    ) {
        this.internalClient = internalClient;
        this.publicClient = publicClient;
        this.bucket = bucket;
        this.publicEndpoint = publicEndpoint.replaceAll("/+$", "");
    }

    public String presignUpload(String objectKey, int expiryMinutes) {
        try {
            return publicClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("업로드 URL을 만들지 못했습니다.", exception);
        }
    }

    public void upload(String objectKey, String contentType, byte[] bytes) {
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            internalClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .contentType(contentType)
                            .headers(Map.of("Cache-Control", IMMUTABLE_CACHE_CONTROL))
                            .stream(input, bytes.length, -1)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("이미지를 저장하지 못했습니다.", exception);
        }
    }

    public byte[] read(String objectKey) {
        try (InputStream input = internalClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build()
        )) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("원본 이미지를 읽지 못했습니다.", exception);
        }
    }

    public ObjectMetadata stat(String objectKey) {
        try {
            StatObjectResponse response = internalClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );
            return new ObjectMetadata(response.size(), response.contentType());
        } catch (Exception exception) {
            throw new IllegalStateException("업로드된 이미지를 확인하지 못했습니다.", exception);
        }
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            internalClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("이미지를 삭제하지 못했습니다.", exception);
        }
    }

    public String publicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return URI.create(publicEndpoint + "/" + bucket + "/" + objectKey).toASCIIString();
    }

    public String immutableCacheControl() {
        return IMMUTABLE_CACHE_CONTROL;
    }
}
