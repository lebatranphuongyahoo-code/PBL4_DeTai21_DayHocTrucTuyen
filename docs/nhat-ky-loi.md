# Nhật ký lỗi

> Mỗi lỗi gặp phải ghi lại: **hiện tượng → nguyên nhân → cách sửa**. Đây là bằng chứng nhóm
> làm thật, thứ không thể giả được. Cả 3 người cùng thêm vào file này.

---

## [TV1] 01 — Giáo viên tạo lớp nhưng không chia sẻ được màn hình

**Hiện tượng.** Bài kiểm thử T0 báo:
```
[KHONG] giao vien duoc phep chia se man hinh
[KHONG] co sharing cua lop da bat
[pool-1-thread-1] TU CHOI chia se man hinh: ... 
```
Nhưng log server lại không hề in dòng "TU CHOI" nào cả — server im lặng.

**Nguyên nhân.** Không phải lỗi phân quyền. Lỗi ở **bài test**: giáo viên gọi `CREATE_ROOM_REQ`
rồi… không gọi `JOIN_ROOM_REQ`. Trong `ClientHandler.handleScreenStart()` có hai lớp kiểm tra:

```java
if (!requireLogin()) return;     // da qua
Room room = currentRoom();       // roomId == -1  ->  room == null
if (room == null) { sendError("Ban chua o trong lop nao"); return; }
```

`roomId` vẫn là `-1` nên hàm thoát sớm, và `sendError` gửi gói ERROR — nhưng bài test chỉ chờ
`SCREEN_START` hoặc `ERROR` với timeout 2 giây, còn gói ERROR thì… có gửi thật.

**Cách sửa.** Cho giáo viên vào lớp ngay sau khi tạo:
```java
joinRoom(teacher, roomId);   // tao lop KHONG tu dong dua nguoi tao vao lop
```

**Bài học.** "Tạo lớp" và "vào lớp" là **hai việc khác nhau**. Đây là hành vi đúng thiết kế —
nếu tạo lớp tự động thêm người tạo vào lớp thì không thể tạo lớp hộ người khác được. Nhưng
giao diện client (TV3) phải nhớ gọi `JOIN_ROOM_REQ` sau khi tạo lớp, nếu không giáo viên sẽ
"ở trong lớp mà không ở trong lớp".

---

## [TV1] 02 — `String.format("%.2f", ...)` với số nguyên làm chương trình chết

**Hiện tượng.**
```
Exception in thread "main" java.util.IllegalFormatConversionException: f != java.lang.Integer
    at com.pbl4.tools.T0Demo.runScenario(T0Demo.java:166)
```
Cả bài kiểm thử chết ở mục 5, nên **mục 6 và 7 không bao giờ chạy** — hai cổng kiểm tra cuối
bị bỏ qua mà không hề báo.

**Nguyên nhân.** Mảng kết quả đo thoại khai báo `int[]`, nên `audio[1]` là `int`, nhưng định
dạng lại dùng `%.2f` (dành cho số thực).

**Cách sửa.** Tỉ lệ mất gói đã được làm tròn về số nguyên phần trăm rồi, nên dùng `%d%%`:
```java
check("ti le mat goi < 5% (" + audio[1] + "%)", audio[1] < 5);
```

**Bài học.** Một dòng `String.format` sai kiểu làm **mất luôn phần kiểm thử phía sau**. Khi
viết công cụ test, phải kiểm tra rằng nó chạy hết tới dòng "KET QUA" ở cuối — nếu không thấy
dòng tổng kết thì bài test đã chết giữa đường, đừng tin các dòng `[DAT]` phía trên.

---

## [TV1] 03 — Số liệu in ra `0,06 MB` thay vì `0.06 MB`

**Hiện tượng.** Log thống kê thoại in:
```
[audio] da relay 100 goi | 2 nguoi gan | 11,8s | 0,06 MB
```
Trong khi tài liệu (và mọi bảng số liệu trong báo cáo) ghi `0.97 MB` với **dấu chấm**.

