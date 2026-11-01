# Mốc ban đầu trước refactor (`baseline.md`)

> Đo ngày 01/11/2026 trên nhánh `refactor/chuan-hoa-cau-truc` tách từ `main = ec82a22`.

## 0.2 Đầu ra nguyên văn trước refactor

### Môi trường
```text
java version "21.0.9" 2025-10-21 LTS
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
```

### `PacketRoundTripTest`
```text
  [OK]   round-trip
round-trip OK | type=18 | payload=5 byte
   (do dai ghi tren duong truyen = 10 byte = 4 + 1 + 5)
  [OK]   payload rong -> do dai = 1
  [OK]   payload rong doc lai duoc
  [OK]   frame 62 KB nguyen ven
   frame lon: 62005 byte tren duong truyen
  [OK]   3 goi lien tiep khong dinh nhau
  [OK]   tu choi do dai rac

==> TAT CA KIEM TRA DAT (5/5)
```

### `T0Demo`
```text
########################################################
#  BAI KIEM THU MUC T0 - PBL4 De tai 21 (Day hoc truc tuyen)
#  Phan cua TV1: server da luong + phong hoc + relay
########################################################

[audio] Relay thoai dang lang nghe tren cong UDP 9001
[main] ===== SERVER DANG CHAY =====
[main] TCP  : 9000  (dieu khien + man hinh)
[main] UDP  : 9001  (chi thoai PCM)
[main] Pool : 50 luong, backlog 50
[main] Payload toi da: 5000000 byte
[main] Cho ket noi...

--- 1. Giao vien dang nhap, tao lop, vao lop ---
[pool-1-thread-1] client da ket noi tu /127.0.0.1:53165 | luong xu ly=pool-1-thread-1
[pool-1-thread-1] dang ky lan (TEACHER) -> trung ten
[pool-1-thread-1] dang nhap thanh cong: lan (TEACHER)
   [DAT]   giao vien dang nhap duoc
   [DAT]   tao lop duoc, ma lop = 838787
   [DAT]   ma lop la so 6 chu so
[pool-1-thread-1] tao lop 'Toan roi rac' ma 838787 boi lan
   [DAT]   giao vien vao lop, lop co 1 nguoi

--- 2. Hoc sinh vao lop va danh sach thanh vien ---
[pool-1-thread-2] client da ket noi tu /127.0.0.1:53166 | luong xu ly=pool-1-thread-2
[pool-1-thread-2] dang ky minh (STUDENT) -> trung ten
[pool-1-thread-2] dang nhap thanh cong: minh (STUDENT)
[pool-1-thread-2] vao lop 838787 (minh), cong UDP nhan thoai = 52915
[pool-1-thread-1] vao lop 838787 (lan), cong UDP nhan thoai = 52914
[pool-1-thread-1] phat USER_LIST cho 2 nguoi trong lop 838787
[pool-1-thread-2] phat USER_LIST cho 2 nguoi trong lop 838787
   [DAT]   hoc sinh 1 vao lop, lop co 2 nguoi
[pool-1-thread-3] client da ket noi tu /127.0.0.1:53167 | luong xu ly=pool-1-thread-3
[pool-1-thread-3] dang ky an (STUDENT) -> OK
[pool-1-thread-3] dang nhap thanh cong: an (STUDENT)
[pool-1-thread-3] vao lop 838787 (an), cong UDP nhan thoai = 52916
[pool-1-thread-3] phat USER_LIST cho 3 nguoi trong lop 838787
   [DAT]   hoc sinh 2 vao lop, lop co 3 nguoi

--- 3. Kiem tra quyen o server (khong tin client) ---
[pool-1-thread-2] TU CHOI chia se man hinh: minh co vai tro STUDENT
   [DAT]   hoc sinh bi TU CHOI chia se man hinh
   [DAT]   co chia se cua lop van = false (khong bat co sai)

--- 4. Giao vien chia se man hinh + 1 client MANG CHAM ---
[pool-1-thread-1] bat dau chia se man hinh lop 838787 (thong bao 3 nguoi)
   [DAT]   giao vien duoc phep chia se man hinh
   [DAT]   co sharing cua lop da bat
[pool-1-thread-4] client da ket noi tu /127.0.0.1:53168 | luong xu ly=pool-1-thread-4
[pool-1-thread-4] dang ky cham (STUDENT) -> OK
[pool-1-thread-4] dang nhap thanh cong: cham (STUDENT)
[pool-1-thread-4] vao lop 838787 (cham), cong UDP nhan thoai = 52917
[pool-1-thread-4] phat USER_LIST cho 4 nguoi trong lop 838787
   [DAT]   client mang cham vao lop, lop co 4 nguoi
   [DAT]   hoc sinh binh thuong nhan duoc frame (30 frame)
   [DAT]   client mang cham nhan duoc it frame hon (8 frame)
   [DAT]   server BO FRAME cua client cham (dropped=22)
   [DAT]   client binh thuong KHONG bi bo frame (dropped=0)
   [DAT]   vung gang cua lop rat ngan (27.13 micro giay)

--- 5. Chuyen tiep thoai UDP (648 byte/goi, 50 goi/giay) ---
[pool-1-thread-4] client ngat ket noi (cham@/127.0.0.1:53168)
[pool-1-thread-4] roi lop 838787 (cham)
[pool-1-thread-4] da dong ket noi | doc 3 goi | gui 8 frame | bo 22 frame
[pool-1-thread-1] bat mic: lan (UDP 127.0.0.1:52914)
[stats] luong dang song=15 | client da ket noi=4 | lop mo=1 | nguoi online=3 | goi thoai da relay=12
[stats]   lop 838787 'Toan roi rac' | 3 nguoi | sharing=true | giu khoa trung binh=26.51 us | 55 lan
[audio] da relay 100 goi | 2 nguoi nhan | 11.7s | 0.06 MB
[pool-1-thread-1] tat mic: lan
   [DAT]   hoc sinh nhan duoc goi thoai (150 goi)
   [DAT]   ti le mat goi < 5% (0%)

--- 6. Roi lop, cap nhat USER_LIST, don lop rong ---
[pool-1-thread-3] roi lop 838787 (an)
   [DAT]   sau khi 1 nguoi roi, USER_LIST con 3 nguoi
[pool-1-thread-1] dung chia se man hinh lop 838787
[pool-1-thread-3] client ngat ket noi (an@/127.0.0.1:53167)
[pool-1-thread-2] client ngat ket noi (minh@/127.0.0.1:53166)
[pool-1-thread-3] da dong ket noi | doc 4 goi | gui 30 frame | bo 0 frame
[pool-1-thread-1] client ngat ket noi (lan@/127.0.0.1:53165)
[pool-1-thread-2] roi lop 838787 (minh)
[pool-1-thread-1] roi lop 838787 (lan)

--- 7. Kiem tra don lop rong khoi bo nho ---
[pool-1-thread-2] lop 838787 khong con ai -> da xoa khoi bo nho
[pool-1-thread-1] lop 838787 khong con ai -> da xoa khoi bo nho
[pool-1-thread-2] da dong ket noi | doc 4 goi | gui 30 frame | bo 0 frame
[pool-1-thread-1] da dong ket noi | doc 38 goi | gui 30 frame | bo 0 frame
   [DAT]   khong con lop nao trong bo nho (rooms=0)
[main] Dang dung server...
[main] Server da dung binh thuong.
[audio] Relay thoai da dung.

########################################################
#  KET QUA: 20 DAT / 20 TONG
########################################################
```

