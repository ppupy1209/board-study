package board.comment.service.response;

import board.comment.entity.Comment;
import board.comment.entity.CommentV2;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
public class CommentResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long commentId;
    private String content;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentCommentId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long writerId;
    private Boolean deleted;
    private String path;
    private LocalDateTime createdAt;

    public static CommentResponse from(Comment comment) {
        CommentResponse response = new CommentResponse();
        response.commentId = comment.getCommentId();
        response.content = comment.getContent();
        response.parentCommentId = comment.getParentCommentId();
        response.articleId = comment.getArticleId();
        response.writerId = comment.getWriterId();
        response.deleted = comment.getDeleted();
        response.createdAt = comment.getCreatedAt();
        return response;
    }

    public static CommentResponse from(CommentV2 comment) {
        CommentResponse response = new CommentResponse();
        response.commentId = comment.getCommentId();
        response.content = comment.getContent();
        response.path = comment.getCommentPath().getPath();
        response.articleId = comment.getArticleId();
        response.writerId = comment.getWriterId();
        response.deleted = comment.getDeleted();
        response.createdAt = comment.getCreatedAt();
        return response;
    }
}
