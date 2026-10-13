package com.pbl4.client.media;

import com.pbl4.common.Protocol;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Thu âm từ mic và gửi lên server qua <b>UDP</b> — chạy trên MỘT luồng riêng
 * ("audio-recorder").
 *
 * <h2>Đường đi của một mẩu tiếng</h2>
 * <pre>
 *   mic (phần cứng)
 *     → TargetDataLine  [đọc ra byte PCM thô]
 *     → 640 byte mỗi lần  (= 20 ms tiếng, xem AudioConfig)
 *     → ghép thêm 4 byte roomId + 4 byte seqNo  = 648 byte
 *     → DatagramSocket.send()  → server UDP 9001
 * </pre>
 *
 * <h2>⚠️ Luật quan trọng nhất: dùng CHUNG DatagramSocket với AudioPlayer</h2>
 * <p>Server trừ chính người gửi để tránh <b>vọng</b> (nghe thấy tiếng của mình), và nó nhận diện
 * người gửi bằng <b>cặp (địa chỉ IP, cổng UDP)</b> đã đăng ký trong {@code JOIN_ROOM_REQ}.</p>
 *
 * <p>Nếu lớp này tự mở một {@code DatagramSocket} riêng để gửi thì gói đi ra từ một cổng nguồn
 * khác cổng đã đăng ký ⇒ server không nhận ra người gửi ⇒ server gửi lại gói cho chính người nói
 * ⇒ <b>vọng</b>. Vì vậy constructor nhận socket từ bên ngoài, không tự tạo.</p>
 *
 * <h2>Vì sao dùng UDP chứ không TCP cho tiếng nói</h2>
 * <p>Mất một gói UDP chỉ mất 20 ms tiếng — người nghe thấy một tiếng "tách" rất nhẹ rồi tiếp tục.
 * Nếu dùng TCP, gói mất sẽ được truyền lại, nhưng luồng TCP chỉ giao dữ liệu <b>đúng thứ tự</b>,
 * nên mọi gói phía sau phải <b>chờ</b> gói được truyền lại. Độ trễ tích luỹ như vậy phá hỏng
 * hội thoại trực tiếp — đó là lý do VoIP luôn dùng UDP.</p>
 *
 * <h2>Vì sao đánh số seqNo</h2>
 * <p>UDP không có cơ chế nào để biết mình đã mất gói. Chỉ khi bên gửi <b>đánh số thứ tự</b> thì
 * bên nhận mới phát hiện được khoảng trống (xem {@link AudioPlayer}). Vì vậy dù đánh số tốn
 * thêm 4 byte/gói, nó là điều kiện để <b>đo được</b> tỉ lệ mất gói — số liệu chính của Chương 3.</p>
 */
public class AudioRecorder {

    /** Socket dùng chung với AudioPlayer — xem ghi chú ở đầu lớp. */
    private final DatagramSocket socket;

    /** Địa chỉ server để gửi thoại tới. */
    private final InetAddress serverAddress;

    /** Cổng UDP của server (mặc định {@link Protocol#UDP_PORT} = 9001). */
    private final int serverPort;

    /** Mã phòng hiện tại — nhét vào 4 byte đầu của mỗi gói. */
    private volatile int roomId;

    /** Bộ đếm số thứ tự gói. */
    private int seqNo;

    private TargetDataLine line;
    private Thread thread;
    private volatile boolean running;

    // ---------- Số liệu cho Chương 3 ----------
    private volatile long packetsSent;
    private volatile long bytesSent;
    private volatile long lastSendMillis;

    /**
     * @param socket        DatagramSocket <b>dùng chung</b> với AudioPlayer (đã bind cổng ngẫu nhiên)
     * @param serverAddress IP của máy chạy server
     * @param serverPort    cổng UDP của server, thường là {@link Protocol#UDP_PORT}
     */
    public AudioRecorder(DatagramSocket socket, InetAddress serverAddress, int serverPort) {
        if (socket == null) {
            throw new IllegalArgumentException("Phai truyen socket dung chung voi AudioPlayer");
        }
        this.socket = socket;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    // ==================================================================
    // BẬT / TẮT
    // ==================================================================

    /**
     * Mở mic và bắt đầu gửi.
     *
     * <p>Thứ tự bắt buộc ở phía gọi (đã chốt với TV3):</p>
     * <ol>
     *   <li>{@code audioPlayer.start()} → lấy cổng UDP đã bind</li>
     *   <li>gửi {@code AUDIO_ON} kèm cổng đó lên server ⇒ server ghi nhận địa chỉ UDP</li>
     *   <li>{@code audioRecorder.start(roomId)} ← <b>giờ mới gọi hàm này</b></li>
     * </ol>
     * <p>Nếu bật mic trước bước 2, server chưa biết cổng UDP của bạn nên mọi gói gửi đi sẽ bị bỏ.</p>
     *
     * @param roomId mã lớp hiện tại, nhét vào mỗi gói để server biết chuyển tiếp cho lớp nào
     * @throws LineUnavailableException khi mic đang bị chương trình khác chiếm, hoặc bị hệ điều
     *                                  hành chặn quyền truy cập
     */
    public synchronized void start(int roomId) throws LineUnavailableException {
        if (running) {
            return;
        }
        this.roomId = roomId;
        this.seqNo = 0;
        this.packetsSent = 0;
        this.bytesSent = 0;

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AudioConfig.FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException(
                    "Thiet bi thu am khong ho tro dinh dang "
                            + AudioConfig.SAMPLE_RATE + " Hz / " + AudioConfig.SAMPLE_SIZE_BITS + " bit");
        }

        line = (TargetDataLine) AudioSystem.getLine(info);
        // Đệm phần cứng 4 gói: đủ để không bị "khát" khi hệ điều hành bận, mà vẫn không thêm độ trễ lớn.
        line.open(AudioConfig.FORMAT, AudioConfig.CHUNK_BYTES * 4);
        line.start();

        running = true;
        thread = new Thread(this::loop, "audio-recorder");
        thread.setDaemon(true);
        thread.start();

        System.out.println("[rec] Bat mic: " + AudioConfig.SAMPLE_RATE + " Hz, goi "
                + AudioConfig.CHUNK_BYTES + " byte moi " + AudioConfig.FRAME_MS + " ms, lop " + roomId);
    }

