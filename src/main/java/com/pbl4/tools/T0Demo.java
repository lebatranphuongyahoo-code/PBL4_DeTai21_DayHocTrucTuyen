package com.pbl4.tools;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;
import com.pbl4.common.Sha256;
import com.pbl4.server.ClientHandler;
import com.pbl4.server.Room;
import com.pbl4.server.RoomManager;
import com.pbl4.server.ServerMain;
import com.pbl4.server.db.DatabaseManager;
import com.pbl4.server.db.InMemoryDatabaseManager;

import java.io.DataInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Bài kiểm thử mức T0 chạy trong MỘT lệnh: tự dựng server, tự dựng 1 giáo viên + 3 học sinh,
 * rồi kiểm tra toàn bộ luồng nghiệp vụ và in ra bảng ĐẠT / KHÔNG ĐẠT.
 *
 * <p>Đây là công cụ để lấy bằng chứng cho <b>cả 3 cổng kiểm tra</b> mà không cần bật 4 cửa sổ
 * terminal bằng tay. Ảnh chụp màn hình của lệnh này chính là bằng chứng nộp cho thầy.</p>
 *
 * <pre>
 * Chay: java -cp target/classes com.pbl4.tools.T0Demo [port]
 * </pre>
 */
public class T0Demo {

    private static final String PASSWORD = "123456";
    private static final String HOST = "127.0.0.1";

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : Protocol.TCP_PORT;
        TestClient.setTarget(HOST, port);

        System.out.println("########################################################");
        System.out.println("#  BAI KIEM THU MUC T0 - PBL4 De tai 21 (Day hoc truc tuyen)");
        System.out.println("#  Phan cua TV1: server da luong + phong hoc + relay");
        System.out.println("########################################################");
        System.out.println();

        RoomManager rooms = new RoomManager();
        DatabaseManager db = new InMemoryDatabaseManager();
        ServerMain server = new ServerMain(port, rooms, db);
        Thread serverThread = new Thread(server::start, "server-main");
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(600);                       // cho server kip mo cong

        try {
            runScenario(rooms);
        } finally {
            server.stop();
            Thread.sleep(400);
        }

