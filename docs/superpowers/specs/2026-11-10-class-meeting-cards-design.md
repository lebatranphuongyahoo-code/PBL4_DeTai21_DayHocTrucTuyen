# Trang lớp và thẻ buổi học

Thiết kế người dùng đã xác nhận trong chat bằng “Đúng vậy”: trang chính của từng lớp hiển thị các thẻ buổi học như ảnh tham chiếu; buổi đang mở có nút vào, buổi kết thúc có giờ bắt đầu/kết thúc, thời lượng và xem lại chat. Chỉ khi vào buổi đang mở mới hiện phòng mic/chia sẻ.

Hướng thực hiện: chuyển MeetingHistoryPanel ra trang lớp, trình bày từng buổi bằng thẻ và dùng lại MeetingChat/AttendanceTable. Giữ thống kê và lượt kết nối cho chủ lớp. Giữ tin cũ chưa rõ buổi trong mục riêng. Không cần API/schema mới. Phương án tạo một luồng bài đăng/kênh chat riêng sẽ thêm dữ liệu không thuộc yêu cầu; giữ lịch sử trong quản lý lớp không đáp ứng vị trí người dùng đã chọn.

Các quyết định giao diện của người thực hiện: chủ lớp bắt đầu sẽ vào học luôn; học viên bấm Vào buổi học. Điện thoại đỏ trở về trang lớp và ngắt riêng. Kết thúc buổi đưa mọi người về trang lớp; mở buổi tiếp theo không tự đưa người đang xem trang lớp vào học. Trang lớp và danh sách lớp giữ media nếu chỉ điều hướng khi đang học. Dùng màu tím xanh và kiểu chữ sẵn có, các thẻ trắng có đường nối thời gian, không thêm thư viện. Buổi bị đóng khi restart hoặc không rõ giờ bắt đầu không được hiển thị thời lượng chính xác giả.

Phạm vi: Classes, MeetingHistory, ClassManagement, CSS và kiểm thử điều hướng/trình bày. Không thêm ghi hình, transcript, lịch học hay sửa giao thức media.
