# Hướng dẫn test PBL4 từ A đến Z

Hướng dẫn này dành cho thành viên nhóm tự tải nhánh, chạy trên database thử riêng và kiểm tra luồng Admin → Teacher → Student. Đây là kiểm thử thủ công; hãy chạy cả test tự động bên dưới.

## A–D. Tải source, kiểm thử và đóng gói

Cài Git, JDK 21, Maven 3.9+ và Node.js tương thích Vite 7. Mở PowerShell:

```powershell
git clone --single-branch --branch codex/trung-tuong-2026-11-22 https://github.com/lebatranphuongyahoo-code/PBL4_DeTai21_DayHocTrucTuyen.git
cd PBL4_DeTai21_DayHocTrucTuyen
git branch --show-current
git log -1 --oneline
npm --prefix web ci
npm --prefix web test
npm --prefix web run build
mvn '-Dserver.artifact.name=classroom-web-server-qa' verify
```

Lệnh Maven dùng tên JAR QA riêng để không vướng file đang chạy. Để đóng gói JAR chứa frontend mới nhất:

```powershell
npm --prefix web run build
mvn '-Dserver.artifact.name=classroom-web-server-qa' -f backend/pom.xml package
```

Ghi lại tên nhánh và commit test. JAR thử nghiệm là `backend/target/classroom-web-server-qa.jar`.

## E–G. Chạy server và tạo tài khoản thử

```powershell
New-Item -ItemType Directory -Force data | Out-Null
java -jar .\backend\target\classroom-web-server-qa.jar --app.database=./data/pbl4-qa.db
```

Giữ terminal mở; vào `http://127.0.0.1:8081`. `/api/health` phải trả `"ok":true`. Chỉ dùng DB `data/pbl4-qa.db`, không dùng DB dự án đang có dữ liệu.

Trên giao diện, đăng ký lần lượt các tài khoản dưới đây. Username gồm chữ Latin, số, `_`; mật khẩu cần ít nhất 8 ký tự gồm chữ hoa, chữ thường, số và ký tự đặc biệt.

| Tài khoản | Mục đích |
|---|---|
| `qaadmin` | Admin hệ thống |
| `qateacher` | Giáo viên/chủ lớp |
| `qastudent1` | Học viên thứ nhất |
| `qastudent2` | Học viên thứ hai |

Đăng ký công khai tạo Student và không cho tự chọn role. Tạo đủ tài khoản xong, dừng server `Ctrl+C`, rồi chạy lại cùng JAR/DB với quyền Admin:

```powershell
java -jar .\backend\target\classroom-web-server-qa.jar --app.database=./data/pbl4-qa.db --grant-admin=qaadmin
```

Đăng nhập Admin bằng `qaadmin`. Cờ này chỉ dùng cho tài khoản thử tại máy điều khiển.

## H–I. Đăng nhập, đăng ký và quyền Student

Kiểm tra màn hình đăng nhập:

- Màn desktop có bố cục hai vùng, panel nhận diện lớp học và form sáng; phong cách lấy cảm hứng từ ảnh tham khảo nhưng nội dung phù hợp PBL4.
- Bấm chuyển qua lại giữa **Đăng nhập** và **Tạo tài khoản**; đăng ký có họ tên, tên tài khoản, mật khẩu và nhập lại mật khẩu.
- Thử mật khẩu sai quy tắc và mật khẩu nhập lại không khớp; lỗi phải hiển thị, không đăng nhập/đăng ký nhầm.
- Không có email, Google, quên mật khẩu hay “ghi nhớ đăng nhập” vì sản phẩm hiện không có các luồng này.
- Thu nhỏ về khoảng 390 × 844: form xếp dọc, không tràn ngang, vẫn dùng được.
- Đăng ký Student rồi đăng nhập. Student không thấy nút, menu hay form **Tạo lớp**, nhưng vẫn thấy **Nhập mã lớp/Tham gia**.

