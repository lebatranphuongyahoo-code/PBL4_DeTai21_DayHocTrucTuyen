package com.pbl4.client.ui;

import com.pbl4.client.media.AudioSession;
import com.pbl4.client.net.ServerConnection;
import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.Protocol;
import com.pbl4.common.RoomInfo;
import com.pbl4.common.msg.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Home screen for creating, browsing, and joining classrooms. */
public class HomeUI {

    private final ServerConnection connection;
    private final AudioSession audio;
    private final String username;
    private final String role;
    private final String serverIp;
    private final JFrame frame = new JFrame("Trang chủ");
    private final JTextField roomNameField = new JTextField(20);
    private final JButton createRoomButton = new JButton("Tạo lớp");
    private final JButton refreshButton = new JButton("Làm mới");
    private final JButton joinButton = new JButton("Tham gia");
    private final JLabel createHint = new JLabel();
    private final JLabel statusLabel = new JLabel("Đã đăng nhập.");
    private final DefaultListModel<RoomEntry> roomModel = new DefaultListModel<>();
    private final JList<RoomEntry> roomList = new JList<>(roomModel);
    private boolean connectionAlive = true;
    private boolean handedOff;
    private volatile RoomEntry joiningRoom;
    private final Object joinPacketLock = new Object();
    private final List<Packet> earlyMemberPackets = new ArrayList<>();
    private ClassroomUI classroom;

    public HomeUI(ServerConnection connection, String username, String role, String serverIp) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.username = username;
        this.role = role;
        this.serverIp = Objects.requireNonNull(serverIp, "serverIp");
        boolean teacher = Protocol.ROLE_TEACHER.equals(role);
        AudioSession openedAudio;
        try {
            openedAudio = AudioSession.open();
        } catch (SocketException e) {
            openedAudio = null;
            statusLabel.setForeground(Color.RED);
            statusLabel.setText("Không mở được cổng UDP nhận thoại: " + e.getMessage());
        }
        audio = openedAudio;

        buildWindow(username, role, teacher);
        connection.setListener(this::onPacket);
        connection.setDisconnectListener(cause -> SwingUtilities.invokeLater(() -> {
            connectionAlive = false;
            statusLabel.setForeground(Color.RED);
            statusLabel.setText("Mất kết nối máy chủ: " + cause.getMessage());
            disableRoomActions();
        }));

        createRoomButton.addActionListener(event -> createRoom());
        refreshButton.addActionListener(event -> requestRoomList());
        joinButton.addActionListener(event -> joinSelectedRoom());
        frame.setVisible(true);

