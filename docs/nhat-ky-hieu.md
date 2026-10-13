# Nhật ký hiểu — Luật N1

> Mỗi thành phần làm xong thì ghi **4 dòng**: **Cấu trúc · Thiết lập · Quy trình · Đánh đổi**.
> Không cần hiểu từng dòng code — AI viết code là hoàn toàn được phép. Cái cần nắm là **hệ thống**:
> vẽ lại được sơ đồ, kể lại được luồng, chỉ ra được chỗ dễ hỏng, nêu được một đánh đổi.

---

## TV1 · `common/PacketUtils` — khung gói tin (bước 1.2)

- **Cấu trúc.** Một gói trên đường truyền gồm 3 phần liền nhau: 4 byte độ dài, 1 byte loại,
  N byte nội dung. Trường độ dài ghi **1 + N** vì nó đếm **cả** byte loại.
- **Thiết lập.** Cổng TCP 9000, `MAX_PAYLOAD = 5.000.000`. Ghi bằng `DataOutputStream`,
  đọc bằng `DataInputStream.readFully()`.
- **Quy trình.** Gửi: `encode()` đóng gói thành `byte[]` → ghi → `flush()`. Nhận: `readInt()`
  lấy độ dài → kiểm tra biên → `readByte()` lấy loại → `readFully()` đọc đủ N byte.
- **Đánh đổi.** Tốn thêm 5 byte cho mỗi gói (0,008% với frame 62 KB — không đáng kể), nhưng đổi
  lại là **biết chắc gói kết thúc ở đâu**. Không có nó thì TCP sẽ "dính gói" và mọi thứ hỏng từ đó.

---

## TV1 · `server/ServerMain` — vòng lặp accept + thread pool (bước 1.3)

- **Cấu trúc.** Một `ServerSocket` cổng 9000, một `ExecutorService` 50 luồng, một vòng `while`
  gọi `accept()`. Kèm một luồng `stats` in số liệu mỗi 10 giây.
- **Thiết lập.** `backlog = 50` (hàng đợi kết nối đã bắt tay TCP xong nhưng chưa `accept()`),
  pool cố định 50 luồng, cờ `volatile boolean running` để tắt sạch sẽ.
- **Quy trình.** `accept()` nhận socket → `threadPool.execute(new ClientHandler(socket))` →
  **quay lại accept ngay**, không chờ client xử lý xong.
- **Đánh đổi.** 50 luồng đủ cho lớp 30–40 người, nhưng nếu lớp đông hơn thì người thứ 51 phải
  chờ trong hàng đợi của pool. Chọn 50 là đổi tài nguyên lấy khả năng phục vụ đồng thời.
  **Vì sao không xử lý client ngay trong luồng accept:** luồng chính bận đọc/ghi socket thì
  không nhận thêm được kết nối nào — học sinh thứ hai phải chờ học sinh thứ nhất ngắt.

---

## TV1 · `server/ClientHandler` — định tuyến gói + luồng ghi duy nhất (bước 1.4)

- **Cấu trúc.** Mỗi kết nối có: 1 luồng đọc (chính là luồng pool), 1 luồng ghi riêng
  (`srv-writer-N`), **hai hàng đợi** (`controlQueue` không giới hạn, `frameQueue` sức chứa 2),
  và trạng thái phiên (username, role, roomId, udpPort).
- **Thiết lập.** `setTcpNoDelay(true)` để gói điều khiển nhỏ không bị thuật toán Nagle gom lại.
  `MAX_PAYLOAD` kiểm tra trước khi cấp phát mảng.
- **Quy trình.** `readPacket()` → `switch(type)` → 18 nhánh xử lý → kết quả đóng gói thành
  `byte[]` → đẩy vào hàng đợi → luồng ghi lấy ra và ghi socket.
- **Đánh đổi.** Tốn **2 luồng cho mỗi kết nối** (đọc + ghi) thay vì 1, nhưng đổi lại là
  **không bao giờ trộn gói** và **một client chậm không chặn được luồng xử lý của người khác**.
  Đây là lý do phải có hai hàng đợi tách biệt: frame được phép bỏ, thông báo vào/ra lớp thì không.

---

## TV1 · `server/Room` — khoá riêng cho từng lớp (bước 2.1)

- **Cấu trúc.** `roomId`, `roomName`, `teacherUsername`, danh sách thành viên,
  **một `ReentrantLock` riêng**, cờ `sharing`, và hai biến đếm số lần/thời gian giữ khoá.
- **Thiết lập.** Mọi truy cập vào danh sách thành viên phải đi qua khoá. Broadcast luôn theo
  đúng 4 bước: khoá → **sao chép** → nhả khoá (trong `finally`) → rồi mới gửi.
- **Quy trình.** `broadcastFrame()` gọi `snapshotMembers()` lấy mảng tạm rồi duyệt mảng tạm để
  đẩy frame vào hàng đợi của từng client.
- **Đánh đổi.** Mỗi lần broadcast tốn một lần sao chép danh sách (vài micro giây), nhưng đổi lại
  là **vùng găng cực ngắn**. Đo thực tế từ `T0Demo`: giữ khoá trung bình **~15 micro giây**.
  Nếu giữ khoá trong lúc gửi socket, một học sinh mạng chậm sẽ làm **cả lớp đứng dù CPU rảnh**.
  Mỗi lớp khoá riêng ⇒ khoá lớp A không chặn lớp B.

