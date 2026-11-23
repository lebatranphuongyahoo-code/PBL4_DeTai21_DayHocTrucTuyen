# Báo cáo kiểm toán và nghiệm thu PBL4

**Ngày:** 23/11/2026
**Phạm vi:** nhánh `codex/trung-tuong-2026-11-22`, commit `a6cabab9bb26c0f44644f44ec2bda47fb787d78e`
**Kết luận:** đạt có điều kiện cho prototype web và các luồng chức năng đã thử; chưa đủ bằng chứng để tuyên bố nghiệm thu toàn diện về mạng thật, hiệu năng hoặc nghiệp vụ đã được giảng viên chốt.

Không có mã nguồn hay cấu hình ứng dụng nào được sửa trong lượt kiểm toán. Ba tệp báo cáo nghiệm thu là đầu ra duy nhất được tạo theo yêu cầu.

## 1. Git, bản sao lưu và phạm vi kiểm toán

- Remote là `origin` trỏ tới repository GitHub của nhóm. Nhánh `codex/trung-tuong-2026-11-22` trên GitHub và worktree `E:\PBL4_DeTai21` cùng trỏ tới `a6cabab`; commit cha để quay lui là `ae190af`. Nhánh `main` hiện ở `ec82a22`.
- Codex đang có một worktree khác tại `C:\Users\ADMIN\.codex\worktrees\trung-tuong-2026-11-22\PBL4_DeTai21`, detached ở commit `ae190af`. Vì vậy kiểm toán được thực hiện trên worktree `E:\PBL4_DeTai21`, không phải worktree đang mở mặc định trong Codex. Nếu chạy ở worktree kia, sẽ kiểm tra bản cũ chưa có commit Admin.
- Trước khi kiểm toán, worktree chính không có staged hoặc modified source files; có sẵn `docs/reports/` chưa được theo dõi và không đụng tới. Trong lúc kiểm toán không có file source nào thay đổi. Các lệnh build tạo `web/dist` và `backend/target`, là output bị ignore.
- Git không theo dõi file DB/credential/certificate. Có bốn SQLite local bị ignore: `data/classroom-team.db`, `data/classroom-web.db`, `data/remote-test.db`, và `pbl4.db`. Chỉ kiểm tra tên/kích thước; không mở hoặc dùng các DB này. Không xác minh nội dung hay tính đầy đủ của bản sao lưu dữ liệu.
- Không thấy file secret kiểu `.env`, certificate, private key hoặc database trong danh sách tracked. Bốn DB ignored vẫn có thể chứa dữ liệu thật; nhóm cần tự xác nhận trước khi sao chép/chia sẻ.

## 2. Kiểm thử tự động

Các kiểm thử chạy tại commit nêu trên; backend dùng DB tạm trong `target` hoặc DB JUnit ngẫu nhiên, không dùng các DB local ở trên.

| Lệnh | Kết quả | Loại bằng chứng |
|---|---:|---|
| `npm test` trong `web/` | 23/23 pass | Node tests cho logic/UI harness; không phải toàn bộ kiểm thử trình duyệt thực |
| `npm run build` trong `web/` | pass | TypeScript check và Vite production build |
| `mvn verify` từ gốc repo | 34/34 JUnit pass; reactor build thành công | Có test service/unit, API Spring MockMvc, và WebSocket thật qua server port ngẫu nhiên |

`MediaRelayTest` mở WebSocket thật để kiểm tra relay, cách ly lớp, quyền và thu hồi kết nối. Đây là kiểm thử tích hợp trên một máy, không phải kiểm thử tải 128 kết nối hay hai máy qua mạng. Frontend tests chạy trong Node, không mô phỏng đầy đủ layout/trình duyệt hoặc thiết bị media.

