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
    public record Classroom(long id, String name, int members) {}
    private final Connection connection;

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
        try (PreparedStatement s = connection.prepareStatement("SELECT c.id,c.name,(SELECT count(*) FROM classroom_members cm WHERE cm.classroom_id=c.id) members FROM classrooms c WHERE c.status='ACTIVE' AND (c.owner_id=? OR EXISTS(SELECT 1 FROM classroom_members m WHERE m.classroom_id=c.id AND m.account_id=?)) ORDER BY c.id")) {
            s.setLong(1,accountId); s.setLong(2,accountId);
            var result = new ArrayList<Classroom>();
            try (ResultSet rs = s.executeQuery()) { while(rs.next()) result.add(new Classroom(rs.getLong(1),rs.getString(2),rs.getInt(3))); }
            return result;
        }
    }
    @PreDestroy public synchronized void close() throws SQLException { connection.close(); }
}
