# KỊCH BẢN DEMO — PBL4 Đề tài 21 (Dạy học trực tuyến)

> Mục tiêu: trong **5 phút** cho giảng viên thấy (1) chương trình chạy được thật,
> (2) đa luồng + đồng bộ nằm ở đâu, (3) nhóm đang ở đâu và còn thiếu gì.
> Người trình bày phải **tự bấm lại toàn bộ kịch bản ít nhất 1 lần** trước buổi demo.

## 0. Chuẩn bị (làm trước, không làm trong giờ demo)

```bash
mvn -q clean package
```

Lệnh này tạo **jar đủ thư viện** (~12 MB, đã gộp `sqlite-jdbc`).
Dùng `target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar`.
**Không** dùng `original-*.jar` — bản đó thiếu driver, server sập ngay khi mở.

Mở **hai** cửa sổ dòng lệnh tại thư mục dự án:

```bash
# CUA SO 1 — server
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar
```

Chờ tới khi thấy `===== SERVER DANG CHAY =====` và `Cho ket noi...` rồi mới mở client.

```bash
# CUA SO 2 — client giao dien (mo 2 lan: 1 giao vien, 1 hoc sinh)
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar client
```

Cách chạy cũ vẫn dùng được: `java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.client.ui.LoginUI`.

Nếu client ở **máy khác**: chép jar sang, rồi thêm IP máy server:

```bash
java -jar pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar client 192.168.1.10 9000
```

## 1. Kịch bản 5 phút

| # | Làm gì | Nói gì |
|---|---|---|
| 1 | Chỉ cửa sổ server: `TCP 9000`, `UDP 9001`, `Pool: 50 luong` | "Server một tiến trình, hai cổng: TCP cho điều khiển và ảnh, UDP riêng cho tiếng. Bể 50 luồng." |
| 2 | Client 1: chọn **Giáo viên**, nhập tên + mật khẩu, bấm **Đăng ký** rồi **Đăng nhập** | "Đăng ký/đăng nhập lưu SQLite. Mật khẩu được băm SHA-256 **ở client** trước khi gửi." |
| 3 | Ở màn hình Home: nhập tên lớp, bấm **Tạo lớp** → ra mã 6 chữ số | "Giáo viên tạo lớp, server cấp mã 6 chữ số." |
| 4 | Client 2: chọn **Học sinh**, đăng ký + đăng nhập, chọn lớp vừa tạo, bấm **Tham gia** | "Học sinh vào bằng mã lớp. Danh sách thành viên cập nhật hai bên." |
| 5 | Client 1 (giáo viên): bấm **Chia sẻ màn hình** | "Client tự chụp bằng `Robot`, nén JPEG 10 hình/giây, gửi TCP. Server **chỉ chuyển tiếp**, không chụp không nén." |
| 6 | Chỉ cửa sổ học sinh: ảnh đang đổ | "Học sinh nhận frame và vẽ lên giao diện." |
| 7 | Client 1: bấm **Bật Mic**; client 2: nói/nghe | "Thoại đi UDP 9001, gói 648 byte = 20 ms tiếng. Mỗi bên có luồng nhận riêng và jitter buffer." |
| 8 | Gõ vài dòng **chat** hai chiều | "Chat chữ đi chung kênh điều khiển TCP. **Tên người gửi do server gắn vào trước khi phát**, client không giả mạo được." |
| 9 | Bấm **Rời phòng** ở học sinh | "Rời lớp thì về **trang chủ, giữ nguyên kết nối** — không phải đăng nhập lại; server cập nhật danh sách; lớp rỗng thì tự xoá khỏi bộ nhớ." |

**Lưu ý khi demo trên MỘT máy:** ảnh chia sẻ là ảnh của chính màn hình đang mở, nên trông
lồng nhau — đó là điều bình thường, không phải lỗi. Âm thanh nên đeo tai nghe hoặc giảm
volume để tránh vọng. Nếu có 2 máy thì demo 2 máy sẽ rõ hơn hẳn.

