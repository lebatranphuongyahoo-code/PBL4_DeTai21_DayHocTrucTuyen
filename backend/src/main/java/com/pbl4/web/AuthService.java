package com.pbl4.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    public record Registration(String username, String displayName, String password, String role) {}
    public record Login(String username, String password) {}
    public record User(String username, String displayName, String role) {}
    public record Session(String token, User user) {}
    public static class Failure extends RuntimeException {
        public final int status; public final Map<String,String> errors;
        public Failure(int status, String message, Map<String,String> errors) { super(message); this.status=status; this.errors=errors; }
    }
    private final Database db;
    private final LoginAttempts attempts=new LoginAttempts();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final String dummyHash = encoder.encode("dummy-password-for-timing");
    private final SecureRandom random = new SecureRandom();
    public AuthService(Database db) { this.db = db; }
    public static User publicUser(Database.Account a) { return new User(a.username(),a.displayName(),"USER"); }
    private String text(String value) { return value == null ? "" : value.strip(); }
    public User register(Registration input) throws SQLException {
        String username=text(input.username()), displayName=text(input.displayName()), password=input.password()==null?"":input.password();
        var errors = new LinkedHashMap<String,String>();
        if (!username.matches("[A-Za-z][A-Za-z0-9_]{2,31}")) errors.put("username","Tên tài khoản cần 3–32 ký tự, bắt đầu bằng chữ, chỉ gồm chữ không dấu, số và _.");
        if (displayName.length()<2 || displayName.length()>80 || displayName.codePoints().anyMatch(Character::isISOControl)) errors.put("displayName","Họ tên cần 2–80 ký tự.");
        if(!strong(password))errors.put("password",PASSWORD_RULE);
        if (input.role()!=null && !input.role().equals("STUDENT")) errors.put("role","Tài khoản không chọn vai trò. Quyền được xác định theo từng lớp.");
        if (!errors.isEmpty()) throw new Failure(400,"Kiểm tra lại thông tin đăng ký.",errors);
        if (!db.createStudent(username, displayName, encoder.encode(password))) throw new Failure(409,"Tên tài khoản đã tồn tại.",Map.of("username","Tên tài khoản đã tồn tại."));
        return new User(username,displayName,"USER");
    }
    public Session login(Login input) throws SQLException {
        attempts.check(text(input.username()));
        Database.Account account = db.findAccount(text(input.username()));
        String password=input.password()==null?"":input.password();
        boolean matches = password.getBytes(StandardCharsets.UTF_8).length<=72 && encoder.matches(password,account==null?dummyHash:account.passwordHash());
        if (account==null || !matches) throw new Failure(401,"Tên tài khoản hoặc mật khẩu không đúng.",Map.of());
        attempts.success(text(input.username()));
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.saveSession(tokenHash(token),account.id(),System.currentTimeMillis()+8*60*60*1000L);
        return new Session(token,publicUser(account));
    }
    private static final String PASSWORD_RULE="Mật khẩu cần ít nhất 8 ký tự, tối đa 72 byte UTF-8, có chữ hoa A–Z, chữ thường a–z, số và ký tự đặc biệt.";
    private boolean strong(String password) {
return !(password.length()<8 || password.getBytes(StandardCharsets.UTF_8).length>72 || password.codePoints().anyMatch(Character::isISOControl) || !password.matches("(?s).*[A-Z].*") || !password.matches("(?s).*[a-z].*") || !password.matches("(?s).*[0-9].*") || password.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c)));
    }
    public void changePassword(Database.Account user,String current,String next)throws SQLException {
        if(current==null||current.getBytes(StandardCharsets.UTF_8).length>72||!encoder.matches(current,user.passwordHash()))throw new Failure(400,"Mật khẩu hiện tại không đúng.",Map.of());
        if(next==null||!strong(next))throw new Failure(400,PASSWORD_RULE,Map.of());
        if(encoder.matches(next,user.passwordHash()))throw new Failure(400,"Mật khẩu mới phải khác mật khẩu hiện tại.",Map.of());
        String hash=encoder.encode(next);db.transaction(c->{try(var q=c.prepareStatement("UPDATE accounts SET password_hash=? WHERE id=? AND password_hash=? AND status='ACTIVE'")){q.setString(1,hash);q.setLong(2,user.id());q.setString(3,user.passwordHash());if(q.executeUpdate()!=1)throw new Failure(409,"Tài khoản vừa thay đổi. Hãy đăng nhập lại.",Map.of());}
            try(var q=c.prepareStatement("DELETE FROM sessions WHERE account_id=?")){q.setLong(1,user.id());q.executeUpdate();}return null;});
    }
    public static String tokenHash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
