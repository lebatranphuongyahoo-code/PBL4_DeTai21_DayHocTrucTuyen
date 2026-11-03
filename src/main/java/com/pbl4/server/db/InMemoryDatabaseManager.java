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
 * <p>Dữ liệu mất khi tắt server. Lớp này kế thừa {@link DatabaseManager} để tiếp tục
 * dùng được ở các nơi nhận kiểu DatabaseManager.</p>
 *
 * <p>Người viết: TV1. <b>TV3 sẽ thay bằng bản SQLite thật.</b></p>
 */
public class InMemoryDatabaseManager extends DatabaseManager {

    /** username → "sha256Hex|role" */
    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();

    /** roomId → "roomName|teacherUser" */
    private final Map<Integer, String> rooms = new ConcurrentHashMap<>();

    public InMemoryDatabaseManager() {
        super(":memory:");
        // Hai tài khoản mẫu để test nhanh: mat khau la "123456"
        String hash = com.pbl4.common.Sha256.hex("123456");
        users.put("lan", hash + "|" + Protocol.ROLE_TEACHER);
        users.put("minh", hash + "|" + Protocol.ROLE_STUDENT);
        displayNames.put("lan", "lan");
        displayNames.put("minh", "minh");
    }

    @Override
    public String authenticate(String user, String sha256Hex) {
        String rec = users.get(user);
        if (rec == null) {
            return null;
        }
        int firstBar = rec.indexOf('|');
        if (firstBar < 0) {
            return null;
        }
        String storedHash = rec.substring(0, firstBar);
        if (!storedHash.equals(sha256Hex)) {
            return null;
        }
        int secondBar = rec.indexOf('|', firstBar + 1);
        if (secondBar < 0) {
            return rec.substring(firstBar + 1);
        }
        return rec.substring(firstBar + 1, secondBar);
    }

    @Override
    public boolean register(String user, String sha256Hex, String role) {
        return register(user, sha256Hex, "", "EMAIL", role);
    }

    @Override
    public boolean register(String user, String sha256Hex,
                           String contact, String contactType, String role) {
        if (!Protocol.isValidRole(role)) {
            return false;
        }
        String payload = sha256Hex + "|" + role + "|"
                + (contact == null ? "" : contact.trim()) + "|"
                + (contactType == null || contactType.isBlank() ? "EMAIL" : contactType.trim().toUpperCase());
        if (users.putIfAbsent(user, payload) != null) {
            return false;
        }
        displayNames.put(user, user);
        return true;
    }

    @Override
    public String getDisplayName(String user) {
        return users.containsKey(user) ? displayNames.getOrDefault(user, user) : null;
    }

    @Override
    public boolean updateDisplayName(String user, String displayName) {
        if (!users.containsKey(user)) {
            return false;
        }
        displayNames.put(user, displayName.trim());
        return true;
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
    public RoomInfo findRoom(int roomId) {
        String value = rooms.get(roomId);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|", 2);
        return new RoomInfo(roomId, parts[0], parts.length > 1 ? parts[1] : "", 0);
    }

    @Override
    public boolean deleteRoom(int roomId, String teacherUser) {
        String value = rooms.get(roomId);
        if (value == null) {
            return false;
        }
        String[] parts = value.split("\\|", 2);
        if (parts.length < 2 || !teacherUser.equals(parts[1])) {
            return false;
        }
        return rooms.remove(roomId, value);
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
