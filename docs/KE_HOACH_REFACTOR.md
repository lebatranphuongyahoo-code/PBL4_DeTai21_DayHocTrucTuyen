# KẾ HOẠCH REFACTOR — PBL4 Đề tài 21 (Dạy học trực tuyến)

> Viết ngày 01/11/2026. Dựa trên việc đọc toàn bộ 33 file Java ở `main = ec82a22`
> và hai tài liệu: *ĐA_PBL4_HĐH&MMT.pdf* (quy định đồ án) và *Danh_Sách_Đề_Tài* (đề 21).
>
> **Người đọc là agent thực thi (workbuddy).** Làm đúng thứ tự, đúng phạm vi. Không tự sáng tạo
> thêm. Gặp điểm nào kế hoạch không nói rõ → **dừng lại và hỏi Minh**, không đoán.

---

## 0. Đọc trước khi làm bất cứ gì

### 0.1 Mục tiêu

1. **Cấu trúc chuẩn**: mỗi lớp một trách nhiệm, định dạng payload nằm ở **một chỗ duy nhất**,
   CSDL đi qua interface, test tự động chạy bằng `mvn verify`.
2. **Sửa lỗi thật** tìm thấy khi đọc code (mục 2) — mỗi lỗi có test chứng minh.
3. **Không phá số liệu đã đo**: thí nghiệm 4.2, `LoadTest`, `T0Demo`, bảng nén 4 mức vẫn còn giá trị
   sau refactor.

### 0.2 Bối cảnh thời gian — vì sao phải cẩn thận

Bảo vệ khoảng 10–16/11. Code đang **chạy được**. Refactor sai = mất demo. Vì vậy:

- Làm trên **nhánh riêng** `refactor/chuan-hoa-cau-truc`. **Không** commit thẳng vào `main`,
  **không** push, **không** merge khi Minh chưa duyệt.
- Mỗi bước **phải xanh** cổng kiểm tra (mục 0.5) trước khi sang bước sau.
- Bước 1B (sửa `TestClient`) được thiết kế để Minh có thể cherry-pick riêng vào `main` sớm.

### 0.3 BẤT BIẾN — tuyệt đối không được đổi

| # | Bất biến | Lý do |
|---|---|---|
| I1 | Giá trị byte của 19 hằng trong `PacketType` | Hợp đồng giao thức, bảng 1.5.3 của báo cáo |
| I2 | Định dạng byte của mọi payload (trừ **nội dung chuỗi** chat ở B5) | Hợp đồng giao thức |
| I3 | Khung TCP `[4B độ dài][1B type][payload]`, `PacketUtils.encode/readPacket` | Hợp đồng |
| I4 | Gói UDP 648 byte `[roomId][seqNo][640 PCM]` | Hợp đồng |
| I5 | Mọi hằng trong `Protocol` (9000, 9001, `MAX_PAYLOAD`, pool 50, backlog 50, `FRAME_QUEUE_CAPACITY = 2`) | Số liệu đo dựa trên chúng |
| I6 | Mẫu "khoá → sao chép → nhả khoá → rồi mới gửi" trong `Room.broadcast*` và `snapshotMembers()` (cả phần đo `lockHoldNanos`) | Thí nghiệm 4.2 đo đúng chỗ này |
| I7 | `ClientHandler`: một luồng ghi `srv-writer-N`, hai hàng đợi, drop-oldest, vòng `writerLoop()` | Quyết định thiết kế 5.1, 5.2 |
| I8 | Tham số media: `JitterBuffer` 10/3, `ScreenCapturer` 10 FPS / quality 0.6 / ngưỡng dò thay đổi, `AudioConfig` | Đã chọn bằng thí nghiệm |
| I9 | Chuỗi log mà tài liệu trích dẫn: `===== SERVER DANG CHAY =====`, `Cho ket noi...`, `Pool : 50 luong`, `KET QUA: %d DAT / %d TONG`, tiền tố `[stats]`, `[audio]`, `[main]`, tên luồng `pool-*`, `srv-writer-*`, `audio-relay`, `stats` | `README`, `KICH_BAN_DEMO`, `LoadTest` dựa vào |
| I10 | Tên các lớp hiện có (trừ `DatabaseManager` ở bước 4) | Báo cáo và nhật ký trích tên lớp |
| I11 | `java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar` (không tham số) vẫn chạy **server** | Kịch bản demo |
| I12 | `java -cp <jar> com.pbl4.client.ui.LoginUI [ip] [port]` vẫn chạy client | Kịch bản demo |

### 0.4 NGOÀI PHẠM VI — không làm, kể cả khi thấy "tiện tay"

Đổi `PacketType` sang `enum` · thư viện log (SLF4J…) · TLS · token phiên · trộn âm (mixing) nhiều người
nói · nén Opus · đổi giao diện Swing ngoài phần nêu ở bước 7 · đổi tên gói/lớp · dịch comment ·
format lại file không liên quan · nâng version dependency · thêm dependency ngoài JUnit.

### 0.5 Cổng kiểm tra (chạy sau MỖI bước)

Trên Git Bash (Windows). Luôn dùng tiền tố `env -u ...` — xem `README.md` mục 1 vì sao.

```bash
cd /e/PBL4_DeTai21
env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL mvn -o -q clean verify
```

- Phải kết thúc **không lỗi**, không test nào FAIL/SKIP ngoài ý muốn.
- `-o` = offline. Cache `~/.m2` đã có JUnit 5.10.2, surefire 3.2.5 (kiểm ngày 01/11/2026: có đủ `junit-jupiter-api/engine/params`, `junit-platform-commons/engine/launcher` 1.10.2, `opentest4j` 1.3.0, `apiguardian-api` 1.1.2, plugin `compiler 3.13.0`, `resources 3.3.1`, `jar 3.4.1`). Nếu vẫn báo thiếu artifact nào thì chạy **một lần** không có `-o` để tải, rồi quay lại `-o`.
- Trước khi chạy: đảm bảo không có server nào đang giữ cổng 9000/9001
  (`netstat -ano | findstr :9000`). Test JUnit dùng cổng ngẫu nhiên nên không cần, nhưng
  `T0Demo` chạy tay thì cần.

Ở các mốc đánh dấu **[SMOKE]**, chạy thêm:

```bash
env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL mvn -o -q clean package
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.T0Demo
# Ky vong dong cuoi: KET QUA: 20 DAT / 20 TONG   (sau buoc 6 co the la 21/21 — xem 6.x)
```

### 0.6 Quy ước commit

- Mỗi bước con = **một commit**. Không gộp hai bước vào một commit.
- Theo đúng kiểu commit của repo: tiếng Việt **không dấu**, tiền tố conventional:
  `refactor(server): ...`, `fix(server): ...`, `test: ...`, `build: ...`, `docs: ...`.
- Quy tắc sở hữu file của nhóm (`README.md` dòng 16): sửa file thuộc TV2/TV3 thì ghi trong dòng tiêu đề
  commit, ví dụ `refactor(client): dung Messages trong HomeUI [TV1 sua file cua TV3]`.
  Bảng sở hữu: `common/`, `server/` (trừ `db/`), `tools/` = TV1 · `client/media/` = TV2 ·
  `server/db/`, `client/net/`, `client/ui/` = TV3.
- Không sửa lịch sử (`rebase -i`, `push -f`, `reset --hard` trên commit đã có).

### 0.7 Việc agent KHÔNG được tự quyết (phải hỏi Minh)

- Merge vào `main`, push, tạo PR.
- Xoá/di chuyển thư mục `.git.broken-20261027` (xem bước 0.3).
- Làm **bước 10** (tính năng ghi danh) — chỉ làm khi Minh ghi rõ "duyệt bước 10".
- Bất kỳ test cũ nào phải nới điều kiện để qua.

---

## 1. Hiện trạng (đã kiểm chứng bằng cách đọc code)

### 1.1 Cấu trúc hiện tại

```
src/main/java/com/pbl4/
├── common/   Protocol, PacketType (19 hằng), Packet, PacketUtils, RoomInfo, Sha256
├── server/   ServerMain, ClientHandler (605 dòng), Room, RoomManager, AudioRelayServer
│   └── db/   DatabaseManager (LỚP CỤ THỂ, SQLite), InMemoryDatabaseManager (extends, mở SQLite thừa)
├── client/
│   ├── net/  ServerConnection, ClientListener
│   ├── media/ AudioConfig, AudioRecorder, AudioPlayer, JitterBuffer, MediaStats,
│   │          ScreenCapturer, ScreenReceiver
│   └── ui/   LoginUI, HomeUI, ClassroomUI (547 dòng), ScreenPanel
└── tools/    TestClient, T0Demo, LoadTest, PacketRoundTripTest, TV2Chang1/2/3Test
src/test/     (KHÔNG CÓ — chưa có test JUnit nào)
```

### 1.2 Vấn đề cấu trúc

| # | Vấn đề | Chỗ |
|---|---|---|
| S1 | Định dạng payload bị **viết tay lặp lại** ở server, 3 màn hình UI và 4 tool (`readRoomId` có 3 bản, `login()` có 3 bản…). Sửa một trường là phải sửa 8 chỗ. | `ClientHandler`, `HomeUI`, `ClassroomUI`, `LoginUI`, `tools/*` |
| S2 | `ClientHandler` vừa là tầng truyền tải, vừa giữ phiên, vừa chứa toàn bộ nghiệp vụ. | `ClientHandler.java` |
| S3 | `DatabaseManager` là lớp cụ thể; `InMemoryDatabaseManager` kế thừa và gọi `super(":memory:")` — mở một kết nối SQLite không dùng. Lệch thiết kế bảng 1.5.5 (README 3.1 tự ghi nhận). | `server/db/` |
| S4 | Không có test tự động thật; "73 kiểm thử" là các `main()` in chữ ĐẠT, phải chạy tay. | `tools/` |
| S5 | Server chỉ chạy được trên cổng cố định 9000/9001 → không thể chạy test song song với server thật; tool phải `Thread.sleep(600)` chờ server mở cổng. | `ServerMain`, `AudioRelayServer` |
| S6 | `TestClient` dùng biến `static host/port` toàn cục (`setTarget`). | `TestClient.java:50` |
| S7 | Socket UDP của client bị tạo ở `HomeUI` nhưng đóng ở `ClassroomUI`; hai interface adapter thừa trong `ClassroomUI`. | `HomeUI:66`, `ClassroomUI:50-187` |
| S8 | Code chết: `ClientHandler.rawOutput()` (còn phá luật một-luồng-ghi), `ScreenPanel.setFrame`, `ScreenCapturer.sameBytes/screenDiagonalInches`. | |
| S9 | Không có cách chạy client bằng `java -jar`. | `pom.xml` |

### 1.3 Lỗi chức năng (sẽ sửa ở bước 6 — mỗi lỗi có test)

| # | Lỗi | Bằng chứng trong code | Hậu quả |
|---|---|---|---|
| **B0** | **`TestClient` không chạy được các lệnh thí nghiệm**: mỗi lần gọi là một kết nối mới **chưa đăng nhập**; `screen-recv`/`screen-send` gửi `JOIN_ROOM_REQ`/`SCREEN_START` mà không login. `screen-send` còn không vào lớp. | `TestClient.java:224-266`; server `requireLogin()` ở `ClientHandler:379`, `:488` | **Quy trình thí nghiệm 4.2 trong `KE_HOACH_HOAN_THIEN.md` mục 4.2 và README 2/6.2 không thể chạy được.** Ví dụ `join 1` còn sai vì mã lớp là 6 chữ số. |
| B1 | Lớp bị xoá khỏi RAM khi người cuối rời, nhưng **vẫn hiện trong danh sách** (lấy từ DB) và **không vào lại được nữa** ("Lop khong ton tai hoac da dong"). Sau khi khởi động lại server, mọi lớp cũ cũng vậy. | `handleRoomList` đọc DB, `handleJoinRoom` chỉ tra RAM (`ClientHandler:386`) | Giáo viên rời lớp một lần là lớp "chết" |
| B2 | Tranh chấp vào/rời: luồng A lấy `Room` rồi mới `addMember`, giữa chừng luồng B rời và `removeIfEmpty` xoá phòng → A nằm trong một phòng "ma" không còn trong `RoomManager`. Lớp tạo ra nhưng không ai vào thì nằm mãi trong RAM. | `ClientHandler:386-401`, `RoomManager.removeIfEmpty` | Rò rỉ + mất thành viên |
| B3 | **Học sinh dừng được màn hình giáo viên**: `SCREEN_STOP` không kiểm tra vai trò. Giáo viên **khác** (không phải chủ lớp) vào lớp cũng chia sẻ được. | `handleScreenStop` `ClientHandler:510` | Trái nguyên tắc "không tin client" mà chính README nêu |
| B4 | Giáo viên mất kết nối khi đang chia sẻ → cờ `sharing` của lớp vẫn `true`, học sinh đứng hình mãi. | `leaveCurrentRoom` không đụng `sharing` | |
| B5 | Chat không có tên người gửi — server chuyển nguyên payload. | `handleChat` `ClientHandler:474` | Lớp học không biết ai nói gì |
| B6 | Relay UDP nhận gói từ **bất kỳ ai** biết `roomId`, kể cả người ngoài lớp hoặc chưa bật mic. | `AudioRelayServer.handle` | Chèn tiếng vào lớp người khác |
| B7 | Server không kiểm tra đầu vào khi đăng ký/tạo lớp (tên rỗng, hash sai độ dài…); một số lệnh không `requireLogin`. | `handleRegister`, `handleLeaveRoom`, `handleAudioOn/Off` | |
| B8 | Bấm "Rời phòng" **đóng luôn kết nối TCP** — phải chạy lại client và đăng nhập lại. | `ClassroomUI.windowClosed` gọi `connection.close()` | Trải nghiệm demo kém |

