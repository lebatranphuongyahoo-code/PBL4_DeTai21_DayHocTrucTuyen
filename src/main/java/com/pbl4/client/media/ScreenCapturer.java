package com.pbl4.client.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.AWTException;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Chụp màn hình giáo viên, nén thành JPEG và đẩy từng frame cho lớp lắp ghép gửi lên server.
 * Chạy trên MỘT luồng riêng ("screen-capturer").
 *
 * <h2>Đường đi của một frame</h2>
 * <pre>
 *   Robot.createScreenCapture()   ~8,3 MB  (1920×1080×4 byte, ảnh thô trong RAM)
 *     → nén JPEG quality 0,6      ~60–90 KB  (nén khoảng 100 lần)
 *     → callback onJpegFrame()    → TV3 gói vào SCREEN_FRAME → TCP 9000
 * </pre>
 *
 * <h2>Vì sao 10 FPS mà không phải 30 FPS</h2>
 * <p>Nội dung bài giảng thay đổi <b>chậm</b> — slide, chữ, con trỏ. 10 FPS đã đủ để thao tác của
 * giáo viên trông liền mạch, mà băng thông chỉ bằng <b>một phần ba</b> so với 30 FPS. Ba lý do
 * cụ thể để không tăng lên 30 FPS:</p>
 * <ol>
 *   <li>Băng thông: 5 Mbps/học sinh → 15 Mbps/học sinh. Lớp 10 em là 150 Mbps — vượt cả cổng LAN.</li>
 *   <li>CPU máy giáo viên: mỗi frame phải sao chép 8,3 MB rồi nén. 30 FPS = 250 MB/s phải xử lý.</li>
 *   <li>Lợi ích gần như bằng không với nội dung bài giảng.</li>
 * </ol>
 *
 * <h2>Vì sao phải BÙ THỜI GIAN khi ngủ giữa các frame</h2>
 * <p>Cách làm sai (rất hay gặp): {@code capture(); encode(); sleep(100);} — vì chụp và nén tốn
 * 30–60 ms, chu kỳ thật thành 130–160 ms, tức chỉ còn <b>6–7 FPS</b> thay vì 10.</p>
 * <p>Cách đúng: tính thời điểm mục tiêu của frame kế tiếp rồi chỉ ngủ <b>phần còn lại</b>:</p>
 * <pre>
 *   long target = startNanos + (frameIndex + 1) * 100_000_000L;   // 100 ms = 10 FPS
 *   long sleepNanos = target - System.nanoTime();
 *   if (sleepNanos > 0) sleep(sleepNanos);
 * </pre>
 *
 * <h2>Tối ưu quan trọng nhất: BỎ FRAME TRÙNG</h2>
 * <p>Trong một tiết học, phần lớn thời gian màn hình <b>đứng yên</b> (giáo viên đang nói, không
 * thao tác gì). Nếu vẫn gửi frame giống hệt nhau thì băng thông bị lãng phí hoàn toàn.</p>
 *
 * <h3>Vì sao KHÔNG dùng "so hash cho bằng nhau"</h3>
 * <p>Cách làm đầu tiên của nhóm là băm thưa các pixel rồi so <b>hash có bằng nhau tuyệt đối</b>
 * hay không. Cách này <b>thất bại trên máy thật</b>: đo được 0/40 frame bị bỏ khi màn hình đứng
 * yên, tức tiết kiệm 0% — hoàn toàn vô dụng.</p>
 *
 * <p>Nguyên nhân: {@code Robot.createScreenCapture()} trên Windows đi qua bộ tổng hợp hình ảnh,
 * nên <b>hai lần chụp cùng một màn hình vẫn ra pixel khác nhau</b> — do con trỏ chuột được vẽ
 * chồng, con trỏ soạn thảo nhấp nháy, và sai số làm tròn của bộ chuyển đổi màu. Chỉ cần <b>một</b>
 * pixel trong hơn 82.000 điểm lấy mẫu lệch 1 đơn vị là hash đổi hoàn toàn.</p>
 *
 * <h3>Cách đúng: so theo NGƯỠNG chịu lỗi</h3>
 * <p>Thay vì so hash tuyệt đối, ta lấy mẫu thưa và đếm <b>tỉ lệ điểm ảnh đổi đáng kể</b>:</p>
 * <ul>
 *   <li>Một điểm coi là "đổi" khi tổng chênh lệch 3 kênh màu vượt {@link #PIXEL_DELTA}.</li>
 *   <li>Frame coi là "mới" khi tỉ lệ điểm đổi vượt {@link #CHANGE_RATIO}.</li>
 * </ul>
 * <p>Nhờ vậy một con trỏ nhấp nháy hay vài điểm nhiễu <b>không</b> làm ta gửi lại toàn bộ frame,
 * nhưng giáo viên thật sự gõ chữ hay chuyển slide thì vẫn được gửi ngay.</p>
 *
 * <h2>Vì sao phải tái sử dụng ImageWriter</h2>
 * <p>{@code ImageIO.write()} tiện nhưng <b>không cho đặt tham số nén</b>, mà nhóm cần
 * quality 0,6. Nên phải dùng {@link ImageWriter} + {@link ImageWriteParam}. Nhưng mỗi lần
 * {@code ImageIO.getImageWritersByFormatName()} lại tạo đối tượng writer mới và khởi tạo bảng
 * lượng tử hoá — tốn CPU đáng kể nếu làm 10 lần/giây. Vì vậy writer được tạo <b>một lần</b> ở
 * ngoài vòng lặp và dùng lại.</p>
 */
public class ScreenCapturer {

    /** Nhịp chụp mặc định. Xem phần "vì sao 10 FPS" ở trên. */
    public static final int DEFAULT_FPS = 10;

    /** Mức chất lượng JPEG của nhóm — chọn theo thí nghiệm 4 mức ở Chặng 4, không theo cảm tính. */
    public static final float DEFAULT_QUALITY = 0.6f;

    /** Bước lấy mẫu khi dò thay đổi — cứ 4 pixel theo mỗi chiều lấy một điểm. */
    private static final int SAMPLE_STEP = 4;

    /** Tổng chênh lệch RGB tối thiểu để một điểm ảnh coi là "đã đổi". */
    private static final int PIXEL_DELTA = 24;

    /** Tỉ lệ điểm ảnh đổi tối thiểu để coi cả frame là "nội dung mới". */
    private static final double CHANGE_RATIO = 0.01;

    private final Consumer<byte[]> onJpegFrame;
    private final Rectangle captureArea;
    private final Robot robot;

    private ImageWriter jpegWriter;
    private ImageWriteParam writeParam;

    private volatile int fps = DEFAULT_FPS;
    private volatile float quality = DEFAULT_QUALITY;
    private volatile boolean skipDuplicates = true;

    private Thread thread;
    private volatile boolean running;

    // ---------- Số liệu ----------
    private final MediaStats stats;

    /**
     * Mẫu pixel của frame trước — dùng để dò thay đổi theo ngưỡng.
     *
     * <p>{@code null} nghĩa là chưa có frame nào, frame kế tiếp chắc chắn được gửi.</p>
     */
    private int[] lastSamples;
    private volatile long framesCaptured;
    private volatile long framesSkipped;
    private volatile long framesSent;
    private volatile long lastFrameBytes;
    private volatile long lastEncodeNanos;

    /**
     * @param onJpegFrame nhận từng frame JPEG đã nén. Lớp lắp ghép (TV3) sẽ gói frame này
     *                    vào {@code SCREEN_FRAME} và gửi lên server.
     * @param stats       bộ đếm dùng chung, có thể truyền {@code null}
     * @throws AWTException khi hệ điều hành không cho phép chụp màn hình
     */
    public ScreenCapturer(Consumer<byte[]> onJpegFrame, MediaStats stats) throws AWTException {
        this.onJpegFrame = onJpegFrame;
        this.stats = stats;
        this.robot = new Robot();
        this.captureArea = defaultScreenArea();
    }

    /** Vùng chụp mặc định: toàn bộ màn hình chính. */
    private static Rectangle defaultScreenArea() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        GraphicsConfiguration config = device.getDefaultConfiguration();
        Rectangle bounds = config.getBounds();
        if (device.getFullScreenWindow() != null) {
            bounds = device.getFullScreenWindow().getBounds();
        }
        return new Rectangle(0, 0, bounds.width, bounds.height);
    }

    // ==================================================================
    // BẬT / TẮT
    // ==================================================================

    /**
     * Bắt đầu chụp.
     *
     * <p><b>Lưu ý cho lớp lắp ghép:</b> phải gửi {@code SCREEN_START} lên server <b>trước</b> khi
     * gọi hàm này — nếu không, server chưa bật cờ chia sẻ và mọi frame gửi đi sẽ bị bỏ qua
     * (xem {@code ClientHandler.handleScreenFrame}).</p>
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        initWriter();

        running = true;
        lastSamples = null;             // bắt đầu phiên mới -> frame đầu luôn được gửi

        thread = new Thread(this::loop, "screen-capturer");
        thread.setDaemon(true);
        thread.start();

        System.out.printf("[screen] Bat chia se man hinh: %dx%d | %d FPS | JPEG quality %.1f"
                        + " | bo frame trung = %s%n",
                captureArea.width, captureArea.height, fps, quality, skipDuplicates);
    }

    /** Dừng chụp. */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        // KHÔNG dispose() writer ở đây — xem giải thích ở encodeJpeg().
        // Writer được giữ lại để lần start() sau dùng tiếp, đúng tinh thần
        // "khởi tạo bộ nén một lần" của lớp này.
        System.out.printf("[screen] Da dung chia se | chup %d frame | gui %d | bo trung %d%n",
                framesCaptured, framesSent, framesSkipped);
    }

    public boolean isRunning() {
        return running;
    }

    // ==================================================================
    // VÒNG LẶP CHỤP — trái tim của lớp này
    // ==================================================================

    private void loop() {
        long period = 1_000_000_000L / fps;      // 10 FPS -> 100_000_000 ns
        long start = System.nanoTime();
        long index = 0;

        // Đệm byte dùng lại giữa các vòng lặp — tránh cấp phát mảng mới 10 lần/giây.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128 * 1024);

        while (running) {
            try {
                // ---------- 1. Chụp ----------
                BufferedImage frame = robot.createScreenCapture(captureArea);
                framesCaptured++;

                // ---------- 2. Bỏ frame trùng ----------
                // Lấy mẫu thưa rồi so với mẫu của frame trước, theo NGƯỠNG chịu lỗi.
                // Xem phần "vì sao không dùng so hash tuyệt đối" ở đầu lớp.
                if (skipDuplicates && !hasChanged(frame)) {
                    framesSkipped++;
                    if (stats != null) {
                        stats.onFrameSkipped();
                    }
                    // Vẫn phải chờ theo nhịp, nếu không vòng lặp sẽ quay cuồng ở 100% CPU.
                    index = pace(start, index, period);
                    continue;
                }
                lastSamples = sample(frame);

                // ---------- 3. Nén JPEG ----------
                long t0 = System.nanoTime();
                byte[] jpeg = encodeJpeg(frame, buffer);
                lastEncodeNanos = System.nanoTime() - t0;
                lastFrameBytes = jpeg.length;

                if (stats != null) {
                    stats.onFrameCaptured(jpeg.length);
                    stats.onEncodeTime(lastEncodeNanos);
                }

                // ---------- 4. Đẩy frame cho lớp gửi ----------
                if (running && onJpegFrame != null) {
                    onJpegFrame.accept(jpeg);
                    framesSent++;
                }

                // ---------- 5. Bù thời gian để giữ đúng nhịp FPS ----------
                index = pace(start, index, period);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                // Nén một frame lỗi KHÔNG được làm chết việc chia sẻ màn hình —
                // bỏ frame đó và thử frame sau, giống triết lý của ScreenReceiver.decode().
                System.err.println("[screen] loi nen frame, bo qua: " + e.getMessage());
            } catch (IllegalStateException e) {
                if (running) {
                    System.err.println("[screen] loi khi chup man hinh: " + e.getMessage());
                }
                return;
            } catch (RuntimeException e) {
                // Một frame lỗi không được làm chết việc chia sẻ màn hình.
                System.err.println("[screen] bo qua frame loi: " + e);
            }
        }
    }

    /**
     * Giữ đúng nhịp FPS bằng cách ngủ <b>phần thời gian còn lại</b> của chu kỳ.
     *
     * <p>Đây là chỗ sửa lỗi "sleep(100) trần" — xem phần ghi chú đầu lớp.</p>
     *
     * @return chỉ số frame kế tiếp
     */
    private long pace(long startNanos, long index, long periodNanos) throws InterruptedException {
        index++;
        long target = startNanos + index * periodNanos;
        long remaining = target - System.nanoTime();
        if (remaining > 0) {
            // Thread.sleep chỉ chính xác tới ~1 ms, nên ngủ milli rồi bù nano còn lại.
            long millis = remaining / 1_000_000;
            int nanos = (int) (remaining % 1_000_000);
            Thread.sleep(millis, nanos);
        }
        // Nếu đã trễ hơn mục tiêu (máy quá tải) thì bỏ qua, không dồn nợ —
        // dồn nợ sẽ làm các frame sau bị dồn cục.
        return index;
    }

    /**
     * Lấy mẫu thưa các pixel của một frame.
     *
     * <p>Đọc toàn bộ ảnh 1536×864 là hơn 1,3 triệu điểm ảnh — quá đắt để làm 10 lần/giây chỉ
     * nhằm phát hiện màn hình có đổi hay không. Lấy mẫu mỗi {@link #SAMPLE_STEP} pixel theo cả
     * hai chiều giảm còn khoảng 82.000 điểm, vẫn đủ nhạy với thay đổi thật.</p>
     *
     * @return mảng giá trị RGB đã lấy mẫu, theo thứ tự hàng ngang từ trên xuống
     */
    private int[] sample(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int cols = (w + SAMPLE_STEP - 1) / SAMPLE_STEP;
        int rows = (h + SAMPLE_STEP - 1) / SAMPLE_STEP;
        int[] out = new int[rows * cols];
        int i = 0;
        for (int y = 0; y < h; y += SAMPLE_STEP) {
            for (int x = 0; x < w; x += SAMPLE_STEP) {
                out[i++] = image.getRGB(x, y);
            }
        }
        return out;
    }

    /**
     * So frame vừa chụp với mẫu của frame trước để quyết định có nên gửi hay không.
     *
     * <p>Cách so là <b>đếm tỉ lệ điểm ảnh đổi đáng kể</b> thay vì so hash tuyệt đối — xem phần
     * "vì sao không dùng so hash tuyệt đối" ở đầu lớp để biết lý do.</p>
     *
     * @return {@code true} nếu nội dung coi như đã thay đổi và cần gửi frame
     */
    private boolean hasChanged(BufferedImage image) {
        if (lastSamples == null) {
            return true;                        // frame đầu tiên luôn phải gửi
        }
        int[] now = sample(image);
        if (now.length != lastSamples.length) {
            return true;                        // đổi độ phân giải -> chắc chắn phải gửi
        }

        int changed = 0;
        for (int i = 0; i < now.length; i++) {
            int a = now[i];
            int b = lastSamples[i];
            int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
            int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
            int db = Math.abs((a & 0xFF) - (b & 0xFF));
            if (dr + dg + db > PIXEL_DELTA) {
                changed++;
            }
        }
        return changed > now.length * CHANGE_RATIO;
    }

    /**
     * Cầu nối cho công cụ tự kiểm: đo tỉ lệ điểm ảnh "đổi đáng kể" giữa hai lần chụp.
     *
     * <p>Chỉ dùng cho bài đo — không dùng trong luồng chạy thật.</p>
     *
     * @return tỉ lệ điểm ảnh đổi, trong khoảng 0..1
     */
    public static double changedRatio(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        int total = 0;
        int changed = 0;
        for (int y = 0; y < h; y += SAMPLE_STEP) {
            for (int x = 0; x < w; x += SAMPLE_STEP) {
                total++;
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                int dr = Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF));
                int dg = Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF));
                int db = Math.abs((p & 0xFF) - (q & 0xFF));
                if (dr + dg + db > PIXEL_DELTA) {
                    changed++;
                }
            }
        }
        return total == 0 ? 0 : (double) changed / total;
    }

    // ==================================================================
    // NÉN JPEG
    // ==================================================================

    /** Khởi tạo writer MỘT LẦN — xem phần "vì sao tái sử dụng ImageWriter" ở đầu lớp. */
    private void initWriter() throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("Khong tim thay bo nen JPEG trong JDK");
        }
        jpegWriter = writers.next();
        writeParam = jpegWriter.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(quality);
    }

    /**
     * Nén một ảnh thành JPEG với tham số quality đã đặt.
     *
     * <h2>Vì sao có kiểm tra {@code jpegWriter == null} ở đây</h2>
     * <p>Bản đầu của lớp này gọi {@code jpegWriter.dispose()} rồi gán {@code null} trong
     * {@link #stop()}. Nhưng {@code stop()} chỉ đặt cờ {@code running = false} — nó
     * <b>không chờ</b> luồng chụp thoát ra. Nếu luồng chụp đang nằm giữa vòng lặp (đã qua
     * bước kiểm tra {@code while (running)}), nó sẽ chạy tới đúng dòng này với
     * {@code jpegWriter == null} và ném {@link NullPointerException}.</p>
     *
     * <p>Triệu chứng thực tế quan sát được trong bài tự kiểm Chặng 3:</p>
     * <pre>
     *   [screen] bo qua frame loi: java.lang.NullPointerException: Cannot invoke
     *   "javax.imageio.ImageWriter.setOutput(Object)" because "this.jpegWriter" is null
     * </pre>
     *
     * <p>Đây là <b>tranh chấp giữa hai luồng</b> (race condition) kinh điển: luồng A giải phóng
     * tài nguyên mà luồng B còn đang dùng. Cách sửa gồm hai lớp phòng thủ:</p>
     * <ol>
     *   <li>{@code stop()} <b>không</b> giải phóng writer nữa — writer được dùng lại cho lần
     *       {@code start()} sau, đúng mục đích tối ưu của lớp này.</li>
     *   <li>Hàm này vẫn tự tạo lại writer nếu thấy {@code null} — phòng trường hợp lớp lắp ghép
     *       gọi {@code dispose()} từ bên ngoài. Tạo lại chỉ tốn một lần, không ảnh hưởng nhịp
     *       10 FPS.</li>
     * </ol>
     *
     * @param image  ảnh thô từ Robot
     * @param buffer đệm dùng lại để tránh cấp phát mới mỗi frame
     */
    private byte[] encodeJpeg(BufferedImage image, ByteArrayOutputStream buffer) throws IOException {
        if (jpegWriter == null) {
            initWriter();               // tự phục hồi thay vì ném NPE
        }
        buffer.reset();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(buffer)) {
            jpegWriter.setOutput(out);
            jpegWriter.write(null, new IIOImage(image, null, null), writeParam);
        }
        return buffer.toByteArray();
    }

    /**
     * Nén thử một ảnh ở mức chất lượng bất kỳ — dùng cho thí nghiệm 1 (bảng 4 mức nén).
     *
     * @return thời gian nén tính bằng nano giây, kết quả JPEG ghi vào {@code outBytes}
     */
    public static byte[] encodeAtQuality(BufferedImage image, float q, ByteArrayOutputStream reuse)
            throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("Khong tim thay bo nen JPEG");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(q);

            ByteArrayOutputStream bos = (reuse == null) ? new ByteArrayOutputStream(128 * 1024) : reuse;
            bos.reset();
            try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bos)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    // ==================================================================
    // CẤU HÌNH & SỐ LIỆU
    // ==================================================================

    /** Đổi nhịp chụp. Phải gọi trước {@code start()}, hoặc gọi giữa chừng để đổi khi đang chạy. */
    public void setFps(int newFps) {
        if (newFps < 1 || newFps > 60) {
            throw new IllegalArgumentException("FPS phai trong [1, 60], nhan duoc: " + newFps);
        }
        this.fps = newFps;
    }

    public int getFps() {
        return fps;
    }

    /**
     * Đổi mức chất lượng nén. Nếu đang chạy thì tham số được áp dụng ngay cho các frame sau.
     * Phải gọi trước {@code start()} để có hiệu lực từ frame đầu.
     */
    public void setQuality(float newQuality) {
        if (newQuality < 0.05f || newQuality > 1.0f) {
            throw new IllegalArgumentException("Quality phai trong [0.05, 1.0]");
        }
        this.quality = newQuality;
        if (writeParam != null) {
            writeParam.setCompressionQuality(newQuality);
        }
    }

    public float getQuality() {
        return quality;
    }

    /** Bật/tắt tối ưu bỏ frame trùng. Cần tắt khi làm thí nghiệm đo băng thông tối đa. */
    public void setSkipDuplicates(boolean skip) {
        this.skipDuplicates = skip;
    }

    public boolean isSkipDuplicates() {
        return skipDuplicates;
    }

    public Rectangle getCaptureArea() {
        return captureArea;
    }

    public long getFramesCaptured() {
        return framesCaptured;
    }

    public long getFramesSkipped() {
        return framesSkipped;
    }

    public long getFramesSent() {
        return framesSent;
    }

    /** Kích thước frame gần nhất sau nén, đơn vị byte. */
    public long getLastFrameBytes() {
        return lastFrameBytes;
    }

    /** Thời gian nén frame gần nhất, đơn vị mili giây. */
    public double getLastEncodeMs() {
        return lastEncodeNanos / 1_000_000.0;
    }

    /** Kích thước màn hình đang chụp, dạng "1920x1080". */
    public String getScreenSize() {
        return captureArea.width + "x" + captureArea.height;
    }

    /** Kích thước ảnh thô một frame trong RAM, đơn vị MB. */
    public double rawFrameMb() {
        return captureArea.width * (double) captureArea.height * 4 / 1_000_000.0;
    }

    /** Chụp thử một frame và nén ở mức chất lượng bất kỳ, không cần bật luồng. Dùng cho thí nghiệm. */
    public byte[] captureOnce(float q) throws IOException {
        BufferedImage frame = robot.createScreenCapture(captureArea);
        return encodeAtQuality(frame, q, null);
    }

    /** Kích thước màn hình chính hiện tại — dùng cho báo cáo và kiểm tra môi trường. */
    public static Rectangle primaryScreenBounds() {
        return defaultScreenArea();
    }

    /** Đếm số màn hình đang kết nối. */
    public static int screenCount() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
    }

    /** Lấy độ phân giải màn hình chính theo pixel để tính băng thông. */
    public static int[] primaryResolution() {
        Rectangle r = defaultScreenArea();
        return new int[]{r.width, r.height};
    }

    @Override
    public String toString() {
        return String.format("ScreenCapturer{%s, %d FPS, quality %.1f, tho %.1f MB/frame}",
                getScreenSize(), fps, quality, rawFrameMb());
    }
}
