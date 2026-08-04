CREATE DATABASE IF NOT EXISTS media
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS media.media_asset (
  media_id VARCHAR(36) NOT NULL,
  article_id BIGINT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  original_key VARCHAR(255) NOT NULL,
  thumbnail_key VARCHAR(255) NULL,
  original_size BIGINT NOT NULL,
  thumbnail_size BIGINT NULL,
  width INT NULL,
  height INT NULL,
  status VARCHAR(30) NOT NULL,
  upload_mode VARCHAR(30) NOT NULL,
  failure_reason VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  attached_at DATETIME(6) NULL,
  PRIMARY KEY (media_id),
  UNIQUE KEY uk_media_asset_original_key (original_key),
  KEY idx_media_asset_article_created_at (article_id, created_at),
  KEY idx_media_asset_status_created_at (status, created_at)
) ENGINE=InnoDB;
