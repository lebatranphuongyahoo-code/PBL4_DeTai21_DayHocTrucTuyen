# TODO trùng tu theo nhận xét giảng viên

Nhánh làm việc: `codex/trung-tuong-2026-11-22`. Các task được sắp theo phụ thuộc. Chỉ đánh dấu `[x]` khi điều kiện hoàn thành đã có bằng chứng trong test, log hoặc ảnh demo.

## Chuẩn bị và quyết định

- [ ] **S0.1** Sao lưu database QA và ghi baseline hiện tại.
  - Files: `README.md`, `docs/RA_SOAT_DE_TAI_21.md`.
  - Done when: có bản sao DB/JAR; `mvn verify`, `npm --prefix web test`, `npm --prefix web run build` được ghi kết quả.
- [x] **S0.2** Chốt ma trận quyền Admin/Teacher/Student.
  - Depends: S0.1.
  - Files: `docs/QUYEN_THEO_LOP.md`.
  - Done when: mỗi vai trò có danh sách API được phép và bị cấm; xác nhận giáo viên được tự tạo lớp.
- [x] **S0.3** Chốt phạm vi tương tác.
  - Depends: S0.2.
  - Done when: giữ chat/giơ tay/mic/share; bài tập chữ → nộp câu trả lời → phản hồi đã được chọn làm MVP; không làm báo cáo điểm danh.

## Admin và phân quyền backend

- [x] **A1** Bootstrap Admin an toàn.
  - Depends: S0.2.
  - Files: `AuthService.java`, `Database.java`, `SecurityConfig.java`, `OperatorCommand.java`.
  - Done when: chỉ bootstrap/local operator cấp Admin; public register luôn là Student; không có role injection.
- [x] **A2** Guard Admin và truy vấn lớp toàn hệ thống.
  - Depends: A1.
  - Files: `AdminController.java`, `ClassroomService.java`, `Database.java`.
  - Done when: Admin list/detail lớp; Teacher/Student gọi API Admin nhận 403; restore được kiểm thử.
- [x] **A3** Admin archive/restore hoặc khóa lớp theo chính sách đã chốt.
  - Depends: A2.
  - Files: `ClassroomService.java`, `ClassroomController.java`.
  - Done when: lớp khóa không join/media; thao tác không xóa thành viên ngoài chủ ý.
- [x] **A4** Admin đổi chủ lớp/giảng viên.
  - Depends: A2.
  - Files: `ClassroomService.java`, `ClassroomController.java`, migration chỉ khi cần.
  - Done when: owner mới quản lý được; owner cũ mất quyền quản lý nhưng vẫn giữ membership theo chính sách.
- [x] **A5** Chuyển học viên giữa hai lớp bằng transaction.
  - Depends: A2, A3.
  - Files: `ClassroomService.java`, `ClassroomController.java`, `Database.java`.
  - Done when: transaction chống trùng, chặn lớp lưu trữ, membership đổi nguyên tử; chưa có audit riêng.
- [x] **A6** Kiểm thử quyền server-side.
  - Depends: A1-A5.
  - Files: `backend/src/test/java/com/pbl4/web/`.
  - Done when: test Admin/Teacher/Student, cross-class, archive, transfer, restart và media đều xanh.

## Admin UI và tương tác

- [x] **U1** Admin dashboard tối thiểu.
  - Depends: A2, A3.
  - Files: `web/src/AdminDashboard.tsx`, `web/src/api.ts`, `web/src/main.tsx`.
  - Done when: Admin xem lớp, owner, thành viên và thao tác đúng quyền; có lọc trạng thái và restore.
- [x] **U2** Hiển thị đúng nhãn Teacher/Student/Owner theo ngữ cảnh.
  - Depends: A2.
  - Files: `web/src/api.ts`, `web/src/ClassManagement.tsx`.
  - Done when: không dùng nhãn “Admin” để biểu thị quyền điều hành media trong phòng.
- [x] **U3** Regression các tương tác hiện có.
  - Depends: A6.
  - Files: `web/src/ClassroomMedia.tsx`, `web/test/`, `backend/src/test/java/com/pbl4/web/MediaRelayTest.java`.
  - Done when: chat, xin phát biểu, duyệt/từ chối mic, share/revoke và remove member chạy lại không lỗi.
