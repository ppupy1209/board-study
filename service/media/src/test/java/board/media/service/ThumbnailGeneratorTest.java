package board.media.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ThumbnailGeneratorTest {
    private final ThumbnailGenerator thumbnailGenerator = new ThumbnailGenerator();

    @Test
    void 큰_이미지를_비율을_유지한_WebP로_축소한다() throws Exception {
        BufferedImage source = new BufferedImage(2400, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setPaint(new Color(38, 87, 161));
        graphics.fillRect(0, 0, 2400, 1600);
        graphics.dispose();

        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(source, "png", original);

        ThumbnailResult result = thumbnailGenerator.generate(original.toByteArray());

        assertThat(result.width()).isEqualTo(1600);
        assertThat(result.height()).isEqualTo(1067);
        assertThat(new String(result.bytes(), 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("RIFF");
        assertThat(result.bytes().length).isLessThan(original.size());
    }
}
