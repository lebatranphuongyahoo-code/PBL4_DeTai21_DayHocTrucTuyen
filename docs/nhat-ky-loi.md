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

## [TV2] 08 — Relay được 300 gói thoại nhưng "0 người nhận"

**Hiện tượng.** Bài tự kiểm Chặng 2 dựng lớp 1 giáo viên + 1 học sinh rồi cho giáo viên nói
250 gói. Kết quả:
```
[audio] da relay 300 goi | 0 nguoi nhan | 10.1s | 0.19 MB
[stats] luong dang song=15 | client da ket noi=2 | lop mo=1 | nguoi online=1
   Goi nhan duoc      : 0
   Ti le mat goi      : 0.00%
   [KHONG] nhan duoc gan du 250 goi (0)
```
Server báo relay thành công, **không** đếm gói rác nào, tỉ lệ mất gói 0,00% — nhưng người nghe
nhận **0 gói**. Nhìn qua thì y hệt một lỗi mạng khó hiểu.

**Nguyên nhân.** Ba dấu hiệu trong log chỉ thẳng vào nguyên nhân thật:
`0 nguoi nhan` (**danh sách người nhận rỗng**), `nguoi online=1` (**lớp chỉ có 1 người**), và
`doc 1 goi` ở dòng đóng kết nối của client thứ hai (**server chỉ đọc được 1 gói từ học sinh**).

Nguyên nhân là **lỗi của bài đo**: giáo viên được `login()`, còn học sinh thì **không**. Bài test
chỉ gọi `login(teacher, ...)` rồi gọi `joinRoom(student, ...)` thẳng. Phía server,
`handleJoinRoom()` bắt đầu bằng `requireLogin()` — nó thất bại nên **không thêm học sinh vào lớp
và không đăng ký cổng UDP** của học sinh. Vì vậy `Room` chỉ có một thành viên, và server trừ
người gửi (chính là thành viên duy nhất đó) ⇒ **danh sách người nhận còn 0**.

Điều làm lỗi này khó thấy: `joinRoom()` gọi `conn.await(JOIN_ROOM_RES, 3000)` nhưng **bỏ qua giá
trị trả về**. `await()` trả `null` khi hết thời gian chờ, nên thất bại bị nuốt im lặng.

**Cách sửa.** Cho học sinh đăng nhập, và — quan trọng hơn — **bắt mọi mã trả về phải được kiểm
tra**. Hai hàm đổi từ `void` sang trả `boolean`, đọc byte trạng thái đầu payload (`1` = thành công):

```java
private static boolean login(TestClient.Conn conn, String user, String role) throws Exception {
    ...
    if (!isStatusOk(conn.await(PacketType.REGISTER_RES, 2000))) return false;
    ...
    return isStatusOk(conn.await(PacketType.LOGIN_RES, 2000));
}

private static boolean isStatusOk(Packet res) {
    if (res == null) return false;              // het thoi gian cho = that bai
    try { return PacketUtils.payloadReader(res.getPayload()).readByte() == 1; }
    catch (Exception e) { return false; }
}
```

Nơi gọi giờ kiểm tra ngay tại chỗ, nên lỗi hiện ra đúng dòng đúng việc:
```java
check("hoc sinh dang ky + dang nhap", login(student, "hs-hoang", Protocol.ROLE_STUDENT));
check("hoc sinh vao lop duoc",          joinRoom(student, roomId, studentAudio.getLocalPort()));
```
Sau khi sửa: **300 gói nhận được, mất 0,00%, sai thứ tự 0, 1 người nhận**.

**Bài học.** Đây **cùng một loại lỗi với TV1-01** (tạo lớp rồi quên vào lớp) — và cùng một bài
học: **gọi một hàm bất đồng bộ mà không kiểm tra kết quả trả về là tự che lỗi của chính mình**.
Dấu hiệu nhận biết đáng nhớ: khi "gửi thành công" nhưng "không ai nhận được", hãy nghi
**danh sách người nhận** trước khi nghi mạng. Cách kiểm tra nhanh nhất là in **số người trong
lớp** — nếu nó là 1 trong khi phải là 2 thì vấn đề nằm ở luồng nghiệp vụ, không phải ở tầng mạng.

