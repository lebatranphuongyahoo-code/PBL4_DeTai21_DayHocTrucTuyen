package com.pbl4.client.ui;

import com.pbl4.client.net.ServerConnection;
import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;
import com.pbl4.common.Sha256;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Login and registration window for the online-classroom client. */
public class LoginUI {

    private enum PendingRequest {
        LOGIN,
        REGISTER
    }

    private final JFrame frame = new JFrame("Đăng nhập");
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JButton loginButton = new JButton("Đăng nhập");
    private final JButton registerButton = new JButton("Đăng ký");
    private final JLabel messageLabel = new JLabel("Đang kết nối máy chủ...");
    private final ServerConnection connection = new ServerConnection();
    private final String serverIp;

    private PendingRequest pendingRequest;
    private boolean connected;
    private boolean handedOff;
    private RegisterUI registerDialog;

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
        registerButton.addActionListener(event -> openRegisterScreen());
        frame.setVisible(true);
        connectAsync(ip, port);
    }

    private void buildWindow() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(240, 244, 255));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Đăng nhập hệ thống");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(24, 69, 120));
        title.setHorizontalAlignment(JLabel.CENTER);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        fields.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(186, 208, 255), 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        fields.setBackground(new Color(255, 255, 255));

        JLabel userLabel = new JLabel("Tên đăng nhập:");
        userLabel.setForeground(new Color(41, 59, 82));
        JLabel passLabel = new JLabel("Mật khẩu:");
        passLabel.setForeground(new Color(41, 59, 82));
        styleInput(usernameField);
        styleInput(passwordField);

        addFieldRow(fields, 0, userLabel, usernameField);
        addFieldRow(fields, 1, passLabel, passwordField);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        styleButton(loginButton, new Color(54, 117, 255), new Color(255, 255, 255));
        styleButton(registerButton, new Color(61, 160, 113), new Color(255, 255, 255));
        buttons.add(loginButton);
        buttons.add(registerButton);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        messageLabel.setForeground(new Color(68, 83, 103));
        messageLabel.setHorizontalAlignment(JLabel.CENTER);
        footer.add(messageLabel, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.SOUTH);

        root.add(title, BorderLayout.NORTH);
        root.add(fields, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(240, 244, 255));
        frame.add(root, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(520, 360);
        frame.setMinimumSize(new Dimension(460, 340));
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

    private void addFieldRow(JPanel panel, int row, JLabel label, java.awt.Component field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.LINE_END;
        labelConstraints.insets = new Insets(8, 0, 8, 10);
        labelConstraints.weightx = 0.35;
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 0.65;
        fieldConstraints.insets = new Insets(8, 0, 8, 0);
        panel.add(field, fieldConstraints);
    }

    private void styleInput(JTextField field) {
        field.setBackground(new Color(249, 251, 255));
        field.setForeground(new Color(34, 42, 53));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(196, 210, 234), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        field.setPreferredSize(new Dimension(220, 34));
        field.setMinimumSize(new Dimension(140, 34));
    }

    private void styleInput(JPasswordField field) {
        field.setBackground(new Color(249, 251, 255));
        field.setForeground(new Color(34, 42, 53));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(196, 210, 234), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        field.setPreferredSize(new Dimension(220, 34));
        field.setMinimumSize(new Dimension(140, 34));
    }

    private void styleButton(JButton button, Color bg, Color textColor) {
        button.setFocusPainted(false);
        button.setBackground(bg);
        button.setForeground(textColor);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
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
        sendAuthenticationRequest();
    }

    private void openRegisterScreen() {
        if (registerDialog != null) {
            registerDialog.close();
        }
        registerDialog = new RegisterUI(this);
    }

    public void submitRegistrationFromDialog(String username, String passwordHash,
                                            String contact, String contactType, String role) {
        if (!connected || pendingRequest != null) {
            showMessage("Hệ thống đang xử lý yêu cầu trước đó.", true);
            return;
        }

        sendRegistrationRequest(username, passwordHash, contact, contactType, role);
    }

    private void sendAuthenticationRequest() {
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

        try {
            byte[] payload = PacketUtils.buildPayload(output -> {
                output.writeUTF(username);
                output.writeUTF(passwordHash);
            });
            pendingRequest = PendingRequest.LOGIN;
            setButtonsEnabled(false);
            showMessage("Đang đăng nhập...", false);
            connection.send(PacketType.LOGIN_REQ, payload);
        } catch (IOException | RuntimeException e) {
            pendingRequest = null;
            setButtonsEnabled(connected);
            showMessage("Không gửi được yêu cầu: " + e.getMessage(), true);
        }
    }

    private void sendRegistrationRequest(String username, String passwordHash,
                                         String contact, String contactType, String role) {
        try {
            byte[] payload = PacketUtils.buildPayload(output -> {
                output.writeUTF(username);
                output.writeUTF(passwordHash);
                output.writeUTF(contact);
                output.writeUTF(contactType);
                output.writeUTF(role);
            });
            pendingRequest = PendingRequest.REGISTER;
            setButtonsEnabled(false);
            showMessage("Đang đăng ký tài khoản...", false);
            connection.send(PacketType.REGISTER_REQ, payload);
        } catch (IOException | RuntimeException e) {
            pendingRequest = null;
            setButtonsEnabled(connected);
            showMessage("Không gửi được yêu cầu đăng ký: " + e.getMessage(), true);
        }
    }

    private void onPacket(Packet packet) {
        if (packet.getType() != PacketType.LOGIN_RES
                && packet.getType() != PacketType.REGISTER_RES) {
            return;
        }
        try {
            try (DataInputStream response = PacketUtils.payloadReader(packet.getPayload())) {
                int ok = response.readUnsignedByte();
                String message = response.readUTF();
                String role = packet.getType() == PacketType.LOGIN_RES ? response.readUTF() : "";
                String displayName = packet.getType() == PacketType.LOGIN_RES && response.available() > 0
                        ? response.readUTF()
                        : usernameField.getText().trim();
                SwingUtilities.invokeLater(() ->
                        handleResponse(packet.getType(), ok, message, role, displayName));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                pendingRequest = null;
                setButtonsEnabled(connected);
                showMessage("Phản hồi máy chủ không hợp lệ.", true);
            });
        }
    }

    private void handleResponse(byte type, int ok, String message, String role, String displayName) {
        PendingRequest expected = type == PacketType.LOGIN_RES
                ? PendingRequest.LOGIN
                : PendingRequest.REGISTER;
        if (pendingRequest != expected) {
            return;
        }

        pendingRequest = null;
        setButtonsEnabled(connected);
        if (expected == PendingRequest.LOGIN) {
            if (ok == 1) {
                handedOff = true;
                new HomeUI(connection, usernameField.getText().trim(), displayName, role, serverIp);
                frame.dispose();
            } else {
                showMessage(message, true);
            }
        } else if (ok == 1) {
            if (registerDialog != null) {
                registerDialog.close();
                registerDialog = null;
            }
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
