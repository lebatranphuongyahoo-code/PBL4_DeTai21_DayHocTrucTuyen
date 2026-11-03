# Bổ sung vòng đời và điều hành — 03/11/2026

Đã thêm: Kết thúc buổi/Bắt đầu buổi mới giữ lớp và thành viên; state is_open SQLite; khóa/mở mic riêng; mời trình bày không cần xin trước; xử lý 401 trở về đăng nhập. Người dùng tự bật mic/share; chỉ chủ lớp gọi API kết thúc/mở, mic-block và screen-grant. Media kết thúc mã 4000; server chặn join và packet trong buổi đóng. End API đóng mọi connection và reset controls ngay.

Migration v2 bổ sung classroom_meetings(classroom_id,is_open), backfill lớp cũ mở như hành vi cũ. Không xóa hay đổi tài khoản/member. Migration transaction/idempotent; test reopen giữ state đóng. Bản cũ vẫn đọc schema thêm được nhưng không hiểu is_open, nên rollback JAR có thể mở buổi đóng. DB/JAR backup backend/target/remote-test-before-meeting-v2.db và classroom-web-server-before-meeting-v2.jar.

Bằng chứng: 11 test backend qua, 3 resampler qua, frontend build. Browser hai context: mic riêng chặn khi mic lớp mở, mở riêng không auto bật; mời trình bày/thu hồi; end đóng cả hai, restart chủ lớp và kiểm tra/rejoin người học; member/class giữ. Regression audio giả lập hai chiều, screen approve/revoke, mic controls/chat qua. Chưa thêm test socket end vào CI; test browser tạm TEMP.

Phạm vi còn chưa hoàn thiện: nghiệm thu audio một chiều Windows thật/15 phút và tải 3–5+ client; đo latency/queue/CPU và đồng bộ audio-screen; login rate limit; đổi/khôi phục mật khẩu có xác minh; account/session management UI; phục hồi archive; CI browser tests lâu dài. Không dựng giả những chức năng này. Không tuyên bố toàn bộ chương trình hoàn thiện.

Cần tiếp tục các giai đoạn account/security và nghiệm thu mạng thật. Vòng đời hiện là state mở/đóng mỗi lớp, chưa có lịch sử session/điểm danh. Khi server restart, is_open giữ nhưng quyền riêng mic/presenter RAM hết; cấp lại trong buổi.