---

## [TV2] 09 — `NullPointerException` khi dừng rồi bật lại chia sẻ màn hình

**Hiện tượng.** Bài tự kiểm Chặng 3 chạy đoạn bỏ frame trùng (dừng capturer rồi tạo cái mới),
log in ra:
```
[screen] bo qua frame loi: java.lang.NullPointerException: Cannot invoke
"javax.imageio.ImageWriter.setOutput(Object)" because "this.jpegWriter" is null
[screen] Da dung chia se | chup 40 frame | gui 39 | bo trung 1
```
Đáng chú ý là **chương trình vẫn chạy tiếp** — frame lỗi bị bỏ và frame sau vẫn gửi. Nhưng cứ mỗi
lần dừng là mất đúng một frame kèm một dòng lỗi đáng sợ trong log.

**Nguyên nhân.** **Tranh chấp giữa hai luồng** (race condition) kinh điển. Trong `stop()`:

```java
running = false;                    // (1) chi dat co, KHONG cho luong chup thoat
if (thread != null) { thread.interrupt(); thread = null; }
if (jpegWriter != null) {
    jpegWriter.dispose();           // (2) giai phong tai nguyen
    jpegWriter = null;              // (3) xoa tham chieu
}
```

`stop()` **không chờ** luồng chụp thoát ra. Nếu luồng chụp đã đi qua bước kiểm tra
`while (running)` (đọc `running == true` ngay trước khi `stop()` đổi nó) thì nó vẫn tiếp tục chạy
hết thân vòng lặp, tới `encodeJpeg()` — và lúc đó `jpegWriter` vừa bị gán `null` ở bước (3).
Sai một nhịp thời gian là nổ.

**Cách sửa.** Hai lớp phòng thủ. Thứ nhất, `stop()` **không** giải phóng writer nữa — giữ lại để
lần `start()` sau dùng tiếp, đúng mục đích tối ưu "khởi tạo bộ nén một lần" của cả lớp:

```java
// KHONG dispose() writer o day — xem giai thich o encodeJpeg().
// Writer duoc giu lai de lan start() sau dung tiep.
```

Thứ hai, `encodeJpeg()` tự phục hồi thay vì ném lỗi, phòng trường hợp lớp lắp ghép gọi
`dispose()` từ bên ngoài:

```java
if (jpegWriter == null) {
    initWriter();               // tu phuc hoi thay vi nem NPE
}
```

**Bài học.** Hai điều rút ra. (1) **Cờ `volatile` không phải cơ chế đồng bộ** — nó chỉ bảo đảm
*nhìn thấy* giá trị mới, không bảo đảm luồng kia đã *dừng*. Muốn giải phóng tài nguyên an toàn
thì phải chờ luồng thoát (join/`CountDownLatch`) hoặc **đừng giải phóng** — ở đây chọn cách thứ
hai vì writer dùng lại được. (2) Việc bọc `catch (RuntimeException)` quanh thân vòng lặp đã cứu
cả tính năng: nếu để NPE nổi lên tới `run()` thì luồng chụp **chết hẳn** và giáo viên mất chức
năng chia sẻ màn hình giữa buổi học mà không hiểu vì sao. Một frame lỗi **không được** làm chết
một tính năng.

---

## [TV2] 10 — Bỏ frame trùng "tiết kiệm 0%" vì so hash tuyệt đối

**Hiện tượng.** Bài tự kiểm Chặng 3 đặt màn hình đứng yên 4 giây (40 frame) ở chế độ bỏ frame
trùng, kỳ vọng phần lớn frame bị bỏ. Kết quả:
```
Chup 40 frame | gui 40 | bo trung 0
Tiet kiem duoc 0.0% luu luong khi man hinh dung yen
[KHONG] co bo frame trung khi man hinh dung yen (0 frame)
[KHONG] phan tram bo trung > 50% khi khong thao tac (0%)
```
Không bỏ được **một frame nào**. Tính năng trông như không hoạt động.

**Nguyên nhân.** Cách làm đầu tiên là băm thưa pixel rồi so **hash có bằng nhau tuyệt đối**:

```java
int hash = fastHash(frame);
if (skipDuplicates && hash == lastFrameHash) { bo qua; }
```

