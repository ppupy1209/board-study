#!/usr/bin/env bash
set -euo pipefail

export MYSQL_PWD="${MYSQL_PASSWORD:-root}"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
REQUESTED_TARGET="${SEED_ARTICLE_COUNT:-15000000}"
TARGET="${REQUESTED_TARGET}"
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
  ON DUPLICATE KEY UPDATE
    target_count=GREATEST(target_count, VALUES(target_count)),
    updated_at=NOW();
"

current="$("${mysql_cmd[@]}" -Nse "SELECT seeded_count FROM article.seed_progress WHERE seed_name='${SEED_NAME}'")"
current="${current:-0}"

if (( current > TARGET )); then
  TARGET="${current}"
fi

"${mysql_cmd[@]}" -e "
  UPDATE article.seed_progress
  SET target_count=GREATEST(target_count, ${TARGET}), updated_at=NOW()
  WHERE seed_name='${SEED_NAME}'
"

echo "모두의 광장 자유게시판 dataset seed: ${current}/${TARGET} (requested=${REQUESTED_TARGET})"

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
      CASE MOD(${current} + n, 16)
        WHEN 0 THEN '퇴근 후 한 시간, 다들 어떻게 보내시나요?'
        WHEN 1 THEN '우리 동네에 오래 남았으면 하는 작은 가게'
        WHEN 2 THEN '여름이 끝나기 전에 가보고 싶은 여행지'
        WHEN 3 THEN '요즘 새로 시작한 취미가 있나요?'
        WHEN 4 THEN '일과 삶의 균형을 찾는 나만의 방법'
        WHEN 5 THEN '최근 마음에 오래 남은 책 한 권'
        WHEN 6 THEN '오늘 나를 웃게 만든 소소한 순간'
        WHEN 7 THEN '처음부터 끝까지 듣기 좋은 앨범을 추천해요'
        WHEN 8 THEN '혼자 걷기 좋은 서울 산책길을 나눠요'
        WHEN 9 THEN '아침을 덜 바쁘게 만드는 작은 습관'
        WHEN 10 THEN '비 오는 날 집에서 보기 좋은 영화'
        WHEN 11 THEN '냉장고 속 재료로 만든 의외의 한 끼'
        WHEN 12 THEN '오래 사용해도 질리지 않는 물건이 있나요?'
        WHEN 13 THEN '낯선 사람의 친절을 기억하는 순간'
        WHEN 14 THEN '주말에 휴대폰 없이 보낸 오후'
        ELSE '요즘 가장 자주 하는 고민은 무엇인가요?'
      END,
      CASE MOD(${current} + n, 16)
        WHEN 0 THEN '집에 도착하자마자 쉬는 날도 있고, 가볍게 산책하거나 저녁을 만들어 먹는 날도 있어요. 여러분의 평일 저녁 루틴이 궁금합니다.'
        WHEN 1 THEN '자주 가지 않아도 그 자리에 있다는 것만으로 마음이 놓이는 가게가 있나요? 동네에서 아끼는 공간을 함께 소개해 주세요.'
        WHEN 2 THEN '멀리 떠나는 여행도 좋지만 당일치기로 다녀올 수 있는 곳도 좋아요. 요즘 마음에 담아둔 여행지가 있다면 알려주세요.'
        WHEN 3 THEN '잘해야 한다는 부담 없이 천천히 즐길 수 있는 취미를 찾고 있어요. 최근 시작해서 즐겁게 이어가는 일이 있나요?'
        WHEN 4 THEN '바쁜 시기에도 나를 돌보는 시간을 놓치지 않으려 합니다. 작지만 꾸준히 지키는 원칙이 있다면 나눠주세요.'
        WHEN 5 THEN '책장을 덮은 뒤에도 한동안 생각나는 문장과 장면이 있더라고요. 최근에 읽은 책 중 오래 기억하고 싶은 한 권은 무엇인가요?'
        WHEN 6 THEN '거창한 일은 아니지만 하루를 조금 밝게 만든 순간이 있었나요? 오늘 발견한 작은 기쁨을 함께 나눠봐요.'
        WHEN 7 THEN '한 곡만 골라 듣기보다 순서대로 들을 때 더 좋은 앨범을 찾고 있습니다. 장르와 시대는 상관없어요.'
        WHEN 8 THEN '사람이 붐비지 않고 천천히 주변을 둘러볼 수 있는 길을 좋아해요. 여러분이 아끼는 산책 코스는 어디인가요?'
        WHEN 9 THEN '전날 밤 가방과 옷을 미리 준비하니 아침이 한결 여유로워졌어요. 하루를 편안하게 시작하는 방법을 알려주세요.'
        WHEN 10 THEN '따뜻한 차 한 잔과 함께 조용히 보기 좋은 영화를 찾고 있어요. 잔잔한 여운이 남는 작품이면 더욱 좋겠습니다.'
        WHEN 11 THEN '장보기 전 남은 재료를 모아 만들었는데 생각보다 훌륭했던 메뉴가 있나요? 간단한 조합도 환영합니다.'
        WHEN 12 THEN '유행과 상관없이 손이 자주 가는 물건에는 저마다 이유가 있는 것 같아요. 오래 곁에 둔 물건을 소개해 주세요.'
        WHEN 13 THEN '잠깐 건네받은 친절이 예상보다 오래 마음에 남을 때가 있어요. 여러분에게도 문득 떠오르는 순간이 있나요?'
        WHEN 14 THEN '알림을 잠시 꺼두고 책을 읽거나 동네를 걸었더니 시간이 다르게 흐르는 기분이었어요. 여러분은 어떻게 쉬고 있나요?'
        ELSE '답을 바로 찾지 못해도 누군가와 이야기하는 것만으로 생각이 정리되곤 합니다. 편하게 요즘의 고민을 들려주세요.'
      END,
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

article_count="$("${mysql_cmd[@]}" -Nse "SELECT COUNT(*) FROM article.article WHERE board_id=1")"
echo "모두의 광장 자유게시판 dataset ready: ${article_count} records in the persistent local volume"
