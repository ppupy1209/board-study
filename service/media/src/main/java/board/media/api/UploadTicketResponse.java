package board.media.api;

import java.time.Instant;
import java.util.Map;

public record UploadTicketResponse(
        String mediaId,
        String uploadUrl,
        Map<String, String> headers,
        Instant expiresAt
) {
}
