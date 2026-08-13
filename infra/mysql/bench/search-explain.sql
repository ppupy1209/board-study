SET NAMES utf8mb4;

-- 최악 경로: 존재하지 않는 문자열은 LIMIT을 채우지 못해 LIKE가 전체 후보를 검사한다.
EXPLAIN ANALYZE
SELECT article_id, title, content, board_id, writer_id, created_at, modified_at
FROM article.article
WHERE board_id = 1
  AND (
    title LIKE '%존재하지않는희귀검색어%'
    OR content LIKE '%존재하지않는희귀검색어%'
  )
ORDER BY article_id DESC
LIMIT 20;