### 1.4 Đối chiếu đề 21 (Danh sách đề tài, trang 6–7)

| Yêu cầu đề 21 | Trạng thái |
|---|---|
| Tạo tài khoản, đăng nhập | Có |
| Tạo lớp học | Có |
| **Thêm người học vào lớp** | **Thiếu** — học sinh tự vào bằng mã, giáo viên không "thêm" được ai. Bước 10 (tuỳ chọn, cần Minh duyệt). |
| Chia sẻ màn hình người dạy | Có (TCP) |
| Gửi/nhận âm thanh realtime | Có (UDP) |
| Server: quản lý tài khoản, lớp học | Có |
| Server: nhận âm thanh và hình ảnh, chia sẻ cho người học | Có (relay) |
| Server: đa luồng + đồng bộ khi gửi/nhận | Có — được giữ nguyên (I6, I7) |

---

## 2. Cấu trúc đích (sau bước 9)

```
src/main/java/com/pbl4/
├── Launcher.java                      MỚI  java -jar app.jar [server|client] ...
├── common/
│   ├── Protocol, PacketType, Packet, PacketUtils, RoomInfo, Sha256   (giữ nguyên API)
│   └── msg/Messages.java              MỚI  mọi định dạng payload, một chỗ duy nhất
├── server/
│   ├── ServerMain.java                sửa  nhận ServerConfig, bind cổng trong constructor
│   ├── ServerConfig.java              MỚI  record (tcpPort, udpPort, dbPath)
│   ├── ServerContext.java             MỚI  record giữ rooms, db, các handler
│   ├── ClientHandler.java             sửa  chỉ còn: socket, đọc, luồng ghi, hàng đợi, phiên, switch
│   ├── Room.java                      sửa  thêm cờ closed (B2) — broadcast giữ nguyên
│   ├── RoomManager.java               sửa  openIfAbsent, removeIfEmpty dùng closeIfEmpty
│   ├── AudioRelayServer.java          sửa  nhận udpPort, lọc người gửi (B6)
│   ├── handler/
│   │   ├── AuthHandler.java           MỚI  LOGIN, REGISTER
│   │   ├── RoomHandler.java           MỚI  CREATE, JOIN, LEAVE, ROOM_LIST, CHAT, rời lớp khi ngắt
│   │   ├── ScreenHandler.java         MỚI  SCREEN_START/STOP/FRAME
│   │   └── AudioHandler.java          MỚI  AUDIO_ON/OFF
│   └── db/
│       ├── DatabaseManager.java       đổi  thành INTERFACE
│       └── SqliteDatabaseManager.java MỚI  (code SQLite hiện tại chuyển sang)
│           (InMemoryDatabaseManager.java — XOÁ)
├── client/
│   ├── net/   ServerConnection, ClientListener          (không đổi)
│   ├── media/ ... + AudioSession.java  MỚI  sở hữu DatagramSocket + AudioPlayer + AudioRecorder
│   └── ui/    LoginUI, HomeUI, ClassroomUI, ScreenPanel  (dùng Messages, AudioSession)
└── tools/     TestClient (sửa B0), T0Demo, LoadTest, TV2Chang1/2/3Test
               (PacketRoundTripTest — XOÁ, thay bằng JUnit)

src/test/java/com/pbl4/
├── common/PacketUtilsTest.java
├── common/msg/MessagesTest.java
├── server/RoomTest.java
├── server/RoomManagerTest.java
├── server/db/SqliteDatabaseManagerTest.java
├── client/media/JitterBufferTest.java
├── client/media/AudioConfigTest.java
└── integration/
    ├── T0ScenarioTest.java
    ├── LoadSmokeTest.java
    └── ServerRulesTest.java           kiểm B1–B7
```

---

## 3. Lộ trình — tổng quan

| Bước | Nội dung | Rủi ro | Ước lượng |
|---|---|---|---|
| 0 | Chuẩn bị nhánh, đo mốc ban đầu | thấp | 30 ph |
| 1 | Hạ tầng build: JUnit, pin plugin, `Launcher`, test `PacketUtils` | thấp | 1 h |
| 1B | **Sửa `TestClient` (B0)** + đo độ trễ frame — để thí nghiệm 4.2 chạy được | thấp | 1.5 h |
| 2 | Server cấu hình được cổng; tool chạy được như hàm; JUnit chạy T0 + LoadTest | trung bình | 2 h |
| 3 | `Messages` — gom định dạng payload; chuyển server/UI/tool sang dùng | trung bình | 3 h |
| 4 | `DatabaseManager` → interface + `SqliteDatabaseManager` | thấp | 1 h |
| 5 | Tách `ClientHandler` thành 4 handler | trung bình | 2.5 h |
| 6 | Sửa lỗi B1–B7 (mỗi lỗi một commit + test) | trung bình | 3 h |
| 7 | Client: `AudioSession`, "Rời phòng" về trang chủ (B8), xoá code chết | trung bình | 2 h |
| 8 | Tài liệu: README, `docs/KIEN_TRUC.md`, kịch bản demo, nhật ký lỗi | thấp | 2 h |
| 9 | Kiểm tra cuối + smoke GUI | — | 1 h |
| 10 | *(Tuỳ chọn, cần duyệt)* Ghi danh học sinh vào lớp | cao | 4 h |

Thứ tự là **bắt buộc**: bước 2 dựng lưới test trước khi bước 3–7 đụng vào logic.

---

## BƯỚC 0 — Chuẩn bị

**0.1** Tạo nhánh:

```bash
cd /e/PBL4_DeTai21
git status            # phai sach; neu khong sach -> DUNG, hoi Minh
git checkout -b refactor/chuan-hoa-cau-truc
```

**0.2** Đo mốc ban đầu. Chạy và chép **nguyên văn** đầu ra vào `docs/refactor/baseline.md`:

```bash
java -version 2>&1 | head -1
env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL mvn -v | head -1
env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL mvn -o -q clean package
java -cp target/classes com.pbl4.tools.PacketRoundTripTest
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.T0Demo
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.LoadTest 10 3
```

Nếu `T0Demo` **không** ra `20 DAT / 20 TONG` ngay từ đầu → **DỪNG, báo Minh**, không refactor
trên một mốc đang hỏng.

**0.3** Kiểm thư mục rác (chỉ kiểm, không xoá):

```bash
git ls-files .git.broken-20261027 | head -3
git ls-files pbl4.db
```

Ghi kết quả vào `baseline.md`. Nếu `.git.broken-20261027` **đang được git theo dõi**: báo Minh,
đề xuất `git rm -r --cached .git.broken-20261027` + thêm dòng `.git.broken-*/` vào `.gitignore`.
Chỉ làm khi Minh đồng ý.

**Commit:** `docs: ghi moc ban dau truoc refactor`

---

## BƯỚC 1 — Hạ tầng build

### 1.1 `pom.xml`

Thêm vào `<properties>`:

```xml
<junit.version>5.10.2</junit.version>
```

Thêm vào `<dependencies>` (sau `sqlite-jdbc`):

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>${junit.version}</version>
    <scope>test</scope>
</dependency>
```

Thêm vào `<plugins>` (đặt **trước** `exec-maven-plugin`). Các version này đã có sẵn trong `~/.m2`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <version>3.3.1</version>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <!-- Test khong mo cua so, khong dung mic/loa/man hinh. -->
        <argLine>-Djava.awt.headless=true -Duser.language=en -Duser.country=US</argLine>
    </configuration>
</plugin>
```

Ở `maven-jar-plugin`: thêm `<version>3.4.1</version>` và đổi `<mainClass>` thành `com.pbl4.Launcher`.
Sửa comment phía trên shade plugin: dòng "jar KHONG chay duoc client bang -jar" → thay bằng
"`java -jar <jar> client [ip] [port]` de chay client (xem Launcher)".

**Không** đổi `<exec.mainClass>` (vẫn `ServerMain`).

### 1.2 `src/main/java/com/pbl4/Launcher.java` (mới)

```java
package com.pbl4;

import com.pbl4.client.ui.LoginUI;
import com.pbl4.server.ServerMain;

import java.util.Arrays;

/**
 * Điểm vào duy nhất của jar.
 *
 * <pre>
 *   java -jar app.jar                         -> server (giữ tương thích kịch bản demo cũ)
 *   java -jar app.jar server [tcp] [udp] [db] -> server
 *   java -jar app.jar client [ip] [port]      -> giao diện client
 * </pre>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            ServerMain.main(args);
            return;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "server" -> ServerMain.main(rest);
            case "client" -> LoginUI.main(rest);
            default -> {
                System.out.println("Dung: java -jar <jar> [server [tcp] [udp] [db] | client [ip] [port]]");
                System.exit(2);
            }
        }
    }
}
```

> Ở bước 1, `ServerMain.main` chỉ đọc `args[0]` = cổng TCP. Bước 2 mới mở rộng. Không sao.

### 1.3 `src/test/java/com/pbl4/common/PacketUtilsTest.java` (mới)

Chuyển **5 ca** của `tools/PacketRoundTripTest.java` sang JUnit 5, giữ nguyên dữ liệu vào/ra
của từng ca (đọc file đó để lấy đúng giá trị):

| Test method | Lấy từ |
|---|---|
| `basicRoundTrip()` | `testBasicRoundTrip` |
| `emptyPayload()` | `testEmptyPayload` |
| `largePayload()` | `testLargePayload` |
| `backToBackPackets()` | `testBackToBackPackets` |
| `rejectsAbsurdLength()` | `testRejectsAbsurdLength` — dùng `assertThrows(IOException.class, ...)` |

Thêm 2 ca:

- `encodeRejectsOversizedPayload()`: `assertThrows(IllegalArgumentException.class, () -> PacketUtils.encode(PacketType.SCREEN_FRAME, new byte[Protocol.MAX_PAYLOAD]))`.
- `encodeLayout()`: `encode((byte)0x0D, new byte[]{7,8})` phải bằng đúng `{0,0,0,3, 0x0D, 7,8}`.

### 1.4 Xoá `tools/PacketRoundTripTest.java`

Sửa `README.md` mục 2, bước 2: thay lệnh `exec:java ...PacketRoundTripTest` bằng

```bash
mvn -q test -Dtest=PacketUtilsTest
```

và dòng kỳ vọng thành `Tests run: 7, Failures: 0`.

**Cổng 0.5.** Thêm kiểm tra tay:

```bash
env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL mvn -o -q clean package
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar          # phai in "SERVER DANG CHAY", Ctrl+C
java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar foo      # phai in dong "Dung: ..." va thoat
```

**Commit:** `build: them JUnit 5, pin plugin, Launcher cho java -jar client`

---

## BƯỚC 1B — Sửa `TestClient` để thí nghiệm 4.2 chạy được (B0)

> Bước này chỉ đụng `tools/TestClient.java` + tài liệu. Được thiết kế để cherry-pick riêng vào `main`.
> **Không** đổi chữ ký `Conn` ở bước này (bước 2 mới đổi).

### 1B.1 Tuỳ chọn đăng nhập `--as`

Cú pháp mới (giữ cú pháp cũ hợp lệ):

```
TestClient <host> <port> [--as user:pass:ROLE] <lenh> [tham so]
```

Trong `main()`: sau khi đọc `host`, `port`, nếu `args[2]` là `--as` thì `args[3]` là `user:pass:ROLE`,
lệnh bắt đầu từ `args[4]`.

