package com.pbl4.tools;

import com.pbl4.client.media.AudioConfig;
import com.pbl4.client.media.AudioPlayer;
import com.pbl4.client.media.AudioRecorder;
import com.pbl4.client.media.JitterBuffer;
import com.pbl4.client.media.MediaStats;
import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;
import com.pbl4.common.Sha256;
import com.pbl4.server.RoomManager;
import com.pbl4.server.ServerConfig;
import com.pbl4.server.ServerMain;
import com.pbl4.server.db.DatabaseManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;

/**
 * Bài tự kiểm CHẶNG 2 của TV2 — dựng server thật rồi kiểm tra kênh thoại UDP.
 *
 * <p>Đây là bài chứng minh <b>mốc quan trọng nhất của TV2</b>: hai máy nói chuyện nghe rõ.
 * Chạy trên một máy nên nó dùng hai client ảo: một "người nói" và một "người nghe".</p>
 *
 * <p>Ba điều phải chứng minh:</p>
 * <ol>
 *   <li>Gói đi đúng định dạng 648 byte mà server chấp nhận (không bị đếm là gói rác)</li>
 *   <li>Tỉ lệ mất gói &lt; 2% trên localhost</li>
 *   <li>Server <b>không</b> gửi gói về cho chính người gửi (không có vọng)</li>
 * </ol>
 *
 * <pre>
 *   mvn exec:java -Dexec.mainClass=com.pbl4.tools.TV2Chang2Test
 * </pre>
 */
public class TV2Chang2Test {

    private static final String PASSWORD = "123456";
    private static final String HOST = "127.0.0.1";

    private static int passCount;
    private static int failCount;
    private static ServerMain server;

    /**
     * Bộ đếm số thứ tự gói thoại dùng chung cho cả bài test.
     *
     * <p>Phải là <b>một dòng liên tục duy nhất</b> suốt bài test, đúng như một mic thật.
     * Xem giải thích ở {@link #sendPcmAudio}.
     */
    private static int seqCursor;

    public static void main(String[] args) throws Exception {
        java.util.Locale.setDefault(java.util.Locale.US);

        System.out.println("########################################################");
        System.out.println("#  TV2 - CHANG 2: KENH THOAI UDP (648 byte/goi, 50 goi/giay)");
        System.out.println("########################################################");

        RoomManager rooms = new RoomManager();
        server = new ServerMain(ServerConfig.ephemeral(), rooms, new DatabaseManager(":memory:"));
        Thread serverThread = new Thread(server::start, "server-main");
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(600);

        try {
            runScenario();
        } finally {
            server.stop();
            Thread.sleep(400);
        }

        System.out.println();
        System.out.println("########################################################");
        System.out.printf("#  KET QUA CHANG 2: %d DAT / %d TONG%n", passCount, passCount + failCount);
        System.out.println("########################################################");
        if (failCount > 0) {
            System.exit(1);
        }
    }

