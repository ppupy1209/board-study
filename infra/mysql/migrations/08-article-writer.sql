SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS article.article_writer (
  article_id BIGINT NOT NULL,
  writer_type VARCHAR(10) NOT NULL,
  writer_nickname VARCHAR(40) NOT NULL,
  PRIMARY KEY (article_id),
  CONSTRAINT fk_article_writer_article
    FOREIGN KEY (article_id) REFERENCES article.article (article_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS article.schema_migration (
  migration_name VARCHAR(100) NOT NULL,
  applied_at DATETIME(6) NOT NULL,
  PRIMARY KEY (migration_name)
) ENGINE=InnoDB;

INSERT INTO article.schema_migration (migration_name, applied_at)
VALUES ('08-article-writer', NOW())
ON DUPLICATE KEY UPDATE migration_name = VALUES(migration_name);
