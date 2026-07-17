SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS article CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS comment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS article_like CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS article_view CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY 'metrics';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';

CREATE TABLE IF NOT EXISTS article.board (
  board_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(300) NOT NULL,
  PRIMARY KEY (board_id)
) ENGINE=InnoDB;

INSERT INTO article.board (board_id, name, description)
VALUES (1, '자유게시판', '일상, 취미, 질문, 커리어 등 다양한 주제로 자유롭게 소통하는 공간')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

CREATE TABLE IF NOT EXISTS article.article (
  article_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  board_id BIGINT NOT NULL,
  writer_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  modified_at DATETIME(6) NOT NULL,
  PRIMARY KEY (article_id),
  KEY idx_article_board_id_article_id (board_id, article_id DESC)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS article.board_article_count (
  board_id BIGINT NOT NULL,
  article_count BIGINT NOT NULL,
  PRIMARY KEY (board_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS article.outbox (
  outbox_id BIGINT NOT NULL,
  event_type VARCHAR(60) NOT NULL,
  payload LONGTEXT NOT NULL,
  shard_key BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (outbox_id),
  KEY idx_outbox_shard_created_at (shard_key, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS article.seed_progress (
  seed_name VARCHAR(50) NOT NULL,
  seeded_count BIGINT NOT NULL,
  target_count BIGINT NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (seed_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comment.comment (
  comment_id BIGINT NOT NULL,
  content VARCHAR(500) NOT NULL,
  parent_comment_id BIGINT NOT NULL,
  article_id BIGINT NOT NULL,
  writer_id BIGINT NOT NULL,
  deleted BOOLEAN NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (comment_id),
  KEY idx_comment_article_parent_id (article_id, parent_comment_id, comment_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comment.comment_v2 (
  comment_id BIGINT NOT NULL,
  content VARCHAR(500) NOT NULL,
  article_id BIGINT NOT NULL,
  writer_id BIGINT NOT NULL,
  path VARCHAR(25) NOT NULL,
  deleted BOOLEAN NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (comment_id),
  -- path 채번은 게시글 단위다(CommentRepositoryV2.findDescendantTopPath가 article_id로 범위를 좁힌다).
  -- 그래서 모든 게시글의 첫 댓글 path가 "00000"이 된다.
  -- unique key를 path 단독으로 두면 "00000"을 가질 수 있는 게시글이 전역에 하나뿐이라,
  -- 두 번째 게시글부터는 첫 댓글이 반드시 Duplicate entry로 실패한다.
  -- 유일성 범위를 채번 범위와 일치시킨다. 이 인덱스가 (article_id, path) 조회도 함께 커버한다.
  UNIQUE KEY uk_comment_v2_article_path (article_id, path)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comment.article_comment_count (
  article_id BIGINT NOT NULL,
  comment_count BIGINT NOT NULL,
  PRIMARY KEY (article_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comment.outbox LIKE article.outbox;

CREATE TABLE IF NOT EXISTS article_like.article_like (
  article_like_id BIGINT NOT NULL,
  article_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (article_like_id),
  UNIQUE KEY uk_article_like_article_user (article_id, user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS article_like.article_like_count (
  article_id BIGINT NOT NULL,
  like_count BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (article_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS article_like.outbox LIKE article.outbox;

CREATE TABLE IF NOT EXISTS article_view.article_view_count (
  article_id BIGINT NOT NULL,
  view_count BIGINT NOT NULL,
  PRIMARY KEY (article_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS article_view.outbox LIKE article.outbox;

INSERT IGNORE INTO article.article
  (article_id, title, content, board_id, writer_id, created_at, modified_at)
SELECT
  8000000000000000000 + n,
  CASE MOD(n, 16)
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
  CASE MOD(n, 16)
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
  MOD(n, 30) + 1,
  TIMESTAMPADD(MINUTE, -n, NOW()),
  TIMESTAMPADD(MINUTE, -n, NOW())
FROM (
  SELECT ones.d + tens.d * 10 AS n
  FROM
    (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
  CROSS JOIN
    (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
) numbers;

INSERT INTO article.board_article_count (board_id, article_count)
VALUES (1, 100)
ON DUPLICATE KEY UPDATE article_count = VALUES(article_count);

INSERT INTO article.seed_progress (seed_name, seeded_count, target_count, updated_at)
VALUES ('free-board-15m', 0, 15000000, NOW())
ON DUPLICATE KEY UPDATE updated_at = updated_at;
