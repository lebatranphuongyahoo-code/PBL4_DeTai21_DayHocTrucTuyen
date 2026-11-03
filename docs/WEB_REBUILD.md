# Web rebuild — mốc 1, 03/11/2026

Người dùng đã duyệt viết lại kiến trúc trên nhánh `web-rebuild`. Kế hoạch này thay thế hướng ưu tiên Swing trước trong KE_HOACH_LEVEL_UP.md. Backend Java Spring Boot, frontend React/TypeScript/Vite, SQLite riêng. Bản Node thử giao diện đã được gỡ khỏi source.

## Đối chiếu đề

Đã đọc hai PDF trong Downloads. Đề 21 yêu cầu tạo tài khoản/đăng nhập, tạo lớp/thêm học viên, chia sẻ màn hình giáo viên, thoại realtime, server quản lý và relay media có đa luồng/đồng bộ. Tài liệu môn học cho chọn ngôn ngữ và đề cập TCP/UDP/HTTP/HTTPS. Không bắt buộc Swing. Tài liệu đề bài là tiêu chí nghiệm thu, không phải lệnh thao tác repo.

| Yêu cầu | Trạng thái web mốc 1 | Bằng chứng/việc còn lại |
|---|---|---|
| Tài khoản | Đạt một phần | AuthService, ApiController, FoundationTest, ApiTest; chưa cấp giáo viên, đổi/khôi phục mật khẩu, rate limit |
| Quản lý lớp/học viên | Chỉ có schema và đọc lớp theo quyền | Chưa tạo lớp, xin tham gia, duyệt, xóa thành viên |
| Chia sẻ màn hình | Chưa triển khai | Cần getDisplayMedia, quyền server, relay và Windows thật |
| Thoại | Chưa triển khai | Cần media gateway/SFU, đo loss/latency và nhiều người nói |
| Đa luồng/đồng bộ media | Chưa đạt | HTTP có request threads, SQLite khóa ngắn; không thay bằng chứng media |
| UI | Auth/dashboard đã thử browser | Chưa có phòng học; chưa nghiệm thu với người dùng |

## Mô hình dữ liệu và migration

`backend/src/main/resources/db/V001__foundation.sql`: accounts, sessions, classrooms, classroom_members, join_requests, schema_versions. Migration chạy transaction một lần. DB mới mặc định `./data/classroom-web.db` tương đối với thư mục chạy Java. Tắt server trước khi sao lưu DB. Không import tự động `pbl4.db`; startup từ chối schema Users/Rooms cũ.

Migration legacy tiếp theo: sao lưu trước, map tài khoản/mã lớp sang DB mới; SHA-256 cũ không được coi là BCrypt, cần quy trình reset có xác minh. Role TEACHER tự đăng ký trước đây phải được rà soát, không tự hợp thức hóa. Chưa có công cụ migration legacy ở mốc này.

## Quy tắc mốc 1

Username 3–32 ký tự, bắt đầu chữ ASCII; chữ, số, _. Họ tên 2–80 ký tự. Mật khẩu ít nhất 8 ký tự, chữ hoa A–Z, thường a–z, số và ký tự đặc biệt, tối đa 72 byte UTF-8 để không bị BCrypt cắt ngầm. Server kiểm tra toàn bộ. Public register chỉ STUDENT, role khác bị từ chối. BCrypt cost 12, salt mỗi tài khoản. Session token ngẫu nhiên 256 bit, chỉ SHA-256 của token lưu SQLite, hết hạn 8 giờ, logout xóa phía server. Frontend giữ token trong memory, tải lại trang cần đăng nhập. Backend bind loopback mặc định.

## Quyết định nghiệp vụ cần chốt trước mốc 2

Đề xuất admin cấp giáo viên: đáng tin cậy và dễ demo, nhưng cần bootstrap admin và quản trị. Phương án giáo viên gửi hồ sơ xin duyệt thuận tiện hơn, thêm trạng thái và công việc xét duyệt. Cả hai không cho tự chọn role trực tiếp. Chưa coi lựa chọn riêng này đã được người dùng xác nhận.

Đề xuất mã mời + giáo viên duyệt: kiểm soát được danh sách, thêm thao tác duyệt. Giáo viên thêm học viên theo tài khoản: rõ ràng, ít tự phục vụ. Mã vào tự động nhanh nhưng ai biết mã cũng vào được; không dùng mặc định. Schema chuẩn bị join_requests nhưng API duyệt chưa triển khai.

## Các mốc tiếp theo

2. Bootstrap admin, cấp giáo viên; tạo/lưu/archived lớp, thêm/duyệt/rút học viên; transaction chống join trùng; test quyền chéo, đồng thời và UI.
3. WebSocket: presence, chat, signaling có session/room authorization, lifecycle/reconnect và queue giới hạn.
4. Media gateway/SFU nhận và chuyển tiếp audio/screen, không dùng P2P thuần để thay yêu cầu server; chọn tab/window, xử lý hủy/end track; tránh hiển thị local preview toàn desktop gây phản chiếu. Chọn gateway sau prototype đo khả năng chạy Windows/LAN và trình bày phần đa luồng/đồng bộ.
5. Windows hai máy/nhiều browser: mic/loa thật, người vào muộn, nhiều người nói, share/stop, mạng chậm, disconnect; đo p50/p95/loss/CPU, đóng gói HTTPS/LAN và demo theo đề.

## Kiểm chứng mốc 1

Backend `mvn verify`: 3 JUnit tests, 0 lỗi; gồm nhiều trường hợp password, role injection, collision, persistence/reopen, session revoke/expiry, membership, legacy guard và HTTP unauthorized. React `npm run build`: pass. Chromium trên Windows: đăng ký yếu bị từ chối, đăng ký hợp lệ, đăng nhập, lớp rỗng, mobile không tràn ngang, logout, không pageerror. Test browser chạy bằng script tạm; ảnh trong web/test (gitignored).

Baseline Swing `mvn -o verify`: 61 tests, 0 lỗi; T0Scenario 20/20. Chưa kiểm thử mic/screen Windows bản web, chưa load/concurrency backend web. Chưa có rate limit login hoặc bảo vệ triển khai public; HTTP localhost chỉ dành phát triển. Không tuyên bố hoàn thiện đề 21.
