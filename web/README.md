# Web client — React + TypeScript + Vite

Backend là Java Spring Boot trong `../backend`. Không còn server Node prototype.

Từ thư mục gốc, chạy backend:
```powershell
mvn -f backend/pom.xml verify
java -jar backend/target/classroom-web-server-0.1.0.jar
```
Mở terminal khác:
```powershell
cd web
npm ci
npm run dev
```
Mở http://127.0.0.1:5173. Vite proxy `/api` tới Java ở 127.0.0.1:8081. `npm run build` kiểm tra TypeScript và đóng gói frontend. Build hiện cần reverse proxy `/api` khi triển khai; Vite preview không thay thế backend/reverse proxy.

Mốc 1: đăng ký học viên (họ tên, username, mật khẩu), đăng nhập, đăng xuất thu hồi session, danh sách lớp chỉ gồm lớp được cấp quyền. Chưa có tạo lớp, cấp giáo viên, duyệt học viên hoặc media.

Mật khẩu được truyền đến backend qua HTTP localhost trong phát triển, backend băm BCrypt cost 12 với salt. Triển khai LAN/public cần HTTPS; không dùng HTTP plaintext để demo mật khẩu thật.

## Mốc lớp học/media

Đã có lớp/thành viên/duyệt/rút/lưu trữ và media relay prototype. Hướng dẫn hiện hành ở ../docs/WEB_MEDIA_DEMO.md. Build từ gốc bằng scripts/build-web.ps1; JAR bao gồm React, mở http://127.0.0.1:8081 không cần reverse proxy. Dev port 5173 proxy /api và /ws. Những dòng “mốc 1” bên trên là lịch sử, không trạng thái hiện tại. Nghiệm thu mic/loa/share Windows nhiều máy và đo mạng vẫn còn.
