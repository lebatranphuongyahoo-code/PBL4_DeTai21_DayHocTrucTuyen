package com.pbl4.server;

import com.pbl4.common.Protocol;

/** Cấu hình khởi chạy server. Cổng = 0 nghĩa là để hệ điều hành chọn cổng trống (dùng cho test). */
public record ServerConfig(int tcpPort, int udpPort, String dbPath) {

    public static ServerConfig defaults() {
        return new ServerConfig(Protocol.TCP_PORT, Protocol.UDP_PORT, "pbl4.db");
    }

    /** Cổng ngẫu nhiên + CSDL trong RAM — chỉ dùng cho test và công cụ đo. */
    public static ServerConfig ephemeral() {
        return new ServerConfig(0, 0, ":memory:");
    }

    /** {@code [tcpPort] [udpPort] [dbPath]} — thiếu tham số nào thì lấy mặc định tham số đó. */
    public static ServerConfig fromArgs(String[] args) {
        ServerConfig d = defaults();
        int tcp = args.length > 0 ? Integer.parseInt(args[0]) : d.tcpPort();
        int udp = args.length > 1 ? Integer.parseInt(args[1]) : d.udpPort();
        String db = args.length > 2 ? args[2] : d.dbPath();
        return new ServerConfig(tcp, udp, db);
    }
}
