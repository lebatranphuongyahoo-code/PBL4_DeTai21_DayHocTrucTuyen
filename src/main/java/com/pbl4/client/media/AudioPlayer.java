package com.pbl4.client.media;

import com.pbl4.common.Protocol;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

/**
 * Nhận thoại từ server qua UDP và phát ra loa — chạy trên <b>BA luồng</b>.
 *
 * <h2>Vì sao phải ba luồng — đây là phần "Hệ điều hành" của TV2</h2>
 * <pre>
 *   luồng 1 "audio-receiver"  : socket.receive() → tách header → jitterBuffer.put()   [producer]
 *   luồng 2 "audio-player"    : jitterBuffer.take() → line.write() ra loa             [consumer]
 *   luồng 3 "audio-stats"     : mỗi giây in số liệu (gói nhận, mất, sai thứ tự, độ trễ)
 * </pre>
 *
 * <p><b>Vì sao không gộp nhận và phát vào một luồng:</b> {@code socket.receive()} <b>chặn</b> cho
 * tới khi có gói tới, còn {@code line.write()} cũng <b>chặn</b> cho tới khi đệm phần cứng tiêu
 * thụ hết dữ liệu. Hai thao tác chặn độc lập nhau, ở hai tốc độ khác nhau. Nếu đặt chung một
 * luồng thì lúc phát (chặn ~20 ms) luồng đó không đọc socket được ⇒ gói UDP ứ lại trong đệm
 * của hệ điều hành ⇒ tràn đệm ⇒ hệ điều hành <b>âm thầm bỏ gói</b>. Đây chính là lỗi "nghe rè
 * mà không hiểu vì sao" thường gặp nhất khi viết ứng dụng thoại.</p>
 *
 * <p>Jitter buffer là <b>cầu nối producer–consumer</b> giữa hai luồng đó — mô hình chuẩn của
 * môn Hệ điều hành (xem {@link JitterBuffer}).</p>
 *
 * <h2>Vì sao KHÔNG tự mở DatagramSocket</h2>
 * <p>Socket được truyền từ ngoài vào vì {@link AudioRecorder} phải gửi từ <b>cùng cổng nguồn</b>
 * đã đăng ký với server. Xem ghi chú đầu lớp {@code AudioRecorder}.</p>
 *
 * @see JitterBuffer
 */
public class AudioPlayer {

    /** Cổng UDP dùng chung với AudioRecorder, đã bind cổng ngẫu nhiên. */
    private final DatagramSocket socket;

    /** Đệm chống giật — cầu nối giữa luồng nhận và luồng phát. */
    private final JitterBuffer jitterBuffer;

    /** Bộ đếm dùng chung để đẩy số liệu lên bảng đo (có thể truyền null). */
    private final MediaStats stats;

    private SourceDataLine line;

    private Thread receiveThread;
    private Thread playThread;
    private Thread statsThread;

    private volatile boolean running;

    // ---------- Số liệu đo mất gói & sai thứ tự ----------
    private int lastSeq = -1;
    private int maxSeq = -1;
    private volatile long packetsReceived;
    private volatile long packetsLost;
    private volatile long packetsOutOfOrder;
    private volatile long packetsDroppedByBuffer;
    private volatile long bytesReceived;
    private volatile long silentFillCount;

    public AudioPlayer(DatagramSocket socket, JitterBuffer jitterBuffer, MediaStats stats) {
        this.socket = socket;
        this.jitterBuffer = jitterBuffer;
        this.stats = stats;
    }

    // ==================================================================
    // BẬT / TẮT
    // ==================================================================

