package com.pbl4.common.msg;

import com.pbl4.common.PacketUtils;
import com.pbl4.common.RoomInfo;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Định dạng payload của mọi loại gói — NGUỒN SỰ THẬT DUY NHẤT (bảng 1.5.3).
 *
 * <p>Trước đây mỗi màn hình, mỗi công cụ tự viết {@code writeUTF/readInt} theo trí nhớ; lệch một trường
 * là bên nhận đọc ra rác. Giờ server, client và công cụ đều đi qua các record dưới đây.</p>
 *
 * <p>Quy ước: {@code encode()} trả payload (KHÔNG gồm 4 byte độ dài và 1 byte type);
 * {@code decode(byte[])} ném {@link IOException} khi payload hỏng.</p>
 */
public final class Messages {

    /** Trần số phần tử của một danh sách — chặn cấp phát khổng lồ khi payload rác. */
    public static final int MAX_LIST_SIZE = 100_000;

    private Messages() {
    }

    // ---------------- Tài khoản ----------------

    /** LOGIN_REQ (0x01). */
    public record LoginRequest(String username, String passwordHash) {
        public LoginRequest {
            username = Objects.requireNonNullElse(username, "");
            passwordHash = Objects.requireNonNullElse(passwordHash, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeUTF(username); o.writeUTF(passwordHash); });
        }
        public static LoginRequest decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new LoginRequest(r.readUTF(), r.readUTF());
        }
    }

    /** LOGIN_RES (0x02). */
    public record LoginResponse(boolean ok, String message, String role) {
        public LoginResponse {
            message = Objects.requireNonNullElse(message, "");
            role = Objects.requireNonNullElse(role, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeByte(ok ? 1 : 0); o.writeUTF(message); o.writeUTF(role);
            });
        }
        public static LoginResponse decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new LoginResponse(r.readByte() != 0, r.readUTF(), r.readUTF());
        }
    }

    /** REGISTER_REQ (0x03). */
    public record RegisterRequest(String username, String passwordHash, String role) {
        public RegisterRequest {
            username = Objects.requireNonNullElse(username, "");
            passwordHash = Objects.requireNonNullElse(passwordHash, "");
            role = Objects.requireNonNullElse(role, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeUTF(username); o.writeUTF(passwordHash); o.writeUTF(role);
            });
        }
        public static RegisterRequest decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new RegisterRequest(r.readUTF(), r.readUTF(), r.readUTF());
        }
    }

    /** REGISTER_RES (0x04), JOIN_ROOM_RES (0x08) — cùng dạng {ok, message}. */
    public record StatusResponse(boolean ok, String message) {
        public StatusResponse {
            message = Objects.requireNonNullElse(message, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeByte(ok ? 1 : 0); o.writeUTF(message); });
        }
        public static StatusResponse decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new StatusResponse(r.readByte() != 0, r.readUTF());
        }
    }

    // ---------------- Phòng học ----------------

    /** CREATE_ROOM_REQ (0x05). */
    public record CreateRoomRequest(String roomName) {
        public CreateRoomRequest {
            roomName = Objects.requireNonNullElse(roomName, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> o.writeUTF(roomName));
        }
        public static CreateRoomRequest decode(byte[] p) throws IOException {
            return new CreateRoomRequest(PacketUtils.payloadReader(p).readUTF());
        }
    }

    /** CREATE_ROOM_RES (0x06). {@code roomId = -1} khi thất bại. */
    public record CreateRoomResponse(boolean ok, String message, int roomId) {
        public CreateRoomResponse {
            message = Objects.requireNonNullElse(message, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeByte(ok ? 1 : 0); o.writeUTF(message); o.writeInt(roomId);
            });
        }
        public static CreateRoomResponse decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new CreateRoomResponse(r.readByte() != 0, r.readUTF(), r.readInt());
        }
    }

    /** JOIN_ROOM_REQ (0x07). {@code udpPort} = cổng client bind để nhận thoại (0 = không nhận). */
    public record JoinRoomRequest(int roomId, int udpPort) {
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeInt(roomId); o.writeInt(udpPort); });
        }
        public static JoinRoomRequest decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new JoinRoomRequest(r.readInt(), r.readInt());
        }
    }

    /**
     * Payload chỉ có {@code roomId}: LEAVE_ROOM_REQ (0x09), SCREEN_START C→S (0x10),
     * SCREEN_STOP C→S (0x11), AUDIO_ON (0x13), AUDIO_OFF (0x14).
     *
     * <p>{@code decode} chỉ đọc 4 byte đầu và bỏ qua phần thừa — bản client cũ gửi kèm
     * cổng UDP trong AUDIO_ON, vẫn phải đọc được.</p>
     */
    public record RoomIdOnly(int roomId) {
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> o.writeInt(roomId));
        }
        public static RoomIdOnly decode(byte[] p) throws IOException {
            return new RoomIdOnly(PacketUtils.payloadReader(p).readInt());
        }
    }

    /** ROOM_LIST_RES (0x0B). ROOM_LIST_REQ (0x0A) có payload rỗng — dùng {@code new byte[0]}. */
    public record RoomList(List<RoomInfo> rooms) {
        public RoomList {
            rooms = List.copyOf(rooms);
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeInt(rooms.size());
                for (RoomInfo info : rooms) {
                    o.writeInt(info.roomId());
                    o.writeUTF(info.roomName());
                    o.writeUTF(info.teacherUsername());
                    o.writeInt(info.memberCount());
                }
            });
        }
        public static RoomList decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            int n = readCount(r);
            List<RoomInfo> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new RoomInfo(r.readInt(), r.readUTF(), r.readUTF(), r.readInt()));
            }
            return new RoomList(out);
        }
    }

    /** Một dòng trong USER_LIST. */
    public record Member(String username, String role) {
        public Member {
            username = Objects.requireNonNullElse(username, "");
            role = Objects.requireNonNullElse(role, "");
        }
    }

    /** USER_LIST (0x0C). */
    public record UserList(List<Member> members) {
        public UserList {
            members = List.copyOf(members);
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeInt(members.size());
                for (Member m : members) {
                    o.writeUTF(m.username());
                    o.writeUTF(m.role());
                }
            });
        }
        public static UserList decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            int n = readCount(r);
            List<Member> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new Member(r.readUTF(), r.readUTF()));
            }
            return new UserList(out);
        }
    }

    /** CHAT_TEXT (0x0D), hai chiều. */
    public record ChatText(int roomId, String text) {
        public ChatText {
            text = Objects.requireNonNullElse(text, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeInt(roomId); o.writeUTF(text); });
        }
        public static ChatText decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new ChatText(r.readInt(), r.readUTF());
        }
    }

    // ---------------- Màn hình ----------------

    /**
     * SCREEN_START S→C (0x10): {@code text} = tên giáo viên.
     * SCREEN_STOP  S→C (0x11): {@code text} = thông báo.
     * (Chiều C→S của hai loại này dùng {@link RoomIdOnly}.) SCREEN_FRAME (0x12) là byte JPEG thô.
     */
    public record ScreenNotice(int roomId, String text) {
        public ScreenNotice {
            text = Objects.requireNonNullElse(text, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeInt(roomId); o.writeUTF(text); });
        }
        public static ScreenNotice decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new ScreenNotice(r.readInt(), r.readUTF());
        }
    }

    // ---------------- Lỗi ----------------

    /** ERROR (0x7F). */
    public record ErrorMessage(String message) {
        public ErrorMessage {
            message = Objects.requireNonNullElse(message, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> o.writeUTF(message));
        }
        public static ErrorMessage decode(byte[] p) throws IOException {
            return new ErrorMessage(PacketUtils.payloadReader(p).readUTF());
        }
    }

    private static int readCount(DataInputStream r) throws IOException {
        int n = r.readInt();
        if (n < 0 || n > MAX_LIST_SIZE) {
            throw new IOException("So phan tu khong hop le: " + n);
        }
        return n;
    }
}
