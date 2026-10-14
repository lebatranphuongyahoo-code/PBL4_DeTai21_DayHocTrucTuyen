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

---

## TV2 · `client/media/AudioConfig` — hợp đồng tham số âm thanh (bước 4.1)

- **Cấu trúc.** Một lớp chỉ chứa hằng số, không có trạng thái: `SAMPLE_RATE = 16.000 Hz`,
  `SAMPLE_SIZE_BITS = 16`, `CHANNELS = 1`, `FRAME_MS = 20`, và ba hằng số **suy ra** từ chúng là
  `CHUNK_BYTES = 640`, `SAMPLES_PER_CHUNK = 320`, `PACKETS_PER_SECOND = 50`. Có thêm `FORMAT`
  kiểu `AudioFormat` để mở thẳng `SourceDataLine`/`TargetDataLine`.
- **Thiết lập.** Phép suy ra là `16000 × 2 byte × 0,02 giây = 640 byte`. Ba hằng số này **không
  được viết tay** mà tính từ ba hằng số gốc, vì viết tay thì sửa 16 kHz thành 44,1 kHz sẽ quên
  sửa 640 và lỗi chỉ hiện ra khi đã ghép với server.
- **Quy trình.** Cả `AudioRecorder` (phía gửi) lẫn `AudioPlayer` (phía nhận) đều đọc từ lớp này,
  nên đổi một chỗ là đổi cả hai phía. Có hàm `selfCheck()` đối chiếu lại toàn bộ hằng số lúc chạy.
- **Đánh đổi.** 16 kHz chỉ tái hiện được tới 8 kHz (định lý Nyquist–Shannon), tức là **mất** phần
  trên 8 kHz của giọng nói. Nhưng dải tiếng nói chỉ cần 300–3.400 Hz, và 16 kHz cho ra **256 kbps**
  thay vì 705 kbps của 44,1 kHz. Chọn 16 kHz là đổi độ trung thực không cần thiết lấy **băng thông
  giảm gần 3 lần** cho một lớp 30 người.

---

## TV2 · `client/media/JitterBuffer` — đệm làm phẳng dao động (bước 4.2)

- **Cấu trúc.** Một `ArrayBlockingQueue<byte[]>` sức chứa **10 gói**, một ngưỡng `prebuffer = 3`,
  cờ `prebuffered`, và hai biến đếm (`droppedCount`, `peakSize`). Đây là cầu nối
  **producer–consumer** giữa luồng nhận UDP và luồng phát ra loa.
- **Thiết lập.** Sức chứa 10 gói = 200 ms, prebuffer 3 gói = **60 ms độ trễ thêm**. Hàm `put()`
  **không bao giờ chặn** — đây là ràng buộc cứng, xem phần đánh đổi.
- **Quy trình.** Luồng nhận `put()` gói vào; luồng phát `take()` ra. Chưa đủ 3 gói thì `take()`
  trả `null` (chưa cho phát). Khi đầy: `poll()` bỏ **gói cũ nhất** rồi đưa gói mới vào.
- **Đánh đổi.** Sức chứa 1.000 gói sẽ "chịu dao động tốt" nhưng là **cái bẫy**: hàng đợi đầy dần
  khi mạng chậm và độ trễ tăng vô hạn — người nghe đang nghe chuyện của nửa phút trước. Với thoại
  trực tiếp, **gói mới quan trọng hơn gói cũ**, nên phải có trần và phải bỏ gói cũ. Đo thực tế ở
  Chặng 1: đầu vào dao động **0–30 ms**, đầu ra đều **21,9 ms**, và 100.000 lần `put()` khi đã đầy
  không hề treo (31 ms).

---

## TV2 · `client/media/AudioPlayer` — ba luồng nghe thoại (bước 4.3)

- **Cấu trúc.** Ba luồng daemon: `audio-receiver` (nhận UDP → tách `roomId`/`seqNo` → đẩy PCM vào
  jitter buffer), `audio-player` (lấy PCM → ghi ra `SourceDataLine`), `audio-stats` (in số liệu).
  Trạng thái: `maxSeqSeen`, `packetsReceived`, `packetsLost`, `packetsOutOfOrder` — tất cả đều
  là `AtomicLong`.
- **Thiết lập.** **Một `DatagramSocket` dùng chung với `AudioRecorder`** (xem phần đánh đổi).
  `SourceDataLine` mở với đệm 8 khối = 160 ms. Cổng UDP do hệ điều hành cấp, **không phải 9001**.
- **Quy trình.** Thứ tự bắt buộc khi bật mic: `player.start()` → gửi `AUDIO_ON` kèm cổng UDP →
  `recorder.start()`. Phía nhận, mỗi gói đến làm `onAudioReceived(seq)`; gói có `seq` nhảy cách
  được tính là mất, `seq` nhỏ hơn gói trước là sai thứ tự. Hàng đợi cạn thì ghi **im lặng** để giữ
  nhịp loa thay vì để loa ngắt quãng.
