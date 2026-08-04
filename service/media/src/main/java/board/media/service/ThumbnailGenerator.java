package board.media.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Component
public class ThumbnailGenerator {
    private static final int MAX_WIDTH = 1600;
    private static final int MAX_HEIGHT = 1200;
    private static final double WEBP_QUALITY = 0.78;

    public ThumbnailResult generate(byte[] original) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
            }

            double scale = Math.min(
                    1.0,
                    Math.min(
                            (double) MAX_WIDTH / source.getWidth(),
                            (double) MAX_HEIGHT / source.getHeight()
                    )
            );
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                Thumbnails.of(source)
                        .size(width, height)
                        .outputFormat("webp")
                        .outputQuality(WEBP_QUALITY)
                        .toOutputStream(output);
                return new ThumbnailResult(output.toByteArray(), width, height);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("WebP 썸네일을 만들지 못했습니다.", exception);
        }
    }
}
