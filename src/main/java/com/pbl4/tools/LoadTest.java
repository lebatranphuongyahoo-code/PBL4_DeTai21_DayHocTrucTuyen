package com.pbl4.tools;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;
import com.pbl4.common.Sha256;
import com.pbl4.server.RoomManager;
import com.pbl4.server.ServerMain;
import com.pbl4.server.db.DatabaseManager;
import com.pbl4.server.db.InMemoryDatabaseManager;

import java.io.DataInputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chạy tải mức T3 — mở nhiều kết nối giả lập, đo số luồng / CPU / RAM ở từng mốc.
 *
 * <p>Đây là công cụ lấy số liệu cho <b>bảng đo ở Chương 3</b>, và cũng là cách phát hiện
 * <b>rò rỉ luồng</b>: sau khi tất cả client ngắt, số luồng của mình phải quay về mức nền.</p>
 *
 * <h2>⚠️ Cách đo quan trọng hơn con số</h2>
 * <p>{@code ThreadMXBean.getThreadCount()} đếm <b>tất cả</b> luồng của JVM, kể cả luồng nội bộ
 * (GC, trình biên dịch JIT, Reference Handler…). Những luồng đó được tạo trong lúc chạy tải và
 * <b>không bao giờ kết thúc</b>, nên nếu đếm tổng bạn sẽ thấy "số luồng không giảm" và kết luận
 * sai là có rò rỉ. Công cụ này đếm riêng theo <b>tiền tố tên luồng của mình</b>:</p>
 * <ul>
 *   <li>{@code pool-*} — luồng trong thread pool (tối đa {@value Protocol#THREAD_POOL_SIZE}).</li>
 *   <li>{@code srv-writer-*} — luồng ghi riêng của từng client (đúng 1 luồng / 1 kết nối).</li>
 * </ul>
 *
 * <pre>
 * Chay: java -cp target/classes com.pbl4.tools.LoadTest [soClient] [soGiayGiu] [port]
 * Vi du: java -cp target/classes com.pbl4.tools.LoadTest 10 20
 * </pre>
 */
public class LoadTest {

    private static final int[] LEVELS = {1, 3, 5, 10};

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        int maxClients = (args.length > 0) ? Integer.parseInt(args[0]) : 10;
        int holdSeconds = (args.length > 1) ? Integer.parseInt(args[1]) : 10;
        int port = (args.length > 2) ? Integer.parseInt(args[2]) : Protocol.TCP_PORT;

        TestClient.setTarget("127.0.0.1", port);

        System.out.println("########################################################");
        System.out.println("#  DO TAI MUC T3 - Thread Pool / CPU / RAM");
        System.out.println("#  PBL4 De tai 21 - phan cua TV1");
        System.out.println("########################################################");

        RoomManager rooms = new RoomManager();
        DatabaseManager db = new InMemoryDatabaseManager();
        ServerMain server = new ServerMain(port, rooms, db);
        Thread serverThread = new Thread(server::start, "server-main");
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(600);

        com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean)
                        ManagementFactory.getOperatingSystemMXBean();

        int basePool = countThreads("pool-");
        int baseWriter = countThreads("srv-writer-");
        int baseTotal = countThreads();
        System.out.println();
        System.out.println("Muc nen (chua co client nao): pool=" + basePool
                + " writer=" + baseWriter + " tong=" + baseTotal);
        System.out.println();

        // Giáo viên tạo lớp trước
        TestClient.Conn teacher = new TestClient.Conn();
        login(teacher, "lan", Protocol.ROLE_TEACHER);
        teacher.send(PacketType.CREATE_ROOM_REQ,
                PacketUtils.buildPayload(o -> o.writeUTF("Lop do tai")));
        Packet created = teacher.await(PacketType.CREATE_ROOM_RES, 3000);
        int roomId = readRoomId(created);
        joinRoom(teacher, roomId);
        System.out.println("Lop do tai: ma " + roomId);
        System.out.println();

        System.out.printf("%-6s | %-8s | %-10s | %-8s | %-8s | %-9s | %s%n",
                "So KH", "pool-", "srv-writer", "tong", "CPU %", "Heap MB", "Nhan xet");
        System.out.println("-------+----------+------------+----------+----------+-----------+------------------");

        List<TestClient.Conn> clients = new ArrayList<>();
        int previousLevel = 0;
        int maxWriter = 0;

        for (int level : LEVELS) {
            if (level > maxClients) {
                break;
            }
            for (int i = previousLevel; i < level; i++) {
                TestClient.Conn c = new TestClient.Conn();
                login(c, "sv" + i, Protocol.ROLE_STUDENT);
                joinRoom(c, roomId);
                clients.add(c);
            }
            previousLevel = level;
            Thread.sleep(1500);

            int pool = countThreads("pool-");
            int writer = countThreads("srv-writer-");
            int total = countThreads();
            double cpu = os.getProcessCpuLoad() * 100.0;
            long heapMb = (Runtime.getRuntime().totalMemory()
                    - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
            maxWriter = Math.max(maxWriter, writer);

            String note = (writer == level + 1 && pool <= Protocol.THREAD_POOL_SIZE)
                    ? "dung thiet ke" : "!! kiem tra lai";
            System.out.printf("%-6d | %-8d | %-10d | %-8d | %-8.1f | %-9d | %s%n",
                    level, pool, writer, total, cpu, heapMb, note);
        }

        System.out.println();
        System.out.println("Giu tai " + holdSeconds + " giay...");
        Thread.sleep(holdSeconds * 1000L);

        int peakWriter = countThreads("srv-writer-");
        int peakPool = countThreads("pool-");
        System.out.println("Luc cao nhat: pool=" + peakPool + " (tran " + Protocol.THREAD_POOL_SIZE
                + ") | writer=" + peakWriter + " (= 1 luong/ket noi)");
        System.out.println("Nguoi online tren server: " + rooms.totalMembers()
                + " | lop mo: " + rooms.size());

        // ============ Kiểm tra rò rỉ ============
        System.out.println();
        System.out.println("Dang ngat tat ca client...");
        for (TestClient.Conn c : clients) {
            c.close();
        }
        teacher.close();

        for (int i = 0; i < 30; i++) {
            Thread.sleep(500);
            if (countThreads("srv-writer-") == 0) {
                break;
            }
        }

        int afterWriter = countThreads("srv-writer-");
        int afterPool = countThreads("pool-");
        int afterTotal = countThreads();
        System.out.println("Sau khi ngat het: pool=" + afterPool + " (truoc do " + peakPool + ")"
                + " | writer=" + afterWriter + " (truoc do " + peakWriter + ")"
                + " | tong=" + afterTotal);
        System.out.println("Lop con lai trong bo nho: " + rooms.size());
        System.out.println();

        System.out.println("########## KET LUAN DO TAI ##########");
        check("luong ghi cua client tra ve 0 sau khi ngat (khong ro ri luong)",
                afterWriter == 0);
        check("luong pool khong vuot tran " + Protocol.THREAD_POOL_SIZE,
                peakPool <= Protocol.THREAD_POOL_SIZE);
        check("so luong pool khong tang them sau khi ngat het",
                afterPool <= peakPool);
        check("tat ca lop da duoc don khoi bo nho", rooms.size() == 0);
        System.out.printf("%n==> %d DAT / %d TONG%n", passCount, passCount + failCount);

        server.stop();
        Thread.sleep(400);
        System.exit(failCount > 0 ? 1 : 0);
    }

    // ==================================================================
    // Đo luồng
    // ==================================================================

    /**
     * Đếm luồng theo tiền tố tên. Không truyền tiền tố nào = đếm tất cả (để tham chiếu).
     */
    private static int countThreads(String... prefixes) {
        int count = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (prefixes.length == 0) {
                count++;
                continue;
            }
            String name = t.getName();
            for (String prefix : prefixes) {
                if (name.startsWith(prefix)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    // ==================================================================
    // Tiện ích
    // ==================================================================

    private static void login(TestClient.Conn conn, String user, String role) throws Exception {
        String hash = Sha256.hex("123456");
        conn.send(PacketType.REGISTER_REQ, PacketUtils.buildPayload(o -> {
            o.writeUTF(user);
            o.writeUTF(hash);
            o.writeUTF(role);
        }));
        conn.await(PacketType.REGISTER_RES, 2000);

        conn.send(PacketType.LOGIN_REQ, PacketUtils.buildPayload(o -> {
            o.writeUTF(user);
            o.writeUTF(hash);
        }));
        conn.await(PacketType.LOGIN_RES, 2000);
    }

    private static void joinRoom(TestClient.Conn conn, int roomId) throws Exception {
        conn.send(PacketType.JOIN_ROOM_REQ, PacketUtils.buildPayload(o -> {
            o.writeInt(roomId);
            o.writeInt(0);
        }));
        conn.await(PacketType.JOIN_ROOM_RES, 2000);
    }

    private static int readRoomId(Packet res) {
        if (res == null) {
            return -1;
        }
        try {
            DataInputStream r = PacketUtils.payloadReader(res.getPayload());
            r.readByte();
            r.readUTF();
            return r.readInt();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passCount++;
        } else {
            failCount++;
        }
        System.out.println("   " + (ok ? "[DAT]  " : "[KHONG]") + " " + name);
    }
}
