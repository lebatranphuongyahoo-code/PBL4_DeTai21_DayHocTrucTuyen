# PBL4 · Đề tài 21 — Dạy học trực tuyến

**Nhóm 4 · Hệ điều hành & Mạng máy tính · Đại học Bách khoa Đà Nẵng**

Phần việc trong repo này chia làm 3 khối, mỗi người sở hữu một khối:

| Thư mục | Người sở hữu | Nội dung |
|---|---|---|
| `com/pbl4/common/` | **TV1 (Minh)** | Hợp đồng giao thức — cả 3 cùng dùng |
| `com/pbl4/server/` | **TV1 (Minh)** | Server đa luồng, phòng học, relay thoại + màn hình |
| `com/pbl4/server/db/` | **TV3** | SQLite: tài khoản, lớp học |
| `com/pbl4/client/net/`, `client/ui/` | **TV3** | Kết nối mạng phía client, giao diện Swing |
| `com/pbl4/client/media/` | **TV2** | Mic, loa, chụp màn hình, jitter buffer |
| `com/pbl4/tools/` | **TV1** | Công cụ kiểm thử — **không nằm trong sản phẩm** |

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

# 2. Kiểm tra hợp đồng giao thức (bài tự kiểm bắt buộc của bước 1.2)
mvn -q test -Dtest=PacketUtilsTest
#    Ky vong:  Tests run: 7, Failures: 0

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

```bash
mvn -q package

# Cua so 1: server (tu tao file pbl4.db ngay thu muc dang chay)
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar

# Cua so 2: giao dien client. Mac dinh noi toi 127.0.0.1:9000
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.client.ui.LoginUI

# Client o may khac: truyen IP cua may chay server
java -cp pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.client.ui.LoginUI 192.168.1.10 9000
```

> Demo 2 máy: hai máy phải cùng mạng, và trên máy chạy server phải mở **TCP 9000** + **UDP 9001**
> trong tường lửa. Nếu không mở UDP 9001 thì vào được lớp, thấy màn hình, nhưng **không nghe tiếng**.

### Client giả lập để test từng chức năng

```bash
java -cp target/classes com.pbl4.tools.TestClient <host> <port> <lenh> [tham so]
```

| Lệnh | Việc |
|---|---|
| `hold <giây>` | Giữ kết nối (chứng minh server nhận nhiều kết nối đồng thời) |
| `auth <user> <pass> [ROLE]` | Đăng ký (nếu chưa có) rồi đăng nhập |
| `rooms` | Xin danh sách lớp |
| `create <tên lớp>` | Tạo lớp (chỉ TEACHER) |
| `join <roomId>` / `leave <roomId>` | Vào / rời lớp |
| `chat <roomId> <nội dung>` | Gửi chat |
| `screen-start` / `screen-stop <roomId>` | Xin bắt đầu / dừng chia sẻ màn hình |
| `screen-send <roomId> <giây>` | Giả máy giáo viên: gửi frame JPEG 62 KB ở 10 FPS |
| `screen-recv <roomId> <giây> [ms]` | Nhận frame. **`ms > 0` = giả lập client MẠNG CHẬM** |
| `audio-send <roomId> <giây>` | Giả mic: gửi gói 648 byte ở 50 gói/giây |
| `audio-recv <roomId> <giây>` | Nhận thoại, **đo tỉ lệ mất gói và số gói sai thứ tự** |

Ví dụ chứng minh bước 3.3 (một client chậm không làm đứng cả lớp) — mở 4 cửa sổ:

```bash
# Cua so 1: server
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar
# Cua so 2: giao vien tao lop, cho 15 giay, roi chia se 30 giay -> chep ROOM_ID in ra
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.TestClient 127.0.0.1 9000 screen-send new 30 15
# Cua so 3: hoc sinh binh thuong (trong 15 giay do)
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.TestClient 127.0.0.1 9000 screen-recv <ROOM_ID> 40
# Cua so 4: hoc sinh MANG CHAM
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.TestClient 127.0.0.1 9000 screen-recv <ROOM_ID> 40 200
```

Kỳ vọng: học sinh bình thường nhận frame đều kèm thống kê độ trễ `p50/p95`; học sinh mạng chậm **không** làm bạn kia
giật, và server in ra số frame đã bỏ của bạn đó. (Lưu ý: độ trễ chỉ đúng khi bên gửi và bên nhận chạy trên cùng một máy.)

---

## 3. Trạng thái hiện tại — cái gì xong, cái gì chưa

