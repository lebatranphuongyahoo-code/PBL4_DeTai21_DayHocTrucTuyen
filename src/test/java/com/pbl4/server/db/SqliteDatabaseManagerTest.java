package com.pbl4.server.db;

import com.pbl4.common.Protocol;
import com.pbl4.common.RoomInfo;
import com.pbl4.common.Sha256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDatabaseManagerTest {

    private static final String H = Sha256.hex("123456");
    private static final String H2 = Sha256.hex("x");

    private SqliteDatabaseManager db;

    @BeforeEach
    void setUp() {
        db = new SqliteDatabaseManager(":memory:");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void registerThenAuthenticate() {
        assertTrue(db.register("lan", H, Protocol.ROLE_TEACHER));
        assertEquals(Protocol.ROLE_TEACHER, db.authenticate("lan", H));
    }

    @Test
    void duplicateRegisterReturnsFalse() {
        assertTrue(db.register("lan", H, Protocol.ROLE_TEACHER));
        assertFalse(db.register("lan", H2, Protocol.ROLE_STUDENT));
    }

    @Test
    void wrongPasswordReturnsNull() {
        assertTrue(db.register("lan", H, Protocol.ROLE_TEACHER));
        assertNull(db.authenticate("lan", H2));
        assertNull(db.authenticate("khong-co", H));
    }

    @Test
    void createRoomGivesSixDigitId() {
        assertTrue(db.register("lan", H, Protocol.ROLE_TEACHER));
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            int id = db.createRoom("Lop " + i, "lan");
            assertTrue(id >= Protocol.ROOM_ID_MIN && id <= Protocol.ROOM_ID_MAX,
                    "roomId out of range: " + id);
            assertTrue(ids.add(id), "duplicate roomId: " + id);
        }
        assertEquals(20, ids.size());
    }

    @Test
    void listRoomsReturnsCreated() {
        assertTrue(db.register("lan", H, Protocol.ROLE_TEACHER));
        int id1 = db.createRoom("Toan", "lan");
        int id2 = db.createRoom("Ly", "lan");

        List<RoomInfo> rooms = db.listRooms();
        assertEquals(2, rooms.size());
        assertTrue(rooms.stream().anyMatch(r ->
                r.roomId() == id1 && "Toan".equals(r.roomName()) && "lan".equals(r.teacherUsername())));
        assertTrue(rooms.stream().anyMatch(r ->
                r.roomId() == id2 && "Ly".equals(r.roomName()) && "lan".equals(r.teacherUsername())));
    }

    @Test
    void findRoom() {
        assertTrue(db.register("lan", H, Protocol.ROLE_TEACHER));
        int id = db.createRoom("Mang may tinh", "lan");

        Optional<RoomInfo> found = db.findRoom(id);
        assertTrue(found.isPresent());
        assertEquals("Mang may tinh", found.get().roomName());
        assertEquals("lan", found.get().teacherUsername());
        assertEquals(0, found.get().memberCount());

        assertTrue(db.findRoom(123).isEmpty());
    }
}
