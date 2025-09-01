package board.comment.api;


import board.comment.service.response.CommentPageResponse;
import board.comment.service.response.CommentResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

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

    @Test
    void readAll() {
        CommentPageResponse response = restClient.get()
                .uri("/v1/comments?articleId=1&&page=1&pageSize=10")
                .retrieve()
                .body(CommentPageResponse.class);
        System.out.println("response.getCommentCount() = " + response.getCommentCount());

        for (CommentResponse comment : response.getComments()) {
            if (!comment.getCommentId().equals(comment.getParentCommentId())) {
                System.out.print("\t");
            }
            System.out.println("comment.getCommentId() = " + comment.getCommentId());
        }

        /**
         * comment.getCommentId() = 220003015212240896
         * 	comment.getCommentId() = 220003015237406720
         * comment.getCommentId() = 220003015212240897
         * 	comment.getCommentId() = 220003015237406724
         * comment.getCommentId() = 220003015212240898
         * 	comment.getCommentId() = 220003015237406722
         * comment.getCommentId() = 220003015212240899
         * 	comment.getCommentId() = 220003015237406727
         * comment.getCommentId() = 220003015212240900
         * 	comment.getCommentId() = 220003015237406726
         */
    }

    @Test
    void readAllInfiniteScroll() {
        List<CommentResponse> responses1 = restClient.get()
                .uri("/v1/comments/infinite-scroll?articleId=1&pageSize=5")
                .retrieve()
                .body(new ParameterizedTypeReference<List<CommentResponse>>() {
                });

        System.out.println("first");
        for (CommentResponse comment : responses1) {
            if (!comment.getCommentId().equals(comment.getParentCommentId())) {
                System.out.print("\t");
            }
            System.out.println("comment.getCommentId() = " + comment.getCommentId());
        }

        Long lastParentCommentId = responses1.getLast().getParentCommentId();
        Long lastCommentId = responses1.getLast().getCommentId();

        List<CommentResponse> responses2 = restClient.get()
                .uri("/v1/comments/infinite-scroll?articleId=1&pageSize=5&lastParentCommentId=%s&lastCommentId=%s".formatted(lastParentCommentId, lastCommentId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<CommentResponse>>() {
                });

        System.out.println("second");
        for (CommentResponse comment : responses2) {
            if (!comment.getCommentId().equals(comment.getParentCommentId())) {
                System.out.print("\t");
            }
            System.out.println("comment.getCommentId() = " + comment.getCommentId());
        }
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