**Nguyên nhân.** JVM lấy locale của hệ điều hành — máy đặt tiếng Việt nên `String.format`
dùng dấu phẩy làm dấu thập phân. Cùng một chương trình chạy trên máy bạn khác sẽ in khác nhau.

**Cách sửa.** Chốt locale ngay dòng đầu của mỗi `main`:
```java
Locale.setDefault(Locale.US);
```
Đã thêm vào cả 5 điểm khởi chạy: `ServerMain`, `TestClient`, `T0Demo`, `LoadTest`,
`PacketRoundTripTest`.

**Bài học.** Số liệu trong báo cáo phải **tái lập được trên máy người khác**. Phụ thuộc vào
locale của hệ điều hành là một lỗi tiềm ẩn rất khó phát hiện — nó không làm chương trình sai,
chỉ làm kết quả *trông* khác đi.

---

## [TV1] 04 — Báo động rò rỉ luồng sai (đo bằng `ThreadMXBean.getThreadCount()`)

**Hiện tượng.** Bài đo tải in ra:
```
So luong nen (chua co client nao): 10
10 client -> So luong: 43
Dang ngat tat ca client...
So luong sau khi ngat het: 21 (nen = 10)
==> CANH BAO: co dau hieu ro ri
```
Nghe như rò rỉ 11 luồng. Nhưng kiểm tra kỹ thì **không có rò rỉ nào**.

**Nguyên nhân.** `ThreadMXBean.getThreadCount()` đếm **tất cả** luồng của JVM, kể cả luồng
nội bộ: GC (G1 có nhiều luồng concurrent), trình biên dịch JIT (C1/C2), Reference Handler,
Common-Cleaner… Những luồng này được **tạo ra trong lúc chạy tải** và **không bao giờ kết thúc**.
Vì vậy tổng số luồng không bao giờ quay về mức đo ban đầu.

**Cách sửa.** Đếm riêng theo **tiền tố tên luồng của mình**:
```java
private static int countThreads(String... prefixes) {
    int count = 0;
    for (Thread t : Thread.getAllStackTraces().keySet()) {
        for (String p : prefixes) {
            if (t.getName().startsWith(p)) { count++; break; }
        }
    }
    return count;
}
countThreads("pool-");          // luong thread pool
countThreads("srv-writer-");    // luong ghi cua tung client
```
Sau khi sửa:
```
10 client -> pool=11 | srv-writer=11
Sau khi ngat het: pool=11 (khong tang) | srv-writer=0 (da ve 0)
[DAT] luong ghi cua client tra ve 0 sau khi ngat (khong ro ri luong)
```

**Bài học.** Đây là bài học lớn nhất của chặng 4: **cách đo quan trọng không kém con số**.
Nếu tin vào phép đo sai, nhóm sẽ đi "sửa" một lỗi không tồn tại. Khi trình bày số liệu với hội
đồng, phải nói rõ **đo cái gì** — chứ không chỉ đưa ra một con số.

---

## [TV1] 05 — `-Dexec.mainClass` bị bỏ qua, Maven luôn chạy `ServerMain`

**Hiện tượng.**
```bash
mvn exec:java -Dexec.mainClass=com.pbl4.tools.PacketRoundTripTest
```
…nhưng Maven lại khởi động server và treo ở dòng "Cho ket noi...".

**Nguyên nhân.** Trong `pom.xml`, `mainClass` được ghi **cứng** trong `<configuration>`:
```xml
<configuration><mainClass>com.pbl4.server.ServerMain</mainClass></configuration>
```
Giá trị trong `<configuration>` **đè lên** tham số dòng lệnh `-Dexec.mainClass`.

