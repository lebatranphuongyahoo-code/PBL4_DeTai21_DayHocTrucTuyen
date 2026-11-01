package com.pbl4.server;

import com.pbl4.server.db.DatabaseManager;
import com.pbl4.server.handler.AudioHandler;
import com.pbl4.server.handler.AuthHandler;
import com.pbl4.server.handler.RoomHandler;
import com.pbl4.server.handler.ScreenHandler;

/** Các đối tượng dùng chung cho mọi kết nối. Handler không giữ trạng thái riêng của client nào. */
public record ServerContext(RoomManager rooms, DatabaseManager db,
                            AuthHandler auth, RoomHandler room,
                            ScreenHandler screen, AudioHandler audio) {

    public static ServerContext create(RoomManager rooms, DatabaseManager db) {
        RoomHandler roomHandler = new RoomHandler(rooms, db);
        return new ServerContext(rooms, db,
                new AuthHandler(db), roomHandler,
                new ScreenHandler(rooms), new AudioHandler(rooms));
    }
}
