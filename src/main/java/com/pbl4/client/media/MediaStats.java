package com.pbl4.client.media;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bộ đếm dùng chung cho cả <b>thoại</b> và <b>màn hình</b> — nguồn số liệu chính của Chương 3.
 *
 * <h2>Vì sao dùng AtomicLong mà không dùng long thường</h2>
 * <p>Các bộ đếm này bị <b>nhiều luồng cập nhật cùng lúc</b>: luồng nhận UDP tăng
 * {@code packetsReceived}, luồng phát tăng {@code silentFillCount}, luồng chụp màn hình tăng
 * {@code framesSent}. Phép {@code counter++} trên {@code long} thường <b>không nguyên tử</b> —
 * nó là ba bước (đọc giá trị → cộng 1 → ghi lại), và hai luồng đan xen nhau sẽ làm <b>mất</b>
 * một lần cộng (race condition). Hậu quả là số liệu trong báo cáo <b>sai một cách âm thầm</b> —
 * nguy hiểm hơn cả lỗi làm chương trình sập, vì không ai phát hiện ra.</p>
 *
 * <p>{@link AtomicLong} dùng phép toán nguyên tử ở tầng CPU (compare-and-swap), nên mọi lần cộng
 * đều được tính đúng mà <b>không cần khoá</b> — rẻ hơn {@code synchronized} rất nhiều.</p>
 *
 * <h2>Cách suy ra gói mất và gói sai thứ tự</h2>
 * <pre>
 *   Giữ lại số thứ tự LỚN NHẤT đã thấy (maxSeq).
 *   Mỗi gói đến có số seq:
 *     seq >  maxSeq  →  cộng (seq − maxSeq − 1) vào packetsLost   [khoảng trống]
 *     seq <= maxSeq  →  cộng 1 vào packetsOutOfOrder              [đến muộn]
 * </pre>
 *
 * <p><b>Vì sao UDP không thể đo mất gói nếu bên gửi không đánh số:</b> UDP không có trường thứ tự,
 * không có báo nhận (ACK), không có truyền lại. Một gói biến mất để lại <b>không dấu vết</b> nào
 * trong dòng dữ liệu nhận được — không ai biết là đã từng có gói đó. Chỉ khi bên gửi nhét số thứ
 * tự vào payload thì bên nhận mới thấy được "khoảng trống" và đếm ra số gói mất.</p>
 */
public class MediaStats {

    // ---------- Bộ đếm thoại ----------
    private final AtomicLong audioPacketsSent = new AtomicLong();
    private final AtomicLong audioPacketsReceived = new AtomicLong();
    private final AtomicLong audioBytesSent = new AtomicLong();
    private final AtomicLong audioBytesReceived = new AtomicLong();
    private final AtomicLong packetsLost = new AtomicLong();
    private final AtomicLong packetsOutOfOrder = new AtomicLong();
    private final AtomicLong audioSilenceFill = new AtomicLong();

    // ---------- Bộ đếm màn hình ----------
    private final AtomicLong framesCaptured = new AtomicLong();
    private final AtomicLong framesSent = new AtomicLong();
    private final AtomicLong framesSkippedDuplicate = new AtomicLong();
    private final AtomicLong framesDropped = new AtomicLong();
    private final AtomicLong framesReceived = new AtomicLong();
    private final AtomicLong framesDecodeFailed = new AtomicLong();
    private final AtomicLong screenBytesSent = new AtomicLong();
    private final AtomicLong screenBytesReceived = new AtomicLong();
    private final AtomicLong screenNanosSpent = new AtomicLong();

    /** Số thứ tự lớn nhất đã thấy — cơ sở để đếm mất gói. */
    private final AtomicLong maxSeqSeen = new AtomicLong(-1);

    private final long startMillis = System.currentTimeMillis();
    private volatile long endMillis;

    // ---------- Ghi nhận từ các luồng ----------

    /** Gọi từ luồng nhận UDP mỗi khi có một gói thoại tới. */
    public void onAudioReceived(int seq, long outOfOrderSoFar) {
        audioPacketsReceived.incrementAndGet();
        audioBytesReceived.addAndGet(com.pbl4.common.Protocol.UDP_PACKET_BYTES);
        packetsOutOfOrder.set(outOfOrderSoFar);

        // Vòng so sánh này chỉ chạy trên MỘT luồng (luồng nhận UDP) nên không tranh chấp,
        // nhưng giá trị đọc/ghi vẫn phải nguyên tử vì luồng thống kê đọc song song.
        while (true) {
            long current = maxSeqSeen.get();
            if (seq <= current) {
                break;
            }
            if (maxSeqSeen.compareAndSet(current, seq)) {
                if (current >= 0) {
                    packetsLost.addAndGet(seq - current - 1);
                }
                break;
            }
        }
    }

