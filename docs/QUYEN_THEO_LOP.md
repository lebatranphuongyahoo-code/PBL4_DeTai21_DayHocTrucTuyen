# Chính sách quyền theo lớp — 03/11/2026

## Quyết định đã được người dùng duyệt

Account thông thường; mọi account ACTIVE được tạo và tham gia lớp. classrooms.owner_id quyết định chủ lớp, classroom_members quyết định thành viên. Người tạo lớp A có thể học ở lớp B. Chia sẻ là quyền tạm thời theo connection trong phòng; chủ lớp cấp/thu hồi, một nguồn trình bày tại một thời điểm. Presenter không được quản lý người khác. Quyền mic độc lập.

Không sao chép toàn bộ quyền Teams: Presenter Teams có thêm quyền điều hành; dự án dùng người trình bày với quyền chia sẻ tối thiểu. Admin hệ thống là chức năng vận hành riêng, không tự có quyền điều hành mọi phòng.

## Tương thích SQLite/API

Không thay schema hoặc viết lại account/lớp/member/session. Cột accounts.role cũ giữ để rollback nhưng không dùng cấp quyền. Public user role trả USER; member trong detail trả OWNER/MEMBER theo quan hệ lớp. room list thêm owned. --grant-teacher được giữ như option ngừng sử dụng, chỉ thông báo và không cập nhật DB. Không dùng CLI cũ để cấu hình nghiệp vụ mới.

Rollback JAR cũ có thể chạy cùng DB vì schema giữ nguyên, nhưng account role cũ STUDENT đã tạo lớp trong chính sách mới sẽ bị hạn chế trên bản cũ. Rollback dữ liệu/chính sách cần đối chiếu lớp mới; không hứa rollback hành vi tương đương. Backup JAR backend/target/classroom-web-server-before-per-room-roles.jar.

## Test

9 test backend qua: account cũ STUDENT tạo lớp/owner; account cũ TEACHER tham gia lớp khác bằng mã hoặc direct add, MEMBER không archive/remove/add/review người khác; owner không tự join/remove/leave; người ngoài không media; quyền tồn tại sau restart và mật khẩu cũ login; xóa member không xóa quyền owner lớp khác. Real socket relay test dùng STUDENT cũ làm owner và TEACHER cũ làm member, vẫn chặn share/control trái phép, isolate phòng, revoke logout/archive. 3 resampler test qua. Frontend build qua.

Browser hai context: cả account demo cũ đều thấy Tạo lớp và Tham gia; demo_student tạo lớp và chia sẻ/điều hành; demo_teacher xin vào, được duyệt, chỉ có xin share/rút lớp, không archive; label đúng theo lớp. Không kiểm thử âm thanh thật hai mạng.

## Còn lại

Chưa có meeting entity/persistence kết thúc buổi, co-organizer hoặc admin UI. Có thể bổ sung sau; không cần viết lại ba service chỉ để có đủ tên Account/Class/Meeting Manager. MediaControls hiện theo connection, mất grant khi rời; cần công khai hành vi này khi thiết kế quyền theo buổi.
