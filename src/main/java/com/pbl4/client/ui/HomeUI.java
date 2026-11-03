package com.pbl4.client.ui;

import com.pbl4.client.net.ServerConnection;
import com.pbl4.common.Packet;
import com.pbl4.common.PacketType;
import com.pbl4.common.PacketUtils;
import com.pbl4.common.Protocol;
import com.pbl4.common.RoomInfo;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Home screen for creating, browsing, and joining classrooms. */
public class HomeUI {

    private final ServerConnection connection;
    private final DatagramSocket audioSocket;
    private final String username;
    private String displayName;
    private final String role;
    private final String serverIp;
    private final JFrame frame = new JFrame("Trang chủ");
    private final JTextField roomNameField = new JTextField(20);
    private final JTextField searchField = new JTextField(20);
    private final JButton createRoomButton = new JButton("Tạo lớp");
    private final JButton refreshButton = new JButton("Làm mới");
    private final JButton joinButton = new JButton("Tham gia");
    private final JButton deleteRoomButton = new JButton("Xóa lớp");
    private final JButton logoutButton = new JButton("Đăng xuất");
    private final JLabel createHint = new JLabel();
    private final JLabel statusLabel = new JLabel("Đã đăng nhập.");
    private JLabel welcomeLabel;
    private final DefaultListModel<RoomEntry> roomModel = new DefaultListModel<>();
    private final JList<RoomEntry> roomList = new JList<>(roomModel);
    private List<RoomInfo> availableRooms = new ArrayList<>();
    private boolean connectionAlive = true;
    private boolean handedOff;
    private volatile RoomEntry joiningRoom;
    private final Object joinPacketLock = new Object();
    private final List<Packet> earlyMemberPackets = new ArrayList<>();
    private ClassroomUI classroom;

    public HomeUI(ServerConnection connection, String username, String role, String serverIp) {
        this(connection, username, username, role, serverIp);
    }

    public HomeUI(ServerConnection connection, String username, String displayName,
                  String role, String serverIp) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.username = username;
        this.displayName = displayName == null || displayName.isBlank() ? username : displayName;
        this.role = role;
        this.serverIp = Objects.requireNonNull(serverIp, "serverIp");
        boolean teacher = Protocol.ROLE_TEACHER.equals(role);
        DatagramSocket openedAudioSocket;
        try {
            openedAudioSocket = new DatagramSocket(0);
        } catch (SocketException e) {
            openedAudioSocket = null;
            statusLabel.setForeground(Color.RED);
            statusLabel.setText("Không mở được cổng UDP nhận thoại: " + e.getMessage());
        }
        audioSocket = openedAudioSocket;

        buildWindow(role, teacher);
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
        deleteRoomButton.addActionListener(event -> deleteSelectedRoom());
        logoutButton.addActionListener(event -> logout());
        frame.setVisible(true);