### Đã xong (TV1)

| File | Nội dung |
|---|---|
| `common/Protocol.java` | Cổng 9000/9001, `MAX_PAYLOAD`, layout gói UDP, hằng số pool |
| `common/PacketType.java` | Mã byte của 19 loại gói, đúng bảng 1.5.3 |
| `common/Packet.java` | Đối tượng gói bất biến |
| `common/PacketUtils.java` | `encode` / `sendPacket` / `readPacket` + `buildPayload` |
| `common/RoomInfo.java` | Thông tin lớp cho `ROOM_LIST_RES` |
| `common/Sha256.java` | Băm mật khẩu phía client |
| `server/ServerMain.java` | ServerSocket 9000, thread pool 50, vòng accept, thống kê định kỳ |
| `server/ClientHandler.java` | Đọc gói, định tuyến 19 loại, **một luồng ghi duy nhất**, 2 hàng đợi |
| `server/Room.java` | Thành viên + `ReentrantLock` riêng + broadcast sao-chép-rồi-nhả-khoá |
| `server/RoomManager.java` | `ConcurrentHashMap` các lớp, dọn lớp rỗng |
| `server/AudioRelayServer.java` | Relay UDP 9001, **trừ người gửi**, thống kê mỗi 100 gói |
| `tools/` | 4 công cụ kiểm thử (xem mục 2) |

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

### 3.1 Hợp đồng đã đổi: `DatabaseManager` không còn là interface

`DatabaseManager` giờ là **lớp cụ thể** bọc SQLite: `new DatabaseManager("pbl4.db")`.
`InMemoryDatabaseManager` đổi thành `extends DatabaseManager` và gọi `super(":memory:")`.
`ServerMain.main()` gọi thẳng bản SQLite.

Hệ quả cần nắm khi bảo vệ:

- **Không còn "đổi một dòng"** như thiết kế ban đầu — CSDL đã gắn cứng trong `ServerMain`.
- **Không có file `SqliteDatabaseManager`** như bảng 1.5.5 mô tả; chức năng nằm ngay trong
  `DatabaseManager`. Nhóm cần xác nhận cách đặt tên này trước khi in báo cáo.
- Muốn quay lại bản tạm trong RAM: sửa `ServerMain.main()` thành
  `DatabaseManager db = new InMemoryDatabaseManager();` — vẫn biên dịch được vì lớp này kế thừa
  `DatabaseManager`.

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

## 5. Bốn quyết định thiết kế quan trọng (dùng để trả lời hội đồng)

### 5.1 Một luồng ghi duy nhất cho mỗi socket

Ghi một gói là **ba thao tác liên tiếp** (4 byte + 1 byte + payload). Nếu hai luồng cùng gọi
`write()` trên một socket, gói của người này sẽ chèn vào giữa gói của người kia và bên nhận
đọc ra rác.

**Cách làm:** mọi gói được **đóng gói xong** thành `byte[]` (`PacketUtils.encode`) rồi đẩy vào
hàng đợi. Chỉ luồng `srv-writer-N` được chạm vào socket.

### 5.2 Hai hàng đợi riêng: điều khiển và frame

| Hàng đợi | Sức chứa | Khi đầy |
|---|---|---|
| `controlQueue` (đăng nhập, vào lớp, chat, lỗi…) | không giới hạn | **không bao giờ bỏ** — luôn được ưu tiên |
| `frameQueue` (frame màn hình) | **2** | **bỏ frame CŨ NHẤT** (drop-oldest) |

Vì sao sức chứa 2 mà không phải 100: sức chứa 100 sẽ tích tụ frame cũ và đẩy độ trễ lên hàng giây.
Với video trực tiếp, frame mới phản ánh hiện tại — giữ frame cũ là đang chiếu lại quá khứ.

Vì sao tách hai hàng đợi: một học sinh mạng chậm chỉ được phép **mất frame màn hình**,
không bao giờ được mất thông báo vào/ra lớp hay lời nhắn chat.

### 5.3 Luật vàng: sao chép trong khoá — nhả khoá rồi mới gửi

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

### 5.4 Mỗi lớp có khoá riêng, không dùng khoá toàn cục

Nếu dùng một khoá chung cho cả server thì mọi lớp phải xếp hàng chờ nhau — mất hết lợi ích của
đa luồng. `ConcurrentHashMap` cho `RoomManager` cũng vì lý do này: tra cứu lớp không trở thành
nút thắt.

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
