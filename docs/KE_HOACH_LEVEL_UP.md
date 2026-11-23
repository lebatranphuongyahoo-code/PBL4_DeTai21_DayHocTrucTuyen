# KẾ HOẠCH LEVEL-UP — PBL4 Đề tài 21: Dạy học trực tuyến

> Cập nhật 02/11/2026. Mục tiêu: hoàn thiện đề tài theo yêu cầu giảng viên, sửa vấn đề chia sẻ màn hình trước, rồi nâng chất lượng trải nghiệm và trình bày.
>
> **Nguyên tắc:** không thay toàn bộ công nghệ chỉ vì HTML/CSS thường trông hiện đại hơn. Ưu tiên làm chắc bản Java đang chạy, vì nó đã có TCP/UDP, đa luồng, lớp học và các bài kiểm tra. Web là hướng nâng cấp riêng, chỉ bắt đầu sau khi bản hiện tại ổn định và nhóm xác nhận phù hợp với thời gian/báo cáo. Không thay giao thức hoặc xóa chức năng đang có nếu chưa có kiểm thử tương đương.

---

## 1. Đọc yêu cầu đề tài và chọn hướng

Đề tài 21 trong `Danh_Sach_De_Tai_PBL4_HDH&MMT.pdf` là **“Xây dựng chương trình dạy học trực tuyến”**. Phần mô tả yêu cầu phía client có đăng nhập, tạo/lựa chọn lớp, chia sẻ màn hình và âm thanh; phía server quản lý tài khoản/lớp học, chuyển tiếp media, có đa luồng và đồng bộ khi gửi/nhận. Đề không bắt buộc dùng web hay Swing.

Tài liệu môn học nhấn mạnh vận dụng kiến thức hệ điều hành, mạng máy tính, lập trình mạng; do đó bản trình diễn phải thể hiện rõ giao thức, TCP/UDP, đa luồng, đồng bộ và cách đo/kiểm chứng, không chỉ có giao diện đẹp.

### Hướng đề xuất

**Giai đoạn A — làm bản Java hiện có đáng tin cậy:** sửa/kiểm tra chia sẻ màn hình trên máy thật, âm thanh, thao tác lớp; nâng UI Swing vừa đủ hiện đại; hoàn thiện kiểm thử, hướng dẫn chạy và kịch bản demo. Đây là đường chính đảm bảo đồ án đáp ứng yêu cầu.

**Giai đoạn B — cân nhắc Web sau khi A xanh:** nếu nhóm còn thời gian và giảng viên/nhóm đồng ý, dựng client web như một giao diện bổ sung, giữ Java server và các nghiệp vụ/giao thức hiện tại ở giai đoạn đầu. Chỉ chuyển media sang WebRTC/WebSocket sau khi làm rõ kiến trúc và đo được; trình duyệt không thể trực tiếp dùng giao thức TCP tùy biến hiện tại. Không tuyên bố “đã chuyển sang web” chỉ bằng cách bọc Swing hoặc tạo trang tĩnh.

**Không khuyến nghị ngay:** viết lại cả server lẫn client sang web trước khi tìm ra nguyên nhân lỗi đang gặp. Việc đó đồng thời thay UI, networking, media, triển khai và kiểm thử, tăng khả năng tạo thêm lỗi và làm mất một bản demo đang chạy.

---

## 2. Hiện trạng đã kiểm tra

- Lịch sử Git hiện có commit bàn giao với 22 commit refactor; `T0ScenarioTest` chạy được và in `KET QUA: 20 DAT / 20 TONG`.
- Một lần tái hiện chạy server và hai kết nối thật qua `ServerConnection`: màn hình **headless** bị từ chối ở `new Robot()` đúng như Java yêu cầu môi trường có màn hình. Khi chạy không headless, giáo viên gửi 1 frame, học sinh nhận và giải mã 1 frame. Điều này chỉ xác nhận đường relay của server hoạt động trong lần thử; **chưa xác nhận GUI trên máy của người dùng**, chưa tái hiện được lỗi cụ thể.
- Chưa có test tự động end-to-end cho chụp màn hình vì cần desktop thật. Vì vậy không kết luận rằng lỗi “chắc chắn do quyền Robot/HiDPI”. Cần thu log và thông tin máy tại bước A1.
- Yêu cầu web không xuất hiện trong đề tài. Giao diện web có thể là lựa chọn trình bày, không phải điều kiện để đạt đề.

