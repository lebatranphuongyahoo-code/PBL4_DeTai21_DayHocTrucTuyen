package com.pbl4.server.db;

import com.pbl4.common.Protocol;
import com.pbl4.common.RoomInfo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SQLite-backed user and room storage.
 *
 * <p>Values must be bound through {@link PreparedStatement}, not concatenated into SQL:
 * concatenating user input can change the meaning of a query (SQL injection), while
 * prepared statements send values separately from the SQL syntax.</p>
 */
public class SqliteDatabaseManager implements DatabaseManager {

    private static final int SQLITE_CONSTRAINT = 19;
    private static final int ROOM_ID_ATTEMPTS = 100;

    private final Connection connection;

    /**
     * Opens the SQLite database at {@code dbPath} and creates its tables if needed.
     *
     * @param dbPath path to the database file; use {@code :memory:} for an in-memory database
     * @throws IllegalStateException if the database cannot be opened or initialized
     */
    public SqliteDatabaseManager(String dbPath) {
        Objects.requireNonNull(dbPath, "dbPath");
        Connection opened = null;
        try {
            opened = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (PreparedStatement statement = opened.prepareStatement("PRAGMA foreign_keys = ON")) {
                statement.execute();
            }
            try (PreparedStatement statement = opened.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS Users (
                        username TEXT PRIMARY KEY,
                        password_hash TEXT NOT NULL,
                        role TEXT NOT NULL
                    )
                    """)) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = opened.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS Rooms (
                        room_id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        teacher_user TEXT NOT NULL,
                        FOREIGN KEY (teacher_user) REFERENCES Users(username)
                    )
                    """)) {
                statement.executeUpdate();
            }
            connection = opened;
        } catch (SQLException e) {
            if (opened != null) {
                try {
                    opened.close();
                } catch (SQLException closeException) {
                    e.addSuppressed(closeException);
                }
            }
            throw new IllegalStateException("Cannot initialize SQLite database: " + dbPath, e);
        }
    }

    /**
     * Registers a user.
     *
     * @return {@code false} only when the username already exists
     */
    @Override
    public synchronized boolean register(String user, String sha256Hex, String role) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO Users (username, password_hash, role) VALUES (?, ?, ?)")) {
            statement.setString(1, user);
            statement.setString(2, sha256Hex);
            statement.setString(3, role);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (isConstraintOn(e, "Users.username")) {
                return false;
            }
            throw new IllegalStateException("Cannot register user", e);
        }
    }

    /** Returns the stored role when both credentials match, or {@code null} otherwise. */
    @Override
    public synchronized String authenticate(String user, String sha256Hex) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT role FROM Users WHERE username = ? AND password_hash = ?")) {
            statement.setString(1, user);
            statement.setString(2, sha256Hex);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("role") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot authenticate user", e);
        }
    }

    /** Creates a room with a unique six-digit ID and returns that ID. */
    @Override
    public synchronized int createRoom(String name, String teacherUser) {
        for (int attempt = 0; attempt < ROOM_ID_ATTEMPTS; attempt++) {
            int roomId = ThreadLocalRandom.current()
                    .nextInt(Protocol.ROOM_ID_MIN, Protocol.ROOM_ID_MAX + 1);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO Rooms (room_id, name, teacher_user) VALUES (?, ?, ?)")) {
                statement.setInt(1, roomId);
                statement.setString(2, name);
                statement.setString(3, teacherUser);
                statement.executeUpdate();
                return roomId;
            } catch (SQLException e) {
                if (isConstraintOn(e, "Rooms.room_id")) {
                    continue;
                }
                throw new IllegalStateException("Cannot create room", e);
            }
        }
        throw new IllegalStateException("Cannot allocate a unique room ID after "
                + ROOM_ID_ATTEMPTS + " attempts");
    }

    /** Returns saved rooms; {@code memberCount} is zero because online state is kept in memory. */
    @Override
    public synchronized List<RoomInfo> listRooms() {
        List<RoomInfo> rooms = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT room_id, name, teacher_user FROM Rooms ORDER BY room_id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rooms.add(new RoomInfo(
                        result.getInt("room_id"),
                        result.getString("name"),
                        result.getString("teacher_user"),
                        0));
            }
            return rooms;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot list rooms", e);
        }
    }

    @Override
    public synchronized Optional<RoomInfo> findRoom(int roomId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT room_id, name, teacher_user FROM Rooms WHERE room_id = ?")) {
            statement.setInt(1, roomId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RoomInfo(result.getInt("room_id"), result.getString("name"),
                        result.getString("teacher_user"), 0));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot find room", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot close SQLite database", e);
        }
    }

    private static boolean isConstraintOn(SQLException exception, String column) {
        return (exception.getErrorCode() & 0xFF) == SQLITE_CONSTRAINT
                && exception.getMessage() != null
                && exception.getMessage().contains(column);
    }
}
