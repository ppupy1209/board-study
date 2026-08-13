package board.article.repository;

import board.article.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Query(
            value = "select article.article_id, article.title, article.content, article.board_id, article.writer_id, " +
                    "article.created_at, article.modified_at " +
                    "from (" +
                    "select article_id from article " +
                    "where board_id = :boardId " +
                    "order by article_id desc " +
                    "limit :limit offset :offset" +
                    ") t left join article on t.article_id = article.article_id ",
            nativeQuery = true
    )
    List<Article> findAll(
            @Param("boardId") Long boardId,
            @Param("offset") Long offset,
            @Param("limit") Long limit
    );

    @Query(
            value = "select count(*) " +
                    "from (" +
                    " select article_id from article " +
                    " where board_id = :boardId " +
                    " limit :limit" +
                    ") t",
            nativeQuery = true
    )
    Long count(@Param("boardId") Long boardId, @Param("limit") Long limit);

    @Query(
            value = "select article.article_id, article.title, article.content, article.board_id, article.writer_id, " +
                    "article.created_at, article.modified_at " +
                    "from article " +
                    "where board_id = :boardId " +
                    "order by article_id desc limit :limit",
            nativeQuery = true
    )
    List<Article> findAllInfiniteScroll(@Param("boardId") Long boardId, @Param("limit") Long limit);

    @Query(
            value = "select article.article_id, article.title, article.content, article.board_id, article.writer_id, " +
                    "article.created_at, article.modified_at " +
                    "from article " +
                    "where board_id = :boardId and article_id < :lastArticleId " +
                    "order by article_id desc limit :limit",
            nativeQuery = true
    )
    List<Article> findAllInfiniteScroll(
            @Param("boardId") Long boardId,
            @Param("limit") Long limit,
            @Param("lastArticleId") Long lastArticleId
            );

    /**
     * 비교 실험용 기준선. 선행 와일드카드 때문에 일반 B-Tree 인덱스로 검색어의 시작점을
     * 찾을 수 없으며, 일치 행을 찾을 때까지 후보 행의 title/content를 검사한다.
     */
    @Query(
            value = "select article_id, title, content, board_id, writer_id, created_at, modified_at " +
                    "from article " +
                    "where board_id = :boardId " +
                    "and (title like :pattern escape '!' or content like :pattern escape '!') " +
                    "order by article_id desc limit :limit",
            nativeQuery = true
    )
    List<Article> searchByLike(
            @Param("boardId") Long boardId,
            @Param("pattern") String pattern,
            @Param("limit") int limit
    );

}
