# Thiết kế giáo trình học bản web

## Mục đích

Giúp thành viên nhóm bắt đầu từ chưa biết lập trình web có thể giải thích, lần theo và kiểm tra các luồng quan trọng của bản web do AI hỗ trợ tạo ra. Thành công không đo bằng việc thuộc tên file; người học phải kể lại được dữ liệu đi đâu, ai quyết định quyền, trạng thái được lưu ở đâu và hệ thống phản ứng thế nào khi lỗi xảy ra.

## Phạm vi

Chỉ gồm ứng dụng web hiện tại: React/TypeScript ở trình duyệt, HTTP API, Spring Boot, Spring Security, SQLite/JDBC, WebSocket, âm thanh/màn hình, build, cấu hình và test liên quan. Bản Java Swing cùng raw TCP/UDP nằm ngoài khóa này; chỉ được nhắc để phân biệt hai ứng dụng.

Khóa giải thích mã nguồn và quyết định của chính dự án. Nó không dạy toàn bộ nội bộ của React, Spring Boot, TCP hay SQLite.

## Cách học đã chọn

Học theo hành trình tính năng: bắt đầu từ thao tác người dùng rồi lần qua UI, mạng, backend và dữ liệu. Mỗi hành trình được ghép với checklist khái niệm để việc đi theo luồng không làm rơi các chủ đề nền tảng. Người học đã chọn cách này thay cho đọc file tuần tự.

Mạch dạy đi từ bản đồ tổng thể đến khái niệm nền, rồi mới vào một hành trình tính năng. Thứ tự phụ thuộc là: (1) ai chạy ở đâu và dữ liệu đi đâu, (2) trình duyệt và state UI, (3) request HTTP end-to-end, (4) tài khoản/xác thực, (5) dữ liệu và quy tắc lớp, (6) vòng đời lớp/buổi/kết nối, (7) WebSocket và thông điệp, (8) luồng chạy/đồng bộ/hàng đợi, (9) âm thanh, (10) màn hình/chat, (11) sự cố và kiểm thử, (12) build/vận hành/bảo vệ. Không đưa race condition hoặc queue trước khi chỉ ra thread, owner và nơi dữ liệu được giữ.

Mỗi bài theo nhịp: nối lại bài trước → mục tiêu dễ hiểu → sơ đồ đường đi → giải thích từ vựng mới → xem đúng đoạn code liên quan → bài tập nhớ lại hoặc dự đoán → cập nhật checklist. Một bài chỉ giới thiệu tối đa một vài khái niệm mới. Nếu cần dùng từ như callback, queue, thread hay lock thì phải nói đó là gì, thuộc tiến trình nào và code nào tạo/quản lý nó trước khi phân tích tình huống.

## Các cách tiếp cận đã cân nhắc

- Theo luồng tính năng: thấy cùng một chức năng đi qua các phần và lý do các phần phối hợp; đây là cách đã chọn.
- Theo file: phủ đều danh sách file nhưng dễ biến thành đọc mã rời rạc, không hợp với người mới bắt đầu.
- Chỉ dùng knowledge graph/dashboard: nhanh để tra quan hệ nhưng người học đã phản hồi dashboard khó theo dõi; không dùng làm phương pháp giảng chính.

## Giáo trình và điểm kiểm tra

