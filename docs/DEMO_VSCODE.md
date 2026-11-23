# Demo PBL4 trong VS Code

## Chạy ứng dụng

Trong VS Code, mở thư mục `E:\PBL4_DeTai21`, chọn **Terminal → New Terminal**, rồi chạy lần lượt trong PowerShell:

```powershell
.\scripts\build-web.ps1
java -jar .\backend\target\classroom-web-server-0.1.0.jar
```

Chờ thấy `Tomcat started on port 8081`, rồi mở <http://127.0.0.1:8081>. Giữ terminal server mở suốt lúc demo. Dừng bằng `Ctrl+C`.

Script build kiểm tra TypeScript, chạy test frontend/backend và đóng gói giao diện vào JAR. Nếu Java server đang chạy, dừng nó bằng `Ctrl+C` trước khi build lại.

## Trình tự demo

1. Tạo tài khoản chủ lớp và đăng nhập.
2. Tạo lớp, chọn **Mở lớp**, rồi bấm **Bắt đầu buổi học**.
3. Ở **Quản lý lớp**, sao chép mã mời. Tạo/đăng nhập tài khoản học viên trong cửa sổ trình duyệt riêng, nhập mã và gửi yêu cầu.
4. Chủ lớp duyệt học viên. Học viên mở lớp và bấm **Vào buổi học**.
5. Gửi tin nhắn hai chiều; có thể bật loa/mic hoặc chia sẻ bài giảng. Trình duyệt thường cần quyền mic và chia sẻ màn hình.
6. Chủ lớp kết thúc buổi. Mở **Xem tin nhắn** trên thẻ buổi để xem lại chat.

Nếu demo qua hai mạng, mở terminal thứ hai, chạy `cloudflared tunnel --url http://127.0.0.1:8081`, rồi gửi URL HTTPS do Cloudflare in ra. Cả terminal server và tunnel phải tiếp tục chạy.

## SQLite

Không cần cài hoặc bật dịch vụ SQL Server. SQLite là cơ sở dữ liệu nhúng trong ứng dụng Java; nó tự mở/tạo file `data/classroom-web.db` tại thư mục PowerShell hiện hành. Các lớp, tài khoản, buổi học và chat được lưu trong file đó.

Không xóa hoặc thay file `.db` khi server đang chạy. Sao lưu: dừng server bằng `Ctrl+C`, rồi copy `data/classroom-web.db`. Để xem bảng trong VS Code, có thể cài extension **SQLite** của alexcvzz và mở `data/classroom-web.db`; không cần extension để ứng dụng hoạt động.

Nếu đặt biến môi trường `PBL4_WEB_DB`, ứng dụng dùng đường dẫn đó thay cho `data/classroom-web.db`. Giữ cùng đường dẫn mỗi lần chạy để tiếp tục dùng đúng dữ liệu.
