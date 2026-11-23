# Chính sách quyền theo lớp — 03/11/2026

## Quyết định đã được người dùng duyệt

Account đăng ký công khai vẫn là STUDENT; role ADMIN/TEACHER chỉ được cấp bằng operator cục bộ. Mọi account ACTIVE vẫn có thể tạo hoặc tham gia lớp theo chính sách owner/member. `classrooms.owner_id` quyết định chủ lớp, `classroom_members` quyết định thành viên. Người tạo lớp A có thể học ở lớp B. Chia sẻ là quyền tạm thời theo connection trong phòng; chủ lớp cấp/thu hồi, một nguồn trình bày tại một thời điểm. Presenter không được quản lý người khác. Quyền mic độc lập.

Không sao chép toàn bộ quyền Teams: Presenter Teams có thêm quyền điều hành; dự án dùng người trình bày với quyền chia sẻ tối thiểu. Admin hệ thống quản lý danh sách lớp, lưu trữ, đổi chủ lớp và chuyển học viên qua `/api/admin/*`, nhưng không tự có quyền điều hành media nếu chưa là thành viên phòng.

## Tương thích SQLite/API

Không thay schema account/lớp/member/session. `accounts.role` được dùng cho guard Admin và operator cấp quyền; public registration không nhận role đặc quyền. Public user vẫn trả USER cho tài khoản thường; member trong detail trả OWNER/MEMBER theo quan hệ lớp. Admin dùng các route riêng `/api/admin/rooms`, `/archive`, `/owner` và `/members/{account}/transfer`.

Rollback JAR cũ có thể chạy cùng DB vì schema giữ nguyên, nhưng account role cũ STUDENT đã tạo lớp trong chính sách mới sẽ bị hạn chế trên bản cũ. Rollback dữ liệu/chính sách cần đối chiếu lớp mới; không hứa rollback hành vi tương đương. Backup JAR backend/target/classroom-web-server-before-per-room-roles.jar.

## Test

9 test backend qua: account cũ STUDENT tạo lớp/owner; account cũ TEACHER tham gia lớp khác bằng mã hoặc direct add, MEMBER không archive/remove/add/review người khác; owner không tự join/remove/leave; người ngoài không media; quyền tồn tại sau restart và mật khẩu cũ login; xóa member không xóa quyền owner lớp khác. Real socket relay test dùng STUDENT cũ làm owner và TEACHER cũ làm member, vẫn chặn share/control trái phép, isolate phòng, revoke logout/archive. 3 resampler test qua. Frontend build qua.

Browser hai context: cả account demo cũ đều thấy Tạo lớp và Tham gia; demo_student tạo lớp và chia sẻ/điều hành; demo_teacher xin vào, được duyệt, chỉ có xin share/rút lớp, không archive; label đúng theo lớp. Không kiểm thử âm thanh thật hai mạng.

## Còn lại

Chưa có co-organizer hoặc audit lịch sử chuyển lớp. Admin UI hiện quản lý lớp và thành viên; Admin vẫn không được bypass `mediaAccess`. MediaControls hiện theo connection, mất grant khi rời; cần công khai hành vi này khi thiết kế quyền theo buổi.
