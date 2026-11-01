package com.pbl4.client.ui;

import com.pbl4.client.net.ServerConnection;
import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.Protocol;
import com.pbl4.common.Sha256;
import com.pbl4.common.msg.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;

/** Login and registration window for the online-classroom client. */
public class LoginUI {

    private enum PendingRequest {
        LOGIN,
        REGISTER
    }

    private final JFrame frame = new JFrame("Đăng nhập");
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JComboBox<String> roleComboBox =
            new JComboBox<>(new String[]{"Giáo viên", "Học sinh"});
    private final JButton loginButton = new JButton("Đăng nhập");
    private final JButton registerButton = new JButton("Đăng ký");
    private final JLabel messageLabel = new JLabel("Đang kết nối máy chủ...");
    private final ServerConnection connection = new ServerConnection();
    private final String serverIp;

    private PendingRequest pendingRequest;
    private boolean connected;
    private boolean handedOff;

    public LoginUI(String ip, int port) {
        serverIp = ip;
        buildWindow();
        connection.setListener(this::onPacket);
        connection.setDisconnectListener(cause -> SwingUtilities.invokeLater(() -> {
            connected = false;
            setButtonsEnabled(false);
            showMessage("Mất kết nối máy chủ: " + cause.getMessage(), true);
        }));

        loginButton.addActionListener(event -> submitLogin());
        registerButton.addActionListener(event -> submitRegistration());
        frame.setVisible(true);
        connectAsync(ip, port);
    }

    private void buildWindow() {
        JPanel fields = new JPanel(new GridLayout(3, 2, 8, 8));
        fields.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        fields.add(new JLabel("Tên đăng nhập:"));
        fields.add(usernameField);
        fields.add(new JLabel("Mật khẩu:"));
        fields.add(passwordField);
        fields.add(new JLabel("Vai trò:"));
        fields.add(roleComboBox);

        JPanel buttons = new JPanel();
        buttons.add(loginButton);
        buttons.add(registerButton);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        footer.add(messageLabel, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.SOUTH);

        frame.setLayout(new BorderLayout());
        frame.add(fields, BorderLayout.CENTER);
        frame.add(footer, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(380, 230);
        frame.setLocationRelativeTo(null);
        setButtonsEnabled(false);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                if (!handedOff) {
                    connection.close();
                }
            }
        });
    }

    private void connectAsync(String ip, int port) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                connection.connect(ip, port);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    connected = true;
                    setButtonsEnabled(true);
                    showMessage("Đã kết nối máy chủ.", false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showMessage("Không thể hoàn tất kết nối máy chủ.", true);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    showMessage("Không thể kết nối máy chủ: " + cause.getMessage(), true);
                }
            }
        }.execute();
    }

    private void submitLogin() {
        sendAuthenticationRequest(PendingRequest.LOGIN);
    }

    private void submitRegistration() {
        sendAuthenticationRequest(PendingRequest.REGISTER);
    }

    private void sendAuthenticationRequest(PendingRequest request) {
        if (!connected || pendingRequest != null) {
            return;
        }

        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        if (username.isEmpty() || password.length == 0) {
            Arrays.fill(password, '\0');
            showMessage("Vui lòng nhập tên đăng nhập và mật khẩu.", true);
            return;
        }

        String passwordHash;
        try {
            passwordHash = Sha256.sha256Hex(new String(password));
        } finally {
            Arrays.fill(password, '\0');
        }

        String role = roleComboBox.getSelectedIndex() == 0
                ? Protocol.ROLE_TEACHER
                : Protocol.ROLE_STUDENT;
        byte packetType = request == PendingRequest.LOGIN
                ? PacketType.LOGIN_REQ
                : PacketType.REGISTER_REQ;

        try {
            byte[] payload = request == PendingRequest.LOGIN
                    ? new Messages.LoginRequest(username, passwordHash).encode()
                    : new Messages.RegisterRequest(username, passwordHash, role).encode();
            pendingRequest = request;
            setButtonsEnabled(false);
            showMessage(request == PendingRequest.LOGIN ? "Đang đăng nhập..." : "Đang đăng ký...", false);
            connection.send(packetType, payload);
        } catch (IOException | RuntimeException e) {
            pendingRequest = null;
            setButtonsEnabled(connected);
            showMessage("Không gửi được yêu cầu: " + e.getMessage(), true);
        }
    }

    private void onPacket(Packet packet) {
        if (packet.getType() != PacketType.LOGIN_RES
                && packet.getType() != PacketType.REGISTER_RES) {
            return;
        }
        try {
            if (packet.getType() == PacketType.LOGIN_RES) {
                Messages.LoginResponse res = Messages.LoginResponse.decode(packet.getPayload());
                SwingUtilities.invokeLater(() ->
                        handleResponse(packet.getType(), res.ok(), res.message(), res.role()));
            } else {
                Messages.StatusResponse res = Messages.StatusResponse.decode(packet.getPayload());
                SwingUtilities.invokeLater(() ->
                        handleResponse(packet.getType(), res.ok(), res.message(), ""));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                pendingRequest = null;
                setButtonsEnabled(connected);
                showMessage("Phản hồi máy chủ không hợp lệ.", true);
            });
        }
    }

    private void handleResponse(byte type, boolean ok, String message, String role) {
        PendingRequest expected = type == PacketType.LOGIN_RES
                ? PendingRequest.LOGIN
                : PendingRequest.REGISTER;
        if (pendingRequest != expected) {
            return;
        }

        pendingRequest = null;
        setButtonsEnabled(connected);
        if (expected == PendingRequest.LOGIN) {
            if (ok) {
                handedOff = true;
                new HomeUI(connection, usernameField.getText().trim(), role, serverIp);
                frame.dispose();
            } else {
                showMessage(message, true);
            }
        } else if (ok) {
            showMessage("Đăng ký thành công. Bạn có thể đăng nhập.", false);
        } else {
            showMessage(message, true);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    private void showMessage(String message, boolean error) {
        messageLabel.setText(message);
        messageLabel.setForeground(error ? java.awt.Color.RED : java.awt.Color.DARK_GRAY);
    }

    public static void main(String[] args) {
        String ip = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.TCP_PORT;
        SwingUtilities.invokeLater(() -> new LoginUI(ip, port));
    }
}