Cách này **thất bại trên máy thật**. Để kiểm chứng, tôi chụp 6 lần liên tiếp **cùng một màn hình
đứng yên** rồi in hash từng lần:
```
  lan 0: hash = -1045487786
  lan 1: hash =  1762558328     <-- khac!
  lan 2: hash =  1645794410     <-- khac!
  ...
```
**Không có hai lần nào trùng hash.** Nguyên nhân: `Robot.createScreenCapture()` trên Windows đi
qua bộ tổng hợp hình ảnh, nên hai lần chụp cùng một màn hình **vẫn ra pixel khác nhau** — do con
trỏ chuột được vẽ chồng, con trỏ soạn thảo nhấp nháy, và sai số làm tròn của bộ chuyển đổi màu.
Chỉ cần **một** pixel trong hơn 82.000 điểm lấy mẫu lệch 1 đơn vị là hash đổi hoàn toàn.
Nói cách khác: thuật toán đúng, nhưng **phép so quá nhạy với nhiễu**.

Đo tỉ lệ điểm ảnh thật sự đổi giữa hai lần chụp cho con số quyết định: **0,0012% – 0,0723%**.

**Cách sửa.** Đổi từ "so hash bằng nhau" sang **so theo ngưỡng chịu lỗi**. Lấy mẫu thưa, đếm tỉ
lệ điểm ảnh đổi *đáng kể*, chỉ coi là frame mới khi vượt ngưỡng:

```java
private static final int    PIXEL_DELTA  = 24;    // tong chenh lech RGB coi la "doi"
private static final double CHANGE_RATIO = 0.01;  // >1% diem doi thi moi gui

private boolean hasChanged(BufferedImage image) {
    if (lastSamples == null) return true;         // frame dau luon gui
    int[] now = sample(image);
    int changed = 0;
    for (int i = 0; i < now.length; i++) {
        int dr = Math.abs(((now[i] >> 16) & 0xFF) - ((lastSamples[i] >> 16) & 0xFF));
        int dg = Math.abs(((now[i] >> 8)  & 0xFF) - ((lastSamples[i] >> 8)  & 0xFF));
        int db = Math.abs(( now[i]        & 0xFF) - ( lastSamples[i]        & 0xFF));
        if (dr + dg + db > PIXEL_DELTA) changed++;
    }
    return changed > now.length * CHANGE_RATIO;
}
```

Sau khi sửa, đo lại trên cùng máy đó: nhiễu chỉ **0,0012%–0,0723%**, còn ngưỡng là **1%** —
tức **cách nhiễu 14 đến 800 lần**. Kết quả bài đo: **bỏ 39/40 frame, tiết kiệm 97,5%** (từ 0%).

**Bài học.** **Đừng bao giờ so sánh tuyệt đối dữ liệu đến từ cảm biến, màn hình, camera hay mạng
— hãy so theo ngưỡng.** Với dữ liệu "bẩn" tự nhiên, câu hỏi đúng không phải "có bằng nhau không"
mà là "khác nhau **bao nhiêu** và mức đó có ý nghĩa không". Bài học thứ hai về **cách đo**: lần
đầu tôi thử chặn `System.out` để terminal khỏi nhấp nháy — kết quả vẫn 0%, vì tiếng ồn đến từ
chính bộ tổng hợp hình ảnh chứ không từ dòng log. Sửa **nguyên nhân** (ngưỡng chịu lỗi) thì được
97,5%, còn chữa **triệu chứng** (chặn log) thì không được gì. Khi một phép đo cho kết quả vô lý,
hãy **đo lại bản thân phép đo** trước khi sửa hệ thống.

---

## [TV1] 11 — Công cụ đo gửi packet mà chưa đăng nhập, không tự dựng lớp (B0)

**Hiện tượng.** Các công cụ đo (`screen-send`, `screen-recv`…) gửi `SCREEN_START`/frame ngay khi
kết nối, server im lặng từ chối (`Ban chua dang nhap`), con số đo ra toàn 0 mà không có dòng lỗi
nào nổi bật.

