package com.pbl4.tools;

import com.pbl4.client.media.MediaStats;
import com.pbl4.client.media.ScreenCapturer;
import com.pbl4.client.media.ScreenReceiver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bài tự kiểm CHẶNG 3 của TV2 — chụp màn hình, nén JPEG, vòng lặp 10 FPS, bỏ frame trùng.
 *
 * <p>Bốn việc phải chứng minh:</p>
 * <ol>
 *   <li>Chụp được một frame thật của màn hình và nén ra JPEG hợp lệ</li>
 *   <li>Tỉ lệ nén đạt khoảng 100 lần (8,3 MB → 60–90 KB) — con số trên slide</li>
 *   <li>Vòng lặp đạt <b>đúng 10 FPS</b> (nhờ bù thời gian), không phải 6–7 FPS</li>
 *   <li>Tối ưu bỏ frame trùng hoạt động: màn hình đứng yên thì không gửi gì</li>
 *   <li>Thí nghiệm 1: bảng 4 mức chất lượng + 4 ảnh so sánh</li>
 * </ol>
 *
 * <pre>
 *   mvn exec:java -Dexec.mainClass=com.pbl4.tools.TV2Chang3Test
 * </pre>
 */
public class TV2Chang3Test {

    private static int passCount;
    private static int failCount;

    /** Bốn mức chất lượng của thí nghiệm 1 (Chặng 4 dùng lại bảng này). */
    private static final float[] QUALITIES = {0.3f, 0.5f, 0.7f, 0.9f};

    public static void main(String[] args) throws Exception {
        java.util.Locale.setDefault(java.util.Locale.US);

        System.out.println("########################################################");
        System.out.println("#  TV2 - CHANG 3: CHUP MAN HINH + NEN JPEG + 10 FPS");
        System.out.println("########################################################");

        // ===== 0. Môi trường =====
        section("0. Moi truong hien thi");
        int[] res = ScreenCapturer.primaryResolution();
        System.out.println("   Do phan giai man hinh chinh : " + res[0] + " x " + res[1]);
        System.out.println("   So man hinh ket noi         : " + ScreenCapturer.screenCount());
        System.out.println("   Kich thuoc 1 frame THO      : "
                + String.format("%.2f MB", res[0] * (double) res[1] * 4 / 1_000_000.0));
        check("chup duoc man hinh (khong bi he dieu hanh chan)", res[0] > 0 && res[1] > 0);

        // ===== 1. Chụp 1 frame =====
        section("1. Chup 1 frame va nen JPEG quality 0,6");
        ScreenCapturer capturer = new ScreenCapturer(jpeg -> {
        }, new MediaStats());
        System.out.println("   " + capturer);

        byte[] jpeg = capturer.captureOnce(ScreenCapturer.DEFAULT_QUALITY);
        double rawMb = capturer.rawFrameMb();
        double jpegKb = jpeg.length / 1024.0;
        double ratio = (rawMb * 1_000_000.0) / jpeg.length;

        System.out.printf("   Anh tho   : %.2f MB%n", rawMb);
        System.out.printf("   Sau JPEG  : %.1f KB%n", jpegKb);
        System.out.printf("   Ti le nen : %.0f lan%n", ratio);

        check("frame JPEG co magic number FF D8 hop le",
                (jpeg[0] & 0xFF) == 0xFF && (jpeg[1] & 0xFF) == 0xD8);
        check("ti le nen dat ~100 lan (" + String.format("%.0f", ratio) + " lan)", ratio > 40);
        check("kich thuoc frame hop ly voi do phan giai man hinh",
                jpegKb > 5);
        check("ScreenReceiver giai ma lai duoc frame vua nen",
                ScreenReceiver.isValidJpeg(jpeg));

        // ===== 2. decode() trả null với ảnh hỏng =====
        section("2. ScreenReceiver.decode() voi du lieu hong (KHONG duoc nem exception)");
        check("tra null voi mang null", ScreenReceiver.decode(null) == null);
        check("tra null voi mang rong", ScreenReceiver.decode(new byte[0]) == null);
        check("tra null voi du lieu rac (khong phai JPEG)",
                ScreenReceiver.decode(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}) == null);
        check("tra null voi JPEG bi cat cut",
                ScreenReceiver.decode(java.util.Arrays.copyOf(jpeg, 100)) == null);

