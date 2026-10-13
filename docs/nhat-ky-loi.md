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

## Mẫu ghi cho lỗi tiếp theo

```
## [TV?] NN — Tên lỗi ngắn gọn

**Hiện tượng.** (copy nguyên văn dòng log / thông báo lỗi)

**Nguyên nhân.** (vì sao nó xảy ra — không phải "chỗ này sai" mà là CƠ CHẾ nào gây ra)

**Cách sửa.** (đoạn code trước/sau)

**Bài học.** (lần sau gặp tình huống tương tự thì làm gì)
```