- [x] **U4** MVP hoạt động học tập và đánh giá.
  - Depends: A6, U1.
  - Files: backend service/controller, migration, `web/src/` và test tương ứng.
  - Done when: Teacher đăng bài tập chữ; Student nộp câu trả lời; Teacher phản hồi/chấm; chỉ thành viên lớp truy cập được. Có `ActivityApiTest`, `ClassroomActivityTest` và `Activities.tsx`.

## Bằng chứng kỹ thuật và demo

- [x] **B1** Sửa tài liệu kiến trúc: WebSocket relay, không gọi WebRTC.
  - Depends: S0.2.
  - Files: `docs/WEB_MEDIA_DEMO.md`, `docs/KIEN_TRUC.md`.
  - Done when: có sơ đồ auth → membership → meeting → relay và link tới file code/test.
- [x] **B2** Viết pseudocode cho permission state machine, producer-consumer queue và lock.
  - Depends: B1.
  - Done when: nhóm giải thích được input, output, lỗi và vì sao cơ chế phục vụ lớp học.
- [x] **B3** Cập nhật kịch bản demo 5 phút.
  - Depends: A6, B1.
  - Files: `docs/KICH_BAN_DEMO.md`, `docs/TEST_HAI_MANG.md`.
  - Done when: demo có Admin → Teacher → hai Student, transfer, giơ tay, relay media, bài tập/nộp/phản hồi và tình huống từ chối quyền.
- [ ] **B4** Chạy hai máy thật và ghi bằng chứng media.
  - Depends: A6, B3.
  - Files: `docs/do-dac/` hoặc thư mục bằng chứng đã thống nhất.
  - Done when: chat/mic/screen/raise-hand đạt; lỗi âm thanh một chiều có kết luận; có ảnh/log.
- [ ] **B5** Đo client chậm và nhiều người.
  - Depends: B4.
  - Done when: ghi latency, frame drop, CPU/RAM, queue disconnect; không đưa số giả lập vào báo cáo như số đo thật.

## Kiểm tra cuối và bàn giao

- [x] **F1** Chạy toàn bộ backend/frontend test và build.
  - Depends: A6, U3, B5.
  - Done when: không có test/build lỗi; đã chạy `mvn -q verify`, `npm test` (23/23) và `npm run build`.
- [ ] **F2** Cập nhật báo cáo, slide và phân công lời nói.
  - Depends: B2, B5, F1.
  - Done when: mỗi thành viên giải thích được phần mình; không nói Minimax, WebRTC hoặc điểm danh khi chưa có triển khai tương ứng.
- [ ] **F3** Tổng duyệt với dữ liệu demo sạch.
  - Depends: F1, F2.
  - Done when: chạy đúng thứ tự, có phương án dự phòng và không dùng database thật để thử nghiệm.

## Không tạo task

- Không thêm Minimax/Max-min: transcript không nêu tên thuật toán; yêu cầu là giải thích relay, kiểm tra quyền, hàng đợi và đồng bộ.
- Không làm báo cáo điểm danh; chỉ giữ lịch sử kết nối kỹ thuật để debug vòng đời buổi học.
- Viết lại toàn bộ media sang WebRTC/SFU.
- Webcam, ghi hình, lịch học và các tính năng không xuất hiện trong tiêu chí nghiệm thu.

## Cách chia việc bằng Orchestra

Model điều phối mạnh giữ S0.2 (ma trận quyền), quyết định hợp đồng API/schema trước khi chia việc, review các thay đổi backend và chịu trách nhiệm F1-F3. Giao mỗi task có phạm vi file và tiêu chí Done rõ cho một model nhỏ; model nhận việc phải báo file đã sửa, test đã chạy và phần chưa kiểm chứng.

Có thể chạy song song sau khi hợp đồng quyền/API được chốt:

- Một model làm backend Admin/ACL và test A3-A6 theo hợp đồng API đã chốt.
- Một model làm frontend Admin, bỏ phần điểm danh riêng và test U1-U3.
- Một model làm MVP bài tập/nộp/phản hồi sau khi schema/API được thống nhất.
- Coordinator cập nhật tài liệu B1-B3 và kiểm thử tích hợp.
- Nghiệm thu hai máy B4-B5 do người có thiết bị thực chạy; model có thể chuẩn bị biểu mẫu/log nhưng không được bịa số đo.

Giữ tuần tự A1 → A2 → A3/A4/A5 vì các bước này cùng chạm service, controller và quyền; tránh giao nhiều model sửa đồng thời các file đó. Chỉ coordinator tích hợp, giải quyết xung đột, chạy full suite và đánh dấu TODO hoàn tất.
