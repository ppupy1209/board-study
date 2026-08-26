SET NAMES utf8mb4;
USE article;

CREATE TABLE IF NOT EXISTS article.schema_migration (
  migration_name VARCHAR(100) NOT NULL,
  applied_at DATETIME(6) NOT NULL,
  PRIMARY KEY (migration_name)
) ENGINE=InnoDB;

INSERT INTO article.board (board_id, name, description)
VALUES (2, '모두의 이야기', 'Docker로 실행한 Modu Square에서 실제로 사용하는 커뮤니티 게시판')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

SET @apply_community_data = NOT EXISTS (
  SELECT 1
  FROM article.schema_migration
  WHERE migration_name = '03-community-data'
);

-- 이전 버전에서 화면용으로 넣었던 100개 게시글과 연결 데이터만 정리한다.
DELETE FROM comment.comment
WHERE article_id BETWEEN 8000000000000000000 AND 8000000000000000099;
DELETE FROM comment.comment_v2
WHERE article_id BETWEEN 8000000000000000000 AND 8000000000000000099;
DELETE FROM comment.article_comment_count
WHERE article_id BETWEEN 8000000000000000000 AND 8000000000000000099;
DELETE FROM article_like.article_like
WHERE article_id BETWEEN 8000000000000000000 AND 8000000000000000099;
DELETE FROM article_like.article_like_count
WHERE article_id BETWEEN 8000000000000000000 AND 8000000000000000099;
DELETE FROM article_view.article_view_count
WHERE article_id BETWEEN 8000000000000000000 AND 8000000000000000099;
DELETE FROM article.article
WHERE article_id BETWEEN 8000000000000000000 AND 8000000000000000099;

INSERT IGNORE INTO article.article
  (article_id, title, content, board_id, writer_id, created_at, modified_at)
