package com.pbl4.client.ui;

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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** User interface for a joined online classroom. */
public class ClassroomUI {

    /** Adapter implemented by the audio module; implementations live with TV2's audio code. */
    public interface AudioPlayerControl {
        int start() throws Exception;

        void stop();
    }

    /** Adapter implemented by the audio module; implementations live with TV2's audio code. */
    public interface AudioRecorderControl {
        void start(String serverIp, int serverPort, int roomId) throws Exception;

        void stop();
    }

    /**
     * Adapter implemented by the screen module; the implementation lives with TV2's media code.
     *
     * <p>Giao diện chỉ biết "bật chụp" và "tắt chụp", không biết gì về Robot, JPEG hay FPS.</p>
     */
    public interface ScreenShareControl {
        void startCapture() throws Exception;

        void stopCapture();
    }

    private final ServerConnection connection;
    private final DatagramSocket audioSocket;
    private final AudioPlayerControl audioPlayer;
    private final AudioRecorderControl audioRecorder;
    private final ScreenShareControl screenShare;
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
    private volatile Consumer<Packet> screenFrameListener = packet -> { };

    private boolean micOn;
    private boolean sharing;
    private boolean inRoom = true;

    public ClassroomUI(ServerConnection connection, String username, String role,
                       int roomId, String roomName, DatagramSocket audioSocket, String serverIp) {
        this(connection, username, role, roomId, roomName, audioSocket, serverIp, null, null, null);
    }

    public ClassroomUI(ServerConnection connection, String username, String role,
                       int roomId, String roomName, DatagramSocket audioSocket, String serverIp,
                       AudioPlayerControl audioPlayer, AudioRecorderControl audioRecorder) {
        this(connection, username, role, roomId, roomName, audioSocket, serverIp,
                audioPlayer, audioRecorder, null);
    }

    /**
     * @param screenShare bộ chụp màn hình của TV2; truyền {@code null} thì nút chia sẻ vẫn hiện
     *                    nhưng bấm sẽ báo chưa nối được thiết bị
     */
    public ClassroomUI(ServerConnection connection, String username, String role,
                       int roomId, String roomName, DatagramSocket audioSocket, String serverIp,
                       AudioPlayerControl audioPlayer, AudioRecorderControl audioRecorder,
                       ScreenShareControl screenShare) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.audioSocket = audioSocket;
        this.roomId = roomId;
        this.role = Objects.requireNonNull(role, "role");
        this.serverIp = Objects.requireNonNull(serverIp, "serverIp");
        this.audioPlayer = audioPlayer;
        this.audioRecorder = audioRecorder;
        this.screenShare = screenShare;
        this.screenFrameListener = this::renderScreenFrame;
        this.frame = new JFrame("Lớp " + roomName + " (" + roomId + ")");

        buildWindow(username);
        connection.setListener(this::onPacket);
        connection.setDisconnectListener(cause -> SwingUtilities.invokeLater(() -> {
            inRoom = false;
            stopAudioDevices();
            disableControls();
            showStatus("Mất kết nối máy chủ: " + cause.getMessage(), true);
        }));