Một điểm lệch hướng dẫn: `docs/DEMO_VSCODE.md:14` nói `scripts/build-web.ps1` chạy frontend/backend tests, nhưng script chỉ chạy `npm run build` và `mvn verify` (`scripts/build-web.ps1:6-12`). Nó không gọi `npm test`. Ba kiểm tra trên đều đã được chạy riêng trong lượt này.

## 3. Nghiệm thu nghiệp vụ và phân quyền

| Vai trò | Hành vi đã xác minh | Giới hạn / tình huống bị cấm đã xác minh |
|---|---|---|
| Admin | Trên trình duyệt, Admin xem lớp do Teacher khác tạo, xem chủ lớp/thành viên, lưu trữ rồi khôi phục lớp. API tập trung ở `AdminController.java:17-22`; service guard ở `ClassroomService.java:28,52-69,278-331`. | Public registration từ chối role `ADMIN`/`TEACHER` (`AuthService.java:38-46`). Admin không tự có quyền vào buổi học/media nếu chưa là owner/member (`ClassroomService.java:46-76`; `AdminApiTest.java`). API test trực tiếp xác nhận Student bị 403; Teacher bị chặn ở service-level test, nhưng chưa có ca HTTP cụ thể cho Teacher gọi `/api/admin/*`.
| Teacher / chủ lớp | Tạo lớp, xem/quản lý lớp do mình phụ trách, duyệt yêu cầu, bắt đầu buổi, chat, tạo bài tập chữ, xem bài nộp, chấm điểm và gửi phản hồi. Thao tác qua `ClassroomController.java:21-39`, `ClassroomService.java:202-239,252-369`. | Owner checks dựa trên `classrooms.owner_id`; Teacher không phải owner không thể quản lý lớp người khác, có service tests (`PerRoomPermissionsTest.java`).
| Student / member | Tạo tài khoản, gửi yêu cầu mã lớp; chờ duyệt; sau khi duyệt xem lớp, vào buổi học, chat, nộp bài. Đã thử trên hai browser context với DB tạm. | Trước khi duyệt không vào danh sách lớp/phòng; người ngoài và thành viên bị gỡ không truy cập lớp/media. ACL ở `ClassroomService.java:46-50,333-364`, kiểm tra relay ở `MediaWebSocketHandler.java:129-135`.

**Nghiệp vụ cần giảng viên xác nhận:** `ClassroomService.create()` không yêu cầu role `TEACHER` (`ClassroomService.java:84-100`); test `ClassroomTest.java` còn xác nhận tài khoản public `STUDENT` tạo được lớp. Quyền quản lý sau đó gắn với owner của từng lớp. Điều này phù hợp nếu “chủ lớp” là bất kỳ người tạo lớp, nhưng chưa phù hợp nếu yêu cầu là chỉ giáo viên được mở lớp. Không có yêu cầu đủ rõ trong đầu vào để kết luận thay giảng viên.

**Điểm danh:** không thấy báo cáo điểm danh riêng. Hệ thống lưu và hiển thị cho chủ lớp lịch sử kết nối, tên người dùng và mốc vào/rời (`ClassroomService.java:142-160`, `web/src/MeetingHistory.tsx`). Tài liệu mô tả đây là lịch sử kỹ thuật để chẩn đoán, không phải có mặt/vắng. Vì UI có timestamp theo từng người, nên cần giảng viên xác nhận phạm vi này có bị hiểu thành điểm danh hay không.

## 4. UI/UX và thao tác trình duyệt

Đã chạy Chrome trên JAR mới với DB tạm `C:\Users\ADMIN\AppData\Local\Temp\pbl4-audit-ui-20261123\audit.db` và các tài khoản thử `auditadmin`, `auditteacher`, `auditstudent`. Không dùng dữ liệu lớp/tài khoản thật.

