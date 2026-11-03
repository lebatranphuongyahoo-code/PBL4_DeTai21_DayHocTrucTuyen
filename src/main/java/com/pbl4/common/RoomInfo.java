package com.pbl4.common;

/**
 * Thông tin một lớp học, dùng cho gói {@link PacketType#ROOM_LIST_RES} (0x0B).
 *
 * <p>{@code memberCount} và {@code teacherPresent} là trạng thái online tạm thời từ
 * {@code RoomManager}; metadata của phòng được lưu bền vững trong cơ sở dữ liệu.</p>
 *
 * <p>Người viết: TV1 · Người dùng: TV1 (đóng gói) và TV3 (đọc để hiển thị danh sách lớp).</p>
 */
public record RoomInfo(int roomId, String roomName, String teacherUsername,
                       int memberCount, boolean teacherPresent) {
    public RoomInfo(int roomId, String roomName, String teacherUsername, int memberCount) {
        this(roomId, roomName, teacherUsername, memberCount, false);
    }
}