**Nguyên nhân.** Tool **giả định** server đang ở trạng thái nào đó (đã có lớp, đã đăng nhập) thay
vì **tự dựng** trạng thái đó. Cùng họ lỗi với [TV2] 08: gọi bất đồng bộ mà không kiểm tra kết quả.

**Cách sửa.** `TestClient.Conn` tự `registerAndLogin`; tool gửi màn hình tự tạo lớp rồi vào lớp;
thêm đo trễ frame (thời gian từ lúc server gửi tới lúc client nhận). Mọi mã trả về đều được kiểm.

**Test.** `T0Demo` vẫn 20/20 sau khi công cụ tự dựng được toàn bộ kịch bản.
**Commit.** `55313ed fix(tools): TestClient tu dang nhap, screen-send tao lop, do tre frame (B0)`

**Bài học.** Công cụ đo phải **tự lo từ đầu tới cuối**: kết nối → đăng ký/đăng nhập → dựng lớp →
vào lớp → đo. Bất kỳ bước nào dựa vào "ai đó đã làm sẵn" thì bài đo sẽ đỏ trên máy người khác.

---

## [TV1] 12 — Tạo lớp xong server restart là lớp biến mất (B1)

**Hiện tượng.** Test đỏ: `expected: <0> but was: <1>` (lớp tồn tại trong RAM ngay sau khi tạo) và
`expected: <true> but was: <false>` (không vào lại được lớp đã có người từng vào rồi rời hết).

**Nguyên nhân.** `handleCreateRoom` cũ mở lớp trong RAM **ngay lúc tạo**:

```java
int newRoomId = db.createRoom(roomName, c.getUsername());
rooms.createRoom(newRoomId, roomName, c.getUsername());   // sai: RAM chỉ là bản sao tạm
```

Lớp nằm trong `RoomManager` (bộ nhớ) chứ không chỉ trong SQLite ⇒ restart server mất lớp; và khi
người cuối rời, `removeIfEmpty` xoá Room khỏi RAM ⇒ không ai vào lại được nữa dù CSDL vẫn còn.

**Cách sửa.** Tách hai tầng: **CSDL là nguồn sự thật, RAM chỉ là phiên chạy**. `handleCreateRoom`
chỉ gọi `db.createRoom(...)`; `handleJoinRoom` tra `db.findRoom(id)` rồi `rooms.openIfAbsent(info)`
— nếu Room chưa có trong RAM thì tạo mới từ `RoomInfo`.

**Test.** `createRoomDoesNotOpenRoomInMemory`, `roomCanBeRejoinedAfterEveryoneLeft`,
`roomSurvivesServerRestart` — cả 3 đỏ trước khi sửa, xanh sau khi sửa.
**Commit.** `782705b fix(server): mo lai lop tu CSDL khi vao, khong mo luc tao (B1)`

**Bài học.** Bộ nhớ in-memory và CSDL phải có một **quy tắc rõ**: CSDL lưu trạng thái lâu dài,
RAM chỉ cache phiên hiện hành. Mỗi thao tác phải hỏi "lưu ở đâu?" trước khi viết code.

---

## [TV1] 13 — "Phòng ma": vào lớp đúng lúc lớp bị dọn thì thành viên lạc vào Room đã bị xoá (B2)

**Hiện tượng.** Test đồng bộ 8 luồng × 2000 lần vào/rời bắt được `ghostViolations > 0`: có thành
viên nằm trong một `Room` mà `RoomManager.get(roomId)` trả `null` — người đó ở trong lớp nhưng
không ai gửi được gì cho họ.

**Nguyên nhân.** Tranh chấp thời gian: luồng A `rooms.get(id)` lấy được Room; luồng B (người cuối)
rời lớp → `removeIfEmpty` xoá Room khỏi map; luồng A `room.addMember(...)` **thành công** — thêm
người vào một Room đã bị gỡ khỏi map.

**Cách sửa.** Room tự giữ **cờ `closed`** do `lock` bảo vệ; thêm `tryAddMember` trả `false` khi
closed; `closeIfEmpty()` kiểm tra rỗng **và** đóng trong **cùng một vùng găng**:

```java
boolean closeIfEmpty() {
    lock.lock();
    try { if (members.isEmpty()) { closed = true; return true; } return false; }
    finally { lock.unlock(); }
}
```

