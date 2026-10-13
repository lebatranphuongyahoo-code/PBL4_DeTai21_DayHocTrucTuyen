package com.pbl4.server.db;

import com.pbl4.common.RoomInfo;

import java.util.List;

/**
 * Hợp đồng giữa TV1 (người gọi) và TV3 (người cài đặt) — bảng 1.5.5 của tài liệu.
 *
 * <p><b>Vì sao là interface chứ không phải class cụ thể:</b> TV1 cần biên dịch và chạy thử server
 * TRƯỚC khi TV3 xong SQLite. Với interface, TV1 dùng bản tạm {@link InMemoryDatabaseManager}
 * để chạy thử mức T0, còn TV3 viết {@code SqliteDatabaseManager} cài đặt đúng interface này.
 * Chữ ký 4 phương thức <b>không đổi</b> so với tài liệu, nên hợp đồng vẫn nguyên vẹn.</p>
 *
 * <p>⚠️ <b>Cần cả nhóm xác nhận</b> (mục 1.5.6) trước khi commit — đây là bổ sung so với bảng 1.5.5.</p>
 *
 * <p>Người viết: TV1 (interface) · Người cài đặt: TV3.</p>
 */
public interface DatabaseManager {

    /**
     * Xác thực đăng nhập.
     *
     * @param user      tên đăng nhập
     * @param sha256Hex mật khẩu ĐÃ BĂM ở client (64 ký tự hex)
     * @return {@code "TEACHER"} hoặc {@code "STUDENT"} nếu đúng, {@code null} nếu sai
     */
    String authenticate(String user, String sha256Hex);

    /**
     * Đăng ký tài khoản mới.
     *
     * @param role {@link com.pbl4.common.Protocol#ROLE_TEACHER} hoặc {@link com.pbl4.common.Protocol#ROLE_STUDENT}
     * @return {@code true} nếu tạo được, {@code false} nếu tên đã tồn tại
     */
    boolean register(String user, String sha256Hex, String role);

    /**
     * Tạo lớp học mới và lưu lại.
     *
     * @return roomId (số 6 chữ số) — <b>cơ sở dữ liệu là nơi cấp mã phòng</b>,
     *         để mã không trùng giữa các lần chạy lại server
     */
    int createRoom(String name, String teacherUser);

    /** Danh sách lớp học đã lưu. Trường {@code memberCount} do TV1 tự điền từ trạng thái RAM. */
    List<RoomInfo> listRooms();
}