    /**
     * Mở loa (nhưng KHÔNG mở socket — socket đã được bind sẵn) và khởi động ba luồng.
     *
     * <p>Hàm này <b>không</b> trả về cổng UDP; cổng đó nằm ở {@code socket.getLocalPort()} và do
     * lớp lắp ghép (TV3) lấy ra để gửi lên server kèm gói {@code AUDIO_ON}.</p>
     *
     * @throws LineUnavailableException khi loa bị chiếm hoặc không hỗ trợ định dạng 16 kHz
     */
    public synchronized void start() throws LineUnavailableException {
        if (running) {
            return;
        }

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, AudioConfig.FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException(
                    "Thiet bi phat am khong ho tro dinh dang "
                            + AudioConfig.SAMPLE_RATE + " Hz / " + AudioConfig.SAMPLE_SIZE_BITS + " bit");
        }

        line = (SourceDataLine) AudioSystem.getLine(info);
        // Đệm phát 8 gói = 160 ms. Phải nhỏ hơn hoặc bằng đệm jitter, nếu không thì
        // "đệm cộng dồn" làm độ trễ thật lớn hơn nhiều so với tính toán trên giấy.
        line.open(AudioConfig.FORMAT, AudioConfig.CHUNK_BYTES * 8);
        line.start();

        running = true;

        receiveThread = new Thread(this::receiveLoop, "audio-receiver");
        receiveThread.setDaemon(true);
        receiveThread.start();

        playThread = new Thread(this::playLoop, "audio-player");
        playThread.setDaemon(true);
        playThread.start();

        statsThread = new Thread(this::statsLoop, "audio-stats");
        statsThread.setDaemon(true);
        statsThread.start();

