package com.pbl4.client.ui;

import com.pbl4.client.media.AudioPlayer;
import com.pbl4.client.media.AudioRecorder;
import com.pbl4.client.media.JitterBuffer;
import com.pbl4.client.media.MediaStats;
import com.pbl4.client.media.ScreenCapturer;
import com.pbl4.client.media.ScreenReceiver;
import com.pbl4.client.net.ServerConnection;
import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;

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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * User interface for a joined online classroom.
 */
public class ClassroomUI {

    /**
     * Adapter implemented by the audio module; implementations live with TV2's audio code.
     */
    public interface AudioPlayerControl {
        int start() throws Exception;

        void stop();
    }

    /**
     * Adapter implemented by the audio module; implementations live with TV2's audio code.
     */
    public interface AudioRecorderControl {
        void start(String serverIp, int serverPort, int roomId) throws Exception;

        void stop();
    }

    private final ServerConnection connection;
    private final DatagramSocket audioSocket;
    private final AudioPlayerControl audioPlayer;
    private final AudioRecorderControl audioRecorder;
    private final String serverIp;
    private final int roomId;
    private final String role;
    private final Runnable returnToHomeAction;
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
    private boolean returningToHome;

    public ClassroomUI(ServerConnection connection, String username, String role,
                       int roomId, String roomName, DatagramSocket audioSocket, String serverIp) {
        this(connection, username, role, roomId, roomName, audioSocket, serverIp,
                createAudioPlayer(audioSocket), createAudioRecorder(audioSocket), username, null);
    }

    public ClassroomUI(ServerConnection connection, String username, String role,
                       int roomId, String roomName, DatagramSocket audioSocket, String serverIp,
                       Runnable returnToHomeAction) {
        this(connection, username, role, roomId, roomName, audioSocket, serverIp,
                createAudioPlayer(audioSocket), createAudioRecorder(audioSocket),
                username, returnToHomeAction);
    }

    public ClassroomUI(ServerConnection connection, String username, String role,
                       int roomId, String roomName, DatagramSocket audioSocket, String serverIp,
                       String displayName, Runnable returnToHomeAction) {
        this(connection, username, role, roomId, roomName, audioSocket, serverIp,
                createAudioPlayer(audioSocket), createAudioRecorder(audioSocket),
                displayName, returnToHomeAction);
    }

    public ClassroomUI(ServerConnection connection, String username, String role,
                       int roomId, String roomName, DatagramSocket audioSocket, String serverIp,
                       AudioPlayerControl audioPlayer, AudioRecorderControl audioRecorder) {
        this(connection, username, role, roomId, roomName, audioSocket, serverIp,
                audioPlayer, audioRecorder, username, null);
    }

    private ClassroomUI(ServerConnection connection, String username, String role,
                        int roomId, String roomName, DatagramSocket audioSocket, String serverIp,
                        AudioPlayerControl audioPlayer, AudioRecorderControl audioRecorder,
                        String displayName, Runnable returnToHomeAction) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.audioSocket = audioSocket;
        this.roomId = roomId;
        this.role = Objects.requireNonNull(role, "role");
        this.serverIp = Objects.requireNonNull(serverIp, "serverIp");
        this.returnToHomeAction = returnToHomeAction;
        this.audioPlayer = audioPlayer;
        this.audioRecorder = audioRecorder;
        this.frame = new JFrame("Lớp " + roomName + " (" + roomId + ")");

