# HỌC NHANH — PBL4 · Đề tài 21

> **Một trang. Dán kèm (hoặc đính kèm) mỗi khi hỏi AI về dự án.**
> Cập nhật **27/10/2026** · commit `58d7e75` · **33 file Java**.
> Mọi số liệu dưới đây lấy từ code thật trong repo, không phải từ trí nhớ.

---

## 1. Dự án làm gì — 4 dòng

1. Giáo viên **tạo lớp**, server cấp **mã 6 chữ số**; học sinh **nhập mã để vào lớp**.
2. Giáo viên **chia sẻ màn hình** → server chuyển tiếp ảnh cho cả lớp.
3. Mọi người **nói chuyện hai chiều** (thoại).
4. Tất cả đi qua **TCP 9000** (điều khiển + màn hình) và **UDP 9001** (chỉ tiếng nói).

**Công nghệ:** Java 21 · Maven · Swing · `java.net` · `javax.sound.sampled` · `java.awt.Robot`.
Chỉ **một** thư viện ngoài: `org.xerial:sqlite-jdbc`.

---

## 2. Trong repo có gì — 51 file Java (41 main + 10 test)

| Gói | Số file | Nội dung |
|---|---|---|
| `common/` | 7 | `Protocol` · `PacketType` · `Packet` · `PacketUtils` · `RoomInfo` · `Sha256` · `msg/Messages` |
| `server/` | 7 | `ServerMain` · `ServerConfig` · `ServerContext` · `ClientHandler` · `Room` · `RoomManager` · `AudioRelayServer` |
| `server/handler/` | 4 | TV1: `AuthHandler` · `RoomHandler` · `ScreenHandler` · `AudioHandler` |
| `server/db/` | 2 | `DatabaseManager` (**interface**) · `SqliteDatabaseManager` (bản cài SQLite) |
| `client/media/` | 8 | TV2: `AudioSession` · `AudioConfig` · `AudioRecorder` · `AudioPlayer` · `JitterBuffer` · `ScreenCapturer` · `ScreenReceiver` · `MediaStats` |
| `client/net/` | 2 | TV3: `ServerConnection` · `ClientListener` |
| `client/ui/` | 4 | TV3: `LoginUI` · `HomeUI` · `ClassroomUI` · `ScreenPanel` |
| `tools/` | 6 | TV1: 4 công cụ kiểm thử · TV2: `TV2Chang1Test`, `TV2Chang2Test`, `TV2Chang3Test` |
| `src/test/` | 10 | 59 test JUnit: hợp đồng (`PacketUtilsTest`, `MessagesTest`), luật server (`ServerRulesTest`), phòng học (`RoomTest`, `RoomManagerTest`), CSDL, media, tích hợp (`T0ScenarioTest`, `LoadSmokeTest`) |

---

## 3. Hai điểm lệch cần biết (dễ bị hỏi)

**a) ĐÃ SỬA — `DatabaseManager` quay về interface** đúng thiết kế bảng 1.5.5:
`SqliteDatabaseManager` là bản cài; `InMemoryDatabaseManager` đã xoá; test dùng SQLite `:memory:`.
Nói đúng khi bảo vệ: *"đúng bảng 1.5.5, đổi bản cài không phải đụng code khác."*

Ngoài ra: TV2 có **nhật ký hiểu + 3 lỗi thật** trong `docs/`; TV3 **chưa có** nhật ký.

**c) Đã xử lý 27/10 — tầng media từng là code chết.** Commit `27ecf3a` của TV3 được tạo **sau**
khi PR #4 đã merge, nên `ClassroomUI` trên `main` **không hề** import `client/media`. Toàn bộ
`ScreenCapturer`, `ScreenReceiver`, `AudioPlayer`, `AudioRecorder` tồn tại nhưng **không ai gọi**.
Đã merge (`ab075bc`) rồi sửa thêm một lỗi thật (`a4d9580`): loa chỉ được mở bên trong
`toggleMicrophone`, mà server relay thoại cho **mọi** thành viên đã đăng ký cổng UDP — nên học
sinh không bấm "Bật Mic" thì gói UDP vẫn tới cổng nhưng **không có luồng nào đọc**, nghe như mất
tiếng. Giờ loa mở ngay khi vào lớp; nút Mic chỉ điều khiển **đường gửi**.

---

## 4. Lộ trình 4 chặng — đang ở đâu

