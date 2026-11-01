package com.pbl4;

import com.pbl4.client.ui.LoginUI;
import com.pbl4.server.ServerMain;
import java.util.Arrays;

/**
 * Diem vao chung cua file JAR dong goi (Main-Class trong MANIFEST.MF).
 *
 * <pre>
 *   java -jar pbl4.jar                        -> chay server (mac dinh, giu I11)
 *   java -jar pbl4.jar server [tcp] [udp] [db] -> chay server voi tham so
 *   java -jar pbl4.jar client [ip] [port]     -> chay giao dien client
 * </pre>
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            ServerMain.main(args);
            return;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "server" -> ServerMain.main(rest);
            case "client" -> LoginUI.main(rest);
            default -> {
                System.out.println("Dung: java -jar <jar> [server [tcp] [udp] [db] | client [ip] [port]]");
                System.exit(2);
            }
        }
    }
}
