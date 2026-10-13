package com.pbl4.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Băm mật khẩu SHA-256 ở phía client (bước 3.1 — nguyên tắc "không bao giờ tin client").
 *
 * <p>Mật khẩu <b>không bao giờ</b> được gửi dạng thô qua mạng, và server <b>không bao giờ</b>
 * lưu mật khẩu gốc — chỉ lưu chuỗi hex 64 ký tự.</p>
 *
 * <p>Người viết: TV1 · Người dùng: TV2, TV3 (client băm trước khi gửi) và TV3 (server so sánh).</p>
 *
 * <p><b>Lưu ý trung thực để ghi vào báo cáo:</b> SHA-256 trần không có salt nên vẫn bị tấn công
 * từ điển. Đây là mức bảo mật của một đồ án môn học, không phải của hệ thống thật.</p>
 */
public final class Sha256 {

    private Sha256() {
    }

    /** Trả về chuỗi hex thường (64 ký tự) của SHA-256 trên chuỗi đầu vào. */
    public static String hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán bắt buộc trong mọi JDK, không thể thiếu.
            throw new IllegalStateException("JDK khong ho tro SHA-256", e);
        }
    }
}
