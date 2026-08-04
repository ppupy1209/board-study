package board.media.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AttachMediaRequest(
        @NotEmpty List<@NotNull String> mediaIds
) {
}
