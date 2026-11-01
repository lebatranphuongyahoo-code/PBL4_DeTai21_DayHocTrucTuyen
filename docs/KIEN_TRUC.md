# KIẾN TRÚC HỆ THỐNG — PBL4 Đề tài 21

> Nguồn cho **Chương 2** của báo cáo. Mọi khẳng định dưới đây viết từ **code thật sau refactor**
> và kèm đường dẫn file — khi báo cáo, kiểm tra lại bằng cách mở đúng file đó.

---

## 1. Sơ đồ gói

```
                ┌──────────────────────────────┐
                │        com.pbl4.common       │  ← HỢP ĐỒNG — cả 3 TV cùng dùng
                │  Protocol · PacketType ·     │
                │  Packet · PacketUtils ·      │
                │  RoomInfo · Sha256 ·         │
                │  msg/Messages (11 record)    │
                └───────┬──────────┬───────────┘
                        │          │
        ┌───────────────▼──┐    ┌──▼─────────────────────────┐
        │  com.pbl4.server │    │     com.pbl4.client        │
        │  (TV1)           │    │                            │
        │  ServerMain      │    │  net/ (TV3):               │
        │  ClientHandler   │    │    ServerConnection        │
        │  Room            │    │  ui/ (TV3):                │
        │  RoomManager     │    │    LoginUI · HomeUI ·      │
        │  AudioRelayServer│    │    ClassroomUI · ScreenPanel│
        │  ServerContext   │    │  media/ (TV2):             │
        │  ServerConfig    │    │    AudioSession ·          │
        │  db/ (TV3):      │    │    AudioRecorder ·         │
        │    DatabaseManager (interface) │ AudioPlayer ·   │
        │    SqliteDatabaseManager       │ JitterBuffer ·  │
        │  handler/ (TV1): │    │    ScreenCapturer ·        │
        │    AuthHandler   │    │    ScreenReceiver ·        │
        │    RoomHandler   │    │    AudioConfig · MediaStats│
        │    ScreenHandler │    └────────────────────────────┘
        │    AudioHandler  │
        └──────────────────┘
                com.pbl4.tools (TV1) — công cụ kiểm thử, KHÔNG nằm trong sản phẩm
```

- `ClientHandler` = **transport + phiên**, định tuyến 12 lệnh; logic nghiệp vụ nằm ở 4 handler
  trong `com.pbl4.server.handler` (`src/main/java/com/pbl4/server/ClientHandler.java`,
  `handler/AuthHandler.java`, `handler/RoomHandler.java`, `handler/ScreenHandler.java`,
  `handler/AudioHandler.java`).
- `ServerContext` là record gói 4 handler + `RoomManager` + `DatabaseManager`, truyền cho mỗi
  `ClientHandler` khi accept (`server/ServerContext.java`).

---

## 2. Hai kênh mạng

| Kênh | Cổng | Dùng cho | Vì sao tách |
|---|---|---|---|
| TCP | 9000 (`Protocol.TCP_PORT`) | Đăng nhập, lớp học, chat, **frame màn hình** | Frame ảnh lớn (trên 60 KB), cần **đúng và đủ** — mất byte giữa là hỏng cả JPEG. TCP tự bảo đảm thứ tự và toàn vẹn. |
| UDP | 9001 (`Protocol.UDP_PORT`) | **Chỉ** thoại PCM | Tiếng nói chấp nhận mất gói, không chấp nhận trễ. Nếu đưa thoại lên TCP thì một gói chậm kẹt sau frame ảnh làm **cả đoạn hội thoại** giật (head-of-line blocking) — xem javadoc `client/media/AudioRecorder.java`, `client/media/ScreenReceiver.java`. |

Client bind cổng UDP **ngẫu nhiên** để nhận thoại và báo cổng đó trong `JOIN_ROOM_REQ`
(`client/media/AudioSession.open()` → `localPort()`).

Gói UDP 648 byte = `[4B roomId][4B seqNo][640B PCM]`
(`Protocol.UDP_PACKET_BYTES`, `UDP_OFFSET_ROOM_ID/SEQ_NO/PCM`); 16 000 Hz / 16 bit / mono /
little-endian; 640 byte = 20 ms (`AudioConfig.CHUNK_BYTES`).

---

## 3. Bảng luồng

### Server