- Luồng thực tế đã đi: đăng ký ba tài khoản → operator cấp Admin/Teacher trên DB thử → Teacher tạo lớp → Student gửi yêu cầu → Teacher duyệt → Student vào buổi → chat hiển thị ở cả hai cửa sổ → Teacher đăng bài tập → Student nộp → Teacher chấm 9/10 và phản hồi → Admin xem lớp/thành viên, lưu trữ và khôi phục.
- Ở viewport 1440px và 390px, trang đăng nhập và Admin không tràn ngang (`document.documentElement.scrollWidth` bằng viewport). Đây không chứng minh mọi trang đều hoạt động ở mọi thiết bị.
- Axe: login 31 pass, 0 violation, 1 incomplete kiểm tra contrast cho phần tử mũi tên trang trí `.button-arrow`; Admin 35 pass, 0 violation, 0 incomplete. Chỉ audit hai màn hình này.
- Khi mô phỏng API Admin lỗi mạng, giao diện đồng thời hiện `Failed to fetch`, `0 lớp`, và “Chưa có lớp nào”. Điều này có thể khiến lỗi tải bị hiểu nhầm là không có lớp. Bằng chứng UI: `AdminDashboard.tsx:7,24,53-55` không có trạng thái `loaded` riêng.
- `Activities.tsx:20-35,55-59` có trạng thái `busy` nhưng không hiện chỉ báo đang tải; vùng có thể trống trong khi tải.
- Tab chat/người trong `ClassroomMedia.tsx:290-291` có `role=tab` và `aria-selected`, nhưng không có `aria-controls`/tab panels gắn nhãn trong đoạn đó. Axe không báo lỗi trên login/Admin; thao tác bàn phím, screen reader, zoom/contrast và focus management chưa nghiệm thu.

## 5. Bảo mật và hiệu năng

**Điểm tốt có bằng chứng:** mật khẩu dùng BCrypt cost 12; session token ngẫu nhiên 256-bit, DB lưu SHA-256 hash, hết hạn sau 8 giờ (`AuthService.java:26,49-59`). Public registration luôn tạo Student và từ chối role nâng quyền (`AuthService.java:38-47`). API mặc định cần bearer session; session được tra DB (`SecurityConfig.java:22-48`). WebSocket ticket dùng một lần, 30 giây (`MediaTicketService.java:20-36`); người gửi/nhận được kiểm tra membership/session, presenter và quyền mic (`MediaWebSocketHandler.java:97-135`). Payload, connection count và hàng đợi có giới hạn; peer chậm bị ngắt (`MediaWebSocketHandler.java:18,37-50,136-145`). SQL được truyền bằng prepared statements ở các đường nghiệp vụ đã kiểm tra.

**Rủi ro và giới hạn:**

- Server mặc định bind `127.0.0.1` (`application.properties:1-2`), phù hợp phát triển local. Tài liệu LAN yêu cầu HTTPS; không nghiệm thu TLS/proxy/tunnel thực.
- Login throttle là in-memory, theo username, tối đa 10 lượt/phút và tối đa 4096 khóa (`LoginAttempts.java:5-10`). Không thấy throttle tương đương cho đăng ký (`AuthService.java:38-46`). Chưa phù hợp để tuyên bố chống abuse khi mở public; đổi username/IP hoặc khởi động lại tiến trình làm giới hạn này kém hiệu lực.
- WebSocket origin mặc định bao gồm wildcard `https://*.trycloudflare.com` (`MediaWebSocketConfig.java:11`). Có thể cấu hình `app.allowed-origins`; triển khai thực cần đặt danh sách origin hẹp, đúng với host được tin cậy.
- Một `MediaWebSocketHandler` có các callback `synchronized` ở cấp instance (`MediaWebSocketHandler.java:46,53,97`) và gọi kiểm tra DB khi xử lý/relay. `Database` dùng một JDBC connection và đồng bộ transaction (`Database.java:13,20-27`). Vì vậy các lớp/phòng có thể chặn nhau; test chức năng không chứng minh đa luồng throughput.
- Mỗi peer có queue tối đa 32 message và 2 MiB; tổng connection cap 128; screen frame tối đa 1.5 MB. Đây là giới hạn prototype, chưa có benchmark CPU/RAM/latency/loss dưới tải.
- Audio và screen dùng cùng WebSocket trên TCP. TCP head-of-line blocking và cạnh tranh băng thông đã được mô tả trong `docs/WEB_MEDIA_DEMO.md:50-80`; không nghiệm thu chất lượng mic/loa thật hay picker chia sẻ màn hình.

