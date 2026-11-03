# Test hai máy ở hai mạng khác nhau

Máy A (bạn) chạy Java server và cloudflared. Máy B (bạn cùng nhóm) dùng Chrome/Edge mở cùng URL HTTPS. Không chạy hai server riêng rồi mong thấy cùng lớp: SQLite/membership/media nằm trên server chung.

## 1. Kéo source

Clone vào thư mục mới để không ảnh hưởng checkout có chỉnh sửa:

```powershell
git clone --branch web-rebuild --single-branch https://github.com/lebatranphuongyahoo-code/PBL4_DeTai21_DayHocTrucTuyen.git PBL4-Web-Test
cd PBL4-Web-Test
git branch --show-current
git log -1 --oneline
```

Máy B có thể kéo source như trên, nhưng để tham gia lớp chỉ cần browser và URL chung. Nếu repo private, chủ repo phải cấp quyền GitHub cho bạn cùng nhóm. Không dùng git reset/checkout ép trên checkout đang có sửa chưa lưu.

## 2. Build trên máy A

Cài JDK21, Maven3.9+, Node22.12+ (hoặc bản tương thích Vite7), Git. Dừng server JAR cũ trước khi build.

```powershell
java -version
mvn -version
node --version
cd web
npm ci
cd ..
./scripts/build-web.ps1
```

Nếu Maven báo cache C:/.m2 không truy cập được, dùng script với -MavenRepository trỏ đến cache của tài khoản Windows hiện tại. Ví dụ máy ADMIN: ./scripts/build-web.ps1 -MavenRepository C:/Users/ADMIN/.m2/repository. Build phải đạt trước khi test. Nếu script PowerShell bị policy chặn, chạy thủ công npm run build trong web rồi mvn -f backend/pom.xml verify ở gốc; không cần hạ execution policy toàn máy.

## 3. Khởi động server thử và tạo tài khoản người dạy

Terminal A, giữ thư mục gốc repo:

```powershell
java -jar backend/target/classroom-web-server-0.1.0.jar --app.database=./data/remote-test.db
```

Mở http://127.0.0.1:8081, đăng ký username teacher_test, họ tên rõ ràng, mật khẩu thử riêng đáp ứng chính sách. Chưa phải teacher cho đến bước cấp quyền. Database này riêng với dữ liệu cũ; luôn dùng đúng --app.database=./data/remote-test.db trong mọi lần restart.

## 4. Máy A mở tunnel HTTPS tạm

Cài cloudflared từ trang chính thức: https://developers.cloudflare.com/tunnel/downloads/ (chọn Windows x64 phù hợp máy). Nếu download file cloudflared-windows-amd64.exe, đổi tên cloudflared.exe; mở PowerShell tại thư mục chứa file đó.

Terminal B:

```powershell
./cloudflared.exe tunnel --url http://127.0.0.1:8081
```

Nếu đã cài và có PATH, dùng cloudflared tunnel --url http://127.0.0.1:8081. Copy URL HTTPS *.trycloudflare.com thật được in ra. URL ví dụ không hoạt động. Giữ terminal B mở. Tunnel là dịch vụ ngoài chuyển tiếp traffic, không thay backend Java. Không cần port forwarding router. URL thay đổi sau mỗi lần tạo tunnel mới.

## 5. Cấp teacher và cho phép đúng origin

Terminal A: Ctrl+C dừng Java. Không dừng terminal B. Khởi động lại cùng DB, thay URL ví dụ bằng URL thật, không có slash cuối:

```powershell
$publicUrl = 'https://TEN-THAT.trycloudflare.com'
java -jar backend/target/classroom-web-server-0.1.0.jar --app.database=./data/remote-test.db --grant-teacher=teacher_test "--app.allowed-origins=$publicUrl"
```

Chỉ dùng --grant-teacher lần đầu. Lần sau restart bỏ tham số đó (tài khoản đã là teacher, lệnh cấp lại sẽ bị từ chối). Lệnh lần sau:

```powershell
java -jar backend/target/classroom-web-server-0.1.0.jar --app.database=./data/remote-test.db "--app.allowed-origins=$publicUrl"
```

Không dùng profile lan cho cách tunnel này: HTTPS do Cloudflare phục vụ, Java loopback vẫn HTTP. Không đặt allowed origins = * để bỏ kiểm tra.

## 6. Cả hai dùng cùng URL HTTPS

Máy A và B mở URL thật. Kiểm tra URL/api/health trả ok=true. Máy A login teacher_test. Máy B đăng ký student_test trên URL đó rồi login. Nếu cả hai đã login localhost từ trước, logout/login lại trên HTTPS. Không dùng localhost của máy B để kết nối máy A. Dùng mật khẩu/tài liệu thử riêng: URL tunnel công khai với người biết link, traffic qua Cloudflare; không dùng thông tin nhạy cảm.