        if (audioSocket != null) {
            requestRoomList();
        }
    }

    private void buildWindow(String role, boolean teacher) {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(240, 244, 255));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));

        welcomeLabel = new JLabel("Xin chào, " + displayName + " (" + role + ")");
        welcomeLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        welcomeLabel.setForeground(new Color(24, 69, 120));
        welcomeLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 0));
        welcomeLabel.setToolTipText("Nhấn để đổi tên hiển thị");
        welcomeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        welcomeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                editDisplayName();
            }
        });
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        styleButton(logoutButton, new Color(201, 76, 76), Color.WHITE);
        header.add(welcomeLabel, BorderLayout.CENTER);
        header.add(logoutButton, BorderLayout.EAST);

        JPanel createPanel = null;
        if (teacher) {
            createPanel = new JPanel(new BorderLayout(8, 8));
            createPanel.setOpaque(false);
            createPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(186, 208, 255), 1, true),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)));
            JPanel createFields = new JPanel(new GridBagLayout());
            createFields.setOpaque(false);
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = 0;
            labelConstraints.anchor = GridBagConstraints.LINE_START;
            labelConstraints.insets = new Insets(0, 0, 0, 10);
            createFields.add(new JLabel("Tên lớp:"), labelConstraints);

            styleInput(roomNameField);
            GridBagConstraints inputConstraints = new GridBagConstraints();
            inputConstraints.gridx = 1;
            inputConstraints.gridy = 0;
            inputConstraints.weightx = 1;
            inputConstraints.fill = GridBagConstraints.HORIZONTAL;
            inputConstraints.insets = new Insets(0, 0, 0, 10);
            createFields.add(roomNameField, inputConstraints);

            createRoomButton.setEnabled(audioSocket != null);
            styleButton(createRoomButton, new Color(54, 117, 255), Color.WHITE);
            GridBagConstraints buttonConstraints = new GridBagConstraints();
            buttonConstraints.gridx = 2;
            buttonConstraints.gridy = 0;
            buttonConstraints.anchor = GridBagConstraints.LINE_END;
            createFields.add(createRoomButton, buttonConstraints);
            createPanel.add(createFields, BorderLayout.CENTER);
        } else {
            styleInput(searchField);
            searchField.setToolTipText("Tìm theo tên lớp, mã lớp hoặc giáo viên");
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    displayRooms(availableRooms);
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    displayRooms(availableRooms);
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    displayRooms(availableRooms);
                }
            });
            JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
            searchPanel.setOpaque(false);
            searchPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(186, 208, 255), 1, true),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)));
            JLabel searchLabel = new JLabel("Tìm phòng:");
            searchLabel.setForeground(new Color(41, 59, 82));
            searchPanel.add(searchLabel, BorderLayout.WEST);
            searchPanel.add(searchField, BorderLayout.CENTER);
            createPanel = searchPanel;
        }

        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setBackground(new Color(250, 252, 255));
        roomList.setForeground(new Color(35, 46, 60));
        roomList.setSelectionBackground(new Color(176, 210, 255));
        roomList.setSelectionForeground(new Color(16, 31, 52));
        roomList.setBorder(BorderFactory.createEmptyBorder());

        JPanel roomPanel = new JPanel(new BorderLayout(8, 8));
        roomPanel.setOpaque(false);
        roomPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(186, 208, 255), 1),
                "Danh sách lớp"));
        roomPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        refreshButton.setEnabled(audioSocket != null);
        joinButton.setEnabled(false);
        deleteRoomButton.setEnabled(false);
        styleButton(refreshButton, new Color(88, 139, 214), Color.WHITE);
        styleButton(joinButton, new Color(41, 161, 111), Color.WHITE);
        actions.add(joinButton);
        if (teacher) {
            actions.add(refreshButton);
            styleButton(deleteRoomButton, new Color(201, 76, 76), Color.WHITE);
            deleteRoomButton.setEnabled(false);
            actions.add(deleteRoomButton);
        }
        roomPanel.add(actions, BorderLayout.SOUTH);
        roomList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateRoomActions();
            }
        });

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(8, 4, 10, 4));
        statusLabel.setForeground(new Color(68, 83, 103));
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        footer.add(statusLabel, BorderLayout.CENTER);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setOpaque(false);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        mainPanel.add(createPanel, BorderLayout.NORTH);
        mainPanel.add(roomPanel, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout(8, 8));
        frame.getContentPane().setBackground(new Color(240, 244, 255));
        frame.add(header, BorderLayout.NORTH);
        frame.add(mainPanel, BorderLayout.CENTER);
        frame.add(footer, BorderLayout.SOUTH);
        frame.setSize(700, 470);
        frame.setMinimumSize(new Dimension(520, 360));
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (!handedOff) {
                    if (audioSocket != null) {
                        audioSocket.close();
                    }
                    HomeUI.this.connection.close();
                }
            }
        });
    }

    private void styleInput(JTextField field) {
        field.setBackground(new Color(249, 251, 255));
        field.setForeground(new Color(34, 42, 53));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(196, 210, 234), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        field.setPreferredSize(new Dimension(0, 34));
        field.setMinimumSize(new Dimension(0, 34));
    }

    private void styleButton(JButton button, Color bg, Color textColor) {
        button.setFocusPainted(false);
        button.setBackground(bg);
        button.setForeground(textColor);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1, true),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
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
        byte[] payload = PacketUtils.buildPayload(output -> output.writeUTF(roomName));
        sendRequest(PacketType.CREATE_ROOM_REQ, payload, "Đang tạo lớp...");
    }

    private void joinSelectedRoom() {
        RoomEntry selected = roomList.getSelectedValue();
        if (selected == null || audioSocket == null) {
            showStatus("Hãy chọn lớp cần tham gia.", true);
            return;
        }
        byte[] payload = PacketUtils.buildPayload(output -> {
            output.writeInt(selected.info().roomId());
            output.writeInt(audioSocket.getLocalPort());
        });
        joiningRoom = selected;
        joinButton.setEnabled(false);
        sendRequest(PacketType.JOIN_ROOM_REQ, payload, "Đang tham gia lớp...");
    }

    private void logout() {
        handedOff = true;
        if (audioSocket != null) {
            audioSocket.close();
        }
        connection.close();
        frame.dispose();
        SwingUtilities.invokeLater(() -> new LoginUI(serverIp, Protocol.TCP_PORT));
    }

    private void editDisplayName() {
        String requestedName = JOptionPane.showInputDialog(frame,
                "Tên hiển thị (1–40 ký tự):", displayName);
        if (requestedName == null) {
            return;
        }
        requestedName = requestedName.trim();
        if (requestedName.isEmpty() || requestedName.length() > 40) {
            showStatus("Tên hiển thị phải có từ 1 đến 40 ký tự.", true);
            return;
        }
        String newName = requestedName;
        sendRequest(PacketType.UPDATE_DISPLAY_NAME_REQ,
                PacketUtils.buildPayload(output -> output.writeUTF(newName)),
                "Đang cập nhật tên hiển thị...");
    }

    private void updateWelcomeLabel() {
        welcomeLabel.setText("Xin chào, " + displayName + " (" + role + ")");
    }

    private void deleteSelectedRoom() {
        RoomEntry selected = roomList.getSelectedValue();
        if (selected == null) {
            showStatus("Hãy chọn lớp cần xóa.", true);
            return;
        }
        if (!Protocol.ROLE_TEACHER.equals(role)
                || !username.equals(selected.info().teacherUsername())) {
            showStatus("Chỉ giáo viên tạo lớp mới được xóa lớp.", true);
            return;
        }
        int roomId = selected.info().roomId();
        sendRequest(PacketType.DELETE_ROOM_REQ,
                PacketUtils.buildPayload(output -> output.writeInt(roomId)),
                "Đang xóa lớp...");
    }

    private void updateRoomActions() {
        RoomEntry selected = roomList.getSelectedValue();
        boolean selectedRoom = selected != null;
        boolean owner = selectedRoom
                && Protocol.ROLE_TEACHER.equals(role)
                && username.equals(selected.info().teacherUsername());
        boolean canJoin = selectedRoom && (owner || selected.info().teacherPresent());
        joinButton.setEnabled(connectionAlive && audioSocket != null && canJoin);
        deleteRoomButton.setEnabled(connectionAlive && owner);
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
                    List<RoomInfo> rooms = readRoomList(packet);
                    SwingUtilities.invokeLater(() -> displayRooms(rooms));
                }
                case PacketType.CREATE_ROOM_RES -> {
                    int ok;
                    String message;
                    int roomId;
                    try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
                        ok = input.readUnsignedByte();
                        message = input.readUTF();
                        roomId = input.readInt();
                    }
                    int response = ok;
                    String responseMessage = message;
                    int createdRoomId = roomId;
                    SwingUtilities.invokeLater(() -> {
                        showStatus(response == 1
                                ? responseMessage + " (mã " + createdRoomId + ")"
                                : responseMessage, response == 0);
                        if (response == 1) {
                            roomNameField.setText("");
                            requestRoomList();
                        }
                    });
                }
                case PacketType.DELETE_ROOM_RES -> {
                    int ok;
                    String message;
                    try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
                        ok = input.readUnsignedByte();
                        message = input.readUTF();
                        input.readInt();
                    }
                    int response = ok;
                    String responseMessage = message;
                    SwingUtilities.invokeLater(() -> {
                        showStatus(responseMessage, response != 1);
                        if (response == 1) {
                            requestRoomList();
                        }
                    });
                }
                case PacketType.UPDATE_DISPLAY_NAME_RES -> {
                    int ok;
                    String message;
                    String returnedName;
                    try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
                        ok = input.readUnsignedByte();
                        message = input.readUTF();
                        returnedName = input.readUTF();
                    }
                    int response = ok;
                    String responseMessage = message;
                    String responseName = returnedName;
                    SwingUtilities.invokeLater(() -> {
                        if (response == 1) {
                            displayName = responseName;
                            updateWelcomeLabel();
                        }
                        showStatus(responseMessage, response != 1);
                    });
                }
                case PacketType.JOIN_ROOM_RES -> {
                    int ok;
                    String message;
                    try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
                        ok = input.readUnsignedByte();
                        message = input.readUTF();
                    }
                    SwingUtilities.invokeLater(() -> {
                        RoomEntry joined = joiningRoom;
                        joiningRoom = null;
                        if (ok == 1 && joined != null) {
                            handedOff = true;
                            List<Packet> buffered;
                            synchronized (joinPacketLock) {
                                classroom = new ClassroomUI(connection, username, role,
                                        joined.info().roomId(), joined.info().roomName(),
                                        audioSocket, serverIp, displayName,
                                        () -> SwingUtilities.invokeLater(() ->
                                                new HomeUI(connection, username, displayName, role, serverIp)));
                                buffered = new ArrayList<>(earlyMemberPackets);
                                earlyMemberPackets.clear();
                            }
                            for (Packet earlyPacket : buffered) {
                                classroom.acceptPacket(earlyPacket);
                            }
                            frame.dispose();
                        } else {
                            updateRoomActions();
                            showStatus(message, true);
                        }
                    });
                }
                case PacketType.ERROR -> {
                    String message;
                    try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
                        message = input.readUTF();
                    }
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

    private static List<RoomInfo> readRoomList(Packet packet) throws IOException {
        try (DataInputStream input = PacketUtils.payloadReader(packet.getPayload())) {
            int count = input.readInt();
            if (count < 0 || count > 100_000) {
                throw new IOException("Số lượng lớp không hợp lệ: " + count);
            }
            List<RoomInfo> rooms = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                rooms.add(new RoomInfo(
                        input.readInt(),
                        input.readUTF(),
                        input.readUTF(),
                        input.readInt(),
                        input.readBoolean()));
            }
            return rooms;
        }
    }

    private void displayRooms(List<RoomInfo> rooms) {
        availableRooms = new ArrayList<>(rooms);
        roomModel.clear();
        String filter = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        List<RoomInfo> visibleRooms = rooms.stream()
                .filter(room -> !Protocol.ROLE_TEACHER.equals(role)
                        || username.equals(room.teacherUsername()))
                .filter(room -> Protocol.ROLE_TEACHER.equals(role) || filter.isEmpty()
                        || room.roomName().toLowerCase(java.util.Locale.ROOT).contains(filter)
                        || Integer.toString(room.roomId()).contains(filter)
                        || room.teacherUsername().toLowerCase(java.util.Locale.ROOT).contains(filter))
                .toList();
        for (RoomInfo room : visibleRooms) {
            roomModel.addElement(new RoomEntry(room));
        }
        updateRoomActions();
        showStatus("Đang hiển thị " + visibleRooms.size() + " / " + rooms.size() + " lớp.", false);
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
                    + info.teacherUsername() + " — " + info.memberCount() + " người — "
                    + (info.teacherPresent() ? "Đang mở" : "Đang khóa");
        }
    }
}