Quy tắc đăng nhập tự động trước khi chạy lệnh:

| Lệnh | Nếu có `--as` | Nếu không có `--as` |
|---|---|---|
| `hold`, `auth`, `rooms` | đăng nhập bằng `--as` (riêng `auth` giữ hành vi cũ) | như cũ, không đăng nhập |
| `create`, `screen-send`, `screen-start`, `screen-stop`, `audio-send` | đăng nhập bằng `--as` | tự đăng nhập user `gv` + 4 số ngẫu nhiên, mật khẩu `123456`, `TEACHER` |
| `join`, `leave`, `chat`, `screen-recv`, `audio-recv` | đăng nhập bằng `--as` | tự đăng nhập user `hs` + 4 số ngẫu nhiên, mật khẩu `123456`, `STUDENT` |

"Đăng nhập" = gửi `REGISTER_REQ` (bỏ qua kết quả trùng tên) rồi `LOGIN_REQ`; nếu `LOGIN_RES` ok=0 →
in `[test] dang nhap that bai: ...` và thoát mã 1. In tên user đã dùng: `[test] dang nhap: <user> (<ROLE>)`.

Viết một hàm `private static void ensureLogin(Conn conn, String user, String pass, String role)`
và dùng lại nó trong `cmdAuth` (thay phần đăng ký/đăng nhập đang viết tay).

### 1B.2 `screen-send` tạo lớp và chờ học sinh

```
screen-send <roomId|new> <giay> [giayChoTruoc=10]
```

- `roomId = new`: gửi `CREATE_ROOM_REQ` tên `"Thi nghiem 4.2"`, đọc `roomId` từ `CREATE_ROOM_RES`,
  in **đúng một dòng** `ROOM_ID=<roomId>` (để người chạy chép sang cửa sổ học sinh).
- Sau đó **luôn** `JOIN_ROOM_REQ(roomId, 0)` và chờ `JOIN_ROOM_RES` ok=1 (sai → in lỗi, thoát 1).
- In `[test] cho <giayChoTruoc> giay de hoc sinh vao lop...`, `Thread.sleep`.
- Rồi mới `SCREEN_START` và vòng gửi như cũ.
- **Mới:** ghi thời điểm gửi vào frame: 8 byte big-endian của `System.currentTimeMillis()` tại offset 2
  (ngay sau `FF D8`), dùng `ByteBuffer.wrap(frame).putLong(2, ...)`. Bỏ dòng `frame[100] = (byte) i;`
  (timestamp đã làm mỗi frame khác nhau).

### 1B.3 `screen-recv` đo độ trễ

- Giữ thứ tự hiện tại: `JOIN_ROOM_REQ(roomId, 0)` (giờ đã đăng nhập nên thành công).
  Nếu `JOIN_ROOM_RES` ok=0 → in lý do, thoát 1.
- Với mỗi frame: `long sent = ByteBuffer.wrap(payload).getLong(2)`; `latency = now - sent`.
  Đo `now` **ngay sau khi đọc được gói, trước** `Thread.sleep(slowReadMs)`.
- Cuối phiên in thêm một dòng (giữ dòng cũ):

```
[test] do tre frame (ms): tb=%.1f | p50=%d | p95=%d | max=%d | n=%d
```

  (lưu các giá trị vào `ArrayList<Long>`, sort, p50 = phần tử `n/2`, p95 = phần tử `(int)(n*0.95)`,
  `n=0` thì in `n=0` và các số là 0.)

> Độ trễ chỉ đúng khi bên gửi và bên nhận **cùng một máy** (cùng đồng hồ). Ghi chú điều này trong
> `usage()` và trong tài liệu.

### 1B.4 `audio-send` / `audio-recv`

Không đổi logic, chỉ thêm đăng nhập tự động theo bảng 1B.1. Kiểm tra `JOIN_ROOM_RES` ok=1, sai thì thoát 1.

### 1B.5 Tài liệu

Sửa `README.md` mục 2 (ví dụ 3.3) và `docs/KE_HOACH_HOAN_THIEN.md` mục 4.2 cho đúng quy trình mới:

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

Trong `KE_HOACH_HOAN_THIEN.md` mục 4.3, thêm cột "Độ trễ p50/p95 (ms)" vào bảng phải điền.
Thêm một ghi chú **không sửa quy trình bản A**: "Bản A cần thiết kế riêng — xem `KE_HOACH_REFACTOR.md` mục 11, câu hỏi Q2."

### 1B.6 Kiểm tra

Chạy đúng 4 lệnh ở 1B.5 (server thật). Kỳ vọng: cửa sổ 3 và 4 in số frame > 0 và dòng độ trễ;
cửa sổ 4 nhận ít frame hơn cửa sổ 3; server in số frame đã bỏ khi client chậm ngắt
(`da dong ket noi | ... | bo N frame`, N > 0). Dán đầu ra vào `docs/refactor/baseline.md` mục "Sau 1B".

**Cổng 0.5.**
**Commit:** `fix(tools): TestClient tu dang nhap, screen-send tao lop, do tre frame (B0)`

---

## BƯỚC 2 — Server cấu hình được cổng, tool chạy như hàm, JUnit chạy kịch bản

Mục tiêu: dựng **lưới an toàn** trước khi refactor sâu. Hành vi server **không đổi**.

### 2.1 `server/ServerConfig.java` (mới)

```java
package com.pbl4.server;

import com.pbl4.common.Protocol;

/** Cấu hình khởi chạy server. Cổng = 0 nghĩa là để hệ điều hành chọn cổng trống (dùng cho test). */
public record ServerConfig(int tcpPort, int udpPort, String dbPath) {

    public static ServerConfig defaults() {
        return new ServerConfig(Protocol.TCP_PORT, Protocol.UDP_PORT, "pbl4.db");
    }

    /** Cổng ngẫu nhiên + CSDL trong RAM — chỉ dùng cho test và công cụ đo. */
    public static ServerConfig ephemeral() {
        return new ServerConfig(0, 0, ":memory:");
    }

    /** {@code [tcpPort] [udpPort] [dbPath]} — thiếu tham số nào thì lấy mặc định tham số đó. */
    public static ServerConfig fromArgs(String[] args) {
        ServerConfig d = defaults();
        int tcp = args.length > 0 ? Integer.parseInt(args[0]) : d.tcpPort();
        int udp = args.length > 1 ? Integer.parseInt(args[1]) : d.udpPort();
        String db = args.length > 2 ? args[2] : d.dbPath();
        return new ServerConfig(tcp, udp, db);
    }
}
```

### 2.2 `AudioRelayServer`

- Constructor đổi thành `AudioRelayServer(RoomManager rooms, int udpPort)`: `new DatagramSocket(udpPort)`.
- Thêm `public int getLocalPort() { return socket.getLocalPort(); }`.
- Dòng log `"[audio] Relay thoai dang lang nghe tren cong UDP " + Protocol.UDP_PORT` → dùng `getLocalPort()`.

### 2.3 `ServerMain`

- Constructor: `public ServerMain(ServerConfig config, RoomManager rooms, DatabaseManager db) throws IOException`.
  **Bind cả hai socket ngay trong constructor**:
  `this.serverSocket = new ServerSocket(config.tcpPort(), Protocol.SOCKET_BACKLOG);`
  `this.audioRelay = new AudioRelayServer(rooms, config.udpPort());`
  Nếu tạo `AudioRelayServer` ném lỗi thì đóng `serverSocket` trước khi ném tiếp.
- Bỏ field `port`. Thêm `public int getTcpPort() { return serverSocket.getLocalPort(); }`,
  `public int getUdpPort() { return audioRelay.getLocalPort(); }`.
- `start()`: bỏ `try (ServerSocket ss = new ServerSocket(...))`, dùng field `serverSocket`; `finally`
  vẫn `shutdownPool(); audioRelay.stop();` và đóng `serverSocket` nếu chưa đóng. Các dòng log khởi động
  giữ **nguyên văn** (I9), chỉ thay giá trị cổng bằng `getTcpPort()` / `getUdpPort()`.
- `main(String[] args)`:
  ```java
  java.util.Locale.setDefault(java.util.Locale.US);
  ServerConfig config = ServerConfig.fromArgs(args);
  DatabaseManager db = new DatabaseManager(config.dbPath());   // bước 4 đổi thành SqliteDatabaseManager
  RoomManager rooms = new RoomManager();
  ServerMain server = new ServerMain(config, rooms, db);
  Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown-hook"));
  server.start();
  ```
- `stop()`: thêm đóng CSDL sau khi đóng socket: `try { db.close(); } catch (RuntimeException ignored) { }`.
  Đảm bảo gọi `stop()` hai lần không lỗi (đã có cờ `running`).

### 2.4 `TestClient.Conn` bỏ biến toàn cục

- Xoá `private static String host`, `private static int port`, `setTarget(...)`.
- `Conn` thành `public static class Conn`, các constructor:
  `public Conn(String host, int tcpPort, int udpPort)` (autoRead = true) và
  `public Conn(String host, int tcpPort, int udpPort, boolean autoRead)`.
  Lưu `host`, `udpPort` vào field `final` để các hàm gửi UDP dùng (`InetAddress.getByName(conn.host)`,
  `conn.udpPort`) thay cho `Protocol.UDP_PORT` và `host` toàn cục.
- Đổi các method của `Conn` mà tool dùng (`send`, `await`, `poll`, `awaitEither`, `readRawOrNull`,
  `awaitRaw`, `openAudioSocket`, `close`) và các field `audioSocket`, `framesReceived` thành `public`.
- Thêm 3 tiện ích **vào `Conn`** để xoá các bản sao trong T0Demo/LoadTest/TV2Chang2Test:
  ```java
  /** REGISTER (bỏ qua trùng tên) rồi LOGIN. Ném IllegalStateException nếu đăng nhập thất bại. */
  public void registerAndLogin(String user, String password, String role) throws Exception
  /** CREATE_ROOM_REQ, trả roomId; ném IllegalStateException nếu ok=0 hoặc hết giờ. */
  public int createRoom(String name) throws Exception
  /** JOIN_ROOM_REQ(roomId, udpPort), chờ JOIN_ROOM_RES; trả true nếu ok=1. */
  public boolean join(int roomId, int udpPort) throws Exception
  ```
  Timeout chờ phản hồi: 3000 ms (giống code hiện tại).
- `TestClient.main` tạo `new Conn(host, port, Protocol.UDP_PORT, !needRaw)`. `ensureLogin` của 1B gọi
  `conn.registerAndLogin(...)`.

### 2.5 `T0Demo` và `LoadTest` thành hàm có kết quả

Cho **mỗi** lớp:

- Thêm `public record Result(int passed, int failed) { public boolean ok() { return failed == 0; } }`.
- Đổi `passCount/failCount` từ `static` thành field của một đối tượng chạy (ví dụ tạo instance
  `new T0Demo()` giữ hai bộ đếm; các hàm `check`, `section`, `runScenario`… thành method instance).
- Thêm hàm công khai:
  - `T0Demo`: `public static Result run(ServerConfig config) throws Exception`
  - `LoadTest`: `public static Result run(ServerConfig config, int maxClients, int holdSeconds) throws Exception`
- Bên trong `run`: tạo `RoomManager`, `DatabaseManager db = new DatabaseManager(config.dbPath())`
  (bước 4 đổi), `ServerMain server = new ServerMain(config, rooms, db)`, chạy `server::start` trên luồng
  `server-main` (daemon). **Bỏ** `Thread.sleep(600)` chờ mở cổng (cổng đã bind trong constructor).
  Mọi `new TestClient.Conn(...)` dùng `"127.0.0.1", server.getTcpPort(), server.getUdpPort()`.
  `finally { server.stop(); }` — giữ `Thread.sleep(400)` sau `stop()` như cũ.
- `main(String[] args)`: dựng `ServerConfig` từ tham số cũ (T0Demo: `[port]` → tcp; udp mặc định 9001;
  db `:memory:`. LoadTest: `[soClient] [soGiay] [port]`), gọi `run`, in dòng `KET QUA` / `==> DAT / TONG`
  **như cũ**, `System.exit(result.ok() ? 0 : 1)`. **Không** còn `System.exit` nào bên trong `run`.
- Thay `login()`, `joinRoom()`, `readRoomId()` riêng bằng `conn.registerAndLogin / createRoom / join`.
  Riêng `T0Demo.joinRoom` đang `openAudioSocket()` rồi gửi cổng UDP — giữ hành vi đó:
  `conn.openAudioSocket(); conn.join(roomId, conn.audioSocket.getLocalPort());`.