| Luồng | Tạo ở đâu | Làm gì | Chặn ở đâu |
|---|---|---|---|
| `main` | JVM | Vòng `accept()` TCP 9000, đẩy kết nối vào bể | `ServerSocket.accept()` |
| `pool-N-thread-M` | `ServerMain` — `Executors.newFixedThreadPool(50)` (`server/ServerMain.java`) | Đọc + xử lý gói của **một** client | `DataInputStream.readFully()` (đợi gói tới) |
| `srv-writer-N` | `ClientHandler.startWriterThread()` (`server/ClientHandler.java`) | **Luồng ghi duy nhất** của một client: lấy gói từ 2 hàng đợi và ghi socket | `LinkedBlockingQueue.take()` / `ArrayBlockingQueue` |
| `audio-relay` | `AudioRelayServer.start()` (`server/AudioRelayServer.java`) | Nhận gói UDP, kiểm người gửi là thành viên bật mic (B6), relay cho từng thành viên | `DatagramSocket.receive()` |
| `stats` | `ServerMain.startStatsThread()` | In số liệu mỗi 10 giây | `Thread.sleep()` |
| `shutdown-hook` | `ServerMain` ctor | Đóng server sạch khi Ctrl+C | — |

### Client

| Luồng | Tạo ở đâu | Làm gì | Chặn ở đâu |
|---|---|---|---|
| EDT (Swing) | JVM | Mọi thao tác giao diện | `invokeLater` đưa việc vẽ về đây |
| `server-connection-reader` | `ServerConnection` (`client/net/ServerConnection.java`) | Đọc gói TCP từ server, gọi listener | `readFully()` |
| `audio-receiver` | `AudioPlayer.start()` (`client/media/AudioPlayer.java`) | Nhận gói UDP 648 byte, tách header, `put` vào jitter buffer | `DatagramSocket.receive()` |
| `audio-player` | `AudioPlayer.start()` | Lấy 640 byte từ jitter buffer mỗi 20 ms, ghi loa | `SourceDataLine.write()` |
| `audio-stats` | `AudioPlayer.start()` | In thống kê mất gói/sai thứ tự mỗi giây | `Thread.sleep()` |
| `audio-recorder` | `AudioRecorder.start()` (`client/media/AudioRecorder.java`) | Đọc mic 20 ms/lần, gửi 648 byte tới server | `TargetDataLine.read()` |
| `screen-capturer` | `ScreenCapturer.start()` (`client/media/ScreenCapturer.java`) | `Robot` chụp 10 FPS, nén JPEG, gửi TCP | `Thread.sleep()` theo FPS |

---

## 4. Bảng cơ chế đồng bộ

| Cơ chế | Ở đâu | Giải quyết vấn đề gì | Nếu bỏ thì sao |
|---|---|---|---|
| `ReentrantLock` **mỗi `Room`** + cờ `closed` (B2) | `server/Room.java` | Thành viên thêm/bớt, bật/tắt chia sẻ, đóng lớp rỗng là nhiều bước — phải atomic | Hai luồng vào/rời đồng thời tạo "phòng ma": thành viên ở trong Room đã bị xoá khỏi map |
| Quy tắc **sao chép → nhả khoá → gửi** | `Room.broadcast*`, `snapshotMembers()` | Gửi socket có thể chặn lâu; không giữ khoá khi I/O | Một client mạng chậm giữ khoá ⇒ **cả lớp** đứng dù CPU rảnh |
| `ConcurrentHashMap` | `server/RoomManager.java` | Nhiều luồng cùng tra cứu/tạo lớp | `HashMap` bị sửa đồng thời hỏng cấu trúc, có thể treo vòng lặp vô hạn |
| `LinkedBlockingQueue` (control) + `ArrayBlockingQueue(2)` (frame, drop-oldest) | `server/ClientHandler.java` | Tách gói quan trọng (không bao giờ bỏ) khỏi frame (bỏ được) | Client chậm tích frame cũ ⇒ độ trễ tăng hàng giây; hoặc hai luồng cùng ghi socket ⇒ gói chèn nhau |
| `ArrayBlockingQueue` + prebuffer (producer–consumer) | `client/media/JitterBuffer.java` | Phát đều 20 ms/lần dù mạng đến không đều; chưa đủ prebuffer thì không phát | Rè ngay từ giây đầu; đệm nhận HĐH đầy ⇒ mất gói âm thầm |
| `synchronized` | `SqliteDatabaseManager`, `ServerConnection.send`, `AudioSession` | SQLite không thích nhiều luồng cùng ghi; gửi gói là 3 lần `write` liên tiếp | Gói chèn nhau, bên nhận đọc rác; CSDL lỗi SQLITE_BUSY |
| `AtomicLong` (bộ đếm) | `AudioRelayServer`, `ScreenReceiver.decodeFailures`, `MediaStats` | Đếm từ nhiều luồng, không cần khoá | `++` trên `volatile` là đọc-ghi hai bước ⇒ mất một số lần đếm |
| `volatile` (cờ chạy) | `AudioRelayServer.running`, `ScreenCapturer.running`… | Luồng khác nhìn thấy giá trị mới | Luồng con không biết lệnh dừng ⇒ rò rỉ luồng |

