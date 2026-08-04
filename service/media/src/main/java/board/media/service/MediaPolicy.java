package board.media.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Component
public class MediaPolicy {
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    public static final int MAX_ARTICLE_IMAGES = 5;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    public void validate(String fileName, String contentType, long size) {
        if (fileName == null || fileName.isBlank()) {
            throw badRequest("파일 이름이 필요합니다.");
        }
        if (!EXTENSIONS.containsKey(contentType)) {
            throw badRequest("JPEG, PNG, WebP 이미지만 첨부할 수 있습니다.");
        }
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw badRequest("이미지는 파일당 10MB 이하만 첨부할 수 있습니다.");
        }
    }

    public String safeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        return name.isBlank() ? "image" : name.substring(0, Math.min(name.length(), 255));
    }

    public String newOriginalKey(String contentType) {
        LocalDate today = LocalDate.now();
        return "originals/%d/%02d/%s.%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                EXTENSIONS.get(contentType)
        );
    }

    public String thumbnailKey(String mediaId) {
        return "thumbnails/%s.webp".formatted(mediaId);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
