package com.pbl4.common;

/**
 * Hằng số dùng chung cho cả 3 thành viên — SINGLE SOURCE OF TRUTH.
 *
 * <p>Quy tắc: không ai được gõ số 9000 hay 9001 rải rác trong code. Mọi thứ đi qua lớp này.
 * Đổi một giá trị ở đây là đổi cho cả 3 người, nên phải báo nhóm trước (mục 1.5.6).</p>
 *
 * <p>Người viết: TV1 · Người dùng: cả 3.</p>
 */
public final class Protocol {

    private Protocol() {
        // lớp hằng số, không cho khởi tạo
    }

    // ===================== CỔNG MẠNG (bảng 1.5.1) =====================

    /** Cổng TCP: điều khiển, danh sách phòng, chat chữ, VÀ frame màn hình. */
    public static final int TCP_PORT = 9000;

    /** Cổng UDP: CHỈ dữ liệu âm thanh PCM. */
    public static final int UDP_PORT = 9001;

    /**
     * Kích thước payload tối đa cho một gói TCP (byte).
     * <p>Bắt buộc phải có: nếu bên gửi (hoặc kẻ xấu) gửi trường độ dài rác,
     * {@code new byte[len - 1]} sẽ cấp phát khổng lồ và làm server chết vì OutOfMemoryError.</p>
     */
    public static final int MAX_PAYLOAD = 5_000_000;

    // ===================== TÀI NGUYÊN SERVER =====================

    /** backlog của ServerSocket: hàng đợi các kết nối đã bắt tay TCP xong nhưng chưa accept(). */
    public static final int SOCKET_BACKLOG = 50;

    /** Số luồng cố định trong thread pool (mục 1.3). */
    public static final int THREAD_POOL_SIZE = 50;

    // ===================== GÓI UDP THOẠI (mục 1.5.4) =====================
    //   +----------------+----------------+--------------------------------+
    //   | 4 byte roomId  | 4 byte seqNo   | 640 byte PCM 16kHz/16bit/mono  | = 648 byte
    //   +----------------+----------------+--------------------------------+

    public static final int UDP_ROOM_ID_BYTES = 4;
    public static final int UDP_SEQ_NO_BYTES = 4;
    public static final int UDP_PCM_BYTES = 640;

    /** Tổng kích thước một gói thoại: 4 + 4 + 640 = 648 byte (nằm gọn trong MTU 1500). */
    public static final int UDP_PACKET_BYTES = 648;

    /** Đệm nhận của DatagramSocket — 1024 là đủ cho gói 648 byte và còn dư. */
    public static final int UDP_RECV_BUFFER_BYTES = 1024;

    /** Vị trí (offset) các trường trong gói UDP. */
    public static final int UDP_OFFSET_ROOM_ID = 0;
    public static final int UDP_OFFSET_SEQ_NO = 4;
    public static final int UDP_OFFSET_PCM = 8;

    // ===================== CHỐNG NGHẼN (bước 3.3) =====================

    /**
     * Sức chứa hàng đợi frame của mỗi client.
     * <p>Cố ý chỉ 2, KHÔNG phải 100: sức chứa 100 sẽ tích tụ frame cũ và đẩy độ trễ lên hàng giây.
     * Với video trực tiếp, frame mới quan trọng hơn frame cũ.</p>
     */
    public static final int FRAME_QUEUE_CAPACITY = 2;

    // ===================== PHÒNG HỌC =====================

    /** roomId là số 6 chữ số (bước 2.2). */
    public static final int ROOM_ID_MIN = 100_000;
    public static final int ROOM_ID_MAX = 999_999;

    // ===================== VAI TRÒ =====================

    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_STUDENT = "STUDENT";

    public static boolean isValidRole(String role) {
        return ROLE_TEACHER.equals(role) || ROLE_STUDENT.equals(role);
    }

    // ===================== TIỆN ÍCH =====================

    /** Đọc {@code int} kiểu big-endian từ mảng byte tại vị trí offset (giống DataInputStream.writeInt). */
    public static int readIntBigEndian(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