## 2. Khi giảng viên hỏi "đa luồng và đồng bộ ở đâu"

Đây là yêu cầu bắt buộc của đề, phải chỉ được **bằng chứng**, không nói suông.

1. **Chỉ console server**: dòng `[stats] luong dang song=...` — số luồng tăng khi có client.
2. **Nói 3 điểm này** (đúng với code):
   - Mỗi client có **hai** luồng: một luồng xử lý trong bể + một luồng ghi riêng
     `srv-writer-N` ⇒ không bao giờ hai luồng cùng ghi một socket.
   - Mỗi `Room` có `ReentrantLock` riêng. Luật vàng: **sao chép dữ liệu trong khoá →
     nhả khoá → rồi mới gửi**. Gửi trong khoá thì cả lớp chờ một người mạng chậm.
   - Hai hàng đợi: **điều khiển không bao giờ bỏ gói**, **frame sức chứa 2, đầy thì vứt
     frame cũ nhất** ⇒ một học sinh mạng chậm không kéo đứng cả lớp.
3. **Chạy bài kiểm thử tự động** nếu còn thời gian:

```bash
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.T0Demo
```

Kỳ vọng: `KET QUA: 20 DAT / 20 TONG`. Bài này tự dựng server + 1 giáo viên + 3 học sinh,
đo cả tỉ lệ mất gói thoại.

## 3. Nhóm đang ở đâu (nói thẳng, không né)

**Đã xong:**
- Server đa luồng + giao thức 19 loại gói, tạo/vào/rời lớp, relay thoại, 4 handler nghiệp vụ (TV1).
- Client media: mic, loa, jitter buffer, chụp + nén màn hình, `AudioSession` gom socket+mic+loa (TV2).
- CSDL SQLite (interface `DatabaseManager` + bản SQLite thật) + giao diện đăng nhập, trang chủ, phòng học (TV3).
- Đã hoàn tất refactor "chuẩn hoá cấu trúc" (Bước 0–7 của `KE_HOACH_REFACTOR.md`): sửa 8 lỗi B0–B8,
  **59 test JUnit 0 lỗi**, `T0Demo` 20/20, `TV2Chang2Test` 17/17.

**Chưa xong:**
- 4.2 Thí nghiệm so sánh "giữ khoá khi gửi" vs "nhả khoá trước khi gửi" (bảng số liệu).
- 4.3 Chương báo cáo. 4.4 Slide. Smoke GUI 14 bước trong `KE_HOACH_REFACTOR.md` 9.2 (cần người bấm thật).
- Chưa thử thoại/màn hình giữa **nhiều máy thật** — mới chạy trên một máy.

## 4. Phương án dự phòng

| Hỏng gì | Làm gì |
|---|---|
| Server báo `No suitable driver found for jdbc:sqlite` | Đang chạy nhầm `original-*.jar`. Chạy lại bằng jar 12 MB. |
| `Address already in use` | Server cũ còn chạy. Đóng cửa sổ server cũ rồi mở lại. |
| Client không kết nối được | Server chưa in `Cho ket noi...`, hoặc sai IP/cổng, hoặc tường lửa chặn TCP 9000. |
| Vào lớp được nhưng **không nghe tiếng** | UDP 9001 bị chặn (2 máy), hoặc chưa bấm **Bật Mic** ở phía nói. |
| Vào lớp bị báo `Lop dang dong, vui long thu lai` | Lớp vừa có người cuối rời (đang được dọn). **Bấm Tham gia lần nữa** là vào được. |
| Đăng ký bị báo tên/mat khau khong hop le | Tên phải 2–32 ký tự: chữ, số, `_` hoặc `.`; mật khẩu đã băm SHA-256 ở client — không gõ hash tay. |
| Không có mạng / máy khác | Demo 1 máy 2 client vẫn chứng minh được toàn bộ luồng xử lý. |
| Sập giữa buổi | Chạy `T0Demo` và nói: "phần này nhóm đã kiểm thử tự động 20/20". |
