#!/usr/bin/env bash
set -euo pipefail

export MYSQL_PWD="${MYSQL_PASSWORD:-root}"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
TARGET="${SEED_ARTICLE_COUNT:-15000000}"
BATCH_SIZE="${SEED_BATCH_SIZE:-100000}"
SEED_NAME="free-board-15m"

if (( BATCH_SIZE > 100000 )); then
  echo "SEED_BATCH_SIZE must be 100000 or lower"
  exit 1
fi

mysql_cmd=(mysql --protocol=tcp -h "$MYSQL_HOST" -u "$MYSQL_USER" --default-character-set=utf8mb4)

"${mysql_cmd[@]}" -e "
  INSERT INTO article.seed_progress (seed_name, seeded_count, target_count, updated_at)
  VALUES ('${SEED_NAME}', 0, ${TARGET}, NOW())
  ON DUPLICATE KEY UPDATE target_count=VALUES(target_count), updated_at=NOW();
"

current="$("${mysql_cmd[@]}" -Nse "SELECT seeded_count FROM article.seed_progress WHERE seed_name='${SEED_NAME}'")"
current="${current:-0}"

"${mysql_cmd[@]}" -e "UPDATE article.seed_progress SET target_count=${TARGET}, updated_at=NOW() WHERE seed_name='${SEED_NAME}'"

echo "모두의 광장 자유게시판 dataset seed: ${current}/${TARGET}"

while (( current < TARGET )); do
  remaining=$((TARGET - current))
  batch=$BATCH_SIZE
  if (( remaining < batch )); then batch=$remaining; fi
  next=$((current + batch))

  "${mysql_cmd[@]}" -e "
    START TRANSACTION;
    INSERT IGNORE INTO article.article
      (article_id, title, content, board_id, writer_id, created_at, modified_at)
    SELECT
      7000000000000000000 + ${current} + n,
      CONCAT(
        CASE MOD(${current} + n, 8)
          WHEN 0 THEN '오늘 하루, 다들 어떻게 보내셨나요? #'
          WHEN 1 THEN '개발하면서 새로 배운 것을 나눠요 #'
          WHEN 2 THEN '주말에 가기 좋은 여행지를 추천해요 #'
          WHEN 3 THEN '요즘 빠져 있는 취미가 궁금해요 #'
          WHEN 4 THEN '커리어 고민을 함께 이야기해요 #'
          WHEN 5 THEN '읽고 있는 책과 문장을 소개해요 #'
          WHEN 6 THEN '오늘의 소소한 질문을 남겨요 #'
          ELSE '반복해서 듣는 음악을 추천해요 #'
        END,
        ${current} + n + 1
      ),
      CONCAT('자유게시판의 1,500만 건 목록 조회와 이벤트 처리 검증을 위한 더미 글입니다. topic=', MOD(${current} + n, 8) + 1, ', sequence=', ${current} + n + 1),
      1,
      MOD(${current} + n, 100000) + 1,
      TIMESTAMPADD(SECOND, -MOD(${current} + n, 2592000), NOW()),
      TIMESTAMPADD(SECOND, -MOD(${current} + n, 2592000), NOW())
    FROM (
      SELECT d0.d + d1.d*10 + d2.d*100 + d3.d*1000 + d4.d*10000 AS n
      FROM
        (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
      CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
      CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
      CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
      CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
    ) digits
    WHERE n < ${batch};
    UPDATE article.seed_progress
      SET seeded_count=${next}, target_count=${TARGET}, updated_at=NOW()
      WHERE seed_name='${SEED_NAME}';
    COMMIT;
  "

  current=$next
  percent=$((current * 100 / TARGET))
  echo "모두의 광장 자유게시판 dataset seed: ${current}/${TARGET} (${percent}%)"
done

"${mysql_cmd[@]}" -e "
  INSERT INTO article.board_article_count (board_id, article_count)
  SELECT 1, COUNT(*) FROM article.article WHERE board_id=1
  ON DUPLICATE KEY UPDATE article_count=VALUES(article_count);
  ANALYZE TABLE article.article;
"

echo "모두의 광장 자유게시판 dataset ready: ${TARGET} scale records plus 100 demo records"
