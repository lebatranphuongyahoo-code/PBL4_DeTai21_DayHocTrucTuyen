package com.pbl4.client.media;

import javax.sound.sampled.AudioFormat;

/**
 * HỢP ĐỒNG THAM SỐ ÂM THANH CỦA CẢ NHÓM — mọi lớp trong gói {@code client.media}
 * đều đọc hằng số từ đây, không ai được gõ số rời rạc trong code.
 *
 * <h2>Bảng thiết lập (mục 1.5 của cẩm nang TV2)</h2>
 * <table border="1">
 *   <tr><th>Tham số</th><th>Giá trị</th><th>Vì sao chọn</th></tr>
 *   <tr><td>Tần số lấy mẫu</td><td>16 000 Hz</td>
 *       <td>Nyquist: biểu diễn được tới 8 kHz, dư cho dải giọng nói 300–3 400 Hz,
 *           mà vẫn tiết kiệm hơn 44 100 Hz của CD</td></tr>
 *   <tr><td>Độ sâu bit</td><td>16 bit</td>
 *       <td>Dải động ~96 dB; 8 bit chỉ được ~48 dB nên nghe rõ tiếng "rè" nền</td></tr>
 *   <tr><td>Số kênh</td><td>1 (mono)</td>
 *       <td>Giọng nói chỉ có một nguồn; stereo sẽ gấp đôi băng thông vô ích</td></tr>
 *   <tr><td>Độ dài một gói</td><td>20 ms</td>
 *       <td>Chuẩn cân bằng của VoIP (xem {@link #CHUNK_BYTES})</td></tr>
 * </table>
 *
 * <h2>Phép tính 640 byte — câu hỏi bảo vệ đầu tiên</h2>
 * <pre>
 *   16 000 mẫu/giây × 2 byte/mẫu × 0,020 giây = 640 byte
 *
 *   Vì sao 20 ms mà không phải 40 ms?
 *     - Gói 40 ms  ⇒ mỗi gói trễ thêm 20 ms, và mất một gói là mất 40 ms tiếng (nghe hụt hẳn).
 *     - Gói 10 ms  ⇒ gấp đôi số gói/giây ⇒ gấp đôi tỉ lệ header (8 byte header / 648 byte gói),
 *                    tăng tải CPU và số lần gọi hệ thống.
 *     - 20 ms là điểm cân bằng đã thành chuẩn của VoIP, và 50 gói/giây là con số đẹp để đo.
 * </pre>
 *
 * <h2>Vì sao dùng PCM thô mà không nén Opus</h2>
 * <p>Đề tài tập trung vào <b>tầng giao vận</b> và cơ chế đóng gói, nên luồng dữ liệu phải
 * minh bạch và dễ đo. Cái giá phải trả là 256 kbps thay vì ~24 kbps nếu dùng Opus — đây là
 * hạn chế thật của nhóm và được ghi ở phần "Hướng phát triển" của báo cáo.</p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/sound/sampled/AudioFormat.html">AudioFormat (JDK 21)</a>
 */
public final class AudioConfig {

    private AudioConfig() {
        // lớp hằng số — không cho khởi tạo
    }

    // ==================================================================
    // 1. THAM SỐ LẤY MẪU
    // ==================================================================

    /** Tần số lấy mẫu 16 000 Hz. */
    public static final float SAMPLE_RATE = 16_000f;

    /** Số bit cho mỗi mẫu. */
    public static final int SAMPLE_SIZE_BITS = 16;

    /** Số kênh — 1 = mono. */
    public static final int CHANNELS = 1;

    /** Có ký hay không. PCM 16 bit có dấu (kiểu {@code short} của Java). */
    public static final boolean SIGNED = true;

    /** Thứ tự byte. PCM thô không có header nên thứ tự này chỉ có ý nghĩa khi ghi ra file WAV. */
    public static final boolean BIG_ENDIAN = false;

    // ==================================================================
    // 2. THAM SỐ ĐÓNG GÓI
    // ==================================================================

    /** Độ dài một gói tính bằng mili giây. */
    public static final int FRAME_MS = 20;

    /**
     * Số byte PCM trong một gói: 16 000 × 2 × 0,02 = <b>640 byte</b>.
     * <p>Giá trị này PHẢI khớp {@code Protocol.UDP_PCM_BYTES} của TV1 — lệch một byte là
     * server coi gói là rác và bỏ ngay (xem {@code AudioRelayServer.handle()}).</p>
     */
    public static final int CHUNK_BYTES = (int) (SAMPLE_RATE * (SAMPLE_SIZE_BITS / 8) * FRAME_MS / 1000);

