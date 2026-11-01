package com.pbl4.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketUtilsTest {

    @Test
    void basicRoundTrip() throws Exception {
        byte type = PacketType.SCREEN_FRAME; // 0x12 = 18
        byte[] payload = {10, 20, 30, 40, 50};

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        PacketUtils.sendPacket(dos, type, payload);

        byte[] wire = bos.toByteArray();
        assertEquals(4 + 1 + payload.length, wire.length);

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(wire));
        Packet got = PacketUtils.readPacket(dis);
        assertEquals(type, got.getType());
        assertArrayEquals(payload, got.getPayload());
    }

    @Test
    void emptyPayload() throws Exception {
        byte[] wire = PacketUtils.encode(PacketType.ROOM_LIST_REQ, new byte[0]);
        assertEquals(1, wire[3]);
        assertEquals(5, wire.length);

        Packet got = PacketUtils.readPacket(new DataInputStream(new ByteArrayInputStream(wire)));
        assertEquals(PacketType.ROOM_LIST_REQ, got.getType());
        assertEquals(0, got.getPayload().length);
    }

    @Test
    void largePayload() throws Exception {
        byte[] big = new byte[62_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i * 31);
        }
        byte[] wire = PacketUtils.encode(PacketType.SCREEN_FRAME, big);
        assertEquals(62_005, wire.length);

        Packet got = PacketUtils.readPacket(new DataInputStream(new ByteArrayInputStream(wire)));
        assertEquals(PacketType.SCREEN_FRAME, got.getType());
        assertArrayEquals(big, got.getPayload());
    }

    @Test
    void backToBackPackets() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        PacketUtils.sendPacket(dos, PacketType.LOGIN_REQ, "user".getBytes(StandardCharsets.UTF_8));
        PacketUtils.sendPacket(dos, PacketType.ROOM_LIST_REQ, new byte[0]);
        PacketUtils.sendPacket(dos, PacketType.CHAT_TEXT, "hello".getBytes(StandardCharsets.UTF_8));

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));
        Packet p1 = PacketUtils.readPacket(dis);
        Packet p2 = PacketUtils.readPacket(dis);
        Packet p3 = PacketUtils.readPacket(dis);

        assertEquals(PacketType.LOGIN_REQ, p1.getType());
        assertEquals(PacketType.ROOM_LIST_REQ, p2.getType());
        assertEquals(PacketType.CHAT_TEXT, p3.getType());
        assertEquals(4, p1.getPayload().length);
        assertEquals(0, p2.getPayload().length);
        assertEquals(5, p3.getPayload().length);
    }

    @Test
    void rejectsAbsurdLength() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(Integer.MAX_VALUE);
        dos.writeByte(PacketType.CHAT_TEXT);
        dos.flush();

        assertThrows(IOException.class, () ->
                PacketUtils.readPacket(new DataInputStream(
                        new ByteArrayInputStream(bos.toByteArray()))));
    }

    @Test
    void encodeRejectsOversizedPayload() {
        assertThrows(IllegalArgumentException.class, () ->
                PacketUtils.encode(PacketType.SCREEN_FRAME, new byte[Protocol.MAX_PAYLOAD]));
    }

    @Test
    void encodeLayout() {
        assertArrayEquals(
                new byte[]{0, 0, 0, 3, 0x0D, 7, 8},
                PacketUtils.encode((byte) 0x0D, new byte[]{7, 8}));
    }
}
