package com.pbl4.server;

import com.pbl4.common.RoomInfo;
import com.pbl4.server.db.SqliteDatabaseManager;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomManagerTest {

    private RoomManager rooms;
    private SqliteDatabaseManager db;
    private ServerContext ctx;

    @BeforeEach
    void setUp() {
        rooms = new RoomManager();
        db = new SqliteDatabaseManager(":memory:");
        ctx = ServerContext.create(rooms, db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private ClientHandler dummyHandler() {
        return new ClientHandler(new Socket(), ctx);
    }

    @Test
    void openIfAbsentReturnsSameInstance() {
        RoomInfo info = new RoomInfo(123456, "Lop A", "gv1", 0);
        Room r1 = rooms.openIfAbsent(info);
        Room r2 = rooms.openIfAbsent(info);
        assertSame(r1, r2);
    }

    @Test
    void removeIfEmptyRemovesAndCloses() {
        RoomInfo info = new RoomInfo(123456, "Lop A", "gv1", 0);
        Room oldRoom = rooms.openIfAbsent(info);
        assertTrue(rooms.removeIfEmpty(123456));
        assertNull(rooms.get(123456));
        assertFalse(oldRoom.tryAddMember(dummyHandler()));

        Room newRoom = rooms.openIfAbsent(info);
        assertNotSame(oldRoom, newRoom);
    }

    @Test
    @Timeout(30)
    void concurrentJoinLeaveNeverCreatesGhostRoom() throws Exception {
        final int roomId = 123456;
        final RoomInfo info = new RoomInfo(roomId, "Lop dua", "gv1", 0);
        final int threadCount = 8;
        final int iterations = 2000;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final AtomicInteger ghostViolations = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final ClientHandler h = dummyHandler();
            Thread worker = new Thread(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < iterations; i++) {
                    Room joined = null;
                    for (int attempt = 0; attempt < 50 && joined == null; attempt++) {
                        Room candidate = rooms.openIfAbsent(info);
                        if (candidate.tryAddMember(h)) {
                            joined = candidate;
                        } else {
                            Thread.yield();
                        }
                    }
                    if (joined == null || rooms.get(roomId) != joined) {
                        ghostViolations.incrementAndGet();
                    }
                    if (joined != null) {
                        joined.removeMember(h);
                        rooms.removeIfEmpty(roomId);
                    }
                }
            }, "join-leave-" + t);
            threads.add(worker);
            worker.start();
        }

        startLatch.countDown();
        for (Thread worker : threads) {
            worker.join();
        }

        assertEquals(0, ghostViolations.get());
        assertEquals(0, rooms.size());
    }
}