    public void onAudioSent(int packets, int bytes) {
        audioPacketsSent.addAndGet(packets);
        audioBytesSent.addAndGet(bytes);
    }

    /** Ghi nhận một frame đã chụp và kích thước sau nén. */
    public void onFrameCaptured(int jpegBytes) {
        framesCaptured.incrementAndGet();
        framesSent.incrementAndGet();
        screenBytesSent.addAndGet(jpegBytes);
    }

    /** Frame bị bỏ vì trùng với frame trước (màn hình đứng yên). */
    public void onFrameSkipped() {
        framesSkippedDuplicate.incrementAndGet();
    }

    /** Frame bị bỏ vì hàng đợi gửi đầy (mạng chậm hơn tốc độ chụp). */
    public void onFrameDropped() {
        framesDropped.incrementAndGet();
    }

    public void onFrameReceived(int jpegBytes) {
        framesReceived.incrementAndGet();
        screenBytesReceived.addAndGet(jpegBytes);
    }

    public void onFrameDecodeFailed() {
        framesDecodeFailed.incrementAndGet();
    }

    public void onEncodeTime(long nanos) {
        screenNanosSpent.addAndGet(nanos);
    }

    // ---------- Báo cáo ----------

    /** Thời lượng đo được, tính bằng giây. */
    public double elapsedSeconds() {
        long end = (endMillis == 0) ? System.currentTimeMillis() : endMillis;
        return (end - startMillis) / 1000.0;
    }

    public void markEnd() {
        endMillis = System.currentTimeMillis();
    }

    /** Tỉ lệ mất gói thoại, tính trên số gói kỳ vọng. */
    public double audioLossPercent() {
        long expected = maxSeqSeen.get() + 1;
        return expected <= 0 ? 0.0 : packetsLost.get() * 100.0 / expected;
    }

    /** Băng thông thoại nhận được, tính bằng kbps. */
    public double audioKbps() {
        double sec = elapsedSeconds();
        return sec <= 0 ? 0 : audioBytesReceived.get() * 8.0 / sec / 1000.0;
    }

    /** Băng thông màn hình gửi đi, tính bằng Mbps. */
    public double screenMbps() {
        double sec = elapsedSeconds();
        return sec <= 0 ? 0 : screenBytesSent.get() * 8.0 / sec / 1_000_000.0;
    }

    /** FPS thực tế nhận được. */
    public double receivedFps() {
        double sec = elapsedSeconds();
        return sec <= 0 ? 0 : framesReceived.get() / sec;
    }

    /** FPS thực tế chụp được (đã trừ frame trùng bị bỏ). */
    public double capturedFps() {
        double sec = elapsedSeconds();
        return sec <= 0 ? 0 : framesCaptured.get() / sec;
    }

    /** Kích thước trung bình một frame JPEG, đơn vị KB. */
    public double averageFrameKb() {
        long n = framesSent.get();
        return n == 0 ? 0 : screenBytesSent.get() / 1024.0 / n;
    }

    /** Thời gian nén trung bình một frame, đơn vị ms. */
    public double averageEncodeMs() {
        long n = framesCaptured.get();
        return n == 0 ? 0 : screenNanosSpent.get() / 1_000_000.0 / n;
    }

    public long getPacketsLost() {
        return packetsLost.get();
    }

    public long getPacketsOutOfOrder() {
        return packetsOutOfOrder.get();
    }

    public long getAudioPacketsReceived() {
        return audioPacketsReceived.get();
    }

    public long getFramesCaptured() {
        return framesCaptured.get();
    }

    public long getFramesSkippedDuplicate() {
        return framesSkippedDuplicate.get();
    }

    public long getFramesDropped() {
        return framesDropped.get();
    }

    public long getFramesReceived() {
        return framesReceived.get();
    }

    public long getFramesDecodeFailed() {
        return framesDecodeFailed.get();
    }

    public long getMaxSeqSeen() {
        return maxSeqSeen.get();
    }

