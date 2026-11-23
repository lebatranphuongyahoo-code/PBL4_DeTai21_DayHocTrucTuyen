# Lộ Trình Học Bản Web

> **For agentic workers:** This is a guided learning plan, not a software implementation. Teach each unchecked unit conversationally from the current source. Do not change application code unless separately requested.

**Goal:** Giúp người học từ con số không lần theo, giải thích và kiểm tra được các luồng quan trọng của bản web trong dự án.

**Architecture:** Dạy theo hành trình người dùng qua React, HTTP/WebSocket, Spring Boot và SQLite. Duy trì checklist khái niệm; mỗi bài có ví dụ từ source, một bài tập nhỏ và điểm kiểm tra.

**Tech Stack:** React 19, TypeScript, Vite, Java 21, Spring Boot 3, Spring Security, WebSocket, JDBC, SQLite.

**Spec:** `docs/superpowers/specs/2026-11-10-web-project-learning-design.md`

## Global Constraints

- Phạm vi chỉ gồm web; desktop Swing/raw TCP/UDP không thuộc lộ trình.
- Người học mới bắt đầu; giải thích thuật ngữ trước khi dùng và chỉ giới thiệu ít khái niệm mỗi bài.
- Mã nguồn và test hiện tại là chuẩn; docs/graph cũ chỉ tham khảo.
- Không đoán tải hoặc chất lượng media khi chưa có phép đo thật.
- Học viên phải tự giải thích được ý chính; không lấy trả lời đúng một câu làm bằng chứng đã nắm chủ đề.

## Review Focus

- Không nhầm xác thực danh tính với phân quyền trên từng lớp.
- Không nhầm lớp, membership, buổi học và kết nối WebSocket.
- Không nói server sắp xếp packet audio bằng sequence; phân biệt TCP order với application sequence.
- Không mô tả `synchronized` như cơ chế tự quyết định thứ tự nghiệp vụ hoặc bảo vệ mọi queue.
- Không nhầm chat đang phát trực tiếp với chat đã lưu và truy vấn lại.

---

## Theo dõi tiến độ

Trạng thái: **Chưa học**, **Đang học**, **Tự giải thích được**, **Cần ôn**. Cập nhật trong cuộc trò chuyện sau mỗi đơn vị; giữ chỗ cho câu hỏi và phần chưa chắc.

### Đơn vị 1: Kiến trúc và từ vựng nền

- [x] Phân biệt trình duyệt, server, database; client-server và backend layers ở mức tổng quan.
- [x] Phân biệt lớp, buổi học, kết nối.
- [ ] Tự vẽ một request HTTP và một luồng media từ trình duyệt tới người nhận.

### Đơn vị 2: React và giao diện

- [ ] Điểm khởi động, component tree, JSX, props và render.
- [ ] State, ref, effect và vòng đời component.
- [ ] Form, dialog, loading/error, danh sách lớp và điều hướng.
- [ ] Vì sao giữ ClassroomMedia mounted khi chuyển màn hình quản lý.

### Đơn vị 3: HTTP, API và đăng nhập

- [x] HTTP, API, JSON, request/response ở mức khái niệm.
- [ ] Endpoint, method, body/header/status code trong Network panel.
- [ ] Lần login từ submit đến phản hồi và tải danh sách lớp.
- [ ] Token opaque, hash, cookie khôi phục, Bearer filter, hết hạn, logout, đổi mật khẩu, rate limit.
- [ ] Xác thực và phân quyền; quyền theo từng lớp.

### Đơn vị 4: Quy tắc lớp và dữ liệu

- [x] Chủ lớp, thành viên, buổi học và kết nối có vòng đời khác nhau.
- [ ] Controller, Service, Database và ranh giới trách nhiệm trong một luồng tạo/tham gia lớp.
- [ ] Bảng, khóa ngoại, index, prepared statement, migration và schema version.
- [ ] Transaction, commit, rollback, dữ liệu sống qua restart và lịch sử có giới hạn.

### Đơn vị 5: WebSocket, chat và quyền media

- [x] WebSocket là kết nối giữ mở, server kiểm tra quyền và relay tới peer cùng lớp.
- [ ] Vé ngắn hạn dùng một lần, handshake, join/leave, presence, ping/pong và reconnect.
- [ ] JSON control/chat so với binary media; giới hạn, rate limit và close codes.
- [ ] Quyền mic/presenter, request/grant/revoke và điều gì xảy ra khi quyền đổi giữa luồng.
- [ ] Chat trực tiếp, lưu lịch sử, phân trang, reconnect/merge và ràng buộc theo buổi.

