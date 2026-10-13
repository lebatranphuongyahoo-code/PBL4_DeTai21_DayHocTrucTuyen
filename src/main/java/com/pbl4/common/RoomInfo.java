package com.pbl4.common;

/**
 * Thông tin một lớp học, dùng cho gói {@link PacketType#ROOM_LIST_RES} (0x0B).
 *
 * <p>Trường {@code memberCount} là số người <b>đang online</b> — lấy từ {@code RoomManager}
 * (trạng thái sống trong RAM), không phải từ cơ sở dữ liệu.</p>
 *
 * <p>Người viết: TV1 · Người dùng: TV1 (đóng gói) và TV3 (đọc để hiển thị danh sách lớp).</p>
 */
public record RoomInfo(int roomId, String roomName, String teacherUsername, int memberCount) {
}
