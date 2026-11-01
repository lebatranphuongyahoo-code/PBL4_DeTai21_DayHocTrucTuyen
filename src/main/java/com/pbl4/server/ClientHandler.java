package com.pbl4.server;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;
import com.pbl4.common.msg.Messages;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Xử lý MỘT kết nối TCP, chạy trên một luồng của thread pool.
 *
 * <h2>Hai quyết định thiết kế quan trọng nhất của lớp này</h2>
 *
 * <h3>1. Chỉ có MỘT luồng được ghi lên socket</h3>
 * <p>Ghi một gói là ba thao tác liên tiếp (4 byte độ dài + 1 byte type + payload). Nếu hai luồng
 * cùng gọi {@code write()} trên một socket, gói của người này sẽ chèn vào giữa gói của người kia
 * và bên nhận đọc ra rác. Cách sửa: mọi gói được <b>đóng gói xong</b> thành {@code byte[]} rồi
 * đẩy vào hàng đợi; chỉ luồng {@code srv-writer-N} được phép chạm vào socket.</p>
 *
 * <h3>2. Hai hàng đợi riêng: điều khiển và frame</h3>
 * <ul>
 *   <li>{@code controlQueue} — gói điều khiển, <b>không bao giờ bị bỏ</b>, luôn được ưu tiên.</li>
 *   <li>{@code frameQueue} — sức chứa {@value Protocol#FRAME_QUEUE_CAPACITY}, <b>bỏ frame cũ nhất</b>
 *       khi đầy (drop-oldest).</li>
 * </ul>
 * <p>Nhờ tách hai hàng đợi, một học sinh mạng chậm chỉ mất frame màn hình — không bao giờ mất
 * thông báo vào/ra lớp hay lời nhắn chat.</p>
 */
public class ClientHandler implements Runnable {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final int clientId = SEQ.incrementAndGet();
    private final Socket socket;
    private final ServerContext context;

    private DataInputStream in;
    private DataOutputStream out;

    /** Gói điều khiển — không bao giờ bỏ. */
    private final BlockingQueue<byte[]> controlQueue = new LinkedBlockingQueue<>();

    /** Frame màn hình — sức chứa 2, đầy thì bỏ frame cũ nhất. */
    private final ArrayBlockingQueue<byte[]> frameQueue =
            new ArrayBlockingQueue<>(Protocol.FRAME_QUEUE_CAPACITY);

    private Thread writerThread;
    private volatile boolean running = true;

    // ---------- Trạng thái phiên (chỉ luồng đọc ghi, writer chỉ đọc) ----------
    private volatile String username;
    private volatile String role;
    private volatile int roomId = -1;
    private volatile InetAddress udpAddress;
    private volatile int udpPort = -1;
    private volatile boolean micOn;

    // ---------- Số liệu cho báo cáo ----------
    private final AtomicLong packetsRead = new AtomicLong();
    private final AtomicLong framesSent = new AtomicLong();
    private final AtomicLong framesDropped = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();

    public ClientHandler(Socket socket, ServerContext context) {
        this.socket = socket;
        this.context = context;
    }

    // ==================================================================
    // Vòng đời
    // ==================================================================

    @Override
    public void run() {
        try {
            socket.setTcpNoDelay(true);   // tắt Nagle: gói điều khiển nhỏ cần đi ngay
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            startWriterThread();

            log("client da ket noi tu " + socket.getRemoteSocketAddress()
                    + " | luong xu ly=" + Thread.currentThread().getName());

            while (running) {
                Packet packet = PacketUtils.readPacket(in);   // ném EOFException khi client ngắt
                packetsRead.incrementAndGet();
                handle(packet);
            }

        } catch (EOFException e) {
            // Client đóng kết nối bình thường (đóng cửa sổ, thoát app).
            // Đây KHÔNG phải lỗi — chỉ log một dòng, không in stack trace.
            log("client ngat ket noi (" + label() + ")");
        } catch (SocketException e) {
            // Connection reset: client tắt đột ngột. Cũng là chuyện bình thường.
            log("client ngat ket noi dot ngot (" + label() + "): " + e.getMessage());
        } catch (IOException e) {
            log("loi I/O (" + label() + "): " + e.getMessage());
        } catch (RuntimeException e) {
            log("loi khong mong doi (" + label() + "): " + e);
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        running = false;
        context.room().leaveCurrentRoom(this);
        if (writerThread != null) {
            writerThread.interrupt();
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // đóng socket thất bại thì cũng không còn gì để làm
        }
        log("da dong ket noi | doc " + packetsRead.get() + " goi | gui "
                + framesSent.get() + " frame | bo " + framesDropped.get() + " frame");
    }

    // ==================================================================
    // Luồng ghi duy nhất
    // ==================================================================

    private void startWriterThread() {
        writerThread = new Thread(this::writerLoop, "srv-writer-" + clientId);
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void writerLoop() {
        try {
            while (running) {
                // Ưu tiên tuyệt đối cho gói điều khiển.
                byte[] pkt = controlQueue.poll();
                if (pkt == null) {
                    pkt = frameQueue.poll();
                }
                if (pkt == null) {
                    // Chờ ngắn trên hàng đợi điều khiển để không quay vòng CPU vô ích.
                    pkt = controlQueue.poll(30, TimeUnit.MILLISECONDS);
                    if (pkt == null) {
                        pkt = frameQueue.poll();
                    }
                }
                if (pkt == null) {
                    continue;
                }
                out.write(pkt);
                out.flush();
                bytesSent.addAndGet(pkt.length);
                if (pkt[4] == PacketType.SCREEN_FRAME) {
                    framesSent.incrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // Socket đã đóng hoặc client ngắt — luồng đọc sẽ tự phát hiện và dọn dẹp.
            running = false;
        }
    }

    // ==================================================================
    // API cho các lớp khác (Room, AudioRelayServer)
    // ==================================================================

    /** Đưa một gói điều khiển vào hàng đợi. Không bao giờ bị bỏ. */
    public boolean sendControl(byte type, byte[] payload) {
        if (!running) {
            return false;
        }
        return controlQueue.offer(PacketUtils.encode(type, payload));
    }

    /** Gửi gói lỗi về client. */
    public boolean sendError(String message) {
        return sendControl(PacketType.ERROR, new Messages.ErrorMessage(message).encode());
    }

    /**
     * Đưa một frame màn hình vào hàng đợi — <b>mô hình producer–consumer, drop-oldest</b>.
     *
     * <p>Vì sao bỏ frame CŨ chứ không bỏ frame MỚI: với video trực tiếp, frame mới phản ánh hiện tại;
     * giữ frame cũ là đang chiếu lại quá khứ và làm tăng độ trễ.</p>
     */
    public void offerFrame(byte[] jpeg) {
        if (!running) {
            return;
        }
        byte[] pkt = PacketUtils.encode(PacketType.SCREEN_FRAME, jpeg);
        if (!frameQueue.offer(pkt)) {
            frameQueue.poll();          // bỏ frame cũ nhất
            framesDropped.incrementAndGet();
            frameQueue.offer(pkt);      // đưa frame mới vào
        }
    }

    /** Audio relay dùng để trừ chính người gửi, tránh hiện tượng vọng. */
    public boolean isSameUdpSource(InetAddress address, int port) {
        return udpPort == port && udpAddress != null && udpAddress.equals(address);
    }

    public boolean hasUdpAddress() {
        return udpAddress != null && udpPort > 0;
    }

    public InetAddress getUdpAddress() {
        return udpAddress;
    }

    public int getUdpPort() { return udpPort; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public int getRoomId() { return roomId; }
    public boolean isMicOn() { return micOn; }
    public long getFramesSent() { return framesSent.get(); }
    public long getFramesDropped() { return framesDropped.get(); }
    public long getBytesSent() { return bytesSent.get(); }

    // ==================================================================
    // API cho handler
    // ==================================================================

    /** Ghi nhận đăng nhập thành công. */
    public void markLoggedIn(String user, String userRole) { this.username = user; this.role = userRole; }

    /** Ghi nhận đã vào lớp; địa chỉ UDP = địa chỉ IP của kết nối TCP + cổng client báo. */
    public void enterRoom(int newRoomId, int clientUdpPort) {
        this.roomId = newRoomId;
        this.udpAddress = socket.getInetAddress();
        this.udpPort = clientUdpPort;
    }

    /** Xoá trạng thái lớp, trả về mã lớp cũ (-1 nếu không ở lớp nào). */
    public int clearRoomState() {
        int previous = roomId;
        roomId = -1; udpPort = -1; udpAddress = null; micOn = false;
        return previous;
    }

    public void setMicOn(boolean on) { this.micOn = on; }

    public boolean isLoggedIn() { return username != null; }

    /** In log kèm tên luồng hiện tại — giữ đúng định dạng "[ten-luong] noi dung". */
    public void log(String message) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + message);
    }

    // ==================================================================
    // Định tuyến gói tin
    // ==================================================================

    private void handle(Packet packet) throws IOException {
        switch (packet.getType()) {

            case PacketType.LOGIN_REQ -> context.auth().handleLogin(this, packet);
            case PacketType.REGISTER_REQ -> context.auth().handleRegister(this, packet);

            case PacketType.CREATE_ROOM_REQ -> context.room().handleCreateRoom(this, packet);
            case PacketType.JOIN_ROOM_REQ -> context.room().handleJoinRoom(this, packet);
            case PacketType.LEAVE_ROOM_REQ -> context.room().handleLeaveRoom(this, packet);
            case PacketType.ROOM_LIST_REQ -> context.room().handleRoomList(this);
            case PacketType.CHAT_TEXT -> context.room().handleChat(this, packet);

            case PacketType.SCREEN_START -> context.screen().handleScreenStart(this);
            case PacketType.SCREEN_STOP -> context.screen().handleScreenStop(this);
            case PacketType.SCREEN_FRAME -> context.screen().handleScreenFrame(this, packet);

            case PacketType.AUDIO_ON -> context.audio().handleAudioOn(this);
            case PacketType.AUDIO_OFF -> context.audio().handleAudioOff(this);

            default -> {
                log("goi khong ho tro: " + packet.typeName());
                sendError("Loai goi khong duoc ho tro: " + packet.typeName());
            }
        }
    }

    // ==================================================================
    // Tiện ích
    // ==================================================================

    /** Nhãn ngắn để log dễ đọc. */
    private String label() {
        return (username == null ? "khach-" + clientId : username)
                + "@" + socket.getRemoteSocketAddress();
    }

    @Override
    public String toString() {
        return label();
    }
}
