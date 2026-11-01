# KẾ HOẠCH HOÀN THIỆN & NÂNG CẤP — PBL4 Đề tài 21 (28/10 → hết đợt 2)

> Viết ngày 28/10/2026 sáng. Mọi số liệu dưới đây lấy từ repo `E:\PBL4_DeTai21` lúc
> `main = 16e7d88`, working tree sạch — không phải nhớ lại.

## 0. Đọc mục này trước — 5 dòng

Code đã đủ để qua môn. Thứ còn thiếu **không phải tính năng**, mà là **bằng chứng**:
số đo cho bước 4.2, nhật ký của TV3, và một lần chạy 2 máy thật. Đó là 3 việc còn lại.
Mọi thứ khác chỉ làm sau khi 3 việc này xong.

---

## 1. Sự thật về trạng thái (kiểm chứng, không đoán)

| Hạng mục | Trạng thái thật | Bằng chứng |
|---|---|---|
| 33 file Java, build sạch | XONG | `git ls-files src/**/*.java` = 33 |
| 19 loại gói định nghĩa | XONG | `PacketType.java` có 19 hằng số `public static final byte` |
| Server định tuyến | XONG | `ClientHandler.java:272` `handle()` — 12 nhánh `case` + `default` báo lỗi |
| 73/73 kiểm thử | XONG (lần cuối 27/10) | `docs/KICH_BAN_DEMO.md:83` |
| **4.2 Thí nghiệm giữ khoá vs nhả khoá** | **CHƯA** | `README.md` mục 6.2 — bảng còn trống 100% |
| **4.3 Chương báo cáo** | **CHƯA** | chưa có file nào trong repo |
| 4.4 Slide | CHƯA | kịch bản demo có, slide không |
| **TV3 nhật ký** | **CHƯA** | `nhat-ky-loi.md` có 10 mục: TV1 7 mục, TV2 3 mục, **TV3 = 0** |
| **Chạy 2 máy thật** | **CHƯA** | `KICH_BAN_DEMO.md:87` "mới chạy trên một máy" |
| Ảnh đo đạc (Task Manager/JConsole) | **CHƯA** | `docs/do-dac/anh-nen/` chỉ có 4 ảnh tham chiếu JPEG quality, không phải ảnh chụp |
| File ghi âm 3 phút | **CHƯA** | `docs/ghi-am/README.md` chỉ là ghi chú |

**Kết luận:** phần code đã đóng. Phần **chứng minh** mới là chỗ trống — và đó cũng là chỗ
hội đồng chấm điểm.

---

## 2. Ưu tiên P0 → P3

| Mức | Việc | Vì sao mức này | Thời lượng |
|---|---|---|---|
| **P0** | Chạy 2 máy thật | Chưa ai chứng minh được TCP 9000 + UDP 9001 qua LAN. Nếu hỏng ở đây thì mọi thứ khác vô nghĩa. | 60–90 phút |
| **P0** | Thí nghiệm 4.2 (giữ khoá vs nhả khoá) | Là **thí nghiệm quan trọng nhất của TV1**; hiện bảng trống. Không có số = không có "đa luồng & đồng bộ" thực chứng. | 2–3 giờ |
| **P1** | TV3 bổ sung nhật ký | Nhóm 3 người mà 2 người có nhật ký. Hội đồng hỏi "Lực làm gì?" là lộ ngay. | 40 phút (Lực tự làm) |
| **P1** | Ảnh đo đạc thật | `do-dac/anh-nen/` đang trống nghĩa là mục 6 README vô chủ. | 30 phút |
| **P2** | Chương 4.3 báo cáo | Dài nhất nhưng là việc **viết**, làm được sau khi có số của P0. | 2–3 ngày |
| **P3** | Slide 4.4 | Chỉ cần có số liệu là làm nhanh. | 1 buổi |

**Luật:** không bắt đầu P2 khi P0 chưa xong. Viết báo cáo trước khi có số là viết lại.

