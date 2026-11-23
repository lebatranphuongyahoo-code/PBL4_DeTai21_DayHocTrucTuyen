# Kế hoạch trang lớp và thẻ buổi học

Spec: ../specs/2026-11-10-class-meeting-cards-design.md. Người dùng đã xác nhận giao diện và yêu cầu thực hiện trong workspace hiện tại. Thực hiện trực tiếp, giữ các thay đổi có sẵn, không commit/push, không chạm dữ liệu thật hoặc server 8081.

- [x] 1. Kiểm thử RED rồi GREEN: mỗi buổi là thẻ, trạng thái kết thúc, thời lượng, không tính chính xác khi restart/không rõ giờ bắt đầu; nút vào chỉ dành cho buổi hiện tại. Chuyển MeetingHistoryPanel thành thẻ, giữ phân trang/chat và thống kê chủ lớp.
- [x] 2. Kiểm thử RED rồi GREEN: mở lớp chỉ xem trang lớp, vào học mới mount media; điều hướng giữ media; rời/kết thúc trở về trang lớp; không tự nối buổi mới; đổi lớp khi chưa vào học được phép. Classes quản lý selected room, joined meeting ID và view; bỏ lịch sử trùng trong ClassManagement. CSS cho trang lớp, thẻ và mobile theo giao diện đang có.
- [x] 3. Chạy toàn bộ frontend tests/build, review độc lập theo executing-plans, đóng gói JAR classroom-meeting-cards.jar; kiểm tra hai tài khoản bằng agent-browser trên database QA sao chép/cổng riêng; cập nhật hướng dẫn và báo cáo.

Review focus: không nối media khi chỉ xem lớp; kết thúc hiển thị thẻ ngay; chuyển menu không ngắt mic; buổi mới không tự nhận kết nối cũ; thời lượng không giả khi restart. Reuse API phân quyền hiện có, không migration.

Ledger: bắt đầu từ các thay đổi chat theo buổi đã kiểm tra ở lượt trước. Giữ workspace vì thay đổi hiện tại chưa commit là nền tảng của yêu cầu; không di chuyển hoặc nhân bản chúng. Task 1/2 chia sẻ props onJoin/currentMeetingId/meetingOpen; UI dùng đúng mã buổi để không nhầm kết nối. Task 3 kiểm tra artifact chứa bundle mới.

Hoàn tất: 19 frontend tests và build qua; review độc lập có hai phát hiện đã tái hiện RED/GREEN và sửa; hai phiên agent-browser trên JAR cuối, desktop/mobile, đã dọn server QA. Báo cáo: ../../reports/class-meeting-cards-2026-11-10.md.
