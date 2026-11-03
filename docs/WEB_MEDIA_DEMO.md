# Web classroom — chạy và nghiệm thu, 03/11/2026

## Luồng nghiệp vụ đã chọn

Đề 21 không bắt buộc ADMIN/duyệt hồ sơ giáo viên hoặc Swing. Người dùng đã ủy quyền chọn luồng phù hợp. Public register chỉ STUDENT; người vận hành cấp TEACHER bằng CLI, không thêm hồ sơ xét duyệt ở mốc này. TEACHER tạo/sở hữu lớp, thêm học viên theo username hoặc duyệt yêu cầu mã lớp. Biết mã chưa được vào lớp. Membership bền vững tách khỏi kết nối online: rời trang ngắt media; Rút khỏi lớp xóa membership; Lưu trữ thu hồi truy cập cả lớp.

## Build và chạy

JDK 21, Maven 3.9+, Node để build React. Từ gốc repo:

```powershell
cd web
npm ci
cd ..
./scripts/build-web.ps1
# Nếu Maven dùng sai home/cache trên máy này:
./scripts/build-web.ps1 -MavenRepository C:/Users/ADMIN/.m2/repository
java -jar backend/target/classroom-web-server-0.1.0.jar
```

Mở http://127.0.0.1:8081. JAR chứa React, REST và WebSocket. Dừng JAR trước khi build lại trên Windows vì file đang chạy có thể bị khóa. Dev riêng: cd web rồi npm run dev; port 5173 proxy cả /api và /ws.

Đăng ký tài khoản người dạy, dừng server rồi chạy một lần, dùng cùng DB:

```powershell
java -jar backend/target/classroom-web-server-0.1.0.jar --grant-teacher=username_giao_vien
```

Lần sau bỏ --grant-teacher; login lại để cập nhật quyền UI. CLI dành người được vận hành server. Không tự tạo tài khoản/mật khẩu đặc quyền.

DB mặc định ./data/classroom-web.db tính từ thư mục chạy, đổi bằng --app.database=...; web DB tách DB Swing. Sao lưu khi server dừng. Không nhập tự động SHA-256 hoặc quyền giáo viên cũ. Migration legacy cần rà soát quyền và reset mật khẩu có xác minh; chưa có công cụ nhập legacy.

## HTTPS cho LAN

HTTP localhost dành phát triển; hai máy dùng certificate được cả hai máy tin cậy, SAN chứa hostname/IP server. Không lưu password keystore vào Git.

```powershell
$env:PBL4_TLS_KEYSTORE='file:E:/certificates/classroom.p12'
$env:PBL4_TLS_PASSWORD='mat_khau_keystore'
$env:PBL4_ALLOWED_ORIGINS='https://ten-may-server:8081'
java -jar backend/target/classroom-web-server-0.1.0.jar --spring.profiles.active=lan
```

Origin phải khớp URL thực, không wildcard * để bỏ kiểm tra. Certificate/firewall phụ thuộc máy triển khai; cấu hình LAN/HTTPS chưa xác minh trong lượt này.

## Media và đa luồng

MediaWebSocketHandler nhận/chuyển tiếp trực tiếp, không P2P. Mỗi peer có executor gửi riêng dùng virtual thread JDK21, tối đa 32 message và 2 MiB đang chờ; decorator đồng bộ send. Người nhận quá chậm bị ngắt. Giới hạn prototype 128 kết nối. SQLite transaction ngắn được synchronized; không gửi mạng trong khóa DB. Kiểm tra membership/session người gửi và người nhận, sweep mỗi giây thu hồi socket nhàn rỗi sau logout/remove/archive. Vé kết nối một lần, 30 giây; URL không chứa bearer dài hạn.

| Loại | Client gửi | Server relay |
|---|---|---|
| Màn hình | byte 1 + JPEG, tối đa 1.5 MB | Giữ payload; không gửi lại người phát |
| Audio | byte 2 + uint32 sequence big-endian + 320 mẫu int16 little-endian | Chèn uint64 accountId big-endian sau byte loại, tổng 653 byte |

