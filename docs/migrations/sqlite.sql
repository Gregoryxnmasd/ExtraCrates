CREATE TABLE IF NOT EXISTS crate_cooldowns (
  player_uuid TEXT NOT NULL,
  crate_id TEXT NOT NULL,
  cooldown_at INTEGER NOT NULL,
  PRIMARY KEY (player_uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS crate_keys (
  player_uuid TEXT NOT NULL,
  crate_id TEXT NOT NULL,
  amount INTEGER NOT NULL,
  PRIMARY KEY (player_uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS crate_opens (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  player_uuid TEXT NOT NULL,
  crate_id TEXT NOT NULL,
  reward_id TEXT NOT NULL,
  server_id TEXT NOT NULL,
  opened_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_crate_opens_player ON crate_opens (player_uuid);

CREATE TABLE IF NOT EXISTS crate_pending_rewards (
  player_uuid TEXT NOT NULL,
  crate_id TEXT NOT NULL,
  reward_id TEXT NOT NULL,
  status TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (player_uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS crate_locks (
  player_uuid TEXT NOT NULL,
  crate_id TEXT NOT NULL,
  locked_at INTEGER NOT NULL,
  PRIMARY KEY (player_uuid, crate_id)
);

CREATE TABLE IF NOT EXISTS crate_pending_rewards (
  player_uuid TEXT NOT NULL PRIMARY KEY,
  crate_id TEXT NOT NULL,
  reward_id TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
