# Chat theo buổi học

Yêu cầu đã thống nhất trong chat: mỗi lớp có nhiều buổi; buổi mới có chat mới; rời/vào lại cùng buổi giữ chat; buổi đóng chỉ đọc; chủ lớp và thành viên hiện tại xem lại chat theo buổi. Giữ nguyên các tin cũ chưa có mã buổi trong mục riêng, không suy đoán bằng thời gian.

- [x] Thêm kiểm thử tách hai buổi, truy cập lịch sử, phân trang, từ chối ghi vào buổi đã đóng/sai lớp, và migration không mất tin cũ.
- [x] Thêm session_id nullable vào class_messages; lưu buổi khi ghi; API đọc theo buổi, mục tin cũ session=0; gửi và relay kiểm tra buổi của socket.
- [x] Cho thành viên đọc danh sách buổi và chat; lịch sử kết nối và thống kê vẫn chỉ chủ lớp xem. UI phòng chỉ hiện chat buổi mới nhất; quản lý lớp có lịch sử chat cho cả hai vai trò.
- [x] Kiểm thử backend/frontend, build, review độc lập theo executing-plans, chạy JAR riêng và hai phiên agent-browser. Không thao tác server 8081 hoặc dữ liệu thật, xuất JAR tên classroom-session-chat.jar.

Quyết định: thành viên hiện tại được xem chat các buổi cũ như quyền đọc chat chung trước đây. Dữ liệu cũ không thể xác định chắc buổi sẽ được giữ riêng. Không thêm lịch học, giới hạn số buổi hay kênh chat chung mới.

Kết quả và bằng chứng: [báo cáo kiểm tra](../../reports/meeting-chat-2026-11-10.md). Review phát hiện handshake có thể vào nhầm buổi khi A đóng/B mở; đã thêm regression tái hiện lỗi rồi kiểm tra bản sửa.
