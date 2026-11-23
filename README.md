# PBL4 · Đề tài 21 — Dạy học trực tuyến

Nhóm 4 · Hệ điều hành & Mạng máy tính · Đại học Bách khoa Đà Nẵng.

Dự án hiện chỉ duy trì bản web: React/TypeScript, Java Spring Boot với Tomcat nhúng, SQLite và WebSocket relay cho chat, âm thanh, ảnh màn hình. Mã Java Swing, server TCP/UDP cũ và bộ kiểm thử của bản desktop đã được gỡ.

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `web/src/` | Giao diện React và media trong trình duyệt |
| `web/public/` | CSS, audio capture worklet và tài nguyên tĩnh |
| `web/test/` | Kiểm thử frontend |
| `backend/src/main/` | API, xác thực, nghiệp vụ lớp/buổi, SQLite và WebSocket |
| `backend/src/test/` | Kiểm thử backend và giao thức WebSocket thật |
| `scripts/build-web.ps1` | Build frontend, kiểm thử và đóng gói backend |
| `pom.xml` | Maven tổng hợp, chỉ build module `backend` |
| `data/` | Database local; giữ nguyên đường dẫn đang dùng khi cập nhật |

## Build và chạy trong VS Code / PowerShell

Yêu cầu Java 21, Maven và Node.js tương thích Vite. Lần đầu cài frontend:

```powershell
npm --prefix web ci
```

Kiểm thử, build và đóng gói từ thư mục gốc:

```powershell
npm --prefix web test
npm --prefix web run build
mvn verify
```

Hoặc dùng script sẵn có: `./scripts/build-web.ps1`. Maven không tự build React, vì vậy luôn build frontend trước khi đóng gói JAR.

Chạy JAR web đã build:

```powershell
java -jar .\backend\target\classroom-web-server-0.1.0.jar
```

Mở http://127.0.0.1:8081. JAR có sẵn frontend và Tomcat; không cần cài server web riêng. `mvn package` tại gốc cũng build backend web; JAR nằm trong `backend/target`, không phải `target` ở gốc. Lệnh `mvn -f backend/pom.xml verify` vẫn dùng được.

Khi cập nhật bản đang chạy, giữ nguyên `PORT`, `BIND_ADDRESS`, `PBL4_WEB_DB` của terminal hiện tại để dùng đúng cổng và dữ liệu. Dừng JAR cũ rồi chạy JAR mới; restart đóng các buổi đang mở, chủ lớp bắt đầu buổi mới. Không dùng database QA thay dữ liệu thật.

## Hướng dẫn hiện hành

- [Demo ngắn trong VS Code và SQLite](docs/DEMO_VSCODE.md).
- [Chạy web và tạo link Cloudflare](docs/HUONG_DAN_CHAY_VA_GUI_LINK_CLOUDFLARE.md).
- [Học kiến trúc và kiểm thử web](docs/HUONG_DAN_HOC_VA_KIEM_THU_WEB.md).
- [Kịch bản kiểm thử nhóm trên hai mạng](docs/TEST_HAI_MANG.md).
- [Hướng dẫn test PBL4 từ A đến Z](docs/HUONG_DAN_TEST_A_DEN_Z.md).
- [Bản đồ học dự án web](docs/BAN_DO_HOC_WEB.md).

Tài liệu cũ về Swing, cổng TCP 9000/UDP 9001 và `com.pbl4.Launcher` trong `docs/` được giữ làm lịch sử; không dùng để chạy hoặc mô tả kiến trúc web hiện tại.