---

## 3. Nguyên tắc bất biến khi nâng cấp

1. Giữ tính năng đề yêu cầu: đăng ký/đăng nhập, tạo/quản lý lớp, người học tham gia lớp, chat, giáo viên chia sẻ màn hình, thoại hai chiều, nhiều client.
2. Giữ và chứng minh phần môn học: server đa luồng, thread pool, đồng bộ trạng thái phòng, TCP cho điều khiển/màn hình và UDP cho thoại theo thiết kế hiện tại.
3. Không âm thầm đổi các byte payload hoặc hằng giao thức hiện tại. Thay đổi giao thức cần versioning/migration, kiểm thử tương thích và cập nhật báo cáo.
4. Mọi bug được sửa phải có bước tái hiện trước, log/kết quả sau, và test phù hợp. Không bỏ qua lỗi chỉ để có giao diện mới.
5. Mỗi mốc phải build/test xanh; giữ nhánh/commit an toàn và không xóa bản chạy ổn định.
6. Không tự thêm tính năng ngoài đề (ví dụ ghi danh chính thức, ghi hình, chia sẻ file) trước khi yêu cầu cốt lõi ổn.

---

## 4. Lộ trình

### Giai đoạn 0 — Chốt baseline an toàn

1. Kiểm tra `git status`, nhánh, commit hiện tại; tạo nhánh làm việc riêng, không ghi đè thay đổi chưa commit.
2. Chạy `mvn -o -q clean verify`, lưu kết quả và phiên bản Java/OS.
3. Chạy server + hai client trên máy thật theo đúng kịch bản; quay màn hình hoặc ghi từng bước và lỗi.
4. Ghi nhận: OS, độ phân giải/scale màn hình, số màn hình, cách chạy (IDE/jar), vai trò tài khoản, thao tác trước khi lỗi; lấy log server và client, đặc biệt `[screen]`, `ERROR`, trạng thái nút.
5. Tiêu chí qua: có baseline tái hiện được hoặc ghi rõ chưa tái hiện được; không sửa theo phỏng đoán.

**Dừng và báo người dùng** nếu test baseline đỏ hoặc working tree có thay đổi không rõ nguồn gốc.

### Giai đoạn A — Ổn định bản Java (ưu tiên cao nhất)

#### A1. Chẩn đoán luồng chia sẻ màn hình trên GUI thật

Theo đường đi cụ thể:

`ClassroomUI.toggleScreenSharing()` → gửi `SCREEN_START` → server xác thực vai trò/chủ lớp → `ScreenCapturer` chụp bằng `Robot` và JPEG → gửi `SCREEN_FRAME` qua TCP → `ScreenHandler` relay → `ScreenReceiver.decode()` → cập nhật `ScreenPanel` trên Swing EDT.

Kiểm tra và ghi rõ từng điểm:
- UI có gửi được yêu cầu và nhận `SCREEN_START`/`ERROR` không; server có `sharing=true` không.
- `Robot` có khởi tạo được không; có thông báo `AWTException`, `SecurityException`, headless hay lỗi thiết bị không.
- Có frame capture/encode/send không; kích thước JPEG, FPS, frame drop.
- Client người học có nhận frame không; decode có tăng lỗi không; ảnh có vào đúng panel không.
- Xác minh trạng thái listener khi HomeUI chuyển sang ClassroomUI (tránh mất gói đến sớm), và việc cập nhật UI đều đưa về EDT.
- Thử màn hình thường, scale Windows 100%/125%/150%, màn hình phụ nếu có; kiểm tra tên/kích thước capture có khớp ảnh hiển thị.