    /** In bảng số liệu ra console — dán thẳng vào Chương 3 của báo cáo. */
    public void report() {
        markEnd();
        double sec = elapsedSeconds();
        System.out.println();
        System.out.println("================ BANG SO LIEU MediaStats ================");
        System.out.printf("Thoi luong do            : %.1f giay%n", sec);
        System.out.println("--- THOAI (UDP) ---");
        System.out.printf("  Goi gui di             : %d%n", audioPacketsSent.get());
        System.out.printf("  Goi nhan duoc          : %d%n", audioPacketsReceived.get());
        System.out.printf("  So thu tu lon nhat     : %d%n", maxSeqSeen.get());
        System.out.printf("  Goi mat                : %d (%.2f%%)%n",
                packetsLost.get(), audioLossPercent());
        System.out.printf("  Goi sai thu tu         : %d%n", packetsOutOfOrder.get());
        System.out.printf("  Bang thong nhan        : %.1f kbps (ky vong 256 kbps PCM)%n", audioKbps());
        System.out.println("--- MAN HINH (TCP) ---");
        System.out.printf("  Frame da chup          : %d%n", framesCaptured.get());
        System.out.printf("  Frame bo vi TRUNG      : %d  <-- toi uu bo frame trung%n",
                framesSkippedDuplicate.get());
        System.out.printf("  Frame bo vi hang day   : %d%n", framesDropped.get());
        System.out.printf("  Frame nhan duoc        : %d%n", framesReceived.get());
        System.out.printf("  Frame giai ma loi      : %d%n", framesDecodeFailed.get());
        System.out.printf("  Kich thuoc TB 1 frame  : %.1f KB%n", averageFrameKb());
        System.out.printf("  Thoi gian nen TB       : %.1f ms/frame%n", averageEncodeMs());
        System.out.printf("  FPS chup thuc te       : %.1f%n", capturedFps());
        System.out.printf("  FPS nhan thuc te       : %.1f%n", receivedFps());
        System.out.printf("  Bang thong man hinh    : %.2f Mbps%n", screenMbps());
        System.out.println("=========================================================");
    }

    /**
     * Ghi cùng nội dung bảng trên ra file CSV trong {@code docs/do-dac/}.
     *
     * <p>File CSV là nguồn số liệu chính thức cho Chương 3 — console có thể bị mất khi cửa sổ
     * đóng, còn file thì còn mãi. Tên file có timestamp để chạy 3 phiên đo không ghi đè nhau.</p>
     *
     * @param label tên phiên đo, ví dụ {@code "bang-thong-phien-1"}
     * @return đường dẫn file đã ghi
     */
    public Path writeCsv(String label) throws IOException {
        markEnd();
        Path dir = Paths.get("docs", "do-dac");
        Files.createDirectories(dir);
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
        Path file = dir.resolve(label + "-" + stamp + ".csv");

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("chi_so,gia_tri,don_vi");
            w.printf("thoi_luong,%.1f,giay%n", elapsedSeconds());
            w.printf("goi_thoai_gui,%d,goi%n", audioPacketsSent.get());
            w.printf("goi_thoai_nhan,%d,goi%n", audioPacketsReceived.get());
            w.printf("goi_thoai_mat,%d,goi%n", packetsLost.get());
            w.printf("ti_le_mat_goi,%.2f,%%%n", audioLossPercent());
            w.printf("goi_sai_thu_tu,%d,goi%n", packetsOutOfOrder.get());
            w.printf("bang_thong_thoai,%.1f,kbps%n", audioKbps());
            w.printf("frame_chup,%d,frame%n", framesCaptured.get());
            w.printf("frame_bo_trung,%d,frame%n", framesSkippedDuplicate.get());
            w.printf("frame_bo_hang_day,%d,frame%n", framesDropped.get());
            w.printf("frame_nhan,%d,frame%n", framesReceived.get());
            w.printf("frame_giai_ma_loi,%d,frame%n", framesDecodeFailed.get());
            w.printf("kich_thuoc_tb_frame,%.1f,KB%n", averageFrameKb());
            w.printf("thoi_gian_nen_tb,%.2f,ms%n", averageEncodeMs());
            w.printf("fps_chup,%.1f,fps%n", capturedFps());
            w.printf("fps_nhan,%.1f,fps%n", receivedFps());
            w.printf("bang_thong_man_hinh,%.2f,Mbps%n", screenMbps());
        }
        System.out.println("[stats] Da ghi so lieu ra " + file.toAbsolutePath());
        return file;
    }

    /** Xuất bảng số liệu 4 mức chất lượng nén — dùng cho thí nghiệm 1 của Chặng 4. */
    public static void writeQualityTable(Path file, String[][] rows) throws IOException {
        Files.createDirectories(file.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("quality,dung_luong_KB,thoi_gian_nen_ms,bang_thong_10fps_Mbps,doc_duoc_chu");
            for (String[] r : rows) {
                w.println(String.join(",", r));
            }
        }
    }
}
