# Rà soát bản web theo Đề tài 21 — 03/11/2026

## Kết luận và phạm vi

Bản web đã triển khai tất cả nhóm chức năng được liệt kê trong đề. Chưa nghiệm thu toàn bộ chương trình: lỗi âm thanh một chiều do người dùng báo chưa có xác nhận hết lỗi trên hai máy thật; chưa đo độ trễ, tải nhiều người và khả năng phục hồi mạng. Test tự động là bằng chứng của các kịch bản cụ thể, không chứng minh mọi tình huống đều đúng.

Nguồn yêu cầu là mô tả Đề tài 21 người dùng cung cấp và phần đối chiếu PDF trong WEB_REBUILD.md. Tài liệu kế hoạch cũ và README Swing là lịch sử, không phải bằng chứng hoàn thiện bản web. Thay đổi lần này giữ Java backend, React client và schema SQLite; không import DB Swing.

## Ma trận yêu cầu và tiêu chí nghiệm thu

| Yêu cầu | Triển khai và bằng chứng source/test | Kết luận/tiêu chí còn phải xác nhận |
|---|---|---|
| Tạo tài khoản, đăng nhập | web/src/main.tsx; backend/src/main/java/com/pbl4/web/AuthService.java, ApiController.java, Database.java; FoundationTest, ApiTest | Có. Server kiểm tra username/họ tên/mật khẩu và từ chối tự cấp role. BCrypt cost 12; token ngẫu nhiên, DB lưu hash, hết hạn 8 giờ. Cần bổ sung hạn chế brute force và vận hành quản lý tài khoản. |
| Tạo lớp, thêm người học | web/src/Classes.tsx; ClassroomService.java, ClassroomController.java; ClassroomTest và ApiTest | Có: chủ lớp tạo, thêm username tài khoản đã tồn tại hoặc duyệt mã mời; người ngoài không được vào trước duyệt. Có xóa/rút/lưu trữ. Test transaction và thêm đồng thời không trùng. Thêm trạng thái yêu cầu cho học viên trong lần này. |
| Chia sẻ màn hình người dạy | web/src/ClassroomMedia.tsx; MediaWebSocketHandler.java, MediaControls.java; MediaRelayTest và browser hai context | Có relay server, quyền trình bày duy nhất; tab/window, stop/end/revoke, ẩn local preview. Người dùng báo share đã chạy giữa hai mạng. Chưa xác minh mọi browser/nguồn Windows hoặc tình huống picker đang mở khi đổi quyền. |
| Gửi/nhận âm thanh realtime | ClassroomMedia.tsx, web/public/audio-capture.js; MediaWebSocketHandler.java; audio-capture.test.cjs và MediaRelayTest | Có PCM 16 kHz, 20 ms/frame, nhiều nguồn, mic/loa độc lập; nguồn theo connection, ping/pong, kiểm tra quyền từng packet. Browser giả lập hai chiều qua. Chưa chốt lỗi một chiều thật đã hết; cần test dài và đo latency. |
| Server quản lý tài khoản/lớp | Database.java, AuthService.java, ClassroomService.java; migration backend/src/main/resources/db/V001__foundation.sql | Có persistence, session revoke và kiểm tra quyền API/media. Quản lý tài khoản hiện là đăng ký/đăng nhập/phiên và cấp giáo viên qua CLI; thiếu giao diện quản lý/khôi phục mật khẩu. |
| Server nhận và chuyển tiếp audio/hình ảnh | MediaTicketService.java, MediaWebSocketHandler.java; MediaRelayTest | Có: JPEG/PCM đi qua server, cô lập lớp, không gửi lại nguồn; người mất tư cách thành viên/logout/archive bị ngắt. Không dùng P2P thay relay. |
| Đa luồng và đồng bộ gửi/nhận | MediaWebSocketHandler.java: receive/control synchronized, mỗi peer có executor virtual thread và queue giới hạn; Database.java: synchronized transaction ngắn | Có cơ chế thật và test hai nguồn đồng thời. Receive dùng khóa chung toàn handler và SQLite dùng một connection nên chưa chứng minh tải lớn. Cần số đo trước khi kết luận đáp ứng quy mô lớp dự kiến. |

