CREATE DATABASE IF NOT EXISTS auth
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS auth.auth_member (
  member_id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(320) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(40) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (member_id),
  UNIQUE KEY uk_auth_member_email (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS auth.refresh_token_session (
  refresh_token_id CHAR(36) NOT NULL,
  family_id CHAR(36) NOT NULL,
  member_id BIGINT NOT NULL,
  token_hash CHAR(43) NOT NULL,
  status VARCHAR(20) NOT NULL,
  replaced_by_token_id CHAR(36) NULL,
  revocation_reason VARCHAR(30) NULL,
  issued_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  rotated_at DATETIME(6) NULL,
  revoked_at DATETIME(6) NULL,
  PRIMARY KEY (refresh_token_id),
  UNIQUE KEY uk_refresh_token_hash (token_hash),
  KEY idx_refresh_token_family_issued (family_id, issued_at),
  KEY idx_refresh_token_member_status (member_id, status),
  KEY idx_refresh_token_status_expires (status, expires_at),
  CONSTRAINT fk_refresh_token_member
    FOREIGN KEY (member_id) REFERENCES auth.auth_member (member_id)
    ON DELETE CASCADE
) ENGINE=InnoDB;
