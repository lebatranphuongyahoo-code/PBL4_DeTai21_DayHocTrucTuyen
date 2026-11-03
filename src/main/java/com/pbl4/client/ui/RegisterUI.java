package com.pbl4.client.ui;

import com.pbl4.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;

/** Registration form shown from the login screen. */
public class RegisterUI {
    private final LoginUI loginUI;
    private final JFrame frame = new JFrame("Đăng ký tài khoản");

    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JTextField contactField = new JTextField();
    private final JComboBox<String> roleComboBox =
            new JComboBox<>(new String[]{"Giáo viên", "Học sinh"});
    private final JLabel messageLabel = new JLabel("Nhập đầy đủ thông tin để tạo tài khoản.");
    private final JButton submitButton = new JButton("Xác nhận");
    private final JButton cancelButton = new JButton("Hủy");

    public RegisterUI(LoginUI loginUI) {
        this.loginUI = loginUI;
        buildWindow();
        frame.setVisible(true);
    }

    public void close() {
        frame.dispose();
    }

    private void buildWindow() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(240, 244, 255));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Tạo tài khoản mới");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(new Color(24, 69, 120));
        title.setHorizontalAlignment(JLabel.CENTER);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(186, 208, 255), 1, true),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        addFormRow(form, 0, "Tên đăng nhập:", usernameField);
        addFormRow(form, 1, "Mật khẩu:", passwordField);
        addFormRow(form, 2, "Số điện thoại hoặc email:", contactField);
        addFormRow(form, 3, "Vai trò:", roleComboBox);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        actions.setOpaque(false);
        styleButton(submitButton, new Color(54, 117, 255), Color.WHITE);
        styleButton(cancelButton, new Color(156, 164, 179), Color.WHITE);
        actions.add(cancelButton);
        actions.add(submitButton);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setOpaque(false);
        messageLabel.setForeground(new Color(68, 83, 103));
        messageLabel.setHorizontalAlignment(JLabel.CENTER);
        footer.add(messageLabel, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.SOUTH);

        submitButton.addActionListener(event -> submitRegistration());
        cancelButton.addActionListener(event -> close());

        root.add(title, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(240, 244, 255));
        frame.add(root, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(520, 360);
        frame.setMinimumSize(new Dimension(480, 340));
        frame.setLocationRelativeTo(null);
    }

    private void addFormRow(JPanel panel, int rowIndex, String labelText, java.awt.Component field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = rowIndex;
        labelConstraints.anchor = GridBagConstraints.LINE_END;
        labelConstraints.insets = new Insets(8, 0, 8, 10);
        labelConstraints.weightx = 0.4;

        JLabel label = new JLabel(labelText);
        label.setForeground(new Color(41, 59, 82));
        panel.add(label, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = rowIndex;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.weightx = 0.6;
        fieldConstraints.insets = new Insets(8, 0, 8, 0);

        field.setPreferredSize(new Dimension(260, 34));
        field.setMinimumSize(new Dimension(160, 34));
        if (field instanceof JTextField textField) {
            textField.setBackground(new Color(249, 251, 255));
            textField.setForeground(new Color(34, 42, 53));
            textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(196, 210, 234), 1, true),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        } else if (field instanceof JComboBox<?> comboBox) {
            comboBox.setBackground(new Color(249, 251, 255));
            comboBox.setForeground(new Color(34, 42, 53));
            comboBox.setBorder(BorderFactory.createLineBorder(new Color(196, 210, 234), 1, true));
        }

        panel.add(field, fieldConstraints);
    }

    private void styleButton(JButton button, Color bg, Color textColor) {
        button.setFocusPainted(false);
        button.setBackground(bg);
        button.setForeground(textColor);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1, true),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    }

    private void submitRegistration() {
        String username = usernameField.getText().trim();
        String contact = contactField.getText().trim();
        String role = roleComboBox.getSelectedIndex() == 0
                ? Protocol.ROLE_TEACHER
                : Protocol.ROLE_STUDENT;

        if (username.isEmpty() || contact.isEmpty()) {
            showMessage("Vui lòng nhập tên đăng nhập và số điện thoại/email.", true);
            return;
        }

        String detectedType = Protocol.detectContactType(contact);
        if (detectedType == null) {
            showMessage("Số điện thoại hoặc email không hợp lệ. Ví dụ: 0901234567 hoặc abc@gmail.com", true);
            return;
        }

        char[] password = passwordField.getPassword();
        if (password.length == 0) {
            Arrays.fill(password, '\0');
            showMessage("Vui lòng nhập mật khẩu.", true);
            return;
        }

        String passwordHash = com.pbl4.common.Sha256.sha256Hex(new String(password));
        Arrays.fill(password, '\0');

        loginUI.submitRegistrationFromDialog(username, passwordHash, contact, detectedType, role);
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.setForeground(error ? Color.RED : new Color(68, 83, 103));
    }
}
