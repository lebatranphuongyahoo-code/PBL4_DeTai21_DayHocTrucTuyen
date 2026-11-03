# PBL4 · Đề tài 21 — Dạy học trực tuyến

## Bản web trên nhánh web-rebuild

Người dùng đã chọn kiến trúc React/TypeScript + Java Spring Boot + SQLite riêng.
Xem [hướng dẫn chạy, nghiệp vụ và nghiệm thu media](docs/WEB_MEDIA_DEMO.md).
Build bằng scripts/build-web.ps1; JAR Java chứa UI React và API/WebSocket.
Phần tài liệu Swing dưới đây mô tả hệ thống cũ, không chứng minh bản web hoàn tất.

**Nhóm 4 · Hệ điều hành & Mạng máy tính · Đại học Bách khoa Đà Nẵng**

Phần việc trong repo này chia làm 3 khối, mỗi người sở hữu một khối:

| Thư mục | Người sở hữu | Nội dung |
|---|---|---|
| `com/pbl4/common/` | **TV1 (Minh)** | Hợp đồng giao thức — cả 3 cùng dùng |
| `com/pbl4/common/msg/` | **TV1 (Minh)** | Record + encode/decode cho mọi payload (`Messages`) |
| `com/pbl4/server/` | **TV1 (Minh)** | Server đa luồng, phòng học, relay thoại + màn hình |
| `com/pbl4/server/handler/` | **TV1 (Minh)** | 4 handler nghiệp vụ: Auth, Room, Screen, Audio |
| `com/pbl4/server/db/` | **TV3** | SQLite: tài khoản, lớp học (`DatabaseManager` + bản cài SQLite) |
| `com/pbl4/client/net/`, `client/ui/` | **TV3** | Kết nối mạng phía client, giao diện Swing |
| `com/pbl4/client/media/` | **TV2** | Mic, loa, chụp màn hình, jitter buffer |
| `com/pbl4/tools/` | **TV1** | Công cụ kiểm thử — **không nằm trong sản phẩm** |
| `src/test/` | **TV1** | 59 test JUnit: hợp đồng, luật server, phòng học, jitter buffer |

> **Quy tắc sở hữu file:** muốn sửa file của người khác thì nhắn trong nhóm trước, và ghi rõ
> trong Summary commit (`[TV3 sua file cua TV1]`). Không tự sửa rồi im lặng.

---

## 1. Yêu cầu môi trường

- **JDK 21** (LTS). Kiểm tra: `javac -version` → phải ra `javac 21.x`.
- **Maven 3.9+**. Kiểm tra: `mvn -version`.
- ⚠️ Nếu `mvn` báo
  `Could not find or load main class org.codehaus.plexus.classworlds.launcher.Launcher`
  thì **không phải do thiếu `JAVA_HOME`** (mvn vẫn tự tìm được `java` trên PATH).
  Nguyên nhân thật: trên Git Bash, script `mvn` truyền đường dẫn kiểu `/c/Users/...` cho JVM
  Windows; nếu việc đổi đường dẫn bị chặn — tức `MSYS_NO_PATHCONV=1` hoặc
  `MSYS2_ARG_CONV_EXCL=*` đang được đặt — JVM sẽ tìm `C:\c\Users\...` và không thấy file.
  Cách sửa: bỏ hai biến đó cho riêng lệnh, hoặc dùng `mvn.cmd`.
  ```bash
  env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL mvn -o clean package
  ```
- ⚠️ Trên Git Bash nếu vẫn lỗi, gõ `mvn.cmd` thay cho `mvn`.

---

## 2. Chạy thử trong 3 phút