        // ===== 3. Vòng lặp 10 FPS =====
        section("3. Vong lap 10 FPS co BU THOI GIAN (khong dung sleep(100) tran)");
        MediaStats stats = new MediaStats();
        AtomicInteger delivered = new AtomicInteger();
        ScreenCapturer live = new ScreenCapturer(jpegFrame -> delivered.incrementAndGet(), stats);
        live.setFps(10);
        live.setQuality(0.6f);
        live.setSkipDuplicates(false);        // tắt bỏ trùng để đo đủ 10 FPS
        live.start();

        long watchStart = System.currentTimeMillis();
        Thread.sleep(5000);
        long watchMs = System.currentTimeMillis() - watchStart;
        int captured = (int) live.getFramesCaptured();
        live.stop();

        double fps = captured * 1000.0 / watchMs;
        System.out.printf("   Trong %.1f giay: chup %d frame -> %.1f FPS%n",
                watchMs / 1000.0, captured, fps);
        System.out.printf("   Kich thuoc frame cuoi: %.1f KB | thoi gian nen: %.1f ms%n",
                live.getLastFrameBytes() / 1024.0, live.getLastEncodeMs());

        check("dat ~10 FPS nho bu thoi gian (" + String.format("%.1f", fps) + " FPS)",
                fps >= 9.0 && fps <= 11.0);
        check("khong bi tut xuong 6-7 FPS (loi sleep tran)", fps > 9.0);
        check("frame den duoc lop gui (" + delivered.get() + " frame)", delivered.get() > 40);

        // ===== 4. Bỏ frame trùng =====
        section("4. Toi uu BO FRAME TRUNG (man hinh dung yen thi khong gui)");
        MediaStats dupStats = new MediaStats();
        ScreenCapturer dup = new ScreenCapturer(j -> {
        }, dupStats);
        dup.setFps(10);
        dup.setSkipDuplicates(true);

        // QUAN TRONG: chinh cua so terminal dang chay bai test cung nam trong vung chup, nen
        // khi ta vua chup vua in log thi terminal tu no doi pixel. Bo dem dung NGƯONG chiu loi
        // (xem ScreenCapturer) nen vai dong log khong lam sai ket qua — khong can chan stdout.
        dup.start();
        Thread.sleep(4000);       // trong 4 giay nay KHONG thao tac gi -> man hinh dung yen
        dup.stop();

        long capturedTotal = dup.getFramesCaptured();
        long sent = dup.getFramesSent();
        long skipped = dup.getFramesSkipped();
        System.out.printf("   Chup %d frame | gui %d | bo trung %d%n", capturedTotal, sent, skipped);
        double savedPercent = capturedTotal == 0 ? 0 : skipped * 100.0 / capturedTotal;
        System.out.printf("   Tiet kiem duoc %.1f%% luu luong khi man hinh dung yen%n", savedPercent);

        check("co bo frame trung khi man hinh dung yen (" + skipped + " frame)", skipped > 0);
        check("so frame GUI it hon so frame CHUP", sent < capturedTotal);
        // Nguong 50%: man hinh dung yen thi hau het frame phai bi bo. Tren may that con co
        // dong ho he thong / con tro nhap nhay nen khong the dat 100%; 50% la muc chac chan dat.
        check("phan tram bo trung > 50% khi khong thao tac (" + String.format("%.0f%%", savedPercent) + ")",
                savedPercent > 50);

        // ===== 5. Thí nghiệm nén 4 mức =====
        section("5. Thi nghiem 1: nen 4 muc chat luong (so lieu cho bao cao)");
        runQualityExperiment(capturer);

        // ===== 6. CSV =====
        section("6. Xuat bang so lieu");
        var csv = stats.writeCsv("chang3-man-hinh");
        check("da ghi CSV so lieu man hinh: " + csv.getFileName(), Files.exists(csv));