## 7. Test lớp và quyền

1. A tạo lớp Toán - Test hai mạng; B chưa thấy lớp.
2. A gửi mã lớp trong UI cho B. B nhập mã gửi yêu cầu; chưa được vào.
3. A vào lớp, Làm mới thành viên/yêu cầu, Duyệt student_test.
4. B Làm mới danh sách, Vào lớp. Cả hai thấy kết nối thành công; nút mic/loa bật được. B không có nút share giáo viên.
5. Test thêm trực tiếp: A thêm username một học viên đã đăng ký; học viên được vào không cần gửi mã. Nếu chỉ có hai người, tạo thêm tài khoản thử trong cửa sổ ẩn danh.

## 8. Test media theo thứ tự

1. Hai người dùng tai nghe, không mở cùng lớp nhiều tab có mic/loa cùng bật. Tắt mic ứng dụng họp khác khi đo.
2. Cả hai Bật loa. A Bật mic, B cho biết có nghe câu đếm 1-10 không. Đảo lại B nói A nghe.
3. A Tắt mic nhưng giữ loa; A vẫn phải nghe B. B kiểm tra tương tự.
4. Hai người cùng nói ngắn: kiểm tra vẫn nghe được hai chiều, không mất một phía.
5. A mở PowerPoint/PDF/tab bài giảng khác, Chia sẻ bài giảng, chọn cửa sổ/tab tài liệu. Không chọn desktop hoặc chính cửa sổ lớp. B thấy slide và thay đổi nội dung. Phía A ẩn preview là chủ ý tránh phản chiếu.
6. A Dừng chia sẻ: B trở về trạng thái chờ, không giữ frame cuối. Share lại rồi dừng từ thanh browser: kết quả tương tự. Hủy picker: mic/loa/phòng vẫn hoạt động.
7. Trong khi share, A nói/B trả lời; ghi nhận đứng hình/đứt tiếng/vọng/trễ. B reload rồi login/vào lại khi A còn share: phải thấy frame tiếp theo.
8. Chat câu hỏi từ mỗi bên, phía kia nhận đúng.

## 9. Test thu hồi quyền và reconnect

1. A Xóa student_test khỏi lớp: B media phải ngắt; reconnect không được bypass membership.
2. A thêm lại, B quay Danh sách lớp/Làm mới rồi vào.
3. B Rút khỏi lớp: lớp mất khỏi danh sách; cần được thêm/duyệt lại.
4. A Lưu trữ lớp: mọi người bị ngắt, lớp không còn trong danh sách active.
5. Tạo lớp khác để test, B tắt mạng vài giây rồi bật; UI phải báo ngắt, mic/share dừng, dùng Kết nối lại hoặc vào lại lớp.
6. Dừng/restart Java cùng DB (bỏ --grant-teacher): tài khoản/lớp/membership còn, phiên media phải nối lại.

## 10. Lỗi thường gặp và kết thúc

| Hiện tượng | Kiểm tra |
|---|---|
| Port 8081 occupied | Dừng đúng server thử cũ; không chạy server thứ hai cùng port |
| Tunnel 502 | Java có chạy không, localhost:8081/api/health có ok không |
| UI mở được, media không nối | --app.allowed-origins phải đúng HTTPS origin tunnel hiện tại; F12 Network WS xem handshake 403/101 |
| Không mic/share | Đúng HTTPS, Chrome/Edge; cho phép mic của site và Windows; chọn đúng device |
| Không nghe | Bật loa bằng nút, volume/headphone output; browser cho phép audio |
| Đăng ký tài khoản cũ rồi mà không thấy | Cùng URL/server/database, không dùng DB localhost máy B |
| Trễ qua tunnel | Ghi nhận triệu chứng, mạng/upload; không kết luận đạt độ trễ chỉ vì kết nối được |

Ghi: bước, máy A/B, browser, giờ, kỳ vọng/thực tế, ảnh lỗi UI và log Java/cloudflared/Network. Không gửi password hoặc session ticket/token trong log. Test đủ bước mới đánh dấu đạt. Chưa đo p50/p95/loss và nghiệm thu hai mạng thật trong môi trường nhóm.

Xong test: Ctrl+C terminal cloudflared, Ctrl+C Java. URL ngừng truy cập khi tunnel dừng. Không để prototype public liên tục (chưa có rate limit login/register).

Nguồn: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/ ; https://developers.cloudflare.com/network/websockets/ ; https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getDisplayMedia .