### Đơn vị 6: Âm thanh và màn hình

- [x] Âm thanh gửi theo frame khoảng 20ms; ảnh màn hình có thể bỏ frame cũ.
- [ ] AudioWorklet, sample rate, PCM16, byte layout, sequence/source ID và playback timeline.
- [ ] TCP reliability/head-of-line delay, queue/backpressure và giới hạn trải nghiệm.
- [ ] getDisplayMedia, canvas/JPEG, tần suất/độ phân giải, presenter và stop cleanup.
- [ ] Phân biệt điều browser, server và hệ điều hành thực hiện.

### Đơn vị 7: Async, đồng bộ và đa luồng

- [x] Nhận diện bài toán tranh chấp khi hai thao tác đổi quyền cùng lúc.
- [ ] JavaScript event loop, Promise, WebSocket callbacks và AudioWorklet thread.
- [ ] Java server callbacks, virtual-thread sender executors, scheduled sweeps và concurrent maps.
- [ ] `synchronized`, monitor/visibility, lock scope, database connection và transaction.
- [ ] Race/interleaving, thứ tự sự kiện, queue có giới hạn, slow recipient và cleanup.
- [ ] Phân tích tình huống thu hồi quyền đồng thời packet audio đang tới; biết packet đã queue có thể vẫn được gửi.

### Đơn vị 8: Test, vận hành và bảo vệ

- [ ] Ghép hành vi với test frontend/backend; unit so với integration/real WebSocket.
- [ ] Build TypeScript/Vite và đóng gói Spring Boot JAR.
- [ ] Port, bind address, proxy, HTTPS/origin, SQLite path và chạy local/LAN.
- [ ] Các ca lỗi: 401, membership revoke, meeting end, restart, timeout, chậm mạng.
- [ ] Luyện giải thích kiến trúc và tradeoff; tự lần một tính năng chưa học.

## Nhịp bài

Mỗi lượt chat học một khái niệm nhỏ, thường 30–45 phút nếu có thực hành. Bắt đầu bằng nhắc lại ngắn và sửa hiểu nhầm; sau đó dùng ví dụ source, một tình huống dự đoán, rồi cập nhật trạng thái. Ước lượng toàn khóa 24–36 giờ thực hành, điều chỉnh theo câu trả lời và thời gian tự đọc code.

## Thứ tự phụ thuộc để giảng

Checklist ở trên là danh mục bao phủ; khi dạy, dùng thứ tự khái niệm này để không nhảy trước nền tảng:

1. Toàn cảnh: tiến trình, trình duyệt, server, database, request, connection.
2. React: component, state, props, event và effect.
3. Một thao tác HTTP hoàn chỉnh từ click tới database và render kết quả.
4. Đăng nhập, token, xác thực, rồi phân quyền.
5. Dữ liệu SQLite và quy tắc lớp/buổi/kết nối.
6. WebSocket lifecycle và các kiểu thông điệp.
7. Khái niệm thread/callback trước khi nói race, lock hoặc queue.
8. Hai buffer riêng: browser `bufferedAmount` và server `Peer.sends`.
9. Luồng audio/screen, backpressure và tình huống thu hồi quyền.
10. Chat/lịch sử, reconnect, restart, test, build và bảo vệ.

### Phân biệt buffer theo nơi sở hữu

- `web/src/ClassroomMedia.tsx`: ứng dụng đọc `WebSocket.bufferedAmount` của trình duyệt. Browser giữ dữ liệu đang chờ ghi ra kết nối; project không tự tạo một queue JavaScript cho việc này. Ngưỡng cao làm client bỏ frame mới để không đẩy dữ liệu cũ vào buffer.
- `backend/src/main/java/com/pbl4/web/MediaWebSocketHandler.java`: server tạo `Peer.sends` và `queuedBytes` riêng cho từng người nhận. Queue này giúp việc ghi chậm vào socket của một người không giữ luồng nhận chung; queue có giới hạn để ngắt peer chậm và bảo vệ bộ nhớ.
- Khi một gói đã được server kiểm tra rồi enqueue, thu hồi quyền không thể rút gói đó ra. Callback xử lý quyền trước thì gói tiếp theo bị từ chối; callback audio trước thì gói đã enqueue có thể vẫn tới.

Không phân tích trường hợp race trước khi đã xác định thread nào gọi, lock nào đang giữ, trạng thái nằm ở đâu và queue thuộc tiến trình nào.