Xác nhận backend cũng từ chối Student (thay mật khẩu mẫu bằng mật khẩu QA vừa đặt):

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8081/api/auth/login -ContentType 'application/json' -Body (@{ username='qastudent1'; password='MAT_KHAU_TEST' } | ConvertTo-Json)
$studentHeaders = @{ Authorization = "Bearer $($login.token)" }
try {
  Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8081/api/rooms -Headers $studentHeaders -ContentType 'application/json' -Body '{"name":"Student không được tạo"}'
  'LỖI: Student tạo được lớp'
} catch { "HTTP $([int]$_.Exception.Response.StatusCode) (mong đợi 403)" }
```

## J–K. Admin cấp Teacher và tạo lớp

Đăng nhập `qaadmin`, mở **Quản lý tài khoản**:

1. Cấp quyền giảng viên cho `qateacher`; xác nhận nhãn role đổi thành Giảng viên.
2. Không còn nút cấp Teacher cho người đã là Teacher/Admin.
3. Mở **Quản lý tất cả lớp** → **Tạo lớp**, đặt tên `Lớp QA PBL4`, chọn `qateacher` làm Teacher phụ trách.
4. Xác nhận lớp xuất hiện cùng đúng chủ lớp. Admin không trở thành thành viên hay có quyền media của lớp.
5. Thử bỏ trống Teacher; UI không cho gửi. Thử gọi API với Student làm owner; backend phải từ chối.
6. Thử xem thành viên, đổi Teacher phụ trách, lưu trữ và khôi phục lớp QA.

## L–N. Teacher, Student và phân quyền API

Đăng xuất Admin, đăng nhập `qateacher`:

- Lớp QA xuất hiện; Teacher mở lớp và có thể bắt đầu buổi, tạo bài tập chữ.
- Teacher chỉ quản lý lớp được giao. Gọi `/api/admin/rooms` phải nhận HTTP 403.

Đăng nhập `qastudent1` trong cửa sổ trình duyệt thứ hai:

1. Nhập mã mời lớp và gửi yêu cầu. Trước khi Teacher duyệt, lớp/buổi chưa truy cập được.
2. Teacher duyệt yêu cầu; Student làm mới hoặc chờ cập nhật rồi mở lớp.
3. Student được nộp câu trả lời bài tập, nhưng không tạo bài, chấm điểm hay xem dữ liệu lớp khác.
4. Tài khoản ngoài lớp không truy cập API lớp đó.

Test tự động `AdminApiTest` bao phủ Teacher gọi các route `/api/admin/*` và xác nhận 403. Chạy lại `mvn verify` nếu cần xác minh cả nhóm endpoint.

## O–Q. Bài tập, buổi học, chat và lịch sử

- Teacher tạo bài tập; Student nộp; Teacher xem bài, chấm và phản hồi; Student xem được phản hồi.
- Dùng hai profile trình duyệt hoặc cửa sổ thường + ẩn danh để đăng nhập Teacher và Student cùng lúc.
- Teacher bắt đầu buổi, Student tham gia; thử chat hai chiều, mic, loa và chia sẻ tab/cửa sổ tài liệu. Dừng chia sẻ, rời/vào lại buổi, kết thúc buổi; xác nhận media dừng khi buổi đóng.
- Thử tắt mạng ngắn rồi bật lại, ghi nhận thời gian/kết quả kết nối lại.
- Teacher/chủ lớp xem lịch sử các lượt vào/rời. Student/thành viên không xem chi tiết kết nối người khác; người ngoài lớp bị chặn. Đây là lịch sử kết nối, không phải điểm danh.
- Ghi nhận một lượt vào/rời, reload trang và xác nhận còn. Dừng server `Ctrl+C`, chạy lại với đúng `--app.database=./data/pbl4-qa.db`, đăng nhập và xác nhận lớp/bài/lịch sử còn.

> Kiểm tra mic/loa/share qua Internet cần HTTPS; xem `docs/TEST_HAI_MANG.md` cho Cloudflare Tunnel. Thành công bắt tay không chứng minh chất lượng âm thanh: cần người thật xác nhận hai chiều trên thiết bị/mạng thực.

## R. Trạng thái tải, lỗi, rỗng và giao diện nhỏ

- Với DB QA chưa có lớp, Admin thấy “Chưa có lớp nào” sau khi API tải xong.
- Khi API đang tải, thấy chỉ báo tải và không thấy “0 lớp”/empty giả.
- DevTools → Network → Offline, bấm làm mới: hiện trạng thái lỗi, không hiểu lỗi thành danh sách rỗng. Bật lại mạng và thử tải thành công.
- Thử trạng thái tương tự ở danh sách tài khoản. Bài tập cũng có chỉ báo đang tải/cập nhật.
- Mở màn login trên desktop và mobile; ở giao diện lớp/Admin kiểm tra không tràn ngang, nội dung/nút bấm dùng được.

## S. Ghi kết quả và xử lý sau test

Ghi commit, vai trò, trình duyệt, kích thước màn, bước tái hiện, mong đợi, thực tế và thời điểm. Có thể chụp màn hình nhưng che username thật, mật khẩu, token, cookie và dữ liệu cá nhân. Phân loại lỗi: sai quyền, chặn luồng chính, hiển thị hay thiết bị/mạng.

Sau khi dừng server, database `data/pbl4-qa.db` có thể được giữ để tái hiện lỗi hoặc xóa thủ công sau khi xác nhận đúng file QA. Không xóa database ứng dụng đang dùng. Không gửi bearer token hay mật khẩu vào nhóm.

## Checklist cuối

- [ ] Đúng branch/commit; `npm test`, `npm run build`, `mvn verify` pass.
- [ ] Student không thấy tạo lớp và API Student trả 403.
- [ ] Admin cấp Teacher, tạo lớp và gán đúng Teacher làm owner.
- [ ] Teacher quản lý lớp được giao, Teacher gọi Admin API nhận 403.
- [ ] Student xin vào/được duyệt, tham gia buổi, làm và nộp bài.
- [ ] Lịch sử kết nối lưu qua reload/restart và không bị xem nhầm là điểm danh.
- [ ] Kiểm tra desktop/mobile, hai browser context; nếu nghiệm thu media thì thử trên thiết bị thật.
- [ ] Ghi lại kết quả/lỗi và không chia sẻ thông tin xác thực.
