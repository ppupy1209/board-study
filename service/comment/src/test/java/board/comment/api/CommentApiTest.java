package board.comment.api;


import board.comment.service.response.CommentResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

public class CommentApiTest {
    RestClient restClient = RestClient.create("http://localhost:9001");

    @Test
    void create() {
        CommentResponse myComment1 = createComment(new CommentCreateRequest(1L, "my comment1", null, 1L));
        CommentResponse myComment2 = createComment(new CommentCreateRequest(1L, "my comment2", myComment1.getCommentId(), 1L));
        CommentResponse myComment3 = createComment(new CommentCreateRequest(1L, "my comment3", myComment1.getCommentId(), 1L));

        System.out.println("myComment1 = " + myComment1);
        System.out.println("\tmyComment2 = " + myComment2);
        System.out.println("\tmyComment3 = " + myComment3);

        //219995589876056064
        //219995590240960512
        //219995590287097856
    }

    @Test
    void read() {
        CommentResponse response = restClient.get()
                .uri("/v1/comments/{commentId}", 219995589876056064L)
                .retrieve()
                .body(CommentResponse.class);
        System.out.println("response = " + response);
    }

    @Test
    void delete() {
        restClient.delete()
                .uri("/v1/comments/{commentId}", 219995590287097856L)
                .retrieve()
                .toBodilessEntity();
    }

    CommentResponse createComment(CommentCreateRequest request) {
        return restClient.post()
                .uri("/v1/comments")
                .body(request)
                .retrieve()
                .body(CommentResponse.class);
    }


    @Getter
    @AllArgsConstructor
    static class CommentCreateRequest {
        private Long articleId;
        private String content;
        private Long parentCommentId;
        private Long writerId;
    }

}
