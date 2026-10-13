package com.pbl4.client.media;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Đệm làm phẳng dao động mạng (jitter buffer) — cầu nối giữa <b>luồng nhận</b> và <b>luồng phát</b>.
 *
 * <h2>Vấn đề nó giải quyết</h2>
 * <p>Gói UDP đến <b>không đều</b>: có gói cách nhau 18 ms, có gói cách nhau 35 ms, có gói đến
 * muộn hơn cả gói phát sau nó. Nhưng loa phải được "cho ăn" <b>đều tăm tắp</b> 20 ms một lần,
 * nếu không sẽ nghe thấy tiếng đứt quãng và rè.</p>
 *
 * <p>Jitter buffer biến dòng đến <b>không đều</b> thành dòng phát <b>đều</b>, bằng cách chấp nhận
 * chờ thêm một chút lúc bắt đầu.</p>
 *
 * <h2>Mô hình producer–consumer</h2>
 * <pre>
 *   luồng nhận UDP  --put()-->  [ ArrayBlockingQueue ]  --poll()-->  luồng phát ra loa
 *        (producer)              sức chứa 10 gói                    (consumer)
 *                                        |
 *                          chờ đủ PREBUFFER gói mới bắt đầu cho phát
 * </pre>
 *
 * <h2>Hai tham số — và đánh đổi của chúng</h2>
 * <table border="1">
 *   <tr><th>Tham số</th><th>Giá trị</th><th>Lớn hơn thì…</th><th>Nhỏ hơn thì…</th></tr>
 *   <tr><td>{@link #CAPACITY} (sức chứa)</td><td>10 gói = 200 ms</td>
 *       <td>chịu dao động tốt hơn, nhưng khi đầy thì phải bỏ gói và độ trễ tích luỹ tăng</td>
 *       <td>tràn nhanh, phải bỏ gói nhiều</td></tr>
 *   <tr><td>{@link #PREBUFFER} (chờ trước)</td><td>3 gói = 60 ms</td>
 *       <td>êm hơn nhưng độ trễ tăng</td>
 *       <td>trễ ít nhưng hay bị "khát" dữ liệu ⇒ rè</td></tr>
 * </table>
 *
 * <p><b>Vì sao chọn prebuffer 3 gói:</b> 3 × 20 ms = 60 ms độ trễ thêm — nằm trong ngưỡng
 * "tương tác tự nhiên" (dưới 150 ms thì người nói chuyện gần như không cảm nhận được), mà vẫn
 * đủ để hấp thụ dao động mạng LAN. Con số này được chứng minh bằng thí nghiệm 4 mức
 * 0/2/5/10 gói ở Chặng 4, không phải chọn theo cảm tính.</p>
 *
 * <h2>Vì sao sức chứa 10 mà không phải 1000</h2>
 * <p>Sức chứa 1000 gói = 20 giây tiếng. Khi mạng chậm, hàng đợi sẽ đầy dần và độ trễ tăng
 * vô hạn — người nghe đang nghe chuyện của nửa phút trước. Với thoại trực tiếp, <b>gói mới
 * quan trọng hơn gói cũ</b>, nên phải có trần. Trần 10 gói (200 ms) là mức chấp nhận được
 * cho một lớp học trực tuyến.</p>
 *
 * @see ArrayBlockingQueue
 */
public class JitterBuffer {

    /** Sức chứa mặc định của hàng đợi, tính bằng số gói. */
    public static final int DEFAULT_CAPACITY = 10;

    /** Số gói phải chờ trước khi bắt đầu cho phát ra loa. */
    public static final int DEFAULT_PREBUFFER = 3;

    /** Sức chứa của hàng đợi. */
    private final int capacity;

    /** Số gói chờ trước khi bắt đầu phát. */
    private final int prebuffer;

    /** Hàng đợi chặn có giới hạn — chính là "cầu nối" producer–consumer. */
    private final BlockingQueue<byte[]> queue;

    /** Số gói bị bỏ vì hàng đợi đầy (dữ liệu cho Chương 3). */
    private volatile long droppedCount;

    /** Số gói hiện có trong hàng đợi tại thời điểm đỉnh — dùng để biết đệm có bị tràn không. */
    private volatile int peakSize;

    /** Đã chờ đủ {@code prebuffer} gói chưa. */
    private volatile boolean prebuffered;

    /**
     * @param capacity  sức chứa, số gói. Phải ≥ 1.
     * @param prebuffer số gói chờ trước khi phát. Phải ≥ 0 và ≤ capacity.
     */
    public JitterBuffer(int capacity, int prebuffer) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Suc chua phai >= 1, nhan duoc: " + capacity);
        }
        if (prebuffer < 0 || prebuffer > capacity) {
            throw new IllegalArgumentException(
                    "Prebuffer phai trong [0, " + capacity + "], nhan duoc: " + prebuffer);
        }
        this.capacity = capacity;
        this.prebuffer = prebuffer;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.prebuffered = (prebuffer == 0);
    }

    public JitterBuffer() {
        this(DEFAULT_CAPACITY, DEFAULT_PREBUFFER);
    }

    // ==================================================================
    // PHÍA PRODUCER — luồng nhận UDP gọi put()
    // ==================================================================

    /**
     * Đẩy một gói PCM vào đệm. <b>Không bao giờ chặn</b> — đây là yêu cầu bắt buộc.
     *
     * <p>Vì sao không dùng {@code queue.put()} (có chặn): nếu luồng nhận bị chặn vì hàng đợi đầy
     * thì nó không đọc được gói tiếp theo từ socket nữa; đệm nhận của hệ điều hành sẽ đầy và
     * các gói sau bị hệ điều hành <b>âm thầm bỏ</b> — lúc đó mất gói nhiều hơn cả việc bỏ gói
     * có kiểm soát ở đây.</p>
     *
     * <p>Khi hàng đợi đầy, ta <b>bỏ gói CŨ NHẤT</b> (drop-oldest) chứ không bỏ gói mới: với
     * thoại trực tiếp, gói mới phản ánh hiện tại, giữ gói cũ là đang phát lại quá khứ và làm
     * độ trễ tăng thêm.</p>
     *
     * @param pcm 640 byte PCM (KHÔNG kèm roomId/seqNo — đã tách ở luồng nhận)
     */
    public void put(byte[] pcm) {
        if (pcm == null) {
            return;
        }
        if (queue.offer(pcm)) {
            trackPeak();
            return;
        }
        // Đầy: bỏ gói cũ nhất rồi đưa gói mới vào.
        if (queue.poll() != null) {
            droppedCount++;
        }
        queue.offer(pcm);
    }

    // ==================================================================
    // PHÍA CONSUMER — luồng phát gọi take()
    // ==================================================================

    /**
     * Lấy một gói ra để phát, <b>chờ tối đa {@code timeoutMs}</b>.
     *
     * <p>Trả {@code null} nghĩa là "bây giờ chưa có gì để phát". Luồng phát phải tự quyết định:
     * ghi 640 byte im lặng (silence) để giữ nhịp, hay bỏ qua nhịp này. Cách thứ nhất làm âm thanh
     * liền mạch hơn nhưng tốn CPU; nhóm chọn cách thứ nhất khi mất gói liên tiếp.</p>
     *
     * <p><b>Chưa đủ prebuffer thì luôn trả null</b> — đây là lý do phải chờ 60 ms lúc bắt đầu.
     * Không có bước này thì những gói đầu tiên chưa kịp tới đã bị phát mất ⇒ rè ngay từ giây đầu.</p>
     */
    public byte[] take(long timeoutMs) throws InterruptedException {
        if (!prebuffered) {
            if (queue.size() < prebuffer) {
                return null;              // chưa tích đủ, tiếp tục chờ
            }
            prebuffered = true;
        }
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Lấy ngay nếu có, không chờ. */
    public byte[] pollNow() {
        if (!prebuffered) {
            if (queue.size() < prebuffer) {
                return null;
            }
            prebuffered = true;
        }
        return queue.poll();
    }

    // ==================================================================
    // TRẠNG THÁI & SỐ LIỆU
    // ==================================================================

    /** Xoá sạch đệm — gọi khi bật lại mic để không phát tiếng cũ còn sót. */
    public void clear() {
        queue.clear();
        prebuffered = (prebuffer == 0);
    }

    public int size() {
        return queue.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public int getPrebuffer() {
        return prebuffer;
    }

    public boolean isPrebuffered() {
        return prebuffered;
    }

    /** Số gói đã bị bỏ do đệm đầy vì luồng phát không theo kịp. */
    public long getDroppedCount() {
        return droppedCount;
    }

    /** Số gói đông nhất từng nằm trong đệm — nếu chạm trần nghĩa là đệm đã bị tràn. */
    public int getPeakSize() {
        return peakSize;
    }

    /** Độ trễ thêm do đệm gây ra, tính bằng mili giây. */
    public int latencyMillis() {
        return prebuffer * AudioConfig.FRAME_MS;
    }

    private void trackPeak() {
        int s = queue.size();
        if (s > peakSize) {
            peakSize = s;
        }
    }

    @Override
    public String toString() {
        return String.format("JitterBuffer{chua %d/%d goi, prebuffer %d, do tre them %d ms, da bo %d}",
                queue.size(), capacity, prebuffer, latencyMillis(), droppedCount);
    }
}
