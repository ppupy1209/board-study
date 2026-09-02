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

CREATE TABLE IF NOT EXISTS article.article_writer (
  article_id BIGINT NOT NULL,
  writer_type VARCHAR(10) NOT NULL,
  writer_nickname VARCHAR(40) NOT NULL,
  PRIMARY KEY (article_id),
  CONSTRAINT fk_article_writer_article
    FOREIGN KEY (article_id) REFERENCES article.article (article_id) ON DELETE CASCADE
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

INSERT INTO article.seed_progress (seed_name, seeded_count, target_count, updated_at)
VALUES ('free-board-15m', 0, 15000000, NOW())
ON DUPLICATE KEY UPDATE updated_at = updated_at;
