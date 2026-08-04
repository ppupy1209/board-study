package board.media.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class MediaEventPublisher {
    public static final String TOPIC = "modu-square.media.uploaded";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishUploaded(String mediaId) {
        try {
            String message = objectMapper.writeValueAsString(new MediaUploadedEvent(mediaId));
            kafkaTemplate.send(TOPIC, mediaId, message).get(5, TimeUnit.SECONDS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("이미지 처리 이벤트를 만들지 못했습니다.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("이미지 처리 이벤트를 전달하지 못했습니다.", exception);
        }
    }
}
