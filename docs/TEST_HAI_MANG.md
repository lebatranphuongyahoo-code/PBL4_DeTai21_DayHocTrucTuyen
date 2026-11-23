# Test cả nhóm ở các mạng khác nhau

Máy A chạy một Java server và một tunnel. B/C chỉ cần Chrome/Edge, tai nghe và URL HTTPS của A; không chạy server riêng. Public registration luôn tạo Student. Người vận hành bootstrap một Admin thử nghiệm bằng CLI; sau đó Admin cấp quyền Teacher trong dashboard, tạo lớp và gán Teacher làm chủ lớp. Teacher điều hành buổi học; thành viên có thể xin quyền trình bày.

## 1. Chuẩn bị trên máy A

Trong VS Code, mở thư mục repository. Nếu cần cập nhật/build, dừng JAR cũ bằng Ctrl+C trước. Kiểm tra git status --short và git branch --show-current. Dùng nhánh `codex/trung-tuong-2026-11-22` và pull fast-forward từ đúng nhánh này. Nếu có thay đổi chưa lưu, không reset hoặc checkout ép; giữ thay đổi và xử lý trước khi pull.

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
    java -jar backend/target/classroom-web-server-0.1.0.jar --app.database=./data/remote-test.db "--app.allowed-origins=$publicUrl" --grant-admin=qaadmin

Không đặt origin là *. Không cần profile LAN hoặc mở port router cho luồng tunnel này.

## 5. Cả nhóm đăng ký trên cùng HTTPS

A đăng ký trước tài khoản `qaadmin`, `qateacher`, `qastudent1`, `qastudent2` (và `qateacher2` nếu thử chuyển chủ lớp). Dừng Java, sau đó khởi động lại như bước 4 với `--grant-admin=qaadmin`; đây là tài khoản thử, không dùng tài khoản dữ liệu thật. Admin dùng dashboard cấp quyền Teacher cho `qateacher` (và `qateacher2` nếu cần). A gửi URL HTTPS cho B/C; tất cả mở cùng URL, kể cả A. Kiểm tra `/api/health` trên URL chung. B/C không mở localhost của mình. Mỗi người dùng tài khoản riêng, một tab lớp học và tai nghe. Cho phép mic của site khi được hỏi.

Username 3–32 ký tự, bắt đầu bằng chữ, sau đó chữ/số/gạch dưới. Mật khẩu tối thiểu 8 ký tự có chữ hoa, chữ thường, số và ký tự đặc biệt. Dùng tài khoản và tài liệu thử riêng.

## 6. Tạo lớp và thêm người học

1. Admin đăng nhập, nâng `qateacher` thành Teacher, tạo lớp “PBL4 – Test nhóm” và chỉ định `qateacher` làm chủ lớp.
2. Teacher đăng nhập, mở lớp, copy mã mời gửi Student.
3. Student chọn Nhập mã lớp và gửi yêu cầu. Chưa được vào khi chưa được duyệt.
4. Teacher duyệt Student. Dashboard Student tự cập nhật khoảng 5 giây khi đang mở; có thể bấm Làm mới. Student bấm Mở lớp.
5. Teacher có thể thêm Student thứ hai trực tiếp bằng username; tài khoản đó vào lớp ngay.
6. Teacher bấm Bắt đầu buổi học; Student bấm Vào buổi học trên trang lớp. Cả nhóm phải thấy kết nối media thành công trước khi thử mic.

## 7. Test voice

1. Cả nhóm Bật loa. Teacher bật mic đếm 1–10, Student xác nhận nghe.
2. Teacher tắt mic, giữ loa; Student bật mic nói, Teacher xác nhận nghe. Đổi lượt giữa các Student.
3. Teacher/Student nói luân phiên rồi đồng thời ngắn; xác nhận nghe được cả hai chiều.
4. Giữ buổi 15 phút, mỗi phút đổi người nói. Ghi nhận mất tiếng một chiều, trễ, đứt tiếng. Kết nối lúc đầu chưa đủ để đánh dấu voice đạt.

## 8. Test share và điều hành

1. Teacher mở PowerPoint/PDF hoặc tab tài liệu riêng, bấm Chia sẻ bài giảng và chọn nguồn đó. Tránh desktop/cửa sổ chứa lớp để tránh phản chiếu. Student thấy nội dung thay đổi, âm thanh vẫn hai chiều.
2. Teacher dừng chia sẻ; thử lại và dừng từ thanh browser. Hủy picker không được làm ngắt mic/loa.
3. Student xin chia sẻ màn hình. Teacher mở Trong phòng & điều hành → duyệt chia sẻ. Student tự bấm Chia sẻ bài giảng và chọn tài liệu.
4. Teacher dừng người đang chia sẻ. Student không được share lại khi chưa được cấp quyền mới. Teacher mời Student khác trình bày; mỗi lần chỉ một người chia sẻ.
5. Teacher khóa mic học viên cả lớp. Student dừng mic; Teacher vẫn nói được. Student xin phát biểu, Teacher duyệt; người được duyệt tự bật mic.
6. Teacher thu hồi quyền mic của một Student rồi mở mic cả lớp; thử khóa mic riêng. Mở quyền không tự bật phần cứng mic.
7. Teacher rời tạm: quyền mic hiện tại được giữ; yêu cầu mới cần chờ Teacher quay lại. Quyền tạm thời có thể reset khi người được cấp rời/vào lại.

## 9. Test vòng đời

1. B Rời buổi học rồi vào lại: vẫn là thành viên.
2. B tắt mạng 10 giây rồi bật: UI nhận biết ngắt, dừng media cũ; kết nối/vào lại, tự bật mic/loa, kiểm tra hai chiều.
3. Teacher kết thúc buổi học và xác nhận: media dừng, trở về trang lớp với thẻ đã kết thúc và lịch sử; Student chưa thể kết nối media khi buổi đóng.
4. Teacher bắt đầu buổi mới; Student chủ động bấm Vào buổi học rồi bật media. Chat buổi mới trống, chat buổi cũ vẫn xem được ở đúng thẻ.
5. Teacher xóa một Student khỏi lớp: Student bị ngắt và không thể kết nối lại bằng quyền cũ. Thêm/duyệt lại để vào.
6. Student rút khỏi lớp: mất membership, cần được thêm/duyệt lại. Chỉ thử lưu trữ trên lớp QA dùng một lần; không dùng thao tác này để kết thúc buổi.
7. Nếu đã tạo `qateacher2`, Admin cấp role Teacher rồi đổi chủ lớp QA sang người này. Xác nhận quyền quản lý theo owner; Student không thể tự tạo lớp.

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
