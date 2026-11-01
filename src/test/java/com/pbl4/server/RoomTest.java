package com.pbl4.server;

import com.pbl4.server.db.SqliteDatabaseManager;
import java.net.Socket;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTest {

    private SqliteDatabaseManager db;
    private ServerContext ctx;

    @BeforeEach
    void setUp() {
        db = new SqliteDatabaseManager(":memory:");
        ctx = ServerContext.create(new RoomManager(), db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private ClientHandler dummyHandler() {
        return new ClientHandler(new Socket(), ctx);
    }

    @Test
    void addRemoveCount() {
        Room room = new Room(100001, "Toan", "gv1");
        ClientHandler h1 = dummyHandler();
        ClientHandler h2 = dummyHandler();

        assertTrue(room.tryAddMember(h1));
        assertTrue(room.tryAddMember(h2));
        assertEquals(2, room.memberCount());

        assertTrue(room.removeMember(h1));
        assertEquals(1, room.memberCount());
    }

    @Test
    void closeIfEmptyOnlyWhenEmpty() {
        Room room = new Room(100001, "Toan", "gv1");
        ClientHandler h1 = dummyHandler();
        assertTrue(room.tryAddMember(h1));
        assertFalse(room.closeIfEmpty());

        assertTrue(room.removeMember(h1));
        assertTrue(room.closeIfEmpty());
    }

    @Test
    void closedRoomRejectsMembers() {
        Room room = new Room(100001, "Toan", "gv1");
        assertTrue(room.closeIfEmpty());
        assertFalse(room.tryAddMember(dummyHandler()));
        assertEquals(0, room.memberCount());
    }

    @Test
    void snapshotIsCopy() {
        Room room = new Room(100001, "Toan", "gv1");
        assertTrue(room.tryAddMember(dummyHandler()));
        List<ClientHandler> snap = room.snapshotMembers();
        snap.clear();
        assertEquals(1, room.memberCount());
    }
}
