package board.media.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PresignUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @Positive long sizeBytes
) {
}
