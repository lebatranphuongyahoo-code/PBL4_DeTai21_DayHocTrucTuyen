package com.pbl4.server.db;

import com.pbl4.common.Protocol;
import com.pbl4.common.RoomInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bản cài đặt TẠM, giữ dữ liệu trong RAM — <b>chỉ dùng để TV1 chạy thử mức T0</b>
 * khi TV3 chưa xong SQLite.
 *
 * <p>Dữ liệu mất khi tắt server. Khi TV3 hoàn thành {@code SqliteDatabaseManager},
 * đổi một dòng trong {@link com.pbl4.server.ServerMain} là xong — không phải sửa
 * bất kỳ chỗ nào khác, vì cả hai cùng cài đặt {@link DatabaseManager}.</p>
 *
 * <p>Người viết: TV1. <b>TV3 sẽ thay bằng bản SQLite thật.</b></p>
 */
public class InMemoryDatabaseManager implements DatabaseManager {

    /** username → "sha256Hex|role" */
    private final Map<String, String> users = new ConcurrentHashMap<>();

    /** roomId → "roomName|teacherUser" */
    private final Map<Integer, String> rooms = new ConcurrentHashMap<>();

    public InMemoryDatabaseManager() {
        // Hai tài khoản mẫu để test nhanh: mat khau la "123456"
        String hash = com.pbl4.common.Sha256.hex("123456");
        users.put("lan", hash + "|" + Protocol.ROLE_TEACHER);
        users.put("minh", hash + "|" + Protocol.ROLE_STUDENT);
    }

    @Override
    public String authenticate(String user, String sha256Hex) {
        String rec = users.get(user);
        if (rec == null) {
            return null;
        }
        int bar = rec.indexOf('|');
        String storedHash = rec.substring(0, bar);
        return storedHash.equals(sha256Hex) ? rec.substring(bar + 1) : null;
    }

    @Override
    public boolean register(String user, String sha256Hex, String role) {
        if (!Protocol.isValidRole(role)) {
            return false;
        }
        return users.putIfAbsent(user, sha256Hex + "|" + role) == null;
    }

    @Override
    public int createRoom(String name, String teacherUser) {
        // Cấp mã phòng 6 chữ số chưa dùng. Bản SQLite thật sẽ dùng UNIQUE + vòng lặp tương tự.
        for (int attempt = 0; attempt < 100; attempt++) {
            int id = ThreadLocalRandom.current()
                    .nextInt(Protocol.ROOM_ID_MIN, Protocol.ROOM_ID_MAX + 1);
            if (rooms.putIfAbsent(id, name + "|" + teacherUser) == null) {
                return id;
            }
        }
        throw new IllegalStateException("Khong cap duoc roomId moi");
    }

    @Override
    public List<RoomInfo> listRooms() {
        List<RoomInfo> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : rooms.entrySet()) {
            String[] parts = e.getValue().split("\\|", 2);
            // memberCount = 0 ở đây; TV1 sẽ điền số người online thật khi đóng gói ROOM_LIST_RES.
            out.add(new RoomInfo(e.getKey(), parts[0], parts.length > 1 ? parts[1] : "", 0));
        }
        return out;
    }
}