## 6. Architecture audit

Hai sơ đồ C4 và truy vết auth → membership → meeting → relay nằm trong [báo cáo Architecture Audit](BAO_CAO_ARCHITECTURE_AUDIT.md). Tóm tắt: một ứng dụng Spring Boot đóng gói UI React, API REST, relay WebSocket và SQLite file; phân quyền lớp ở service, media validation ở relay. Cấu trúc đơn giản để demo nhưng các đường media và DB hiện tập trung đồng bộ trong một tiến trình.

## 7. Phát hiện theo mức ưu tiên

| ID / mức | Bằng chứng và nguyên nhân | Ảnh hưởng | Hướng tiếp theo |
|---|---|---|---|
| BIZ-01 · P1 cần chốt | `ClassroomService.java:84-100`; Student tạo được lớp. | Có thể sai ma trận Admin–Teacher–Student mà giảng viên mong muốn. | Hỏi giảng viên: chỉ Teacher mở lớp hay tài khoản nào cũng có thể là owner; cập nhật ma trận nghiệp vụ trước khi đổi code.
| PERF-01 · P1 trước khi tuyên bố tải | Callback WebSocket synchronized toàn handler (`MediaWebSocketHandler.java:46,53,97`), DB một connection/lock (`Database.java:13,20-27`); chưa có load test web. | Một frame/DB call có thể chặn message ở phòng khác; hiệu năng đồng thời chưa được chứng minh. | Đo p50/p95, CPU/RAM và nhiều lớp/peer; nếu hụt tiêu chí, thiết kế lại vùng khóa và DB access rồi benchmark.
| ENV-01 · P1 quy trình | Codex worktree mặc định detached ở `ae190af`; nhánh mới là `a6cabab` tại `E:\PBL4_DeTai21`. | Có thể kiểm tra nhầm bản thiếu Admin. | Mở/chuyển sang nhánh mới trước khi thành viên chạy test; ghi SHA trong kết quả.
| UX-01 · P2 | Mô phỏng API lỗi Admin tạo đồng thời thông báo lỗi và empty-state (`AdminDashboard.tsx:53-55`). | Quản trị viên có thể tưởng không có lớp thay vì lỗi kết nối. | Tách trạng thái loading/error/empty và kiểm tra bằng request chậm/lỗi.
| UX-02 · P2 | `Activities.tsx:20-35,55-59` không render tiến độ load. | Màn hình tương tác trống khó phân biệt đang tải với không có bài. | Hiện trạng thái loading và gắn `aria-busy`.
| SEC-01 · P2 trước khi mở public | Login throttle theo username/in-memory; register không throttle (`LoginAttempts.java:5-10`, `AuthService.java:38-46`). | Có thể bị lạm dụng tạo tài khoản hoặc đổi tên để né throttle. | Thêm giới hạn ở proxy/IP và endpoint, theo threat model triển khai.
| SEC-02 · P2 cấu hình triển khai | Wildcard Cloudflare origin trong default (`MediaWebSocketConfig.java:11`). | Mở rộng origin được chấp nhận ngoài host cụ thể của nhóm. | Cấu hình allowlist hostname chính xác khi demo/LAN; xác minh proxy/TLS.
| TEST-01 · P2 | `AdminApiTest.java` kiểm tra Student HTTP 403; Teacher denial chỉ service-level (`AdminPermissionsTest.java`). | Thiếu regression ở ranh giới HTTP cho một vai trò. | Thêm test HTTP Teacher gọi từng nhóm `/api/admin/*` cần bảo vệ.
| DOC-01 · P2 | `docs/DEMO_VSCODE.md:14` nói build script chạy frontend tests; `scripts/build-web.ps1:6-12` không gọi `npm test`. `docs/WEB_MEDIA_DEMO.md:86` vẫn ghi 6 JUnit tests dù hiện chạy 34. | Nhóm có thể tưởng CI đã chạy đủ hoặc báo số cũ. | Sửa tài liệu/pipeline ở một lượt triển khai được duyệt; không sửa trong giai đoạn audit này.
| SCOPE-01 · P2 cần xác nhận | Owner xem timestamp vào/rời theo người (`ClassroomService.java:151-160`, `MeetingHistory.tsx`). | Dù không có báo cáo điểm danh, UI có thể bị xem như attendance log. | Hỏi giảng viên có giữ lịch sử kết nối cho debug hay cần ẩn/giảm chi tiết.
| A11Y-01 · P3 | Tab chỉ có `role=tab`/`aria-selected`; axe có một contrast check incomplete trên mũi tên trang trí. | Khả năng thao tác bàn phím/screen reader chưa được xác nhận. | Test keyboard/screen reader, tab semantics, contrast và focus dialog.

