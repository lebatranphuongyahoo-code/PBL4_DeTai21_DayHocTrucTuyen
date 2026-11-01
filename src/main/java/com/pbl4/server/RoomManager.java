package com.pbl4.server;

import com.pbl4.common.RoomInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sổ quản lý các lớp học đang mở.
 *
 * <p>Dùng {@link ConcurrentHashMap} chứ không dùng {@code HashMap}: nhiều luồng trong thread pool
 * cùng tra cứu và cùng tạo lớp. {@code HashMap} bị sửa đồng thời có thể hỏng cấu trúc bên trong
 * (mất dữ liệu, thậm chí treo vòng lặp vô hạn). {@code ConcurrentHashMap} an toàn mà không cần
 * khoá toàn bộ bảng — nhờ vậy tra cứu phòng không trở thành nút thắt cho cả server.</p>
 *
 * <p><b>Vì sao roomId do cơ sở dữ liệu cấp:</b> hợp đồng quy định
 * {@code DatabaseManager.createRoom()} trả về {@code int roomId}. Nếu lớp này tự sinh mã riêng
 * thì hai không gian mã sẽ lệch nhau sau khi server khởi động lại. Vì vậy
 * {@link #openIfAbsent(RoomInfo)} nhận thông tin phòng đã lưu từ cơ sở dữ liệu truyền vào.</p>
 */
public class RoomManager {

    private final ConcurrentHashMap<Integer, Room> rooms = new ConcurrentHashMap<>();

    /** Lấy lớp đang mở, hoặc mở lớp từ thông tin đã lưu nếu chưa mở. */
    public Room openIfAbsent(RoomInfo info) {
        return rooms.computeIfAbsent(info.roomId(),
                id -> new Room(id, info.roomName(), info.teacherUsername()));
    }

    public Room get(int roomId) {
        return rooms.get(roomId);
    }

    /**
     * Xoá lớp khỏi bộ nhớ nếu không còn ai.
     *
     * <p>Bắt buộc phải gọi khi người cuối cùng rời lớp. Nếu không, mở 100 lớp trong lúc test
     * là RAM phình lên mãi — đây chính là "rò rỉ bộ nhớ" mà hội đồng rất thích hỏi.</p>
     *
     * @return {@code true} nếu lớp đã bị xoá
     */
    public boolean removeIfEmpty(int roomId) {
        Room room = rooms.get(roomId);
        if (room == null || !room.closeIfEmpty()) {
            return false;
        }
        rooms.remove(roomId, room);
        return true;
    }

    public int memberCount(int roomId) {
        Room room = rooms.get(roomId);
        return (room == null) ? 0 : room.memberCount();
    }

    /** Số lớp đang mở. */
    public int size() {
        return rooms.size();
    }

    public List<Room> snapshotRooms() {
        return new ArrayList<>(rooms.values());
    }

    /** Số liệu cho báo cáo: tổng số người đang online trên toàn server. */
    public int totalMembers() {
        int total = 0;
        for (Room r : rooms.values()) {
            total += r.memberCount();
        }
        return total;
    }

    @Override
    public String toString() {
        return "RoomManager{" + rooms.size() + " lop, " + totalMembers() + " nguoi online}";
    }
}
