package com.pbl4.common.msg;

import com.pbl4.common.PacketUtils;
import com.pbl4.common.RoomInfo;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagesTest {

    // ---------------- (a) Golden bytes ----------------

    @Test
    void loginRequestMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> {
            o.writeUTF("gv1");
            o.writeUTF("hash123");
        });
        assertArrayEquals(legacy, new Messages.LoginRequest("gv1", "hash123").encode());
    }

    @Test
    void loginResponseMatchesLegacyBytes() {
        byte[] legacyOk = PacketUtils.buildPayload(o -> {
            o.writeByte(1);
            o.writeUTF("Dang nhap thanh cong");
            o.writeUTF("TEACHER");
        });
        assertArrayEquals(legacyOk,
                new Messages.LoginResponse(true, "Dang nhap thanh cong", "TEACHER").encode());

        byte[] legacyFail = PacketUtils.buildPayload(o -> {
            o.writeByte(0);
            o.writeUTF("Sai ten dang nhap hoac mat khau");
            o.writeUTF("");
        });
        assertArrayEquals(legacyFail,
                new Messages.LoginResponse(false, "Sai ten dang nhap hoac mat khau", "").encode());
    }

    @Test
    void registerRequestMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> {
            o.writeUTF("hs1");
            o.writeUTF("hash456");
            o.writeUTF("STUDENT");
        });
        assertArrayEquals(legacy,
                new Messages.RegisterRequest("hs1", "hash456", "STUDENT").encode());
    }

    @Test
    void statusResponseMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> {
            o.writeByte(1);
            o.writeUTF("Vao lop thanh cong");
        });
        assertArrayEquals(legacy,
                new Messages.StatusResponse(true, "Vao lop thanh cong").encode());
    }

    @Test
    void createRoomRequestMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> o.writeUTF("Toan roi rac"));
        assertArrayEquals(legacy, new Messages.CreateRoomRequest("Toan roi rac").encode());
    }

    @Test
    void createRoomResponseMatchesLegacyBytes() {
        byte[] legacyOk = PacketUtils.buildPayload(o -> {
            o.writeByte(1);
            o.writeUTF("Tao lop thanh cong");
            o.writeInt(123456);
        });
        assertArrayEquals(legacyOk,
                new Messages.CreateRoomResponse(true, "Tao lop thanh cong", 123456).encode());

        byte[] legacyFail = PacketUtils.buildPayload(o -> {
            o.writeByte(0);
            o.writeUTF("Chi giao vien moi duoc tao lop");
            o.writeInt(-1);
        });
        assertArrayEquals(legacyFail,
                new Messages.CreateRoomResponse(false, "Chi giao vien moi duoc tao lop", -1).encode());
    }

    @Test
    void joinRoomRequestMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> {
            o.writeInt(123456);
            o.writeInt(9010);
        });
        assertArrayEquals(legacy, new Messages.JoinRoomRequest(123456, 9010).encode());
    }

    @Test
    void roomIdOnlyMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> o.writeInt(654321));
        assertArrayEquals(legacy, new Messages.RoomIdOnly(654321).encode());
    }

    @Test
    void roomListMatchesLegacyBytes() {
        byte[] legacyEmpty = PacketUtils.buildPayload(o -> o.writeInt(0));
        assertArrayEquals(legacyEmpty, new Messages.RoomList(List.of()).encode());

        byte[] legacyTwo = PacketUtils.buildPayload(o -> {
            o.writeInt(2);
            o.writeInt(100001);
            o.writeUTF("Mang may tinh");
            o.writeUTF("lan");
            o.writeInt(3);
            o.writeInt(200002);
            o.writeUTF("He dieu hanh");
            o.writeUTF("minh");
            o.writeInt(0);
        });
        List<RoomInfo> two = List.of(
                new RoomInfo(100001, "Mang may tinh", "lan", 3),
                new RoomInfo(200002, "He dieu hanh", "minh", 0));
        assertArrayEquals(legacyTwo, new Messages.RoomList(two).encode());
    }

    @Test
    void userListMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> {
            o.writeInt(2);
            o.writeUTF("lan");
            o.writeUTF("TEACHER");
            o.writeUTF("khach");
            o.writeUTF("");
        });
        List<Messages.Member> members = List.of(
                new Messages.Member("lan", "TEACHER"),
                new Messages.Member("khach", null));
        assertArrayEquals(legacy, new Messages.UserList(members).encode());
    }

    @Test
    void chatTextMatchesLegacyBytes() {
        String text = "[lan]: Xin chào lớp PBL4 — Đề tài 21!";
        byte[] legacy = PacketUtils.buildPayload(o -> {
            o.writeInt(123456);
            o.writeUTF(text);
        });
        assertArrayEquals(legacy, new Messages.ChatText(123456, text).encode());
    }

    @Test
    void screenNoticeMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> {
            o.writeInt(123456);
            o.writeUTF("lan");
        });
        assertArrayEquals(legacy, new Messages.ScreenNotice(123456, "lan").encode());
    }

    @Test
    void errorMessageMatchesLegacyBytes() {
        byte[] legacy = PacketUtils.buildPayload(o -> o.writeUTF("Ban chua dang nhap"));
        assertArrayEquals(legacy, new Messages.ErrorMessage("Ban chua dang nhap").encode());
    }

    // ---------------- (b) Round-trip ----------------

    @Test
    void roundTripAllRecordTypes() throws Exception {
        var loginReq = new Messages.LoginRequest("u", "h");
        assertEquals(loginReq, Messages.LoginRequest.decode(loginReq.encode()));

        var loginRes = new Messages.LoginResponse(true, "ok", "TEACHER");
        assertEquals(loginRes, Messages.LoginResponse.decode(loginRes.encode()));

        var regReq = new Messages.RegisterRequest("u", "h", "STUDENT");
        assertEquals(regReq, Messages.RegisterRequest.decode(regReq.encode()));

        var status = new Messages.StatusResponse(false, "loi");
        assertEquals(status, Messages.StatusResponse.decode(status.encode()));

        var createReq = new Messages.CreateRoomRequest("Lop A");
        assertEquals(createReq, Messages.CreateRoomRequest.decode(createReq.encode()));

        var createRes = new Messages.CreateRoomResponse(true, "ok", 111222);
        assertEquals(createRes, Messages.CreateRoomResponse.decode(createRes.encode()));

        var joinReq = new Messages.JoinRoomRequest(111222, 5000);
        assertEquals(joinReq, Messages.JoinRoomRequest.decode(joinReq.encode()));

        var roomIdOnly = new Messages.RoomIdOnly(111222);
        assertEquals(roomIdOnly, Messages.RoomIdOnly.decode(roomIdOnly.encode()));

        var roomList = new Messages.RoomList(List.of(
                new RoomInfo(111222, "Lop A", "lan", 4)));
        assertEquals(roomList, Messages.RoomList.decode(roomList.encode()));

        var userList = new Messages.UserList(List.of(
                new Messages.Member("lan", "TEACHER"),
                new Messages.Member("minh", "STUDENT")));
        assertEquals(userList, Messages.UserList.decode(userList.encode()));

        var chat = new Messages.ChatText(111222, "Chào cả lớp!");
        assertEquals(chat, Messages.ChatText.decode(chat.encode()));

        var screen = new Messages.ScreenNotice(111222, "Giao vien da dung chia se");
        assertEquals(screen, Messages.ScreenNotice.decode(screen.encode()));

        var err = new Messages.ErrorMessage("Co loi");
        assertEquals(err, Messages.ErrorMessage.decode(err.encode()));
    }

    // ---------------- (c) Payload hỏng ----------------

    @Test
    void corruptedPayloadsAndTrailingBytes() throws Exception {
        byte[] negCount = PacketUtils.buildPayload(o -> o.writeInt(-1));
        assertThrows(IOException.class, () -> Messages.RoomList.decode(negCount));

        byte[] hugeCount = PacketUtils.buildPayload(o -> o.writeInt(100_001));
        assertThrows(IOException.class, () -> Messages.RoomList.decode(hugeCount));

        byte[] trailing = new byte[]{0, 0, 0, 5, 0, 0, 0x23, 0x28};
        assertEquals(5, Messages.RoomIdOnly.decode(trailing).roomId());

        assertThrows(IOException.class, () -> Messages.LoginRequest.decode(new byte[]{0}));
    }
}