**Cách sửa.** Đưa nó thành **property** để dòng lệnh ghi đè được:
```xml
<properties>
  <exec.mainClass>com.pbl4.server.ServerMain</exec.mainClass>
</properties>
...
<configuration><mainClass>${exec.mainClass}</mainClass></configuration>
```
Bây giờ `mvn exec:java` chạy server, còn `mvn exec:java -Dexec.mainClass=...` chạy đúng class
được chỉ định.

**Bài học.** Cấu hình cứng trong `pom.xml` làm mất tính linh hoạt. Quy tắc: giá trị nào người
dùng có thể muốn đổi thì để thành **property**, đừng ghi cứng.

---

## [TV1] 06 — Giả lập "client mạng chậm" không hoạt động

**Hiện tượng.** Chạy thí nghiệm bước 3.3 với `screen-recv <roomId> 30 200` (đọc chậm 200 ms
mỗi frame), nhưng server báo **bỏ 0 frame** — trong khi đáng lẽ client chậm phải bị bỏ frame.

**Nguyên nhân.** Lớp `Conn` của `TestClient` có một **luồng đọc nền** liên tục hút gói từ socket
vào hàng đợi trong bộ nhớ. Nên dù vòng lặp chính có `Thread.sleep(200)` thì luồng nền vẫn đọc
hết dữ liệu → đệm socket **không bao giờ đầy** → luồng ghi của server không bị chặn → hàng đợi
frame không đầy → **không có frame nào bị bỏ**. Thí nghiệm đo một thứ khác với thứ mình tưởng.

**Cách sửa.** Thêm chế độ kết nối **không có luồng đọc nền**, để chương trình tự đọc từ socket
và thật sự đọc chậm:
```java
Conn(boolean autoRead)                    // false = khong co luong nen
Packet readRawOrNull(int timeoutMs)       // doc thang tu socket
```
Chế độ đọc chậm bắt buộc dùng `new Conn(false)`. Kết quả sau khi sửa:
```
[DAT] hoc sinh binh thuong nhan duoc frame (30 frame)
[DAT] client mang cham nhan duoc it frame hon (8 frame)
[DAT] server BO FRAME cua client cham (dropped=22)
[DAT] client binh thuong KHONG bi bo frame (dropped=0)
```

**Bài học.** Muốn chứng minh cơ chế chống nghẽn thì **phải chặn ở đúng tầng gây ra nghẽn** —
tầng socket. Chặn ở tầng vòng lặp ứng dụng trong khi có một luồng khác đang hút dữ liệu là
**đo sai hoàn toàn**, mà kết quả lại trông rất hợp lý nên rất dễ tin nhầm.

---

## [TV1] 07 — Git Bash làm hỏng đường dẫn `@/tmp/srcs.txt`

**Hiện tượng.**
```bash
javac @/tmp/srcs.txt
# error: file not found: \tmp\srcs.txt
```

**Nguyên nhân.** Git Bash tự chuyển `/tmp/...` thành đường dẫn kiểu Windows `\tmp\...` trước khi
truyền cho `javac`.

**Cách sửa.** Đặt file danh sách nguồn **trong thư mục dự án** và dùng đường dẫn tương đối:
```bash
find src -name "*.java" > target/srcs.txt
javac -encoding UTF-8 -d target/classes @target/srcs.txt
```

**Bài học.** Trên Git Bash, đừng truyền đường dẫn kiểu Unix (`/tmp`, `/c/...`) cho các công cụ
Windows khi tham số bắt đầu bằng `@` hoặc `-`. Dùng đường dẫn tương đối trong thư mục dự án.

---

## Mẫu ghi cho lỗi tiếp theo

```
## [TV?] NN — Tên lỗi ngắn gọn

**Hiện tượng.** (copy nguyên văn dòng log / thông báo lỗi)

**Nguyên nhân.** (vì sao nó xảy ra — không phải "chỗ này sai" mà là CƠ CHẾ nào gây ra)

**Cách sửa.** (đoạn code trước/sau)

**Bài học.** (lần sau gặp tình huống tương tự thì làm gì)
```
