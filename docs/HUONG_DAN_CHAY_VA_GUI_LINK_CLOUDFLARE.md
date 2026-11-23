# Hướng dẫn chạy Classroom và gửi link Cloudflare cho nhóm

Tài liệu này dành cho máy của bạn đóng vai trò **máy chủ buổi học**. Chỉ máy này cần chạy Java và Cloudflare Tunnel. Các thành viên còn lại chỉ mở link bằng trình duyệt.

## 1. Chuẩn bị một lần

Kiểm tra ba lệnh sau trong PowerShell:

```powershell
java -version
mvn -version
cloudflared version
```

Nếu `cloudflared` chưa có trên Windows, tải bản Windows 64-bit từ [trang tải chính thức của Cloudflare](https://developers.cloudflare.com/tunnel/downloads/) rồi cài đặt. Đóng và mở lại PowerShell sau khi cài để lệnh được nhận.

## 2. Build bản web mới

Mở PowerShell tại thư mục dự án:

```powershell
cd E:\PBL4_DeTai21
npm --prefix web run build
mvn -f backend/pom.xml '-Dserver.artifact.name=classroom-meeting-cards' -DskipTests package
```

Sau khi build, file cần chạy là:

```text
E:\PBL4_DeTai21\backend\target\classroom-meeting-cards.jar
```

Mỗi khi sửa frontend, phải chạy lại cả npm build và đóng gói JAR trước khi chạy. Nếu không, server có thể vẫn dùng giao diện cũ.

## 3. Khởi động server

Mở **PowerShell cửa sổ 1** và chạy:

```powershell
cd E:\PBL4_DeTai21
$env:PORT="8081"
$env:BIND_ADDRESS="127.0.0.1"
$env:PBL4_WEB_DB="E:\PBL4_DeTai21\data\classroom-team.db"
java -jar .\backend\target\classroom-meeting-cards.jar
```

Giữ nguyên cửa sổ này trong suốt buổi test. Khi thấy dòng có nội dung tương tự `Tomcat started on port 8081`, server đã sẵn sàng.

Mở trình duyệt trên chính máy chủ và kiểm tra trước:

```text
http://127.0.0.1:8081
```

Nếu trang đăng nhập hiện ra thì tiếp tục bước 4. Nếu cổng 8081 đã bị dùng, đổi cả `$env:PORT` và địa chỉ trong lệnh Cloudflare ở bước 5 sang cùng một cổng khác, ví dụ `8090`.

## 4. Tạo lớp và chuẩn bị buổi học

Trên trang web local:

1. Bấm **Tạo tài khoản**, điền họ tên, tên tài khoản, mật khẩu và bấm **Tạo tài khoản**.
2. Đăng nhập bằng tài khoản vừa tạo.
3. Bấm **Tạo lớp mới**.
4. Nhập tên lớp, ví dụ `PBL4 Nhóm 4`, rồi bấm **Tạo lớp**.
5. Bấm **Mở lớp** ở thẻ lớp vừa tạo.
6. Bấm **Quản lý lớp**.
7. Ở mục **Mời học viên**, bấm **Sao chép mã**.
8. Giữ mã này để gửi cho các bạn trong nhóm.

Lớp mới chưa mở buổi học. Chủ lớp bấm **Bắt đầu buổi học** trên trang lớp. Học viên cần có tên trong lớp và buổi học đang mở mới kết nối được. Khi buổi học mở, học viên có thể rời rồi vào lại. Chủ lớp bấm **Kết thúc buổi học** để ngắt mọi người và chặn kết nối đến lần bắt đầu tiếp theo. Giao diện đang mở tự kiểm tra trạng thái mỗi 5 giây.

Buổi học tự đóng nếu chủ lớp không kết nối media lại trong 10 phút, hoặc khi server khởi động lại. Sau đó chủ lớp cần bấm Bắt đầu buổi học. Tài khoản, thành viên, chat và lịch sử vẫn được giữ. Khóa mic riêng áp dụng cho mọi tab của cùng tài khoản đến khi được mở khóa hoặc kết thúc buổi.

Chat được tách theo từng buổi. Rời rồi vào lại cùng buổi vẫn thấy chat; bắt đầu buổi mới có chat trống. Ngay trên **Trang lớp → Các buổi học**, mỗi buổi có thẻ với thời gian, trạng thái và nút **Xem tin nhắn**. Buổi đang mở có nút **Vào buổi học**; chỉ bấm nút này mới kết nối mic/chia sẻ. Tin từ phiên bản cũ nằm riêng ở **Tin nhắn cũ chưa xác định buổi**.

Lưu ý các nút trong phòng:

- **Bật loa**: cho phép nghe âm thanh từ người khác.
- **Bật mic**: phát âm thanh của máy bạn sau khi được phép.
- **Chia sẻ bài giảng**: chọn cửa sổ hoặc màn hình để gửi hình ảnh.
- **Trong phòng & điều hành**: xem thành viên và các yêu cầu xin quyền.
- **Rời buổi học**: chỉ ngắt kết nối của mình và trở về trang lớp, kể cả khi mình là chủ lớp.
- **Kết thúc buổi học**: chủ lớp đóng buổi học cho tất cả; nút nằm ngay phía trên khu vực bài giảng.
- **Lớp học** hình ngôi nhà: về danh sách lớp nhưng vẫn giữ buổi học đang kết nối.
- **Trang lớp**: xem các thẻ buổi học; vẫn giữ kết nối nếu bạn đang học.
- **Phòng học**: quay lại buổi học đang chạy.

Không bấm **Lưu trữ lớp học** trong buổi test nếu chưa muốn vô hiệu hóa lớp cho tất cả thành viên.

## 5. Tạo link Cloudflare tạm thời

Mở **PowerShell cửa sổ 2**, không đóng cửa sổ 1, rồi chạy:

```powershell
cloudflared tunnel --url http://127.0.0.1:8081
```

Cloudflare sẽ in ra một URL dạng:

```text
https://ten-ngau-nhien.trycloudflare.com
```

Copy nguyên URL bắt đầu bằng `https://` và gửi cho nhóm. Mọi người dùng đúng URL này, không dùng `127.0.0.1` hoặc `localhost` vì hai địa chỉ đó chỉ trỏ về máy của từng người.

Link Quick Tunnel là link tạm thời. Nó chỉ hoạt động khi cả server Java ở cửa sổ 1 và lệnh `cloudflared` ở cửa sổ 2 còn chạy. Dừng một trong hai cửa sổ là link ngừng hoạt động; chạy lại sẽ thường tạo URL khác.

## 6. Hướng dẫn cho từng thành viên trong nhóm

Gửi cho mỗi bạn các bước sau:

1. Mở link Cloudflare bằng Chrome hoặc Edge.
2. Bấm **Tạo tài khoản** nếu chưa có tài khoản.
3. Đăng nhập.
4. Bấm **Nhập mã lớp**.
5. Dán mã lớp mà chủ lớp đã gửi, rồi bấm gửi yêu cầu.
6. Chờ chủ lớp mở **Quản lý lớp** và bấm **Duyệt**.
7. Bấm **Lớp học** để cập nhật danh sách nếu lớp chưa xuất hiện.
8. Bấm **Mở lớp**, sau đó **Vào buổi học** khi chủ lớp đã mở buổi.
9. Nếu cần nghe, bấm **Bật loa**. Nếu cần nói, gửi yêu cầu phát biểu hoặc chờ chủ lớp cấp quyền rồi bấm **Bật mic**.
10. Khi kết thúc, bấm nút điện thoại đỏ **Rời buổi học**.

Mỗi người phải dùng tài khoản riêng. Không gửi token, mật khẩu hoặc file database cho nhóm.

## 7. Kịch bản test nhóm nên chạy

Chủ lớp và ít nhất một học viên cùng mở link:

1. Học viên vào lớp và xuất hiện trong mục **Trong phòng**.
2. Chủ lớp gửi một tin nhắn ở tab **Tin nhắn**.
3. Học viên xác nhận nhận được tin nhắn.
4. Chủ lớp bấm biểu tượng nhà **Lớp học**.
5. Xác nhận không có hộp thoại hỏi rời buổi học.
6. Chủ lớp bấm **Phòng học** và vẫn thấy phòng, chat và danh sách người trong phòng.
7. Chủ lớp bấm **Rời buổi học**.
8. Học viên xác nhận chủ lớp đã rời phòng.
9. Chủ lớp vào lại và bấm **Kết thúc buổi học**, xác nhận hộp thoại. Học viên phải bị ngắt kết nối và không bật mic/chat được.
10. Học viên rời rồi vào lại vẫn phải chờ. Chủ lớp bấm **Bắt đầu buổi học**; học viên đang chờ được kết nối trong khoảng 5 giây.

Nếu chỉ test giao diện, không cần bật mic hoặc chia sẻ màn hình. Nếu test âm thanh/hình ảnh, trình duyệt sẽ hỏi quyền microphone hoặc screen capture; chỉ cấp quyền cho link Cloudflare của nhóm.

## 8. Xử lý lỗi thường gặp

**Link không mở được**

- Kiểm tra cửa sổ 1 còn chạy Java.
- Kiểm tra cửa sổ 2 còn dòng `cloudflared tunnel --url ...`.
- Mở lại đúng URL `https://...trycloudflare.com`.

**Trang mở nhưng API báo lỗi**

- Đảm bảo server và tunnel dùng cùng cổng, ví dụ đều là `8081`.
- Không chạy frontend bằng một link khác với server JAR.
- Build lại JAR nếu vừa sửa code.

**Không gửi được chat hoặc không thấy người trong phòng**

- Không mở bằng `file:///...`.
- Dùng đúng link Cloudflare có `https://`.
- Không mở nhiều tab bằng cùng một tài khoản khi đang kiểm thử.
- Bấm tải lại trang, đăng nhập lại và vào lớp lại.

**Mic hoặc chia sẻ màn hình không hoạt động**

- Cho phép microphone khi trình duyệt hỏi.
- Chủ lớp phải cấp quyền tương ứng nếu hệ thống đang khóa mic hoặc quyền trình bày.
- Không đóng tab phòng học trong lúc đang chia sẻ.

## 9. Kết thúc buổi test

1. Mỗi thành viên bấm **Rời buổi học**.
2. Chủ lớp có thể đóng trình duyệt.
3. Quay lại PowerShell cửa sổ 2 và nhấn `Ctrl+C` để dừng Cloudflare.
4. Quay lại PowerShell cửa sổ 1 và nhấn `Ctrl+C` để dừng Java.

Không đưa file `data\classroom-team.db` lên GitHub hoặc gửi vào nhóm chat. File này chứa dữ liệu tài khoản và lớp thử nghiệm.

### Ghi chú về triển khai lâu dài

Quick Tunnel chỉ phù hợp cho demo và test nhóm. Khi cần link ổn định, dùng Cloudflare Tunnel có tài khoản/domain, chạy tunnel dạng named tunnel và tạo file cấu hình riêng. Không cần làm bước đó cho buổi test hiện tại.

Tài liệu Cloudflare: [Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/), [WebSockets](https://developers.cloudflare.com/network/websockets/), [Tải cloudflared](https://developers.cloudflare.com/tunnel/downloads/).