Đường dẫn test Java ở backend/src/test/java/com/pbl4/web/. “Đồng bộ” hiện bảo vệ trạng thái quyền/thành viên và thứ tự gửi. Chưa có timestamp chung cho audio/screen hay kiểm chứng đồng bộ thời gian dưới mạng nghẽn; không nên ghi báo cáo rằng đã giải quyết đầy đủ đồng bộ media.

## Chính sách nghiệp vụ hiện tại

Đề không bắt buộc hai role toàn hệ thống. TEACHER/STUDENT là chính sách lớp do người dùng trước đó ủy quyền lựa chọn: operator cấp quyền người dạy, public register chỉ tạo học viên. Phân quyền đáng tin cậy không đồng nghĩa đề bắt buộc mô hình này.

Hiện có hạn chế: một tài khoản TEACHER không xin vào hoặc được thêm làm học viên ở lớp khác; STUDENT không tạo lớp. Nếu muốn trải nghiệm phòng họp, nên chuyển quyền điều hành về quan hệ từng lớp (owner/member), tài khoản thông thường có thể học hoặc dạy tùy lớp. Đây là thay đổi nghiệp vụ/schema/API/test riêng, chưa thực hiện âm thầm trong đợt UI.

Phân biệt ba thao tác: “Danh sách lớp” chỉ rời kết nối buổi học; “Rút khỏi lớp” xóa tư cách thành viên; “Lưu trữ lớp” dừng truy cập cả lớp lâu dài. Chưa có chức năng kết thúc một buổi nhưng giữ lớp/thành viên cho buổi sau. Khi chủ lớp rời, thành viên vẫn có thể trao đổi; quyền điều hành trong RAM hết khi phòng trống/server restart, ngoại lệ cá nhân hết khi connection rời.

Học viên có thể xin chia sẻ; chủ lớp duyệt/từ chối/thu hồi. Mở quyền mic không tự bật thiết bị. Nút khóa mic cả lớp và cấp ngoại lệ chỉ có chủ lớp; hiện muốn chặn riêng một người phải khóa cả lớp rồi cấp ngoại lệ cho những người khác. Chưa có nút mute riêng khi lớp đang mở mic.

## Thiếu sót phân theo ưu tiên

### P0 — trước nghiệm thu media

- Tiếng một chiều: tái kiểm tra hai Windows, tai nghe thật, 10–15 phút, đổi lượt nói, tắt/bật mic, chuyển tab, rời/vào lại, mất mạng rồi reconnect. Ghi Mic/Loa/gói gửi-nhận mỗi 5 giây khi lỗi. Test giả lập không nghe được âm thanh thật và không tái hiện chính xác thiết bị người dùng.
- Đo nhiều người nói cùng chia sẻ: tối thiểu 3–5 client, một mạng chậm. Ghi thời gian end-to-end, gián đoạn tiếng, CPU/heap và queue disconnect. Audio và JPEG dùng cùng WebSocket/TCP, ảnh hoặc mạng chậm có thể làm tiếng chờ; tách kênh audio/screen hoặc gateway chuyên dụng chỉ quyết định sau số đo.
- Xác nhận share nguồn Windows thật: cửa sổ/tab bài giảng, hủy picker, dừng từ browser, học viên được cấp/thu hồi quyền, chủ lớp lấy lại; không chọn chính tab lớp gây phản chiếu.

### P1 — hoàn thiện luồng sử dụng và vận hành

