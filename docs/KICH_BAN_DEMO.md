# Kịch bản demo 5 phút — PBL4 Đề tài 21

## Chuẩn bị

```powershell
./scripts/build-web.ps1
java -jar backend/target/classroom-web-server-0.1.0.jar
```

Mở `http://127.0.0.1:8081`. Tạo ba tài khoản, sau đó dừng server một lần để cấp quyền vận hành:

```powershell
java -jar backend/target/classroom-web-server-0.1.0.jar --grant-teacher=teacher_demo --grant-admin=admin_demo
```

Đăng nhập lại sau khi cấp quyền.

## Trình tự trình bày

| Bước | Thao tác | Điểm cần giải thích |
|---|---|---|
| 1 | Admin mở dashboard và lọc lớp đang hoạt động | Admin là cấp quản lý toàn hệ thống; UI gọi `/api/admin/*`. |
| 2 | Teacher tạo lớp, mở quản lý lớp và gửi mã mời | Chủ lớp là owner theo quan hệ lớp, không phải quyền global. |
| 3 | Student nhập mã, Teacher duyệt yêu cầu | Backend kiểm tra membership; người ngoài không đọc được lớp. |
| 4 | Teacher bắt đầu buổi học; Student vào phòng | Buổi học có session riêng; join/leave chỉ ghi lịch sử kết nối kỹ thuật. |
| 5 | Hai người dùng chat, xin phát biểu, bật/tắt mic hoặc chia sẻ màn hình | WebSocket server relay theo phòng; không gọi WebRTC/P2P. |
| 6 | Teacher mở Bài tập tương tác, đăng câu hỏi chữ | Nội dung và giới hạn độ dài được kiểm tra ở service. |
| 7 | Student nộp câu trả lời; Teacher xem, chấm 0–10 và gửi phản hồi | Đây là tương tác học tập; Student chỉ thấy bài của mình, Teacher thấy bài thành viên. |
| 8 | Admin lưu trữ lớp; thử mở lại bằng Student | Lớp archived bị chặn; Admin khôi phục được và thành viên vẫn còn. |

## Khi thầy hỏi thuật toán

Trả lời: “Thuật toán lõi là relay/fan-out theo phòng. Server xác thực ticket và membership, kiểm tra quyền mic/presenter, rồi đưa packet vào hàng đợi giới hạn riêng của từng peer. Peer chậm bị ngắt khi queue vượt ngưỡng. Với `n` người, mỗi packet được chuyển tối đa `n-1` lần nên chi phí là `O(n)`.”

Không gọi phần này là Minimax/Max-min khi chưa có yêu cầu hoặc triển khai tương ứng. Không trình bày lịch sử kết nối như báo cáo điểm danh.

## Dữ liệu dự phòng

Nếu không có hai máy thật, chạy backend test và frontend test/build để chứng minh quyền, relay, chat và vòng đời UI. Ghi rõ đây là kiểm thử tự động; chưa thay thế nghiệm thu mạng thật và không được bịa số latency hoặc CPU/RAM.