`handleJoinRoom` dùng `openIfAbsent` + `tryAddMember` trong vòng thử lại (50 lần,
`Thread.yield()` giữa các lần) — hoặc thêm vào thành công, hoặc nhận "Lop dang dong, vui long thu lai".

**Test.** `RoomTest` (4 test: addRemoveCount, closeIfEmptyOnlyWhenEmpty, closedRoomRejectsMembers,
snapshotIsCopy) + `RoomManagerTest` (3 test, trong đó `concurrentJoinLeaveNeverCreatesGhostRoom`
chạy 8 luồng × 2000 lần, `@Timeout(30)`) — sau sửa `ghostViolations == 0`.
**Commit.** `5e887ca fix(server): co closed trong Room chong phong ma khi vao/roi dong thoi (B2)`

**Bài học.** "Kiểm tra rồi làm" gồm **hai bước** chỉ khi không có khoá. Muốn hai việc là một thì
phải đưa cả hai vào **cùng một vùng găng**. Đây là bài toán check-then-act kinh điển.

---

## [TV1] 14 — Học sinh tự bật chia sẻ màn hình vì server chỉ tin `roomId` client gửi (B3)

**Hiện tượng.** Test đỏ: học sinh gửi `SCREEN_STOP` và giáo viên khác gửi `SCREEN_START` đều
không nhận được gói `ERROR` nào — server im lặng làm theo.

**Nguyên nhân.** Server xử lý `SCREEN_START` chỉ dựa vào `roomId` trong payload — client tự khai
mình ở lớp nào, server không đối chiếu **vai trò** và **quyền chủ lớp**:

```java
Room room = rooms.get(packet.getRoomId());   // tin client 100%
room.setSharing(true);                        // ai cũng bật được
```

**Cách sửa.** "Không bao giờ tin client": thêm `Room.isOwner(username)`; `ScreenHandler` kiểm
`ROLE_TEACHER.equals(c.getRole()) && room.isOwner(c.getUsername())` cho START/STOP, frame chỉ
relay khi `isOwner`. Người bị từ chối nhận `ERROR "Chi giao vien cua lop duoc ..."`.

**Test.** `studentCannotStopTeacherShare`, `otherTeacherCannotShare`, `ownerCanStopShare` — đỏ
trước, xanh sau. T0 mục 3 (học sinh bị từ chối) vẫn DAT.
**Commit.** `6704406 fix(server): chi giao vien chu lop duoc bat/tat/gui man hinh (B3)`

**Bài học.** Quyền hạn phải được kiểm tra **tại nơi dữ liệu vào server**, dựa trên phiên đăng
nhập phía server — không bao giờ dựa vào trường client tự gửi. Kiểm tra ở UI chỉ là trang trí.

---

## [TV1] 15 — Giáo viên rời lớp thì cờ chia sẻ kẹt `true`, cả lớp treo màn hình (B4)

**Hiện tượng.** Test đỏ: giáo viên đang chia sẻ, ngắt kết nối — `SCREEN_STOP` không bao giờ được
phát, `room.isSharing()` vẫn `true`, danh sách học sinh mãi chờ frame.

**Nguyên nhân.** Đường rời lớp chỉ `removeMember` + `broadcastUserList`, **không ai tắt cờ
sharing**. Cờ chỉ được tắt bởi chính giáo viên gửi `SCREEN_STOP` — nhưng họ đã ngắt kết nối.

**Cách sửa.** Trong `leaveCurrentRoom`, sau khi `removeMember` thành công:

```java
if (room.isSharing() && room.isOwner(c.getUsername())) {
    room.setSharing(false);
    room.broadcastControl(PacketType.SCREEN_STOP,
            new Messages.ScreenNotice(room.getRoomId(),
                    "Giao vien da roi lop, dung chia se man hinh").encode());
}
```

**Test.** `teacherDisconnectStopsShare` — đỏ trước, xanh sau; client học sinh nhận được
`SCREEN_STOP` trong 3 giây sau khi giáo viên `close()`.
**Commit.** `5b9f0c8 fix(server): giao vien roi lop thi tat chia se (B4)`