---

## 3. P0-A · Chạy 2 máy thật (sáng nay)

Chuẩn bị đã có sẵn trong `E:\PBL4_Hoc\DEMO\`:
`0-Xem-IP.cmd`, `1-Server.cmd`, `2-Client.cmd <IP>`, `3-Mo-Tuong-Lua.cmd`, `0-HUONG-DAN.txt` (phần B).

**Thứ tự bắt buộc — sai thứ tự là mất 30 phút:**

1. Máy chủ: chạy `0-Xem-IP.cmd` → ghi lại IP Wi-Fi (lần trước là `192.168.1.2`).
2. Máy chủ **trước khi mở server**: `netstat -ano | findstr :9000` → nếu có dòng nào thì
   **phải diệt tiến trình đó** (`taskkill /PID <pid> /F`), đừng chỉ đóng cửa sổ.
3. Máy chủ: `1-Server.cmd` → phải thấy dòng `Cho ket noi...` và `Pool : 50 luong`.
4. Máy 2: `2-Client.cmd 192.168.1.2` → phải vào được màn hình đăng nhập.
5. Máy 2 đăng ký tài khoản học sinh → vào lớp → **bấm Bật Mic**.
6. Máy chủ mở thêm client, đăng nhập **giáo viên** → tạo lớp → lấy mã 6 số.
7. Máy 2 nhập mã → vào lớp → giáo viên bấm **Chia sẻ màn hình**.
8. **Kiểm 3 thứ, ghi lại kết quả từng thứ:** (a) máy 2 thấy màn hình máy chủ?
   (b) máy 2 nói → máy chủ nghe? (c) máy chủ nói → máy 2 nghe?

**Nếu bước 4 hỏng:** 90% là Wi-Fi bật **client isolation**. Cách xử: điện thoại phát Wi-Fi
(4G) → cả hai máy nối vào đó. Đừng mất thời gian mở tường lửa thêm — máy đã có rule sẵn cho
`java.exe` (đã kiểm 27/10).

**Ghi vào đâu:** tạo `docs/do-dac/2may-20261028.md`, mỗi bước một dòng đạt/không + giờ chạy.
Đây là **bằng chứng duy nhất** cho câu "nhóm đã chạy nhiều máy thật".

**Chụp ảnh:** màn hình máy 2 đang hiện màn hình máy chủ → lưu `docs/do-dac/anh-nen/2may-chia-se-man-hinh.png`.
Đây sẽ là ảnh đẹp nhất trong báo cáo.

---

## 4. P0-B · Thí nghiệm 4.2 — giữ khoá vs nhả khoá

Đây là thí nghiệm biến một câu lý thuyết thành một con số. **Chỉ mình Minh làm được**
vì nó sửa `Room.java` — file của TV1.

### 4.1 Cơ chế (hiểu trước khi làm)

Bản B (đang có, `Room.java:115`):

```java
List<ClientHandler> targets = snapshotMembers();   // khoá → chép → nhả
for (ClientHandler c : targets) { c.sendFrame(...); }   // gửi NGOÀI khoá
```

Bản A (thí nghiệm) — gửi **trong** khoá:

```java
lock.lock();
try {
    for (ClientHandler c : members) { c.sendFrame(...); }   // gửi TRONG khoá
} finally { lock.unlock(); }
```

### 4.2 Các bước (theo đúng `README.md` 6.2)

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

> Lặp lại **đúng 3 lần** mỗi bản, mỗi lần 30 giây. Con số đưa vào bảng là **trung vị** của 3 lần
> — đừng lấy lần đẹp nhất. Lưu ý: độ trễ chỉ đúng khi bên gửi và bên nhận chạy trên cùng một máy.
>
> **Ghi chú:** Bản A cần thiết kế riêng — xem `KE_HOACH_REFACTOR.md` mục 11, câu hỏi Q2.

### 4.3 Bảng phải điền (chép vào `docs/do-dac/thi-nghiem-4-2.md`)

| Cấu hình | Học sinh bình thường nhận được | Frame/giây thực tế | Độ trễ p50/p95 (ms) | Frame bị bỏ |
|---|---|---|---|---|
| **Bản A** — giữ khoá khi gửi | … | … | … | 0 |
| **Bản B** — nhả khoá trước khi gửi | … | … | … | > 0 |

### 4.4 Dự đoán kết quả — và điều sẽ rút ra

Ở 1 máy, mạng là loopback nên gửi rất nhanh, chênh lệch có thể **không ấn tượng**.
Nếu vậy, chốt luận điểm bằng **một con số khác** đã có sẵn: `Room.averageLockHoldMicros()`
(T0Demo in ra ở dòng `:171`). Bản A sẽ có thời gian giữ khoá **lớn hơn nhiều lần** bản B.

> **Đây chính là điều đáng nói trước hội đồng:** "trên loopback gửi rất nhanh nên chênh lệch
> nhỏ; cái đáng đo là **thời gian giữ khoá** — nó quyết định client khác có bị chặn hay không.
> Trên mạng thật, mỗi lần gửi mất 2–20 ms, nên bản A chặn cả lớp 2–20 ms cho mỗi frame."
>
> Nói được câu này = chứng minh đã hiểu, mạnh hơn mọi con số đẹp.

### 4.5 Dọn dẹp (đừng bỏ qua)

```bash
git checkout main
git branch -D exp/hold-lock        # hoặc giữ nhánh để chụp ảnh diff cho báo cáo
```

---

## 5. P1 · TV3 bổ sung nhật ký — nhắn Lực hôm nay

`docs/nhat-ky-loi.md` có 10 mục: **TV1 = 7, TV2 = 3, TV3 = 0**. Đây là rủi ro thật: nếu hội
đồng hỏi Lực "phần em có lỗi nào?" mà không có gì thì cả nhóm bị trừ.

**Nhắn Lực cụ thể, đừng nhắn chung:** 2 lỗi có thật, chỉ Lực biết nguyên nhân:

1. `ServerMain.java` — Lực sửa 5 dòng để chuyển sang SQLite. Lỗi gì đã gặp?
   (`No suitable driver found`? `path` sai? `journal`?)
2. `ClassroomUI.java` — merge `27ecf3a` sau khi PR #4 đã merge ⇒ **code chết**. Nguyên nhân
   là quy trình Git (push muộn) — chính Lực ghi lại thì thành bài học nhóm, không thành lỗi.

**Mẫu ghi đã có sẵn ở cuối file** (`nhat-ky-loi.md:400`): `## [TV?] NN — Tên lỗi ngắn gọn`.