---

## 5. Luồng nghiệp vụ (sequence)

```
Đăng nhập
  C→S LOGIN_REQ(user, sha256) ──▶ server: db.authenticate ──▶ LOGIN_RES(ok, role)

Tạo lớp → vào lớp (lớp mở từ CSDL — B1)
  GV: CREATE_ROOM_REQ(name) ──▶ db.createRoom ──▶ CREATE_ROOM_RES(roomId)   [RAM KHÔNG mở]
  HS: JOIN_ROOM_REQ(roomId, udpPort) ──▶ db.findRoom ──▶ leaveCurrentRoom
        ──▶ enterRoom ──▶ rooms.openIfAbsent(info) + tryAddMember (thử lại 50 lần — B2)
        ──▶ JOIN_ROOM_RES(ok) + USER_LIST cho cả lớp

Chia sẻ màn hình (không tin client — B3)
  GV: SCREEN_START(roomId) ──▶ server kiểm tra role + isOwner ──▶ broadcast SCREEN_START
  GV: SCREEN_FRAME(jpeg) ──▶ relay nguyên payload (chỉ khi isOwner + sharing)
  HS: chỉ nhận, vẽ lên ScreenPanel

Bật mic (báo cổng trước, bật mic sau)
  HS: JOIN_ROOM_REQ đã kèm udpPort ──▶ AUDIO_ON(roomId) ──▶ server đặt micOn=true
  HS: AudioRecorder gửi UDP 648 byte ──▶ audio-relay: kiểm thành viên + micOn (B6)
        ──▶ phát cho từng thành viên, TRỪ người gửi (chống vọng)

Rời lớp / mất kết nối
  CLIENT: LEAVE_ROOM_REQ ──▶ hoặc socket EOF ──▶ cleanup
  server: removeMember ──▶ nếu chủ lớp đang chia sẻ: setSharing(false) + SCREEN_STOP (B4)
        ──▶ USER_LIST ──▶ lớp rỗng: closeIfEmpty + gỡ khỏi map
```

---

## 6. Quy tắc bảo mật phía server — "không bao giờ tin client"

1. **B3 — quyền chia sẻ**: `SCREEN_START/STOP/FRAME` chỉ chấp nhận khi `ROLE_TEACHER` **và**
   `Room.isOwner(username)` — vai trò lấy từ phiên đăng nhập phía server, không phải trường
   client gửi (`handler/ScreenHandler.java`).
2. **B6 — relay thoại**: mỗi gói UDP phải khớp cặp (IP, cổng) của một thành viên **và** thành
   viên đó đang bật mic (`server/AudioRelayServer.handle`); gói lạ bị đếm vào
   `packetsRejected`.
3. **B7 — kiểm tra đầu vào**: tên đăng nhập phải khớp `[A-Za-z0-9_.]{2,32}`, hash phải
   `[0-9a-f]{64}`, tên lớp 1–100 ký tự; `LEAVE_ROOM`, `AUDIO_ON/OFF`, `SCREEN_STOP` đều yêu cầu
   đăng nhập (`handler/AuthHandler.java`, `handler/RoomHandler.java`).
4. **B5 — chat**: server `strip()`, giới hạn 1000 ký tự, kiểm `roomId`, và **gắn tên người gửi**
   phía server — client không giả mạo được.

---

## 7. Bảng đối chiếu yêu cầu đề 21

| Yêu cầu đề 21 | Trạng thái | Bằng chứng |
|---|---|---|
| Đăng ký / đăng nhập | ✅ | `db/Auth*`, `client/ui/LoginUI`, test `MessagesTest`, `ServerRulesTest` |
| Giáo viên tạo lớp học | ✅ | `handler/RoomHandler.handleCreateRoom`, mã 6 số từ CSDL |
| Học sinh vào lớp bằng mã | ✅ | `handleJoinRoom`, mở lớp từ CSDL (B1), chống phòng ma (B2) |
| Chia sẻ màn hình | ✅ | `ScreenCapturer` (TV2) + relay TCP `SCREEN_FRAME` (TV1) |
| Thoại hai chiều | ✅ | `AudioRecorder`/`AudioPlayer` + relay UDP, không vọng, chống gói lạ (B6) |
| **Đa luồng** | ✅ | Bể 50 luồng, mỗi client 2 luồng, relay riêng — bảng luồng mục 3 |
| **Đồng bộ khi gửi/nhận** | ✅ | Khoá mỗi Room + luật sao chép→nhả khoá→gửi; 2 hàng đợi; mục 4 |
| Thêm người học vào lớp | ⏸ Bước 10 (tuỳ chọn — chờ duyệt) | Kế hoạch 10.1–10.5 |

