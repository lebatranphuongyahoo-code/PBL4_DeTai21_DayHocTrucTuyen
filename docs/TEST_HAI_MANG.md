# Test cả nhóm ở các mạng khác nhau

Máy A chạy một Java server và một tunnel. B/C chỉ cần Chrome/Edge, tai nghe và URL HTTPS của A; không chạy server riêng. Public registration không chọn role. Người vận hành cấp một tài khoản ADMIN và một tài khoản TEACHER bằng CLI; Admin quản lý lớp, Teacher điều hành buổi học. Người được thêm/duyệt là thành viên và có thể xin quyền trình bày.

## 1. Chuẩn bị trên máy A

Trong VS Code, mở thư mục E:/PBL4_DeTai21. Nếu cần cập nhật/build, dừng JAR cũ bằng Ctrl+C trước. Kiểm tra git status --short và git branch --show-current. Nhánh dùng là web-rebuild. Nếu có thay đổi chưa lưu, không reset hoặc checkout ép; giữ thay đổi và xử lý trước khi pull. Checkout sạch có thể chạy git pull --ff-only origin web-rebuild.

Build: npm --prefix web ci, sau đó ./scripts/build-web.ps1 tại gốc repo. Cần JDK21, Maven3.9+, Node tương thích Vite7 (ví dụ 22.12+). Nếu policy chặn script, chạy npm --prefix web run build rồi mvn -f backend/pom.xml verify. Bản đã build đúng phiên bản có thể bỏ qua.

## 2. Terminal 1: chạy server

    cd E:PBL4_DeTai21
    java -jar backend/target/classroom-web-server-0.1.0.jar --app.database=./data/remote-test.db

Mở http://127.0.0.1:8081/api/health, phải thấy ok: true. Luôn dùng cùng thư mục gốc và cùng database khi restart. Nếu port bận, dừng đúng server cũ, không tắt tất cả Java.

## 3. Terminal 2: mở tunnel

    cloudflared tunnel --url http://127.0.0.1:8081

Nếu không tìm thấy lệnh nhưng đã cài tại vị trí mặc định trên máy ADMIN:

    & 'C:Program Files (x86)cloudflaredcloudflared.exe' tunnel --url http://127.0.0.1:8081

Copy URL https://...trycloudflare.com thật được in ra. Giữ terminal 2 chạy. URL có thể đổi mỗi lần mở tunnel. Không dùng URL của buổi cũ hoặc URL trang tải cloudflared.

## 4. Terminal 1: cho phép origin media

Ctrl+C dừng Java, giữ tunnel. Điền URL thật, không có dấu / cuối:

    $publicUrl = 'https://DIEN-URL-THAT.trycloudflare.com'
    java -jar backend/target/classroom-web-server-0.1.0.jar --app.database=./data/remote-test.db "--app.allowed-origins=$publicUrl"

Không đặt origin là *. Không cần profile LAN hoặc mở port router cho luồng tunnel này.

## 5. Cả nhóm đăng ký trên cùng HTTPS

A gửi URL cho B/C. Tất cả mở cùng URL, kể cả A. Kiểm tra /api/health trên URL chung. B/C không mở localhost của mình. Mỗi người dùng tài khoản riêng, một tab lớp học và tai nghe. Cho phép mic của site khi được hỏi.

Username 3–32 ký tự, bắt đầu bằng chữ, sau đó chữ/số/gạch dưới. Mật khẩu tối thiểu 8 ký tự có chữ hoa, chữ thường, số và ký tự đặc biệt. Dùng tài khoản và tài liệu thử riêng.

## 6. Tạo lớp và thêm người học

1. A đăng nhập, Tạo lớp “PBL4 – Test nhóm”, Mở lớp để xem trang các buổi học.
2. A mở Quản lý lớp, copy mã lớp gửi B.
3. B chọn Nhập mã lớp và gửi yêu cầu. Chưa được vào khi chưa được duyệt.
4. A duyệt B. Dashboard B tự cập nhật khoảng 5 giây khi đang mở; có thể bấm Làm mới. B bấm Mở lớp.
5. C đăng ký xong báo username; A dùng Thêm học viên với username C. C được vào ngay.
6. A bấm Bắt đầu buổi học; B/C bấm Vào buổi học trên trang lớp. Cả nhóm phải thấy kết nối media thành công trước khi thử mic.

## 7. Test voice