- Hạn chế login/register/control spam, giới hạn kích thước HTTP body và báo lỗi server có cấu trúc. Hiện có giới hạn packet/connection/queue, throttling chat/audio/screen; chưa có rate limit đăng nhập hay mọi lệnh điều hành.
- Luồng tài khoản đổi/khôi phục mật khẩu có xác minh, khóa tài khoản và quản lý phiên. Hiện reload mất token ở client và cần đăng nhập lại; logout một phiên không logout các phiên khác.
- Kết thúc buổi học mà giữ lớp, xác định hành vi khi chủ lớp mất kết nối; nút rời buổi rõ ràng và trạng thái quyền thiết bị.
- Thông báo/cập nhật danh sách thành viên và yêu cầu đang chờ. Lần này thêm trạng thái yêu cầu học viên, cập nhật bằng Làm mới; chưa push realtime kết quả duyệt trên dashboard.
- Nếu chốt mô hình phòng họp: migration quyền theo lớp, giữ owner/member cũ, test người dạy lớp A học lớp B, sửa CLI/UI/phân quyền đồng bộ.

### P2 — tiện ích không nằm trong yêu cầu đề

Lịch học, webcam, ghi hình, bài tập, tải tài liệu, lịch sử chat, đồng chủ lớp, điểm danh xuất file. Không thêm nút giả cho những chức năng này. Chỉ làm khi có nhu cầu và thời gian sau P0/P1.

## Thay đổi của lần rà soát này

- UI tham khảo bố cục ảnh: nền sáng, viền mảnh, accent tím nhẹ; ô người kết nối thật, bài giảng lớn, điều hành/quản lý cạnh chat, điều khiển dưới bài giảng, mobile xếp dọc. Không giả webcam hay trạng thái đang nói.
- Quản lý lớp nằm trong mục mở rộng, không chiếm toàn bộ khu vực buổi học; thông tin chẩn đoán âm thanh nằm trong mục chi tiết.
- Sửa resume loa hoàn tất muộn sau khi rời phòng, dọn stream khi khởi động share/mic lỗi và tránh cập nhật trạng thái lỗi của thao tác mic cũ.
- Thêm GET /api/rooms/join-requests chỉ đọc yêu cầu của phiên hiện tại; test chờ/duyệt/từ chối/cô lập tài khoản và ẩn lớp đã lưu trữ. Đây là lịch sử kết quả yêu cầu, không thay danh sách membership hiện tại.
- Không đổi schema hoặc DB đang dùng. Các sửa Swing tồn tại từ trước giữ nguyên.

## Quy trình nghiệm thu hai máy

Bằng chứng lần này: frontend TypeScript/Vite build thành công; 3 test resampler; Maven verify 7 test không lỗi (đã mở rộng kịch bản trạng thái yêu cầu). Playwright trên candidate 8091 kiểm tra học viên gửi mã/chờ/được duyệt, chủ lớp duyệt, xin/duyệt/thu hồi share, ảnh học viên tới chủ lớp, khóa/xin/cấp/thu hồi mic, audio giả lập hai chiều và tắt/bật lại mic, chat, mobile 390 px không tràn ngang, không pageerror. Screenshot desktop/mobile trong web/test (gitignored). Thử nguồn media giả lập và thời gian ngắn không thay nghiệm thu Windows thật.

1. Cả hai Ctrl+Shift+R tại cùng URL HTTPS, đăng nhập, dùng một tab và tai nghe.
2. A tạo lớp; B gửi mã; B xem trạng thái chờ; A duyệt trong Danh sách & quản lý lớp; B Làm mới rồi vào. Thử người ngoài không được truy cập.
3. Cả hai bật loa/mic, luân phiên nói 10–15 phút; B tắt/bật mic, rời buổi rồi vào lại. Khi mất tiếng mở Thông tin kết nối âm thanh, ghi hai bộ số cách nhau 5 giây ở cả A/B.
4. A share tab/window tài liệu, B xem; B xin share, A duyệt và xem ảnh B; A thu hồi; thử hủy picker và dừng browser.
5. A khóa mic; B xin phát biểu; A duyệt; B tự bật; A thu hồi; mở lại cả lớp không tự bật mic B.
6. A xóa B, B mất media/quyền lớp; duyệt lại để vào. Logout và lưu trữ lớp phải chặn truy cập media hiện có.
7. Lặp lại với người thứ ba và mạng chậm; ghi kết quả thật trong báo cáo, không dùng số liệu giả lập làm số đo hai mạng.
