package com.pbl4.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Length-prefix framing cho kênh TCP — HỢP ĐỒNG DÙNG CHUNG (mục 1.5.2).
 *
 * <pre>
 * +----------------+-------------+------------------------------+
 * | 4 byte độ dài  | 1 byte TYPE | N byte payload               |
 * | = 1 + N        |             |                              |
 * +----------------+-------------+------------------------------+
 * </pre>
 *
 * <p><b>Vì sao cần lớp này:</b> TCP là một luồng byte liên tục, KHÔNG giữ ranh giới thông điệp.
 * Một lần {@code write()} có thể bị cắt thành nhiều lần {@code read()}, và nhiều lần {@code write()}
 * có thể gộp thành một lần {@code read()}. Không có trường độ dài thì bên nhận không biết gói
 * kết thúc ở đâu (hiện tượng "dính gói" / "xé gói").</p>
 *
 * <p>Người viết: TV1 · Người dùng: cả 3. <b>Chữ ký hai hàm dưới đây đã chốt — đổi phải báo nhóm.</b></p>
 */
public final class PacketUtils {

    private PacketUtils() {
    }

    /** Hàm ghi payload theo từng trường, cho phép ném IOException (dùng cho {@link #buildPayload}). */
    @FunctionalInterface
    public interface PayloadWriter {
        void write(DataOutputStream out) throws IOException;
    }

    // ==================================================================
    // 1. GHI
    // ==================================================================

    /**
     * Đóng gói một gói tin thành mảng byte hoàn chỉnh {@code [4B độ dài][1B type][payload]}.
     *
     * <p>Dùng hàm này khi cần đưa gói vào hàng đợi rồi mới ghi (mô hình một luồng ghi duy nhất).
     * Nhờ nó, mỗi phần tử trong hàng đợi là <b>một gói đã đóng gói xong</b> — không thể bị trộn
     * với gói khác, vì việc ghi chỉ còn là một lời gọi {@code write(byte[])}.</p>
     */
    public static byte[] encode(byte type, byte[] payload) {
        byte[] body = (payload == null) ? new byte[0] : payload;
        int len = 1 + body.length;                 // độ dài đếm CẢ byte type
        if (len > Protocol.MAX_PAYLOAD) {
            throw new IllegalArgumentException(
                    "Payload qua lon: " + len + " byte (toi da " + Protocol.MAX_PAYLOAD + ")");
        }
        byte[] out = new byte[4 + len];
        out[0] = (byte) (len >>> 24);
        out[1] = (byte) (len >>> 16);
        out[2] = (byte) (len >>> 8);
        out[3] = (byte) len;
        out[4] = type;
        System.arraycopy(body, 0, out, 5, body.length);
        return out;
    }

    /**
     * Ghi một gói tin ra {@link DataOutputStream} và {@code flush()} ngay.
     *
     * <p><b>Cảnh báo:</b> chỉ được gọi hàm này từ MỘT luồng duy nhất cho mỗi socket.
     * Ghi là ba thao tác liên tiếp (4 byte + 1 byte + payload); hai luồng đan xen nhau
     * sẽ chèn gói của người này vào giữa gói của người kia và bên nhận đọc ra rác.</p>
     */
    public static void sendPacket(DataOutputStream out, byte type, byte[] payload) throws IOException {
        out.write(encode(type, payload));
        out.flush();
    }

    // ==================================================================
    // 2. ĐỌC
    // ==================================================================

    /**
     * Đọc đúng một gói tin từ luồng.
     *
     * <p>Ném {@link java.io.EOFException} khi client ngắt kết nối — đây là chuyện
     * <b>bình thường</b>, bên gọi phải bắt và chỉ log một dòng, không in stack trace.</p>
     */
    public static Packet readPacket(DataInputStream in) throws IOException {
        int len = in.readInt();                    // ném EOFException nếu client ngắt
        if (len < 1 || len > Protocol.MAX_PAYLOAD) {
            throw new IOException("Do dai goi khong hop le: " + len);
        }
        byte type = in.readByte();
        byte[] payload = new byte[len - 1];
        in.readFully(payload);                     // KHÔNG dùng in.read(payload)
        return new Packet(type, payload);
    }

    // ==================================================================
    // 3. TIỆN ÍCH DỰNG / ĐỌC PAYLOAD
    // ==================================================================

    /**
     * Dựng payload từ nhiều trường theo đúng thứ tự trong bảng 1.5.3.
     *
     * <pre>
     * byte[] p = PacketUtils.buildPayload(o -&gt; {
     *     o.writeByte(1);
     *     o.writeUTF("Dang nhap thanh cong");
     *     o.writeUTF("TEACHER");
     * });
     * </pre>
     */
    public static byte[] buildPayload(PayloadWriter writer) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
            DataOutputStream dos = new DataOutputStream(bos);
            writer.write(dos);
            dos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            // Không thể xảy ra với ByteArrayOutputStream, nhưng vẫn phải khai báo.
            throw new IllegalStateException("Loi khi dung payload", e);
        }
    }

    /** Mở một bộ đọc cho payload vừa nhận, đọc đúng thứ tự trường đã ghi. */
    public static DataInputStream payloadReader(byte[] payload) {
        return new DataInputStream(new ByteArrayInputStream(payload));
    }
}