### `LoadTest 10 3`
```text
########################################################
#  DO TAI MUC T3 - Thread Pool / CPU / RAM
#  PBL4 De tai 21 - phan cua TV1
########################################################
[main] ===== SERVER DANG CHAY =====
[audio] Relay thoai dang lang nghe tren cong UDP 9001
[main] TCP  : 9000  (dieu khien + man hinh)
[main] UDP  : 9001  (chi thoai PCM)
[main] Pool : 50 luong, backlog 50
[main] Payload toi da: 5000000 byte
[main] Cho ket noi...

Muc nen (chua co client nao): pool=0 writer=0 tong=10

Lop do tai: ma 466322

So KH  | pool-    | srv-writer | tong     | CPU %    | Heap MB   | Nhan xet
-------+----------+------------+----------+----------+-----------+------------------
1      | 2        | 2          | 16       | 6.9      | 12        | dung thiet ke
3      | 4        | 4          | 22       | 1.0      | 15        | dung thiet ke
5      | 6        | 6          | 28       | 0.3      | 18        | dung thiet ke
10     | 11       | 11         | 43       | 0.4      | 7         | dung thiet ke

Giu tai 3 giay...
Luc cao nhat: pool=11 (tran 50) | writer=11 (= 1 luong/ket noi)
Nguoi online tren server: 11 | lop mo: 1

Sau khi ngat het: pool=11 (truoc do 11) | writer=0 (truoc do 11) | tong=21
Lop con lai trong bo nho: 0

########## KET LUAN DO TAI ##########
   [DAT]   luong ghi cua client tra ve 0 sau khi ngat (khong ro ri luong)
   [DAT]   luong pool khong vuot tran 50
   [DAT]   so luong pool khong tang them sau khi ngat het
   [DAT]   tat ca lop da duoc don khoi bo nho

==> 4 DAT / 4 TONG
```