    /** Tắt mic và đóng luồng. */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (line != null) {
            line.stop();
            line.close();       // giải phóng mic cho chương trình khác
            line = null;
        }
        System.out.println("[rec] Da tat mic | gui " + packetsSent + " goi, "
                + String.format("%.2f MB", bytesSent / 1_000_000.0));
    }

    public boolean isRunning() {
        return running;
    }

    // ==================================================================
    // VÒNG LẶP GỬI
    // ==================================================================

    private void loop() {
        byte[] chunk = new byte[AudioConfig.CHUNK_BYTES];
        byte[] packet = new byte[Protocol.UDP_PACKET_BYTES];

        // Ghi roomId vào 4 byte đầu MỘT LẦN — nó không đổi suốt phiên.
        writeInt(packet, Protocol.UDP_OFFSET_ROOM_ID, roomId);

        while (running) {
            try {
                // read() chặn cho tới khi có đủ 640 byte mới. Nhờ vậy vòng lặp tự chạy
                // đúng 50 lần/giây theo nhịp của phần cứng — KHÔNG cần Thread.sleep().
                int read = line.read(chunk, 0, chunk.length);
                if (read <= 0) {
                    continue;
                }
                if (read < chunk.length) {
                    // Gói ngắn hơn 640 byte sẽ bị server coi là rác (xem AudioRelayServer).
                    // Đệm thêm byte 0 cho đủ — byte 0 trong PCM là im lặng.
                    java.util.Arrays.fill(chunk, read, chunk.length, (byte) 0);
                }

                // Ghép 640 byte PCM vào sau 8 byte header.
                System.arraycopy(chunk, 0, packet, Protocol.UDP_OFFSET_PCM, chunk.length);
                writeInt(packet, Protocol.UDP_OFFSET_SEQ_NO, seqNo++);

                socket.send(new DatagramPacket(packet, packet.length, serverAddress, serverPort));

                packetsSent++;
                bytesSent += packet.length;
                lastSendMillis = System.currentTimeMillis();

            } catch (IOException e) {
                if (running) {
                    System.err.println("[rec] loi gui goi: " + e.getMessage());
                }
                // Một gói lỗi không được làm chết luồng — thoát vòng lặp mới là thảm hoạ.
            } catch (IllegalStateException e) {
                // line.read() ném khi line đã đóng (lúc stop()) — thoát êm.
                break;
            }
        }
    }

    // ==================================================================
    // SỐ LIỆU
    // ==================================================================

    public long getPacketsSent() {
        return packetsSent;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public int getSeqNo() {
        return seqNo;
    }

    /** Băng thông gửi thực tế tính từ lúc bắt đầu, đơn vị kbps. */
    public double currentKbps() {
        if (lastSendMillis == 0) {
            return 0;
        }
        return AudioConfig.actualKbps(Protocol.UDP_PACKET_BYTES);
    }

    public int getRoomId() {
        return roomId;
    }

    // ==================================================================
    // TIỆN ÍCH
    // ==================================================================

    /**
     * Ghi int kiểu big-endian, khớp với {@link Protocol#readIntBigEndian} ở phía server.
     *
     * <p>Để {@code public} vì công cụ kiểm thử ở gói khác cũng cần dựng gói thoại giả để test.</p>
     */
    public static void writeInt(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value >>> 24);
        buf[offset + 1] = (byte) (value >>> 16);
        buf[offset + 2] = (byte) (value >>> 8);
        buf[offset + 3] = (byte) value;
    }

    /**
     * Liệt kê các thiết bị thu âm có trên máy — bằng chứng cho bước 1.2 của Chặng 1.
     *
     * @return mảng mô tả mixer; rỗng nghĩa là máy không có mic nào khả dụng
     */
    public static String[] listInputDevices() {
        java.util.List<String> found = new java.util.ArrayList<>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AudioConfig.FORMAT);
        for (javax.sound.sampled.Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                javax.sound.sampled.Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    found.add(mixerInfo.getName() + " — " + mixerInfo.getDescription());
                }
            } catch (Exception ignored) {
                // mixer lỗi thì bỏ qua, không làm chết cả quá trình liệt kê
            }
        }
        return found.toArray(new String[0]);
    }
}