        buildWindow(displayName);
        connection.setListener(this::onPacket);
        connection.setDisconnectListener(cause -> SwingUtilities.invokeLater(() -> {
            inRoom = false;
            stopAudioDevices();
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

    private static AudioPlayerControl createAudioPlayer(DatagramSocket socket) {
        if (socket == null) {
            return null;
        }
        AudioPlayer player = new AudioPlayer(socket, new JitterBuffer(), new MediaStats());
        return new AudioPlayerControl() {
            @Override
            public int start() throws Exception {
                player.start();
                return socket.getLocalPort();
            }

            @Override
            public void stop() {
                player.stop();
            }
        };
    }

    private static AudioRecorderControl createAudioRecorder(DatagramSocket socket) {
        if (socket == null) {
            return null;
        }
        return new AudioRecorderControl() {
            private AudioRecorder recorder;

            @Override
            public void start(String serverIp, int serverPort, int roomId) throws Exception {
                if (recorder == null) {
                    recorder = new AudioRecorder(socket, InetAddress.getByName(serverIp), serverPort);
                }
                recorder.start(roomId);
            }

            @Override
            public void stop() {
                if (recorder != null) {
                    recorder.stop();
                }
            }
        };
    }

    private void buildWindow(String username) {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(240, 244, 255));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel membersPanel = new JPanel(new BorderLayout(8, 8));
        membersPanel.setBackground(new Color(248, 250, 255));
        membersPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(186, 208, 255), 1),
                "Thành viên"));
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        memberList.setBackground(new Color(250, 252, 255));
        memberList.setForeground(new Color(35, 46, 60));
        memberList.setSelectionBackground(new Color(176, 210, 255));
        memberList.setSelectionForeground(new Color(16, 31, 52));
        membersPanel.add(new JScrollPane(memberList), BorderLayout.CENTER);
        membersPanel.setPreferredSize(new Dimension(230, 0));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setBackground(new Color(240, 244, 255));
        JLabel userBadge = new JLabel("Người dùng: " + username);
        userBadge.setFont(new Font("SansSerif", Font.BOLD, 14));
        userBadge.setForeground(new Color(24, 69, 120));
        userBadge.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 0));
        center.add(screenPanel, BorderLayout.CENTER);
        center.add(userBadge, BorderLayout.NORTH);

        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        controlBar.setOpaque(false);
        micButton.setEnabled(audioPlayer != null && audioRecorder != null);
        styleButton(micButton, new Color(54, 117, 255), Color.WHITE);
        if (Protocol.ROLE_TEACHER.equals(role)) {
            styleButton(shareButton, new Color(101, 71, 192), Color.WHITE);
            controlBar.add(shareButton);
        }
        styleButton(leaveButton, new Color(201, 76, 76), Color.WHITE);
        controlBar.add(micButton);
        controlBar.add(leaveButton);

        JPanel chatPanel = new JPanel(new BorderLayout(6, 6));
        chatPanel.setOpaque(false);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(new Color(249, 252, 255));
        chatArea.setBorder(BorderFactory.createLineBorder(new Color(196, 210, 234), 1, true));
        chatArea.setForeground(new Color(35, 46, 60));
        chatInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(196, 210, 234), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        chatInput.setBackground(new Color(249, 252, 255));
        chatInput.setPreferredSize(new Dimension(0, 36));
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        chatPanel.add(chatInput, BorderLayout.SOUTH);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setOpaque(false);
        south.add(controlBar, BorderLayout.NORTH);
        south.add(chatPanel, BorderLayout.CENTER);
        statusLabel.setForeground(new Color(68, 83, 103));
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 4, 0, 0));
        south.add(statusLabel, BorderLayout.SOUTH);

        frame.setLayout(new BorderLayout(8, 8));
        frame.getContentPane().setBackground(new Color(240, 244, 255));
        frame.add(root, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        root.add(membersPanel, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        frame.setSize(1100, 700);
        frame.setMinimumSize(new Dimension(760, 520));
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                sendLeaveRequest();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                ClassroomUI.this.stopAudioDevices();
                ClassroomUI.this.stopScreenCapturer();
                if (ClassroomUI.this.audioSocket != null) {
                    ClassroomUI.this.audioSocket.close();
                }
                if (returningToHome && returnToHomeAction != null) {
                    returnToHomeAction.run();
                } else {
                    ClassroomUI.this.connection.close();
                }
            }
        });
    }

    private void styleButton(JButton button, Color bg, Color textColor) {
        button.setFocusPainted(false);
        button.setBackground(bg);
        button.setForeground(textColor);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    }

    private void toggleMicrophone() {
        if (audioPlayer == null || audioRecorder == null) {
            showStatus("Chưa được kết nối với phần âm thanh của client.", true);
            return;
        }
        if (micOn) {
            audioRecorder.stop();
            audioPlayer.stop();
            micOn = false;
            micButton.setText("Bật Mic");
            try {
                byte[] payload = PacketUtils.buildPayload(output -> output.writeInt(roomId));
                connection.send(PacketType.AUDIO_OFF, payload);
                showStatus("Đã tắt mic.", false);
            } catch (IOException e) {
                showStatus("Đã dừng mic, nhưng không gửi được AUDIO_OFF: " + e.getMessage(), true);
            }
            return;
        }

        try {
            int udpPort = audioPlayer.start();
            byte[] payload = PacketUtils.buildPayload(output -> {
                output.writeInt(roomId);
                output.writeInt(udpPort);
            });
            connection.send(PacketType.AUDIO_ON, payload);
            audioRecorder.start(serverIp, Protocol.UDP_PORT, roomId);
            micOn = !micOn;
            micButton.setText(micOn ? "Tắt Mic" : "Bật Mic");
            showStatus("Đã bật mic.", false);
        } catch (Exception e) {
            audioRecorder.stop();
            audioPlayer.stop();
            try {
                byte[] offPayload = PacketUtils.buildPayload(output -> output.writeInt(roomId));
                connection.send(PacketType.AUDIO_OFF, offPayload);
            } catch (IOException offException) {
                e.addSuppressed(offException);
            }
            showStatus("Không thể bật mic: " + e.getMessage(), true);
        }
    }

    private void stopAudioDevices() {
        if (audioRecorder != null) {
            audioRecorder.stop();
        }
        if (audioPlayer != null) {
            audioPlayer.stop();
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
            byte[] payload = PacketUtils.buildPayload(output -> output.writeInt(roomId));
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
                byte[] stopPayload = PacketUtils.buildPayload(output -> output.writeInt(roomId));
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
            byte[] payload = PacketUtils.buildPayload(output -> output.writeInt(roomId));
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
            byte[] payload = PacketUtils.buildPayload(output -> {
                output.writeInt(roomId);
                output.writeUTF(text);
            });
            connection.send(PacketType.CHAT_TEXT, payload);
            chatInput.setText("");
        } catch (IOException e) {
            showStatus("Không gửi được tin nhắn: " + e.getMessage(), true);
        }
    }

    private void leaveRoom() {
        sendLeaveRequest();
        inRoom = false;
        returningToHome = true;
        frame.dispose();
    }

    private void sendLeaveRequest() {
        if (!inRoom) {
            return;
        }
        inRoom = false;
        try {
            if (micOn) {
                audioRecorder.stop();
                audioPlayer.stop();
                micOn = false;
                byte[] audioOffPayload =
                        PacketUtils.buildPayload(output -> output.writeInt(roomId));
                connection.send(PacketType.AUDIO_OFF, audioOffPayload);
            }
            if (sharing) {
                stopScreenCapturer();
                sharing = false;
                sendRoomCommand("Đang rời phòng...");
            }
            byte[] payload = PacketUtils.buildPayload(output -> output.writeInt(roomId));
            connection.send(PacketType.LEAVE_ROOM_REQ, payload);
        } catch (IOException e) {
            showStatus("Không gửi được yêu cầu rời phòng: " + e.getMessage(), true);
        }
    }

    private void onPacket(Packet packet) {
        try {
            switch (packet.getType()) {
                case PacketType.USER_LIST -> {
                    List<String> members = readMembers(packet);
                    SwingUtilities.invokeLater(() -> displayMembers(members));
                }
                case PacketType.SCREEN_FRAME -> {
                    BufferedImage image = ScreenReceiver.decode(packet.getPayload());
                    if (image != null) {
                        SwingUtilities.invokeLater(() -> screenPanel.setImage(image));
                    }
                }
                case PacketType.SCREEN_START -> {
                    String teacher = readTeacher(packet);
                    SwingUtilities.invokeLater(() ->
                            showStatus(teacher + " đang chia sẻ màn hình.", false));
                }
                case PacketType.SCREEN_STOP -> {
                    String message = readScreenStopMessage(packet);
                    SwingUtilities.invokeLater(() -> {
                        stopScreenCapturer();
                        sharing = false;
                        shareButton.setText("Chia sẻ màn hình");
                        screenPanel.setImage(null);
                        showStatus(message, false);
                    });
                }
                case PacketType.CHAT_TEXT -> {
                    String message = readChat(packet);
                    if (message != null) {
                        SwingUtilities.invokeLater(() -> appendChat(message));
                    }
                }
                case PacketType.ERROR -> {
                    String message = readError(packet);
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

    private static List<String> readMembers(Packet packet) throws IOException {
        try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
            int count = input.readInt();
            if (count < 0 || count > 100_000) {
                throw new IOException("Số lượng thành viên không hợp lệ: " + count);
            }
            List<String> members = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String displayName = input.readUTF();
                String role = input.readUTF();
                String roleLabel = Protocol.ROLE_TEACHER.equals(role) ? "Giáo viên" : "Học sinh";
                members.add(displayName + " - " + roleLabel);
            }
            return members;
        }
    }

    private static String readTeacher(Packet packet) throws IOException {
        try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
            input.readInt();
            return input.readUTF();
        }
    }

    private static String readScreenStopMessage(Packet packet) throws IOException {
        try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
            input.readInt();
            return input.readUTF();
        }
    }

    private String readChat(Packet packet) throws IOException {
        try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
            int messageRoomId = input.readInt();
            String text = input.readUTF();
            return messageRoomId == roomId ? text : null;
        }
    }

    private static String readError(Packet packet) throws IOException {
        try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
            return input.readUTF();
        }
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
