package com.pbl4.web;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthService auth; private final Database db; private final MediaTicketService mediaTickets;
    public ApiController(AuthService auth, Database db, MediaTicketService mediaTickets) { this.auth=auth; this.db=db; this.mediaTickets=mediaTickets; }
    @GetMapping("/health") public Map<String,Object> health() { return Map.of("ok",true,"service","classroom-java","phase","foundation"); }
    @PostMapping("/auth/register") public ResponseEntity<?> register(@RequestBody AuthService.Registration input) throws SQLException {
        return ResponseEntity.status(201).body(Map.of("ok",true,"user",auth.register(input)));
    }
    private void restoreCookie(HttpServletRequest request,HttpServletResponse response,String token,long age) {
        response.addHeader("Set-Cookie",ResponseCookie.from("pbl4_restore",token).httpOnly(true).secure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))).sameSite("Strict").path("/api/auth").maxAge(age).build().toString());
        response.setHeader("Cache-Control","no-store");
    }
    @PostMapping("/auth/login") public Map<String,Object> login(@RequestBody AuthService.Login input,HttpServletRequest request,HttpServletResponse response) throws SQLException {
        var session=auth.login(input); restoreCookie(request,response,session.token(),8*60*60); return Map.of("ok",true,"user",session.user(),"token",session.token());
    }
    // Cookie restores a bearer into memory only; it never authorizes modifying APIs.
    @GetMapping("/auth/session") public ResponseEntity<?> restore(@CookieValue(name="pbl4_restore",required=false) String token) throws SQLException {
        var account=token==null||token.length()>128?null:db.sessionAccount(AuthService.tokenHash(token));
        if(account==null)return ResponseEntity.status(401).header("Cache-Control","no-store").body(Map.of("ok",false,"message","Phiên đã hết hạn. Hãy đăng nhập lại."));
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("ok",true,"user",AuthService.publicUser(account),"token",token));
    }
    @PostMapping("/media/ticket") public Map<String,Object> mediaTicket(Authentication identity,@RequestHeader("Authorization") String authorization) { return Map.of("ok",true,"ticket",mediaTickets.issue(AuthService.tokenHash(authorization.substring(7)))); }
    @GetMapping("/me") public Map<String,Object> me(Authentication identity) {
        return Map.of("ok",true,"user",AuthService.publicUser((Database.Account)identity.getPrincipal()));
    }
    @PostMapping("/auth/logout") public Map<String,Object> logout(@RequestHeader("Authorization") String authorization,HttpServletRequest request,HttpServletResponse response) throws SQLException {
        db.deleteSession(AuthService.tokenHash(authorization.substring(7))); restoreCookie(request,response,"",0);return Map.of("ok",true);
    }
    @GetMapping("/rooms") public Map<String,Object> rooms(Authentication identity) throws SQLException {
        return Map.of("ok",true,"rooms",db.listClassrooms(((Database.Account)identity.getPrincipal()).id()));
    }
    @ExceptionHandler(AuthService.Failure.class) public ResponseEntity<?> failure(AuthService.Failure failure) {
        return ResponseEntity.status(failure.status).body(Map.of("ok",false,"message",failure.getMessage(),"errors",failure.errors));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) public ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().body(Map.of("ok",false,"message","Dữ liệu gửi lên không hợp lệ."));
    }
}