Không có phát hiện P0. Không có bằng chứng từ lượt này để kết luận hệ thống đã sẵn sàng Internet/public hoặc tải cao.

## Checklist cần người dùng/nhóm nghiệm thu

- [ ] Giảng viên xác nhận Student có được tạo lớp không.
- [ ] Giảng viên xác nhận lịch sử kết nối có được giữ và hiển thị không.
- [ ] Hai máy Windows qua HTTPS: Admin, Teacher, hai Student; kiểm tra quyền chéo và restart.
- [ ] Chia sẻ cửa sổ/tab thật; xác minh stop/end/late join không để hình cũ.
- [ ] Kiểm tra mic/loa hai chiều, hai người nói, mic off, tai nghe/echo.
- [ ] Đo p50/p95, frame/audio loss, CPU/RAM theo số lượng người thực tế của đồ án.
- [ ] Chạy lại API-negative tests với Student và Teacher trên `/api/admin/*`; ghi HTTP status.
- [ ] Kiểm tra các DB ignored có dữ liệu cần sao lưu; không đưa chúng lên GitHub.

## Cập nhật sau khi xử lý các phát hiện

Phần trên là ảnh chụp audit tại commit `a6cabab`; các phát hiện về quyền và UX đã được xử lý trong bản cập nhật trên cùng nhánh `codex/trung-tuong-2026-11-22`:

- Tạo lớp qua `/api/rooms` yêu cầu Teacher; Student và Admin bị trả 403. Admin tạo ở `/api/admin/rooms` và phải chỉ định Teacher đang hoạt động làm owner. Admin Dashboard quản lý danh sách tài khoản và cấp quyền Teacher cho Student.
- `AdminApiTest` kiểm tra Teacher bị từ chối với các route Admin; kiểm tra Student tạo lớp bị từ chối và Admin tạo lớp có Teacher owner.
- Admin Dashboard phân biệt loading/error/empty; Activities hiển thị trạng thái bận; Student không còn control tạo lớp.
- Login và các màn signed-in dùng chung bảng màu xanh/sky/amber, font Plus Jakarta Sans, kiểu nút chính/ghost và surface theo Lumina EdTech. Chỉ dùng bố cục/màu từ tài liệu UI tham khảo; không thêm Google SSO, email, quên mật khẩu hoặc ghi nhớ đăng nhập.
- Có checklist thủ công trong [hướng dẫn test A–Z](HUONG_DAN_TEST_A_DEN_Z.md). Test tự động xác nhận logic/API; kiểm thử âm thanh, chia sẻ màn hình và mạng hai máy vẫn cần nhóm thực hiện trên thiết bị thật.
