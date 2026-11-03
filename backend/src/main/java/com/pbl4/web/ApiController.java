package com.pbl4.web;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthService auth; private final Database db;
    public ApiController(AuthService auth, Database db) { this.auth=auth; this.db=db; }
    @GetMapping("/health") public Map<String,Object> health() { return Map.of("ok",true,"service","classroom-java","phase","foundation"); }
    @PostMapping("/auth/register") public ResponseEntity<?> register(@RequestBody AuthService.Registration input) throws SQLException {
        return ResponseEntity.status(201).body(Map.of("ok",true,"user",auth.register(input)));
    }
    @PostMapping("/auth/login") public Map<String,Object> login(@RequestBody AuthService.Login input) throws SQLException {
        var session=auth.login(input); return Map.of("ok",true,"user",session.user(),"token",session.token());
    }
    @GetMapping("/me") public Map<String,Object> me(Authentication identity) {
        return Map.of("ok",true,"user",AuthService.publicUser((Database.Account)identity.getPrincipal()));
    }
    @PostMapping("/auth/logout") public Map<String,Object> logout(@RequestHeader("Authorization") String authorization) throws SQLException {
        db.deleteSession(AuthService.tokenHash(authorization.substring(7))); return Map.of("ok",true);
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
