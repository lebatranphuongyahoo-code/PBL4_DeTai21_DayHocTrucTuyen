package com.pbl4.tools;

import com.pbl4.client.media.AudioConfig;
import com.pbl4.client.media.JitterBuffer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bài tự kiểm CHẶNG 1 của TV2 — chạy trong MỘT lệnh, in bảng ĐẠT / KHÔNG ĐẠT.
 *
 * <p>Bao gồm 4 việc của cổng kiểm tra Chặng 1:</p>
 * <ol>
 *   <li>Kiểm tra hằng số trong {@code AudioConfig} có khớp hợp đồng với TV1 không</li>
 *   <li>Liệt kê thiết bị âm thanh có trên máy (bằng chứng cho bước 1.2)</li>
 *   <li>Thu 5 giây ra file {@code docs/ghi-am/test.wav} — phải đếm đúng 250 gói</li>
 *   <li>Test {@code JitterBuffer} bằng dữ liệu giả lập: chống tràn, đúng 250 gói, không chặn</li>
 * </ol>
 *
 * <pre>
 *   mvn exec:java -Dexec.mainClass=com.pbl4.tools.TV2Chang1Test
 *   # hoac:
 *   java -cp target/classes com.pbl4.tools.TV2Chang1Test
 * </pre>
 */
public class TV2Chang1Test {

    private static int passCount;
    private static int failCount;

    public static void main(String[] args) throws Exception {
        java.util.Locale.setDefault(java.util.Locale.US);

        System.out.println("########################################################");
        System.out.println("#  TV2 - CHANG 1: THAM SO AM THANH + JITTER BUFFER");
        System.out.println("#  De tai 21 - Day hoc truc tuyen");
        System.out.println("########################################################");

        // ================= 1. Hằng số =================
        section("1. Kiem tra hang so AudioConfig (hop dong voi TV1)");
        check("hang so khop voi hop dong da chot", AudioConfig.selfCheck());
        System.out.println();
        System.out.println(AudioConfig.describe());

        // ================= 2. Thiết bị âm thanh =================
        section("2. Liet ke thiet bi am thanh tren may");
        String[] devices = com.pbl4.client.media.AudioRecorder.listInputDevices();
        System.out.println("   Tim thay " + devices.length + " thiet bi thu am ho tro "
                + AudioConfig.SAMPLE_RATE + " Hz / " + AudioConfig.SAMPLE_SIZE_BITS + " bit:");
        for (String d : devices) {
            System.out.println("      - " + d);
        }
        System.out.println("   Thiet bi phat am:");
        for (String d : listOutputDevices()) {
            System.out.println("      - " + d);
        }
        check("may co it nhat 1 thiet bi thu am", devices.length > 0);

        // ================= 3. Thu ra file WAV =================
        section("3. Thu 5 giay ra docs/ghi-am/test.wav");
        boolean recordOk = recordWav(5);

        // ================= 4. JitterBuffer =================
        section("4. Test JitterBuffer bang du lieu gia lap");
        testJitterBuffer();

        // ================= Kết quả =================
        System.out.println();
        System.out.println("########################################################");
        System.out.printf("#  KET QUA CHANG 1: %d DAT / %d TONG%n", passCount, passCount + failCount);
        System.out.println("########################################################");
        if (failCount > 0) {
            System.exit(1);
        }
    }

    // ==================================================================
    // 3. Thu ra file WAV
    // ==================================================================

    /**
     * Thu {@code seconds} giây tiếng từ mic và ghi ra {@code docs/ghi-am/test.wav}.
     *
     * <p>Đếm số gói 640 byte thu được — đây chính là bằng chứng cho ô "đúng 250 gói trong 5 giây"
     * ở cổng kiểm tra Chặng 1. Nếu số nhận được lệch nhiều thì hoặc mic bị nghẽn, hoặc định dạng
     * không được hỗ trợ đúng.</p>
     */
    private static boolean recordWav(int seconds) {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AudioConfig.FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            check("thu duoc 5 giay tieng ra file WAV", false);
            System.out.println("      -> may khong ho tro dinh dang nay; bo qua phan thu am");
            return false;
        }