- Giữ nguyên **số lượng và nội dung** các `check(...)` (20 ở T0Demo, 4 ở LoadTest).
- `InMemoryDatabaseManager` **không dùng nữa** trong hai tool này (dùng SQLite `:memory:`).

### 2.6 `TV2Chang2Test`

Chỉ sửa cho biên dịch được và dùng cổng ngẫu nhiên: `new ServerMain(ServerConfig.ephemeral(), rooms,
new DatabaseManager(":memory:"))`, `Conn` mới, thay `Protocol.UDP_PORT` ở dòng gửi UDP (hiện là dòng 247)
bằng `server.getUdpPort()`; dòng 116–117 (kiểm cổng client khác 9001) đổi so sánh với `server.getUdpPort()`.
Không đổi logic kiểm tra khác. `[TV1 sua file cua TV2]` — thực ra `tools/` là của TV1, nhưng file này do TV2 viết:
ghi chú `[sua file TV2 viet]` trong commit.

### 2.7 Test JUnit tích hợp

`src/test/java/com/pbl4/integration/T0ScenarioTest.java`:

```java
@Test
@Timeout(value = 90, unit = TimeUnit.SECONDS)
void t0ScenarioPassesEveryCheck() throws Exception {
    T0Demo.Result r = T0Demo.run(ServerConfig.ephemeral());
    assertEquals(0, r.failed(), "so check KHONG DAT");
    assertEquals(20, r.passed());
}
```

`LoadSmokeTest.java`: `LoadTest.run(ServerConfig.ephemeral(), 5, 1)`, assert `failed == 0`.

> Nếu `T0ScenarioTest` chập chờn (lúc qua lúc không) vì ngưỡng thời gian: chạy lại 5 lần
> (`mvn -o -q test -Dtest=T0ScenarioTest` × 5). Chập chờn → **báo Minh**, không nới ngưỡng tự ý.

**Cổng 0.5 + [SMOKE].**
**Commit:** `refactor(server): ServerConfig, bind cong trong constructor; tool chay nhu ham; JUnit cho T0 va LoadTest`

---

## BƯỚC 3 — `Messages`: một chỗ duy nhất cho định dạng payload

### 3.1 `src/main/java/com/pbl4/common/msg/Messages.java` (mới)

Chép nguyên khối dưới đây. Mỗi record = một dạng payload. Thứ tự trường **đúng** như comment trong
`PacketType.java` và đúng code server hiện tại (đã đối chiếu).

```java
package com.pbl4.common.msg;

import com.pbl4.common.PacketUtils;
import com.pbl4.common.RoomInfo;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Định dạng payload của mọi loại gói — NGUỒN SỰ THẬT DUY NHẤT (bảng 1.5.3).
 *
 * <p>Trước đây mỗi màn hình, mỗi công cụ tự viết {@code writeUTF/readInt} theo trí nhớ; lệch một trường
 * là bên nhận đọc ra rác. Giờ server, client và công cụ đều đi qua các record dưới đây.</p>
 *
 * <p>Quy ước: {@code encode()} trả payload (KHÔNG gồm 4 byte độ dài và 1 byte type);
 * {@code decode(byte[])} ném {@link IOException} khi payload hỏng.</p>
 */
public final class Messages {

    /** Trần số phần tử của một danh sách — chặn cấp phát khổng lồ khi payload rác. */
    public static final int MAX_LIST_SIZE = 100_000;

    private Messages() {
    }

    // ---------------- Tài khoản ----------------

    /** LOGIN_REQ (0x01). */
    public record LoginRequest(String username, String passwordHash) {
        public LoginRequest {
            username = Objects.requireNonNullElse(username, "");
            passwordHash = Objects.requireNonNullElse(passwordHash, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeUTF(username); o.writeUTF(passwordHash); });
        }
        public static LoginRequest decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new LoginRequest(r.readUTF(), r.readUTF());
        }
    }

    /** LOGIN_RES (0x02). */
    public record LoginResponse(boolean ok, String message, String role) {
        public LoginResponse {
            message = Objects.requireNonNullElse(message, "");
            role = Objects.requireNonNullElse(role, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeByte(ok ? 1 : 0); o.writeUTF(message); o.writeUTF(role);
            });
        }
        public static LoginResponse decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new LoginResponse(r.readByte() != 0, r.readUTF(), r.readUTF());
        }
    }

    /** REGISTER_REQ (0x03). */
    public record RegisterRequest(String username, String passwordHash, String role) {
        public RegisterRequest {
            username = Objects.requireNonNullElse(username, "");
            passwordHash = Objects.requireNonNullElse(passwordHash, "");
            role = Objects.requireNonNullElse(role, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeUTF(username); o.writeUTF(passwordHash); o.writeUTF(role);
            });
        }
        public static RegisterRequest decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new RegisterRequest(r.readUTF(), r.readUTF(), r.readUTF());
        }
    }

    /** REGISTER_RES (0x04), JOIN_ROOM_RES (0x08) — cùng dạng {ok, message}. */
    public record StatusResponse(boolean ok, String message) {
        public StatusResponse {
            message = Objects.requireNonNullElse(message, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeByte(ok ? 1 : 0); o.writeUTF(message); });
        }
        public static StatusResponse decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new StatusResponse(r.readByte() != 0, r.readUTF());
        }
    }

    // ---------------- Phòng học ----------------

    /** CREATE_ROOM_REQ (0x05). */
    public record CreateRoomRequest(String roomName) {
        public CreateRoomRequest {
            roomName = Objects.requireNonNullElse(roomName, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> o.writeUTF(roomName));
        }
        public static CreateRoomRequest decode(byte[] p) throws IOException {
            return new CreateRoomRequest(PacketUtils.payloadReader(p).readUTF());
        }
    }

    /** CREATE_ROOM_RES (0x06). {@code roomId = -1} khi thất bại. */
    public record CreateRoomResponse(boolean ok, String message, int roomId) {
        public CreateRoomResponse {
            message = Objects.requireNonNullElse(message, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeByte(ok ? 1 : 0); o.writeUTF(message); o.writeInt(roomId);
            });
        }
        public static CreateRoomResponse decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new CreateRoomResponse(r.readByte() != 0, r.readUTF(), r.readInt());
        }
    }

    /** JOIN_ROOM_REQ (0x07). {@code udpPort} = cổng client bind để nhận thoại (0 = không nhận). */
    public record JoinRoomRequest(int roomId, int udpPort) {
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeInt(roomId); o.writeInt(udpPort); });
        }
        public static JoinRoomRequest decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new JoinRoomRequest(r.readInt(), r.readInt());
        }
    }

    /**
     * Payload chỉ có {@code roomId}: LEAVE_ROOM_REQ (0x09), SCREEN_START C→S (0x10),
     * SCREEN_STOP C→S (0x11), AUDIO_ON (0x13), AUDIO_OFF (0x14).
     *
     * <p>{@code decode} chỉ đọc 4 byte đầu và bỏ qua phần thừa — bản client cũ gửi kèm
     * cổng UDP trong AUDIO_ON, vẫn phải đọc được.</p>
     */
    public record RoomIdOnly(int roomId) {
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> o.writeInt(roomId));
        }
        public static RoomIdOnly decode(byte[] p) throws IOException {
            return new RoomIdOnly(PacketUtils.payloadReader(p).readInt());
        }
    }

    /** ROOM_LIST_RES (0x0B). ROOM_LIST_REQ (0x0A) có payload rỗng — dùng {@code new byte[0]}. */
    public record RoomList(List<RoomInfo> rooms) {
        public RoomList {
            rooms = List.copyOf(rooms);
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeInt(rooms.size());
                for (RoomInfo info : rooms) {
                    o.writeInt(info.roomId());
                    o.writeUTF(info.roomName());
                    o.writeUTF(info.teacherUsername());
                    o.writeInt(info.memberCount());
                }
            });
        }
        public static RoomList decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            int n = readCount(r);
            List<RoomInfo> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new RoomInfo(r.readInt(), r.readUTF(), r.readUTF(), r.readInt()));
            }
            return new RoomList(out);
        }
    }

    /** Một dòng trong USER_LIST. */
    public record Member(String username, String role) {
        public Member {
            username = Objects.requireNonNullElse(username, "");
            role = Objects.requireNonNullElse(role, "");
        }
    }

    /** USER_LIST (0x0C). */
    public record UserList(List<Member> members) {
        public UserList {
            members = List.copyOf(members);
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> {
                o.writeInt(members.size());
                for (Member m : members) {
                    o.writeUTF(m.username());
                    o.writeUTF(m.role());
                }
            });
        }
        public static UserList decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            int n = readCount(r);
            List<Member> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new Member(r.readUTF(), r.readUTF()));
            }
            return new UserList(out);
        }
    }

    /** CHAT_TEXT (0x0D), hai chiều. */
    public record ChatText(int roomId, String text) {
        public ChatText {
            text = Objects.requireNonNullElse(text, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeInt(roomId); o.writeUTF(text); });
        }
        public static ChatText decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new ChatText(r.readInt(), r.readUTF());
        }
    }

    // ---------------- Màn hình ----------------

    /**
     * SCREEN_START S→C (0x10): {@code text} = tên giáo viên.
     * SCREEN_STOP  S→C (0x11): {@code text} = thông báo.
     * (Chiều C→S của hai loại này dùng {@link RoomIdOnly}.) SCREEN_FRAME (0x12) là byte JPEG thô.
     */
    public record ScreenNotice(int roomId, String text) {
        public ScreenNotice {
            text = Objects.requireNonNullElse(text, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> { o.writeInt(roomId); o.writeUTF(text); });
        }
        public static ScreenNotice decode(byte[] p) throws IOException {
            DataInputStream r = PacketUtils.payloadReader(p);
            return new ScreenNotice(r.readInt(), r.readUTF());
        }
    }

    // ---------------- Lỗi ----------------

    /** ERROR (0x7F). */
    public record ErrorMessage(String message) {
        public ErrorMessage {
            message = Objects.requireNonNullElse(message, "");
        }
        public byte[] encode() {
            return PacketUtils.buildPayload(o -> o.writeUTF(message));
        }
        public static ErrorMessage decode(byte[] p) throws IOException {
            return new ErrorMessage(PacketUtils.payloadReader(p).readUTF());
        }
    }

    private static int readCount(DataInputStream r) throws IOException {
        int n = r.readInt();
        if (n < 0 || n > MAX_LIST_SIZE) {
            throw new IOException("So phan tu khong hop le: " + n);
        }
        return n;
    }
}
```

### 3.2 `MessagesTest` — bảo đảm **không đổi một byte nào trên dây** (I2)

`src/test/java/com/pbl4/common/msg/MessagesTest.java`. Hai nhóm test:

**(a) Golden bytes** — với mỗi record, so `encode()` với payload dựng **bằng tay đúng như code cũ**:

```java
@Test
void loginResponseMatchesLegacyBytes() {
    byte[] legacy = PacketUtils.buildPayload(o -> {
        o.writeByte(1); o.writeUTF("Dang nhap thanh cong"); o.writeUTF("TEACHER");
    });
    assertArrayEquals(legacy, new Messages.LoginResponse(true, "Dang nhap thanh cong", "TEACHER").encode());
}
```

Bắt buộc có đủ golden test cho: `LoginRequest`, `LoginResponse` (ok=true và ok=false với role `""`),
`RegisterRequest`, `StatusResponse`, `CreateRoomRequest`, `CreateRoomResponse` (cả `roomId=-1`),
`JoinRoomRequest`, `RoomIdOnly`, `RoomList` (0 phần tử và 2 phần tử), `UserList` (có role `""`),
`ChatText` (có chữ tiếng Việt có dấu), `ScreenNotice`, `ErrorMessage`.

**(b) Round-trip** — `X.decode(x.encode())` bằng `x` (record có `equals` sẵn) cho từng loại trên.

**(c) Payload hỏng**: `RoomList.decode` với count = -1 và count = 100_001 → `IOException`;
`RoomIdOnly.decode(new byte[]{0,0,0,5, 0,0,0x23,0x28})` trả `roomId == 5` (bỏ qua phần thừa);
`LoginRequest.decode(new byte[]{0})` → `IOException` (`EOFException` là con của `IOException`).

### 3.3 Chuyển code sang dùng `Messages` — từng file

Nguyên tắc: **thay cách dựng/đọc payload, không đổi luồng xử lý, không đổi chuỗi thông báo.**

