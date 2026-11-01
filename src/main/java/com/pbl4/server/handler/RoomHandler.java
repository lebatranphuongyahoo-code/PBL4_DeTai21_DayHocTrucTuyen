package com.pbl4.server.handler;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.Protocol;
import com.pbl4.common.RoomInfo;
import com.pbl4.common.msg.Messages;
import com.pbl4.server.ClientHandler;
import com.pbl4.server.Room;
import com.pbl4.server.RoomManager;
import com.pbl4.server.db.DatabaseManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Xử lý tạo lớp, vào/rời lớp, liệt kê lớp và chat văn bản. */
public final class RoomHandler {

    private final RoomManager rooms;
    private final DatabaseManager db;

    public RoomHandler(RoomManager rooms, DatabaseManager db) {
        this.rooms = rooms;
        this.db = db;
    }

    public void handleCreateRoom(ClientHandler c, Packet packet) throws IOException {
        if (!requireLogin(c) || !requireTeacher(c)) {
            return;
        }
        String roomName = Messages.CreateRoomRequest.decode(packet.getPayload()).roomName().trim();
        if (roomName.isEmpty()) {
            c.sendControl(PacketType.CREATE_ROOM_RES,
                    new Messages.CreateRoomResponse(false, "Ten lop khong duoc de trong", -1).encode());
            return;
        }

        // Cơ sở dữ liệu cấp mã phòng; lớp chỉ mở trong RAM khi có người đầu tiên vào.
        int newRoomId = db.createRoom(roomName, c.getUsername());

        c.sendControl(PacketType.CREATE_ROOM_RES,
                new Messages.CreateRoomResponse(true, "Da tao lop " + roomName, newRoomId).encode());
        c.log("tao lop '" + roomName + "' ma " + newRoomId + " boi " + c.getUsername());
    }

    public void handleJoinRoom(ClientHandler c, Packet packet) throws IOException {
        if (!requireLogin(c)) {
            return;
        }
        Messages.JoinRoomRequest req = Messages.JoinRoomRequest.decode(packet.getPayload());
        int targetRoomId = req.roomId();
        int clientUdpPort = req.udpPort();

        Optional<RoomInfo> found = db.findRoom(targetRoomId);
        if (found.isEmpty()) {
            c.sendControl(PacketType.JOIN_ROOM_RES,
                    new Messages.StatusResponse(false, "Lop khong ton tai hoac da dong").encode());
            return;
        }
        RoomInfo info = found.get();

        // Mỗi client chỉ ở trong MỘT phòng tại một thời điểm (mục 1.5.4).
        leaveCurrentRoom(c);
        c.enterRoom(info.roomId(), req.udpPort()); // đặt trạng thái TRƯỚC khi thêm, để relay thấy cổng UDP ngay

        Room room = null;
        for (int attempt = 0; attempt < 50 && room == null; attempt++) {
            Room candidate = rooms.openIfAbsent(info);
            if (candidate.tryAddMember(c)) {
                room = candidate;
            } else {
                Thread.yield();                    // lớp cũ vừa đóng, chờ RoomManager gỡ nó ra rồi thử lại
            }
        }
        if (room == null) {
            c.clearRoomState();
            c.sendControl(PacketType.JOIN_ROOM_RES,
                    new Messages.StatusResponse(false, "Lop dang dong, vui long thu lai").encode());
            return;
        }

        c.sendControl(PacketType.JOIN_ROOM_RES,
                new Messages.StatusResponse(true, "Da vao lop " + room.getRoomName()).encode());
        c.log("vao lop " + targetRoomId + " (" + c.getUsername() + "), cong UDP nhan thoai = " + clientUdpPort);

        // Gửi lại danh sách thành viên cho TẤT CẢ mọi người trong lớp (bước 2.2).
        int notified = room.broadcastUserList();
        c.log("phat USER_LIST cho " + notified + " nguoi trong lop " + targetRoomId);
    }

    public void handleLeaveRoom(ClientHandler c, Packet packet) throws IOException {
        int targetRoomId = Messages.RoomIdOnly.decode(packet.getPayload()).roomId();
        if (targetRoomId != c.getRoomId()) {
            c.sendError("Ban khong o trong lop " + targetRoomId);
            return;
        }
        leaveCurrentRoom(c);
    }

    /** Rời lớp hiện tại (nếu có), phát lại USER_LIST, và dọn lớp rỗng khỏi bộ nhớ. */
    public void leaveCurrentRoom(ClientHandler c) {
        int current = c.clearRoomState();
        if (current < 0) {
            return;
        }
        Room room = rooms.get(current);
        if (room == null) {
            return;
        }
        if (room.removeMember(c)) {
            c.log("roi lop " + current + " (" + c.getUsername() + ")");
            room.broadcastUserList();
        }
        if (room.isEmpty()) {
            rooms.removeIfEmpty(current);
            c.log("lop " + current + " khong con ai -> da xoa khoi bo nho");
        }
    }

    public void handleRoomList(ClientHandler c) {
        List<RoomInfo> stored = db.listRooms();
        List<RoomInfo> list = new ArrayList<>(stored.size());
        for (RoomInfo info : stored) {
            list.add(new RoomInfo(info.roomId(), info.roomName(), info.teacherUsername(),
                    rooms.memberCount(info.roomId())));
        }
        c.sendControl(PacketType.ROOM_LIST_RES, new Messages.RoomList(list).encode());
    }

    public void handleChat(ClientHandler c, Packet packet) throws IOException {
        if (!requireLogin(c)) {
            return;
        }
        Room room = currentRoom(c);
        if (room == null) {
            c.sendError("Ban chua o trong lop nao");
            return;
        }
        try {
            Messages.ChatText.decode(packet.getPayload());
        } catch (IOException e) {
            c.sendError("Tin nhan khong hop le");
            return;
        }
        // Chuyển tiếp nguyên payload cho cả lớp, kể cả người gửi, để giao diện của họ
        // hiển thị được tin nhắn của chính mình.
        room.broadcastControl(PacketType.CHAT_TEXT, packet.getPayload());
    }

    private Room currentRoom(ClientHandler c) {
        int current = c.getRoomId();
        return (current < 0) ? null : rooms.get(current);
    }

    private static boolean requireLogin(ClientHandler c) {
        if (!c.isLoggedIn()) {
            c.sendError("Ban chua dang nhap");
            return false;
        }
        return true;
    }

    private static boolean requireTeacher(ClientHandler c) {
        if (!Protocol.ROLE_TEACHER.equals(c.getRole())) {
            c.sendError("Chi giao vien duoc tao lop");
            return false;
        }
        return true;
    }
}