        System.out.println();
        System.out.println("########################################################");
        System.out.printf("#  KET QUA: %d DAT / %d TONG%n", passCount, passCount + failCount);
        System.out.println("########################################################");
        if (failCount > 0) {
            System.exit(1);
        }
    }

    private static void runScenario(RoomManager rooms) throws Exception {

        // ============ 1. Giáo viên: đăng nhập, tạo lớp, VÀO lớp ============
        section("1. Giao vien dang nhap, tao lop, vao lop");
        try (TestClient.Conn teacher = new TestClient.Conn()) {
            login(teacher, "lan", Protocol.ROLE_TEACHER);
            check("giao vien dang nhap duoc", true);

            teacher.send(PacketType.CREATE_ROOM_REQ,
                    PacketUtils.buildPayload(o -> o.writeUTF("Toan roi rac")));
            Packet created = teacher.await(PacketType.CREATE_ROOM_RES, 3000);
            int roomId = readRoomId(created);
            check("tao lop duoc, ma lop = " + roomId, roomId > 0);
            check("ma lop la so 6 chu so",
                    roomId >= Protocol.ROOM_ID_MIN && roomId <= Protocol.ROOM_ID_MAX);

            // Quan trong: tao lop KHONG tu dong dua giao vien vao lop.
            // Muon chia se man hinh thi phai la thanh vien cua lop.
            joinRoom(teacher, roomId);
            Room room = rooms.get(roomId);
            check("giao vien vao lop, lop co 1 nguoi",
                    room != null && room.memberCount() == 1);

            // ============ 2. Học sinh vào lớp ============
            section("2. Hoc sinh vao lop va danh sach thanh vien");
            try (TestClient.Conn s1 = new TestClient.Conn()) {
                login(s1, "minh", Protocol.ROLE_STUDENT);
                joinRoom(s1, roomId);
                Thread.sleep(200);
                check("hoc sinh 1 vao lop, lop co 2 nguoi",
                        room != null && room.memberCount() == 2);

                try (TestClient.Conn s2 = new TestClient.Conn()) {
                    login(s2, "an", Protocol.ROLE_STUDENT);
                    joinRoom(s2, roomId);
                    Thread.sleep(300);

                    check("hoc sinh 2 vao lop, lop co 3 nguoi",
                            room != null && room.memberCount() == 3);

                    // ============ 3. Không tin client ============
                    section("3. Kiem tra quyen o server (khong tin client)");
                    s1.send(PacketType.SCREEN_START,
                            PacketUtils.buildPayload(o -> o.writeInt(roomId)));
                    Packet denied = s1.awaitEither(PacketType.SCREEN_START, PacketType.ERROR, 2000);
                    check("hoc sinh bi TU CHOI chia se man hinh",
                            denied != null && denied.getType() == PacketType.ERROR);
                    check("co chia se cua lop van = false (khong bat co sai)",
                            room != null && !room.isSharing());

                    // ============ 4. Giáo viên chia sẻ màn hình ============
                    section("4. Giao vien chia se man hinh + 1 client MANG CHAM");
                    teacher.send(PacketType.SCREEN_START,
                            PacketUtils.buildPayload(o -> o.writeInt(roomId)));
                    Packet ok = teacher.awaitEither(PacketType.SCREEN_START, PacketType.ERROR, 2000);
                    check("giao vien duoc phep chia se man hinh",
                            ok != null && ok.getType() == PacketType.SCREEN_START);
                    check("co sharing cua lop da bat", room != null && room.isSharing());

                    // Client mạng chậm: kết nối KHÔNG có luồng đọc nền.
                    try (TestClient.Conn slow = new TestClient.Conn(false)) {
                        login(slow, "cham", Protocol.ROLE_STUDENT);
                        joinRoom(slow, roomId);
                        Thread.sleep(200);
                        check("client mang cham vao lop, lop co 4 nguoi",
                                room != null && room.memberCount() == 4);

                        final int frames = 30;
                        Thread sender = new Thread(() -> {
                            try {
                                sendFrames(teacher, frames);
                            } catch (Exception e) {
                                System.out.println("   [loi] gui frame: " + e.getMessage());
                            }
                        }, "fake-teacher-screen");
                        sender.start();

                        int fastFrames = drainFrames(s1, 4000);
                        int slowFrames = readSlow(slow, 4000, 200);
                        sender.join();

                        ClientHandler slowHandler = findMember(room, "cham");
                        ClientHandler fastHandler = findMember(room, "minh");

                        check("hoc sinh binh thuong nhan duoc frame (" + fastFrames + " frame)",
                                fastFrames > 0);
                        check("client mang cham nhan duoc it frame hon (" + slowFrames + " frame)",
                                slowFrames < fastFrames);
                        check("server BO FRAME cua client cham (dropped="
                                        + (slowHandler == null ? "?" : slowHandler.getFramesDropped()) + ")",
                                slowHandler != null && slowHandler.getFramesDropped() > 0);
                        check("client binh thuong KHONG bi bo frame (dropped="
                                        + (fastHandler == null ? "?" : fastHandler.getFramesDropped()) + ")",
                                fastHandler != null && fastHandler.getFramesDropped() == 0);
                        check("vung gang cua lop rat ngan ("
                                        + String.format("%.2f", room.averageLockHoldMicros())
                                        + " micro giay)",
                                room.averageLockHoldMicros() < 500);
                    }

                    // ============ 5. Thoại UDP ============
                    section("5. Chuyen tiep thoai UDP (648 byte/goi, 50 goi/giay)");
                    int[] audio = runAudioTest(s1, teacher, roomId, 3);
                    check("hoc sinh nhan duoc goi thoai (" + audio[0] + " goi)", audio[0] > 0);
                    check("ti le mat goi < 5% (" + audio[1] + "%)", audio[1] < 5);

                    // ============ 6. Rời lớp ============
                    section("6. Roi lop, cap nhat USER_LIST, don lop rong");
                    s2.send(PacketType.LEAVE_ROOM_REQ,
                            PacketUtils.buildPayload(o -> o.writeInt(roomId)));
                    Packet listAfter = s1.await(PacketType.USER_LIST, 2000);
                    int remaining = readUserCount(listAfter);
                    check("sau khi 1 nguoi roi, USER_LIST con " + remaining + " nguoi",
                            remaining == 3);

                    teacher.send(PacketType.SCREEN_STOP,
                            PacketUtils.buildPayload(o -> o.writeInt(roomId)));
                    Thread.sleep(200);
                }
            }
        }

        // ============ 7. Dọn lớp rỗng ============
        section("7. Kiem tra don lop rong khoi bo nho");
        Thread.sleep(600);
        check("khong con lop nao trong bo nho (rooms=" + rooms.size() + ")",
                rooms.size() == 0);
    }

    // ==================================================================
    // Các bước con
    // ==================================================================

    /** Đăng ký (bỏ qua nếu đã có) rồi đăng nhập. */
    private static void login(TestClient.Conn conn, String user, String role) throws Exception {
        String hash = Sha256.hex(PASSWORD);
        conn.send(PacketType.REGISTER_REQ, PacketUtils.buildPayload(o -> {
            o.writeUTF(user);
            o.writeUTF(hash);
            o.writeUTF(role);
        }));
        conn.await(PacketType.REGISTER_RES, 2000);

        conn.send(PacketType.LOGIN_REQ, PacketUtils.buildPayload(o -> {
            o.writeUTF(user);
            o.writeUTF(hash);
        }));
        Packet res = conn.await(PacketType.LOGIN_RES, 2000);
        if (res == null) {
            throw new IllegalStateException("Khong dang nhap duoc: " + user);
        }
        DataInputStream r = PacketUtils.payloadReader(res.getPayload());
        if (r.readByte() != 1) {
            throw new IllegalStateException("Dang nhap that bai: " + user);
        }
    }

    /**
     * Vào lớp, có mở sẵn cổng UDP và báo cổng đó cho server.
     * <p>Dùng CHUNG một DatagramSocket cho cả gửi và nhận — nếu không, server không nhận ra
     * người gửi và sẽ xảy ra vọng (xem AudioRelayServer).</p>
     */
    private static void joinRoom(TestClient.Conn conn, int roomId) throws Exception {
        conn.openAudioSocket();
        int udpPort = conn.audioSocket.getLocalPort();
        conn.send(PacketType.JOIN_ROOM_REQ, PacketUtils.buildPayload(o -> {
            o.writeInt(roomId);
            o.writeInt(udpPort);
        }));
        conn.await(PacketType.JOIN_ROOM_RES, 3000);
    }

    private static void sendFrames(TestClient.Conn teacher, int count) throws Exception {
        byte[] frame = TestClient.fakeJpegFrame();
        for (int i = 0; i < count; i++) {
            frame[100] = (byte) i;
            teacher.send(PacketType.SCREEN_FRAME, frame);
            Thread.sleep(100);                    // 10 FPS
        }
    }

    /** Đếm frame nhận được trên client có luồng đọc nền. */
    private static int drainFrames(TestClient.Conn conn, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        int count = 0;
        while (System.currentTimeMillis() < deadline) {
            Packet p = conn.inbox.poll(50, TimeUnit.MILLISECONDS);
            if (p == null) {
                continue;
            }
            if (p.getType() == PacketType.SCREEN_FRAME) {
                count++;
            }
        }
        return count;
    }

    /**
     * Đọc chậm ở tầng socket — đây mới thật sự giả lập được client mạng chậm.
     * Đọc 1 frame rồi nghỉ {@code sleepMs}, nên tốc độ tiêu thụ thấp hơn tốc độ server gửi
     * ⇒ đệm socket đầy ⇒ luồng ghi của server bị chặn ⇒ hàng đợi frame đầy ⇒ server bỏ frame.
     */
    private static int readSlow(TestClient.Conn conn, long millis, int sleepMs) throws Exception {
        long deadline = System.currentTimeMillis() + millis;
        int count = 0;
        while (System.currentTimeMillis() < deadline) {
            Packet p = conn.readRawOrNull(400);
            if (p == null) {
                continue;
            }
            if (p.getType() == PacketType.SCREEN_FRAME) {
                count++;
                Thread.sleep(sleepMs);
            }
        }
        return count;
    }

    /** Chạy đồng thời: giáo viên gửi thoại, học sinh nhận và đo mất gói. */
    private static int[] runAudioTest(TestClient.Conn student, TestClient.Conn teacher,
                                      int roomId, int seconds) throws Exception {
        final int[] result = {0, 0};

        Thread receiver = new Thread(() -> {
            try {
                DatagramSocket socket = student.audioSocket;
                socket.setSoTimeout(300);
                byte[] buf = new byte[Protocol.UDP_RECV_BUFFER_BYTES];
                long deadline = System.currentTimeMillis() + seconds * 1000L + 2000;
                int received = 0;
                int lost = 0;
                int lastSeq = -1;
                int maxSeq = -1;
                while (System.currentTimeMillis() < deadline) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    try {
                        socket.receive(p);
                    } catch (java.net.SocketTimeoutException e) {
                        continue;
                    }
                    if (p.getLength() < Protocol.UDP_PACKET_BYTES) {
                        continue;
                    }
                    int seq = Protocol.readIntBigEndian(buf, Protocol.UDP_OFFSET_SEQ_NO);
                    received++;
                    if (lastSeq >= 0 && seq > lastSeq + 1) {
                        lost += seq - lastSeq - 1;
                    }
                    lastSeq = seq;
                    maxSeq = Math.max(maxSeq, seq);
                }
                int expected = maxSeq + 1;
                result[0] = received;
                result[1] = (expected == 0) ? 100 : (int) Math.round(lost * 100.0 / expected);
            } catch (Exception e) {
                System.out.println("   [loi] nhan thoai: " + e.getMessage());
            }
        }, "fake-student-audio");
        receiver.start();

        Thread.sleep(200);
        sendAudio(teacher, roomId, seconds);
        receiver.join();
        return result;
    }

    /** Giáo viên đã ở trong lớp rồi — ở đây chỉ bật mic, nói, rồi tắt mic. */
    private static void sendAudio(TestClient.Conn teacher, int roomId, int seconds)
            throws Exception {
        teacher.send(PacketType.AUDIO_ON, PacketUtils.buildPayload(o -> o.writeInt(roomId)));

        byte[] packet = new byte[Protocol.UDP_PACKET_BYTES];
        TestClient.writeInt(packet, Protocol.UDP_OFFSET_ROOM_ID, roomId);
        InetAddress target = InetAddress.getByName(HOST);
        int total = seconds * 50;
        long t0 = System.nanoTime();
        for (int seq = 0; seq < total; seq++) {
            TestClient.writeInt(packet, Protocol.UDP_OFFSET_SEQ_NO, seq);
            teacher.audioSocket.send(
                    new DatagramPacket(packet, packet.length, target, Protocol.UDP_PORT));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            long targetMs = (seq + 1) * 20L;
            if (targetMs > elapsedMs) {
                Thread.sleep(targetMs - elapsedMs);
            }
        }
        teacher.send(PacketType.AUDIO_OFF, PacketUtils.buildPayload(o -> o.writeInt(roomId)));
    }

    // ==================================================================
    // Tiện ích
    // ==================================================================

    private static ClientHandler findMember(Room room, String username) {
        if (room == null) {
            return null;
        }
        for (ClientHandler c : room.snapshotMembers()) {
            if (username.equals(c.getUsername())) {
                return c;
            }
        }
        return null;
    }

    private static int readRoomId(Packet res) {
        if (res == null) {
            return -1;
        }
        try {
            DataInputStream r = PacketUtils.payloadReader(res.getPayload());
            r.readByte();
            r.readUTF();
            return r.readInt();
        } catch (Exception e) {
            return -1;
        }
    }

    private static int readUserCount(Packet userList) {
        if (userList == null) {
            return -1;
        }
        try {
            return PacketUtils.payloadReader(userList.getPayload()).readInt();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("--- " + title + " ---");
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passCount++;
        } else {
            failCount++;
        }
        System.out.println("   " + (ok ? "[DAT]  " : "[KHONG]") + " " + name);
    }
}
