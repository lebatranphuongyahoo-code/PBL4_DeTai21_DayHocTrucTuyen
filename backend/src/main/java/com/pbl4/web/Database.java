package com.pbl4.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

/** Serializes short SQLite operations; no network or password hashing under this lock. */
@Component
public class Database {
    public record Account(long id, String username, String displayName, String passwordHash, String role) {}
    public record Classroom(long id, String name, int members, boolean owned) {}
    public record AdminClassroom(long id, String name, long ownerId, String ownerUsername, String ownerDisplayName,
                                 int members, String status, long createdAt) {}
    private final Connection connection;
    @FunctionalInterface interface Transaction<T> { T execute(Connection connection) throws SQLException; }
    synchronized <T> T transaction(Transaction<T> work) throws SQLException {
        connection.setAutoCommit(false);
        try { T result = work.execute(connection); connection.commit(); return result; }
        catch (SQLException | RuntimeException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }

    public Database(@Value("${app.database}") String path) throws Exception {
        Path target = Path.of(path).toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Connection opened = DriverManager.getConnection("jdbc:sqlite:" + target);
        try {
            try (Statement s = opened.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
                // Legacy schema must only be imported by an explicit, reviewed migration.
                try (ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND lower(name) IN ('users','rooms')")) {
                    if (rs.next()) throw new IllegalStateException("Legacy database detected. Use a separate web database; do not migrate pbl4.db in place.");
                }
            }
            opened.setAutoCommit(false);
            try (Statement s = opened.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS schema_versions(version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
                boolean applied;
                try (ResultSet rs = s.executeQuery("SELECT version FROM schema_versions WHERE version=1")) { applied = rs.next(); }
                if (!applied) {
                    try (var input = Database.class.getResourceAsStream("/db/V001__foundation.sql")) {
                        if (input == null) throw new IllegalStateException("Migration missing");
                        String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                        for (String command : sql.split(";")) if (!command.isBlank()) s.execute(command);
                    }
                    s.executeUpdate("INSERT INTO schema_versions VALUES (1, strftime('%s','now'))");
                }
                s.execute("CREATE TABLE IF NOT EXISTS classroom_meetings(classroom_id INTEGER PRIMARY KEY REFERENCES classrooms(id) ON DELETE CASCADE, is_open INTEGER NOT NULL DEFAULT 0 CHECK(is_open IN (0,1)))");
                s.execute("INSERT OR IGNORE INTO classroom_meetings(classroom_id) SELECT id FROM classrooms");
                s.execute("INSERT OR IGNORE INTO schema_versions VALUES(2,strftime('%s','now'))");
                boolean historyApplied;
                try (ResultSet rs = s.executeQuery("SELECT version FROM schema_versions WHERE version=3")) { historyApplied = rs.next(); }
                if (!historyApplied) {
                    s.execute("CREATE TABLE meeting_sessions(id INTEGER PRIMARY KEY, classroom_id INTEGER NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE, started_at INTEGER NOT NULL, ended_at INTEGER)");
                    s.execute("CREATE UNIQUE INDEX meeting_sessions_open ON meeting_sessions(classroom_id) WHERE ended_at IS NULL");
                    // Existing open meetings are recorded from migration time, not their unknown original start.
                    s.execute("INSERT INTO meeting_sessions(classroom_id,started_at) SELECT classroom_id,CAST(strftime('%s','now') AS INTEGER)*1000 FROM classroom_meetings WHERE is_open=1");
                    s.execute("INSERT INTO schema_versions VALUES(3,strftime('%s','now'))");
                }
                boolean attendanceApplied;
                try (ResultSet rs = s.executeQuery("SELECT version FROM schema_versions WHERE version=4")) { attendanceApplied=rs.next(); }
                if (!attendanceApplied) {
                    s.execute("ALTER TABLE meeting_sessions ADD COLUMN start_uncertain INTEGER NOT NULL DEFAULT 1");
                    s.execute("CREATE TABLE meeting_connections(id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES meeting_sessions(id) ON DELETE CASCADE, account_id INTEGER NOT NULL REFERENCES accounts(id), joined_at INTEGER NOT NULL, left_at INTEGER, interrupted INTEGER NOT NULL DEFAULT 0)");
                    s.execute("CREATE INDEX meeting_connections_session ON meeting_connections(session_id,id)");
                    s.execute("INSERT INTO schema_versions VALUES(4,strftime('%s','now'))");
                }
                s.execute("CREATE TABLE IF NOT EXISTS meeting_microphone_blocks(session_id INTEGER NOT NULL REFERENCES meeting_sessions(id) ON DELETE CASCADE, username TEXT NOT NULL, PRIMARY KEY(session_id,username))");
                s.execute("CREATE TABLE IF NOT EXISTS class_messages(id INTEGER PRIMARY KEY, classroom_id INTEGER NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE, account_id INTEGER NOT NULL REFERENCES accounts(id), body TEXT NOT NULL CHECK(length(body) BETWEEN 1 AND 500), sent_at INTEGER NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS class_messages_room ON class_messages(classroom_id,id)");
                s.execute("CREATE INDEX IF NOT EXISTS meeting_connections_account ON meeting_connections(session_id,account_id,joined_at)");
                boolean leaseApplied;
                try(ResultSet rs=s.executeQuery("SELECT version FROM schema_versions WHERE version=5")){leaseApplied=rs.next();}
                if(!leaseApplied){
                    s.execute("ALTER TABLE meeting_sessions ADD COLUMN owner_seen_at INTEGER NOT NULL DEFAULT 0");
                    s.execute("ALTER TABLE meeting_sessions ADD COLUMN end_reason TEXT");
                    s.execute("INSERT INTO schema_versions VALUES(5,strftime('%s','now'))");
                }
                boolean chatSessionApplied;
                try(ResultSet rs=s.executeQuery("SELECT version FROM schema_versions WHERE version=6")){chatSessionApplied=rs.next();}
                if(!chatSessionApplied){
                    // Older messages have no reliable meeting identity; keep them unassigned.
                    s.execute("ALTER TABLE class_messages ADD COLUMN session_id INTEGER REFERENCES meeting_sessions(id)");
                    s.execute("CREATE INDEX class_messages_session ON class_messages(classroom_id,session_id,id)");
                    s.execute("INSERT INTO schema_versions VALUES(6,strftime('%s','now'))");
                }
                boolean activitiesApplied;
                try(ResultSet rs=s.executeQuery("SELECT version FROM schema_versions WHERE version=7")){activitiesApplied=rs.next();}
                if(!activitiesApplied){
                    s.execute("CREATE TABLE class_activities(id INTEGER PRIMARY KEY, classroom_id INTEGER NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE, title TEXT NOT NULL CHECK(length(title) BETWEEN 2 AND 120), prompt TEXT NOT NULL CHECK(length(prompt) BETWEEN 1 AND 2000), created_at INTEGER NOT NULL)");
                    s.execute("CREATE INDEX class_activities_room ON class_activities(classroom_id,id)");
                    s.execute("CREATE TABLE activity_submissions(id INTEGER PRIMARY KEY, activity_id INTEGER NOT NULL REFERENCES class_activities(id) ON DELETE CASCADE, account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, answer TEXT NOT NULL CHECK(length(answer) BETWEEN 1 AND 5000), submitted_at INTEGER NOT NULL, score INTEGER CHECK(score BETWEEN 0 AND 10), feedback TEXT, reviewed_at INTEGER, UNIQUE(activity_id,account_id))");
                    s.execute("CREATE INDEX activity_submissions_activity ON activity_submissions(activity_id,submitted_at)");
                    s.execute("INSERT INTO schema_versions VALUES(7,strftime('%s','now'))");
                }
                // Open records from the previous server process have an unknown departure time.
                s.execute("UPDATE meeting_connections SET interrupted=1 WHERE left_at IS NULL AND interrupted=0");
                s.execute("UPDATE meeting_sessions SET ended_at=CAST(strftime('%s','now') AS INTEGER)*1000,end_reason='SERVER_RESTART' WHERE ended_at IS NULL");
                s.execute("UPDATE classroom_meetings SET is_open=0 WHERE is_open=1");
                opened.commit();
            } catch (Exception e) { opened.rollback(); throw e; }
            opened.setAutoCommit(true);
            connection = opened;
        } catch (Exception e) { opened.close(); throw e; }
    }

    public synchronized boolean createStudent(String username, String displayName, String hash) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("INSERT INTO accounts(username,display_name,password_hash,role,created_at) VALUES (?,?,?,'STUDENT',?) ON CONFLICT(username) DO NOTHING")) {
            s.setString(1, username); s.setString(2, displayName); s.setString(3, hash); s.setLong(4, System.currentTimeMillis());
            return s.executeUpdate() == 1;
        }
    }
    /** Only the local operator command calls this; public registration always creates students. */
    public synchronized boolean setRole(String username, String role) throws SQLException {
        if (!List.of("STUDENT", "TEACHER", "ADMIN").contains(role)) throw new IllegalArgumentException("Unsupported role");
        try (PreparedStatement s = connection.prepareStatement("UPDATE accounts SET role=? WHERE username=? AND status='ACTIVE'")) {
            s.setString(1, role); s.setString(2, username == null ? "" : username.strip());
            return s.executeUpdate() == 1;
        }
    }
    private Account account(ResultSet rs) throws SQLException {
        return new Account(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("password_hash"), rs.getString("role"));
    }
    public synchronized Account findAccount(String username) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT * FROM accounts WHERE username=? AND status='ACTIVE'")) {
            s.setString(1, username);
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? account(rs) : null; }
        }
    }
    public synchronized void saveSession(String hash, long id, long expiry) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("DELETE FROM sessions WHERE expires_at<=?")) {
            s.setLong(1, System.currentTimeMillis()); s.executeUpdate();
        }
        try (PreparedStatement s = connection.prepareStatement("INSERT INTO sessions VALUES (?,?,?)")) {
            s.setString(1, hash); s.setLong(2,id); s.setLong(3,expiry); s.executeUpdate();
        }
    }
    public synchronized Account sessionAccount(String hash) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT a.* FROM accounts a JOIN sessions s ON s.account_id=a.id WHERE s.token_hash=? AND s.expires_at>? AND a.status='ACTIVE'")) {
            s.setString(1,hash); s.setLong(2,System.currentTimeMillis());
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? account(rs) : null; }
        }
    }
    public synchronized void deleteSession(String hash) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("DELETE FROM sessions WHERE token_hash=?")) { s.setString(1,hash); s.executeUpdate(); }
    }
    public synchronized List<Classroom> listClassrooms(long accountId) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT c.id,c.name,(SELECT count(*) FROM classroom_members cm WHERE cm.classroom_id=c.id) members,c.owner_id FROM classrooms c WHERE c.status='ACTIVE' AND (c.owner_id=? OR EXISTS(SELECT 1 FROM classroom_members m WHERE m.classroom_id=c.id AND m.account_id=?)) ORDER BY c.id")) {
            s.setLong(1,accountId); s.setLong(2,accountId);
            var result = new ArrayList<Classroom>();
            try (ResultSet rs = s.executeQuery()) { while(rs.next()) result.add(new Classroom(rs.getLong(1),rs.getString(2),rs.getInt(3),rs.getLong(4)==accountId)); }
            return result;
        }
    }
    public synchronized List<AdminClassroom> listAdminClassrooms() throws SQLException {
        String sql = "SELECT c.id,c.name,c.owner_id,o.username,o.display_name,"+
            "(SELECT count(*) FROM classroom_members m WHERE m.classroom_id=c.id),c.status,c.created_at "+
            "FROM classrooms c JOIN accounts o ON o.id=c.owner_id ORDER BY c.id";
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet rs = s.executeQuery()) {
            var result = new ArrayList<AdminClassroom>();
            while (rs.next()) result.add(new AdminClassroom(rs.getLong(1), rs.getString(2), rs.getLong(3),
                rs.getString(4), rs.getString(5), rs.getInt(6), rs.getString(7), rs.getLong(8)));
            return result;
        }
    }
    @PreDestroy public synchronized void close() throws SQLException { connection.close(); }
}