        System.out.println();
        System.out.println("########################################################");
        System.out.printf("#  KET QUA CHANG 3: %d DAT / %d TONG%n", passCount, passCount + failCount);
        System.out.println("########################################################");
        if (failCount > 0) {
            System.exit(1);
        }
    }

    // ==================================================================
    // Thí nghiệm 1 — nén 4 mức chất lượng
    // ==================================================================

    /**
     * Chụp <b>cùng một màn hình</b> ở 4 mức chất lượng, đo dung lượng và thời gian nén,
     * rồi lưu 4 ảnh lại để đưa vào báo cáo dưới dạng hình so sánh.
     *
     * <p>Đây là hình rất được điểm vì nó chứng minh nhóm chọn quality 0,6 <b>có căn cứ</b>,
     * không phải theo cảm tính.</p>
     */
    private static void runQualityExperiment(ScreenCapturer capturer) throws Exception {
        // Chụp MỘT lần duy nhất, rồi nén lại ở các mức khác nhau — đây là điều kiện để so sánh
        // công bằng: cùng nội dung ảnh thì mới so sánh được dung lượng.
        BufferedImage frame = new java.awt.Robot()
                .createScreenCapture(ScreenCapturer.primaryScreenBounds());

        Path dir = Paths.get("docs", "do-dac", "anh-nen");
        Files.createDirectories(dir);

        String[][] table = new String[QUALITIES.length][];
        ByteArrayOutputStream reuse = new ByteArrayOutputStream(256 * 1024);

        System.out.printf("   %-8s %-15s %-18s %-20s%n",
                "Quality", "Dung luong", "Thoi gian nen", "Bang thong 10 FPS");
        System.out.println("   " + "-".repeat(64));

        for (int i = 0; i < QUALITIES.length; i++) {
            float q = QUALITIES[i];

            long t0 = System.nanoTime();
            byte[] out = ScreenCapturer.encodeAtQuality(frame, q, reuse);
            long encodeMs = (System.nanoTime() - t0) / 1_000_000;

            double kb = out.length / 1024.0;
            double mbps = out.length * 8.0 * 10 / 1_000_000.0;      // 10 FPS

            // Lưu ảnh để đưa vào báo cáo (Hình 3.x — ảnh cùng nội dung ở 4 mức nén).
            File outFile = dir.resolve("quality-" + String.format("%.1f", q).replace('.', '_') + ".jpg")
                    .toFile();
            ImageIO.write(ImageIO.read(new java.io.ByteArrayInputStream(out)), "jpg", outFile);

            System.out.printf("   %-8s %-15s %-18s %-20s%n",
                    String.format("%.1f", q),
                    String.format("%.1f KB", kb),
                    String.format("%d ms", encodeMs),
                    String.format("%.2f Mbps", mbps));

            table[i] = new String[]{
                    String.format("%.1f", q),
                    String.format("%.1f", kb),
                    String.valueOf(encodeMs),
                    String.format("%.2f", mbps),
                    (q == 0.6f) ? "muc chon cua nhom" : "can do bang mat"
            };
        }

        System.out.println("   -> 4 anh so sanh da luu vao: " + dir.toAbsolutePath());
        System.out.println("   -> Mo 4 anh canh nhau va tra loi: muc nao la thap nhat ma CHU VAN DOC RO?");

        Path csv = Paths.get("docs", "do-dac", "thi-nghiem-nen-4-muc.csv");
        MediaStats.writeQualityTable(csv, table);

        check("luu duoc 4 anh so sanh 4 muc nen", countJpg(dir) == 4);
        check("xuat bang so lieu 4 muc nen ra CSV", Files.exists(csv));
        check("muc 0,3 nho hon muc 0,9 (nen manh hon thi nho hon)",
                Double.parseDouble(table[0][1]) < Double.parseDouble(table[3][1]));
        check("ti le nen giua muc 0,3 va 0,9 hop ly (>1.5 lan)",
                Double.parseDouble(table[3][1]) / Double.parseDouble(table[0][1]) > 1.5);
    }

    private static long countJpg(Path dir) throws Exception {
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".jpg")).count();
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