    /** Số mẫu PCM trong một gói: 640 / 2 = 320 mẫu. */
    public static final int SAMPLES_PER_CHUNK = CHUNK_BYTES / (SAMPLE_SIZE_BITS / 8);

    /** Số gói gửi đi mỗi giây: 1000 / 20 = <b>50 gói/giây</b>. */
    public static final int PACKETS_PER_SECOND = 1000 / FRAME_MS;

    /**
     * Băng thông thoại một chiều, tính bằng bit/giây.
     * <p>16 000 × 16 = 256 000 bit/s = <b>256 kbps</b> — chưa tính header UDP/IP.</p>
     */
    public static final int BITRATE_BPS = (int) SAMPLE_RATE * SAMPLE_SIZE_BITS * CHANNELS;

    // ==================================================================
    // 3. ĐỊNH DẠNG CHO javax.sound.sampled
    // ==================================================================

    /**
     * Định dạng âm thanh dùng cho cả {@code TargetDataLine} (thu) và {@code SourceDataLine} (phát).
     *
     * <p>Dùng chung <b>một</b> đối tượng cho cả hai chiều là điều kiện để dữ liệu thu được
     * phát lại đúng tốc độ: nếu bên thu 44 100 Hz mà bên phát 16 000 Hz thì tiếng nghe như
     * tua băng nhanh (chipmunk).</p>
     */
    public static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            (SAMPLE_SIZE_BITS / 8) * CHANNELS,   // frameSize = 2 byte/mẫu × 1 kênh = 2
            SAMPLE_RATE,                          // frameRate = 16 000 frame/giây
            BIG_ENDIAN);

    // ==================================================================
    // 4. TIỆN ÍCH
    // ==================================================================

    /**
     * Số byte PCM cần thiết cho {@code millis} mili giây — dùng để tính kích thước file WAV
     * hoặc dung lượng đệm.
     */
    public static int bytesForMillis(int millis) {
        return (int) (SAMPLE_RATE * (SAMPLE_SIZE_BITS / 8) * CHANNELS * millis / 1000);
    }

    /**
     * Băng thông của thoại <b>kể cả</b> header gói tự thêm, tính bằng kbps.
     *
     * @param packetBytes tổng kích thước gói UDP thực tế (648 byte theo hợp đồng của nhóm)
     */
    public static double actualKbps(int packetBytes) {
        return packetBytes * 8.0 * PACKETS_PER_SECOND / 1000.0;
    }

    /** In ra bảng thiết lập — dùng cho công cụ tự kiểm và cho bằng chứng trong báo cáo. */
    public static String describe() {
        return String.format(
                "AudioConfig: %.0f Hz | %d bit | %d kenh | goi %d ms%n"
                        + "  -> CHUNK_BYTES     = %d byte (%d mau)%n"
                        + "  -> PACKETS_PER_S   = %d goi/giay%n"
                        + "  -> bang thong PCM  = %d kbps%n"
                        + "  -> goi UDP 648 B   = %.1f kbps (ke ca header tu them)",
                SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, FRAME_MS,
                CHUNK_BYTES, SAMPLES_PER_CHUNK,
                PACKETS_PER_SECOND,
                BITRATE_BPS / 1000,
                actualKbps(648));
    }

    /**
     * Tự kiểm tra tính đúng đắn của các hằng số — gọi từ công cụ test.
     *
     * @return {@code true} nếu mọi hằng số khớp với hợp đồng đã chốt với TV1
     */
    public static boolean selfCheck() {
        boolean ok = true;
        ok &= expect("CHUNK_BYTES = 640", CHUNK_BYTES == 640);
        ok &= expect("SAMPLES_PER_CHUNK = 320", SAMPLES_PER_CHUNK == 320);
        ok &= expect("PACKETS_PER_SECOND = 50", PACKETS_PER_SECOND == 50);
        ok &= expect("BITRATE = 256 kbps", BITRATE_BPS == 256_000);
        ok &= expect("bang thong 648 B = 259.2 kbps",
                Math.abs(actualKbps(648) - 259.2) < 0.05);
        ok &= expect("FORMAT.frameSize = 2", FORMAT.getFrameSize() == 2);
        ok &= expect("byte cho 1000 ms = 32000", bytesForMillis(1000) == 32_000);
        return ok;
    }

    private static boolean expect(String label, boolean condition) {
        System.out.println("   " + (condition ? "[DAT]  " : "[KHONG]") + " " + label);
        return condition;
    }
}