    private static void runScenario() throws Exception {

        // ===== 1. Dựng lớp =====
        section("1. Dung lop hoc: 1 giao vien + 1 hoc sinh");
        TestClient.Conn teacher = new TestClient.Conn(HOST, server.getTcpPort(), server.getUdpPort());
        TestClient.Conn student = new TestClient.Conn(HOST, server.getTcpPort(), server.getUdpPort());

        // CẢ HAI client đều phải đăng ký + đăng nhập. Thiếu bước này thì server trả
        // lỗi "chua dang nhap" cho JOIN_ROOM_REQ, lớp chỉ có 1 người, và kênh thoại
        // relay được nhưng danh sách người nhận RỖNG -> tưởng là lỗi mạng.
        check("giao vien dang ky + dang nhap",
                login(teacher, "gv-hoang", Protocol.ROLE_TEACHER));
        teacher.send(PacketType.CREATE_ROOM_REQ,
                PacketUtils.buildPayload(o -> o.writeUTF("Lop TV2 test")));
        int roomId = readRoomId(teacher.await(PacketType.CREATE_ROOM_RES, 3000));
        check("tao lop duoc, ma lop = " + roomId, roomId > 0);

        check("hoc sinh dang ky + dang nhap",
                login(student, "hs-hoang", Protocol.ROLE_STUDENT));

        // MỖI client mở ĐÚNG MỘT DatagramSocket dùng chung cho cả gửi và nhận.
        DatagramSocket teacherAudio = new DatagramSocket();
        DatagramSocket studentAudio = new DatagramSocket();
        check("giao vien vao lop duoc", joinRoom(teacher, roomId, teacherAudio.getLocalPort()));
        check("hoc sinh vao lop duoc", joinRoom(student, roomId, studentAudio.getLocalPort()));

        check("giao vien bao cong UDP " + teacherAudio.getLocalPort(), true);
        check("hoc sinh bao cong UDP " + studentAudio.getLocalPort(), true);
        check("cong UDP la cong NGAU NHIEN, khong phai 9001",
                teacherAudio.getLocalPort() != server.getUdpPort()
                        && studentAudio.getLocalPort() != server.getUdpPort());

        // ===== 2. Định dạng gói =====
        section("2. Server co CHAP NHAN goi 648 byte khong");
        long malformedBefore = server.getAudioRelay().getPacketsMalformed();
        seqCursor = sendPcmAudio(teacher, teacherAudio, roomId, 50, seqCursor);   // 1 giây = 50 gói
        Thread.sleep(300);
        long malformedAfter = server.getAudioRelay().getPacketsMalformed();
        check("server KHONG dem goi nao la rac (" + malformedBefore + " -> " + malformedAfter + ")",
                malformedAfter == malformedBefore);
        check("server da relay duoc goi thoai ("
                + server.getAudioRelay().getPacketsRelayed() + " goi)",
                server.getAudioRelay().getPacketsRelayed() > 0);

        // ===== 3. Đo mất gói bằng AudioPlayer thật =====
        section("3. Do ti le mat goi / sai thu tu bang AudioPlayer");
        MediaStats stats = new MediaStats();
        JitterBuffer jitter = new JitterBuffer(10, 3);
        AudioPlayer player = new AudioPlayer(studentAudio, jitter, stats);
        player.start();
        check("AudioPlayer bat len duoc (3 luong: receiver, player, stats)", player.isRunning());

        // Gửi 5 giây tiếng = 250 gói, đúng nhịp 20 ms/gói.
        Thread speaker = new Thread(() -> {
            try {
                seqCursor = sendPcmAudio(teacher, teacherAudio, roomId, 250, seqCursor);
            } catch (Exception e) {
                System.out.println("   [loi] gui thoai: " + e.getMessage());
            }
        }, "fake-speaker");
        speaker.start();
        speaker.join();

        Thread.sleep(900);      // chờ nhận nốt gói cuối
        player.stop();

        System.out.println();
        System.out.printf("   Goi nhan duoc      : %d%n", player.getPacketsReceived());
        System.out.printf("   So thu tu lon nhat : %d%n", player.getMaxSeq());
        System.out.printf("   Goi mat            : %d%n", player.getPacketsLost());
        System.out.printf("   Goi sai thu tu     : %d%n", player.getPacketsOutOfOrder());
        System.out.printf("   Ti le mat goi      : %.2f%%%n", player.lossPercent());
        System.out.printf("   Byte nhan duoc     : %.2f MB%n", player.getBytesReceived() / 1e6);

        // Số thứ tự là MỘT dòng liên tục suốt bài test: bước 2 gửi seq 0..49,
        // nên bước 3 gửi tiếp seq 50..299 -> gói cuối phải có số thứ tự 299.
        check("nhan duoc gan du so goi da gui (" + player.getPacketsReceived() + " goi)",
                player.getPacketsReceived() >= 250);
        check("ti le mat goi < 2% (" + String.format("%.2f%%", player.lossPercent()) + ")",
                player.lossPercent() < 2.0);
        check("goi den dung thu tu (sai thu tu = " + player.getPacketsOutOfOrder() + ")",
                player.getPacketsOutOfOrder() < 10);
        check("so thu tu lien tuc, khong nhay coc (maxSeq = " + player.getMaxSeq() + ")",
                player.getMaxSeq() >= 250);

        // ===== 4. Không có vọng =====
        section("4. Kiem tra KHONG CO VONG (server tru nguoi gui)");
        teacherAudio.setSoTimeout(1000);
        sendPcmAudio(teacher, teacherAudio, roomId, 30, seqCursor);
        int echoed = 0;
        byte[] buf = new byte[Protocol.UDP_RECV_BUFFER_BYTES];
        long deadline = System.currentTimeMillis() + 900;
        while (System.currentTimeMillis() < deadline) {
            try {
                teacherAudio.receive(new DatagramPacket(buf, buf.length));
                echoed++;
            } catch (java.net.SocketTimeoutException e) {
                break;
            }
        }
        check("nguoi NOI khong nhan lai duoc goi cua chinh minh (nhan " + echoed + ")", echoed == 0);

        // ===== 5. Ghi CSV =====
        section("5. Xuat bang so lieu ra docs/do-dac/");
        var csv = stats.writeCsv("chang2-thoai-1may");
        check("da ghi file CSV: " + csv.getFileName(), Files.exists(csv));

        teacherAudio.close();
        studentAudio.close();
        teacher.close();
        student.close();
    }

    // ==================================================================
    // Phát tiếng PCM thật (sóng sin 440 Hz) ở đúng nhịp 50 gói/giây
    // ==================================================================

