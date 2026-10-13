package com.pbl4.tools;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;

/**
 * Kiểm tra length-prefix framing bằng cách ghi một gói vào bộ đệm rồi đọc lại.
 *
 * <p>Đây là bài tự kiểm bắt buộc của bước 1.2 — chạy được bài này nghĩa là
 * <b>hợp đồng giao thức của TV1 đã đáng tin</b>, và hai bạn kia có thể dùng ngay.</p>
 *
 * <pre>
 * Chay: java -cp target/classes com.pbl4.tools.PacketRoundTripTest
 * Ky vong dong dau tien:
 *   round-trip OK | type=18 | payload=5 byte
 * </pre>
 */
public class PacketRoundTripTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        java.util.Locale.setDefault(java.util.Locale.US);   // so lieu in ra dung dau cham
        testBasicRoundTrip();
        testEmptyPayload();
        testLargePayload();
        testBackToBackPackets();
        testRejectsAbsurdLength();

        System.out.println();
        if (failures == 0) {
            System.out.println("==> TAT CA KIEM TRA DAT (" + 5 + "/5)");
        } else {
            System.out.println("==> CO " + failures + " KIEM TRA THAT BAI");
            System.exit(1);
        }
    }

    /** Bài chính — in ra đúng dòng mà tài liệu yêu cầu. */
    private static void testBasicRoundTrip() throws Exception {
        byte type = PacketType.SCREEN_FRAME;             // 0x12 = 18
        byte[] payload = {10, 20, 30, 40, 50};

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        PacketUtils.sendPacket(dos, type, payload);

        byte[] wire = bos.toByteArray();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(wire));
        Packet got = PacketUtils.readPacket(dis);

        boolean ok = got.getType() == type && Arrays.equals(got.getPayload(), payload);
        check("round-trip", ok);

        System.out.printf("round-trip OK | type=%d | payload=%d byte%n",
                got.getType(), got.getPayload().length);
        System.out.println("   (do dai ghi tren duong truyen = " + wire.length
                + " byte = 4 + 1 + " + payload.length + ")");
    }

    /** Payload rỗng: độ dài ghi là 1 (chỉ có byte type), không phải 0. */
    private static void testEmptyPayload() throws Exception {
        byte[] wire = PacketUtils.encode(PacketType.ROOM_LIST_REQ, new byte[0]);
        check("payload rong -> do dai = 1", wire[3] == 1 && wire.length == 5);

        Packet got = PacketUtils.readPacket(
                new DataInputStream(new ByteArrayInputStream(wire)));
        check("payload rong doc lai duoc", got.getType() == PacketType.ROOM_LIST_REQ
                && got.getPayload().length == 0);
    }

    /** Một frame JPEG thật ~62 KB: phải đi qua nguyên vẹn. */
    private static void testLargePayload() throws Exception {
        byte[] big = new byte[62_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i * 31);
        }
        byte[] wire = PacketUtils.encode(PacketType.SCREEN_FRAME, big);
        Packet got = PacketUtils.readPacket(
                new DataInputStream(new ByteArrayInputStream(wire)));
        check("frame 62 KB nguyen ven", Arrays.equals(got.getPayload(), big));
        System.out.println("   frame lon: " + wire.length + " byte tren duong truyen");
    }

    /**
     * Ba gói ghi liên tiếp rồi đọc liên tiếp — mô phỏng đúng tình huống
     * nhiều gói bị TCP gộp thành một lần đọc. Đây là lý do phải có trường độ dài.
     */
    private static void testBackToBackPackets() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        PacketUtils.sendPacket(dos, PacketType.LOGIN_REQ, "user".getBytes("UTF-8"));
        PacketUtils.sendPacket(dos, PacketType.ROOM_LIST_REQ, new byte[0]);
        PacketUtils.sendPacket(dos, PacketType.CHAT_TEXT, "hello".getBytes("UTF-8"));

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));
        Packet p1 = PacketUtils.readPacket(dis);
        Packet p2 = PacketUtils.readPacket(dis);
        Packet p3 = PacketUtils.readPacket(dis);

        check("3 goi lien tiep khong dinh nhau",
                p1.getType() == PacketType.LOGIN_REQ
                        && p2.getType() == PacketType.ROOM_LIST_REQ
                        && p3.getType() == PacketType.CHAT_TEXT
                        && p1.getPayload().length == 4
                        && p2.getPayload().length == 0
                        && p3.getPayload().length == 5);
    }

    /** Trường độ dài rác phải bị từ chối, không được cấp phát bộ nhớ khổng lồ. */
    private static void testRejectsAbsurdLength() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(Integer.MAX_VALUE);       // độ dài rác
        dos.writeByte(PacketType.CHAT_TEXT);
        dos.flush();

        boolean rejected = false;
        try {
            PacketUtils.readPacket(new DataInputStream(
                    new ByteArrayInputStream(bos.toByteArray())));
        } catch (Exception e) {
            rejected = true;
        }
        check("tu choi do dai rac", rejected);
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  [OK]   " : "  [FAIL] ") + name);
        if (!ok) {
            failures++;
        }
    }
}
