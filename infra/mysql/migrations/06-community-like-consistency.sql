SET NAMES utf8mb4;
USE article;

CREATE TABLE IF NOT EXISTS article.schema_migration (
  migration_name VARCHAR(100) NOT NULL,
  applied_at DATETIME(6) NOT NULL,
  PRIMARY KEY (migration_name)
) ENGINE=InnoDB;

SET @apply_community_like_consistency = NOT EXISTS (
  SELECT 1
  FROM article.schema_migration
  WHERE migration_name = '06-community-like-consistency'
);

-- 이전 생성 범위가 50에서 끝나 누락됐던 마지막 두 좋아요를 보완한다.
INSERT IGNORE INTO article_like.article_like
  (article_like_id, article_id, user_id, created_at)
SELECT
  5000000000000000000 + MOD(340000000000000015, 100) * 100 + numbers.n,
  340000000000000015,
  10000 + numbers.n,
  TIMESTAMPADD(MINUTE, -numbers.n, NOW())
FROM (
  SELECT 51 n UNION ALL SELECT 52
) numbers
WHERE @apply_community_like_consistency = 1
  AND EXISTS (
    SELECT 1
    FROM article.article
    WHERE article_id = 340000000000000015
  );

-- 화면에 노출되는 집계값을 실제 좋아요 행 수와 다시 맞춘다.
INSERT INTO article_like.article_like_count (article_id, like_count, version)
SELECT articles.article_id, COUNT(likes.article_like_id), 0
FROM article.article articles
LEFT JOIN article_like.article_like likes
  ON likes.article_id = articles.article_id
WHERE @apply_community_like_consistency = 1
  AND articles.article_id BETWEEN 340000000000000001 AND 340000000000000015
GROUP BY articles.article_id
ON DUPLICATE KEY UPDATE like_count = VALUES(like_count);

INSERT INTO article.schema_migration (migration_name, applied_at)
SELECT '06-community-like-consistency', NOW()
WHERE @apply_community_like_consistency = 1;