| File | Việc |
|---|---|
| `ClientHandler` | Mọi `PacketUtils.buildPayload(o -> ...)` và `payloadReader(...)` → record tương ứng. `sendError(m)` → `sendControl(ERROR, new ErrorMessage(m).encode())`. `handleRoomList`: dựng `List<RoomInfo>` mới với `memberCount` từ `rooms.memberCount(id)` rồi `new RoomList(list).encode()`. `handleChat` tạm thời vẫn chuyển nguyên payload (B5 sửa sau) nhưng phải `ChatText.decode` trước để chặn payload rác (decode lỗi → `sendError("Tin nhan khong hop le")`, không relay). |
| `Room.buildUserListPayload()` | Dựng `List<Member>` từ snapshot (bỏ người `username == null` như cũ) → `new UserList(list).encode()`. |
| `LoginUI` | `sendAuthenticationRequest`: `LoginRequest` / `RegisterRequest`. `onPacket`: `LoginResponse.decode` cho `LOGIN_RES`, `StatusResponse.decode` cho `REGISTER_RES`; `handleResponse` nhận `boolean ok` thay cho `int ok`. |
| `HomeUI` | `ROOM_LIST_REQ` gửi `new byte[0]`; `CREATE_ROOM_REQ` → `CreateRoomRequest`; `JOIN_ROOM_REQ` → `JoinRoomRequest`; đọc bằng `RoomList`, `CreateRoomResponse`, `StatusResponse`, `ErrorMessage`. Xoá `readRoomList`. |
| `ClassroomUI` | Gửi: `RoomIdOnly` cho `AUDIO_ON` (**chỉ roomId**, bỏ int cổng UDP thừa), `AUDIO_OFF`, `SCREEN_START`, `SCREEN_STOP`, `LEAVE_ROOM_REQ`; `ChatText` cho chat. Đọc: `UserList` (hiển thị `username + " — " + role` như cũ), `ScreenNotice` cho `SCREEN_START/STOP`, `ChatText` (giữ lọc `roomId == this.roomId`), `ErrorMessage`. Xoá các hàm `readMembers/readTeacher/readScreenStopMessage/readChat/readError`. |
| `tools/TestClient`, `T0Demo`, `LoadTest`, `TV2Chang2Test` | Thay mọi payload viết tay bằng record. Xoá các `readRoomId`, `readUserCount` riêng (dùng `CreateRoomResponse.decode(...).roomId()`, `UserList.decode(...).members().size()`). `TestClient.describe()` dùng `StatusResponse`/`LoginResponse`. |

Kiểm: sau bước này, lệnh sau **chỉ** được còn kết quả trong `PacketUtils.java` và `Messages.java`:

```bash
grep -rn "buildPayload\|payloadReader" src/main/java
```

**Cổng 0.5 + [SMOKE].** Thêm smoke GUI ngắn (mục 9.2, các bước 1–8) vì UI bị sửa.

**Commit (tách 3):**
1. `refactor(common): them Messages - mot cho cho dinh dang payload + golden test`
2. `refactor(server): dung Messages trong ClientHandler va Room`
3. `refactor(client,tools): dung Messages trong UI va cong cu [TV1 sua file cua TV3]`

---

## BƯỚC 4 — `DatabaseManager` thành interface

### 4.1 `server/db/DatabaseManager.java` → interface

```java
package com.pbl4.server.db;

import com.pbl4.common.RoomInfo;

import java.util.List;
import java.util.Optional;

/**
 * Hợp đồng lưu trữ tài khoản và lớp học (bảng 1.5.5). Server chỉ biết interface này,
 * nên đổi nơi lưu (SQLite, RAM, MySQL…) không phải sửa tầng server.
 *
 * <p>Mọi phương thức phải an toàn khi nhiều luồng của thread pool gọi cùng lúc.</p>
 */
public interface DatabaseManager extends AutoCloseable {

    /** @return {@code false} chỉ khi tên đăng nhập đã tồn tại */
    boolean register(String user, String sha256Hex, String role);

    /** @return vai trò nếu đúng tên và mật khẩu, ngược lại {@code null} */
    String authenticate(String user, String sha256Hex);

    /** Tạo lớp với mã 6 chữ số chưa dùng, trả về mã đó. */
    int createRoom(String name, String teacherUser);

    /** Mọi lớp đã lưu; {@code memberCount} luôn là 0 (số online nằm trong RAM). */
    List<RoomInfo> listRooms();

    /** Lớp có mã {@code roomId}, hoặc rỗng nếu không có. {@code memberCount} = 0. */
    Optional<RoomInfo> findRoom(int roomId);

    @Override
    void close();
}
```

### 4.2 `server/db/SqliteDatabaseManager.java` (mới)

Chuyển **nguyên văn** toàn bộ thân lớp `DatabaseManager` hiện tại sang lớp mới
`public class SqliteDatabaseManager implements DatabaseManager`, đổi tên constructor, thêm `@Override`.
Thêm:

```java
@Override
public synchronized Optional<RoomInfo> findRoom(int roomId) {
    try (PreparedStatement statement = connection.prepareStatement(
            "SELECT room_id, name, teacher_user FROM Rooms WHERE room_id = ?")) {
        statement.setInt(1, roomId);
        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return Optional.empty();
            }
            return Optional.of(new RoomInfo(result.getInt("room_id"), result.getString("name"),
                    result.getString("teacher_user"), 0));
        }
    } catch (SQLException e) {
        throw new IllegalStateException("Cannot find room", e);
    }
}
```

Dùng `git mv` để giữ lịch sử: `git mv src/main/java/com/pbl4/server/db/DatabaseManager.java
src/main/java/com/pbl4/server/db/SqliteDatabaseManager.java`, sửa nội dung, rồi tạo file interface mới.

### 4.3 Xoá `InMemoryDatabaseManager.java`

Trước khi xoá: `grep -rn InMemoryDatabaseManager src` phải chỉ còn chính file đó
(bước 2 đã bỏ dùng ở tool). Thay mọi `new DatabaseManager(...)` thành `new SqliteDatabaseManager(...)`
(`ServerMain.main`, `T0Demo.run`, `LoadTest.run`, `TV2Chang2Test`).

### 4.4 `SqliteDatabaseManagerTest`

Mỗi test dùng `new SqliteDatabaseManager(":memory:")` trong `@BeforeEach`, `close()` trong `@AfterEach`.

| Test | Kỳ vọng |
|---|---|
| `registerThenAuthenticate` | `register("lan", H, "TEACHER")` = true; `authenticate("lan", H)` = `"TEACHER"` |
| `duplicateRegisterReturnsFalse` | lần 2 = false |
| `wrongPasswordReturnsNull` | `authenticate("lan", H2)` = null; user không tồn tại = null |
| `createRoomGivesSixDigitId` | tạo 20 lớp, mọi id trong [100000, 999999], không trùng |
| `listRoomsReturnsCreated` | 2 lớp → `listRooms().size() == 2`, đúng tên + giáo viên |
| `findRoom` | id vừa tạo → có, đúng tên; id 123 → `Optional.empty()` |

(`H` = `Sha256.hex("123456")`, `H2` = `Sha256.hex("x")`. Phải `register` giáo viên trước `createRoom`
vì có khoá ngoại.)

### 4.5 Tài liệu

`README.md` mục 3.1: viết lại — "Đã quay về thiết kế 1.5.5: `DatabaseManager` là interface,
`SqliteDatabaseManager` là bản cài đặt; `InMemoryDatabaseManager` đã xoá, test dùng SQLite `:memory:`."
`KE_HOACH_HOAN_THIEN.md` mục 8, hạn chế 1: đánh dấu **đã khắc phục** kèm tên commit.

**Cổng 0.5 + [SMOKE].**
**Commit:** `refactor(db): DatabaseManager thanh interface, SqliteDatabaseManager, xoa InMemory [TV1 sua file cua TV3]`

---

## BƯỚC 5 — Tách `ClientHandler`

Mục tiêu: `ClientHandler` chỉ còn **truyền tải + phiên**; nghiệp vụ sang 4 handler. Hành vi **không đổi**
(B1–B7 chưa sửa ở bước này).

### 5.1 `server/ServerContext.java` (mới)

```java
package com.pbl4.server;

import com.pbl4.server.db.DatabaseManager;
import com.pbl4.server.handler.AudioHandler;
import com.pbl4.server.handler.AuthHandler;
import com.pbl4.server.handler.RoomHandler;
import com.pbl4.server.handler.ScreenHandler;

/** Các đối tượng dùng chung cho mọi kết nối. Handler không giữ trạng thái riêng của client nào. */
public record ServerContext(RoomManager rooms, DatabaseManager db,
                            AuthHandler auth, RoomHandler room,
                            ScreenHandler screen, AudioHandler audio) {

    public static ServerContext create(RoomManager rooms, DatabaseManager db) {
        RoomHandler roomHandler = new RoomHandler(rooms, db);
        return new ServerContext(rooms, db,
                new AuthHandler(db), roomHandler,
                new ScreenHandler(rooms), new AudioHandler(rooms));
    }
}
```

`ServerMain` tạo `ServerContext.create(rooms, db)` một lần trong constructor, truyền vào
`new ClientHandler(socket, context)`.

### 5.2 `ClientHandler` sau khi tách

Giữ nguyên: field socket/in/out, hai hàng đợi, `writerLoop`, `run`, `offerFrame`, `sendControl`,
bộ đếm, mọi getter, `isSameUdpSource`, `hasUdpAddress`, `label`, `toString`, khối javadoc đầu lớp.

Đổi:

- Constructor: `public ClientHandler(Socket socket, ServerContext context)`.
- `handle(Packet)` vẫn là `switch` theo type, nhưng mỗi nhánh gọi handler:
  `case PacketType.LOGIN_REQ -> context.auth().handleLogin(this, packet);` …
  (LOGIN, REGISTER → auth · CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, ROOM_LIST, CHAT → room ·
  SCREEN_START, SCREEN_STOP, SCREEN_FRAME → screen · AUDIO_ON, AUDIO_OFF → audio). `default` giữ nguyên.
- `cleanup()`: thay `leaveCurrentRoom()` bằng `context.room().leaveCurrentRoom(this);`.
- `sendError(String)` giữ (dùng `ErrorMessage`).
- **Xoá** `rawOutput()` (S8) và import `OutputStream`.
- Thêm API công khai cho handler (đặt trong mục "API cho handler"):

```java
/** Ghi nhận đăng nhập thành công. */
public void markLoggedIn(String user, String userRole) { this.username = user; this.role = userRole; }

/** Ghi nhận đã vào lớp; địa chỉ UDP = địa chỉ IP của kết nối TCP + cổng client báo. */
public void enterRoom(int newRoomId, int clientUdpPort) {
    this.roomId = newRoomId;
    this.udpAddress = socket.getInetAddress();
    this.udpPort = clientUdpPort;
}

/** Xoá trạng thái lớp, trả về mã lớp cũ (-1 nếu không ở lớp nào). */
public int clearRoomState() {
    int previous = roomId;
    roomId = -1; udpPort = -1; udpAddress = null; micOn = false;
    return previous;
}

public void setMicOn(boolean on) { this.micOn = on; }

public boolean isLoggedIn() { return username != null; }

/** In log kèm tên luồng hiện tại — giữ đúng định dạng "[ten-luong] noi dung". */
public void log(String message) { ... (thân hàm log cũ) }
```

(`log` đổi từ `private` thành `public`. Các field phiên vẫn `private volatile`.)

### 5.3 Bốn handler — package `com.pbl4.server.handler`

Mỗi lớp `public final class`, constructor nhận phụ thuộc, các method `public void handleX(ClientHandler c, Packet p) throws IOException`
(method không cần packet thì chỉ nhận `c`). **Chép thân hàm từ `ClientHandler` hiện tại**, thay:
`username` → `c.getUsername()`, `role` → `c.getRole()`, `sendControl(...)` → `c.sendControl(...)`,
`sendError(...)` → `c.sendError(...)`, `log(...)` → `c.log(...)`, `this.username = ...; this.role = ...`
→ `c.markLoggedIn(...)`, gán `roomId/udpAddress/udpPort` → `c.enterRoom(...)`, `micOn = x` → `c.setMicOn(x)`,
`currentRoom()` → hàm tiện ích riêng trong handler `Room currentRoom(ClientHandler c)`.

| Lớp | Phụ thuộc | Method (chép từ) |
|---|---|---|
| `AuthHandler` | `DatabaseManager db` | `handleLogin` ← `handleLogin`; `handleRegister` ← `handleRegister` |
| `RoomHandler` | `RoomManager rooms`, `DatabaseManager db` | `handleCreateRoom`, `handleJoinRoom`, `handleLeaveRoom`, `handleRoomList`, `handleChat`, `public void leaveCurrentRoom(ClientHandler c)` ← `leaveCurrentRoom` (dùng `c.clearRoomState()`) |
| `ScreenHandler` | `RoomManager rooms` | `handleScreenStart`, `handleScreenStop`, `handleScreenFrame` |
| `AudioHandler` | `RoomManager rooms` | `handleAudioOn`, `handleAudioOff` |

