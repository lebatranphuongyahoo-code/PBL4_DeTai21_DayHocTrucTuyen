package com.pbl4.server;

import com.pbl4.common.Protocol;
import com.pbl4.server.db.DatabaseManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Điểm khởi chạy của server — mở cổng TCP 9000 và phân phối việc cho thread pool.
 *
 * <h2>Vì sao KHÔNG xử lý client ngay trong vòng lặp accept()</h2>
 * <p>{@code accept()} chạy trên luồng chính. Nếu xử lý luôn client ở đó (đọc gói, chờ mạng,
 * ghi socket) thì trong suốt thời gian đó server <b>không thể nhận thêm kết nối nào</b> —
 * học sinh thứ hai phải chờ học sinh thứ nhất ngắt. Vòng lặp accept phải quay lại thật nhanh;
 * việc nặng đẩy cho pool.</p>
 *
 * <h2>Vì sao dùng thread pool thay vì {@code new Thread()} mỗi kết nối</h2>
 * <p>Mỗi luồng tốn khoảng 1 MB stack, cộng chi phí tạo/huỷ và chi phí chuyển ngữ cảnh.
 * Nếu mở luồng vô hạn theo số kết nối, server sẽ cạn bộ nhớ khi bị dội kết nối.
 * Pool giới hạn trần số luồng và tái sử dụng luồng cũ.</p>
 *
 * <h2>Ý nghĩa tham số backlog = {@value Protocol#SOCKET_BACKLOG}</h2>
 * <p>Đó là độ dài hàng đợi các kết nối <b>đã hoàn tất bắt tay TCP</b> nhưng chưa được
 * {@code accept()}. Vượt quá con số này thì kết nối mới bị hệ điều hành từ chối.</p>
 */
public class ServerMain {

    private final int port;
    private final RoomManager rooms;
    private final DatabaseManager db;
    private final ExecutorService threadPool;
    private final AudioRelayServer audioRelay;
    private final AtomicInteger connectedClients = new AtomicInteger();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread statsThread;

    public ServerMain(int port, RoomManager rooms, DatabaseManager db) throws IOException {
        this.port = port;
        this.rooms = rooms;
        this.db = db;
        // Không truyền ThreadFactory riêng: mặc định của JDK đã đặt tên đúng dạng
        // "pool-1-thread-1", "pool-1-thread-2"... — chính là dạng tên mà tài liệu
        // yêu cầu in ra để chứng minh là dùng thread pool chứ không phải luồng chính.
        this.threadPool = Executors.newFixedThreadPool(Protocol.THREAD_POOL_SIZE);
        this.audioRelay = new AudioRelayServer(rooms);
    }

    public static void main(String[] args) throws Exception {
        // Máy Việt Nam mặc định format số kiểu "0,97" (dấu phẩy). Log và số liệu trong báo cáo
        // phải dùng dấu chấm cho nhất quán và cho khớp tài liệu, nên chốt locale ngay từ đầu.
        java.util.Locale.setDefault(java.util.Locale.US);

        int port = (args.length > 0) ? Integer.parseInt(args[0]) : Protocol.TCP_PORT;

        DatabaseManager db = new DatabaseManager("pbl4.db");

        RoomManager rooms = new RoomManager();
        ServerMain server = new ServerMain(port, rooms, db);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));
        server.start();
    }

    /**
     * Chạy server — phương thức này <b>chặn</b> cho tới khi {@link #stop()} được gọi.
     */
    public void start() {
        running = true;
        try (ServerSocket ss = new ServerSocket(port, Protocol.SOCKET_BACKLOG)) {
            this.serverSocket = ss;

            audioRelay.start();
            startStatsThread();

            System.out.println("[main] ===== SERVER DANG CHAY =====");
            System.out.println("[main] TCP  : " + port + "  (dieu khien + man hinh)");
            System.out.println("[main] UDP  : " + Protocol.UDP_PORT + "  (chi thoai PCM)");
            System.out.println("[main] Pool : " + Protocol.THREAD_POOL_SIZE + " luong, backlog " + Protocol.SOCKET_BACKLOG);
            System.out.println("[main] Payload toi da: " + Protocol.MAX_PAYLOAD + " byte");
            System.out.println("[main] Cho ket noi...");

            while (running) {
                Socket socket = ss.accept();
                connectedClients.incrementAndGet();
                // Vòng lặp quay lại ngay — việc nặng đã giao cho pool.
                threadPool.execute(new ClientHandler(socket, rooms, db));
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("[main] Loi server socket: " + e.getMessage());
                e.printStackTrace();
            } else {
                System.out.println("[main] Server da dung binh thuong.");
            }
        } finally {
            shutdownPool();
            audioRelay.stop();
        }
    }

    /** Dừng server sạch sẽ: đóng ServerSocket để {@code accept()} ném lỗi và vòng lặp thoát. */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println("[main] Dang dung server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // không còn gì để làm
        }
        if (statsThread != null) {
            statsThread.interrupt();
        }
    }

    private void shutdownPool() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * In số liệu định kỳ mỗi 10 giây — đây là <b>bằng chứng</b> cho yêu cầu
     * "số luồng không tăng dần" ở cổng kiểm tra chặng 2, và là số liệu cho Chương 3.
     */
    private void startStatsThread() {
        statsThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!running) {
                    return;
                }
                System.out.printf(
                        "[stats] luong dang song=%d | client da ket noi=%d | lop mo=%d | nguoi online=%d"
                                + " | goi thoai da relay=%d%n",
                        Thread.activeCount(),
                        connectedClients.get(),
                        rooms.size(),
                        rooms.totalMembers(),
                        audioRelay.getPacketsRelayed());
                for (Room r : rooms.snapshotRooms()) {
                    System.out.printf("[stats]   lop %d '%s' | %d nguoi | sharing=%s"
                                    + " | giu khoa trung binh=%.2f us | %d lan%n",
                            r.getRoomId(), r.getRoomName(), r.memberCount(), r.isSharing(),
                            r.averageLockHoldMicros(), r.getLockAcquisitions());
                }
            }
        }, "stats");
        statsThread.setDaemon(true);
        statsThread.start();
    }

    // ---------- Getter cho test ----------

    public RoomManager getRooms() {
        return rooms;
    }

    public AudioRelayServer getAudioRelay() {
        return audioRelay;
    }

    public int getConnectedClients() {
        return connectedClients.get();
    }

    public boolean isRunning() {
        return running;
    }
}