**Bài học.** Mỗi **trạng thái có chủ** (cờ sharing thuộc về giáo viên) phải có **đường dọn dẹp khi
chủ biến mất** — ngắt kết nối cũng là một cách "rời lớp", và thường là cách phổ biến nhất giữa
buổi học (mạngWifi rớt).

---

## [TV1] 16 — Tin nhắn chat không có tên người gửi, còn dính khoảng trắng thừa (B5)

**Hiện tượng.** Test đỏ: người nhận thấy `  xin chao ` nguyên bản (giữ khoảng trắng client gửi)
và không biết ai nói — mọi tin nhắn trông như hệ thống tự nói.

**Nguyên nhân.** `handleChat` chuyển tiếp nguyên payload, không tiền tố, không kiểm roomId, không
giới hạn độ dài — ai cũng có thể gửi chat hộ lớp khác bằng cách khai `roomId` khác.

**Cách sửa.** Decode `ChatText` → từ chối nếu `msg.roomId() != room.getRoomId()`; `strip()` văn
bản, bỏ qua nếu rỗng, chặn trên 1000 ký tự; phát kèm tên:

```java
room.broadcastControl(PacketType.CHAT_TEXT,
        new Messages.ChatText(room.getRoomId(), c.getUsername() + ": " + text).encode());
```

**Test.** `chatIsPrefixedWithSender` (`"  xin chao "` → `"minh: xin chao"`),
`chatToOtherRoomRejected` — đỏ trước, xanh sau.
**Commit.** `d5dd10c fix(server): chat kem ten nguoi gui (B5)`

**Bài học.** Server là nơi **chuẩn hoá nội dung phát đi** — trim, giới hạn độ dài, gắn nguồn. Để
client tự xử rồi relay nguyên bản là mỗi client một chuẩn, và kẻ xấu gửi gì cả lớp nhận nấy.

---

## [TV1] 17 — Relay UDP chuyển tiếp gói của người lạ và của người chưa bật mic (B6)

**Hiện tượng.** Test đỏ: một `DatagramSocket` lạ (không trong lớp) gửi 20 gói 648 byte đúng
roomId → học sinh **nhận đủ 20 gói**; thành viên chưa gửi `AUDIO_ON` nói → vẫn được relay.

**Nguyên nhân.** `AudioRelayServer.handle` sau khi tra phòng chỉ **trừ người gửi** rồi phát cho
tất cả thành viên — không kiểm tra "người gửi có phải thành viên không" và "đã bật mic chưa":

```java
// cũ: chỉ trừ người gửi, ai gửi cũng relay
if (member.isSameUdpSource(sourceAddress, sourcePort)) { continue; }
```

**Cách sửa.** Tìm người gửi trong danh sách thành viên đã sao chép dưới khoá; không tìm thấy hoặc
chưa bật mic thì **bỏ gói và đếm**:

```java
if (sender == null || !sender.isMicOn()) {
    packetsRejected.incrementAndGet();
    return;
}
```

Thêm `getPacketsRejected()` để đo; javadoc đầu lớp thêm bước "kiểm tra người gửi là thành viên
đang bật mic".

**Test.** `udpFromNonMemberIsNotRelayed` (20 gói lạ → `SocketTimeoutException`, rejected ≥ 20),
`udpFromMemberWithoutMicIsNotRelayed` (10 gói → rejected ≥ 10); T0 mục 5 (đường thoại hợp lệ)
vẫn 20/20 — chứng minh không làm hỏng chức năng thật.
**Commit.** `ccc3034 fix(server): relay UDP chi nhan tu thanh vien da bat mic (B6)`

**Bài học.** UDP không có kết nối nên **mỗi gói đến đều là người lạ cho tới khi chứng minh được
ngược lại**. Đồng thời bật mic phải là trạng thái phía server (`micOn`) — client tự nói không
tạo ra quyền nói.

---

## [TV1] 18 — Đăng ký nhận tên `"a"` và hash `"abc"`, tạo lớp tên 101 ký tự, lệnh không cần đăng nhập (B7)

**Hiện tượng.** Test đỏ: `REGISTER_REQ` với tên `"a"`, `"co dau cach"`, 33 ký tự, hoặc hash
`"abc"` đều **thành công**; `CREATE_ROOM_REQ` tên 101 ký tự thành công; `LEAVE_ROOM` gửi từ kết
nối chưa đăng nhập không bị từ chối.

