SET NAMES utf8mb4;
USE article;

SET @apply_initial_comment_time = NOT EXISTS (
  SELECT 1
  FROM article.schema_migration
  WHERE migration_name = '05-initial-comment-time'
);

UPDATE comment.comment_v2
SET created_at = TIMESTAMPADD(MINUTE, -MOD(comment_id, 100), NOW())
WHERE @apply_initial_comment_time = 1
  AND article_id BETWEEN 340000000000000001 AND 340000000000000015
  AND writer_id BETWEEN 20001 AND 20010;

INSERT INTO article.schema_migration (migration_name, applied_at)
SELECT '05-initial-comment-time', NOW()
WHERE @apply_initial_comment_time = 1;
