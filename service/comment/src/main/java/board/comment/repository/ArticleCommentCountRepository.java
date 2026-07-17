package board.comment.repository;

import board.comment.entity.ArticleCommentCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleCommentCountRepository extends JpaRepository<ArticleCommentCount, Long> {
    /**
     * 댓글 수를 1 늘리되, 행이 없으면 새로 만든다.
     *
     * <p>"UPDATE 해보고 0건이면 INSERT" 방식은 아직 행이 없는 게시글에 동시 댓글이 들어올 때
     * gap lock 경합으로 deadlock을 일으킨다. 한 문장으로 원자화해 그 경합을 없앤다.
     *
     * <p>부수 효과가 하나 더 있고, 그게 중요하다. 이 문장은 (article_id) 행에 배타 잠금을 걸고
     * 트랜잭션이 끝날 때까지 유지한다. 그래서 이 호출을 댓글 생성 트랜잭션의 <b>맨 앞</b>에 두면
     * 같은 게시글의 댓글 생성이 여기서 직렬화되고, 뒤따르는 comment path 채번(읽고-계산하고-쓰기)이
     * 서로 경쟁하지 않는다. 잠금 범위는 게시글 단위라 다른 게시글끼리는 그대로 병렬 처리된다.
     */
    @Query(
            value = "insert into article_comment_count (article_id, comment_count) values (:articleId, 1) " +
                    "on duplicate key update comment_count = comment_count + 1",
            nativeQuery = true
    )
    @Modifying
    int increaseOrCreate(@Param("articleId") Long articleId);

    @Query(
            value = "update article_comment_count set comment_count = comment_count - 1 where article_id = :articleId",
            nativeQuery = true
    )
    @Modifying
    int decrease(@Param("articleId") Long articleId);
}
