package com.pbl4.server;

import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dữ liệu một lớp học đang mở, kèm <b>một khoá riêng cho lớp đó</b>.
 *
 * <h2>Luật vàng của lớp này — đọc trước khi sửa</h2>
 * <p>Mọi phương thức broadcast đều theo đúng 4 bước:</p>
 * <ol>
 *   <li>khoá lock,</li>
 *   <li><b>sao chép</b> danh sách thành viên ra một ArrayList tạm,</li>
 *   <li>mở khoá trong {@code finally} — <b>trước khi</b> chạm vào mạng,</li>
 *   <li>rồi mới duyệt mảng tạm để gửi.</li>
 * </ol>
 *
 * <p><b>Vì sao bước 3 phải xảy ra trước bước 4:</b> {@code write()} lên socket có thể bị chặn
 * rất lâu khi client có mạng chậm hoặc đệm gửi đầy. Nếu vẫn đang giữ khoá trong lúc đó, mọi luồng
 * khác cần khoá của lớp này sẽ đứng chờ — một học sinh mạng yếu làm <b>cả lớp</b> đứng,
 * dù CPU hoàn toàn rảnh. Cắt vùng găng xuống chỉ còn thao tác sao chép (vài micro giây) là cách sửa.</p>
 *
 * <p>Mỗi lớp có khoá riêng ⇒ khoá lớp A không chặn lớp B.</p>
 */
public class Room {

    private final int roomId;
    private final String roomName;
    private final String teacherUsername;

    /** Danh sách thành viên — CHỈ được truy cập khi đang giữ {@link #lock}. */
    private final List<ClientHandler> members = new ArrayList<>();

    /** Khoá riêng của lớp này. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Giáo viên của lớp này có đang chia sẻ màn hình không (bước 3.1). */
    private volatile boolean sharing;

    /** Số liệu chứng minh vùng găng chỉ kéo dài vài micro giây (dùng cho báo cáo). */
    private final AtomicLong lockAcquisitions = new AtomicLong();
    private final AtomicLong lockHoldNanos = new AtomicLong();

    public Room(int roomId, String roomName, String teacherUsername) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.teacherUsername = teacherUsername;
    }

    // ==================================================================
    // Thành viên
    // ==================================================================

    public void addMember(ClientHandler handler) {
        lock.lock();
        try {
            if (!members.contains(handler)) {
                members.add(handler);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean removeMember(ClientHandler handler) {
        lock.lock();
        try {
            return members.remove(handler);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sao chép danh sách thành viên ra một mảng tạm — <b>vùng găng duy nhất được phép có</b>.
     * Mọi thao tác gửi mạng phải chạy trên mảng trả về, không chạy trên {@code members}.
     */
    public List<ClientHandler> snapshotMembers() {
        lock.lock();
        long t0 = System.nanoTime();      // bắt đầu đo SAU khi đã có khoá:
        try {                             // như vậy số đo chỉ là thời gian GIỮ khoá thật,
            return new ArrayList<>(members);   // không tính thời gian chờ tới lượt.
        } finally {
            lock.unlock();
            long dt = System.nanoTime() - t0;
            lockAcquisitions.incrementAndGet();
            lockHoldNanos.addAndGet(dt);
        }
    }

    public int memberCount() {
        lock.lock();
        try {
            return members.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        return memberCount() == 0;
    }

    // ==================================================================
    // Broadcast — theo đúng 4 bước ở đầu lớp
    // ==================================================================

    /** Gửi một gói điều khiển cho tất cả thành viên. Trả về số người đã nhận. */
    public int broadcastControl(byte type, byte[] payload) {
        List<ClientHandler> targets = snapshotMembers();   // (1)(2)(3) khoá → sao chép → nhả khoá
        int sent = 0;
        for (ClientHandler c : targets) {                  // (4) giờ mới gửi
            if (c.sendControl(type, payload)) {
                sent++;
            }
        }
        return sent;
    }

    /**
     * Chuyển tiếp một frame màn hình cho tất cả thành viên.
     *
     * <p>Không ghi socket ở đây — chỉ đẩy vào hàng đợi có giới hạn của từng client
     * (mô hình producer–consumer, bước 3.3). Nhờ vậy một học sinh mạng chậm chỉ bị
     * <b>bỏ frame của chính mình</b>, không làm chậm những người còn lại.</p>
     */
    public int broadcastFrame(byte[] jpeg) {
        List<ClientHandler> targets = snapshotMembers();
        int sent = 0;
        for (ClientHandler c : targets) {
            c.offerFrame(jpeg);
            sent++;
        }
        return sent;
    }

    /**
     * Dựng payload {@code USER_LIST} (0x0C) từ danh sách thành viên hiện tại.
     * <p>Định dạng: {@code writeInt(n)}, rồi n lần {@code writeUTF(username)}, {@code writeUTF(role)}.</p>
     */
    public byte[] buildUserListPayload() {
        List<ClientHandler> targets = snapshotMembers();
        List<ClientHandler> loggedIn = new ArrayList<>();
        for (ClientHandler c : targets) {
            if (c.getUsername() != null) {
                loggedIn.add(c);
            }
        }
        return PacketUtils.buildPayload(out -> {
            out.writeInt(loggedIn.size());
            for (ClientHandler c : loggedIn) {
                out.writeUTF(c.getUsername());
                out.writeUTF(c.getRole() == null ? "" : c.getRole());
            }
        });
    }

    /** Gửi lại danh sách thành viên cho cả lớp — gọi sau mỗi lần có người vào hoặc ra (bước 2.2). */
    public int broadcastUserList() {
        return broadcastControl(PacketType.USER_LIST, buildUserListPayload());
    }

    // ==================================================================
    // Trạng thái chia sẻ màn hình
    // ==================================================================

    public boolean isSharing() {
        return sharing;
    }

    public void setSharing(boolean sharing) {
        this.sharing = sharing;
    }

    // ==================================================================
    // Getter + số liệu
    // ==================================================================

    public int getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getTeacherUsername() {
        return teacherUsername;
    }

    public long getLockAcquisitions() {
        return lockAcquisitions.get();
    }

    /** Thời gian giữ khoá trung bình, đơn vị micro giây — phải ở mức vài micro giây là đúng. */
    public double averageLockHoldMicros() {
        long n = lockAcquisitions.get();
        return n == 0 ? 0.0 : (lockHoldNanos.get() / 1000.0) / n;
    }

    @Override
    public String toString() {
        return "Room{" + roomId + ", '" + roomName + "', GV=" + teacherUsername
                + ", " + memberCount() + " nguoi, sharing=" + sharing + "}";
    }
}
