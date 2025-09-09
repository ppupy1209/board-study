package board.comment.api;

import board.comment.service.response.CommentPageResponse;
import board.comment.service.response.CommentResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;

public class CommentApiV2Test {
    RestClient restClient = RestClient.create("http://localhost:9001");

    @Test
    void create() {
        CommentResponse response1 = create(new CommentCreateRequestV2(1L, "my comment1", null, 1L));
        CommentResponse response2 = create(new CommentCreateRequestV2(1L, "my comment1", response1.getPath(), 1L));
        CommentResponse response3 = create(new CommentCreateRequestV2(1L, "my comment1", response2.getPath(), 1L));

        System.out.println("response1.getCommentId() = " + response1.getPath());
        System.out.println("response1.getCommentId() = " + response1.getCommentId());
        System.out.println("\tresponse2.getCommentId() = " + response2.getPath());
        System.out.println("\tresponse2.getCommentId() = " + response2.getCommentId());
        System.out.println("\t\tresponse3.getCommentId() = " + response3.getPath());
        System.out.println("\t\tresponse3.getCommentId() = " + response3.getCommentId());

        // response1.getCommentId() = 00002
        //response1.getCommentId() = 223420442187292672
        //	response2.getCommentId() = 0000200000
        //	response2.getCommentId() = 223420442325704704
        //		response3.getCommentId() = 000020000000000
        //		response3.getCommentId() = 223420442384424960
    }

    CommentResponse create(CommentCreateRequestV2 request) {
        return restClient.post()
                .uri("/v2/comments")
                .body(request)
                .retrieve()
                .body(CommentResponse.class);
    }

    @Getter
    @AllArgsConstructor
    public static class CommentCreateRequestV2 {
        private Long articleId;
        private String content;
        private String parentPath;
        private Long writerId;
    }

    @Test
    void read() {
        CommentResponse response = restClient.get()
                .uri("/v2/comments/{commentId}", 223420442187292672L)
                .retrieve()
                .body(CommentResponse.class);

        System.out.println("response = " + response);
    }

    @Test
    void delete() {
        ResponseEntity<Void> response = restClient.delete()
                .uri("/v2/comments/{commentId}", 223420442187292672L)
                .retrieve()
                .toBodilessEntity();
        System.out.println("response.getStatusCode() = " + response.getStatusCode());
    }
    
    @Test
    void readAll() {
        CommentPageResponse response = restClient.get()
                .uri("/v2/comments?articleId=1&pageSize=10&page=1")
                .retrieve()
                .body(CommentPageResponse.class);

        System.out.println("response.getCommentCount() = " + response.getCommentCount());
        for (CommentResponse comment : response.getComments()) {
            System.out.println("comment.getCommentId() = " + comment.getCommentId());
        }

        /**
         * comment.getCommentId() = 223418968652800000
         * comment.getCommentId() = 223418969080619008
         * comment.getCommentId() = 223418969114173440
         * comment.getCommentId() = 223419163163648000
         * comment.getCommentId() = 223419163310448640
         * comment.getCommentId() = 223419163369168896
         * comment.getCommentId() = 223420442187292672
         * comment.getCommentId() = 223420442325704704
         * comment.getCommentId() = 223420442384424960
         * comment.getCommentId() = 223422660062138377
         */
    }

    @Test
    void readALlInfiniteScroll() {
        List<CommentResponse> responses1 = restClient.get()
                .uri("/v2/comments/infinite-scroll?articleId=1&pageSize=5")
                .retrieve()
                .body(new ParameterizedTypeReference<List<CommentResponse>>() {
                });

        System.out.println("first page");
        for (CommentResponse response : responses1) {
            System.out.println("response.getCommentId() = " + response.getCommentId());
        }

        System.out.println("second page");
        String lastPath = responses1.getLast().getPath();
        List<CommentResponse> responses2 = restClient.get()
                .uri("/v2/comments/infinite-scroll?articleId=1&pageSize=5&lastPath=%s".formatted(lastPath))
                .retrieve()
                .body(new ParameterizedTypeReference<List<CommentResponse>>() {
                });
        for (CommentResponse response : responses2) {
            System.out.println("response.getCommentId() = " + response.getCommentId());
        }
    }
}