---

## 6. P2 · Chương 4.3 — dàn ý 6 mục

Đừng viết từ trang trắng. Dàn ý bám đúng 4 tầng thầy Hà yêu cầu:

| Mục | Nội dung | Lấy từ đâu |
|---|---|---|
| 4.3.1 | Kiến trúc: TCP 9000 (điều khiển) + UDP 9001 (thoại) — **vì sao tách hai đường** | `README.md` mục 4 |
| 4.3.2 | Hợp đồng: khung 4B+1B, bảng 19 loại gói | `PacketUtils.java:14`, `PacketType.java` |
| 4.3.3 | Đa luồng: bể 50, 2 luồng/client, luật vàng sao chép-nhả-gửi | `README.md` 5.1–5.4 |
| 4.3.4 | Đánh đổi: hàng đợi 2 frame drop-oldest; khoá theo lớp không khoá toàn cục | `README.md` 5.2, 5.4 |
| 4.3.5 | **Đo đạc** — bảng 4.2 + bảng `LoadTest` + tỉ lệ mất gói T0Demo | `README.md` 6.1, 6.2 |
| 4.3.6 | Hạn chế & hướng nâng cấp — **chỉ nêu cái thật, đừng nêu cho có** | mục 7 dưới |

**Nguồn viết:** mỗi mục 4.3.x phải có **một con số hoặc một đường dẫn code**. Viết không có
dẫn chứng là bị hỏi lại.