Chuyển kèm javadoc "Bước 3.1 — không bao giờ tin client" và "Bước 3.2 — chuyển tiếp frame" sang
`ScreenHandler`. `requireLogin()` / `requireTeacher()`: viết thành `static boolean` private trong handler
cần dùng (gọi `c.isLoggedIn()`, `c.sendError(...)` với **đúng chuỗi lỗi cũ**).

### 5.4 Kiểm tra

- `ClientHandler.java` còn khoảng ≤ 330 dòng (chủ yếu là comment).
- `grep -n "db\.\|rooms\." src/main/java/com/pbl4/server/ClientHandler.java` → không còn kết quả.
- T0 / LoadTest JUnit xanh.

**Cổng 0.5 + [SMOKE].**
**Commit:** `refactor(server): tach ClientHandler thanh 4 handler, bo rawOutput`

---

## BƯỚC 6 — Sửa lỗi B1–B7

Quy tắc: **mỗi lỗi một commit**, trong commit có cả sửa code và test chứng minh. Viết test **trước**,
chạy thấy đỏ, sửa, thấy xanh.

### 6.0 Khung test `ServerRulesTest`

`src/test/java/com/pbl4/integration/ServerRulesTest.java`:

- `@BeforeEach`: `rooms = new RoomManager(); db = new SqliteDatabaseManager(":memory:");
  server = new ServerMain(ServerConfig.ephemeral(), rooms, db);` chạy `server::start` trên luồng daemon.
- `@AfterEach`: `server.stop()`, đóng mọi `Conn` đã mở.
- Hàm tiện ích `Conn client(String user, String role)` = `new Conn("127.0.0.1", server.getTcpPort(), server.getUdpPort())`
  + `registerAndLogin(user, "123456", role)`.
- Mọi test có `@Timeout(20)`.

### 6.1 B1 — Lớp vào lại được sau khi rỗng / sau khi khởi động lại

**Sửa:**

- `RoomManager`: thêm
  ```java
  /** Lấy lớp đang mở, hoặc mở lớp từ thông tin đã lưu nếu chưa mở. */
  public Room openIfAbsent(RoomInfo info) {
      return rooms.computeIfAbsent(info.roomId(),
              id -> new Room(id, info.roomName(), info.teacherUsername()));
  }
  ```
  Xoá `createRoom(int, String, String)` (sau khi sửa không còn ai gọi — kiểm bằng grep).
- `RoomHandler.handleCreateRoom`: **chỉ** `db.createRoom(...)`, **không** mở lớp trong RAM nữa
  (lớp mở khi có người vào đầu tiên ⇒ hết rò rỉ lớp tạo ra mà không ai vào). Sửa comment tương ứng.
- `RoomHandler.handleJoinRoom`: tra `db.findRoom(roomId)`; rỗng → `JOIN_ROOM_RES(false, "Lop khong ton tai hoac da dong")`
  (giữ chuỗi cũ); có → `rooms.openIfAbsent(info)` (chi tiết vòng lặp ở 6.2).
- Sửa javadoc lớp `RoomManager` đoạn "Vì sao roomId do cơ sở dữ liệu cấp" cho khớp.

**Test:**
- `roomCanBeRejoinedAfterEveryoneLeft`: GV tạo lớp, vào, rời (`LEAVE_ROOM_REQ`) → `rooms.size() == 0`;
  HS vào lớp đó → `JOIN_ROOM_RES` ok=1, `rooms.get(id).memberCount() == 1`.
- `createRoomDoesNotOpenRoomInMemory`: GV tạo lớp → `rooms.size() == 0`.
- `roomSurvivesServerRestart`: dùng file DB tạm (`@TempDir Path dir`, `dir.resolve("t.db").toString()`):
  server 1 tạo lớp rồi `stop()`; server 2 (cùng file DB, `ServerConfig(0, 0, path)`) → HS vào lớp đó ok=1.

### 6.2 B2 — Không còn phòng "ma" khi vào/rời đồng thời

**Sửa `Room`** (giữ nguyên mọi phần broadcast — I6):

```java
/** Đã đóng chưa — CHỈ đọc/ghi khi giữ lock. Lớp đã đóng không nhận thêm thành viên. */
private boolean closed;

/**
 * Thêm thành viên. Trả {@code false} nếu lớp vừa bị đóng (người cuối cùng vừa rời) —
 * khi đó bên gọi phải lấy lại lớp từ RoomManager và thử lại.
 */
public boolean tryAddMember(ClientHandler handler) {
    lock.lock();
    try {
        if (closed) {
            return false;
        }
        if (!members.contains(handler)) {
            members.add(handler);
        }
        return true;
    } finally {
        lock.unlock();
    }
}

/** Đóng lớp nếu rỗng — kiểm tra và đóng trong CÙNG một vùng găng, nên không ai chen vào giữa được. */
boolean closeIfEmpty() {
    lock.lock();
    try {
        if (members.isEmpty()) {
            closed = true;
            return true;
        }
        return false;
    } finally {
        lock.unlock();
    }
}
```

Xoá `addMember` (thay hết bằng `tryAddMember`).

**Sửa `RoomManager.removeIfEmpty`:**

```java
public boolean removeIfEmpty(int roomId) {
    Room room = rooms.get(roomId);
    if (room == null || !room.closeIfEmpty()) {
        return false;
    }
    rooms.remove(roomId, room);
    return true;
}
```

**Sửa `RoomHandler.handleJoinRoom`** (sau khi đã có `info` từ 6.1):

```java
leaveCurrentRoom(c);                       // mỗi client chỉ ở MỘT lớp (mục 1.5.4)
c.enterRoom(info.roomId(), req.udpPort()); // đặt trạng thái TRƯỚC khi thêm, để relay thấy cổng UDP ngay

Room room = null;
for (int attempt = 0; attempt < 50 && room == null; attempt++) {
    Room candidate = rooms.openIfAbsent(info);
    if (candidate.tryAddMember(c)) {
        room = candidate;
    } else {
        Thread.yield();                    // lớp cũ vừa đóng, chờ RoomManager gỡ nó ra rồi thử lại
    }
}
if (room == null) {
    c.clearRoomState();
    c.sendControl(PacketType.JOIN_ROOM_RES,
            new Messages.StatusResponse(false, "Lop dang dong, vui long thu lai").encode());
    return;
}
// ... gửi JOIN_ROOM_RES ok, log, broadcastUserList như cũ
```

**Test** — `src/test/java/com/pbl4/server/RoomTest.java` và `RoomManagerTest.java`
(dùng `new ClientHandler(new Socket(), ServerContext.create(rooms, db))` — `new Socket()` chưa kết nối
là hợp lệ, constructor không đọc ghi gì):

| Test | Kỳ vọng |
|---|---|
| `RoomTest.addRemoveCount` | thêm 2, `memberCount()==2`, bỏ 1, `==1` |
| `RoomTest.closeIfEmptyOnlyWhenEmpty` | có thành viên → false; rỗng → true |
| `RoomTest.closedRoomRejectsMembers` | sau `closeIfEmpty()==true`, `tryAddMember` = false |
| `RoomTest.snapshotIsCopy` | sửa list trả về không ảnh hưởng `memberCount()` |
| `RoomManagerTest.openIfAbsentReturnsSameInstance` | gọi 2 lần cùng id → cùng đối tượng |
| `RoomManagerTest.removeIfEmptyRemovesAndCloses` | sau remove: `get(id)==null`, đối tượng cũ `tryAddMember`=false, `openIfAbsent` trả đối tượng **mới** |
| `RoomManagerTest.concurrentJoinLeaveNeverCreatesGhostRoom` | 8 luồng, mỗi luồng một handler riêng, 2000 vòng/luồng. Mỗi vòng: vào bằng đúng vòng lặp `openIfAbsent` + `tryAddMember` ở trên (lấy được `joined`); **ngay sau khi vào**, kiểm `rooms.get(id) == joined` (khi mình còn là thành viên thì lớp không thể bị đóng, nên phải luôn đúng — sai nghĩa là đang ở phòng "ma"); rồi `joined.removeMember(h)` + `rooms.removeIfEmpty(id)`. Đếm số lần sai bằng `AtomicInteger` → phải bằng 0. Cuối cùng `rooms.size() == 0`. Bắt đầu cùng lúc bằng `CountDownLatch`, `@Timeout(30)`. |

(Các method `closeIfEmpty` là package-private nên test phải nằm cùng package `com.pbl4.server`.)

### 6.3 B3 — Chỉ giáo viên **chủ lớp** được bật/tắt/gửi màn hình

**Sửa** `Room`: thêm `public boolean isOwner(String user) { return teacherUsername.equals(user); }`.

`ScreenHandler`:

- `handleScreenStart`: thứ tự kiểm tra: đăng nhập → đang ở lớp → `ROLE_TEACHER.equals(c.getRole()) && room.isOwner(c.getUsername())`.
  Sai → log `"TU CHOI chia se man hinh: ..."` (như cũ) + `sendError("Chi giao vien cua lop duoc chia se man hinh")`.
- `handleScreenStop`: đăng nhập → đang ở lớp → chủ lớp. Sai → `sendError("Chi giao vien cua lop duoc dung chia se man hinh")`,
  **không** đổi cờ `sharing`, **không** broadcast.
- `handleScreenFrame`: thay kiểm tra role bằng `room.isOwner(c.getUsername())` (bỏ qua im lặng như cũ).

> Lưu ý: `ClassroomUI` nhận `ERROR` có chứa `"chi giao vien"` (không phân biệt hoa thường) để reset nút
> chia sẻ. Hai chuỗi mới đều bắt đầu bằng "Chi giao vien" ⇒ vẫn khớp. **Không đổi tiền tố này.**

**Test** (`ServerRulesTest`):
- `studentCannotStopTeacherShare`: GV chủ lớp + HS cùng lớp; GV `SCREEN_START` → nhận `SCREEN_START`;
  HS `SCREEN_STOP` → HS nhận `ERROR`; `rooms.get(id).isSharing()` vẫn `true`.
- `otherTeacherCannotShare`: GV2 (`TEACHER`, không phải chủ) vào lớp, `SCREEN_START` → `ERROR`, `isSharing()==false`.
- `ownerCanStopShare`: GV chủ `SCREEN_STOP` → HS nhận `SCREEN_STOP`, `isSharing()==false`.

### 6.4 B4 — Giáo viên rời/mất kết nối thì dừng chia sẻ

**Sửa** `RoomHandler.leaveCurrentRoom(c)`: sau khi `room.removeMember(c)` thành công, **trước**
`broadcastUserList()`:

```java
if (room.isSharing() && room.isOwner(c.getUsername())) {
    room.setSharing(false);
    room.broadcastControl(PacketType.SCREEN_STOP,
            new Messages.ScreenNotice(room.getRoomId(), "Giao vien da roi lop, dung chia se man hinh").encode());
    c.log("giao vien roi lop khi dang chia se -> tat chia se lop " + room.getRoomId());
}
```

**Test:**
- `teacherDisconnectStopsShare`: GV bật chia sẻ; HS trong lớp; GV `close()` socket đột ngột →
  HS nhận `SCREEN_STOP` trong 3 s; `isSharing()==false` (lớp vẫn còn vì HS ở lại).

### 6.5 B5 — Chat có tên người gửi

**Sửa** `RoomHandler.handleChat`:

1. `requireLogin`, đang ở lớp (như cũ).
2. `ChatText msg = ChatText.decode(p.getPayload())`; lỗi → `sendError("Tin nhan khong hop le")`.
3. `msg.roomId() != room.getRoomId()` → `sendError("Ban khong o trong lop " + msg.roomId())`.
4. `String text = msg.text().strip()`; rỗng → bỏ qua im lặng; dài > 1000 ký tự → `sendError("Tin nhan toi da 1000 ky tu")`.
5. `room.broadcastControl(CHAT_TEXT, new ChatText(room.getRoomId(), c.getUsername() + ": " + text).encode())`.

Định dạng byte không đổi (I2) — chỉ nội dung chuỗi có thêm tên. `ClassroomUI` không cần sửa.

**Test:** `chatIsPrefixedWithSender`: HS "minh" gửi `ChatText(id, "  xin chao ")` → GV nhận `"minh: xin chao"`.
`chatToOtherRoomRejected`: gửi roomId khác → nhận `ERROR`.

### 6.6 B6 — Relay UDP chỉ nhận từ thành viên đang bật mic

