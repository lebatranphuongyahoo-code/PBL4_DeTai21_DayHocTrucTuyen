package com.pbl4.server.db;

import com.pbl4.common.RoomInfo;

import java.util.List;
import java.util.Optional;

/**
 * Hợp đồng lưu trữ tài khoản và lớp học (bảng 1.5.5). Server chỉ biết interface này,
 * nên đổi nơi lưu (SQLite, RAM, MySQL…) không phải sửa tầng server.
 *
 * <p>Mọi phương thức phải an toàn khi nhiều luồng của thread pool gọi cùng lúc.</p>
 */
public interface DatabaseManager extends AutoCloseable {

    /** @return {@code false} chỉ khi tên đăng nhập đã tồn tại */
    boolean register(String user, String sha256Hex, String role);

    /** @return vai trò nếu đúng tên và mật khẩu, ngược lại {@code null} */
    String authenticate(String user, String sha256Hex);

    /** Tạo lớp với mã 6 chữ số chưa dùng, trả về mã đó. */
    int createRoom(String name, String teacherUser);

    /** Mọi lớp đã lưu; {@code memberCount} luôn là 0 (số online nằm trong RAM). */
    List<RoomInfo> listRooms();

    /** Lớp có mã {@code roomId}, hoặc rỗng nếu không có. {@code memberCount} = 0. */
    Optional<RoomInfo> findRoom(int roomId);

    @Override
    void close();
}
