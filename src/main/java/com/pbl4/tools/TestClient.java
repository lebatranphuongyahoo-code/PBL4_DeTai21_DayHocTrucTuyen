package com.pbl4.tools;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;
import com.pbl4.common.Sha256;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client giả lập để kiểm thử server — <b>công cụ test, KHÔNG nằm trong sản phẩm</b>.
 *
 * <pre>
 * java -cp target/classes com.pbl4.tools.TestClient &lt;host&gt; &lt;port&gt; [--as user:pass:ROLE] &lt;lenh&gt; [tham so]
 *
 *   hold &lt;giay&gt;                        giu ket noi (test 3 ket noi dong thoi)
 *   auth &lt;user&gt; &lt;pass&gt; [ROLE]         dang ky (neu chua co) roi dang nhap
 *   rooms                              xin danh sach lop
 *   create &lt;ten lop&gt;                   tao lop (chi TEACHER)
 *   join &lt;roomId&gt;                      vao lop voi cong UDP gia
 *   leave &lt;roomId&gt;                     roi lop
 *   chat &lt;roomId&gt; &lt;noi dung&gt;           gui chat
 *   screen-start &lt;roomId&gt;              xin chia se man hinh (chi TEACHER)
 *   screen-stop &lt;roomId&gt;               dung chia se
 *   screen-send &lt;roomId|new&gt; &lt;giay&gt; [giayChoTruoc=10]  gui frame JPEG gia 62 KB o 10 FPS
 *   screen-recv &lt;roomId&gt; &lt;giay&gt; [ms]   nhan frame; ms&gt;0 = gia lap client MANG CHAM
 *   audio-send &lt;roomId&gt; &lt;giay&gt;         gui goi thoai 648 byte o 50 goi/giay
 *   audio-recv &lt;roomId&gt; &lt;giay&gt;         nhan thoai, do ti le mat goi va goi sai thu tu
 * </pre>
 *
 * <p>Lưu ý: số đo độ trễ của {@code screen-recv} chỉ chính xác khi bên gửi và bên nhận chạy
 * trên <b>cùng một máy</b> (cùng đồng hồ hệ thống).</p>
 */
public class TestClient {