        System.out.println("[play] Bat loa, dang nghe tren cong UDP " + socket.getLocalPort()
                + " | jitter buffer " + jitterBuffer);
    }

    /** Dừng phát, đóng loa và dừng cả ba luồng. */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        for (Thread t : new Thread[]{receiveThread, playThread, statsThread}) {
            if (t != null) {
                t.interrupt();
            }
        }
        receiveThread = playThread = statsThread = null;

        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
        jitterBuffer.clear();
        printSummary();
    }

    public boolean isRunning() {
        return running;
    }

    // ==================================================================
    // LUỒNG 1 — NHẬN UDP (producer)
    // ==================================================================

    private void receiveLoop() {
        byte[] buffer = new byte[Protocol.UDP_RECV_BUFFER_BYTES];
        byte[] pcm = new byte[Protocol.UDP_PCM_BYTES];

        try {
            // Timeout để vòng lặp còn kiểm tra cờ running — nếu không, receive() chặn vô hạn
            // và luồng không bao giờ thoát khi gọi stop().
            socket.setSoTimeout(200);
        } catch (IOException e) {
            System.err.println("[play] khong dat duoc timeout: " + e.getMessage());
            return;
        }

        while (running) {
            DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(incoming);
            } catch (SocketTimeoutException e) {
                continue;                       // bình thường: chưa có gói nào tới
            } catch (IOException e) {
                if (running) {
                    System.err.println("[play] loi nhan goi: " + e.getMessage());
                }
                break;                          // socket đã đóng
            }

            if (incoming.getLength() < Protocol.UDP_PACKET_BYTES) {
                continue;                       // gói rác / sai kích thước
            }

            // Đọc seqNo từ 4 byte ở offset 4 — đây là cơ sở để ĐO mất gói.
            int seq = Protocol.readIntBigEndian(buffer, Protocol.UDP_OFFSET_SEQ_NO);
            onPacketReceived(seq);

            // Tách 640 byte PCM ra khỏi header 8 byte trước khi đưa vào đệm.
            System.arraycopy(buffer, Protocol.UDP_OFFSET_PCM, pcm, 0, Protocol.UDP_PCM_BYTES);
            jitterBuffer.put(pcm);
        }
    }

    /**
     * Cập nhật bộ đếm mất gói và sai thứ tự dựa vào {@code seqNo}.
     *
     * <p><b>Vì sao suy ra được từ seqNo:</b> UDP không báo cho biết gói nào bị mất. Nhưng vì bên
     * gửi đánh số tăng dần, một khoảng trống trong dãy số nghĩa là đúng chừng đó gói đã mất.
     * Gói có số <b>nhỏ hơn</b> số lớn nhất đã thấy nghĩa là gói đến <b>sai thứ tự</b> (đến muộn),
     * tức là đã bị mạng đảo lộn — dấu hiệu jitter nặng.</p>
     */
    private void onPacketReceived(int seq) {
        packetsReceived++;
        bytesReceived += Protocol.UDP_PACKET_BYTES;

        if (seq > maxSeq) {
            if (maxSeq >= 0) {
                packetsLost += seq - maxSeq - 1;   // khoảng trống = số gói mất
            }
            maxSeq = seq;
        } else {
            packetsOutOfOrder++;                  // đến muộn hoặc trùng
        }
        lastSeq = seq;

        if (stats != null) {
            stats.onAudioReceived(seq, packetsOutOfOrder);
        }
    }

    // ==================================================================
    // LUỒNG 2 — PHÁT RA LOA (consumer)
    // ==================================================================

    private void playLoop() {
        while (running) {
            try {
                byte[] chunk = jitterBuffer.take(100);
                if (chunk == null || chunk.length == 0) {
                    // Đệm đang "khát" — chưa có gì để phát.
                    //
                    // Nhóm chọn ghi 640 byte IM LẶNG để giữ NHỊP của loa. Vì sao: nếu bỏ hẳn
                    // nhịp này thì loa không được cấp dữ liệu, và khi gói sau tới thì âm thanh
                    // bị "dồn cục" — nghe như tiếng nấc. Ghi im lặng tốn CPU không đáng kể
                    // (640 byte/20 ms = 32 KB/s) mà giữ được dòng âm thanh liền mạch.
                    if (jitterBuffer.size() == 0 && packetsReceived > 0) {
                        line.write(new byte[AudioConfig.CHUNK_BYTES], 0, AudioConfig.CHUNK_BYTES);
                        silentFillCount++;
                    }
                    continue;
                }
                line.write(chunk, 0, Math.min(chunk.length, AudioConfig.CHUNK_BYTES));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IllegalStateException e) {
                return;         // line đã đóng
            }
        }
    }

    // ==================================================================
    // LUỒNG 3 — THỐNG KÊ
    // ==================================================================

    private void statsLoop() {
        long lastReceived = 0;
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) {
                return;
            }
            long now = packetsReceived;
            long delta = now - lastReceived;
            lastReceived = now;

            System.out.printf("[play] %d goi/s | nhan %d | mat %d (%.2f%%) | sai thu tu %d"
                            + " | hang doi %d/%d goi | im lang %d | do tre them %d ms%n",
                    delta, now, packetsLost, lossPercent(), packetsOutOfOrder,
                    jitterBuffer.size(), jitterBuffer.getCapacity(),
                    silentFillCount, jitterBuffer.latencyMillis());
        }
    }

    private void printSummary() {
        System.out.printf("[play] TONG KET: nhan %d goi (%.2f MB) | mat %d (%.2f%%)"
                        + " | sai thu tu %d | bo do tran hang doi %d | im lang %d goi%n",
                packetsReceived, bytesReceived / 1_000_000.0,
                packetsLost, lossPercent(), packetsOutOfOrder,
                jitterBuffer.getDroppedCount(), silentFillCount);
    }

    // ==================================================================
    // SỐ LIỆU
    // ==================================================================

    public long getPacketsReceived() {
        return packetsReceived;
    }

    public long getPacketsLost() {
        return packetsLost;
    }

    public long getPacketsOutOfOrder() {
        return packetsOutOfOrder;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public long getSilentFillCount() {
        return silentFillCount;
    }

    /** Tỉ lệ mất gói tính trên số gói KỲ VỌNG (số thứ tự lớn nhất + 1). */
    public double lossPercent() {
        long expected = maxSeq + 1;
        return expected <= 0 ? 0.0 : packetsLost * 100.0 / expected;
    }

    public int getMaxSeq() {
        return maxSeq;
    }

    public JitterBuffer getJitterBuffer() {
        return jitterBuffer;
    }
}
