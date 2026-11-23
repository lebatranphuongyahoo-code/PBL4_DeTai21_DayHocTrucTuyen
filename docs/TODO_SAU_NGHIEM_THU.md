# TODO sau nghiệm thu PBL4

Danh sách này ghi các việc còn lại sau audit và cập nhật phân quyền/UI ngày 23/11/2026. Các mục đã được xử lý được đánh dấu hoàn tất; các mục kiểm thử mạng thật, tải và thiết bị vẫn chờ nhóm nghiệm thu.

## P1 — chốt trước khi nghiệm thu cuối

- [x] **Chốt nghiệp vụ mở lớp:** regular create chỉ cho Teacher; Admin tạo qua endpoint riêng và gán Teacher làm owner; Student bị 403.
- [x] **Xác minh Admin trên đúng branch:** bản thay đổi nằm trên `codex/trung-tuong-2026-11-22`; ghi SHA của commit được publish vào biên bản test.
- [ ] **Đo tải relay trước khi quảng bá đa lớp/đa luồng:** thử 1, 2, 3 lớp; ghi user/socket, audio/screen cùng chạy, p50/p95, dropped frame, CPU/RAM. Hiện handler WebSocket và SQLite transaction đều serialize trên lock chung.
- [ ] **Nghiệm thu hai máy thật qua HTTPS:** login/role, duyệt lớp, restart, reconnect; không dùng HTTP qua Internet.
- [ ] **Nghiệm thu mic/loa và screen thật:** 2 chiều, hai người nói, mic tắt, tai nghe/echo; share window/tab, late join, stop, track ended. Ghi giới hạn thiết bị/browser; không dùng fake audio/canvas làm bằng chứng chất lượng media.

## P2 — bảo mật, kiểm thử và UX

- [ ] **Hỏi giảng viên về lịch sử kết nối:** UI hiện tên và timestamp vào/rời cho owner; xác nhận đây là lịch sử debug được phép giữ, tránh bị hiểu là điểm danh.
- [x] **Mở rộng kiểm thử Admin API:** `AdminApiTest` gửi HTTP từ Teacher và mong đợi 403 cho các route Admin.
- [x] **Tách trạng thái Admin loading/error/empty:** API lỗi/tải không còn hiển thị danh sách rỗng giả.
- [x] **Hiện loading bài tập:** Activities báo trạng thái bận và đặt `aria-busy`.
- [ ] **Đánh giá rate limiting trước public:** đăng ký chưa có throttling; login throttle hiện theo tên và memory của một process, chưa theo IP/proxy.
- [ ] **Thu hẹp `app.allowed-origins` khi triển khai:** bỏ wildcard `*.trycloudflare.com`, chỉ giữ origin chính xác của tunnel/LAN/proxy được tin cậy.
- [ ] **Sửa hướng dẫn/pipeline:** `docs/DEMO_VSCODE.md` nói build script chạy frontend tests, nhưng `scripts/build-web.ps1` không gọi `npm test`; `docs/WEB_MEDIA_DEMO.md` còn ghi 6 backend test trong khi hiện 34.
- [ ] **Chạy kiểm tra backup/restore trên DB copy:** xác nhận file DB chứa lớp/tài khoản mong muốn; không thử migration trên DB duy nhất.

## P3 — hoàn thiện sau khi chức năng đã chốt

- [ ] Kiểm tra keyboard-only, focus vào/ra dialog, screen reader, phóng đại 200%, tab semantics trong phòng học và contrast incomplete trên icon mũi tên.
- [ ] Thêm UI test cho loading/error và mobile flow ở lớp/phòng/Admin, không chỉ login/Admin list.
- [ ] Cập nhật `/api/health` phase đang trả `foundation` nếu tên phase không còn đúng.
- [ ] Cân nhắc admin audit trail chỉ nếu yêu cầu nghiệp vụ đòi ai archive/transfer/assign owner và khi nào.

## Checklist chạy lại sau khi nhóm xử lý

- [x] `npm test` (frontend)
- [x] `npm run build` (frontend)
- [x] `mvn verify` (backend)
- [x] API-negative tests Admin/Teacher/Student
- [x] Chromium desktop + mobile visual check; Admin loading/error states have automated tests. Keyboard/screen-reader audit still open.
- [ ] WebSocket text, mic thật, screen thật, disconnect/reconnect
- [ ] Hai máy HTTPS và tải theo mục tiêu đã thống nhất
- [ ] Chụp/export logs, commit SHA, môi trường, kịch bản và kết quả; không ghi thông tin đăng nhập thật vào báo cáo
