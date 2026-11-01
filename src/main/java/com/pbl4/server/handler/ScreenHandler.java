package com.pbl4.server.handler;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.Protocol;
import com.pbl4.common.msg.Messages;
import com.pbl4.server.ClientHandler;
import com.pbl4.server.Room;
import com.pbl4.server.RoomManager;

/** Xử lý chia sẻ màn hình ({@code SCREEN_START}, {@code SCREEN_STOP}, {@code SCREEN_FRAME}). */
public final class ScreenHandler {

    private final RoomManager rooms;

    public ScreenHandler(RoomManager rooms) {
        this.rooms = rooms;
    }

    /**
     * Bước 3.1 — "không bao giờ tin client".
     *
     * <p>Gói {@code SCREEN_START} chỉ chứa {@code roomId}, client KHÔNG gửi kèm vai trò.
     * Server tự kiểm tra vai trò và quyền chủ lớp của chính phiên đăng nhập này (đã lấy từ cơ sở
     * dữ liệu lúc đăng nhập). Nếu kiểm tra ở giao diện client thì chỉ cần sửa client, hoặc dùng
     * công cụ khác gửi gói, là qua được toàn bộ kiểm tra.</p>
     */
    public void handleScreenStart(ClientHandler c) {
        if (!requireLogin(c)) {
            return;
        }
        Room room = currentRoom(c);
        if (room == null) {
            c.sendError("Ban chua o trong lop nao");
            return;
        }
        if (!Protocol.ROLE_TEACHER.equals(c.getRole()) || !room.isOwner(c.getUsername())) {
            c.log("TU CHOI chia se man hinh: " + c.getUsername() + " co vai tro " + c.getRole());
            c.sendError("Chi giao vien cua lop duoc chia se man hinh");
            return;   // KHÔNG bật cờ sharing
        }

        room.setSharing(true);
        int n = room.broadcastControl(PacketType.SCREEN_START,
                new Messages.ScreenNotice(room.getRoomId(), room.getTeacherUsername()).encode());
        c.log("bat dau chia se man hinh lop " + room.getRoomId() + " (thong bao " + n + " nguoi)");
    }

    public void handleScreenStop(ClientHandler c) {
        Room room = currentRoom(c);
        if (room == null) {
            return;
        }
        if (!Protocol.ROLE_TEACHER.equals(c.getRole()) || !room.isOwner(c.getUsername())) {
            c.sendError("Chi giao vien cua lop duoc dung chia se man hinh");
            return;
        }
        room.setSharing(false);
        room.broadcastControl(PacketType.SCREEN_STOP,
                new Messages.ScreenNotice(room.getRoomId(), "Giao vien da dung chia se man hinh").encode());
        c.log("dung chia se man hinh lop " + room.getRoomId());
    }

    /**
     * Bước 3.2 — chuyển tiếp frame.
     *
     * <p>Ba lớp bảo vệ: (1) phải đang ở trong lớp, (2) cờ chia sẻ của lớp phải đang bật,
     * (3) người gửi phải là giáo viên chủ lớp. Nhờ vậy client tự ý gửi frame mà không xin phép
     * sẽ bị bỏ qua hoàn toàn.</p>
     */
    public void handleScreenFrame(ClientHandler c, Packet packet) {
        Room room = currentRoom(c);
        if (room == null || !room.isSharing()) {
            return;   // bỏ qua, không relay
        }
        if (!room.isOwner(c.getUsername())) {
            return;
        }
        room.broadcastFrame(packet.getPayload());
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
}