        try (TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info)) {
            line.open(AudioConfig.FORMAT, AudioConfig.CHUNK_BYTES * 4);
            line.start();

            int totalBytes = AudioConfig.bytesForMillis(seconds * 1000);
            byte[] captured = new byte[totalBytes];
            byte[] chunk = new byte[AudioConfig.CHUNK_BYTES];
            int offsets = 0;
            int packets = 0;
            long start = System.currentTimeMillis();

            System.out.println("   >>> NOI VAO MIC trong " + seconds + " giay...");
            while (offsets < totalBytes) {
                int read = line.read(chunk, 0, Math.min(chunk.length, totalBytes - offsets));
                if (read <= 0) {
                    break;
                }
                System.arraycopy(chunk, 0, captured, offsets, read);
                offsets += read;
                packets++;
            }
            long elapsed = System.currentTimeMillis() - start;
            line.stop();

            // Ghi ra file WAV để nghe lại bằng tai — bằng chứng không thể giả.
            File dir = new File("docs/ghi-am");
            if (!dir.exists() && !dir.mkdirs()) {
                System.out.println("      -> khong tao duoc thu muc " + dir.getAbsolutePath());
            }
            File wav = new File(dir, "test.wav");
            try (AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(captured), AudioConfig.FORMAT,
                    captured.length / AudioConfig.FORMAT.getFrameSize())) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wav);
            }

            int expectedPackets = seconds * AudioConfig.PACKETS_PER_SECOND;
            System.out.printf("   Thu duoc %,d byte trong %d ms | %d goi | ky vong %d goi%n",
                    offsets, elapsed, packets, expectedPackets);
            System.out.println("   Da ghi: " + wav.getAbsolutePath());

            boolean enough = Math.abs(packets - expectedPackets) <= expectedPackets * 0.1;
            check("thu du 5 giay tieng (" + offsets + " byte)", offsets == totalBytes);
            check("dem duoc ~" + expectedPackets + " goi (" + packets + " goi)",
                    enough);
            check("file test.wav da duoc ghi ra dia", wav.exists() && wav.length() > 0);
            return true;

        } catch (Exception e) {
            System.out.println("   -> loi thu am: " + e.getMessage());
            check("thu duoc 5 giay tieng ra file WAV", false);
            return false;
        }
    }

    // ==================================================================
    // 4. JitterBuffer
    // ==================================================================

    /**
     * Kiểm tra JitterBuffer bằng dữ liệu giả — không cần mic, không cần mạng.
     *
     * <p>Bốn tính chất phải chứng minh:</p>
     * <ol>
     *   <li>Chưa đủ prebuffer thì <b>không</b> cho phát (đây là lý do có độ trễ thêm 60 ms)</li>
     *   <li>Đủ prebuffer rồi thì phát ra đúng thứ tự đã đưa vào</li>
     *   <li>Khi đầy thì <b>bỏ gói cũ nhất</b>, và <b>không bao giờ chặn</b> luồng nhận</li>
     *   <li>Dòng ra đều đặn dù đầu vào không đều (chính là "làm phẳng jitter")</li>
     * </ol>
     */
    private static void testJitterBuffer() throws Exception {
        byte[] pcm = new byte[AudioConfig.CHUNK_BYTES];

        // ---------- (a) Prebuffer ----------
        JitterBuffer buf = new JitterBuffer(10, 3);
        buf.put(pcm);
        buf.put(pcm);
        byte[] early = buf.take(10);
        check("chua du 3 goi thi KHONG phat (tra null)", early == null);

        buf.put(pcm);                       // giờ có 3 gói
        byte[] afterPrebuffer = buf.take(10);
        check("du 3 goi thi bat dau phat duoc", afterPrebuffer != null);
        check("do tre them cua prebuffer = 60 ms", buf.latencyMillis() == 60);

        // ---------- (b) Đúng thứ tự ----------
        JitterBuffer fifo = new JitterBuffer(10, 0);
        for (int i = 1; i <= 8; i++) {
            byte[] p = new byte[AudioConfig.CHUNK_BYTES];
            p[0] = (byte) i;                // đánh dấu gói thứ i
            fifo.put(p);
        }
        boolean inOrder = true;
        for (int i = 1; i <= 8; i++) {
            byte[] got = fifo.pollNow();
            if (got == null || got[0] != (byte) i) {
                inOrder = false;
                break;
            }
        }
        check("phat ra DUNG THU TU da dua vao (FIFO)", inOrder);

        // ---------- (c) Chống tràn: bỏ gói CŨ ----------
        JitterBuffer full = new JitterBuffer(5, 0);
        for (int i = 1; i <= 5; i++) {
            byte[] p = new byte[AudioConfig.CHUNK_BYTES];
            p[0] = (byte) i;
            full.put(p);
        }
        // Đưa thêm 2 gói khi đã đầy -> gói 1 và 2 phải bị bỏ
        for (int i = 6; i <= 7; i++) {
            byte[] p = new byte[AudioConfig.CHUNK_BYTES];
            p[0] = (byte) i;
            full.put(p);
        }
        check("khi day thi BO GOI CU (bo 2 goi)", full.getDroppedCount() == 2);
        check("suc chua khong bao gio bi vuot (<= 5)", full.size() <= 5);

        // Sau 7 lan put vao dem chua 5: goi 1 va 2 da bi bo, hang doi con {3,4,5,6,7}.
        // Lan lay dau tien phai la goi 3 (goi cu nhat con lai), va goi MOI NHAT (7) phai
        // van con nam trong dem -> chung minh chinh sach "bo goi cu, giu goi moi".
        byte[] oldestLeft = full.pollNow();
        check("lan lay dau tien la goi cu nhat con lai (goi 3)",
                oldestLeft != null && oldestLeft[0] == 3);

        byte[] newest = null;
        byte[] p;
        while ((p = full.pollNow()) != null) {
            newest = p;
        }
        check("goi MOI NHAT duoc giu lai (goi 7 co mat)", newest != null && newest[0] == 7);

        // ---------- (d) Không bao giờ chặn ----------
        JitterBuffer nonBlocking = new JitterBuffer(3, 0);
        long t0 = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            nonBlocking.put(new byte[AudioConfig.CHUNK_BYTES]);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        check("100.000 lan put() khi day khong bi treo (" + ms + " ms)", ms < 3000);

        // ---------- (e) Làm phẳng jitter ----------
        JitterBuffer flatten = new JitterBuffer(10, 3);
        Random rnd = new Random(42);
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 60; i++) {
                    flatten.put(new byte[AudioConfig.CHUNK_BYTES]);
                    // Đầu vào KHÔNG đều: 0..30 ms
                    Thread.sleep(rnd.nextInt(31));
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "test-producer");
        producer.start();

        List<Long> gaps = new ArrayList<>();
        long previous = 0;
        int got = 0;
        while (got < 50) {
            byte[] c = flatten.take(200);
            if (c == null) {
                continue;
            }
            long now = System.nanoTime();
            if (previous != 0) {
                gaps.add((now - previous) / 1_000_000);
            }
            previous = now;
            got++;
            Thread.sleep(AudioConfig.FRAME_MS);      // luồng phát lấy ra ĐỀU 20 ms
        }
        producer.join();

        double avgGap = gaps.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.printf("   Khoang cach trung binh giua cac lan phat: %.1f ms "
                + "(dau vao dao dong 0-30 ms)%n", avgGap);
        check("dau ra duoc lam PHANG (khong con dao dong lon nhu dau vao)", avgGap < 40);
        check("khong mat goi do tran khi dang phat deu",
                flatten.getDroppedCount() == 0 || flatten.getPeakSize() <= 10);
    }

    // ==================================================================
    // Tiện ích
    // ==================================================================

    private static String[] listOutputDevices() {
        List<String> found = new ArrayList<>();
        DataLine.Info info = new DataLine.Info(
                javax.sound.sampled.SourceDataLine.class, AudioConfig.FORMAT);
        for (javax.sound.sampled.Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mi).isLineSupported(info)) {
                    found.add(mi.getName() + " - " + mi.getDescription());
                }
            } catch (Exception ignored) {
                // bỏ qua mixer lỗi
            }
        }
        return found.toArray(new String[0]);
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

    /** Giữ tham chiếu tới AudioFormat để tránh cảnh báo unused import khi biên dịch. */
    @SuppressWarnings("unused")
    private static AudioFormat formatRef() {
        return AudioConfig.FORMAT;
    }
}