```bash
# 1. Biên dịch
mvn -q compile

# 2. Chạy TOÀN BỘ kiểm thử JUnit (hợp đồng, luật server, phòng học, jitter buffer)
mvn -o -q verify
#    Ky vong:  Tests run: 59, Failures: 0

# 3. Bài kiểm thử T0 đầy đủ — tự dựng server + 1 giáo viên + 3 học sinh
mvn -q exec:java -Dexec.mainClass=com.pbl4.tools.T0Demo
#    Ky vong:  KET QUA: 20 DAT / 20 TONG

# 4. Đo tải mức T3 — số liệu cho Chương 3
mvn -q exec:java -Dexec.mainClass=com.pbl4.tools.LoadTest

# 5. Chạy server thật (chờ client kết nối)
mvn exec:java
```

### Chạy server và client bằng jar (cách dùng khi demo)

`mvn package` giờ tạo **jar đủ thư viện** (đã gộp `sqlite-jdbc` bằng shade plugin, ~12 MB),
nên chạy được trên máy không cài Maven. Bản `original-*.jar` là jar mỏng chỉ có code của mình —
**không dùng bản đó**, server sẽ báo `No suitable driver found for jdbc:sqlite`.

Jar dùng **một điểm vào chung** `com.pbl4.Launcher`:

```bash
mvn -q package

# Cua so 1: server (tu tao file pbl4.db ngay thu muc dang chay)
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar

# Cua so 2: giao dien client. Mac dinh noi toi 127.0.0.1:9000
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar client

# Client o may khac: truyen IP cua may chay server
java -jar pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar client 192.168.1.10 9000
```

Cách chạy cũ (trỏ thẳng class) vẫn dùng được:

```bash
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.client.ui.LoginUI
java -cp pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.client.ui.LoginUI 192.168.1.10 9000
```

> Demo 2 máy: hai máy phải cùng mạng, và trên máy chạy server phải mở **TCP 9000** + **UDP 9001**
> trong tường lửa. Nếu không mở UDP 9001 thì vào được lớp, thấy màn hình, nhưng **không nghe tiếng**.

### Client giả lập để test từng chức năng

```bash
java -cp target/classes com.pbl4.tools.TestClient <host> <port> [--as user:pass:ROLE] <lenh> [tham so]
```

`--as` tự đăng ký + đăng nhập trước khi chạy lệnh, ví dụ
`TestClient 127.0.0.1 9000 --as lan:123456:TEACHER screen-send new 30 15`.

| Lệnh | Việc |
|---|---|
| `hold <giây>` | Giữ kết nối (chứng minh server nhận nhiều kết nối đồng thời) |
| `auth <user> <pass> [ROLE]` | Đăng ký (nếu chưa có) rồi đăng nhập |
| `rooms` | Xin danh sách lớp |
| `create <tên lớp>` | Tạo lớp (chỉ TEACHER) |
| `join <roomId>` / `leave <roomId>` | Vào / rời lớp |
| `chat <roomId> <nội dung>` | Gửi chat |
| `screen-start` / `screen-stop <roomId>` | Xin bắt đầu / dừng chia sẻ màn hình |
| `screen-send <roomId\|new> <giây> [giâyChờ]` | Giả máy giáo viên: `new` = tự tạo lớp rồi gửi frame JPEG 62 KB ở 10 FPS |
| `screen-recv <roomId> <giây> [ms]` | Nhận frame. **`ms > 0` = giả lập client MẠNG CHẬM**; in độ trễ frame `p50/p95/max` |
| `audio-send <roomId> <giây>` | Giả mic: gửi gói 648 byte ở 50 gói/giây |
| `audio-recv <roomId> <giây>` | Nhận thoại, **đo tỉ lệ mất gói và số gói sai thứ tự** |

Ví dụ chứng minh bước 3.3 (một client chậm không làm đứng cả lớp) — mở 4 cửa sổ:

```bash
# Cua so 1: server
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar
# Cua so 2: giao vien TU TAO lop, cho 15 giay, roi chia se 30 giay -> chep ROOM_ID in ra
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.TestClient 127.0.0.1 9000 --as lan:123456:TEACHER screen-send new 30 15
# Cua so 3: hoc sinh binh thuong (trong 15 giay do)
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.TestClient 127.0.0.1 9000 --as hs1:123456:STUDENT screen-recv <ROOM_ID> 40
# Cua so 4: hoc sinh MANG CHAM
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.TestClient 127.0.0.1 9000 --as hs2:123456:STUDENT screen-recv <ROOM_ID> 40 200
```

Kỳ vọng: học sinh bình thường nhận frame đều kèm thống kê độ trễ `p50/p95`; học sinh mạng chậm **không** làm bạn kia
giật, và server in ra số frame đã bỏ của bạn đó. (Lưu ý: độ trễ chỉ đúng khi bên gửi và bên nhận chạy trên cùng một máy.)

---

## 3. Trạng thái hiện tại — cái gì xong, cái gì chưa

> **Kiểm thử tự động: 59 test JUnit, 0 lỗi** (`mvn -o -q verify`) + `T0Demo` 20/20 +
> `TV2Chang2Test` 17/17. Chi tiết từng nhóm test xem `docs/KIEN_TRUC.md` mục 7.

### Cấu trúc sau refactor (nhánh `refactor/chuan-hoa-cau-truc`)

| File | Nội dung |
|---|---|
| `common/Protocol.java` | Cổng 9000/9001, `MAX_PAYLOAD`, layout gói UDP, hằng số pool |
| `common/PacketType.java` | Mã byte của 19 loại gói, đúng bảng 1.5.3 |
| `common/Packet.java`, `PacketUtils.java` | Gói bất biến; `encode` / `sendPacket` / `readPacket` |
| `common/msg/Messages.java` | **Một chỗ duy nhất** encode/decode payload — 11 record + golden test |
| `common/RoomInfo.java`, `Sha256.java` | Thông tin lớp; băm mật khẩu phía client |
| `server/ServerMain.java` | ServerSocket 9000, thread pool 50, vòng accept, thống kê định kỳ |
| `server/ServerConfig.java` | Cấu hình server; **bind cổng trong constructor** ⇒ test dùng cổng ngẫu nhiên |
| `server/ClientHandler.java` | Chỉ còn transport + phiên (313 dòng); định tuyến qua `ServerContext` |
| `server/handler/AuthHandler.java` | Đăng nhập/đăng ký + kiểm tra đầu vào (B7) |
| `server/handler/RoomHandler.java` | Tạo/vào/rời lớp từ CSDL (B1), chống phòng ma (B2), chat (B5) |
| `server/handler/ScreenHandler.java` | Chỉ chủ lớp được chia sẻ (B3), relay frame |
| `server/handler/AudioHandler.java` | Bật/tắt mic, yêu cầu đăng nhập (B7) |
| `server/Room.java` | Thành viên + `ReentrantLock` riêng + cờ `closed` (B2) + broadcast sao-chép-rồi-nhả-khoá |
| `server/RoomManager.java` | `ConcurrentHashMap`, `openIfAbsent`, dọn lớp rỗng |
| `server/AudioRelayServer.java` | Relay UDP 9001, **trừ người gửi**, **chỉ nhận từ thành viên bật mic** (B6) |
| `server/db/DatabaseManager.java` | **Interface**; `SqliteDatabaseManager` là bản cài SQLite (test dùng `:memory:`) |
| `client/media/AudioSession.java` | MỘT đối tượng sở hữu socket UDP + mic + loa của một lần vào lớp |
| `tools/` | 4 công cụ kiểm thử (xem mục 2) |
| `src/test/` | 59 test JUnit (xem mục 2) |

### Đã nhận từ TV2 và TV3 (cập nhật 27/10/2026)

