# GIAO THỨC — PBL4 Đề tài 21

> Sinh và đối chiếu 100% từ `src/main/java/com/pbl4/common/PacketType.java` +
> `src/main/java/com/pbl4/common/msg/Messages.java`. **Đổi giao thức phải báo cả nhóm** —
> sai một mã byte là server im lặng từ chối, rất khó tìm.

## Cổng

| Cổng | Giao thức | Dùng cho |
|---|---|---|
| **9000** | TCP | Đăng nhập, lớp học, chat chữ, frame màn hình |
| **9001** | UDP | **Chỉ** gói thoại PCM (relay) |
| ngẫu nhiên | UDP | Cổng client bind để **nhận** thoại — báo trong `JOIN_ROOM_REQ` |

## Khung gói TCP

```
+----------------+-------------+------------------------------+
| 4 byte độ dài  | 1 byte TYPE | N byte payload               |
| = 1 + N        |             |                              |
+----------------+-------------+------------------------------+
```

Ghi bằng `DataOutputStream`, đọc bằng `DataInputStream.readFully()` **đúng N byte**
(`common/PacketUtils.encode / sendPacket / readPacket`). Payload tối đa
`Protocol.MAX_PAYLOAD = 5_000_000` byte.

## Gói UDP thoại

```
+----------------+----------------+--------------------------------+
| 4 byte roomId  | 4 byte seqNo   | 640 byte PCM 16kHz/16bit/mono  |  = 648 byte
+----------------+----------------+--------------------------------+
```

- Big-endian cho roomId và seqNo; PCM là **little-endian** (đúng thứ tự byte mà
  `javax.sound.sampled` xuất).
- 648 byte nằm gọn trong MTU 1500 ⇒ không bị phân mảnh IP.
- `seqNo` **không dùng để phát** — chỉ để đo mất gói / sai thứ tự ở máy nhận.
- Server **không gửi lại gói cho người gửi** (chống vọng) và **chỉ nhận gói từ thành viên
  đang bật mic** (B6).

## Bảng 19 loại gói

| Mã | Tên | Chiều | Record trong `Messages` | Payload |
|---|---|---|---|---|
| `0x01` | `LOGIN_REQ` | C→S | `LoginRequest` | UTF username, UTF sha256 |
| `0x02` | `LOGIN_RES` | S→C | `LoginResponse` | byte ok, UTF msg, UTF role |
| `0x03` | `REGISTER_REQ` | C→S | `RegisterRequest` | UTF username, UTF sha256, UTF role |
| `0x04` | `REGISTER_RES` | S→C | `StatusResponse` | byte ok, UTF msg |
| `0x05` | `CREATE_ROOM_REQ` | C→S | `CreateRoomRequest` | UTF roomName |
| `0x06` | `CREATE_ROOM_RES` | S→C | `CreateRoomResponse` | byte ok, UTF msg, int roomId |
| `0x07` | `JOIN_ROOM_REQ` | C→S | `JoinRoomRequest` | int roomId, int udpPort |
| `0x08` | `JOIN_ROOM_RES` | S→C | `StatusResponse` | byte ok, UTF msg |
| `0x09` | `LEAVE_ROOM_REQ` | C→S | `RoomIdOnly` | int roomId |
| `0x0A` | `ROOM_LIST_REQ` | C→S | — (rỗng) | |
| `0x0B` | `ROOM_LIST_RES` | S→C | `RoomList` | int n, rồi n × (int roomId, UTF name, UTF teacher, int soNguoi) |
| `0x0C` | `USER_LIST` | S→C | `UserList` (`Member`) | int n, rồi n × (UTF username, UTF role) |
| `0x0D` | `CHAT_TEXT` | 2 chiều | `ChatText` | int roomId, UTF text (server gắn `"nguoiGui: "` vào text — B5) |
| `0x10` | `SCREEN_START` | C→S→C | `RoomIdOnly` gửi / `ScreenNotice` nhận | server kiểm role + chủ lớp (B3); nhận: int roomId, UTF text |
| `0x11` | `SCREEN_STOP` | C→S→C | `RoomIdOnly` gửi / `ScreenNotice` nhận | như trên |
| `0x12` | `SCREEN_FRAME` | C→S→C | — | byte JPEG thô, server relay nguyên payload |
| `0x13` | `AUDIO_ON` | C→S | `RoomIdOnly` | int roomId (client phải đã báo cổng UDP khi join) |
| `0x14` | `AUDIO_OFF` | C→S | `RoomIdOnly` | int roomId |
| `0x7F` | `ERROR` | S→C | `ErrorMessage` | UTF message |

## Quy tắc payload

- Mọi record đều có `encode()` / `decode(byte[])` — **không ai được tự xây payload bằng tay**
  (`buildPayload` / `payloadReader` chỉ tồn tại trong `PacketUtils` và `Messages`).
- Chuỗi ghi bằng `writeUTF` (độ dài 2 byte + UTF-8), giới hạn 65 535 byte.
- Danh sách giới hạn `Messages.MAX_LIST_SIZE = 100_000` phần tử — chặn gói rác cấp phát mảng khổng lồ.
- Test chốt hợp đồng: `common/PacketUtilsTest` (7 test) và `common/msg/MessagesTest`
  (golden + round-trip, 15 test).
