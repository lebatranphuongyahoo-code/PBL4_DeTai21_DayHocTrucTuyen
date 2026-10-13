package com.pbl4.common;

/**
 * Mã byte của từng loại gói — copy CHÍNH XÁC từ bảng 1.5.3 của tài liệu hợp đồng.
 *
 * <p>Sai một mã ở đây là sai cả hệ thống, và lỗi sẽ rất khó tìm vì server chỉ "im lặng"
 * khi gặp loại gói không biết.</p>
 *
 * <p>Người viết: TV1 · Người dùng: cả 3.</p>
 */
public final class PacketType {

    private PacketType() {
    }

    // ---------- Tài khoản (TV3 xử lý) ----------
    public static final byte LOGIN_REQ = 0x01;       // C→S: writeUTF(username), writeUTF(sha256Hex)
    public static final byte LOGIN_RES = 0x02;       // S→C: writeByte(ok), writeUTF(msg), writeUTF(role)
    public static final byte REGISTER_REQ = 0x03;    // C→S: writeUTF(username), writeUTF(sha256Hex), writeUTF(role)
    public static final byte REGISTER_RES = 0x04;    // S→C: writeByte(ok), writeUTF(msg)

    // ---------- Phòng học (TV1 xử lý) ----------
    public static final byte CREATE_ROOM_REQ = 0x05; // C→S: writeUTF(roomName)
    public static final byte CREATE_ROOM_RES = 0x06; // S→C: writeByte(ok), writeUTF(msg), writeInt(roomId)
    public static final byte JOIN_ROOM_REQ = 0x07;   // C→S: writeInt(roomId), writeInt(udpPort)
    public static final byte JOIN_ROOM_RES = 0x08;   // S→C: writeByte(ok), writeUTF(msg)
    public static final byte LEAVE_ROOM_REQ = 0x09;  // C→S: writeInt(roomId)
    public static final byte ROOM_LIST_REQ = 0x0A;   // C→S: payload rỗng
    public static final byte ROOM_LIST_RES = 0x0B;   // S→C: writeInt(n), rồi n lần (roomId, roomName, teacher, soNguoi)
    public static final byte USER_LIST = 0x0C;       // S→C: writeInt(n), rồi n lần (username, role)
    public static final byte CHAT_TEXT = 0x0D;       // 2 chiều: writeInt(roomId), writeUTF(text)

    // ---------- Màn hình (TV1 relay, TV2 nén/giải mã) ----------
    public static final byte SCREEN_START = 0x10;    // C→S: writeInt(roomId) — server kiểm tra role
    public static final byte SCREEN_STOP = 0x11;     // C→S: writeInt(roomId)
    public static final byte SCREEN_FRAME = 0x12;    // C→S→C: byte JPEG thô, server relay nguyên payload

    // ---------- Mic (TV1 xử lý) ----------
    public static final byte AUDIO_ON = 0x13;        // C→S: writeInt(roomId)
    public static final byte AUDIO_OFF = 0x14;       // C→S: writeInt(roomId)

    // ---------- Lỗi ----------
    public static final byte ERROR = 0x7F;           // S→C: writeUTF(message)

    /** Tên loại gói để in log — giúp đọc log nhanh hơn nhiều so với nhìn số hex. */
    public static String name(byte type) {
        return switch (type) {
            case LOGIN_REQ -> "LOGIN_REQ";
            case LOGIN_RES -> "LOGIN_RES";
            case REGISTER_REQ -> "REGISTER_REQ";
            case REGISTER_RES -> "REGISTER_RES";
            case CREATE_ROOM_REQ -> "CREATE_ROOM_REQ";
            case CREATE_ROOM_RES -> "CREATE_ROOM_RES";
            case JOIN_ROOM_REQ -> "JOIN_ROOM_REQ";
            case JOIN_ROOM_RES -> "JOIN_ROOM_RES";
            case LEAVE_ROOM_REQ -> "LEAVE_ROOM_REQ";
            case ROOM_LIST_REQ -> "ROOM_LIST_REQ";
            case ROOM_LIST_RES -> "ROOM_LIST_RES";
            case USER_LIST -> "USER_LIST";
            case CHAT_TEXT -> "CHAT_TEXT";
            case SCREEN_START -> "SCREEN_START";
            case SCREEN_STOP -> "SCREEN_STOP";
            case SCREEN_FRAME -> "SCREEN_FRAME";
            case AUDIO_ON -> "AUDIO_ON";
            case AUDIO_OFF -> "AUDIO_OFF";
            case ERROR -> "ERROR";
            default -> "UNKNOWN(0x" + String.format("%02X", type) + ")";
        };
    }
}
