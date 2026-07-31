package board.hotarticle.service.response;

import board.hotarticle.repository.HotArticleQueryModel;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
public class HotArticleResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;
    private String title;
    private LocalDateTime createdAt;

    public static HotArticleResponse from(HotArticleQueryModel queryModel) {
        HotArticleResponse hotArticleResponse = new HotArticleResponse();
        hotArticleResponse.articleId = queryModel.getArticleId();
        hotArticleResponse.title = queryModel.getTitle();
        hotArticleResponse.createdAt = queryModel.getCreatedAt();
        return hotArticleResponse;
    }
}
