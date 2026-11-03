# Bổ sung tài khoản — 03/11/2026

Có Đổi mật khẩu tại nút Tài khoản: yêu cầu mật khẩu hiện tại, mật khẩu mới mạnh theo cùng policy đăng ký, không trùng cũ; cập nhật hash bằng compare-and-swap trong transaction và thu hồi mọi session. Cookie restore xóa; UI trở về login. Không lưu password. Login có bộ đếm bounded theo username không phân biệt hoa thường: 10 lần/60 giây, thành công reset; không khóa account vĩnh viễn. Giới hạn này không thay bảo vệ IP/đăng ký phân tán; attacker có thể gây hạn chế login một phút cho username.

13 test backend qua (thêm PasswordChangeTest, LoginAttemptsTest), browser account disposable kiểm tra current sai/new login và revoke; frontend build. State meeting v2 và role theo lớp giữ nguyên. Server 8081 dùng cùng DB. Chưa tích hợp browser tests vào CI.

Chưa hoàn thành toàn bộ chương trình: kiểm chứng audio thật 2 mạng/15 phút, multi-user/load; khôi phục quên mật khẩu cần kênh xác minh danh tính; quản lý/thu hồi từng phiên UI, phục hồi archive, đăng ký rate limit, CI. Không dùng luồng reset thiếu xác minh. Vòng đời/mic riêng/media regression đã ghi BO_SUNG_VONG_DOI_BUOI_HOC.md.