        micButton.addActionListener(event -> toggleMicrophone());
        shareButton.addActionListener(event -> toggleScreenSharing());
        leaveButton.addActionListener(event -> leaveRoom());
        chatInput.addActionListener(event -> sendChat());
        frame.setVisible(true);
    }

    /** Registers TV2's screen-frame receiver; it is called from the network thread. */
    @SuppressWarnings("unused")
    public void setScreenFrameListener(Consumer<Packet> listener) {
        screenFrameListener = Objects.requireNonNull(listener, "listener");
    }

    private void buildWindow(String username) {
        JPanel membersPanel = new JPanel(new BorderLayout());
        membersPanel.setBorder(BorderFactory.createTitledBorder("Thành viên"));
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        membersPanel.add(new JScrollPane(memberList), BorderLayout.CENTER);
        membersPanel.setPreferredSize(new java.awt.Dimension(230, 0));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(screenPanel, BorderLayout.CENTER);
        center.add(new JLabel("Người dùng: " + username), BorderLayout.NORTH);

        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        micButton.setEnabled(audioPlayer != null && audioRecorder != null);
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
                // Tắt thiết bị trước khi đóng socket/kết nối, nếu không luồng chụp và luồng
                // thu sẽ ghi lên tài nguyên đã đóng.
                ClassroomUI.this.stopScreenCapture();
                ClassroomUI.this.stopAudioDevices();
                if (ClassroomUI.this.audioSocket != null) {
                    ClassroomUI.this.audioSocket.close();
                }
                ClassroomUI.this.connection.close();
            }
        });
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
            // Ngừng chụp TRƯỚC khi báo server, để không còn frame nào bay đi sau khi lớp tắt cờ.
            stopScreenCapture();
            if (sendRoomCommand(PacketType.SCREEN_STOP, "Đã dừng chia sẻ màn hình.")) {
                sharing = false;
                shareButton.setText("Chia sẻ màn hình");
            }
            return;
        }

        if (screenShare == null) {
            showStatus("Chưa nối được bộ chụp màn hình.", true);
            return;
        }
        if (!sendRoomCommand(PacketType.SCREEN_START, "Đang bắt đầu chia sẻ...")) {
            return;
        }
        sharing = true;
        shareButton.setText("Dừng chia sẻ màn hình");

        // Server chỉ chuyển tiếp frame khi cờ chia sẻ của lớp đã bật, mà cờ đó bật lúc server
        // xử lý xong SCREEN_START. Vì vậy phải gửi lệnh trước rồi mới bật chụp.
        try {
            screenShare.startCapture();
            showStatus("Đang chia sẻ màn hình.", false);
        } catch (Exception e) {
            sharing = false;
            shareButton.setText("Chia sẻ màn hình");
            sendRoomCommand(PacketType.SCREEN_STOP, "Không chia sẻ được màn hình.");
            showStatus("Không chụp được màn hình: " + e.getMessage(), true);
        }
    }

    /** Dừng bộ chụp nếu đã nối; an toàn khi gọi nhiều lần. */
    private void stopScreenCapture() {
        if (screenShare != null) {
            screenShare.stopCapture();
        }
    }

    /**
     * Vẽ frame nhận được lên {@link ScreenPanel}.
     *
     * <p>Hàm chạy trên <b>luồng mạng</b> (xem {@link #onPacket}), nên phần cập nhật giao diện
     * được đẩy sang EDT bằng {@code invokeLater}. Frame hỏng bị bỏ qua và giữ lại frame cũ —
     * thà chậm một frame còn hơn nháy đen.</p>
     */
    private void renderScreenFrame(Packet packet) {
        BufferedImage image = ScreenReceiver.decode(packet.getPayload());
        if (image == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> screenPanel.setImage(image));
    }

    private boolean sendRoomCommand(byte type, String status) {
        try {
            byte[] payload = PacketUtils.buildPayload(output -> output.writeInt(roomId));
            connection.send(type, payload);
            showStatus(status, false);
            return true;
        } catch (IOException e) {
            showStatus("Không gửi được yêu cầu: " + e.getMessage(), true);
            return false;
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
                stopScreenCapture();
                sendRoomCommand(PacketType.SCREEN_STOP, "Đang rời phòng...");
                sharing = false;
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
                case PacketType.SCREEN_FRAME -> screenFrameListener.accept(packet);
                case PacketType.SCREEN_START -> {
                    String teacher = readTeacher(packet);
                    SwingUtilities.invokeLater(() ->
                            showStatus(teacher + " đang chia sẻ màn hình.", false));
                }
                case PacketType.SCREEN_STOP -> {
                    String message = readScreenStopMessage(packet);
                    SwingUtilities.invokeLater(() -> {
                        // Xoá frame cũ, trả khung hình về dòng chữ "Chưa có ai chia sẻ màn hình".
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
                members.add(input.readUTF() + " — " + input.readUTF());
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