---

## 7. Nâng cấp — chỉ sau khi P0 + P1 xong

Xếp theo **tỉ lệ điểm/công sức**, không theo độ hấp dẫn:

| # | Nâng cấp | Điểm được gì | Công | Có nên? |
|---|---|---|---|---|
| 1 | **Họp phòng nhiều lớp cùng lúc** — kiểm `LoadTest` 3 lớp × 20 người, chứng minh khoá theo lớp hoạt động | Rất cao: đây là điểm "đồng bộ" có thật | 1 buổi đo | ✅ **Nên** — rẻ, chứng minh đúng thứ đề bài đòi |
| 2 | **Ghi âm buổi học** — relay ghi ra file WAV | Trung bình, dễ "trình diễn" | ½ ngày | ⚠️ Chỉ làm nếu còn ≥ 3 ngày |
| 3 | **Kick học sinh / khoá phòng** | Thấp về kỹ thuật | 1 ngày | ❌ Không cần |
| 4 | **Chuyển sang WebRTC** | Rất cao nhưng **phá toàn bộ kiến trúc** | Nhiều tuần | ❌ Tuyệt đối không, sát hạn |
| 5 | **Mã hoá TLS** | Trung bình | 2 ngày, dễ hỏng dây | ❌ Không cần |

**Quan trọng:** mỗi nâng cấp phải hỏi trước **"nó chứng minh điều gì trong đề bài?"**.
Đề bài đòi *"đa luồng và đồng bộ trong gửi/nhận âm thanh và hình ảnh"* → nâng cấp #1 chứng
minh đúng câu đó; #3, #5 thì không.

---

## 8. Hạn chế — nói thẳng, đừng giấu

Ba điều này **nên** được ghi trong báo cáo, vì hội đồng sẽ tự thấy nếu mình không nói:

1. ~~**`DatabaseManager` không còn là interface** như thiết kế ban đầu — CSDL gắn cứng trong
   `ServerMain`. Đã ghi ở `README.md` 3.1.~~ → **Đã khắc phục** trong commit
   `refactor(db): DatabaseManager thanh interface, SqliteDatabaseManager, xoa InMemory [TV1 sua file cua TV3]`
   (đã tách lại interface `DatabaseManager` và lớp `SqliteDatabaseManager` đúng bảng 1.5.5, xoá `InMemoryDatabaseManager`, test dùng SQLite `:memory:`).
2. **Không có xác thực phiên** — sau `LOGIN_RES`, server tin client là ai mãi mãi; mất kết nối
   thì phải đăng nhập lại.
3. **Chưa mã hoá** — mật khẩu băm SHA-256 phía client, nhưng đường truyền là plaintext.

Ghi 3 điều này + **một câu hướng sửa** cho mỗi điều = mục 4.3.6 đủ điểm, không bị hỏi vặn.

---

## 9. Lịch 3 tuần (đợt 2 còn lại)

| Tuần | Việc chính | Kết quả phải có |
|---|---|---|
| **Tuần này (28/10 – 2/11)** | P0-A + P0-B + P1 | `2may-20261028.md` · `thi-nghiem-4-2.md` · `nhat-ky-loi.md` có mục TV3 |
| Tuần sau (3/11 – 9/11) | P2 chương 4.3 + nâng cấp #1 | Báo cáo đủ 6 mục, bảng 3 lớp × 20 người |
| Tuần cuối (10/11 – 16/11) | Slide + tổng duyệt | Slide + chạy lại toàn bộ 1 lần, ghi giờ |

**Không dồn.** Minh đã nhắc nhiều lần: không nén khối lượng các tuần. Ba tuần là đủ nếu
tuần này làm xong P0.