**Nguyên nhân.** `handleRegister` chỉ kiểm vai trò, tin tuyệt đối tên và hash; `handleCreateRoom`
chỉ kiểm tên rỗng; bốn lệnh `LEAVE_ROOM`, `AUDIO_ON`, `AUDIO_OFF`, `SCREEN_STOP` không có
`requireLogin` — client chưa đăng nhập vẫn thao tác được.

**Cách sửa.** Hai regex ở `AuthHandler`:

```java
private static final Pattern USERNAME   = Pattern.compile("[A-Za-z0-9_.]{2,32}");
private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
```

Tên lớp: rỗng hoặc > 100 ký tự → `"Ten lop phai tu 1 den 100 ky tu"`. Thêm `requireLogin` vào cả
4 handler trên; `handleScreenFrame` chưa đăng nhập thì bỏ qua im lặng. Lưu ý kéo theo:
`TV2Chang2Test` dùng tên `"gv-hoang"`/`"hs-hoang"` có dấu gạch ngang — không khớp regex, đổi
thành `"gv_hoang"`/`"hs_hoang"`.

**Test.** `registerRejectsBadUsername` (3 tên xấu), `registerRejectsBadHash`, `createRoomRejectsLongName`,
`leaveWithoutLoginGetsError` (`"Ban chua dang nhap"`) — đỏ trước, xanh sau.
**Commit.** `e4949ba fix(server): kiem tra dau vao dang ky/tao lop, requireLogin day du (B7)`

**Bài học.** Mọi chuỗi từ client là **đầu vào không tin được** — quy tắc: đăng ký chặn ở regex,
tạo tài nguyên chặn ở độ dài, **mọi lệnh** đều bắt đầu bằng `requireLogin`. Khi siết quy tắc mới,
phải chạy lại **tất cả** công cụ đo — chữ ký hợp lệ cũ có thể thành bất hợp lệ.

---

## [TV1] 19 — T0 đỏ ngẫu nhiên ở mục 7 "dọn lớp rỗng" (flaky)

**Hiện tượng.** Đôi lần chạy, mục cuối báo `[KHONG] khong con lop nao trong bo nho (rooms=1)`
— chạy lại lại DAT. Lỗi "ma" không tái hiện được mỗi lần.

**Nguyên nhân.** Việc dọn lớp chạy trên luồng của server **sau khi** socket đóng (đọc EOF →
`cleanup` → `leaveCurrentRoom` → `removeIfEmpty`). Bài test đọc `rooms.size()` **ngay sau**
`close()`, nên căn vào tốc độ máy: máy nhanh thấy 0, máy chậm thấy 1. Lỗi không có thật —
chỉ là bài đo thiếu sự chờ đợi.

**Cách sửa.** Chờ tối đa 3 giây thay vì đọc đúng một lần:

```java
long deadline = System.currentTimeMillis() + 3000;
while (rooms.size() > 0 && System.currentTimeMillis() < deadline) { Thread.sleep(20); }
```

**Test.** `T0Demo` chạy lặp lại nhiều lần liên tiếp, 20/20 ổn định.
**Commit.** `ec5b667 fix(tools): T0Demo cho don lop toi da 3s truoc khi check, chong flaky`

**Bài học.** Test đỏ ngẫu nhiên phải được coi là **lỗi của bài test** cho tới khi chứng minh
ngược lại. Với các sự kiện chạy trên luồng khác, hãy chờ có giới hạn (deadline) thay vì assert
ngay — assert ngay sau khi gửi lệnh bất đồng bộ là đo nhịp thời gian, không phải đo logic.

---

## Mẫu ghi cho lỗi tiếp theo

```
## [TV?] NN — Tên lỗi ngắn gọn

**Hiện tượng.** (copy nguyên văn dòng log / thông báo lỗi)

**Nguyên nhân.** (vì sao nó xảy ra — không phải "chỗ này sai" mà là CƠ CHẾ nào gây ra)

**Cách sửa.** (đoạn code trước/sau)

**Bài học.** (lần sau gặp tình huống tương tự thì làm gì)
```
