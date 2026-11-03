# Sửa luồng sau rà soát agent-browser — 03/11/2026

## Thay đổi

- Rail Tạo lớp/Tham gia mở RoomEntryDialog thực tế; dialog có label, validation, trạng thái bận và lỗi. Tạo thành công cập nhật danh sách và thông báo Vào lớp.
- Dashboard học viên tự cập nhật lớp/kết quả duyệt mỗi 5 giây; dialog chủ lớp tự cập nhật yêu cầu. Không polling khi tab ẩn; tránh chồng request và bỏ response cũ sau unmount/thay lớp. Mutation dùng ref khóa submit đồng thời.
- Yêu cầu chia sẻ/phát biểu có banner/badge trên cả tab chat; nút Xem yêu cầu mở điều hành; học viên nhận thông báo kết quả dựa vào controls server.
- Chủ lớp vắng mặt: giữ quyền mic hiện tại, hiển thị rõ chờ chủ lớp quay lại, vô hiệu hóa yêu cầu mới; có thể hủy yêu cầu chia sẻ đã gửi. Không tự mở mic hoặc tự kết thúc buổi.
- Cookie pbl4_restore HttpOnly, SameSite=Strict, path /api/auth, tối đa 8 giờ, Secure trên HTTPS. GET /api/auth/session kiểm tra DB và trả bearer để giữ trong memory. Cookie không xác thực API khác; sửa đổi vẫn cần Authorization. Logout thu hồi DB và xóa cookie. Cache-Control no-store. Không localStorage/sessionStorage, không lưu password.
- Giữ role hiện có và schema SQLite. Reload phục hồi dashboard; không tự quay vào phòng/mở mic/share.
- Sửa contrast và heading/ARIA các màn hình được audit.

## Bằng chứng

- TypeScript/Vite build; 8 test Maven verify (gồm restore cookie không cấp quyền GET rooms/POST rooms, logout revoke); 3 test resampler đều qua.
- Agent-browser xác nhận rail mở dialog, submit tạo thật, reload phục hồi. Axe login/dashboard/entry/dialog không còn violation đã phát hiện; bản phòng học cuối có 0 violation, 0 incomplete trong kịch bản audit. Không đồng nghĩa mọi trạng thái UI đạt đầy đủ WCAG.
- Hai context browser: tạo lớp, tự cập nhật pending và approval không bấm Làm mới, badge ngay khi giáo viên ở chat, từ chối share/thông báo, duyệt mic/thông báo, chủ lớp vắng mặt khóa yêu cầu mới, reload/logout, không auto media, không pageerror.
- Hồi quy screen request/approve/revoke, ảnh học viên tới giáo viên, mic lock/request/approve/revoke, mở lớp không tự bật mic, audio giả lập hai chiều và restart mic/chat qua.
- Server 8081 trả asset index-0My7gIEv.js; DB remote-test.db không đổi schema. Backup JAR: backend/target/classroom-web-server-before-workflow-fixes.jar.

## Giới hạn

Chưa xác nhận lỗi audio một chiều trên 2 máy/mạng thật đã hết; chưa nghiệm thu 15 phút/3–5 người. Cookie có cùng tên theo host, nên các port thử cùng localhost chia sẻ phạm vi cookie; test dùng browser context riêng. Cookie Secure dựa request TLS hoặc X-Forwarded-Proto từ reverse proxy; vận hành cần proxy tin cậy. Không có CORS cho endpoint restore và cookie Strict; bearer API cũ vẫn tương thích.

Chưa thêm end-session, mute riêng khi cả lớp mở mic, reset password UI, khôi phục archive hoặc đổi chính sách role. Các tiện ích này cần giai đoạn riêng. Test browser tạm ở TEMP; chưa tích hợp thành CI lâu dài.