        if (audio != null) {
            requestRoomList();
        }
    }

    private void buildWindow(String username, String role, boolean teacher) {
        JPanel createPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        createPanel.add(new JLabel("Tên lớp:"));
        createPanel.add(roomNameField);
        createRoomButton.setEnabled(teacher && audio != null);
        createPanel.add(createRoomButton);
        createHint.setText(teacher ? "" : "Chỉ giáo viên được tạo lớp.");
        createPanel.add(createHint);

        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel roomPanel = new JPanel(new BorderLayout(8, 8));
        roomPanel.setBorder(BorderFactory.createTitledBorder("Danh sách lớp"));
        roomPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        refreshButton.setEnabled(audio != null);
        joinButton.setEnabled(false);
        actions.add(refreshButton);
        actions.add(joinButton);
        roomPanel.add(actions, BorderLayout.SOUTH);
        roomList.addListSelectionListener(event ->
                joinButton.setEnabled(!event.getValueIsAdjusting()
                        && roomList.getSelectedValue() != null
                        && connectionAlive
                        && audio != null));

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        footer.add(statusLabel, BorderLayout.CENTER);

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        mainPanel.add(createPanel, BorderLayout.NORTH);
        mainPanel.add(roomPanel, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(new JLabel("Xin chào, " + username + " (" + role + ")"),
                BorderLayout.NORTH);
        frame.add(mainPanel, BorderLayout.CENTER);
        frame.add(footer, BorderLayout.SOUTH);
        frame.setSize(640, 430);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (!handedOff) {
                    if (audio != null) {
                        audio.close();
                    }
                    HomeUI.this.connection.close();
                }
            }
        });
    }

    private void requestRoomList() {
        sendRequest(PacketType.ROOM_LIST_REQ, new byte[0], "Đang tải danh sách lớp...");
    }

    private void createRoom() {
        String roomName = roomNameField.getText().trim();
        if (roomName.isEmpty()) {
            showStatus("Vui lòng nhập tên lớp.", true);
            return;
        }
        byte[] payload = new Messages.CreateRoomRequest(roomName).encode();
        sendRequest(PacketType.CREATE_ROOM_REQ, payload, "Đang tạo lớp...");
    }

    private void joinSelectedRoom() {
        RoomEntry selected = roomList.getSelectedValue();
        if (selected == null || audio == null) {
            showStatus("Hãy chọn lớp cần tham gia.", true);
            return;
        }
        byte[] payload = new Messages.JoinRoomRequest(
                selected.info().roomId(), audio.localPort()).encode();
        joiningRoom = selected;
        joinButton.setEnabled(false);
        sendRequest(PacketType.JOIN_ROOM_REQ, payload, "Đang tham gia lớp...");
    }

    private void sendRequest(byte type, byte[] payload, String message) {
        try {
            connection.send(type, payload);
            showStatus(message, false);
        } catch (IOException e) {
            showStatus("Không gửi được yêu cầu: " + e.getMessage(), true);
        }
    }

    private void onPacket(Packet packet) {
        if (packet.getType() == PacketType.USER_LIST) {
            synchronized (joinPacketLock) {
                if (classroom != null) {
                    classroom.acceptPacket(packet);
                    return;
                }
                if (joiningRoom != null) {
                    earlyMemberPackets.add(packet);
                    return;
                }
            }
        }
        try {
            switch (packet.getType()) {
                case PacketType.ROOM_LIST_RES -> {
                    List<RoomInfo> rooms = Messages.RoomList.decode(packet.getPayload()).rooms();
                    SwingUtilities.invokeLater(() -> displayRooms(rooms));
                }
                case PacketType.CREATE_ROOM_RES -> {
                    Messages.CreateRoomResponse res =
                            Messages.CreateRoomResponse.decode(packet.getPayload());
                    SwingUtilities.invokeLater(() -> {
                        showStatus(res.ok()
                                ? res.message() + " (mã " + res.roomId() + ")"
                                : res.message(), !res.ok());
                        if (res.ok()) {
                            roomNameField.setText("");
                            requestRoomList();
                        }
                    });
                }
                case PacketType.JOIN_ROOM_RES -> {
                    Messages.StatusResponse res =
                            Messages.StatusResponse.decode(packet.getPayload());
                    SwingUtilities.invokeLater(() -> {
                        RoomEntry joined = joiningRoom;
                        joiningRoom = null;
                        if (res.ok() && joined != null) {
                            handedOff = true;
                            List<Packet> buffered;
                            synchronized (joinPacketLock) {
                                classroom = new ClassroomUI(connection, username, role,
                                        joined.info().roomId(), joined.info().roomName(),
                                        audio, serverIp);
                                buffered = new ArrayList<>(earlyMemberPackets);
                                earlyMemberPackets.clear();
                            }
                            for (Packet earlyPacket : buffered) {
                                classroom.acceptPacket(earlyPacket);
                            }
                            frame.dispose();
                        } else {
                            joinButton.setEnabled(connectionAlive
                                    && roomList.getSelectedValue() != null
                                    && audio != null);
                            showStatus(res.message(), true);
                        }
                    });
                }
                case PacketType.ERROR -> {
                    String message = Messages.ErrorMessage.decode(packet.getPayload()).message();
                    SwingUtilities.invokeLater(() -> showStatus(message, true));
                }
                default -> {
                    // Other packet types are handled by the active screen.
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> showStatus("Phản hồi máy chủ không hợp lệ.", true));
        }
    }

    private void displayRooms(List<RoomInfo> rooms) {
        roomModel.clear();
        for (RoomInfo room : rooms) {
            roomModel.addElement(new RoomEntry(room));
        }
        showStatus("Đã tải " + rooms.size() + " lớp.", false);
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.setForeground(error ? Color.RED : Color.DARK_GRAY);
    }

    private void disableRoomActions() {
        createRoomButton.setEnabled(false);
        refreshButton.setEnabled(false);
        joinButton.setEnabled(false);
    }

    private record RoomEntry(RoomInfo info) {
        @Override
        public String toString() {
            return info.roomId() + " — " + info.roomName() + " — "
                    + info.teacherUsername() + " — " + info.memberCount();
        }
    }
}
