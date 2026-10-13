package com.pbl4.common;

import java.util.Arrays;

/**
 * Một gói tin đã được tách khỏi luồng byte TCP: {@code type} + {@code payload}.
 *
 * <p>Lớp bất biến (immutable): sau khi tạo thì không sửa được {@code type} nữa.
 * Riêng mảng {@code payload} Java không cho bất biến thật, nên quy ước của nhóm là
 * <b>không ai sửa mảng payload sau khi tạo gói</b>.</p>
 *
 * <p>Người viết: TV1 · Người dùng: cả 3.</p>
 */
public final class Packet {

    private final byte type;
    private final byte[] payload;

    public Packet(byte type, byte[] payload) {
        this.type = type;
        this.payload = (payload == null) ? new byte[0] : payload;
    }

    public static Packet of(byte type, byte[] payload) {
        return new Packet(type, payload);
    }

    public byte getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int payloadLength() {
        return payload.length;
    }

    /** Tên loại gói, dùng cho log. */
    public String typeName() {
        return PacketType.name(type);
    }

    @Override
    public String toString() {
        return "Packet{" + PacketType.name(type)
                + ", payload=" + payload.length + " byte"
                + (payload.length <= 32 ? " " + Arrays.toString(payload) : "")
                + "}";
    }
}
