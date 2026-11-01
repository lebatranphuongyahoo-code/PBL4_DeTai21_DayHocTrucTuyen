package com.pbl4.client.ui;

import com.pbl4.client.media.AudioSession;
import com.pbl4.client.media.ScreenCapturer;
import com.pbl4.client.media.ScreenReceiver;
import com.pbl4.client.net.ServerConnection;
import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.Protocol;
import com.pbl4.common.msg.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * User interface for a joined online classroom.
 */
public class ClassroomUI {

    private final ServerConnection connection;
    private final AudioSession audio;
    private final String username;
    private final String serverIp;
    private final int roomId;
    private final String role;
    private final JFrame frame;
    private final ScreenPanel screenPanel = new ScreenPanel();
    private final DefaultListModel<String> memberModel = new DefaultListModel<>();
    private final JList<String> memberList = new JList<>(memberModel);
    private final JButton micButton = new JButton("Bật Mic");
    private final JButton shareButton = new JButton("Chia sẻ màn hình");
    private final JButton leaveButton = new JButton("Rời phòng");
    private final JTextArea chatArea = new JTextArea(3, 22);
    private final JTextField chatInput = new JTextField(16);
    private final JLabel statusLabel = new JLabel("Đã vào lớp.");
    private volatile ScreenCapturer screenCapturer;
    private volatile boolean screenSendFailed;

    private boolean micOn;
    private boolean sharing;
    private boolean inRoom = true;
    private boolean returningHome;

    public ClassroomUI(ServerConnection connection, String username, String role,
                       int roomId, String roomName, AudioSession audio, String serverIp) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.audio = audio;
        this.username = Objects.requireNonNull(username, "username");
        this.roomId = roomId;
        this.role = Objects.requireNonNull(role, "role");
        this.serverIp = Objects.requireNonNull(serverIp, "serverIp");
        this.frame = new JFrame("Lớp " + roomName + " (" + roomId + ")");

        buildWindow();
        startAudioPlayback();
        connection.setListener(this::onPacket);
        connection.setDisconnectListener(cause -> SwingUtilities.invokeLater(() -> {
            inRoom = false;
            if (audio != null) {
                audio.close();
            }
            stopScreenCapturer();
            sharing = false;
            shareButton.setText("Chia sẻ màn hình");
            disableControls();
            showStatus("Mất kết nối máy chủ: " + cause.getMessage(), true);
        }));