| Chặng | Nội dung | Trạng thái |
|---|---|---|
| 1 | Khung dự án · hợp đồng giao thức · `ServerMain` · `ClientHandler` · T0 | ✅ |
| 2 | `Room`/`RoomManager` · tạo–vào–rời lớp · `AudioRelayServer` | ✅ |
| 3 | Quyền chia sẻ · nhả khoá trước khi gửi · chống nghẽn 2 hàng đợi | ✅ |
| 4 | 4.1 đo tải ✅ · **4.2 thí nghiệm giữ-khoá vs nhả-khoá ⬜** · **4.3 chương báo cáo ⬜** · **4.4 slide + demo ⬜** | đang làm |

**TV2:** chặng 1–3 xong, tự kiểm 16/16 · 17/17 · 20/20 ĐẠT.
**TV1:** `T0Demo` **20/20 ĐẠT** trên code đã gộp (chạy lại 27/10), `LoadTest` 4/4 ĐẠT.
**Sau khi gộp TV3 (27/10):** build `mvn -o clean package` OK · `T0Demo` 20/20 · TV2 chặng 1
16/16 · chặng 2 17/17 · chặng 3 20/20 — **73/73 ĐẠT**.
**Chưa làm:** thử thoại / chia sẻ màn hình giữa **nhiều máy thật** — mới chạy trên một máy.

---

## 5. Từ điển 15 từ — nghĩa đơn giản trước, thuật ngữ sau

| Từ | Nghĩa đơn giản |
|---|---|
| **Socket** | Ổ cắm mạng: một đầu dây nối giữa 2 chương trình |
| **TCP** | Gửi như chuyển phát bảo đảm: đúng thứ tự, mất thì gửi lại |
| **UDP** | Gửi như bưu thiếp: nhanh, có thể mất, không xếp lại thứ tự |
| **Khung gói (framing)** | Ghi độ dài lên đầu gói để bên kia biết đọc bao nhiêu byte |
| **Bể luồng (thread pool)** | 50 nhân viên trực sẵn, khách nào tới thì một người tiếp |
| **Luồng (thread)** | Một việc đang chạy song song với việc khác |
| **Khoá (lock)** | Chìa khoá phòng: ai vào thì khoá lại, người khác chờ |
| **Vùng găng** | Đoạn code chỉ một luồng được chạy tại một thời điểm |
| **Deadlock** | Hai người mỗi người giữ một chìa, cùng chờ chìa của người kia |
| **Hàng đợi chặn** | Ống nước có van: đầy thì người bỏ vào phải chờ |
| **Drop-oldest** | Ống đầy thì **vứt cái cũ nhất**, giữ cái mới |
| **Relay** | Trạm trung chuyển: nhận rồi chuyển tiếp, không xử lý nội dung |
| **Jitter buffer** | Đệm chống giật: giữ vài gói rồi phát đều |
| **PCM** | Âm thanh thô: 16 000 mẫu/giây, 16 bit, 1 kênh ⇒ **640 byte mỗi 20 ms** |
| **MTU** | Kích thước tối đa một gói đi trong mạng (~1500 byte) |

---

## 6. Sáu câu hội đồng hay hỏi — phải trả lời được

1. **Vì sao thoại dùng UDP, màn hình dùng TCP?**
   Thoại trễ là hỏng, mất một gói 20 ms thì tai không nghe ra ⇒ thà mất còn hơn chậm.
   Ảnh màn hình mất một mảnh là hỏng cả khung ⇒ cần TCP gửi lại.

2. **Vì sao mỗi client cần HAI luồng?**
   Một luồng trong bể xử lý logic; một luồng ghi riêng chỉ ghi ra socket. Nhờ vậy không
   bao giờ có hai luồng cùng ghi một socket (làm gói bị trộn byte).

3. **Vì sao hàng đợi frame chỉ có sức chứa 2?**
   Để 100 thì frame cũ tích tụ, độ trễ lên hàng giây. Video trực tiếp: frame mới quan trọng
   hơn frame cũ ⇒ đầy thì vứt cái cũ.

4. **Vì sao phải nhả khoá TRƯỚC khi gửi?**
   Ghi ra socket có thể chậm (mạng nghẽn). Giữ khoá trong lúc gửi thì cả lớp phải chờ một
   người ⇒ biến khoá thành nút thắt. Luật vàng: **sao chép trong khoá → nhả khoá → rồi mới gửi**.

5. **Vì sao nói "không bao giờ tin client"?**
   Client có thể bị sửa. Ẩn nút trên giao diện **không phải** bảo mật — server phải tự kiểm
   lại quyền (4 cửa kiểm tra) trước khi cho chia sẻ màn hình.

6. **Vì sao `DatabaseManager` là lớp mà không phải interface?**
   Thiết kế ban đầu là interface để có hai bản cài (RAM và SQLite). Thực tế nhóm chỉ cần một
   bản SQLite ⇒ gộp lại cho gọn. Đánh đổi: mất khả năng "đổi một dòng", nhưng bớt một lớp.
