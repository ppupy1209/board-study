package board.like.repository;

import board.like.entity.ArticleLikeCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleLikeCountRepository extends JpaRepository<ArticleLikeCount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ArticleLikeCount> findLockedByArticleId(Long articleId);

    /**
     * 좋아요 수를 1 늘리되, 행이 없으면 새로 만든다.
     *
     * <p>"UPDATE 해보고 0건이면 INSERT" 방식은 아직 행이 없는 게시글에 동시 좋아요가 들어올 때 deadlock을 일으킨다.
     * 실패한 UPDATE가 gap lock을 잡은 채로 여러 트랜잭션이 같은 gap에 INSERT하려 하면서
     * insert intention lock이 서로 충돌하기 때문이다. 한 문장으로 원자적으로 처리해 그 경합을 없앤다.
     */
    @Query(
            value = "insert into article_like_count (article_id, like_count, version) values (:articleId, 1, 0) " +
                    "on duplicate key update like_count = like_count + 1",
            nativeQuery = true
    )
    @Modifying
    int increaseOrCreate(@Param("articleId") Long articleId);

    @Query(
            value = "update article_like_count set like_count = like_count - 1 where article_id = :articleId",
            nativeQuery = true
    )
    @Modifying
    int decrease(@Param("articleId") Long articleId);


}