- **Đánh đổi.** **Vì sao phải dùng chung một socket:** server trừ người gửi bằng cách so **cặp
  (IP, cổng UDP nguồn)** đã đăng ký lúc `JOIN_ROOM_REQ`. Nếu recorder và player mở hai socket
  khác nhau, cổng mà server biết là của recorder, còn gói đến lại rơi vào socket của player —
  và **người nói sẽ nghe thấy tiếng của chính mình (vọng)**. Đây là lỗi dễ ghép nhất của TV2 và
  đã được kiểm chứng riêng ở Chặng 2. **Vì sao ba luồng mà không phải một:** gộp lại thì lúc ghi
  ra loa (tốn vài ms) sẽ không đọc được socket, đệm nhận của hệ điều hành đầy và **gói bị mất âm
  thầm** — không có cách nào phát hiện từ phía ứng dụng.

---

## TV2 · `client/media/ScreenCapturer` — chụp màn hình + nén JPEG (bước 5.1 & 5.2)

- **Cấu trúc.** Một `Robot` chụp vùng màn hình chính, một `ImageWriter` **dùng lại** cùng
  `ImageWriteParam`, một `ByteArrayOutputStream` dùng lại, và mẫu pixel `lastSamples` để dò thay
  đổi. Luồng chụp tên `screen-capturer`.
- **Thiết lập.** `10 FPS`, `quality 0.6`, bước lấy mẫu `4`, ngưỡng điểm ảnh đổi `24`,
  tỉ lệ đổi tối thiểu `1%`.
- **Quy trình.** Vòng lặp 5 bước: chụp → **dò thay đổi theo ngưỡng** (giống thì bỏ qua) → nén
  JPEG → đẩy cho lớp gửi → **bù thời gian** để giữ đúng nhịp. Nhịp được tính bằng
  `target = start + (i+1) × 100 ms` rồi chỉ ngủ **phần còn lại**, không dùng `sleep(100)` trần.
- **Đánh đổi.** Nén JPEG quality 0,6 cho tỉ lệ **63 lần** (5,31 MB → 82,9 KB) và chỉ tốn **28 ms**
  mỗi frame — chọn giữa thời gian nén và băng thông. Thí nghiệm 4 mức cho thấy rõ: quality 0,3
  chỉ **60,5 KB** (4,96 Mbps) còn 0,9 lên **153,6 KB** (12,59 Mbps) — tức gấp **2,5 lần** băng
  thông để đổi lấy độ nét mà **chữ trên slide đã đọc rõ từ mức 0,5**. Tối ưu bỏ frame trùng tiết
  kiệm **97,5%** lưu lượng khi màn hình đứng yên (39/40 frame bị bỏ) — đây là con số quan trọng
  nhất vì trong một tiết học phần lớn thời gian giáo viên đang nói, không thao tác gì.

---

## TV2 · Ba lỗi thật đã gặp và cách sửa (đọc kèm `docs/nhat-ky-loi.md`)

- **Cấu trúc.** Ba lỗi nằm ở ba tầng khác nhau: một ở **bài đo** (bỏ qua mã kết quả trả về), một ở
  **tranh chấp hai luồng** (`stop()` giải phóng writer khi luồng chụp còn đang dùng), một ở
  **thuật toán** (so hash tuyệt đối quá nhạy với nhiễu của bộ tổng hợp hình ảnh).
- **Thiết lập.** Cả ba đều **không** làm chương trình báo lỗi rõ ràng: lỗi 1 biểu hiện thành
  "relay 300 gói nhưng 0 người nhận" (trông y như lỗi mạng), lỗi 2 chỉ in một dòng `NullPointerException`
  rồi tự bỏ qua frame, lỗi 3 biểu hiện thành "tiết kiệm 0%" (trông y như tính năng không hoạt động).
- **Quy trình.** Sửa theo thứ tự: (1) cho `login()`/`joinRoom()` **trả về `true/false`** và kiểm
  tra bằng `check()` để lỗi hiện ngay tại chỗ; (2) `stop()` **không** giải phóng writer nữa, và
  `encodeJpeg()` tự phục hồi nếu writer bị `null`; (3) đổi sang **so theo ngưỡng chịu lỗi** —
  đếm tỉ lệ điểm ảnh đổi đáng kể thay vì so hash bằng nhau.
- **Đánh đổi.** Ba cách sửa này đều chọn **phòng thủ nhiều lớp** thay vì sửa một dòng cho gọn:
  giữ `check()` cho mọi mã trả về tốn thêm vài dòng test nhưng **không bao giờ che lỗi nghiệp vụ**;
  giữ nhánh tự phục hồi trong `encodeJpeg()` là code "không bao giờ chạy tới" nhưng đổi lại **lỗi
  ở lớp lắp ghép không thể làm chết việc chia sẻ màn hình**. Đo được bằng số: ngưỡng 1% nằm cao
  hơn nhiễu thật **14–800 lần** (nhiễu đo được chỉ 0,0012%–0,0723%), nên vẫn bắt được thay đổi
  thật mà không gửi frame thừa.