| Nhóm | File | Ghi chú |
|---|---|---|
| TV2 | `client/media/` — 7 file | `AudioConfig`, `AudioRecorder`, `AudioPlayer`, `JitterBuffer`, `ScreenCapturer`, `ScreenReceiver`, `MediaStats` |
| TV3 | `client/net/` — 2 file | `ServerConnection`, `ClientListener` |
| TV3 | `client/ui/` — 4 file | `LoginUI`, `HomeUI`, `ClassroomUI`, `ScreenPanel` |
| TV3 | `server/db/DatabaseManager` | Đã thành **lớp SQLite thật** — xem mục 3.1 |
| **Gộp 27/10** | `client/ui/ClassroomUI.java` | Merge `27ecf3a` của TV3 — nối UI với `client/media`; kèm 1 fix (xem 3.2) |

> `27ecf3a` được commit **sau** khi PR #4 đã merge, nên trước đó `ClassroomUI` trên `main`
> không import `client/media` — cả tầng media là code chết. Đã gộp trong `ab075bc`.

### 3.2 Fix kèm theo khi gộp TV3: loa phải mở từ lúc vào lớp

`AudioRelayServer` relay thoại cho **mọi** thành viên đã đăng ký cổng UDP (chỉ lọc theo
`hasUdpAddress()`, **không** lọc theo mic bật/tắt). Nhưng bản gộp đầu tiên chỉ gọi
`AudioPlayer.start()` **bên trong** `toggleMicrophone()`.

⇒ Học sinh không bấm "Bật Mic" thì gói UDP vẫn tới cổng, nhưng **không có luồng nào đọc**
socket → nghe như mất tiếng. Đã sửa trong `a4d9580`:

- Constructor gọi `startAudioPlayback()`; lỗi loa chỉ hiện trạng thái, không làm sập phòng.
- Tắt mic **chỉ** dừng `AudioRecorder`, không dừng `AudioPlayer`.
- `AUDIO_ON` lấy cổng từ `audioSocket.getLocalPort()` thay vì `audioPlayer.start()`.

### 3.1 Đã quay về thiết kế 1.5.5: `DatabaseManager` là interface

Đã quay về thiết kế 1.5.5: `DatabaseManager` là interface, `SqliteDatabaseManager` là bản cài đặt; `InMemoryDatabaseManager` đã xoá, test dùng SQLite `:memory:`.

---

## 4. Hợp đồng giao thức (chốt — đổi phải báo nhóm)

### Cổng

| Cổng | Giao thức | Dùng cho |
|---|---|---|
| **9000** | TCP | Đăng nhập, lớp học, chat chữ, **frame màn hình** |
| **9001** | UDP | **Chỉ** dữ liệu âm thanh PCM |
| ngẫu nhiên | UDP | Cổng client bind để **nhận** thoại — client báo cổng này trong `JOIN_ROOM_REQ` |

### Khung gói TCP

```
+----------------+-------------+------------------------------+
| 4 byte độ dài  | 1 byte TYPE | N byte payload               |
| = 1 + N        |             |                              |
+----------------+-------------+------------------------------+
```

Ghi bằng `DataOutputStream`, đọc bằng `DataInputStream.readFully()` **đúng N byte**.

### Gói UDP thoại

```
+----------------+----------------+--------------------------------+
| 4 byte roomId  | 4 byte seqNo   | 640 byte PCM 16kHz/16bit/mono  | = 648 byte
+----------------+----------------+--------------------------------+
```

- `seqNo` **không dùng để phát** — dùng để **đo** tỉ lệ mất gói và số gói đến sai thứ tự.
- 648 byte nằm gọn trong MTU 1500 ⇒ không bị phân mảnh IP.
- Server **không gửi lại gói cho người gửi** (nếu không sẽ bị vọng).

### Bảng mã gói

Xem `common/PacketType.java` — đã ghi kèm định dạng payload từng loại.

---

## 5. Bốn quyết định thiết kế quan trọng

