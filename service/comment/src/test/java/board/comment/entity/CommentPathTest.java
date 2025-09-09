package board.comment.entity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class CommentPathTest {

    @Test
    void createChildCommentTest() {
        createChildCommentTest(CommentPath.create(""), null, "00000");

        createChildCommentTest(CommentPath.create("00000"), null, "0000000000");

        createChildCommentTest(CommentPath.create(""),"00000","00001");

        createChildCommentTest(CommentPath.create("0000z"),"0000zabcdzzzzzzzzzzz","0000zabce0");
    }

    void createChildCommentTest(CommentPath commentPath, String descendantTop, String expectedChildPath) {
        CommentPath childCommentPath = commentPath.createChildCommentPath(descendantTop);
        assertThat(childCommentPath.getPath()).isEqualTo(expectedChildPath);
    }

    @Test
    void createChildCommentPathIfMaxDepthTest() {
        assertThatThrownBy(() ->
                CommentPath.create("zzzzz".repeat(5)).createChildCommentPath(null)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createChildCommentPathIfChunkOverflowTest() {
        CommentPath commentPath = CommentPath.create("");

        assertThatThrownBy(() -> commentPath.createChildCommentPath("zzzzz"))
                .isInstanceOf(IllegalStateException.class);
    }
}