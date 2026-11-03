CREATE TABLE accounts (
 id INTEGER PRIMARY KEY,
 username TEXT NOT NULL COLLATE NOCASE UNIQUE,
 display_name TEXT NOT NULL,
 password_hash TEXT NOT NULL,
 role TEXT NOT NULL DEFAULT 'STUDENT' CHECK(role IN ('STUDENT','TEACHER','ADMIN')),
 status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','DISABLED')),
 created_at INTEGER NOT NULL
);
CREATE TABLE sessions (
 token_hash TEXT PRIMARY KEY,
 account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 expires_at INTEGER NOT NULL
);
CREATE INDEX sessions_expiry ON sessions(expires_at);
CREATE TABLE classrooms (
 id INTEGER PRIMARY KEY,
 name TEXT NOT NULL,
 owner_id INTEGER NOT NULL REFERENCES accounts(id),
 invite_code TEXT NOT NULL UNIQUE,
 status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','ARCHIVED')),
 created_at INTEGER NOT NULL
);
CREATE TABLE classroom_members (
 classroom_id INTEGER NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
 account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 joined_at INTEGER NOT NULL,
 PRIMARY KEY(classroom_id,account_id)
);
CREATE TABLE join_requests (
 classroom_id INTEGER NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
 account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
 status TEXT NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED')),
 requested_at INTEGER NOT NULL,
 reviewed_at INTEGER,
 reviewed_by INTEGER REFERENCES accounts(id),
 PRIMARY KEY(classroom_id,account_id)
);