---

## TV1 · `server/RoomManager` — sổ quản lý lớp (bước 2.1)

- **Cấu trúc.** Một `ConcurrentHashMap<Integer, Room>`.
- **Thiết lập.** `roomId` do **cơ sở dữ liệu cấp** (hợp đồng: `DatabaseManager.createRoom()`
  trả về `int`), không phải `RoomManager` tự sinh — nếu tự sinh thì hai không gian mã sẽ lệch
  nhau sau khi server khởi động lại.
- **Quy trình.** `createRoom()` dùng `putIfAbsent`; `removeIfEmpty()` dùng `remove(key, value)`
  để không xoá nhầm lớp vừa được tạo lại cùng mã.
- **Đánh đổi.** `ConcurrentHashMap` tốn bộ nhớ hơn `HashMap` một chút, nhưng đổi lại là
  **tra cứu lớp không trở thành nút thắt** cho cả server và không cần khoá toàn cục.
  Bắt buộc phải dọn lớp rỗng — nếu không, mở 100 lớp lúc test là RAM phình lên mãi
  (đây chính là "rò rỉ bộ nhớ" mà hội đồng rất thích hỏi).

---

## TV1 · `server/AudioRelayServer` — trạm chuyển tiếp thoại (bước 2.3)

- **Cấu trúc.** Một `DatagramSocket` cổng 9001 trên một luồng riêng, đệm nhận 1024 byte,
  vòng `receive()`, cùng các bộ đếm gói (nhận, đã relay, byte, gói rác, lớp không tồn tại).
- **Thiết lập.** Gói đến phải đúng 648 byte; đọc 4 byte đầu thành `roomId`. **Không đọc**
  `seqNo` — trường đó để máy nhận đo, server chỉ chuyển tiếp nguyên gói.
- **Quy trình.** `receive()` → kiểm tra độ dài → đọc `roomId` → tra lớp → **sao chép danh sách
  thành viên rồi nhả khoá** → gửi nguyên gói cho từng người **trừ người gửi** → mỗi 100 gói in
  thống kê.
- **Đánh đổi.** UDP **không đảm bảo** gói đến — nhưng đây chính là điều mình muốn: mất 1 gói chỉ
  mất 20 ms tiếng, trong khi TCP truyền lại sẽ làm các gói sau bị chặn chờ và độ trễ tích luỹ.
  **Bắt buộc phải trừ người gửi**, nếu không họ sẽ nghe thấy tiếng của chính mình (vọng).

---

## TV1 · Chống nghẽn — hàng đợi có giới hạn + drop-oldest (bước 3.3)

- **Cấu trúc.** Mỗi `ClientHandler` có `ArrayBlockingQueue<byte[]>` **sức chứa 2** cho frame,
  cộng một luồng ghi riêng chỉ làm nhiệm vụ lấy frame ra và ghi socket.
- **Thiết lập.** Sức chứa 2 (không phải 100), chiến lược **bỏ frame cũ nhất**, biến đếm
  `droppedFrames` để chứng minh bằng số.
- **Quy trình.** `offerFrame()`: hàng đợi đầy → `poll()` bỏ frame cũ → tăng `droppedFrames` →
  đưa frame mới vào. Luồng ghi lấy frame ra và ghi socket.
- **Đánh đổi.** Sức chứa 100 sẽ giữ được nhiều frame hơn nhưng **đẩy độ trễ lên hàng giây** —
  với video trực tiếp, frame mới phản ánh hiện tại nên **bỏ frame cũ** đúng hơn là bỏ frame mới.
  Bằng chứng đo được: client mạng chậm bị bỏ **22 frame**, hai client bình thường bị bỏ **0 frame**.

---

## TV1 · Không bao giờ tin client (bước 3.1)

- **Cấu trúc.** Gói `SCREEN_START` chỉ chứa `roomId` — client **không gửi kèm vai trò**.
  Vai trò nằm ở phía server, trong `ClientHandler.role`, lấy từ cơ sở dữ liệu lúc đăng nhập.
- **Thiết lập.** Ba lớp kiểm tra ở server: đã đăng nhập chưa → có đang ở trong lớp không →
  vai trò có phải `TEACHER` không. Cộng thêm lớp thứ tư ở `SCREEN_FRAME`: cờ `sharing` của lớp
  phải đang bật, nếu không thì bỏ qua.
- **Quy trình.** Kiểm tra thất bại → gửi `ERROR` và **không bật cờ** `sharing`. Nhờ vậy một client
  tự ý gửi frame mà không xin phép sẽ bị bỏ qua hoàn toàn.
- **Đánh đổi.** Nếu kiểm tra ở giao diện client thì code đơn giản hơn, nhưng **chỉ cần sửa client
  hoặc dùng công cụ khác gửi gói là qua được toàn bộ kiểm tra**. Đây là đánh đổi bắt buộc phải
  chọn phía server — kiểm tra phải nằm ở nơi duy nhất đáng tin, tức là nơi giữ dữ liệu.