SELECT article_id, title, content, 2, writer_id, created_at, created_at
FROM (
  SELECT 340000000000000001 article_id, '출근 전에 20분 걷기, 생각보다 괜찮네요' title, '이번 주부터 한 정거장 먼저 내려서 걷고 있어요. 아침 공기가 아직 선선해서인지 하루를 덜 급하게 시작하는 기분이 듭니다. 출근 전에 지키는 작은 습관이 있나요?' content, 101 writer_id, TIMESTAMPADD(MINUTE, -225, NOW()) created_at
  UNION ALL SELECT 340000000000000002, '서울에서 조용히 책 읽기 좋은 곳을 찾고 있어요', '주말 오후에 두세 시간 머물 수 있고 음악이 너무 크지 않은 곳이면 좋겠습니다. 자주 가는 도서관이나 북카페가 있다면 알려주세요.', 102, TIMESTAMPADD(MINUTE, -210, NOW())
  UNION ALL SELECT 340000000000000003, '냉장고에 남은 두부로 만든 저녁 메뉴', '두부를 바삭하게 구운 뒤 간장과 식초, 쪽파를 섞은 소스를 올렸더니 간단한데도 꽤 든든했어요. 재료가 적을 때 자주 만드는 메뉴도 궁금합니다.', 103, TIMESTAMPADD(MINUTE, -195, NOW())
  UNION ALL SELECT 340000000000000004, '휴대폰 사진을 정리하다 발견한 지난여름', '사진을 날짜별로 지우다가 잊고 있던 바닷가 사진을 찾았습니다. 잘 찍힌 사진보다 그날의 소리가 떠오르는 사진을 결국 남기게 되네요.', 104, TIMESTAMPADD(MINUTE, -180, NOW())
  UNION ALL SELECT 340000000000000005, '일요일 저녁이 아쉽지 않게 보내는 방법', '다음 주 할 일을 미리 생각하면 쉬는 기분이 사라져서 저녁에는 좋아하는 음악을 틀고 천천히 요리해요. 여러분은 주말의 끝을 어떻게 보내나요?', 105, TIMESTAMPADD(MINUTE, -165, NOW())
  UNION ALL SELECT 340000000000000006, '요즘 끝까지 듣게 되는 앨범 한 장', '출퇴근길에 곡을 건너뛰지 않고 들을 수 있는 앨범을 찾고 있습니다. 장르는 상관없고, 처음 들을 때 좋았던 이유도 함께 들려주세요.', 106, TIMESTAMPADD(MINUTE, -150, NOW())
  UNION ALL SELECT 340000000000000007, '비 오는 날에도 걷기 좋은 실내 공간', '비가 계속 오는 주말에는 대형 쇼핑몰 말고 천천히 둘러볼 곳이 잘 떠오르지 않네요. 전시나 오래된 시장처럼 걷는 재미가 있는 공간을 추천받고 싶어요.', 107, TIMESTAMPADD(MINUTE, -135, NOW())
  UNION ALL SELECT 340000000000000008, '오래 쓰고 있는 물건에는 이유가 있더라고요', '십 년째 쓰는 작은 스탠드가 있습니다. 새 제품보다 편한 것도 있지만 함께 지낸 시간이 아까워서 쉽게 바꾸지 못하겠어요. 여러분 곁에도 그런 물건이 있나요?', 108, TIMESTAMPADD(MINUTE, -120, NOW())
  UNION ALL SELECT 340000000000000009, '혼자 여행할 때 가장 먼저 정하는 한 가지', '숙소보다 아침에 걸을 동네를 먼저 고릅니다. 관광지를 많이 보는 것보다 낯선 동네의 하루를 따라가 보는 여행을 좋아해요.', 109, TIMESTAMPADD(MINUTE, -105, NOW())
  UNION ALL SELECT 340000000000000010, '퇴근 후 아무것도 하지 않는 시간도 필요하네요', '계획을 세워야 잘 쉰다고 생각했는데 요즘은 소파에 앉아 창밖을 보는 시간이 오히려 오래 남습니다. 쉬는 데도 연습이 필요한 것 같아요.', 110, TIMESTAMPADD(MINUTE, -90, NOW())
  UNION ALL SELECT 340000000000000011, '작은 가게에서 받은 뜻밖의 친절', '우산 없이 비를 피하고 있었는데 가게 사장님이 남는 우산을 빌려주셨어요. 다시 돌려드리러 가는 길이 괜히 즐거웠습니다.', 111, TIMESTAMPADD(MINUTE, -75, NOW())
  UNION ALL SELECT 340000000000000012, '처음 해보는 취미를 오래 이어가는 요령', '잘하려는 마음이 앞서면 금방 지쳐서 이번에는 일주일에 한 번만 하기로 했어요. 부담 없이 취미를 이어가는 자기만의 방법이 있나요?', 112, TIMESTAMPADD(MINUTE, -60, NOW())
  UNION ALL SELECT 340000000000000013, '동네 빵집에서 꼭 고르는 메뉴가 있나요?', '새 빵집에 가면 가장 기본적인 소금빵부터 먹어봅니다. 특별한 메뉴보다 기본 메뉴에서 그 가게의 취향이 보이는 것 같아요.', 113, TIMESTAMPADD(MINUTE, -45, NOW())
  UNION ALL SELECT 340000000000000014, '오늘 하루를 조금 나아지게 한 말', '회의가 길어져 지쳐 있었는데 동료가 덕분에 정리가 잘됐다고 말해줬어요. 짧은 한마디가 생각보다 오래 힘이 되네요.', 114, TIMESTAMPADD(MINUTE, -30, NOW())
  UNION ALL SELECT 340000000000000015, '이번 주말에 가볍게 다녀올 곳을 추천해주세요', '멀리 떠나기보다는 기차로 한 시간 안팎이면 닿는 곳을 찾고 있어요. 걷기 좋은 길과 편하게 밥 먹을 곳이 함께 있으면 좋겠습니다.', 115, TIMESTAMPADD(MINUTE, -15, NOW())
) initial_articles
WHERE @apply_community_data = 1;