---

## 8. Bốn quyết định thiết kế quan trọng (dùng để trả lời hội đồng)

### 8.1 Một luồng ghi duy nhất cho mỗi socket

Ghi một gói là **ba thao tác liên tiếp** (4 byte + 1 byte + payload). Nếu hai luồng cùng gọi
`write()` trên một socket, gói của người này sẽ chèn vào giữa gói của người kia và bên nhận
đọc ra rác.

**Cách làm:** mọi gói được **đóng gói xong** thành `byte[]` (`PacketUtils.encode`) rồi đẩy vào
hàng đợi. Chỉ luồng `srv-writer-N` được chạm vào socket.

### 8.2 Hai hàng đợi riêng: điều khiển và frame

| Hàng đợi | Sức chứa | Khi đầy |
|---|---|---|
| `controlQueue` (đăng nhập, vào lớp, chat, lỗi…) | không giới hạn | **không bao giờ bỏ** — luôn được ưu tiên |
| `frameQueue` (frame màn hình) | **2** | **bỏ frame CŨ NHẤT** (drop-oldest) |

Vì sao sức chứa 2 mà không phải 100: sức chứa 100 sẽ tích tụ frame cũ và đẩy độ trễ lên hàng giây.
Với video trực tiếp, frame mới phản ánh hiện tại — giữ frame cũ là đang chiếu lại quá khứ.

Vì sao tách hai hàng đợi: một học sinh mạng chậm chỉ được phép **mất frame màn hình**,
không bao giờ được mất thông báo vào/ra lớp hay lời nhắn chat.

### 8.3 Luật vàng: sao chép trong khoá — nhả khoá rồi mới gửi

```java
List<ClientHandler> targets;
lock.lock();
try {
    targets = new ArrayList<>(members);   // CHỈ sao chép, không I/O ở đây
} finally {
    lock.unlock();                        // nhả khoá TRƯỚC khi chạm vào mạng
}
for (ClientHandler c : targets) {         // giờ mới gửi
    c.offerFrame(jpeg);
}
```

`write()` lên socket có thể bị chặn rất lâu khi client mạng chậm hoặc đệm gửi đầy. Nếu vẫn giữ
khoá trong lúc đó, mọi luồng khác cần khoá của lớp này sẽ đứng chờ — **một học sinh mạng yếu làm
cả lớp đứng, dù CPU hoàn toàn rảnh**.

Số đo thực tế từ `T0Demo`: vùng găng chỉ kéo dài **~15 micro giây**.

Mỗi lớp có khoá riêng ⇒ khoá lớp A không chặn lớp B.

### 8.4 Mỗi lớp có khoá riêng, không dùng khoá toàn cục

Nếu dùng một khoá chung cho cả server thì mọi lớp phải xếp hàng chờ nhau — mất hết lợi ích của
đa luồng. `ConcurrentHashMap` cho `RoomManager` cũng vì lý do này: tra cứu lớp không trở thành
nút thắt.

---

## 9. Hạn chế còn lại (thật, không thêm cho có)

- Mật khẩu băm **SHA-256 không salt** — chịu được tấn công rainbow table.
- Chưa **mã hoá đường truyền** — ai bắt gói thấy rõ nội dung (tuy nhiên hash mật khẩu không
  gửi dưới dạng gốc, và gói UDP thoại không chứa thông tin đăng nhập).
- Không có **token phiên** — "đăng nhập" là trạng thái trên kết nối TCP; kết nối mới phải
  đăng nhập lại.
- Nhiều người cùng nói: jitter buffer **nối tiếp** gói của nhiều người (không trộn âm),
  và thống kê mất gói theo `seqNo` **sai** khi có > 1 người nói (mỗi người một dòng seqNo).
- Client luôn gửi thoại tới `Protocol.UDP_PORT` (9001) — nếu server đổi cổng UDP thì client
  không biết (cổng relay chỉ dùng để **nhận**).
