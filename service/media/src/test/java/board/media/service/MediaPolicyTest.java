package board.media.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaPolicyTest {
    private final MediaPolicy mediaPolicy = new MediaPolicy();

    @Test
    void 이미지_형식과_크기를_검증한다() {
        mediaPolicy.validate("photo.png", "image/png", 1024);

        assertThatThrownBy(() ->
                mediaPolicy.validate("video.mp4", "video/mp4", 1024)
        ).isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() ->
                mediaPolicy.validate("large.png", "image/png", MediaPolicy.MAX_FILE_SIZE + 1)
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 파일명에서_경로와_제어문자를_제거한다() {
        assertThat(mediaPolicy.safeFileName("../folder/photo.png"))
                .isEqualTo("photo.png");
    }

    @Test
    void 콘텐츠_타입에_맞는_확장자로_저장_키를_만든다() {
        assertThat(mediaPolicy.newOriginalKey("image/jpeg"))
                .startsWith("originals/")
                .endsWith(".jpg");
    }
}