PCM mono 16kHz, khung 20ms; AudioWorklet resample theo sampleRate thật. Mic/loa độc lập; bật loa cần thao tác người dùng. Người nói có timeline riêng; lịch playback tương lai giới hạn 160ms. JPEG khoảng 8fps, 1280x720; client bỏ frame khi nghẽn. Server audio tối đa 65 khung/giây.

WebSocket chạy TCP: truyền tin cậy nhưng mất gói mạng có thể gây chờ/tăng trễ. Đây là relay Java cho prototype lớp nhỏ để thể hiện server/đa luồng/đồng bộ theo đề; chưa phải SFU/WebRTC hoặc bằng chứng quy mô/độ trễ. Audio/screen cùng socket vẫn tranh băng thông; phải đo trước khi quyết định tách socket/nâng gateway.

Browser picker được gợi ý chọn window/tab, ẩn current tab/full desktop khi hỗ trợ. Client từ chối nguồn monitor nếu browser báo loại đó, giáo viên không hiển thị preview tự phản chiếu. Web API không đảm bảo loại trừ mọi cửa sổ trên mọi browser: vẫn phải chọn đúng tài liệu. Nút dừng và track ended gửi screen-stop xóa ảnh học viên; rời trang/ngắt mạng dừng tracks/contexts.

## Bằng chứng

- Backend 6 JUnit tests, 0 failure/error: auth/hash/password/session/persistence, HTTP role injection, lớp/duyệt/quyền chéo/concurrent add và socket thật. Relay test: cách ly hai lớp, teacher-only screen, packet audio, hai nguồn gửi đồng thời, JSON chat, remove/logout/archive ngắt kết nối.
- React TypeScript/Vite build đạt; JAR chứa UI đóng gói đạt sau khi dừng server thử.
- Chromium Windows headless: login/create/add, socket qua Vite, khung màn hình canvas nhận được, stop xóa ảnh, fake mic start/stop giữ loa, chat, mobile không tràn ngang, remove ngắt media. Luồng mã lớp→chờ duyệt chưa vào→duyệt→vào→rút đạt. Không pageerror.
- Screen canvas và fake mic chỉ chứng minh pipeline/lifecycle, không chất lượng âm thanh hay picker thật. Script browser tạm trong TEMP; ảnh web/test gitignored.

## Nghiệm thu còn cần máy thật

| Kịch bản | Tiêu chí | Trạng thái |
|---|---|---|
| Hai Windows HTTPS | Login/tạo/thêm/duyệt; student chỉ vào lớp được cấp | Chưa xác minh |
| Share picker | Window PowerPoint/tab khác; hủy/end; người vào muộn; stop không ảnh cũ | Pipeline một phần, picker thật chưa xác minh |
| Phản chiếu | Nguồn bài giảng hợp lệ không lặp; không chọn app/desktop | Chưa xác minh thật |
| Teacher + 3 students | Voice hai chiều; ít nhất hai người cùng nói; mic off vẫn nghe; tai nghe không vọng | Socket concurrent đạt; nghe thật chưa xác minh |
| Mạng/độ trễ | Đo p50/p95/loss/frame bỏ/CPU/RAM; mục tiêu đề xuất audio p95<250ms, screen<500ms LAN | Chưa đo/chưa cam kết đạt |
| Disconnect/reconnect | Không queue vô hạn; UI báo ngắt; không bypass quyền | Unit/socket/UI một phần, mạng thật chưa xác minh |
| Restart | DB còn lớp/thành viên; phiên media phải nối lại; vé cũ không tái dùng | DB test đạt; demo nhiều máy chưa xác minh |

Chưa tuyên bố hoàn thiện đề 21. Rate limit login/register, phục hồi mật khẩu, admin GUI, nhập legacy và vận hành Internet chưa triển khai. Chúng không đều là yêu cầu đề, nhưng cần cân nhắc trước khi mở public.
