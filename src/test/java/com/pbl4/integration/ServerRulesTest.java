package com.pbl4.integration;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.Protocol;
import com.pbl4.common.msg.Messages;
import com.pbl4.server.RoomManager;
import com.pbl4.server.ServerConfig;
import com.pbl4.server.ServerMain;
import com.pbl4.server.db.DatabaseManager;
import com.pbl4.server.db.SqliteDatabaseManager;
import com.pbl4.tools.TestClient.Conn;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRulesTest {

    private RoomManager rooms;
    private DatabaseManager db;
    private ServerMain server;
    private final List<Conn> conns = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        rooms = new RoomManager();
        db = new SqliteDatabaseManager(":memory:");
        server = new ServerMain(ServerConfig.ephemeral(), rooms, db);
        Thread t = new Thread(server::start, "test-server");
        t.setDaemon(true);
        t.start();
    }

    @AfterEach
    void tearDown() {
        for (Conn c : conns) {
            try {
                c.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
        conns.clear();
        if (server != null) {
            server.stop();
        }
    }

    private Conn client(String user, String role) throws Exception {
        Conn c = new Conn("127.0.0.1", server.getTcpPort(), server.getUdpPort());
        conns.add(c);
        c.registerAndLogin(user, "123456", role);
        return c;
    }

    // ==================================================================
    // B1 — Lớp vào lại được sau khi rỗng / sau khi khởi động lại
    // ==================================================================

    @Test
    @Timeout(20)
    void createRoomDoesNotOpenRoomInMemory() throws Exception {
        Conn gv = client("gv1", Protocol.ROLE_TEACHER);
        gv.createRoom("Lop thu nghiem");
        assertEquals(0, rooms.size());
    }

    @Test
    @Timeout(20)
    void roomCanBeRejoinedAfterEveryoneLeft() throws Exception {
        Conn gv = client("gv1", Protocol.ROLE_TEACHER);
        int id = gv.createRoom("Lop thu nghiem");
        assertTrue(gv.join(id, 0));
        assertNotNull(gv.await(PacketType.USER_LIST, 2000));

        gv.send(PacketType.LEAVE_ROOM_REQ, new Messages.RoomIdOnly(id).encode());
        long deadline = System.currentTimeMillis() + 2000;
        while (rooms.size() != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, rooms.size());

        Conn hs = client("hs1", Protocol.ROLE_STUDENT);
        assertTrue(hs.join(id, 0));
        assertNotNull(rooms.get(id));
        assertEquals(1, rooms.get(id).memberCount());
    }

    @Test
    @Timeout(20)
    void roomSurvivesServerRestart(@TempDir Path dir) throws Exception {
        String dbFile = dir.resolve("t.db").toString();

        RoomManager rooms1 = new RoomManager();
        DatabaseManager db1 = new SqliteDatabaseManager(dbFile);
        ServerMain s1 = new ServerMain(new ServerConfig(0, 0, dbFile), rooms1, db1);
        Thread t1 = new Thread(s1::start, "s1");
        t1.setDaemon(true);
        t1.start();

        int roomId;
        try (Conn gv = new Conn("127.0.0.1", s1.getTcpPort(), s1.getUdpPort())) {
            gv.registerAndLogin("gv1", "123456", Protocol.ROLE_TEACHER);
            roomId = gv.createRoom("Lop ben vung");
        } finally {
            s1.stop();
        }

        RoomManager rooms2 = new RoomManager();
        DatabaseManager db2 = new SqliteDatabaseManager(dbFile);
        ServerMain s2 = new ServerMain(new ServerConfig(0, 0, dbFile), rooms2, db2);
        Thread t2 = new Thread(s2::start, "s2");
        t2.setDaemon(true);
        t2.start();

        try (Conn hs = new Conn("127.0.0.1", s2.getTcpPort(), s2.getUdpPort())) {
            hs.registerAndLogin("hs1", "123456", Protocol.ROLE_STUDENT);
            assertTrue(hs.join(roomId, 0));
        } finally {
            s2.stop();
        }
    }

    // ==================================================================
    // B3 — Chỉ giáo viên chủ lớp được bật/tắt/gửi màn hình
    // ==================================================================

    @Test
    @Timeout(20)
    void studentCannotStopTeacherShare() throws Exception {
        Conn gv = client("gv1", Protocol.ROLE_TEACHER);
        int id = gv.createRoom("Lop B3");
        assertTrue(gv.join(id, 0));

        Conn hs = client("hs1", Protocol.ROLE_STUDENT);
        assertTrue(hs.join(id, 0));

        gv.send(PacketType.SCREEN_START, new Messages.RoomIdOnly(id).encode());
        assertNotNull(gv.await(PacketType.SCREEN_START, 2000));
        assertNotNull(hs.await(PacketType.SCREEN_START, 2000));
        assertTrue(rooms.get(id).isSharing());

        hs.send(PacketType.SCREEN_STOP, new Messages.RoomIdOnly(id).encode());
        Packet err = hs.await(PacketType.ERROR, 2000);
        assertNotNull(err);
        assertTrue(Messages.ErrorMessage.decode(err.getPayload()).message()
                .toLowerCase().contains("chi giao vien"));
        assertTrue(rooms.get(id).isSharing());
    }

    @Test
    @Timeout(20)
    void otherTeacherCannotShare() throws Exception {
        Conn gv1 = client("gv1", Protocol.ROLE_TEACHER);
        int id = gv1.createRoom("Lop B3");
        assertTrue(gv1.join(id, 0));

        Conn gv2 = client("gv2", Protocol.ROLE_TEACHER);
        assertTrue(gv2.join(id, 0));

        gv2.send(PacketType.SCREEN_START, new Messages.RoomIdOnly(id).encode());
        Packet err = gv2.await(PacketType.ERROR, 2000);
        assertNotNull(err);
        assertTrue(Messages.ErrorMessage.decode(err.getPayload()).message()
                .toLowerCase().contains("chi giao vien"));
        assertFalse(rooms.get(id).isSharing());
    }

    @Test
    @Timeout(20)
    void ownerCanStopShare() throws Exception {
        Conn gv = client("gv1", Protocol.ROLE_TEACHER);
        int id = gv.createRoom("Lop B3");
        assertTrue(gv.join(id, 0));

        Conn hs = client("hs1", Protocol.ROLE_STUDENT);
        assertTrue(hs.join(id, 0));

        gv.send(PacketType.SCREEN_START, new Messages.RoomIdOnly(id).encode());
        assertNotNull(hs.await(PacketType.SCREEN_START, 2000));
        assertTrue(rooms.get(id).isSharing());

        gv.send(PacketType.SCREEN_STOP, new Messages.RoomIdOnly(id).encode());
        assertNotNull(hs.await(PacketType.SCREEN_STOP, 2000));
        assertFalse(rooms.get(id).isSharing());
    }
}
