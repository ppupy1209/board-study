SET NAMES utf8mb4;
USE article;

CREATE TABLE IF NOT EXISTS article.schema_migration (
  migration_name VARCHAR(100) NOT NULL,
  applied_at DATETIME(6) NOT NULL,
  PRIMARY KEY (migration_name)
) ENGINE=InnoDB;

SET @apply_product_board = NOT EXISTS (
  SELECT 1
  FROM article.schema_migration
  WHERE migration_name = '04-product-board'
);

INSERT INTO article.board (board_id, name, description)
VALUES (2, '모두의 이야기', 'Docker로 실행한 Modu Square에서 실제로 사용하는 커뮤니티 게시판')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

UPDATE article.article
SET board_id = 2
WHERE @apply_product_board = 1
  AND article_id BETWEEN 340000000000000001 AND 340000000000000015;

INSERT INTO article.board_article_count (board_id, article_count)
SELECT 2, COUNT(*) FROM article.article WHERE board_id = 2
ON DUPLICATE KEY UPDATE article_count = VALUES(article_count);

INSERT INTO article.schema_migration (migration_name, applied_at)
SELECT '04-product-board', NOW()
WHERE @apply_product_board = 1;
