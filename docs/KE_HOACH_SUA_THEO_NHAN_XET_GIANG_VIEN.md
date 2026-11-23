# Kế hoạch hoàn thiện theo nhận xét giảng viên

Ngày lập: 22/11/2026

Tài liệu này chuyển hai đoạn nhận xét trong file ghi chú của nhóm thành kế hoạch sửa có thể kiểm chứng. Phạm vi ưu tiên là Admin quản lý toàn hệ thống, phân quyền đúng ở server, tăng tương tác trong lớp và chứng minh kiến trúc mạng bằng demo thật.

## 1. Hiện trạng đã xác nhận

| Hạng mục | Trạng thái | Bằng chứng hiện có |
|---|---|---|
| Tài khoản, phiên đăng nhập, SQLite | Đã có | `AuthService`, `Database`, `ApiController` |
| Tạo lớp, mã mời, thêm/xóa thành viên, duyệt yêu cầu | Đã có | `ClassroomService`, `ClassroomController`, `ClassManagement.tsx` |
| Chat, xin phát biểu, duyệt mic, yêu cầu/chia sẻ màn hình | Đã có | `ClassroomMedia.tsx`, `MediaControls`, `MediaWebSocketHandler` |
| Relay media và hàng đợi gửi riêng từng người nhận | Đã có | `MediaWebSocketHandler`, `MediaRelayTest` |
| Quyền theo `owner/member` trong từng lớp | Đã có | `QUYEN_THEO_LOP.md`, `ClassroomTest`, `PerRoomPermissionsTest` |
| Admin quản lý lớp, xem thành viên, lưu trữ, đổi chủ lớp | Đã có trong nhánh trùng tu | `AdminController`, `ClassroomService`, `AdminDashboard`; cần hoàn thiện kiểm tra role và restore |
| Admin chuyển học viên giữa lớp | Đã có trong nhánh trùng tu | `ClassroomService.transferMember`; transaction và kiểm tra quyền đã có |
| Chat, giơ tay/xin mic, chia sẻ màn hình | Đã có | `ClassroomMedia.tsx`, `MediaControls`, `MediaWebSocketHandler` |
| Bài tập, bài nộp, chấm/nhận xét | Chưa có | Đây là hướng tăng tương tác thầy nêu ví dụ; chỉ thêm nếu nhóm/giảng viên chốt làm MVP |
| Báo cáo điểm danh | Không làm | Thầy nêu như ví dụ, không phải điều kiện bắt buộc; bỏ màn hình/API riêng, giữ lịch sử kết nối phục vụ debug buổi học |

## Nguồn tham khảo kỹ thuật

Số liệu lấy từ GitHub API ngày 22/11/2026. Sao/fork là tín hiệu phổ biến và hoạt động cộng đồng, không phải chứng nhận chất lượng hay bảo đảm phù hợp; chỉ vận dụng quy trình, không thêm dependency vào sản phẩm.