## 0.3 Kiểm thư mục rác

- `git ls-files .git.broken-20261027` → rỗng (chưa từng được git theo dõi; đã thêm `.git.broken-*/` vào `.gitignore` theo quyết định của Minh).
- `git ls-files pbl4.db` → rỗng (đã được ignore bởi `*.db`).

---

## Sau 1B — Thí nghiệm 4 tiến trình (`TestClient` tự đăng nhập + tạo lớp + đo độ trễ)

### Cửa sổ 2 — Giáo viên (`screen-send new 6 3`)
```text
[test] dang nhap: gv8884 (TEACHER)
ROOM_ID=388085
[test] cho 3 giay de hoc sinh vao lop...
[test] da gui 60 frame | 60.5 KB/frame | 10.0 fps | 5.0 Mbps
[test] xong.
```

### Cửa sổ 3 — Học sinh bình thường (`screen-recv 388085 9`)
```text
[test] dang nhap: hs7977 (STUDENT)
[test] da nhan 60 frame | 60.5 KB/frame | 6.4 fps | 3.72 MB
[test] do tre frame (ms): tb=20.2 | p50=16 | p95=32 | max=48 | n=60
[test] xong.
```

### Cửa sổ 4 — Học sinh mạng chậm (`screen-recv 388085 9 200`)
```text
[test] dang nhap: hs6665 (STUDENT)
[test] CHE DO MANG CHAM: doc cham 200 ms/frame
[test] da nhan 36 frame | 60.5 KB/frame | 4.0 fps | 2.23 MB
[test] do tre frame (ms): tb=1036.4 | p50=1180 | p95=1375 | max=1394 | n=36
[test] xong.
```

### Cửa sổ 1 — Server (`java -jar target/pbl4-de-tai-21-day-hoc-truc-tuyen-1.0.0.jar`)
```text
[audio] Relay thoai dang lang nghe tren cong UDP 9001
[main] ===== SERVER DANG CHAY =====
[main] TCP  : 9000  (dieu khien + man hinh)
[main] UDP  : 9001  (chi thoai PCM)
[main] Pool : 50 luong, backlog 50
[main] Payload toi da: 5000000 byte
[main] Cho ket noi...
[pool-1-thread-1] client da ket noi tu /127.0.0.1:51176 | luong xu ly=pool-1-thread-1
[pool-1-thread-1] dang ky gv8884 (TEACHER) -> OK
[pool-1-thread-1] dang nhap thanh cong: gv8884 (TEACHER)
[pool-1-thread-1] tao lop 'Thi nghiem 4.2' ma 388085 boi gv8884
[pool-1-thread-1] vao lop 388085 (gv8884), cong UDP nhan thoai = 0
[pool-1-thread-1] phat USER_LIST cho 1 nguoi trong lop 388085
[pool-1-thread-2] client da ket noi tu /127.0.0.1:51177 | luong xu ly=pool-1-thread-2
[pool-1-thread-2] dang ky hs7977 (STUDENT) -> OK
[pool-1-thread-2] dang nhap thanh cong: hs7977 (STUDENT)
[pool-1-thread-2] vao lop 388085 (hs7977), cong UDP nhan thoai = 0
[pool-1-thread-2] phat USER_LIST cho 2 nguoi trong lop 388085
[pool-1-thread-3] client da ket noi tu /127.0.0.1:51178 | luong xu ly=pool-1-thread-3
[pool-1-thread-3] dang ky hs6665 (STUDENT) -> OK
[pool-1-thread-3] dang nhap thanh cong: hs6665 (STUDENT)
[pool-1-thread-3] vao lop 388085 (hs6665), cong UDP nhan thoai = 0
[pool-1-thread-3] phat USER_LIST cho 3 nguoi trong lop 388085
[pool-1-thread-1] bat dau chia se man hinh lop 388085 (thong bao 3 nguoi)
[stats] luong dang song=9 | client da ket noi=3 | lop mo=1 | nguoi online=3 | goi thoai da relay=0
[stats]   lop 388085 'Thi nghiem 4.2' | 3 nguoi | sharing=true | giu khoa trung binh=13.58 us | 50 lan
[pool-1-thread-1] client ngat ket noi (gv8884@/127.0.0.1:51176)
[pool-1-thread-1] roi lop 388085 (gv8884)
[pool-1-thread-1] da dong ket noi | doc 65 goi | gui 60 frame | bo 0 frame
[pool-1-thread-3] client ngat ket noi (hs6665@/127.0.0.1:51178)
[pool-1-thread-3] roi lop 388085 (hs6665)
[pool-1-thread-3] da dong ket noi | doc 3 goi | gui 36 frame | bo 24 frame
[pool-1-thread-2] client ngat ket noi (hs7977@/127.0.0.1:51177)
[pool-1-thread-2] roi lop 388085 (hs7977)
[pool-1-thread-2] lop 388085 khong con ai -> da xoa khoi bo nho
[pool-1-thread-2] da dong ket noi | doc 3 goi | gui 60 frame | bo 0 frame
```


