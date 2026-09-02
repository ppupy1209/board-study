package board.article.repository;

import board.article.entity.ArticleWriter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleWriterRepository extends JpaRepository<ArticleWriter, Long> {
}