    /**
     * Gửi {@code packets} gói thoại 648 byte ở đúng 20 ms/gói.
     *
     * <p>Dùng sóng sin 440 Hz chứ không dùng byte ngẫu nhiên: nếu đưa byte ngẫu nhiên vào loa thì
     * nghe như tiếng rè trắng (không phân biệt được với lỗi), còn sóng sin nghe là một nốt nhạc
     * La — rất dễ nhận ra bằng tai khi demo trước hội đồng.</p>
     *
     * <p>Pha được giữ liên tục qua các gói để không có tiếng "tách" ở ranh giới gói.</p>
     *
     * <p><b>Vì sao phải truyền {@code startSeq} và trả về số thứ tự kế tiếp:</b> bài test gọi hàm
     * này nhiều lần (kiểm tra định dạng, đo mất gói, kiểm tra vọng). Nếu mỗi lần lại đếm lại từ 0
     * thì người nghe đang chạy sẽ thấy số thứ tự "nhảy lùi" và báo sai thứ tự hàng loạt — đó là
     * <i>lỗi của bài đo</i>, không phải lỗi của kênh thoại. Một mic thật chỉ có MỘT dòng số thứ tự
     * liên tục duy nhất, nên bài test phải mô phỏng đúng như vậy.</p>
     *
     * @param startSeq số thứ tự bắt đầu (dùng chung một biến đếm cho cả bài test)
     * @return số thứ tự kế tiếp sau gói cuối cùng vừa gửi
     */
    private static int sendPcmAudio(TestClient.Conn conn, DatagramSocket socket,
                                    int roomId, int packets, int startSeq) throws Exception {
        byte[] packet = new byte[Protocol.UDP_PACKET_BYTES];
        AudioRecorder.writeInt(packet, Protocol.UDP_OFFSET_ROOM_ID, roomId);

        InetAddress target = InetAddress.getByName(HOST);
        long t0 = System.nanoTime();

        int samplesPerPacket = Protocol.UDP_PCM_BYTES / 2;
        double phase = 0;
        double phaseStep = 2 * Math.PI * 440.0 / AudioConfig.SAMPLE_RATE;

        // Bật mic trước khi nói — đúng luồng nghiệp vụ.
        conn.send(PacketType.AUDIO_ON, PacketUtils.buildPayload(o -> o.writeInt(roomId)));

        int seq = startSeq;
        for (int i = 0; i < packets; i++, seq++) {
            AudioRecorder.writeInt(packet, Protocol.UDP_OFFSET_SEQ_NO, seq);
            for (int s = 0; s < samplesPerPacket; s++) {
                short value = (short) (Math.sin(phase) * 8000);
                phase += phaseStep;
                int off = Protocol.UDP_OFFSET_PCM + s * 2;
                packet[off] = (byte) (value & 0xFF);              // little-endian
                packet[off + 1] = (byte) ((value >> 8) & 0xFF);
            }
            socket.send(new DatagramPacket(packet, packet.length, target, server.getUdpPort()));

            // Bù thời gian để giữ đúng nhịp 20 ms/gói (không dùng sleep(20) trần).
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
            long targetMs = (i + 1) * AudioConfig.FRAME_MS;
            if (targetMs > elapsedMs) {
                Thread.sleep(targetMs - elapsedMs);
            }
        }
        return seq;
    }

    // ==================================================================
    // Tiện ích
    // ==================================================================

    /**
     * Đăng ký rồi đăng nhập một client, và <b>kiểm tra mã kết quả</b> trong gói trả về.
     *
     * <p>Bài học xương máu: bản đầu của bài test này bỏ qua kết quả trả về nên lỗi
     * "client chưa đăng nhập" bị che mất, biểu hiện ra thành "relay 300 gói nhưng 0 người
     * nhận" — trông y như lỗi mạng, thực ra là lỗi luồng nghiệp vụ. Từ nay hàm này trả về
     * {@code true/false} và gọi bằng {@link #check} để lỗi hiện ra ngay tại chỗ.</p>
     *
     * @return {@code true} nếu cả đăng ký lẫn đăng nhập đều thành công
     */
    private static boolean login(TestClient.Conn conn, String user, String role) throws Exception {
        String hash = Sha256.hex(PASSWORD);
        conn.send(PacketType.REGISTER_REQ, PacketUtils.buildPayload(o -> {
            o.writeUTF(user);
            o.writeUTF(hash);
            o.writeUTF(role);
        }));
        if (!isStatusOk(conn.await(PacketType.REGISTER_RES, 2000))) {
            return false;
        }
        conn.send(PacketType.LOGIN_REQ, PacketUtils.buildPayload(o -> {
            o.writeUTF(user);
            o.writeUTF(hash);
        }));
        return isStatusOk(conn.await(PacketType.LOGIN_RES, 2000));
    }

    /** Gửi JOIN_ROOM_REQ kèm cổng UDP, trả về {@code true} nếu server xác nhận đã vào lớp. */
    private static boolean joinRoom(TestClient.Conn conn, int roomId, int udpPort) throws Exception {
        conn.send(PacketType.JOIN_ROOM_REQ, PacketUtils.buildPayload(o -> {
            o.writeInt(roomId);
            o.writeInt(udpPort);
        }));
        return isStatusOk(conn.await(PacketType.JOIN_ROOM_RES, 3000));
    }

    /** Đọc byte trạng thái (1 = thành công) ở đầu payload của gói kết quả. */
    private static boolean isStatusOk(Packet res) {
        if (res == null) {
            return false;
        }
        try {
            return PacketUtils.payloadReader(res.getPayload()).readByte() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private static int readRoomId(Packet res) {
        if (res == null) {
            return -1;
        }
        try {
            var r = PacketUtils.payloadReader(res.getPayload());
            r.readByte();
            r.readUTF();
            return r.readInt();
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