**Sửa** `AudioRelayServer.handle`: sau `targets = room.snapshotMembers()`:

```java
ClientHandler sender = null;
for (ClientHandler member : targets) {
    if (member.isSameUdpSource(sourceAddress, sourcePort)) {
        sender = member;
        break;
    }
}
if (sender == null || !sender.isMicOn()) {
    // Người ngoài lớp, hoặc chưa gửi AUDIO_ON — không chuyển tiếp.
    packetsRejected.incrementAndGet();
    return;
}
```

Vòng gửi giữ nguyên (vẫn bỏ qua `sender`). Thêm field `private final AtomicLong packetsRejected` +
getter `getPacketsRejected()`. Cập nhật javadoc đầu lớp: thêm bước "kiểm tra người gửi là thành viên đã bật mic".

> Kiểm tra trước khi sửa: mọi chỗ gửi UDP trong `tools/` đều gửi `AUDIO_ON` trước
> (`TestClient.cmdAudioSend`, `T0Demo.sendAudio`, `TV2Chang2Test.sendPcmAudio`) — đã đối chiếu là có;
> agent `grep -n "AUDIO_ON" src/main/java/com/pbl4/tools` để xác nhận lại.

**Test:**
- `udpFromNonMemberIsNotRelayed`: HS trong lớp (đã `join` với cổng UDP), một `DatagramSocket` lạ gửi 20 gói
  648 byte đúng roomId → HS không nhận gói nào trong 1 s (`setSoTimeout(1000)` → `SocketTimeoutException`);
  `server.getAudioRelay().getPacketsRejected() >= 20`.
- `udpFromMemberWithoutMicIsNotRelayed`: thành viên chưa gửi `AUDIO_ON` gửi gói → không relay.
- T0 vẫn 20/20 (đường thoại hợp lệ vẫn chạy).

### 6.7 B7 — Kiểm tra đầu vào, đăng nhập cho mọi lệnh

**Sửa** `AuthHandler.handleRegister` (sau kiểm tra role hiện có):

```java
private static final java.util.regex.Pattern USERNAME = java.util.regex.Pattern.compile("[A-Za-z0-9_.]{2,32}");
private static final java.util.regex.Pattern SHA256_HEX = java.util.regex.Pattern.compile("[0-9a-f]{64}");
```

- Tên không khớp `USERNAME` → `REGISTER_RES(false, "Ten dang nhap 2-32 ky tu: chu, so, _ hoac .")`.
- Hash không khớp `SHA256_HEX` → `REGISTER_RES(false, "Mat khau khong hop le")`.

> Trước khi sửa: `grep -rn "registerAndLogin\|\"sv\"\|\"gv\"\|\"hs\"" src` và kiểm mọi tên dùng trong tool/test
> khớp regex (hiện có `lan`, `minh`, `an`, `cham`, `sv0..`, `gv####`, `hs####` — đều khớp).
> `LoginUI` đã `trim()` tên phía client.

`RoomHandler.handleCreateRoom`: `name = req.roomName().strip()`; rỗng **hoặc** > 100 ký tự →
`CREATE_ROOM_RES(false, "Ten lop phai tu 1 den 100 ky tu", -1)` (thay chuỗi "Ten lop khong duoc de trong").

Thêm `requireLogin` vào: `handleLeaveRoom`, `handleAudioOn`, `handleAudioOff`, `handleScreenStop`
(chuỗi lỗi "Ban chua dang nhap" như cũ). `handleScreenFrame` chưa đăng nhập → bỏ qua im lặng.

**Test:** `registerRejectsBadUsername` (`"a"`, `"co dau cach"`, chuỗi 33 ký tự) → ok=false;
`registerRejectsBadHash` (`"abc"`) → ok=false; `createRoomRejectsLongName` (101 ký tự) → ok=false, roomId=-1;
`leaveWithoutLoginGetsError` → `ERROR`.

### 6.8 Sau bước 6

- `T0Demo` vẫn 20/20. **Không** thêm check vào T0Demo (giữ số 20 để khớp báo cáo/kịch bản demo).
- Ghi mỗi lỗi B0–B8 vào `docs/nhat-ky-loi.md` theo **đúng mẫu ở cuối file đó** (đọc mẫu trước),
  tiêu đề dạng `## [TV1] NN — <tên lỗi>`, nêu: triệu chứng, nguyên nhân (trích dòng code cũ), cách sửa,
  test chứng minh, commit.

**Cổng 0.5 + [SMOKE]** sau mỗi commit.
**Commit (7 cái):** `fix(server): mo lai lop tu CSDL khi vao, khong mo luc tao (B1)` ·
`fix(server): co closed trong Room chong phong ma khi vao/roi dong thoi (B2)` ·
`fix(server): chi giao vien chu lop duoc bat/tat/gui man hinh (B3)` ·
`fix(server): giao vien roi lop thi tat chia se (B4)` ·
`fix(server): chat kem ten nguoi gui (B5)` ·
`fix(server): relay UDP chi nhan tu thanh vien da bat mic (B6)` ·
`fix(server): kiem tra dau vao dang ky/tao lop, requireLogin day du (B7)`

---

## BƯỚC 7 — Client: `AudioSession`, rời phòng về trang chủ, dọn code chết

### 7.1 `client/media/AudioSession.java` (mới)

```java
package com.pbl4.client.media;

import javax.sound.sampled.LineUnavailableException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Gom DatagramSocket + AudioPlayer + AudioRecorder của MỘT lần vào lớp.
 *
 * <p>Lý do tồn tại: recorder và player BẮT BUỘC dùng chung một socket (server trừ người gửi theo
 * cặp IP:cổng — xem AudioRelayServer). Trước đây socket do HomeUI tạo nhưng ClassroomUI đóng;
 * giờ một đối tượng sở hữu trọn vòng đời.</p>
 */
public final class AudioSession implements AutoCloseable {

    private final DatagramSocket socket;
    private final AudioPlayer player;
    private AudioRecorder recorder;
    private boolean closed;

    private AudioSession(DatagramSocket socket) {
        this.socket = socket;
        this.player = new AudioPlayer(socket, new JitterBuffer(), new MediaStats());
    }

    /** Bind cổng UDP ngẫu nhiên do hệ điều hành cấp. */
    public static AudioSession open() throws SocketException {
        return new AudioSession(new DatagramSocket(0));
    }

    /** Cổng phải báo cho server trong JOIN_ROOM_REQ. */
    public int localPort() {
        return socket.getLocalPort();
    }

    /** Mở loa ngay khi vào lớp (xem ghi chú a4d9580 trong README 3.2). */
    public synchronized void startPlayback() throws LineUnavailableException {
        player.start();
    }

    /** Gọi SAU khi đã gửi AUDIO_ON. */
    public synchronized void startMic(InetAddress server, int serverUdpPort, int roomId)
            throws LineUnavailableException {
        if (recorder == null) {
            recorder = new AudioRecorder(socket, server, serverUdpPort);
        }
        recorder.start(roomId);
    }

    public synchronized void stopMic() {
        if (recorder != null) {
            recorder.stop();
        }
    }

    public synchronized boolean isMicOn() {
        return recorder != null && recorder.isRunning();
    }

    /** Dừng mic, loa, đóng socket. Gọi nhiều lần không lỗi. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        stopMic();
        player.stop();
        socket.close();
    }
}
```

### 7.2 `HomeUI`

- Thay field `DatagramSocket audioSocket` bằng `AudioSession audio` (mở bằng `AudioSession.open()` trong
  constructor, lỗi → `audio = null` và hiện trạng thái như hiện nay).
- `JoinRoomRequest(roomId, audio.localPort())`.
- Khi `JOIN_ROOM_RES` ok: `new ClassroomUI(connection, username, role, roomId, roomName, audio, serverIp)`.
- `windowClosed` khi chưa `handedOff`: `audio.close()` (nếu khác null) + `connection.close()`.

### 7.3 `ClassroomUI`

- Xoá 2 interface `AudioPlayerControl`, `AudioRecorderControl`, 2 hàm `createAudioPlayer/createAudioRecorder`,
  constructor thứ hai, field `audioSocket/audioPlayer/audioRecorder`. Chỉ còn **một** constructor:
  `ClassroomUI(ServerConnection connection, String username, String role, int roomId, String roomName,
  AudioSession audio, String serverIp)`. Lưu `username` vào field.
- `startAudioPlayback()` → `audio.startPlayback()` (giữ try/catch và thông báo cũ).
- `toggleMicrophone()`: bật = gửi `AUDIO_ON(RoomIdOnly)` rồi
  `audio.startMic(InetAddress.getByName(serverIp), Protocol.UDP_PORT, roomId)`; tắt = `audio.stopMic()` +
  `AUDIO_OFF`. Giữ nhánh lỗi (gửi `AUDIO_OFF` bù) và mọi chuỗi trạng thái.
- `micButton.setEnabled(audio != null)`.
- `sendLeaveRequest()`: bỏ `audioPlayer.stop()` ở nhánh `micOn` (bản cũ tắt loa ở đây — sai ý đồ);
  chỉ `audio.stopMic()` + `AUDIO_OFF`.

### 7.4 B8 — "Rời phòng" quay về trang chủ, giữ kết nối

- Thêm field `private boolean returningHome;`.
- `leaveRoom()` (nút "Rời phòng"):
  ```java
  sendLeaveRequest();
  inRoom = false;
  returningHome = true;
  frame.dispose();                                   // windowClosed chạy, KHÔNG đóng kết nối
  new HomeUI(connection, username, role, serverIp);  // HomeUI tự setListener và tải lại danh sách lớp
  ```
- `windowClosed`: luôn `stopScreenCapturer()` và `audio.close()` (nếu khác null);
  **chỉ** `connection.close()` khi `!returningHome`.
- Nút X của cửa sổ lớp: giữ hành vi cũ (rời lớp + đóng kết nối = thoát).
- Disconnect listener: không đổi.

> Lưu ý thứ tự: `dispose()` → `windowClosed` đóng `AudioSession` cũ **trước** khi `HomeUI` mở `AudioSession`
> mới. Cả hai chạy trên EDT nên tuần tự. Nếu thấy `windowClosed` không chạy đồng bộ, gọi `audio.close()`
> ngay trong `leaveRoom()` trước `dispose()` (close gọi lặp an toàn).

### 7.5 Dọn code chết

Với **mỗi** mục dưới: chạy grep, chỉ xoá khi **không còn tham chiếu nào ngoài định nghĩa**.

| Mục | Lệnh kiểm |
|---|---|
| `ScreenPanel.setFrame` | `grep -rn "setFrame(" src` |
| `ScreenCapturer.sameBytes` | `grep -rn "sameBytes" src` |
| `ScreenCapturer.screenDiagonalInches` | `grep -rn "screenDiagonalInches" src` |
| `RoomManager.allRooms` | `grep -rn "allRooms" src` |
| `JitterBuffer.pollNow` | `grep -rn "pollNow" src` |

`ScreenReceiver.decodeFailures`: đổi `static volatile long` thành `static final AtomicLong`
(`++` trên `volatile` không nguyên tử — đúng lý do mà `MediaStats` đã giải thích), cập nhật 3 getter/reset.

### 7.6 Test

- `JitterBufferTest`:
  `takeReturnsNullUntilPrebuffered` (capacity 10, prebuffer 3: put 2 → `take(10)` null; put thêm 1 → `take` không null) ·
  `dropsOldestWhenFull` (capacity 2, prebuffer 0: put A, B, C → `getDroppedCount()==1`, lấy ra B rồi C) ·
  `clearResetsPrebuffer` · `constructorRejectsBadArgs` (capacity 0; prebuffer > capacity → `IllegalArgumentException`).
- `AudioConfigTest`: `assertTrue(AudioConfig.selfCheck())`; `assertEquals(640, AudioConfig.CHUNK_BYTES)`;
  `assertEquals(Protocol.UDP_PCM_BYTES, AudioConfig.CHUNK_BYTES)`.
- `AudioSession`, UI: không viết JUnit (cần thiết bị âm thanh/màn hình) — kiểm bằng smoke GUI 9.2.

**Cổng 0.5 + [SMOKE] + smoke GUI 9.2 đầy đủ.**
**Commit (3):** `refactor(client): AudioSession so huu socket UDP + mic + loa [TV1 sua file cua TV2, TV3]` ·
`fix(client): Roi phong quay ve trang chu, giu ket noi (B8) [TV1 sua file cua TV3]` ·
`refactor: xoa code chet, ScreenReceiver dung AtomicLong [TV1 sua file cua TV2, TV3]`

---

## BƯỚC 8 — Tài liệu

