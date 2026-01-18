CREATE TABLE IF NOT EXISTS crate_cooldowns (
  player_uuid CHAR(36) NOT NULL,
  crate_id VARCHAR(64) NOT NULL,
  cooldown_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS crate_keys (
  player_uuid CHAR(36) NOT NULL,
  crate_id VARCHAR(64) NOT NULL,
  amount INT NOT NULL,
  PRIMARY KEY (player_uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS crate_opens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  player_uuid CHAR(36) NOT NULL,
  crate_id VARCHAR(64) NOT NULL,
  reward_id VARCHAR(64) NOT NULL,
  server_id VARCHAR(64) NOT NULL,
  opened_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_crate_opens_player (player_uuid)
);

CREATE TABLE IF NOT EXISTS crate_pending_rewards (
  player_uuid CHAR(36) NOT NULL,
  crate_id VARCHAR(64) NOT NULL,
  reward_id VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS crate_locks (
  player_uuid CHAR(36) NOT NULL,
  crate_id VARCHAR(64) NOT NULL,
  locked_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS crate_pending_rewards (
  player_uuid CHAR(36) NOT NULL,
  crate_id VARCHAR(64) NOT NULL,
  reward_id VARCHAR(64) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (player_uuid)
);
