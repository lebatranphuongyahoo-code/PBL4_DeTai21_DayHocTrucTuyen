package com.pbl4.server.handler;

import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.Protocol;
import com.pbl4.common.msg.Messages;
import com.pbl4.server.ClientHandler;
import com.pbl4.server.db.DatabaseManager;

import java.io.IOException;
import java.util.regex.Pattern;

/** Xử lý đăng nhập ({@code LOGIN_REQ}) và đăng ký ({@code REGISTER_REQ}). */
public final class AuthHandler {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{2,32}");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final DatabaseManager db;

    public AuthHandler(DatabaseManager db) {
        this.db = db;
    }

    public void handleLogin(ClientHandler c, Packet packet) throws IOException {
        Messages.LoginRequest req = Messages.LoginRequest.decode(packet.getPayload());
        String user = req.username();
        String hash = req.passwordHash();

        String foundRole = db.authenticate(user, hash);
        if (foundRole == null) {
            c.sendControl(PacketType.LOGIN_RES,
                    new Messages.LoginResponse(false, "Sai ten dang nhap hoac mat khau", "").encode());
            c.log("dang nhap that bai: " + user);
            return;
        }

        c.markLoggedIn(user, foundRole);
        c.sendControl(PacketType.LOGIN_RES,
                new Messages.LoginResponse(true, "Dang nhap thanh cong", foundRole).encode());
        c.log("dang nhap thanh cong: " + user + " (" + foundRole + ")");
    }

    public void handleRegister(ClientHandler c, Packet packet) throws IOException {
        Messages.RegisterRequest req = Messages.RegisterRequest.decode(packet.getPayload());
        String user = req.username();
        String hash = req.passwordHash();
        String requestedRole = req.role();

        // Không tin client: role phải là một trong hai giá trị hợp lệ.
        if (!Protocol.isValidRole(requestedRole)) {
            c.sendControl(PacketType.REGISTER_RES,
                    new Messages.StatusResponse(false, "Vai tro khong hop le").encode());
            return;
        }
        if (user == null || !USERNAME.matcher(user).matches()) {
            c.sendControl(PacketType.REGISTER_RES,
                    new Messages.StatusResponse(false, "Ten dang nhap 2-32 ky tu: chu, so, _ hoac .").encode());
            return;
        }
        if (hash == null || !SHA256_HEX.matcher(hash).matches()) {
            c.sendControl(PacketType.REGISTER_RES,
                    new Messages.StatusResponse(false, "Mat khau khong hop le").encode());
            return;
        }

        boolean ok = db.register(user, hash, requestedRole);
        c.sendControl(PacketType.REGISTER_RES,
                new Messages.StatusResponse(ok, ok ? "Dang ky thanh cong" : "Ten dang nhap da ton tai").encode());
        c.log("dang ky " + user + " (" + requestedRole + ") -> " + (ok ? "OK" : "trung ten"));
    }
}
