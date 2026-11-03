# Kiểm tra giao diện và thao tác quản lý lớp — 03/11/2026

## Lỗi xác định và sửa

- CSS `.chat-panel form/input` áp dụng lên form quản lý lồng trong cột chat, ép nút thêm học viên và ô nhập nhỏ. Giới hạn CSS vào `.chat-content`; tách quản lý ra native dialog rộng tối đa 1060 px.
- Số kết nối media bị ghi nhãn Thành viên trong khi danh sách SQLite bao gồm chủ lớp và học viên đăng ký. Đổi tab thành Trong phòng; dialog ghi rõ số đăng ký gồm chủ lớp.
- Nút điều hướng Phòng học chỉ cuộn về vùng đang mở. Chuyển thành chỉ báo trang hiện tại; Tạo lớp/Tham gia đưa focus đến input thực tế. Loại bỏ các nút header ẩn bị trùng.
- Logout lỗi hiển thị trong nhánh đăng nhập đã ẩn. Truyền lỗi và trạng thái bận tới màn hình đang đăng nhập.
- Lệnh điều hành khi socket đóng có thể im lặng. Thêm thông báo; đưa trạng thái media và Kết nối lại lên đầu màn hình.
- Xóa thành viên thiếu xác nhận, thêm trùng không có phản hồi rõ: thêm xác nhận và thông báo. Lưu trữ ghi rõ dừng truy cập dài hạn, chưa có khôi phục UI.

Không đổi schema SQLite, chính sách role, cơ chế PCM/JPEG hay dữ liệu remote-test.db.

## Bằng chứng kiểm thử

Frontend TypeScript/Vite build; 3 test audio resampler; Maven verify 7 test backend đều qua.

Ba kịch bản trình duyệt riêng chạy ở localhost:8091 với database kiểm thử và tài khoản thử:

1. Quản lý: điều hướng focus, fullscreen vào/ra, sao chép clipboard, tìm thành viên/không có kết quả, username không tồn tại, thêm trực tiếp/trùng, hủy và xác nhận xóa, kết nối bị thu hồi khi xóa, từ chối/xin lại/duyệt yêu cầu, rút lớp, hủy/xác nhận lưu trữ, lỗi logout hiển thị và logout thành công. API thêm/lưu trữ/xem yêu cầu do học viên gọi trả 403.
2. Media: xin/duyệt/thu hồi chia sẻ, ảnh học viên tới chủ lớp; khóa mic/xin/duyệt/thu hồi/mở lại; âm thanh giả lập hai chiều và tắt bật mic; chat; không pageerror.
3. Đường lỗi: mật khẩu nhập lại sai, mật khẩu yếu bị server từ chối; getUserMedia từ chối; hủy getDisplayMedia; bật tắt loa; đóng socket cưỡng bức, nút media vô hiệu hóa rồi Kết nối lại phục hồi.

Dialog ở viewport 1440/900/390 có chiều rộng 1060/860/370 px, input 15 px, nút submit cao 42 px, không tràn ngang. Ảnh trong web/test/management-1440.png, management-900.png, management-390.png (gitignored). Kiểm tra hình học DOM và chụp ảnh tự động không thay đánh giá thị giác thủ công.

## Giới hạn và việc còn lại

Đây là kiểm thử các kịch bản cụ thể, không chứng minh toàn bộ chương trình không lỗi. Bài test trình duyệt đặt ở thư mục TEMP, không phải bộ CI lâu dài.

- Lỗi âm thanh một chiều trên hai Windows/mạng khác nhau cần test 10–15 phút với mic/tai nghe thật; giả lập không xác nhận được chất lượng nghe.
- Chưa đo tải 3–5 người trở lên, latency, nghẽn mạng và phối hợp audio/screen dài hạn.
- Danh sách quản lý/yêu cầu dùng Làm mới, chưa push realtime. Tab Trong phòng có presence realtime. Một account có thể mở nhiều connection.
- Khóa mic cả lớp có ngoại lệ; chưa hỗ trợ mute riêng một người khi mic cả lớp đang mở.
- Chưa có kết thúc một buổi giữ nguyên lớp, khôi phục lớp lưu trữ, khôi phục mật khẩu và quản lý account bằng UI. Đây là thiếu chức năng đã ghi nhận, không dựng nút giả.
- Phân quyền TEACHER/STUDENT toàn hệ thống còn hạn chế giáo viên học ở lớp khác. Thay chính sách cần một giai đoạn migration/API/test riêng.

Rà soát dùng source/API/test backend cùng browser regression và cần nghiệm thu media trên máy thật. Không có công cụ đơn lẻ kiểm tra tất cả hoặc tự đảm bảo hết lỗi.