CREATE TEMPORARY TABLE initial_article_stats (
  article_id BIGINT NOT NULL,
  like_count BIGINT NOT NULL,
  comment_count BIGINT NOT NULL,
  view_count BIGINT NOT NULL,
  PRIMARY KEY (article_id)
);

INSERT INTO initial_article_stats (article_id, like_count, comment_count, view_count) VALUES
  (340000000000000001, 12, 3, 184),
  (340000000000000002, 18, 4, 276),
  (340000000000000003, 21, 5, 342),
  (340000000000000004, 27, 6, 421),
  (340000000000000005, 14, 4, 198),
  (340000000000000006, 33, 7, 515),
  (340000000000000007, 45, 8, 689),
  (340000000000000008, 16, 3, 244),
  (340000000000000009, 49, 9, 732),
  (340000000000000010, 25, 5, 403),
  (340000000000000011, 22, 4, 358),
  (340000000000000012, 41, 7, 617),
  (340000000000000013, 19, 3, 289),
  (340000000000000014, 36, 6, 556),
  (340000000000000015, 52, 10, 804);

INSERT IGNORE INTO article_like.article_like (article_like_id, article_id, user_id, created_at)
SELECT
  5000000000000000000 + MOD(stats.article_id, 100) * 100 + numbers.n,
  stats.article_id,
  10000 + numbers.n,
  TIMESTAMPADD(MINUTE, -numbers.n, NOW())
FROM initial_article_stats stats
CROSS JOIN (
  SELECT ones.n + tens.n * 10 + 1 n
  FROM
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
  CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) tens
) numbers
WHERE @apply_community_data = 1
  AND numbers.n <= stats.like_count;

INSERT INTO article_like.article_like_count (article_id, like_count, version)
SELECT article_id, like_count, 0
FROM initial_article_stats
WHERE @apply_community_data = 1
ON DUPLICATE KEY UPDATE like_count = VALUES(like_count);

INSERT IGNORE INTO comment.comment_v2
  (comment_id, content, article_id, writer_id, path, deleted, created_at)
SELECT
  4100000000000000000 + MOD(stats.article_id, 100) * 100 + numbers.n,
  CASE MOD(numbers.n, 5)
    WHEN 0 THEN '이야기를 읽으니 비슷한 경험이 떠오르네요. 잘 읽었습니다.'
    WHEN 1 THEN '저도 궁금했던 주제예요. 다른 분들의 이야기도 기다려집니다.'
    WHEN 2 THEN '소개해 주신 방법을 이번 주에 한번 해보려고요.'
    WHEN 3 THEN '사소해 보여도 이런 순간이 하루를 바꾸는 것 같아요.'
    ELSE '편하게 읽히면서도 오래 생각하게 되는 이야기네요.'
  END,
  stats.article_id,
  20000 + numbers.n,
  LPAD(numbers.n - 1, 5, '0'),
  FALSE,
  TIMESTAMPADD(MINUTE, -numbers.n, NOW())
FROM initial_article_stats stats
CROSS JOIN (
  SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
  UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) numbers
WHERE @apply_community_data = 1
  AND numbers.n <= stats.comment_count;

INSERT INTO comment.article_comment_count (article_id, comment_count)
SELECT article_id, comment_count
FROM initial_article_stats
WHERE @apply_community_data = 1
ON DUPLICATE KEY UPDATE comment_count = VALUES(comment_count);

INSERT INTO article_view.article_view_count (article_id, view_count)
SELECT article_id, view_count
FROM initial_article_stats
WHERE @apply_community_data = 1
ON DUPLICATE KEY UPDATE view_count = VALUES(view_count);

INSERT INTO article.board_article_count (board_id, article_count)
SELECT 2, COUNT(*) FROM article.article WHERE board_id = 2
ON DUPLICATE KEY UPDATE article_count = VALUES(article_count);

INSERT INTO article.schema_migration (migration_name, applied_at)
SELECT '03-community-data', NOW()
WHERE @apply_community_data = 1;

DROP TEMPORARY TABLE initial_article_stats;
