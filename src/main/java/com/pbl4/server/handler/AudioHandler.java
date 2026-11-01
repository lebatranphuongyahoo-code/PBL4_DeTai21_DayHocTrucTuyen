package com.pbl4.server.handler;

import com.pbl4.server.ClientHandler;
import com.pbl4.server.Room;
import com.pbl4.server.RoomManager;

/** Xử lý bật/tắt mic ({@code AUDIO_ON}, {@code AUDIO_OFF}). */
public final class AudioHandler {

    private final RoomManager rooms;

    public AudioHandler(RoomManager rooms) {
        this.rooms = rooms;
    }

    public void handleAudioOn(ClientHandler c) {
        if (!requireLogin(c)) {
            return;
        }
        Room room = currentRoom(c);
        if (room == null) {
            c.sendError("Ban chua o trong lop nao");
            return;
        }
        if (!c.hasUdpAddress()) {
            c.sendError("Ban chua bao cong UDP khi vao lop");
            return;
        }
        c.setMicOn(true);
        c.log("bat mic: " + c.getUsername() + " (UDP "
                + c.getUdpAddress().getHostAddress() + ":" + c.getUdpPort() + ")");
    }

    public void handleAudioOff(ClientHandler c) {
        if (!requireLogin(c)) {
            return;
        }
        c.setMicOn(false);
        c.log("tat mic: " + c.getUsername());
    }

    private Room currentRoom(ClientHandler c) {
        int current = c.getRoomId();
        return (current < 0) ? null : rooms.get(current);
    }

    private static boolean requireLogin(ClientHandler c) {
        if (!c.isLoggedIn()) {
            c.sendError("Ban chua dang nhap");
            return false;
        }
        return true;
    }
}