Chỉ sửa nguyên nhân đã quan sát được. Bổ sung thông báo lỗi thân thiện cho lỗi quyền chụp/môi trường không hỗ trợ; không nuốt exception khiến nút báo đang chia sẻ nhưng không có frame. Thêm test unit cho phần có thể tách/giả lập; kiểm thử GUI bằng smoke test có checklist.

**Tiêu chí qua:** giáo viên chủ lớp bấm chia sẻ thì ít nhất một frame hợp lệ đến người học; dừng chia sẻ dừng capture; lỗi môi trường hiển thị rõ và nút được khôi phục; không ảnh hưởng test T0.

#### A2. Ổn định âm thanh và phòng học

- Kiểm tra học sinh vào lớp trước/sau giáo viên, bật/tắt mic, đóng cửa sổ, mất kết nối; xác minh không còn luồng/socket chạy rò.
- Xác nhận hai client nghe được nhau qua UDP trên cùng LAN, nêu rõ yêu cầu firewall/cổng và giới hạn hiện có.
- Kiểm tra quy tắc chỉ giáo viên chủ lớp điều khiển chia sẻ; server kiểm tra quyền, không dựa vào nút UI.
- Chạy test luật server hiện có và bổ sung trường hợp lỗi thực tế tìm thấy.

**Tiêu chí qua:** hai client demo ổn định theo checklist; rời lớp/mất kết nối dọn tài nguyên đúng; test tự động xanh.

#### A3. Nâng UI Swing — không đổi nền tảng vội

- Thiết kế lại Login/Home/Classroom theo một bộ màu, font, khoảng cách, trạng thái/nút nhất quán; phân cấp rõ: nội dung lớp ở giữa, danh sách người học bên cạnh, chat và điều khiển dễ tìm.
- Thêm trạng thái kết nối, lớp hiện tại, đang chia sẻ/đang thu tiếng, lỗi và thao tác đang xử lý; tránh hộp thoại lỗi liên tục.
- Bảo đảm cửa sổ co giãn, DPI, độ tương phản, phím Enter/Tab; tách dựng giao diện khỏi xử lý mạng để không treo EDT.
- Dùng Swing chuẩn trước, không thêm dependency/theme ngoài nếu không cần; mọi cập nhật component từ listener phải chạy trên EDT.
- Chụp ảnh trước/sau và duyệt trên kích thước cửa sổ laptop phổ biến.

**Tiêu chí qua:** luồng demo không cần hướng dẫn miệng để tìm nút; cửa sổ không treo khi mạng chậm; media không bị ảnh hưởng.

#### A4. Hoàn thiện trải nghiệm và nội dung học tập

Đối chiếu từng yêu cầu đề 21 với chức năng chạy được. Bổ sung/hoàn thiện những chức năng còn thiếu thật sự theo đúng mô tả đề, ưu tiên thao tác giáo viên/học sinh và quản lý lớp. Không tự diễn giải “quản lý” thành các tính năng lớn ngoài khả năng nhóm.

- Kiểm thử các luồng đăng ký/đăng nhập, tạo lớp, tham gia/rời lớp, danh sách thành viên, chat, màn hình, thoại.
- Kiểm tra đầu vào sai, reconnect, server tắt, lớp rỗng, nhiều client.
- Hoàn thiện hướng dẫn cài/chạy trên máy khác, firewall/cổng, dữ liệu demo và tài khoản demo.

**Tiêu chí qua:** từng yêu cầu đề có bằng chứng: test, ảnh/log demo hoặc bước trình diễn.

#### A5. Kiểm thử chấp nhận và đóng gói demo

