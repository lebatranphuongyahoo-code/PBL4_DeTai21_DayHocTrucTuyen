package com.pbl4.server;

import com.pbl4.common.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Trạm chuyển tiếp thoại — chạy trên MỘT luồng riêng, mở {@code DatagramSocket} cổng 9001.
 *
 * <h2>Nó làm gì với mỗi gói đến</h2>
 * <ol>
 *   <li>Kiểm tra độ dài gói (phải đúng {@value Protocol#UDP_PACKET_BYTES} byte).</li>
 *   <li>Đọc 4 byte đầu thành {@code roomId}.</li>
 *   <li>Tra lớp, <b>sao chép</b> danh sách thành viên ra mảng tạm rồi <b>nhả khoá</b>.</li>
 *   <li>Gửi <b>nguyên gói</b> cho từng thành viên, <b>trừ người vừa gửi</b>.</li>
 * </ol>
 *
 * <h2>Vì sao không đọc trường seqNo</h2>
 * <p>{@code seqNo} là để <b>máy nhận</b> đo tỉ lệ mất gói và số gói đến sai thứ tự. Server chỉ
 * chuyển tiếp nguyên gói nên không cần chạm vào trường đó — đọc thêm chỉ tốn CPU vô ích.</p>
 *
 * <h2>Vì sao phải trừ người gửi</h2>
 * <p>Nếu gửi lại gói cho chính người vừa nói, sau một khoảng trễ họ sẽ nghe thấy tiếng của mình —
 * hiện tượng vọng (echo). Đây là lỗi rất dễ thấy khi demo trước hội đồng.</p>
 *
 * <h2>⚠️ Yêu cầu bắt buộc với TV2</h2>
 * <p>Việc trừ người gửi so sánh theo <b>cặp (địa chỉ IP, cổng UDP)</b> đã đăng ký trong
 * {@code JOIN_ROOM_REQ}. Vì vậy client <b>phải gửi audio từ đúng cổng UDP mà nó đã đăng ký</b> —
 * nói cách khác, {@code AudioRecorder} và {@code AudioPlayer} phải dùng
 * <b>chung một DatagramSocket</b>. Nếu recorder mở socket riêng để gửi thì cổng nguồn khác cổng
 * đã đăng ký, server không nhận ra người gửi và <b>sẽ xảy ra vọng</b>.</p>
 */
public class AudioRelayServer implements Runnable {

    private final RoomManager rooms;
    private final DatagramSocket socket;
    private Thread thread;
    private volatile boolean running = true;

    // ---------- Số liệu cho Chương 3 của báo cáo ----------
    private final AtomicLong packetsReceived = new AtomicLong();
    private final AtomicLong packetsRelayed = new AtomicLong();
    private final AtomicLong bytesRelayed = new AtomicLong();
    private final AtomicLong packetsMalformed = new AtomicLong();
    private final AtomicLong packetsUnknownRoom = new AtomicLong();

    private long startMillis;
    private long lastStatMillis;
    private int sinceLastStat;
    private int lastReceiverCount;

    public AudioRelayServer(RoomManager rooms, int udpPort) throws SocketException {
        this.rooms = rooms;
        this.socket = new DatagramSocket(udpPort);
        // Đệm nhận lớn hơn một chút để không mất gói khi có nhiều phòng cùng lúc.
        this.socket.setReceiveBufferSize(1 << 20);
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public void start() {
        thread = new Thread(this, "audio-relay");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        startMillis = System.currentTimeMillis();
        lastStatMillis = startMillis;
        byte[] buffer = new byte[Protocol.UDP_RECV_BUFFER_BYTES];

        System.out.println("[audio] Relay thoai dang lang nghe tren cong UDP " + getLocalPort());

        while (running) {
            try {
                DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
                socket.receive(incoming);          // chặn cho tới khi có gói
                handle(incoming);
            } catch (SocketException e) {
                if (running) {
                    System.err.println("[audio] loi socket: " + e.getMessage());
                }
                // socket.close() từ stop() cũng ném SocketException — thoát êm.
                break;
            } catch (IOException e) {
                if (running) {
                    System.err.println("[audio] loi khi nhan goi: " + e.getMessage());
                }
            }
        }
        System.out.println("[audio] Relay thoai da dung.");
    }

    private void handle(DatagramPacket incoming) throws IOException {
        packetsReceived.incrementAndGet();

        int length = incoming.getLength();
        if (length < Protocol.UDP_PACKET_BYTES) {
            // Gói rác hoặc client gửi sai kích thước (ví dụ quên đệm 640 byte PCM).
            packetsMalformed.incrementAndGet();
            return;
        }

        int roomId = Protocol.readIntBigEndian(incoming.getData(), incoming.getOffset()
                + Protocol.UDP_OFFSET_ROOM_ID);

        Room room = rooms.get(roomId);
        if (room == null) {
            packetsUnknownRoom.incrementAndGet();
            return;
        }

        // Sao chép danh sách thành viên dưới khoá rồi nhả khoá NGAY, trước khi gửi.
        List<ClientHandler> targets = room.snapshotMembers();

        InetAddress sourceAddress = incoming.getAddress();
        int sourcePort = incoming.getPort();

        int sentTo = 0;
        for (ClientHandler member : targets) {
            if (!member.hasUdpAddress()) {
                continue;   // người này chưa báo cổng UDP
            }
            // TRỪ NGƯỜI GỬI — nếu không sẽ bị vọng.
            if (member.isSameUdpSource(sourceAddress, sourcePort)) {
                continue;
            }
            DatagramPacket outgoing = new DatagramPacket(
                    incoming.getData(), incoming.getOffset(), length,
                    member.getUdpAddress(), member.getUdpPort());
            socket.send(outgoing);
            sentTo++;
        }

        packetsRelayed.incrementAndGet();
        bytesRelayed.addAndGet(length);
        lastReceiverCount = sentTo;

        if (++sinceLastStat >= 100) {
            sinceLastStat = 0;
            printStats();
        }
    }

    /**
     * In thống kê mỗi 100 gói.
     * <p>Kỳ vọng khi giáo viên nói liên tục 30 giây: khoảng <b>1.500 gói</b> và <b>0,97 MB</b>
     * (30 s ÷ 0,02 s = 1.500 gói; 1.500 × 648 byte = 972.000 byte).</p>
     */
    private void printStats() {
        long now = System.currentTimeMillis();
        double elapsedSec = (now - startMillis) / 1000.0;
        System.out.printf("[audio] da relay %d goi | %d nguoi nhan | %.1fs | %.2f MB%n",
                packetsRelayed.get(), lastReceiverCount, elapsedSec,
                bytesRelayed.get() / 1_000_000.0);
        lastStatMillis = now;
    }

    public void stop() {
        running = false;
        socket.close();
        if (thread != null) {
            thread.interrupt();
        }
    }

    // ---------- Getter cho báo cáo ----------

    public long getPacketsReceived() {
        return packetsReceived.get();
    }

    public long getPacketsRelayed() {
        return packetsRelayed.get();
    }

    public long getBytesRelayed() {
        return bytesRelayed.get();
    }

    public long getPacketsMalformed() {
        return packetsMalformed.get();
    }

    public long getPacketsUnknownRoom() {
        return packetsUnknownRoom.get();
    }

    /** Tỉ lệ gói đã chuyển tiếp trên số gói nhận được — dùng cho bảng đo ở Chương 3. */
    public double relayRatePercent() {
        long received = packetsReceived.get();
        return received == 0 ? 0.0 : packetsRelayed.get() * 100.0 / received;
    }
}