        micButton.addActionListener(event -> toggleMicrophone());
        shareButton.addActionListener(event -> toggleScreenSharing());
        leaveButton.addActionListener(event -> leaveRoom());
        chatInput.addActionListener(event -> sendChat());
        frame.setVisible(true);
    }

    /**
     * Mở loa ngay khi vào lớp.
     *
     * <p>Server relay thoại cho <b>mọi</b> thành viên đã đăng ký cổng UDP, không phân biệt
     * người đó có bật mic hay không. Vì vậy nếu chỉ mở loa lúc bấm "Bật Mic" thì học sinh
     * ngồi nghe sẽ không nhận được gì — gói UDP vẫn tới cổng nhưng không có luồng nào đọc.
     * Nút mic chỉ điều khiển <i>đường gửi</i>; đường nhận luôn chạy từ lúc vào lớp.</p>
     */
    private void startAudioPlayback() {
        if (audio == null) {
            return;
        }
        try {
            audio.startPlayback();
        } catch (Exception e) {
            showStatus("Không mở được loa: " + e.getMessage(), true);
        }
    }

    private void buildWindow() {
        JPanel membersPanel = new JPanel(new BorderLayout());
        membersPanel.setBorder(BorderFactory.createTitledBorder("Thành viên"));
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        membersPanel.add(new JScrollPane(memberList), BorderLayout.CENTER);
        membersPanel.setPreferredSize(new java.awt.Dimension(230, 0));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(screenPanel, BorderLayout.CENTER);
        center.add(new JLabel("Người dùng: " + username), BorderLayout.NORTH);

        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        micButton.setEnabled(audio != null);
        controlBar.add(micButton);
        if (Protocol.ROLE_TEACHER.equals(role)) {
            controlBar.add(shareButton);
        }
        controlBar.add(leaveButton);

        JPanel chatPanel = new JPanel(new BorderLayout(4, 4));
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        chatPanel.add(chatInput, BorderLayout.SOUTH);
        controlBar.add(chatPanel);

        JPanel south = new JPanel(new BorderLayout());
        south.add(controlBar, BorderLayout.CENTER);
        south.add(statusLabel, BorderLayout.SOUTH);

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(center, BorderLayout.CENTER);
        frame.add(membersPanel, BorderLayout.EAST);
        frame.add(south, BorderLayout.SOUTH);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                sendLeaveRequest();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                ClassroomUI.this.stopScreenCapturer();
                if (ClassroomUI.this.audio != null) {
                    ClassroomUI.this.audio.close();
                }
                if (!ClassroomUI.this.returningHome) {
                    ClassroomUI.this.connection.close();
                }
            }
        });
    }

    private void toggleMicrophone() {
        if (audio == null) {
            showStatus("Chưa được kết nối với phần âm thanh của client.", true);
            return;
        }
        if (micOn) {
            audio.stopMic();
            micOn = false;
            micButton.setText("Bật Mic");
            try {
                byte[] payload = new Messages.RoomIdOnly(roomId).encode();
                connection.send(PacketType.AUDIO_OFF, payload);
                showStatus("Đã tắt mic.", false);
            } catch (IOException e) {
                showStatus("Đã dừng mic, nhưng không gửi được AUDIO_OFF: " + e.getMessage(), true);
            }
            return;
        }

        try {
            // Loa đã chạy từ lúc vào lớp; cổng UDP chính là cổng đã đăng ký trong JOIN_ROOM_REQ.
            byte[] payload = new Messages.RoomIdOnly(roomId).encode();
            connection.send(PacketType.AUDIO_ON, payload);
            audio.startMic(InetAddress.getByName(serverIp), Protocol.UDP_PORT, roomId);
            micOn = !micOn;
            micButton.setText(micOn ? "Tắt Mic" : "Bật Mic");
            showStatus("Đã bật mic.", false);
        } catch (Exception e) {
            audio.stopMic();
            try {
                byte[] offPayload = new Messages.RoomIdOnly(roomId).encode();
                connection.send(PacketType.AUDIO_OFF, offPayload);
            } catch (IOException offException) {
                e.addSuppressed(offException);
            }
            showStatus("Không thể bật mic: " + e.getMessage(), true);
        }
    }

    private void toggleScreenSharing() {
        if (!Protocol.ROLE_TEACHER.equals(role)) {
            return;
        }
        if (sharing) {
            stopScreenCapturer();
            sharing = false;
            shareButton.setText("Chia sẻ màn hình");
            sendRoomCommand("Đã dừng chia sẻ màn hình.");
            return;
        }

        ScreenCapturer capturer;
        try {
            capturer = new ScreenCapturer(this::sendScreenFrame, null);
        } catch (AWTException | RuntimeException e) {
            showStatus("Không thể khởi tạo chia sẻ màn hình: " + e.getMessage(), true);
            return;
        }

        try {
            byte[] payload = new Messages.RoomIdOnly(roomId).encode();
            connection.send(PacketType.SCREEN_START, payload);
            screenSendFailed = false;
            screenCapturer = capturer;
            capturer.start();
            sharing = true;
            shareButton.setText("Dừng chia sẻ màn hình");
            showStatus("Đang chia sẻ màn hình.", false);
        } catch (IOException | RuntimeException e) {
            capturer.stop();
            screenCapturer = null;
            try {
                byte[] stopPayload = new Messages.RoomIdOnly(roomId).encode();
                connection.send(PacketType.SCREEN_STOP, stopPayload);
            } catch (IOException stopException) {
                e.addSuppressed(stopException);
            }
            showStatus("Không thể bắt đầu chia sẻ màn hình: " + e.getMessage(), true);
        }
    }

    private void sendScreenFrame(byte[] jpeg) {
        if (screenSendFailed) {
            return;
        }
        try {
            connection.send(PacketType.SCREEN_FRAME, jpeg);
        } catch (IOException e) {
            screenSendFailed = true;
            SwingUtilities.invokeLater(() -> {
                stopScreenCapturer();
                sharing = false;
                shareButton.setText("Chia sẻ màn hình");
                showStatus("Không gửi được hình ảnh màn hình: " + e.getMessage(), true);
            });
        }
    }

    private void stopScreenCapturer() {
        ScreenCapturer current = screenCapturer;
        screenCapturer = null;
        if (current != null) {
            current.stop();
        }
    }

    private void sendRoomCommand(String status) {
        try {
            byte[] payload = new Messages.RoomIdOnly(roomId).encode();
            connection.send(PacketType.SCREEN_STOP, payload);
            showStatus(status, false);
        } catch (IOException e) {
            showStatus("Không gửi được yêu cầu: " + e.getMessage(), true);
        }
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            byte[] payload = new Messages.ChatText(roomId, text).encode();
            connection.send(PacketType.CHAT_TEXT, payload);
            chatInput.setText("");
        } catch (IOException e) {
            showStatus("Không gửi được tin nhắn: " + e.getMessage(), true);
        }
    }

    /** Nút "Rời phòng": về trang chủ nhưng GIỮ kết nối — không phải đăng nhập lại (B8). */
    private void leaveRoom() {
        sendLeaveRequest();
        inRoom = false;
        returningHome = true;
        if (audio != null) {
            audio.close();   // đóng AudioSession cũ TRƯỚC khi HomeUI mở cái mới (close lặp an toàn)
        }
        frame.dispose();     // windowClosed chạy, KHÔNG đóng kết nối
        new HomeUI(connection, username, role, serverIp);
    }

    private void sendLeaveRequest() {
        if (!inRoom) {
            return;
        }
        inRoom = false;
        try {
            if (micOn) {
                if (audio != null) {
                    audio.stopMic();
                }
                micOn = false;
                byte[] audioOffPayload = new Messages.RoomIdOnly(roomId).encode();
                connection.send(PacketType.AUDIO_OFF, audioOffPayload);
            }
            if (sharing) {
                stopScreenCapturer();
                sharing = false;
                sendRoomCommand("Đang rời phòng...");
            }
            byte[] payload = new Messages.RoomIdOnly(roomId).encode();
            connection.send(PacketType.LEAVE_ROOM_REQ, payload);
        } catch (IOException e) {
            showStatus("Không gửi được yêu cầu rời phòng: " + e.getMessage(), true);
        }
    }

    private void onPacket(Packet packet) {
        try {
            switch (packet.getType()) {
                case PacketType.USER_LIST -> {
                    Messages.UserList userList = Messages.UserList.decode(packet.getPayload());
                    List<String> members = new ArrayList<>(userList.members().size());
                    for (Messages.Member m : userList.members()) {
                        members.add(m.username() + " — " + m.role());
                    }
                    SwingUtilities.invokeLater(() -> displayMembers(members));
                }
                case PacketType.SCREEN_FRAME -> {
                    BufferedImage image = ScreenReceiver.decode(packet.getPayload());
                    if (image != null) {
                        SwingUtilities.invokeLater(() -> screenPanel.setImage(image));
                    }
                }
                case PacketType.SCREEN_START -> {
                    String teacher = Messages.ScreenNotice.decode(packet.getPayload()).text();
                    SwingUtilities.invokeLater(() ->
                            showStatus(teacher + " đang chia sẻ màn hình.", false));
                }
                case PacketType.SCREEN_STOP -> {
                    String message = Messages.ScreenNotice.decode(packet.getPayload()).text();
                    SwingUtilities.invokeLater(() -> {
                        stopScreenCapturer();
                        sharing = false;
                        shareButton.setText("Chia sẻ màn hình");
                        screenPanel.setImage(null);
                        showStatus(message, false);
                    });
                }
                case PacketType.CHAT_TEXT -> {
                    Messages.ChatText chat = Messages.ChatText.decode(packet.getPayload());
                    if (chat.roomId() == roomId) {
                        SwingUtilities.invokeLater(() -> appendChat(chat.text()));
                    }
                }
                case PacketType.ERROR -> {
                    String message = Messages.ErrorMessage.decode(packet.getPayload()).message();
                    SwingUtilities.invokeLater(() -> {
                        if (sharing && message.toLowerCase(java.util.Locale.ROOT)
                                .contains("chi giao vien")) {
                            stopScreenCapturer();
                            sharing = false;
                            shareButton.setText("Chia sẻ màn hình");
                        }
                        showStatus(message, true);
                    });
                }
                default -> {
                    // Ignore packets not used by this screen.
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> showStatus("Phản hồi máy chủ không hợp lệ.", true));
        }
    }

    void acceptPacket(Packet packet) {
        onPacket(packet);
    }

    private void displayMembers(List<String> members) {
        memberModel.clear();
        for (String member : members) {
            memberModel.addElement(member);
        }
    }

    private void appendChat(String message) {
        chatArea.append(message + System.lineSeparator());
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void disableControls() {
        micButton.setEnabled(false);
        shareButton.setEnabled(false);
        leaveButton.setEnabled(false);
        chatInput.setEnabled(false);
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? Color.RED : Color.DARK_GRAY);
    }
}