    public static void main(String[] args) throws Exception {
        java.util.Locale.setDefault(java.util.Locale.US);   // so lieu in ra dung dau cham
        if (args.length < 3) {
            usage();
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        String asSpec = null;
        int cmdIdx = 2;
        if ("--as".equals(args[2])) {
            if (args.length < 5) {
                usage();
                return;
            }
            asSpec = args[3];
            cmdIdx = 4;
        }
        String command = args[cmdIdx];
        String[] a = Arrays.copyOfRange(args, cmdIdx + 1, args.length);

        // Chế độ đọc chậm phải dùng kết nối KHÔNG có luồng đọc nền (xem Conn ở dưới).
        boolean needRaw = command.equals("screen-recv") && a.length > 2
                && Integer.parseInt(a[2]) > 0;

        try (Conn conn = new Conn(host, port, Protocol.UDP_PORT, !needRaw)) {
            autoLoginIfNeeded(conn, command, asSpec);
            switch (command) {
                case "hold" -> cmdHold(conn, a);
                case "auth" -> cmdAuth(conn, a);
                case "rooms" -> cmdRooms(conn);
                case "create" -> cmdCreate(conn, a);
                case "join" -> cmdJoin(conn, a, 0);
                case "leave" -> cmdLeave(conn, a);
                case "chat" -> cmdChat(conn, a);
                case "screen-start" -> cmdScreenStart(conn, a);
                case "screen-stop" -> cmdScreenStop(conn, a);
                case "screen-send" -> cmdScreenSend(conn, a);
                case "screen-recv" -> cmdScreenRecv(conn, a);
                case "audio-send" -> cmdAudioSend(conn, a);
                case "audio-recv" -> cmdAudioRecv(conn, a);
                default -> usage();
            }
        }
        System.out.println("[test] xong.");
        System.exit(0);
    }

    private static void usage() {
        System.out.println("Dung: TestClient <host> <port> [--as user:pass:ROLE] <lenh> [tham so]");
        System.out.println("Cac lenh: hold | auth | rooms | create | join | leave | chat");
        System.out.println("          screen-start | screen-stop | screen-send | screen-recv");
        System.out.println("          audio-send | audio-recv");
        System.out.println("Luu y: do tre frame cua screen-recv chi dung khi ben gui va ben nhan cung mot may.");
    }

    private static void autoLoginIfNeeded(Conn conn, String command, String asSpec) throws Exception {
        if ("auth".equals(command)) {
            return; // auth giu hanh vi cu
        }
        if (asSpec != null) {
            String[] parts = asSpec.split(":", 3);
            if (parts.length < 2) {
                System.out.println("[test] cu phap --as sai (can user:pass[:ROLE]): " + asSpec);
                System.exit(1);
            }
            String user = parts[0];
            String pass = parts[1];
            String role = parts.length >= 3 ? parts[2] : Protocol.ROLE_STUDENT;
            ensureLogin(conn, user, pass, role);
            return;
        }
        switch (command) {
            case "hold", "rooms" -> {
                // khong dang nhap
            }
            case "create", "screen-send", "screen-start", "screen-stop", "audio-send" -> {
                int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
                ensureLogin(conn, "gv" + suffix, "123456", Protocol.ROLE_TEACHER);
            }
            case "join", "leave", "chat", "screen-recv", "audio-recv" -> {
                int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
                ensureLogin(conn, "hs" + suffix, "123456", Protocol.ROLE_STUDENT);
            }
            default -> {
                // cac lenh khac: khong dang nhap
            }
        }
    }

    private static void ensureLogin(Conn conn, String user, String pass, String role) throws Exception {
        try {
            conn.registerAndLogin(user, pass, role);
            System.out.println("[test] dang nhap: " + user + " (" + role + ")");
        } catch (IllegalStateException e) {
            System.out.println("[test] dang nhap that bai: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Gửi JOIN_ROOM_REQ và kiểm tra ok=1; nếu thất bại thì in lý do và thoát mã 1. */
    private static void ensureJoinRoom(Conn conn, int roomId, int udpPort) throws Exception {
        if (!conn.join(roomId, udpPort)) {
            System.out.println("[test] vao lop that bai: roomId=" + roomId);
            System.exit(1);
        }
    }

    // ==================================================================
    // Các lệnh
    // ==================================================================

    /** Mở kết nối và giữ nguyên — dùng để chứng minh server nhận nhiều kết nối đồng thời. */
    private static void cmdHold(Conn conn, String[] a) throws Exception {
        int seconds = a.length > 0 ? Integer.parseInt(a[0]) : 10;
        System.out.println("[test] da ket noi, giu trong " + seconds + " giay...");
        Thread.sleep(seconds * 1000L);
    }

    private static void cmdAuth(Conn conn, String[] a) throws Exception {
        String user = a[0];
        String pass = a[1];
        String role = a.length > 2 ? a[2] : Protocol.ROLE_STUDENT;
        ensureLogin(conn, user, pass, role);

        conn.send(PacketType.ROOM_LIST_REQ, new byte[0]);
        conn.await(PacketType.ROOM_LIST_RES, 3000);
    }

    private static void cmdRooms(Conn conn) throws Exception {
        conn.send(PacketType.ROOM_LIST_REQ, new byte[0]);
        Packet res = conn.await(PacketType.ROOM_LIST_RES, 3000);
        if (res == null) {
            System.out.println("[test] khong nhan duoc ROOM_LIST_RES");
            return;
        }
        DataInputStream r = PacketUtils.payloadReader(res.getPayload());
        int n = r.readInt();
        System.out.println("[test] co " + n + " lop:");
        for (int i = 0; i < n; i++) {
            System.out.printf("   ma=%d  ten='%s'  GV=%s  soNguoi=%d%n",
                    r.readInt(), r.readUTF(), r.readUTF(), r.readInt());
        }
    }

    private static void cmdCreate(Conn conn, String[] a) throws Exception {
        conn.send(PacketType.CREATE_ROOM_REQ,
                PacketUtils.buildPayload(o -> o.writeUTF(a[0])));
        Packet res = conn.await(PacketType.CREATE_ROOM_RES, 3000);
        if (res == null) {
            System.out.println("[test] khong nhan duoc CREATE_ROOM_RES");
            return;
        }
        DataInputStream r = PacketUtils.payloadReader(res.getPayload());
        int ok = r.readByte();
        String msg = r.readUTF();
        int roomId = r.readInt();
        System.out.println("[test] tao lop: ok=" + ok + " | " + msg + " | roomId=" + roomId);
    }

    private static void cmdJoin(Conn conn, String[] a, int udpPort) throws Exception {
        int roomId = Integer.parseInt(a[0]);
        conn.send(PacketType.JOIN_ROOM_REQ, PacketUtils.buildPayload(o -> {
            o.writeInt(roomId);
            o.writeInt(udpPort);
        }));
        Packet res = conn.await(PacketType.JOIN_ROOM_RES, 3000);
        System.out.println("[test] vao lop: " + describe(res));
        Packet userList = conn.await(PacketType.USER_LIST, 2000);
        if (userList != null) {
            printUserList(userList);
        }
    }

    private static void cmdLeave(Conn conn, String[] a) throws Exception {
        int roomId = Integer.parseInt(a[0]);
        conn.send(PacketType.LEAVE_ROOM_REQ,
                PacketUtils.buildPayload(o -> o.writeInt(roomId)));
        Packet userList = conn.await(PacketType.USER_LIST, 2000);
        if (userList != null) {
            System.out.println("[test] sau khi roi lop:");
            printUserList(userList);
        }
    }

    private static void cmdChat(Conn conn, String[] a) throws Exception {
        int roomId = Integer.parseInt(a[0]);
        String text = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        conn.send(PacketType.CHAT_TEXT, PacketUtils.buildPayload(o -> {
            o.writeInt(roomId);
            o.writeUTF(text);
        }));
        Packet echoed = conn.await(PacketType.CHAT_TEXT, 2000);
        if (echoed != null) {
            DataInputStream r = PacketUtils.payloadReader(echoed.getPayload());
            System.out.println("[test] chat: lop=" + r.readInt() + " | " + r.readUTF());
        }
    }

    private static void cmdScreenStart(Conn conn, String[] a) throws Exception {
        int roomId = Integer.parseInt(a[0]);
        conn.send(PacketType.SCREEN_START,
                PacketUtils.buildPayload(o -> o.writeInt(roomId)));
        Packet p = conn.awaitEither(PacketType.SCREEN_START, PacketType.ERROR, 2000);
        System.out.println("[test] xin chia se man hinh -> "
                + (p == null ? "khong phan hoi" : p.typeName()));
        if (p != null && p.getType() == PacketType.ERROR) {
            System.out.println("        " + PacketUtils.payloadReader(p.getPayload()).readUTF());
        }
    }

    private static void cmdScreenStop(Conn conn, String[] a) throws Exception {
        int roomId = Integer.parseInt(a[0]);
        conn.send(PacketType.SCREEN_STOP,
                PacketUtils.buildPayload(o -> o.writeInt(roomId)));
        Thread.sleep(300);
        System.out.println("[test] da gui SCREEN_STOP");
    }

    /** Giả lập máy giáo viên: gửi frame JPEG 62 KB ở 10 FPS. */
    private static void cmdScreenSend(Conn conn, String[] a) throws Exception {
        int roomId;
        if ("new".equalsIgnoreCase(a[0])) {
            try {
                roomId = conn.createRoom("Thi nghiem 4.2");
            } catch (IllegalStateException e) {
                System.out.println("[test] tao lop that bai: " + e.getMessage());
                System.exit(1);
                return;
            }
            System.out.println("ROOM_ID=" + roomId);
        } else {
            roomId = Integer.parseInt(a[0]);
        }
        int seconds = Integer.parseInt(a[1]);
        int waitBeforeSec = a.length > 2 ? Integer.parseInt(a[2]) : 10;

        ensureJoinRoom(conn, roomId, 0);

        System.out.println("[test] cho " + waitBeforeSec + " giay de hoc sinh vao lop...");
        if (waitBeforeSec > 0) {
            Thread.sleep(waitBeforeSec * 1000L);
        }

        final int targetRoomId = roomId;
        conn.send(PacketType.SCREEN_START,
                PacketUtils.buildPayload(o -> o.writeInt(targetRoomId)));
        Packet p = conn.awaitEither(PacketType.SCREEN_START, PacketType.ERROR, 2000);
        if (p == null || p.getType() == PacketType.ERROR) {
            System.out.println("[test] khong duoc phep chia se man hinh!");
            return;
        }

        byte[] frame = fakeJpegFrame();
        ByteBuffer frameBuf = ByteBuffer.wrap(frame);
        int total = seconds * 10;
        long t0 = System.nanoTime();
        for (int i = 0; i < total; i++) {
            frameBuf.putLong(2, System.currentTimeMillis());
            conn.send(PacketType.SCREEN_FRAME, frame);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            long targetMs = (i + 1) * 100L;        // 10 FPS = 100 ms/frame
            if (targetMs > elapsedMs) {
                Thread.sleep(targetMs - elapsedMs);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[test] da gui %d frame | %.1f KB/frame | %.1f fps | %.1f Mbps%n",
                total, frame.length / 1024.0, total * 1000.0 / ms,
                total * (double) frame.length * 8 / (ms / 1000.0) / 1_000_000);
    }

    /**
     * Nhận frame. Nếu {@code slowReadMs > 0} thì đọc chậm — đây là cách giả lập
     * <b>client mạng chậm</b> để chứng minh cơ chế drop-oldest ở bước 3.3.
     */
    private static void cmdScreenRecv(Conn conn, String[] a) throws Exception {
        int roomId = Integer.parseInt(a[0]);
        int seconds = Integer.parseInt(a[1]);
        int slowReadMs = a.length > 2 ? Integer.parseInt(a[2]) : 0;

        ensureJoinRoom(conn, roomId, 0);

        if (slowReadMs > 0) {
            System.out.println("[test] CHE DO MANG CHAM: doc cham " + slowReadMs + " ms/frame");
        }

        long deadline = System.currentTimeMillis() + seconds * 1000L;
        int frames = 0;
        long bytes = 0;
        List<Long> latencies = new ArrayList<>();
        long t0 = System.nanoTime();

        while (System.currentTimeMillis() < deadline) {
            Packet p = conn.poll(PacketType.SCREEN_FRAME, 300);
            if (p == null) {
                continue;
            }
            long now = System.currentTimeMillis();
            byte[] payload = p.getPayload();
            if (payload.length >= 10) {
                long sent = ByteBuffer.wrap(payload).getLong(2);
                latencies.add(now - sent);
            }
            frames++;
            bytes += payload.length;
            if (slowReadMs > 0) {
                Thread.sleep(slowReadMs);      // gia lap duong truyen cham
            }
        }
        double sec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("[test] da nhan %d frame | %.1f KB/frame | %.1f fps | %.2f MB%n",
                frames, frames == 0 ? 0 : bytes / 1024.0 / frames, frames / sec, bytes / 1e6);

        int n = latencies.size();
        if (n == 0) {
            System.out.printf("[test] do tre frame (ms): tb=%.1f | p50=%d | p95=%d | max=%d | n=%d%n",
                    0.0, 0L, 0L, 0L, 0);
        } else {
            Collections.sort(latencies);
            long sum = 0;
            for (long v : latencies) {
                sum += v;
            }
            double avg = sum / (double) n;
            long p50 = latencies.get(n / 2);
            long p95 = latencies.get((int) (n * 0.95));
            long max = latencies.get(n - 1);
            System.out.printf("[test] do tre frame (ms): tb=%.1f | p50=%d | p95=%d | max=%d | n=%d%n",
                    avg, p50, p95, max, n);
        }
    }

    /** Giả lập máy giáo viên nói: gửi gói 648 byte ở 50 gói/giây (20 ms/gói). */
    private static void cmdAudioSend(Conn conn, String[] a) throws Exception {
        int roomId = Integer.parseInt(a[0]);
        int seconds = Integer.parseInt(a[1]);

        // QUAN TRONG: dung DUNG mot DatagramSocket cho ca gui va nhan,
        // va bao chinh cong do cho server trong JOIN_ROOM_REQ.
        // Neu gui tu cong khac, server khong nhan ra nguoi gui -> se bi VONG.
        conn.openAudioSocket();
        int myPort = conn.audioSocket.getLocalPort();

        ensureJoinRoom(conn, roomId, myPort);
        conn.send(PacketType.AUDIO_ON, PacketUtils.buildPayload(o -> o.writeInt(roomId)));
        System.out.println("[test] bao cong UDP " + myPort + " va bat mic, noi trong " + seconds + " giay");

        byte[] packet = new byte[Protocol.UDP_PACKET_BYTES];
        writeInt(packet, Protocol.UDP_OFFSET_ROOM_ID, roomId);
        InetAddress server = InetAddress.getByName(conn.host);
        int total = seconds * 50;
        long t0 = System.nanoTime();
        for (int seq = 0; seq < total; seq++) {
            writeInt(packet, Protocol.UDP_OFFSET_SEQ_NO, seq);
            // Gia lap song am: day byte thay doi
            for (int i = 0; i < Protocol.UDP_PCM_BYTES; i++) {
                packet[Protocol.UDP_OFFSET_PCM + i] = (byte) (seq + i);
            }
            conn.audioSocket.send(new DatagramPacket(packet, packet.length, server, conn.udpPort));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            long targetMs = (seq + 1) * 20L;
            if (targetMs > elapsedMs) {
                Thread.sleep(targetMs - elapsedMs);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[test] da gui %d goi thoai | %.1fs | %.2f MB%n",
                total, ms / 1000.0, total * (double) packet.length / 1e6);

        conn.send(PacketType.AUDIO_OFF, PacketUtils.buildPayload(o -> o.writeInt(roomId)));
    }

    /** Nhận thoại và đo tỉ lệ mất gói + số gói đến sai thứ tự nhờ trường seqNo. */
    private static void cmdAudioRecv(Conn conn, String[] a) throws Exception {
        int roomId = Integer.parseInt(a[0]);
        int seconds = Integer.parseInt(a[1]);

        conn.openAudioSocket();
        int myPort = conn.audioSocket.getLocalPort();
        ensureJoinRoom(conn, roomId, myPort);
        System.out.println("[test] dang nghe thoai tren cong UDP " + myPort
                + " trong " + seconds + " giay...");

        byte[] buffer = new byte[Protocol.UDP_RECV_BUFFER_BYTES];
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        conn.audioSocket.setSoTimeout(300);

        int received = 0;
        int expected = 0;
        int lost = 0;
        int outOfOrder = 0;
        int lastSeq = -1;
        long bytes = 0;
        long firstSeqTime = 0;
        int maxSeq = -1;

        while (System.currentTimeMillis() < deadline) {
            DatagramPacket p = new DatagramPacket(buffer, buffer.length);
            try {
                conn.audioSocket.receive(p);
            } catch (java.net.SocketTimeoutException e) {
                continue;
            }
            if (p.getLength() < Protocol.UDP_PACKET_BYTES) {
                continue;
            }
            int seq = Protocol.readIntBigEndian(buffer, Protocol.UDP_OFFSET_SEQ_NO);
            if (firstSeqTime == 0) {
                firstSeqTime = System.currentTimeMillis();
            }
            received++;
            bytes += p.getLength();
            if (lastSeq >= 0) {
                if (seq <= lastSeq) {
                    outOfOrder++;                     // den muon / trung
                } else if (seq > lastSeq + 1) {
                    lost += seq - lastSeq - 1;        // co khoang trong = mat goi
                }
            }
            lastSeq = seq;
            maxSeq = Math.max(maxSeq, seq);
        }

        expected = maxSeq + 1;
        System.out.println("[test] ===== KET QUA DO THOAI =====");
        System.out.println("   goi nhan duoc      : " + received);
        System.out.println("   goi ky vong        : " + expected + " (theo so thu tu lon nhat)");
        System.out.println("   goi mat            : " + lost
                + String.format(" (%.2f%%)", expected == 0 ? 0 : lost * 100.0 / expected));
        System.out.println("   goi sai thu tu     : " + outOfOrder);
        System.out.printf("   tong du lieu       : %.2f MB%n", bytes / 1e6);
    }

    // ==================================================================
    // Ha tang
    // ==================================================================

    /** Một kết nối TCP tới server.
     *
     * <p>Hai chế độ:</p>
     * <ul>
     *   <li><b>autoRead = true</b> (mặc định): có luồng đọc nền hút gói vào hàng đợi — giống client thật.</li>
     *   <li><b>autoRead = false</b>: KHÔNG có luồng nền, bên gọi tự đọc từ socket.
     *       Bắt buộc phải dùng chế độ này để giả lập <b>client mạng chậm</b>: nếu có luồng nền
     *       hút dữ liệu liên tục thì đệm socket không bao giờ đầy, server không bao giờ bỏ frame,
     *       và thí nghiệm drop-oldest sẽ không chứng minh được gì.</li>
     * </ul>
     */
    public static class Conn implements Closeable {

        public final String host;
        public final int tcpPort;
        public final int udpPort;
        public final Socket socket;
        public final DataInputStream in;
        public final DataOutputStream out;
        public final BlockingQueue<Packet> inbox = new LinkedBlockingQueue<>();
        public final AtomicBoolean open = new AtomicBoolean(true);
        public final AtomicInteger framesReceived = new AtomicInteger();
        private final boolean autoRead;
        public DatagramSocket audioSocket;

        public Conn(String host, int tcpPort, int udpPort) throws IOException {
            this(host, tcpPort, udpPort, true);
        }

        public Conn(String host, int tcpPort, int udpPort, boolean autoRead) throws IOException {
            this.host = host;
            this.tcpPort = tcpPort;
            this.udpPort = udpPort;
            this.autoRead = autoRead;
            socket = new Socket(host, tcpPort);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            if (autoRead) {
                Thread reader = new Thread(this::readLoop, "testclient-reader");
                reader.setDaemon(true);
                reader.start();
            }
        }

        /** REGISTER (bỏ qua trùng tên) rồi LOGIN. Ném IllegalStateException nếu đăng nhập thất bại. */
        public void registerAndLogin(String user, String password, String role) throws Exception {
            String hash = Sha256.hex(password);
            send(PacketType.REGISTER_REQ, PacketUtils.buildPayload(o -> {
                o.writeUTF(user);
                o.writeUTF(hash);
                o.writeUTF(role);
            }));
            await(PacketType.REGISTER_RES, 3000);

            send(PacketType.LOGIN_REQ, PacketUtils.buildPayload(o -> {
                o.writeUTF(user);
                o.writeUTF(hash);
            }));
            Packet login = await(PacketType.LOGIN_RES, 3000);
            if (login == null) {
                throw new IllegalStateException("khong nhan duoc LOGIN_RES");
            }
            DataInputStream r = PacketUtils.payloadReader(login.getPayload());
            int ok = r.readByte();
            String msg = r.readUTF();
            if (ok == 0) {
                throw new IllegalStateException(msg);
            }
        }

        /** CREATE_ROOM_REQ, trả roomId; ném IllegalStateException nếu ok=0 hoặc hết giờ. */
        public int createRoom(String name) throws Exception {
            send(PacketType.CREATE_ROOM_REQ, PacketUtils.buildPayload(o -> o.writeUTF(name)));
            Packet res = await(PacketType.CREATE_ROOM_RES, 3000);
            if (res == null) {
                throw new IllegalStateException("khong nhan duoc CREATE_ROOM_RES");
            }
            DataInputStream r = PacketUtils.payloadReader(res.getPayload());
            int ok = r.readByte();
            String msg = r.readUTF();
            int roomId = r.readInt();
            if (ok == 0) {
                throw new IllegalStateException(msg);
            }
            return roomId;
        }

        /** JOIN_ROOM_REQ(roomId, udpPort), chờ JOIN_ROOM_RES; trả true nếu ok=1. */
        public boolean join(int roomId, int udpPort) throws Exception {
            send(PacketType.JOIN_ROOM_REQ, PacketUtils.buildPayload(o -> {
                o.writeInt(roomId);
                o.writeInt(udpPort);
            }));
            Packet res = await(PacketType.JOIN_ROOM_RES, 3000);
            if (res == null) {
                return false;
            }
            DataInputStream r = PacketUtils.payloadReader(res.getPayload());
            int ok = r.readByte();
            r.readUTF();
            return ok == 1;
        }

        /**
         * Đọc thẳng một gói từ socket, có timeout ở ĐẦU gói.
         *
         * <p>An toàn vì timeout được đặt ở ranh giới gói: nếu chưa có byte nào tới thì
         * {@code readInt()} ném timeout mà chưa tiêu thụ byte nào, luồng dữ liệu còn nguyên.</p>
         *
         * @return gói đọc được, hoặc {@code null} nếu hết thời gian chờ
         */
        public Packet readRawOrNull(int timeoutMs) throws IOException {
            socket.setSoTimeout(timeoutMs);
            try {
                return PacketUtils.readPacket(in);
            } catch (java.net.SocketTimeoutException e) {
                return null;
            }
        }

        /** Đọc thẳng cho tới khi gặp gói đúng loại (bỏ qua các loại khác). */
        public Packet awaitRaw(byte type, long timeoutMs) throws IOException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                Packet p = readRawOrNull(400);
                if (p == null) {
                    continue;
                }
                if (p.getType() == type) {
                    return p;
                }
            }
            return null;
        }

        public void openAudioSocket() throws IOException {
            if (audioSocket == null) {
                audioSocket = new DatagramSocket();     // cong ngau nhien do HDH cap
            }
        }

        private void readLoop() {
            try {
                while (open.get()) {
                    Packet p = PacketUtils.readPacket(in);
                    if (p.getType() == PacketType.SCREEN_FRAME) {
                        framesReceived.incrementAndGet();
                    }
                    inbox.offer(p);
                }
            } catch (IOException e) {
                open.set(false);
            }
        }

        public void send(byte type, byte[] payload) throws IOException {
            PacketUtils.sendPacket(out, type, payload);
        }

        /** Chờ một gói đúng loại; các gói khác bị bỏ qua. */
        public Packet poll(byte type, long timeoutMs) throws InterruptedException {
            if (!autoRead) {
                try {
                    return awaitRaw(type, timeoutMs);
                } catch (IOException e) {
                    return null;
                }
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                Packet p = inbox.poll(50, TimeUnit.MILLISECONDS);
                if (p == null) {
                    continue;
                }
                if (p.getType() == type) {
                    return p;
                }
            }
            return null;
        }

        public Packet await(byte type, long timeoutMs) throws InterruptedException {
            return poll(type, timeoutMs);
        }

        /** Chờ một trong hai loại gói — dùng khi cần bắt cả kết quả lẫn gói ERROR. */
        public Packet awaitEither(byte typeA, byte typeB, long timeoutMs) throws InterruptedException {
            if (!autoRead) {
                long deadline = System.currentTimeMillis() + timeoutMs;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        Packet p = readRawOrNull(400);
                        if (p == null) {
                            continue;
                        }
                        if (p.getType() == typeA || p.getType() == typeB) {
                            return p;
                        }
                    } catch (IOException e) {
                        return null;
                    }
                }
                return null;
            }
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                Packet p = inbox.poll(50, TimeUnit.MILLISECONDS);
                if (p == null) {
                    continue;
                }
                if (p.getType() == typeA || p.getType() == typeB) {
                    return p;
                }
            }
            return null;
        }

        @Override
        public void close() {
            open.set(false);
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
            if (audioSocket != null) {
                audioSocket.close();
            }
        }
    }

    // ==================================================================
    // Tiện ích nhỏ
    // ==================================================================

    /** Mô tả nội dung một gói LOGIN_RES / REGISTER_RES / JOIN_ROOM_RES. */
    static String describe(Packet p) {
        if (p == null) {
            return "khong phan hoi";
        }
        try {
            DataInputStream r = PacketUtils.payloadReader(p.getPayload());
            int ok = r.readByte();
            String msg = r.readUTF();
            String extra = "";
            if (p.getType() == PacketType.LOGIN_RES && r.available() > 0) {
                extra = " | role=" + r.readUTF();
            }
            return p.typeName() + " ok=" + ok + " | " + msg + extra;
        } catch (IOException e) {
            return p.typeName() + " (khong doc duoc payload)";
        }
    }

    static void printUserList(Packet userList) throws IOException {
        DataInputStream r = PacketUtils.payloadReader(userList.getPayload());
        int n = r.readInt();
        System.out.println("   USER_LIST: " + n + " nguoi");
        for (int i = 0; i < n; i++) {
            System.out.println("      - " + r.readUTF() + " (" + r.readUTF() + ")");
        }
    }

    /** Frame JPEG giả 62 KB — server chỉ chuyển tiếp byte thô nên không cần ảnh thật. */
    static byte[] fakeJpegFrame() {
        byte[] frame = new byte[62_000];
        ThreadLocalRandom.current().nextBytes(frame);
        // hai byte dau cho giong header JPEG cho de nhan dien trong log
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xD8;
        return frame;
    }

    static void writeInt(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value >>> 24);
        buf[offset + 1] = (byte) (value >>> 16);
        buf[offset + 2] = (byte) (value >>> 8);
        buf[offset + 3] = (byte) value;
    }
}
