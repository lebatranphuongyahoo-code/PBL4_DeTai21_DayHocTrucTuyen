package com.pbl4.server;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;
import com.pbl4.common.RoomInfo;
import com.pbl4.server.db.DatabaseManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
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
    private final RoomManager rooms;
    private final DatabaseManager db;

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

    public ClientHandler(Socket socket, RoomManager rooms, DatabaseManager db) {
        this.socket = socket;
        this.rooms = rooms;
        this.db = db;
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
        leaveCurrentRoom();
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
        return sendControl(PacketType.ERROR,
                PacketUtils.buildPayload(o -> o.writeUTF(message)));
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

    public int getUdpPort() {
        return udpPort;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public int getRoomId() {
        return roomId;
    }

    public boolean isMicOn() {
        return micOn;
    }

    public long getFramesSent() {
        return framesSent.get();
    }

    public long getFramesDropped() {
        return framesDropped.get();
    }

    public long getBytesSent() {
        return bytesSent.get();
    }

    // ==================================================================
    // Định tuyến gói tin
    // ==================================================================

    private void handle(Packet packet) throws IOException {
        switch (packet.getType()) {

            case PacketType.LOGIN_REQ -> handleLogin(packet);
            case PacketType.REGISTER_REQ -> handleRegister(packet);

            case PacketType.CREATE_ROOM_REQ -> handleCreateRoom(packet);
            case PacketType.JOIN_ROOM_REQ -> handleJoinRoom(packet);
            case PacketType.LEAVE_ROOM_REQ -> handleLeaveRoom(packet);
            case PacketType.ROOM_LIST_REQ -> handleRoomList();
            case PacketType.CHAT_TEXT -> handleChat(packet);

            case PacketType.SCREEN_START -> handleScreenStart();
            case PacketType.SCREEN_STOP -> handleScreenStop();
            case PacketType.SCREEN_FRAME -> handleScreenFrame(packet);

            case PacketType.AUDIO_ON -> handleAudioOn();
            case PacketType.AUDIO_OFF -> handleAudioOff();

            default -> {
                log("goi khong ho tro: " + packet.typeName());
                sendError("Loai goi khong duoc ho tro: " + packet.typeName());
            }
        }
    }

    // ---------- Tài khoản (TV3 cài đặt DatabaseManager) ----------

    private void handleLogin(Packet packet) throws IOException {
        DataInputStream r = PacketUtils.payloadReader(packet.getPayload());
        String user = r.readUTF();
        String hash = r.readUTF();

        String foundRole = db.authenticate(user, hash);
        if (foundRole == null) {
            sendControl(PacketType.LOGIN_RES, PacketUtils.buildPayload(o -> {
                o.writeByte(0);
                o.writeUTF("Sai ten dang nhap hoac mat khau");
                o.writeUTF("");
            }));
            log("dang nhap that bai: " + user);
            return;
        }

        this.username = user;
        this.role = foundRole;
        sendControl(PacketType.LOGIN_RES, PacketUtils.buildPayload(o -> {
            o.writeByte(1);
            o.writeUTF("Dang nhap thanh cong");
            o.writeUTF(foundRole);
        }));
        log("dang nhap thanh cong: " + user + " (" + foundRole + ")");
    }

    private void handleRegister(Packet packet) throws IOException {
        DataInputStream r = PacketUtils.payloadReader(packet.getPayload());
        String user = r.readUTF();
        String hash = r.readUTF();
        String requestedRole = r.readUTF();

        // Không tin client: role phải là một trong hai giá trị hợp lệ.
        if (!Protocol.isValidRole(requestedRole)) {
            sendControl(PacketType.REGISTER_RES, PacketUtils.buildPayload(o -> {
                o.writeByte(0);
                o.writeUTF("Vai tro khong hop le");
            }));
            return;
        }

        boolean ok = db.register(user, hash, requestedRole);
        sendControl(PacketType.REGISTER_RES, PacketUtils.buildPayload(o -> {
            o.writeByte(ok ? 1 : 0);
            o.writeUTF(ok ? "Dang ky thanh cong" : "Ten dang nhap da ton tai");
        }));
        log("dang ky " + user + " (" + requestedRole + ") -> " + (ok ? "OK" : "trung ten"));
    }

    // ---------- Phòng học ----------

    private void handleCreateRoom(Packet packet) throws IOException {
        if (!requireLogin() || !requireTeacher()) {
            return;
        }
        DataInputStream r = PacketUtils.payloadReader(packet.getPayload());
        String roomName = r.readUTF().trim();
        if (roomName.isEmpty()) {
            sendControl(PacketType.CREATE_ROOM_RES, PacketUtils.buildPayload(o -> {
                o.writeByte(0);
                o.writeUTF("Ten lop khong duoc de trong");
                o.writeInt(-1);
            }));
            return;
        }

        // Cơ sở dữ liệu cấp mã phòng, rồi TV1 mở lớp đó trong RAM.
        int newRoomId = db.createRoom(roomName, username);
        rooms.createRoom(newRoomId, roomName, username);

        sendControl(PacketType.CREATE_ROOM_RES, PacketUtils.buildPayload(o -> {
            o.writeByte(1);
            o.writeUTF("Da tao lop " + roomName);
            o.writeInt(newRoomId);
        }));
        log("tao lop '" + roomName + "' ma " + newRoomId + " boi " + username);
    }

    private void handleJoinRoom(Packet packet) throws IOException {
        if (!requireLogin()) {
            return;
        }
        DataInputStream r = PacketUtils.payloadReader(packet.getPayload());
        int targetRoomId = r.readInt();
        int clientUdpPort = r.readInt();

        Room room = rooms.get(targetRoomId);
        if (room == null) {
            sendControl(PacketType.JOIN_ROOM_RES, PacketUtils.buildPayload(o -> {
                o.writeByte(0);
                o.writeUTF("Lop khong ton tai hoac da dong");
            }));
            return;
        }

        // Mỗi client chỉ ở trong MỘT phòng tại một thời điểm (mục 1.5.4).
        leaveCurrentRoom();

        this.roomId = targetRoomId;
        this.udpAddress = socket.getInetAddress();
        this.udpPort = clientUdpPort;
        room.addMember(this);

        sendControl(PacketType.JOIN_ROOM_RES, PacketUtils.buildPayload(o -> {
            o.writeByte(1);
            o.writeUTF("Da vao lop " + room.getRoomName());
        }));
        log("vao lop " + targetRoomId + " (" + username + "), cong UDP nhan thoai = " + clientUdpPort);

        // Gửi lại danh sách thành viên cho TẤT CẢ mọi người trong lớp (bước 2.2).
        int notified = room.broadcastUserList();
        log("phat USER_LIST cho " + notified + " nguoi trong lop " + targetRoomId);
    }

    private void handleLeaveRoom(Packet packet) throws IOException {
        DataInputStream r = PacketUtils.payloadReader(packet.getPayload());
        int targetRoomId = r.readInt();
        if (targetRoomId != roomId) {
            sendError("Ban khong o trong lop " + targetRoomId);
            return;
        }
        leaveCurrentRoom();
    }

    /** Rời lớp hiện tại (nếu có), phát lại USER_LIST, và dọn lớp rỗng khỏi bộ nhớ. */
    private void leaveCurrentRoom() {
        int current = roomId;
        if (current < 0) {
            return;
        }
        roomId = -1;
        udpPort = -1;
        udpAddress = null;
        micOn = false;

        Room room = rooms.get(current);
        if (room == null) {
            return;
        }
        if (room.removeMember(this)) {
            log("roi lop " + current + " (" + username + ")");
            room.broadcastUserList();
        }
        if (room.isEmpty()) {
            rooms.removeIfEmpty(current);
            log("lop " + current + " khong con ai -> da xoa khoi bo nho");
        }
    }

    private void handleRoomList() {
        List<RoomInfo> stored = db.listRooms();
        sendControl(PacketType.ROOM_LIST_RES, PacketUtils.buildPayload(o -> {
            o.writeInt(stored.size());
            for (RoomInfo info : stored) {
                o.writeInt(info.roomId());
                o.writeUTF(info.roomName());
                o.writeUTF(info.teacherUsername());
                // Số người online lấy từ trạng thái RAM, không phải từ DB.
                o.writeInt(rooms.memberCount(info.roomId()));
            }
        }));
    }

    private void handleChat(Packet packet) throws IOException {
        if (!requireLogin()) {
            return;
        }
        Room room = currentRoom();
        if (room == null) {
            sendError("Ban chua o trong lop nao");
            return;
        }
        // Chuyển tiếp nguyên payload cho cả lớp, kể cả người gửi, để giao diện của họ
        // hiển thị được tin nhắn của chính mình.
        room.broadcastControl(PacketType.CHAT_TEXT, packet.getPayload());
    }

    // ---------- Chia sẻ màn hình ----------

    /**
     * Bước 3.1 — "không bao giờ tin client".
     *
     * <p>Gói {@code SCREEN_START} chỉ chứa {@code roomId}, client KHÔNG gửi kèm vai trò.
     * Server tự kiểm tra vai trò của chính phiên đăng nhập này (đã lấy từ cơ sở dữ liệu lúc đăng nhập).
     * Nếu kiểm tra ở giao diện client thì chỉ cần sửa client, hoặc dùng công cụ khác gửi gói,
     * là qua được toàn bộ kiểm tra.</p>
     */
    private void handleScreenStart() {
        if (!requireLogin()) {
            return;
        }
        Room room = currentRoom();
        if (room == null) {
            sendError("Ban chua o trong lop nao");
            return;
        }
        if (!Protocol.ROLE_TEACHER.equals(role)) {
            log("TU CHOI chia se man hinh: " + username + " co vai tro " + role);
            sendError("Chi giao vien duoc chia se man hinh");
            return;   // KHÔNG bật cờ sharing
        }

        room.setSharing(true);
        int n = room.broadcastControl(PacketType.SCREEN_START, PacketUtils.buildPayload(o -> {
            o.writeInt(room.getRoomId());
            o.writeUTF(room.getTeacherUsername());
        }));
        log("bat dau chia se man hinh lop " + room.getRoomId() + " (thong bao " + n + " nguoi)");
    }

    private void handleScreenStop() {
        Room room = currentRoom();
        if (room == null) {
            return;
        }
        room.setSharing(false);
        room.broadcastControl(PacketType.SCREEN_STOP, PacketUtils.buildPayload(o -> {
            o.writeInt(room.getRoomId());
            o.writeUTF("Giao vien da dung chia se man hinh");
        }));
        log("dung chia se man hinh lop " + room.getRoomId());
    }

    /**
     * Bước 3.2 — chuyển tiếp frame.
     *
     * <p>Hai lớp bảo vệ: (1) phải đang ở trong lớp, (2) cờ chia sẻ của lớp phải đang bật.
     * Nhờ (2), một client tự ý gửi frame mà không xin phép sẽ bị bỏ qua hoàn toàn.</p>
     */
    private void handleScreenFrame(Packet packet) {
        Room room = currentRoom();
        if (room == null || !room.isSharing()) {
            return;   // bỏ qua, không relay
        }
        if (!Protocol.ROLE_TEACHER.equals(role)) {
            return;
        }
        room.broadcastFrame(packet.getPayload());
    }

    // ---------- Mic ----------

    private void handleAudioOn() {
        Room room = currentRoom();
        if (room == null) {
            sendError("Ban chua o trong lop nao");
            return;
        }
        if (!hasUdpAddress()) {
            sendError("Ban chua bao cong UDP khi vao lop");
            return;
        }
        micOn = true;
        log("bat mic: " + username + " (UDP " + udpAddress.getHostAddress() + ":" + udpPort + ")");
    }

    private void handleAudioOff() {
        micOn = false;
        log("tat mic: " + username);
    }

    // ==================================================================
    // Tiện ích
    // ==================================================================

    private boolean requireLogin() {
        if (username == null) {
            sendError("Ban chua dang nhap");
            return false;
        }
        return true;
    }

    private boolean requireTeacher() {
        if (!Protocol.ROLE_TEACHER.equals(role)) {
            sendError("Chi giao vien duoc tao lop");
            return false;
        }
        return true;
    }

    private Room currentRoom() {
        int current = roomId;
        return (current < 0) ? null : rooms.get(current);
    }

    /** Nhãn ngắn để log dễ đọc. */
    private String label() {
        return (username == null ? "khach-" + clientId : username)
                + "@" + socket.getRemoteSocketAddress();
    }

    private void log(String message) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + message);
    }

    @Override
    public String toString() {
        return label();
    }

    /** Cho phép các lớp khác (ví dụ lớp thống kê) lấy OutputStream nếu cần. */
    public OutputStream rawOutput() {
        return out;
    }
}