Bốn quyết định lớn nhất của thiết kế (một luồng ghi duy nhất, hai hàng đợi, luật
"sao chép → nhả khoá → gửi", mỗi lớp một khoá) đã chuyển sang
**`docs/KIEN_TRUC.md` mục 8** — cùng sơ đồ gói, bảng luồng, bảng cơ chế đồng bộ và các quy tắc
bảo mật phía server. Đó là nguồn cho Chương 2 của báo cáo.

---

## 6. Đo đạc — số liệu đưa vào Chương 3

### 6.1 Bảng đo tải (từ `LoadTest`)

Chạy `mvn -q exec:java -Dexec.mainClass=com.pbl4.tools.LoadTest` rồi chép bảng in ra vào báo cáo.
Công cụ in sẵn 4 cột: `pool-`, `srv-writer`, tổng số luồng, CPU %, heap.

> ⚠️ **Đừng dùng `ThreadMXBean.getThreadCount()` trực tiếp.** Nó đếm cả luồng nội bộ JVM
> (GC, trình biên dịch JIT) — những luồng này được tạo trong lúc chạy tải và không bao giờ kết thúc,
> nên bạn sẽ tưởng là rò rỉ luồng. `LoadTest` đếm riêng theo **tiền tố tên luồng của mình**.

### 6.2 Thí nghiệm "giữ khoá khi gửi" và "nhả khoá trước khi gửi" (bước 4.2)

Đây là thí nghiệm quan trọng nhất của TV1: nó biến một câu lý thuyết thành một con số.

1. Tạo nhánh Git `exp/hold-lock`. Sửa `Room.broadcastFrame()` thành **bản A**: gửi socket
   *trong lúc vẫn giữ khoá*.
2. Chạy 1 giáo viên `screen-send` + 1 học sinh `screen-recv` + 1 học sinh `screen-recv … 200`.
3. Đo thời gian học sinh bình thường chờ để nhận được 1 frame.
4. Quay lại nhánh chính (**bản B**), chạy lại đúng kịch bản, đo lại.
5. Điền bảng:

| Cấu hình | Thời gian chờ của client bình thường | Frame/giây thực tế | Số frame bị bỏ |
|---|---|---|---|
| Bản A — giữ khoá khi gửi | | | 0 (nhưng chậm) |
| Bản B — nhả khoá trước khi gửi | | | > 0 (client chậm bị bỏ) |

> Ghi lại cả hai con số, **kể cả nếu kết quả không đẹp như mong đợi**. Số đo thật luôn đáng tin
> hơn số đo đẹp.

---

## 7. Yêu cầu bắt buộc với TV2 (dễ ghép lỗi nhất)

1. **Client phải gửi audio từ đúng cổng UDP đã đăng ký** trong `JOIN_ROOM_REQ`. Nói cách khác,
   `AudioRecorder` và `AudioPlayer` phải dùng **chung một `DatagramSocket`**.
   Lý do: server trừ người gửi bằng cách so khớp **cặp (IP, cổng)**. Nếu recorder mở socket riêng
   để gửi thì cổng nguồn khác cổng đã đăng ký, server không nhận ra người gửi và **sẽ xảy ra vọng**.
2. Cổng UDP mà client bind để nhận là **số ngẫu nhiên do hệ điều hành cấp**, không phải 9001.
   Lấy bằng `datagramSocket.getLocalPort()`.
3. Mọi cập nhật Swing phải qua `SwingUtilities.invokeLater(...)` — **không vẽ UI từ thread mạng**.

---

## 8. Nhật ký

- `docs/nhat-ky-loi.md` — mỗi lỗi gặp phải, nguyên nhân, cách sửa (bằng chứng nhóm làm thật).
- `docs/nhat-ky-hieu.md` — mỗi thành phần xong, ghi 4 dòng: cấu trúc · thiết lập · quy trình · đánh đổi.
- `docs/do-dac/` — ảnh Task Manager, JConsole, bảng số đo.
- `docs/trang-trang/` — ảnh chụp bài viết trang trắng.
