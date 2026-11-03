# Media và điều hành lớp — bản sửa 03/11/2026

## Phạm vi và bằng chứng

- Mỗi kết nối WebSocket có sourceId âm thanh riêng. Trước đây accountId được dùng làm nguồn; reconnect hoặc hai tab cùng tài khoản có thể khiến bộ lọc số thứ tự loại nhầm tiếng. SourceId không phải accountId; kích thước packet vẫn 653 byte. Client và server phải cập nhật cùng phiên bản.
- Loa resume lại context đang suspended thay vì tạo thêm context; UI phản ánh trạng thái loa/mic bị trình duyệt tạm dừng. Mic được giải phóng khi bị tạm dừng để người dùng bật lại. Sửa biên nội suy resampler ở 44.1 kHz.
- Ping/pong mỗi 5 giây; quá 45 giây không nhận phản hồi thì kết nối đóng. Có trạng thái context và tổng số gói gửi/nhận để khoanh vùng tiếng một chiều. Mã ngắt 1008 dành cho phiên/quyền, 1013 cho hàng đợi chậm, 1011 cho lỗi gửi/transport. Log không ghi token/ticket hay nội dung audio.
- Học viên xin/hủy yêu cầu chia sẻ; chủ lớp duyệt/từ chối/thu hồi. Một kết nối có quyền trình bày tại một thời điểm. Chủ lớp có thể lấy lại quyền khi chủ động bắt đầu chia sẻ. Server chặn frame không có quyền; người trình bày không xem lại frame của mình, chủ lớp xem được frame học viên.
- Chủ lớp khóa/mở quyền mic học viên cả lớp, cấp/thu hồi ngoại lệ cho từng kết nối; học viên xin phát biểu. Server chặn audio bị khóa. Mở quyền không tự bật micro; cần thao tác người dùng và quyền thiết bị của browser. Khóa cả lớp xóa ngoại lệ đang có.
- Quyền là trạng thái buổi học trong RAM: quyền riêng hết khi rời/reconnect; khóa cả lớp giữ khi còn người trong phòng, hết khi phòng trống hoặc server restart. Không sửa schema hoặc dữ liệu SQLite.
- Đồng bộ xử lý receive/control để quyết định quyền và relay không đua nhau; gửi dùng hàng đợi riêng từng người. Đây là thiết kế cho demo lớp nhỏ, chưa chứng minh tải lớn.

Đã qua: Maven verify (7 test), frontend build, 3 test resampler 16/44.1/48 kHz; WebSocket thật kiểm tra quyền, cô lập lớp, đồng thời, thu hồi thành viên; Playwright hai browser kiểm tra luồng share học viên, chủ lớp thấy ảnh, thu hồi, khóa/xin/cấp/thu hồi mic, không tự bật mic khi mở khóa; audio giả lập hai chiều, tắt/bật mic, chat, giao diện mobile và xóa thành viên.

Chưa xác nhận: nguyên nhân duy nhất của tiếng một chiều trên hai máy người dùng, mic/loa thật sau nhiều phút và khi chuyển tab/sleep, độ trễ/mất tiếng trên hai mạng; tải nhiều người cùng phát; nghiệm thu đầy đủ đề tài.

## Test tuyến tính máy A (chủ lớp) và máy B (học viên)

1. Cả hai tải lại cùng URL tunnel bằng Ctrl+Shift+R, đăng nhập và vào lớp cũ. Mỗi máy chỉ mở một tab lớp, dùng tai nghe.
2. Cả hai Bật loa và Bật mic; luân phiên đếm 1–10 trong ít nhất 5 phút. B tắt/bật mic; B ra danh sách rồi vào lại, bật mic/loa lại; A phải tiếp tục nghe.
3. Nếu mất tiếng, ghi lại dòng Mic/Loa/Gói gửi-nhận ở cả A và B sau hai lần cách nhau 5 giây. Nếu B không tăng gửi, kiểm tra capture/thiết bị; nếu A tăng nhận mà không nghe, kiểm tra playback/output. Tổng gói nhận có thể gồm nhiều người, nên test hai người trước.
4. A khóa mic cả lớp. B đang nói phải dừng; nút báo mic bị khóa. B Xin phát biểu; A Duyệt phát biểu; B tự Bật mic. A Thu hồi quyền mic; B dừng gửi audio. A Mở quyền mic cả lớp; B vẫn cần tự bật.
5. B Xin chia sẻ màn hình; A Duyệt chia sẻ; B Chia sẻ bài giảng và chọn cửa sổ/tab tài liệu. A thấy ảnh; B không xem lại màn hình mình. A Dừng người đang chia sẻ; B dừng capture. Test từ chối/hủy yêu cầu và hủy picker.
6. A chia sẻ lại bài giảng; hai bên tiếp tục nói; B vẫn xem được. B rời phòng: yêu cầu/quyền riêng mất. Xóa thành viên/lưu trữ lớp tiếp tục phải ngắt media.

## Các phần nghiệp vụ còn cần hoàn thiện

Đề tài không bắt buộc role tài khoản cố định, duyệt share hay khóa mic; đây là chính sách triển khai lớp có người điều hành. Bản hiện tại vẫn dùng teacher/student toàn hệ thống; chưa chuyển sang chủ lớp/thành viên theo từng lớp kiểu phòng họp. Không đổi chính sách tài khoản âm thầm trong đợt sửa media.

Các phần cần giai đoạn riêng: cấp quyền giáo viên qua giao diện quản lý thay CLI, quên/đổi mật khẩu và session management, rate limit login/register và chống spam control, danh sách yêu cầu tham gia của học viên, lưu trạng thái điều hành nếu cần sống qua restart, đo tải/độ trễ thật và chiến lược media khi đông người. Không dùng kết quả test ngắn để tuyên bố hoàn thiện hoàn toàn.

## Build riêng khi server đang chạy

Chạy npm --prefix web test và npm --prefix web run build, sau đó:

```powershell
mvn -f backend/pom.xml "-Dmaven.repo.local=C:/Users/ADMIN/.m2/repository" "-Dserver.artifact.name=classroom-media-candidate" verify
```

JAR candidate không ghi đè JAR đang chạy. Chỉ dừng đúng server và thay sau khi test qua, giữ nguyên --app.database và origin tunnel thật. Mỗi lần đổi URL tunnel phải đổi --app.allowed-origins; không copy nguyên URL mẫu.
