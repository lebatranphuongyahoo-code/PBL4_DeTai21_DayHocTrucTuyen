# Học và kiểm thử bản lớp học web

## Kiến trúc hiện tại

Trình duyệt React gửi yêu cầu HTTP đến ứng dụng Java Spring Boot. Tomcat đã được nhúng trong JAR, không cần cài Tomcat riêng. Spring Security xác thực, controller nhận yêu cầu, service kiểm tra quyền và xử lý nghiệp vụ, SQLite lưu tài khoản và lớp. Có thể mô tả đây là kiến trúc client-server và backend phân lớp; không nên gọi toàn bộ hệ thống là MVC thuần. React đảm nhiệm giao diện; controller và service là các phần khác ở máy chủ.

Âm thanh, ảnh màn hình, chat và điều khiển phòng đi qua WebSocket đến Java server rồi được chuyển tiếp tới những người trong phòng. Đây không phải WebRTC, cũng không phải kết nối trực tiếp giữa hai trình duyệt. Khi thử từ hai mạng, Cloudflare Tunnel đưa HTTPS/WebSocket về máy chạy JAR. Máy chạy server và tunnel phải tiếp tục hoạt động; đường truyền, độ trễ và giới hạn của dịch vụ đều ảnh hưởng trải nghiệm.

## Lớp, buổi và lượt kết nối

Lớp là danh sách thành viên lâu dài. Tạo lớp chưa mở buổi; mỗi lần chủ lớp bắt đầu sau khi kết thúc tạo một buổi mới. Mở lớp sẽ hiện trang các buổi học; bấm Vào buổi học mới kết nối media. Mỗi buổi có thẻ riêng với thời gian, trạng thái và chat. Kết thúc hoặc rời buổi trở về trang lớp; chuyển menu Trang lớp/Danh sách lớp trong lúc học vẫn giữ kết nối.

Chủ lớp và thành viên hiện tại xem chat theo từng thẻ, phân trang 50 tin. Tin cũ chưa có mã buổi được giữ riêng. Chỉ chủ lớp thấy thống kê tham gia/lượt kết nối. Danh sách tải 50 buổi mỗi trang; lượt kết nối gần nhất hiển thị tối đa 200, còn thống kê tính tất cả lượt và gộp thời gian trùng giữa các tab. Đây là thời gian kết nối, không tự chứng minh người học có mặt/chú ý.

Nếu server bị dừng đột ngột, lượt chưa ghi nhận rời được đánh dấu gián đoạn khi server chạy lại; không có cách xác định chính xác thời gian rời thực tế. Các buổi có từ trước khi nâng cấp lịch sử có thời gian bắt đầu chưa xác minh. Lịch sử cũ trước khi có chức năng này không thể khôi phục. Khi đổi JAR, sao lưu cơ sở dữ liệu trước; bản JAR cũ có thể không hiểu trạng thái và dữ liệu mới.

## Chạy và kiểm thử

Yêu cầu Java 21, Maven và Node tương thích Vite. Tại thư mục dự án, build giao diện trước rồi đóng gói server:

```powershell
npm --prefix web ci
npm --prefix web run build
mvn -f backend/pom.xml verify
java -jar backend/target/classroom-web-server-0.1.0.jar --app.database=./data/classroom-web.db
```

Mở `http://127.0.0.1:8081`. Để thử hai mạng, dùng kịch bản trong `docs/TEST_HAI_MANG.md`; không chia sẻ mật khẩu hay dữ liệu thật khi demo. Chạy một tiến trình server duy nhất với mỗi tệp SQLite. Nếu cổng đang bận, dừng đúng tiến trình cũ hoặc chọn `--server.port=8082` và điều chỉnh tunnel tương ứng.

Kiểm tra: chủ lớp tạo lớp và thêm học viên; mở hai thẻ học viên, vào/rời riêng từng thẻ, rồi xem lịch sử; kết thúc và mở lại buổi, xác nhận có hai buổi; học viên không được xem lịch sử của chủ lớp. Dừng cưỡng bức server lúc một thẻ đang kết nối, chạy lại và kiểm tra trạng thái gián đoạn. Test thêm thu hồi thành viên, khóa/cấp mic và cấp/thu quyền trình bày.

Đo trên hai mạng thật với số người, thời gian, độ phân giải và mạng ghi rõ. Ghi nhận độ trễ hình/tiếng, gián đoạn và tài nguyên server theo từng kịch bản. Thử từ 2 người trước, tăng dần và dừng khi chất lượng không đạt. Chưa có phép đo thì không tuyên bố chịu được một số người dùng cụ thể; WebSocket truyền media qua server có thể tốn băng thông khi số người tăng.

## Chia việc cho ba thành viên

1. Tài khoản, lớp, quyền, SQLite và vòng đời buổi: giải thích API, kiểm thử quyền và migration.
2. WebSocket, đồng bộ, chia sẻ màn hình và âm thanh: giải thích luồng gói tin, kiểm tra điều khiển và đo chất lượng.
3. Giao diện React, triển khai JAR/tunnel, kịch bản demo và tổng hợp kết quả. Cả nhóm cần hiểu đường đi end-to-end và giới hạn của hệ thống.
