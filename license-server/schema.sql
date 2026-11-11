CREATE TABLE IF NOT EXISTS licenses (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 key_hash TEXT NOT NULL UNIQUE,
 plan TEXT NOT NULL CHECK(plan IN ('trial','monthly','lifetime')),
 status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','revoked')),
 expires_at TEXT,
 device_limit INTEGER NOT NULL DEFAULT 1,
 devices_json TEXT NOT NULL DEFAULT '[]',
 customer_note TEXT,
 created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_licenses_status ON licenses(status);