1. **Bản đồ hệ thống**: client/server, tiến trình đang chạy, trình duyệt, HTTP, WebSocket, cơ sở dữ liệu; phân biệt lớp học, buổi học, kết nối. Kiểm tra: tự vẽ đường đi của một request và một gói media.
2. **Giao diện React**: `main.tsx`, component, props, `useState`, `useEffect`, `useRef`, form, dialog, điều hướng và giữ kết nối khi đổi màn hình. Kiểm tra: giải thích một thao tác UI làm state nào đổi.
3. **HTTP và API**: URL, method, request/response, JSON, mã trạng thái, kiểu dữ liệu, helper `api()`, lỗi và trạng thái tải. Kiểm tra: lần request từ click đến phản hồi UI.
4. **Tài khoản và bảo mật**: đăng ký, BCrypt, token opaque, hash token, cookie khôi phục, Bearer filter, hết hạn/đăng xuất/đổi mật khẩu, giới hạn thử đăng nhập, xác thực khác phân quyền. Kiểm tra: giải thích vì sao cookie khôi phục không thay thế Bearer và vì sao login không đồng nghĩa có quyền quản lý mọi lớp.
5. **Domain lớp học**: chủ lớp, membership, yêu cầu tham gia, quyền từng lớp, tạo/xóa/rời/ẩn lớp, mở/kết thúc buổi, mỗi tab/kết nối và lịch sử. Kiểm tra: dự đoán dữ liệu nào còn lại sau khi đóng tab, rời buổi, rời lớp, kết thúc buổi và restart server.
6. **SQLite và JDBC**: bảng/khóa ngoại/index, schema versions, migration 1–6, transaction, commit/rollback, một connection dùng chung, `synchronized`, lưu chat/lịch sử, dữ liệu cũ và giới hạn báo cáo lịch sử. Kiểm tra: lần một thao tác từ service tới câu SQL và giải thích rollback.
7. **WebSocket và giao thức ứng dụng**: HTTP upgrade, vé một lần ngắn hạn, handshake, join/leave, JSON control/chat, gói nhị phân, giới hạn kích thước, heartbeat, relay, rate limit, quyền mic/trình bày và thu hồi quyền. Kiểm tra: vẽ đường đi của kết nối hợp lệ và yêu cầu bị từ chối.
8. **Âm thanh trình duyệt**: quyền micro, `AudioWorklet`, resample 16 kHz, PCM16 mono, frame 320 mẫu/20 ms, header/sequence/source ID, playback timeline, echo/noise options, backpressure và cleanup. Phân biệt TCP giữ thứ tự byte trên mỗi kết nối với sequence của ứng dụng; sequence không phải bộ sắp xếp gói mạng của server.
9. **Chia sẻ màn hình**: browser permission, `getDisplayMedia`, canvas/JPEG, độ phân giải/tần suất, chỉ presenter được gửi, frame cũ bị bỏ khi buffer cao, stop/revoke tài nguyên và các giới hạn của chụp màn hình web.
10. **Chat và lịch sử**: chat thời gian thực so với lưu bền, lịch sử phân trang/giới hạn, gắn với buổi, merge/reconnect và quyền đọc. Kiểm tra: phân biệt tin trong socket với tin được truy vấn lại từ database.
11. **Bất đồng bộ, đồng bộ và đa luồng**: JavaScript event loop/Promise/callback, `AudioWorklet`, Java threads/executors/scheduled sweep, concurrent collections, `synchronized`, database lock/transaction, race condition, visibility, thứ tự sự kiện, queue giới hạn và slow peer. Kiểm tra: đọc một interleaving cụ thể và xác định lock/quy tắc nào bảo vệ dữ liệu; không suy ra `synchronized` tự quyết định nghiệp vụ.
12. **Vòng đời, lỗi và an toàn tài nguyên**: tab đóng, disconnect, reconnect, restart, stale async completion, generation/version guards, cleanup stream/audio context/object URL, interrupted attendance, server error/timeout và quyền bị thu hồi. Kiểm tra: kể nguyên nhân, trạng thái lưu lại và thông báo người dùng trong từng ca.
13. **Build, chạy và cấu hình**: Vite dev proxy, build TypeScript, Spring Boot JAR chứa static web, Java 21, port/bind, SQLite path, LAN/HTTPS/origin và quy trình build. Kiểm tra: giải thích lệnh chạy, dữ liệu nằm ở đâu và vì sao build web trước khi đóng gói.
14. **Test và bảo vệ dự án**: đọc test theo hành vi, phân biệt unit/integration/real WebSocket, map test với feature, giới hạn load test và đo đạc; luyện câu hỏi về kiến trúc, API, quyền, protocol, concurrency, dữ liệu và tradeoff. Hoàn thành khi người học tự lần được một luồng không học thuộc, nêu được file/test liên quan, và trả lời được ca lỗi cơ bản.

Các chủ đề an ninh, xác thực dữ liệu, giới hạn tài nguyên và quyền ở server là điều kiện xuyên suốt, không phải phần tùy chọn. Khi giải thích hành vi, mã nguồn và test hiện tại là chuẩn; tài liệu cũ hoặc knowledge graph chỉ là chỉ dẫn phụ.

## Nhịp và ước lượng

Một bài ngắn khoảng 30–45 phút, có thể chia thành nhiều lượt chat. Ước tính 24–36 giờ học và thực hành để đi hết giáo trình web và tự giải thích các luồng chính. Đây là ước lượng, không phải bảo đảm thành thạo; thời gian phụ thuộc việc người học tự mở code, chạy test và làm bài tập. Không ép tiến độ theo số buổi.

## Cách theo dõi

Trong cuộc trò chuyện, ghi rõ mỗi chủ đề là **chưa học / đang học / tự giải thích được / cần ôn**. Mỗi bài kiểm tra ít nhất một năng lực: dự đoán đường đi, giải thích dữ liệu/quyền, đọc code, hoặc áp dụng kiến thức vào tình huống mới. Nếu câu trả lời trước của giáo viên sai hoặc đơn giản hóa quá mức, sửa công khai trước khi xây tiếp.

## Phân biệt các hàng đợi

- **Server outbound queue**: do `Peer.sends` trong `MediaWebSocketHandler` quản lý; mỗi peer nhận có một executor và queue hữu hạn để socket chậm không chặn việc server đọc message, và để giới hạn bộ nhớ. `queuedBytes` đếm dữ liệu đang chờ gửi.
- **Browser WebSocket buffer**: do trình duyệt quản lý; ứng dụng chỉ đọc `socket.bufferedAmount` trước khi gửi frame mic/màn hình để quyết định có bỏ frame mới hay không. Đây không phải queue JS tự tạo trong project.
- Hai buffer ở hai đầu khác nhau, có mục tiêu và chính sách khác nhau. Luồng dạy phải vẽ vị trí trước khi bàn backpressure hoặc frame bị bỏ.

## Điều không làm

- Không dạy desktop trong lộ trình web.
- Không yêu cầu học thuộc toàn bộ source hoặc nội bộ framework.
- Không khẳng định tải tối đa, bảo đảm chất lượng media hay số người dùng nếu chưa đo trong điều kiện thật.
- Không coi việc dựng knowledge graph là thay thế cho giải thích và thực hành cùng người học.