- `mvn -o clean verify` xanh.
- `T0Demo` đạt 20/20 và `LoadTest` theo kịch bản đã chốt.
- Smoke GUI hai client theo toàn bộ yêu cầu đề.
- Đóng gói jar, chạy từ thư mục sạch; xác nhận DB, cổng và hướng dẫn không phụ thuộc đường dẫn IDE.
- Chuẩn bị video/ảnh dự phòng cho thiết bị không có microphone/camera/màn hình phù hợp; không thay thế demo trực tiếp nếu giảng viên yêu cầu.

### Giai đoạn B — Quyết định có thêm Web hay không

Chỉ bắt đầu khi Giai đoạn A đạt tiêu chí qua, còn đủ thời gian, và nhóm/giảng viên đồng ý phạm vi. Trước khi code, làm prototype ngắn và chốt cách trình bày với báo cáo.

#### B1. Kiến trúc web khả thi

Đề xuất an toàn là **giữ Java server làm nghiệp vụ**, thêm một HTTP/WebSocket gateway cho client web; nhưng phải thiết kế rõ xác thực/phiên, nguồn dữ liệu, sự kiện lớp và media. Trình duyệt không thể kết nối trực tiếp vào TCP protocol tùy biến mà Java Swing hiện dùng.

- UI đăng nhập/lớp/chat có thể dùng HTTP + WebSocket.
- Chia sẻ màn hình trong browser cần quyền `getDisplayMedia` qua HTTPS/localhost và vận chuyển video phù hợp, thường là WebRTC; chuyển nguyên JPEG frames qua WebSocket là prototype đơn giản nhưng băng thông/độ trễ kém hơn và cần giới hạn rõ.
- Thoại browser nên dùng WebRTC audio; không giả định browser có thể gửi/nhận các UDP PCM datagram hiện có.
- Không triển khai mật khẩu/session qua WebSocket thiếu xác thực, không mở server công khai, không giả vờ TLS/HTTPS có sẵn.

#### B2. Prototype có phạm vi giới hạn

1. Chọn một luồng: đăng nhập giả lập/đã xác thực + danh sách lớp + chat realtime, chưa media.
2. Đo thời gian dựng, reconnect, hai client và xử lý lỗi.
3. Trình diễn prototype cho nhóm/giảng viên; xác nhận nó phục vụ đồ án chứ không làm lệch trọng tâm OS/network.
4. Chỉ sau phê duyệt mới thiết kế media WebRTC và quyết định web bổ sung hay thay Swing.

**Tiêu chí tiếp tục:** prototype ổn, thời gian đủ cho kiểm thử và báo cáo, Java baseline vẫn chạy. Nếu không đạt, giữ Swing đã nâng cấp; không xóa nhánh/bản ổn định.

---

## 5. Thứ tự ưu tiên và cách báo tiến độ

**P0:** tái hiện lỗi chia sẻ màn hình → sửa bằng bằng chứng → xác nhận demo hai máy.

**P1:** rà đủ yêu cầu đề 21 → hoàn thiện luồng người dạy/người học, thoại, chat, quản lý lớp và kiểm thử.

**P2:** làm mới giao diện hiện có và trải nghiệm người dùng.

**P3:** prototype web nếu P0–P2 đã đạt và được nhóm/giảng viên chấp thuận.

Mỗi hạng mục báo: triệu chứng ban đầu, nguyên nhân đã chứng minh, file/commit thay đổi, lệnh test, kết quả, việc còn chưa xác minh. Không ghi “đã sửa” nếu chưa chạy được trên đường đi liên quan.

---

## 6. Việc cần có trước khi triển khai

Người dùng đã yêu cầu hướng đề xuất và kế hoạch, chưa yêu cầu bắt đầu sửa code. Vì vậy hiện tại chỉ chốt kế hoạch, không tự thay đổi mã nguồn. Khi chuyển sang triển khai, bắt đầu tại Giai đoạn 0/A1; cần người dùng cung cấp log hoặc cùng thực hiện một lần tái hiện trên máy có màn hình, vì môi trường chạy của agent không tương đương desktop thật.