1. Cả nhóm Bật loa. A Bật mic đếm 1–10, B/C xác nhận nghe.
2. A tắt mic, giữ loa; B nói, A/C xác nhận. Đổi lượt cho C.
3. A/B nói luân phiên rồi đồng thời ngắn; C xác nhận nghe cả hai.
4. Giữ buổi 15 phút, mỗi phút đổi người nói. Ghi nhận mất tiếng một chiều, trễ, đứt tiếng. Kết nối lúc đầu chưa đủ để đánh dấu voice đạt.

## 8. Test share và điều hành

1. A mở PowerPoint/PDF hoặc tab tài liệu riêng. Chia sẻ bài giảng và chọn nguồn đó. Tránh desktop hoặc cửa sổ chứa lớp học để tránh phản chiếu. B/C thấy nội dung thay đổi, vẫn nói/nghe hai chiều.
2. A Dừng chia sẻ; thử lại và dừng từ thanh browser. Hủy picker không được làm ngắt mic/loa.
3. B Xin chia sẻ màn hình. A Xem yêu cầu / Trong phòng & điều hành → Duyệt chia sẻ. B tự bấm Chia sẻ bài giảng, chọn tài liệu.
4. A Dừng người đang chia sẻ. B không được share lại khi chưa được cấp quyền mới. A Mời trình bày C, C tự chọn nguồn. Chỉ một người trình bày tại một thời điểm.
5. A Khóa mic học viên cả lớp. B/C dừng mic, A vẫn nói được. B Xin phát biểu, A Duyệt phát biểu; B tự bật mic, C vẫn bị khóa.
6. A Thu hồi quyền mic B rồi Mở quyền mic cả lớp. Thử Khóa mic riêng B, C vẫn nói; Mở mic riêng để B tự bật lại. Mở quyền không tự bật phần cứng mic.
7. A rời tạm: quyền mic hiện tại được giữ; yêu cầu mới cần chờ A quay lại. Quyền tạm thời có thể reset khi người được cấp rời/vào lại.

## 9. Test vòng đời

1. B Rời buổi học rồi vào lại: vẫn là thành viên.
2. B tắt mạng 10 giây rồi bật: UI nhận biết ngắt, dừng media cũ; kết nối/vào lại, tự bật mic/loa, kiểm tra hai chiều.
3. A Kết thúc buổi học và xác nhận: tất cả media dừng, trở về trang lớp với thẻ Đã kết thúc, thời gian/thời lượng và Xem tin nhắn; B chưa kết nối media được khi buổi đóng.
4. A Bắt đầu buổi mới; B/C vẫn ở trang lớp, chủ động bấm Vào buổi học rồi tự bật media. Chat buổi mới trống, chat buổi cũ vẫn xem được ở đúng thẻ.
5. A xóa B khỏi lớp: B bị ngắt và không thể kết nối lại bằng quyền cũ. Thêm/duyệt lại để vào.
6. B Rút khỏi lớp: mất membership, cần được thêm/duyệt lại. Lưu trữ lớp chỉ thử ở lớp dùng một lần; không dùng để kết thúc buổi, chưa có UI khôi phục lưu trữ.
7. B tạo lớp khác, A tham gia: B là chủ lớp mới, A là thành viên, chứng minh quyền theo lớp.

## 10. Xử lý lỗi và ghi kết quả

| Hiện tượng | Kiểm tra |
|---|---|
| Không mở link / 502 | Java chạy? localhost health OK? Tunnel còn chạy? Đúng URL mới? |
| UI mở nhưng media ngắt | Origin HTTPS trong lệnh Java đúng? Membership đã duyệt? Buổi mở? F12 → Network → WS: 101 là handshake thành công; 403 cần kiểm tra origin/quyền. |
| Không bật mic | HTTPS, quyền mic site/Windows, đúng thiết bị, có bị chủ lớp khóa? |
| Voice một chiều | Cả hai mở Thông tin kết nối âm thanh, ghi số liệu hai lần cách 5 giây; xem mic gửi, dữ liệu nhận, trạng thái loa, volume và output tai nghe. Đừng refresh trước khi ghi triệu chứng. |
| Share lặp | Dừng và chọn cửa sổ/tab tài liệu riêng. |
| Mất dữ liệu khi restart | Đúng thư mục gốc và ./data/remote-test.db? |

Mỗi ca ghi: giờ, người thao tác, số người, browser, mạng Wi-Fi/4G, bước, kỳ vọng, thực tế, ảnh UI/thông tin âm thanh, lỗi Java/cloudflared. Không gửi password, cookie, bearer token hoặc session ticket.

Xong: Ctrl+C tunnel rồi Java. Không để máy A ngủ trong buổi test. Test tự động/fake media không chứng minh lỗi voice một chiều đã hết; cần kiểm tra trên thiết bị và mạng thật của nhóm.