### 8.1 `docs/KIEN_TRUC.md` (mới) — nguồn cho **Chương 2** báo cáo

Các mục bắt buộc (viết từ code thật sau refactor, mỗi khẳng định kèm đường dẫn file):

1. **Sơ đồ gói** (ASCII, giống mục 2 của file này).
2. **Hai kênh mạng**: TCP 9000 (điều khiển + frame), UDP 9001 (thoại) — vì sao tách (lấy từ javadoc
   `ScreenReceiver`, `AudioRecorder`).
3. **Bảng luồng** — server: `main` (accept), `pool-1-thread-N` (đọc + xử lý), `srv-writer-N` (ghi),
   `audio-relay`, `stats`, `shutdown-hook`; client: EDT, `server-connection-reader`, `audio-receiver`,
   `audio-player`, `audio-stats`, `audio-recorder`, `screen-capturer`. Cột: tên · tạo ở đâu · làm gì · chặn ở đâu.
4. **Bảng cơ chế đồng bộ**: `ReentrantLock` mỗi `Room` (+ cờ `closed`, B2) · `ConcurrentHashMap` trong
   `RoomManager` · `LinkedBlockingQueue`/`ArrayBlockingQueue` (control/frame, drop-oldest) ·
   `ArrayBlockingQueue` trong `JitterBuffer` (producer–consumer) · `synchronized` trong
   `SqliteDatabaseManager`, `ServerConnection.send` · `AtomicLong` bộ đếm · `volatile` cờ chạy.
   Mỗi dòng: vấn đề nó giải quyết, điều gì xảy ra nếu bỏ.
5. **Luồng nghiệp vụ** (sequence ASCII): đăng nhập · tạo lớp → vào lớp (mở lớp từ CSDL) · chia sẻ màn hình ·
   bật mic (`JOIN` báo cổng → `AUDIO_ON` → UDP) · rời lớp / mất kết nối (tắt chia sẻ, đóng lớp rỗng).
6. **Quy tắc bảo mật phía server** (B3, B6, B7) — "không bao giờ tin client".
7. **Bảng đối chiếu yêu cầu đề 21** (mục 1.4 của file này, cập nhật trạng thái).
8. **Hạn chế còn lại** (thật, không thêm cho có): SHA-256 không salt; chưa mã hoá đường truyền;
   không có token phiên; nhiều người cùng nói thì jitter buffer nối tiếp gói của nhiều người (không trộn),
   và thống kê mất gói theo `seqNo` bị sai khi có >1 người nói; client luôn gửi thoại tới `Protocol.UDP_PORT`
   (server đổi cổng UDP thì client không biết).

### 8.2 `docs/GIAO_THUC.md` (mới)

Bảng 19 loại gói: mã hex · tên · chiều · record trong `Messages` · trường. Sinh từ `PacketType.java` +
`Messages.java` (đúng 100%, đối chiếu từng dòng). Kèm khung TCP và gói UDP.

### 8.3 `README.md`

- Mục "Thư mục / người sở hữu": thêm `server/handler/`, `common/msg/`, `src/test/`.
- Mục 2 "Chạy thử": thêm `mvn -o -q verify` (chạy toàn bộ test), lệnh client mới
  `java -jar <jar> client <ip> 9000`; giữ lệnh cũ (I12).
- Mục 3: cập nhật bảng file theo cấu trúc mới; mục 3.1 theo 4.5.
- Mục "Client giả lập": cú pháp `--as`, `screen-send new`, dòng độ trễ.
- Thay khối "Bốn quyết định thiết kế" bằng một đoạn ngắn + liên kết sang `docs/KIEN_TRUC.md`
  (**chuyển** nội dung sang đó, không mất chữ nào).
- Ghi số kiểm thử mới: "JUnit: N test" (lấy N thật từ `target/surefire-reports`).

### 8.4 `docs/KICH_BAN_DEMO.md`

- Mục 0: thêm cách chạy client `java -jar <jar> client`.
- Bảng kịch bản: bước 8 chat → nói "tin nhắn có tên người gửi do **server** gắn, client không giả mạo được";
  bước 9 rời phòng → "quay về trang chủ, không phải đăng nhập lại".
- Mục 3 "Nhóm đang ở đâu": cập nhật.
- Mục 4 dự phòng: thêm "Vào lớp báo `Lop dang dong, vui long thu lai` → bấm Tham gia lần nữa".

### 8.5 Rà toàn bộ tài liệu

```bash
grep -rn "InMemoryDatabaseManager\|PacketRoundTripTest\|setTarget\|rawOutput\|AudioPlayerControl\|addMember(" docs README.md
```

Mọi kết quả phải được sửa cho đúng hiện trạng, **trừ** các đoạn nhật ký lịch sử (`nhat-ky-*.md`) —
những đoạn đó giữ nguyên vì là bằng chứng quá trình.

**Commit:** `docs: KIEN_TRUC, GIAO_THUC, cap nhat README va kich ban demo sau refactor`

---

## BƯỚC 9 — Kiểm tra cuối

### 9.1 Tự động

```bash
env -u MSYS_NO_PATHCONV -u MSYS2_ARG_CONV_EXCL mvn -o clean verify       # khong -q, de thay so test
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.T0Demo
java -cp target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar com.pbl4.tools.LoadTest 10 5
grep -rn "buildPayload\|payloadReader" src/main/java        # chi PacketUtils.java, Messages.java
```

Kỳ vọng: `BUILD SUCCESS`, 0 fail; `KET QUA: 20 DAT / 20 TONG`; LoadTest `4 DAT / 4 TONG`.

### 9.2 Smoke GUI (một máy, hai client) — ghi đạt/không từng dòng vào `docs/refactor/smoke-<ngay>.md`

| # | Thao tác | Kỳ vọng |
|---|---|---|
| 1 | `java -jar <jar>` | Server in `SERVER DANG CHAY`, `Cho ket noi...` |
| 2 | `java -jar <jar> client` → đăng ký GV `gvtest`, đăng nhập | Vào trang chủ |
| 3 | Tạo lớp "Lop smoke" | Hiện mã 6 số, danh sách có lớp đó |
| 4 | Client 2: đăng ký HS `hstest`, chọn lớp, Tham gia | Thấy lớp; **chưa** có GV trong lớp → danh sách thành viên chỉ có `hstest` |
| 5 | Client 1: chọn lớp, Tham gia | Cả hai thấy 2 thành viên |
| 6 | GV bấm Chia sẻ màn hình | HS thấy ảnh |
| 7 | GV và HS bật mic, nói (đeo tai nghe) | Nghe được hai chiều, không vọng |
| 8 | HS gõ chat "abc" | Cả hai thấy `hstest: abc` |
| 9 | HS bấm "Rời phòng" | HS về **trang chủ**, không phải đăng nhập lại; GV thấy còn 1 thành viên |
| 10 | HS Tham gia lại cùng lớp | Vào được |
| 11 | GV bấm "Rời phòng" khi đang chia sẻ | HS thấy màn hình về "Chưa có ai chia sẻ", thông báo dừng |
| 12 | HS rời phòng → lớp rỗng. HS Tham gia lại | **Vào được** (B1) |
| 13 | Tắt server, mở lại, client đăng nhập, Tham gia "Lop smoke" | **Vào được** (B1, lớp từ CSDL) |
| 14 | Đóng cửa sổ lớp bằng nút X | Client thoát, server log "ngat ket noi" |

### 9.3 Bàn giao

- `git log --oneline main..HEAD` dán vào cuối `docs/refactor/baseline.md`.
- Báo Minh: số commit, số test, kết quả 9.1/9.2, mọi điểm lệch kế hoạch. **Dừng** — chờ Minh duyệt merge.

---

## BƯỚC 10 — *(TUỲ CHỌN — CHỈ LÀM KHI MINH GHI "DUYỆT BƯỚC 10")* Giáo viên thêm học sinh vào lớp

Đề 21 ghi "tạo lớp học và **thêm người học**". Hiện học sinh tự vào bằng mã. Bước này **đổi giao thức**
(19 → 21 loại gói) và đổi luật vào lớp, nên phải được duyệt.

### 10.1 Giao thức

`PacketType`: thêm
```java
public static final byte ADD_MEMBER_REQ = 0x15;  // C→S: writeInt(roomId), writeUTF(studentUsername)
public static final byte ADD_MEMBER_RES = 0x16;  // S→C: writeByte(ok), writeUTF(msg)
```
và hai nhánh trong `name()`. `Messages`: `AddMemberRequest(int roomId, String username)`; phản hồi dùng
`StatusResponse`. Thêm golden + round-trip test.

### 10.2 CSDL

Bảng mới trong `SqliteDatabaseManager` constructor:
```sql
CREATE TABLE IF NOT EXISTS RoomMembers (
    room_id INTEGER NOT NULL,
    username TEXT NOT NULL,
    PRIMARY KEY (room_id, username),
    FOREIGN KEY (room_id) REFERENCES Rooms(room_id),
    FOREIGN KEY (username) REFERENCES Users(username)
)
```
Interface thêm: `String roleOf(String user)` (null nếu không có) · `boolean addRoomMember(int roomId, String user)`
(false nếu đã có) · `boolean isRoomMember(int roomId, String user)` · `List<RoomInfo> listRoomsForStudent(String user)` ·
`List<RoomInfo> listRoomsOfTeacher(String teacher)`.

### 10.3 Luật server

- `ADD_MEMBER_REQ`: phải đăng nhập, là chủ lớp `roomId` (tra CSDL); user phải tồn tại và có vai trò `STUDENT`;
  đã có → ok=false "Hoc sinh da co trong lop". Thành công → ok=true "Da them <user> vao lop".
- `JOIN_ROOM_REQ`: chủ lớp luôn được vào; `STUDENT` chỉ được vào nếu `isRoomMember`; khác → ok=false
  "Ban chua duoc giao vien them vao lop nay".
- `ROOM_LIST_RES`: `TEACHER` → lớp của mình; `STUDENT` → lớp mình được thêm.

### 10.4 Client

`ClassroomUI` (chỉ GV): ô nhập + nút "Thêm học sinh" ở thanh điều khiển; xử lý `ADD_MEMBER_RES` hiện ở
`statusLabel`. `HomeUI` không đổi (server đã lọc danh sách).

### 10.5 Tool/test phải sửa theo

`T0Demo`: sau khi GV tạo lớp, gửi `ADD_MEMBER_REQ` cho `minh`, `an`, `cham` (các HS phải `registerAndLogin`
**trước** để tồn tại) — giữ đúng 20 check. `LoadTest`: thêm `sv0..svN`. `TestClient screen-recv/audio-recv`:
thêm tuỳ chọn để GV thêm HS — hoặc `screen-send new` tự thêm các user truyền qua `--add u1,u2`.
`ServerRulesTest`: thêm 4 test (không phải chủ thì không thêm được; thêm GV → lỗi; HS chưa được thêm → không vào;
HS được thêm → vào và thấy lớp trong danh sách). Cập nhật mọi tài liệu ghi "19 loại gói".

**Commit:** tách theo 10.1–10.5.

---

## 11. Câu hỏi còn mở cho Minh (agent không tự trả lời)

| # | Câu hỏi | Ảnh hưởng |
|---|---|---|
| Q1 | Cả nhóm (TV2, TV3) đồng ý cho refactor đụng file của mình không? | Quy tắc sở hữu README dòng 16 |
| Q2 | **Thí nghiệm 4.2 bản A**: `Room.broadcastFrame` hiện chỉ `offerFrame` vào hàng đợi (không ghi socket), nên "giữ khoá trong lúc gửi" theo kiến trúc hiện tại **không chặn ai** — muốn bản A có ý nghĩa thì phải ghi socket thật bên trong khoá, mà làm vậy lại phạm luật một-luồng-ghi (I7). Cần quyết định thiết kế thí nghiệm trước khi đo. `KE_HOACH_HOAN_THIEN.md` 4.1 đang dùng `c.sendFrame(...)` — hàm này **không tồn tại**. | Số liệu bảng 4.2 |
| Q3 | Có làm bước 10 (ghi danh) không? | Đổi giao thức, ~4 h |
| Q4 | Thư mục `.git.broken-20261027` có bỏ khỏi repo không? | Bước 0.3 |
| Q5 | Merge nhánh refactor trước hay sau khi chạy P0 (2 máy, thí nghiệm 4.2)? Khuyến nghị: chạy P0 trên `main` + cherry-pick 1B ngay; refactor merge sau khi 9.2 đạt. Vì I5–I7 giữ nguyên, số đo trên `main` vẫn đúng cho bản refactor. | Lịch |
