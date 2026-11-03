package com.pbl4.web;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class ClassroomController {
    public record Create(String name) {}
    public record Add(String username) {}
    public record Join(String code) {}
    public record Review(boolean approve) {}
    private final ClassroomService rooms;
    public ClassroomController(ClassroomService rooms){this.rooms=rooms;}
    private Database.Account user(Authentication a){return (Database.Account)a.getPrincipal();}
    @PostMapping public Map<String,Object> create(Authentication a,@RequestBody Create input)throws SQLException{return Map.of("ok",true,"room",rooms.create(user(a),input.name()));}
    @GetMapping("/{id}") public Map<String,Object> detail(Authentication a,@PathVariable long id)throws SQLException{return Map.of("ok",true,"room",rooms.detail(user(a),id));}
    @PostMapping("/{id}/members") public Map<String,Object> add(Authentication a,@PathVariable long id,@RequestBody Add input)throws SQLException{rooms.add(user(a),id,input.username());return Map.of("ok",true);}
    @DeleteMapping("/{id}/members/{account}") public Map<String,Object> remove(Authentication a,@PathVariable long id,@PathVariable long account)throws SQLException{rooms.remove(user(a),id,account);return Map.of("ok",true);}
    @PostMapping("/{id}/leave") public Map<String,Object> leave(Authentication a,@PathVariable long id)throws SQLException{rooms.leave(user(a),id);return Map.of("ok",true);}
    @PostMapping("/{id}/archive") public Map<String,Object> archive(Authentication a,@PathVariable long id)throws SQLException{rooms.archive(user(a),id);return Map.of("ok",true);}
    @PostMapping("/join-requests") public Map<String,Object> request(Authentication a,@RequestBody Join input)throws SQLException{rooms.requestJoin(user(a),input.code());return Map.of("ok",true);}
    @GetMapping("/join-requests") public Map<String,Object> myRequests(Authentication a)throws SQLException{return Map.of("ok",true,"joinStatuses",rooms.myRequests(user(a)));}
    @GetMapping("/{id}/requests") public Map<String,Object> requests(Authentication a,@PathVariable long id)throws SQLException{return Map.of("ok",true,"requests",rooms.requests(user(a),id));}
    @PostMapping("/{id}/requests/{account}") public Map<String,Object> review(Authentication a,@PathVariable long id,@PathVariable long account,@RequestBody Review input)throws SQLException{rooms.review(user(a),id,account,input.approve());return Map.of("ok",true);}
}
