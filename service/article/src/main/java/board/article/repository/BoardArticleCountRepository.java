package board.article.repository;

import board.article.entity.BoardArticleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BoardArticleCountRepository extends JpaRepository<BoardArticleCount, Long> {

    /**
     * 게시글 수를 1 늘리되, 행이 없으면 새로 만든다.
     *
     * <p>"UPDATE 해보고 0건이면 INSERT" 방식은 아직 행이 없는 board에 동시 요청이 몰릴 때 deadlock을 일으킨다.
     * 실패한 UPDATE가 gap lock을 잡은 채로 여러 트랜잭션이 같은 key를 INSERT하려 하면서
     * insert intention lock이 서로 충돌하기 때문이다. 한 문장으로 원자적으로 처리해 그 경합을 없앤다.
     */
    @Query(
            value = "insert into board_article_count (board_id, article_count) values (:boardId, 1) " +
                    "on duplicate key update article_count = article_count + 1",
            nativeQuery = true
    )
    @Modifying
    int increaseOrCreate(@Param("boardId") Long boardId);

    @Query(
            value = "update board_article_count set article_count = article_count - 1 where board_id = :boardId",
            nativeQuery = true
    )
    @Modifying
    int decrease(@Param("boardId") Long boardId);
}