---

## Sau khi hoàn thành Bước 0–9 (01/11/2026)

Danh sách commit của nhánh `refactor/chuan-hoa-cau-truc` so với `main`:

```
96f8b94 docs: KIEN_TRUC, GIAO_THUC, cap nhat README va kich ban demo sau refactor
8e12121 refactor: xoa code chet, ScreenReceiver dung AtomicLong, them test JitterBuffer/AudioConfig [TV1 sua file cua TV2, TV3]
df9d132 fix(client): Roi phong quay ve trang chu, giu ket noi (B8) [TV1 sua file cua TV3]
9852fa6 refactor(client): AudioSession so huu socket UDP + mic + loa [TV1 sua file cua TV2, TV3]
8e8243f docs: nhat ky loi B0-B7 theo mau (buoc 6.8)
ec5b667 fix(tools): T0Demo cho don lop toi da 3s truoc khi check, chong flaky
e4949ba fix(server): kiem tra dau vao dang ky/tao lop, requireLogin day du (B7)
ccc3034 fix(server): relay UDP chi nhan tu thanh vien da bat mic (B6)
d5dd10c fix(server): chat kem ten nguoi gui (B5)
5b9f0c8 fix(server): giao vien roi lop thi tat chia se (B4)
6704406 fix(server): chi giao vien chu lop duoc bat/tat/gui man hinh (B3)
5e887ca fix(server): co closed trong Room chong phong ma khi vao/roi dong thoi (B2)
782705b fix(server): mo lai lop tu CSDL khi vao, khong mo luc tao (B1)
5faa90a refactor(server): tach ClientHandler thanh 4 handler, bo rawOutput
454b492 refactor(db): DatabaseManager thanh interface, SqliteDatabaseManager, xoa InMemory [TV1 sua file cua TV3]
69400fe refactor(client,tools): dung Messages trong UI va cong cu [TV1 sua file cua TV3]
cd63f1f refactor(server): dung Messages trong ClientHandler va Room
9b63dd7 refactor(common): them Messages - mot cho cho dinh dang payload + golden test
139a6a8 refactor(server): ServerConfig, bind cong trong constructor; tool chay nhu ham; JUnit cho T0 va LoadTest [sua file TV2 viet]
55313ed fix(tools): TestClient tu dang nhap, screen-send tao lop, do tre frame (B0)
eb38f1e build: them JUnit 5, pin plugin, Launcher cho java -jar client
d0578e6 docs: ghi moc ban dau truoc refactor
```

**Tổng: 22 commit. Kết quả kiểm tra cuối (9.1):** `mvn -o clean verify` → BUILD SUCCESS,
**59/59 test JUnit** · `T0Demo` **20/20** · `LoadTest 10 5` **4/4** ·
`buildPayload/payloadReader` chỉ còn trong `PacketUtils.java` và `Messages.java`.

**Còn lại:** smoke GUI 14 bước (9.2 — cần bấm tay, ghi vào `docs/refactor/smoke-<ngay>.md`).
**DỪNG — chờ Minh duyệt merge (Bước 10 chỉ làm khi Minh ghi "DUYỆT BƯỚC 10").**