| Repository | Sao / fork | Cách áp dụng vào dự án |
|---|---:|---|
| [obra/superpowers](https://github.com/obra/superpowers) | 323,794 / 28,721 | Làm rõ yêu cầu trước, chia việc nhỏ có điều kiện nghiệm thu, kiểm thử trước/sau, rà soát phần dư thừa. MIT; cập nhật gần nhất 03/11/2026. |
| [github/spec-kit](https://github.com/github/spec-kit) | 150,949 / 13,555 | Viết đặc tả quyền Admin–Teacher–Student, tiêu chí chấp nhận và task phụ thuộc nhau trước khi triển khai. MIT; cập nhật gần nhất 20/11/2026. |
| [github/awesome-copilot](https://github.com/github/awesome-copilot) | 41,430 / 5,401 | Tham khảo tuyển tập skill/instructions và quy trình làm việc; không cần cài vào dự án. MIT; cập nhật gần nhất 20/11/2026. |

## 2. Nghiệp vụ đích

Giữ mô hình nhỏ nhất phù hợp với nhận xét:

- `ADMIN`: xem và quản lý toàn bộ lớp; khóa hoặc lưu trữ lớp; đổi chủ lớp; chuyển học viên.
- `TEACHER`: tạo và quản lý lớp do mình phụ trách; duyệt thành viên; điều hành buổi học và quyền media.
- `STUDENT`: tham gia lớp được cấp/duyệt và dùng các chức năng tương tác.
- Giáo viên vẫn có thể mở lớp; Admin là cấp cao hơn để giám sát và xử lý toàn hệ thống. Admin không tự động được quyền điều khiển mic/chia sẻ trong một buổi nếu chưa là thành viên phòng.
- `classrooms.owner_id` tiếp tục biểu diễn giáo viên/chủ lớp ở giai đoạn đầu. Chỉ thêm `created_by` hoặc bảng audit nếu cần ghi nhận người tạo/chuyển quyền.

Nếu giảng viên yêu cầu Admin phải là người tạo mọi lớp, chỉ thay luồng tạo lớp và thêm `created_by`; không viết lại media hoặc quan hệ thành viên.

## 3. Nguyên tắc kỹ thuật giữ lại

- Giữ React/TypeScript, Spring Boot, SQLite và WebSocket relay hiện tại.
- Gọi đúng kiến trúc là WebSocket relay qua TCP; không mô tả thành WebRTC nếu chưa chuyển công nghệ.
- Giữ hàng đợi giới hạn, luồng gửi riêng, kiểm tra quyền từng packet và cơ chế lock hiện có.
- Quyền Admin phải được kiểm tra ở backend; UI chỉ hiển thị những thao tác đã được server cho phép.
- Không thêm Minimax/Max-min. Thuật toán mạng nên trình bày là relay/broadcast theo phòng, kiểm tra membership/quyền trước khi chuyển tiếp, cùng bounded producer-consumer queue để giới hạn bộ nhớ và ngắt peer chậm.
- Nêu đúng giới hạn hiện tại: WebSocket chạy trên TCP; khung audio và ảnh dùng chung kết nối, nên TCP head-of-line blocking có thể tăng trễ. Chưa tuyên bố WebRTC/SFU hay khả năng tải lớn nếu chưa đo.

## 4. Bảng kế hoạch thực hiện

| Mã | Ưu tiên | Việc cần làm | Phụ thuộc | Kết quả nghiệm thu |
|---|---|---|---|---|
| P0-1 | P0 | Chốt ma trận quyền Admin/Teacher/Student và luồng giáo viên tự mở lớp | Không | Có bảng quyền cho từng API và thao tác UI |
| P0-2 | P0 | Bootstrap/cấp Admin server-side; không cho đăng ký tự chọn role | P0-1 | Tài khoản Student không thể tự nâng quyền; Admin đăng nhập được |
| P0-3 | P0 | Rà guard Admin và truy vấn toàn bộ lớp | P0-2 | Student/Teacher nhận 403; Admin xem được lớp mình không phụ trách; Admin không tự bypass media membership |
| P0-4 | P0 | Admin lưu trữ/khôi phục lớp và đổi đúng giảng viên phụ trách | P0-3 | Lớp archived chặn join/media; chỉ tài khoản TEACHER được giao owner; restore giữ nguyên thành viên |
| P0-5 | P0 | Nghiệm thu chuyển học viên giữa hai lớp bằng transaction | P0-3 | Không trùng thành viên, không chuyển vào lớp archived, lỗi thì rollback |
| P0-6 | P0 | Cập nhật Admin dashboard và nhãn quyền trên frontend | P0-3, P0-4 | Có luồng Admin → lớp → thành viên; không lộ nút trái quyền |
| P0-7 | P0 | Viết test quyền chéo, transfer, archive và media sau khi đổi role | P0-2 đến P0-5 | Backend test xanh; Admin không tự bypass media membership |
| P0-8 | P0 | Sửa tài liệu kiến trúc, demo và câu trả lời bảo vệ | P0-1 | Mọi tuyên bố khớp code: WebSocket relay, đa luồng, queue, lock |
| P1-1 | P1 | Nghiệm thu hai máy thật: chat, mic, screen, xin phát biểu, revoke | P0-7 | Có log, ảnh, kết quả đạt/không đạt và lỗi đã biết |
| P1-2 | P1 | Đo 3–5 client và một client chậm | P1-1 | Có latency, frame drop, CPU/RAM và giới hạn được ghi rõ |
| P1-3 | P1 | Giữ và trình bày các tương tác đã có | P0-8 | Demo được chat, raise hand, duyệt mic, share/revoke |
| P1-4 | P1 tùy chọn | Nếu cần tăng tương tác ngoài chat/giơ tay/mic/share, thêm bài tập chữ → nộp → phản hồi | P0-6 | Chỉ triển khai sau khi nhóm/giảng viên chốt; ACL theo lớp và test lớp chéo |
| P2-2 | P2 | Chạy build, test, đóng gói và tổng duyệt kịch bản | P0, P1 | `mvn verify`, frontend test/build và demo hoàn chỉnh |
| P3-1 | P3 | Cập nhật báo cáo, slide, sơ đồ và bằng chứng | P1-2, P2-2 | Mỗi kết luận có file test, log hoặc ảnh đi kèm |

## 5. Tiêu chí hoàn thành tổng thể

1. Admin quản lý được tất cả lớp nhưng không tự động điều khiển media của lớp chưa tham gia.
2. Teacher chỉ quản lý lớp mình phụ trách; Student không gọi được API Admin.
3. Chuyển học viên là thao tác nguyên tử và không làm mất quyền ở lớp khác.
4. Demo tối thiểu thể hiện chat, giơ tay/mic và chia sẻ màn hình; bài tập → nộp → phản hồi chỉ làm nếu đã chốt.
5. Báo cáo mô tả đúng WebSocket relay, không nhận là WebRTC hoặc Minimax; có pseudocode relay và queue.
6. Không có tính năng điểm danh riêng; lịch sử kết nối chỉ phục vụ vòng đời/debug buổi học.
7. Có kiểm thử backend, frontend và ít nhất một lần chạy hai máy thật.

## 6. Ngoài phạm vi

Không làm điểm danh có mặt/vắng nếu giảng viên không yêu cầu; không viết lại sang WebRTC/SFU; không thêm webcam, ghi hình, lịch học hoặc Minimax chỉ để tăng số lượng tính năng.

Rủi ro chính là thay đổi role có thể ảnh hưởng dữ liệu `owner/member` hiện tại. Trước migration phải sao lưu database và chạy regression test quyền lớp cũ.
