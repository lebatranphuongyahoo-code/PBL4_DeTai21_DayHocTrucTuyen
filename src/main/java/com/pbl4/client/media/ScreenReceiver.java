package com.pbl4.client.media;

import com.pbl4.common.Protocol;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Giải mã frame JPEG nhận được từ server thành {@link BufferedImage} để vẽ lên giao diện.
 *
 * <h2>Vì sao màn hình dùng TCP mà không dùng UDP</h2>
 * <p>Một frame JPEG là <b>một đơn vị nguyên vẹn</b>: mất một byte ở giữa là cả bức ảnh không giải
 * mã được, và JPEG <b>không có cơ chế phục hồi</b> phần đã mất. UDP không truyền lại gói đã mất,
 * nên dùng UDP cho ảnh nghĩa là thỉnh thoảng người học sẽ thấy màn hình vỡ hoặc trắng.</p>
 *
 * <p>Thêm một lý do kỹ thuật: frame nén của nhóm nặng 60–90 KB, <b>vượt xa MTU 1500 byte</b> của
 * Ethernet. UDP sẽ tự phân mảnh thành ~40–60 mảnh IP, và mất <b>một</b> mảnh là hỏng cả frame mà
 * không có cách nào truyền lại. TCP thì giải quyết việc này ở tầng giao vận và bảo đảm dữ liệu
 * đến đủ, đúng thứ tự.</p>
 *
 * <p>Với ảnh, độ trễ không nhạy như với tiếng: một frame đến muộn 100 ms chỉ làm hình đứng thêm
 * một chút, mắt hầu như không nhận ra. Đó là lý do chọn TCP ở đây và UDP ở kênh thoại.</p>
 *
 * <h2>Vì sao {@code decode()} trả {@code null} thay vì ném exception</h2>
 * <p>Hàm này chạy trên <b>luồng mạng</b>. Một frame hỏng (do đường truyền, do bên gửi lỗi, do
 * giải mã thất bại) là chuyện <b>bình thường</b> trong truyền thông thời gian thực — không được
 * phép làm chết luồng. Chỉ cần bỏ frame đó và chờ frame sau. Ném exception ở đây sẽ khiến luồng
 * nhận frame dừng lại, và người học mất hình vĩnh viễn dù mạng đã hồi phục.</p>
 */
public final class ScreenReceiver {

    private ScreenReceiver() {
        // lớp tiện ích — không cho khởi tạo
    }

    /** Số frame giải mã lỗi trong phiên — dùng cho bảng số liệu Chương 3. */
    private static volatile long decodeFailures;

    /**
     * Giải mã một frame JPEG.
     *
     * @param jpeg byte JPEG thô do server chuyển tiếp (payload của {@code SCREEN_FRAME})
     * @return ảnh đã giải mã, hoặc {@code null} nếu dữ liệu rỗng / hỏng / không phải JPEG
     */
    public static BufferedImage decode(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4) {
            decodeFailures++;
            return null;
        }
        // Kiểm tra magic number JPEG (FF D8) trước khi đưa vào ImageIO.
        // Rẻ hơn nhiều so với việc để ImageIO thử rồi thất bại, và tránh được
        // trường hợp dữ liệu rác làm ImageIO ném exception.
        if ((jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            decodeFailures++;
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (image == null) {
                decodeFailures++;
            }
            return image;
        } catch (IOException | RuntimeException e) {
            // Bắt cả RuntimeException: ImageIO có thể ném IllegalArgumentException với
            // dữ liệu hỏng, và nó KHÔNG được phép làm chết luồng mạng.
            decodeFailures++;
            return null;
        }
    }

    /**
     * Kiểm tra nhanh một mảng byte có phải JPEG hợp lệ và đọc được hay không.
     * Dùng trong công cụ tự kiểm của Chặng 3.
     */
    public static boolean isValidJpeg(byte[] data) {
        return decode(data) != null;
    }

    /** Số frame đã giải mã lỗi kể từ lúc chạy. */
    public static long getDecodeFailures() {
        return decodeFailures;
    }

    public static void resetCounters() {
        decodeFailures = 0;
    }

    /**
     * Kích thước payload tối đa cho một frame — chặn dữ liệu rác trước khi cấp phát mảng lớn.
     * Khớp với giới hạn của server ({@link Protocol#MAX_PAYLOAD}).
     */
    public static boolean isWithinLimit(int length) {
        return length > 0 && length <= Protocol.MAX_PAYLOAD;
    }
}
