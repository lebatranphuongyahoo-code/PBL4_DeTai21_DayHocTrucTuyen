# Kiến trúc hiện tại — PBL4 Đề tài 21

Tài liệu này mô tả đúng bản web đang chạy trong nhánh trùng tu. Hệ thống dùng React/TypeScript ở trình duyệt, Spring Boot ở máy chủ, SQLite cho dữ liệu bền vững và WebSocket qua TCP cho media thời gian thực.

## Phân lớp

```text
Trình duyệt React
  ├─ REST /api/auth, /api/rooms, /api/admin
  ├─ WebSocket /ws/classroom (ticket một lần)
  └─ giao diện lớp, chat, media, bài tập
          |
Spring Boot
  ├─ AuthService + SecurityConfig: phiên bearer, role operator
  ├─ ClassroomService: owner/member/admin và transaction SQLite
  ├─ AdminController: quản lý toàn bộ lớp
  └─ MediaWebSocketHandler: kiểm tra membership rồi relay theo phòng
          |
SQLite: accounts, classrooms, members, meetings, messages, activities, submissions
```

`ADMIN` quản lý danh sách lớp, lưu trữ/khôi phục, đổi chủ lớp và chuyển học viên qua `/api/admin/*`. Admin không tự được điều khiển mic hoặc chia sẻ màn hình nếu chưa là thành viên lớp. `TEACHER` là chủ lớp và điều hành lớp của mình. `STUDENT` chỉ truy cập lớp khi là thành viên. Public registration luôn tạo tài khoản thường; cấp Admin/Teacher dùng operator cục bộ.

## Luồng quyền

1. Client đăng nhập nhận bearer token. Server tra session từ SQLite và tạo `Authentication` từ tài khoản phía server.
2. Khi mở phòng, `ClassroomService` kiểm tra lớp đang `ACTIVE` và người dùng là owner hoặc member.
3. Khi kết nối WebSocket, ticket dùng một lần được đổi lấy quyền media. Mỗi packet tiếp tục kiểm tra room/session/membership.
4. Media được fan-out tới các peer cùng phòng, trừ nguồn gửi. Quyền presenter và microphone được kiểm tra theo trạng thái của buổi học.
5. Admin archive lớp sẽ đóng buổi học và thu hồi quyền truy cập; restore giữ nguyên thành viên.

## Thuật toán mạng cốt lõi

Đề tài không có bằng chứng yêu cầu Minimax hoặc Max-min. Thuật toán thực sự là relay/fan-out theo phòng:

```text
onPacket(sender, packet):
    verify bearer ticket, room and membership
    if audio and sender has no microphone grant: drop
    if screen and sender is not current presenter: drop
    for peer in room except sender:
        enqueue to peer's bounded single-writer queue
        if queue is full or queued bytes exceed 2 MiB:
            close the slow peer
```

Với `n` peer, một packet cần tối đa `n-1` lần enqueue nên chi phí fan-out là `O(n)` mỗi packet. Mỗi peer chỉ có một writer tuần tự; queue bị giới hạn để client chậm không làm bộ nhớ tăng vô hạn. Callback nhận WebSocket và transaction SQLite vẫn có khóa dùng chung ở một số đoạn, vì vậy chưa được tuyên bố là hệ thống tải lớn.

WebSocket chạy trên TCP nên audio và screen cùng chịu head-of-line blocking. Đây là relay prototype cho lớp học nhỏ, chưa phải WebRTC/SFU và chưa có số đo tải thật.

## Tương tác học tập

Chủ lớp tạo bài tập chữ. Thành viên đọc, nộp hoặc nộp lại câu trả lời. Chủ lớp xem các bài đã nộp, chấm 0–10 và gửi phản hồi. Không nhận file và không có hạn nộp trong MVP. ACL được kiểm tra ở backend, không chỉ ẩn nút trên UI.

## Bằng chứng và giới hạn

- Backend có test cho Admin, archive/restore, transfer, media relay, chat, meeting history và activity ACL.
- Frontend có test điều hướng, chat, audio, vòng đời buổi học và Admin dashboard; build TypeScript/Vite đạt.
- Chưa có nghiệm thu hai máy thật, đo p50/p95, CPU/RAM hay chất lượng âm thanh. Không đưa các số này vào báo cáo khi chưa đo.
