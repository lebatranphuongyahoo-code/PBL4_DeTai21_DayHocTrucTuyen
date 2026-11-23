package com.pbl4.web;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record CreateRoom(String name,String teacherUsername) {}
    public record Owner(String username) {}
    public record Move(long targetRoomId) {}
    private final ClassroomService rooms;
    private final MediaWebSocketHandler media;
    public AdminController(ClassroomService rooms,MediaWebSocketHandler media){this.rooms=rooms;this.media=media;}
    private Database.Account user(Authentication a){return (Database.Account)a.getPrincipal();}
    @GetMapping("/rooms") public Map<String,Object> rooms(Authentication a)throws SQLException{return Map.of("ok",true,"rooms",rooms.adminClassrooms(user(a)));}
    @PostMapping("/rooms") public Map<String,Object> createRoom(Authentication a,@RequestBody CreateRoom input)throws SQLException{return Map.of("ok",true,"roomId",rooms.createForTeacher(user(a),input.name(),input.teacherUsername()));}
    @GetMapping("/rooms/{id}") public Map<String,Object> detail(Authentication a,@PathVariable long id)throws SQLException{return Map.of("ok",true,"room",rooms.adminDetail(user(a),id));}
    @PostMapping("/rooms/{id}/archive") public Map<String,Object> archive(Authentication a,@PathVariable long id)throws SQLException{ synchronized(media){rooms.archiveAsAdmin(user(a),id);media.endMeeting(id);}return Map.of("ok",true); }
    @PostMapping("/rooms/{id}/restore") public Map<String,Object> restore(Authentication a,@PathVariable long id)throws SQLException{rooms.restore(user(a),id);return Map.of("ok",true);}
    @PostMapping("/rooms/{id}/owner") public Map<String,Object> owner(Authentication a,@PathVariable long id,@RequestBody Owner input)throws SQLException{ synchronized(media){if(rooms.assignOwner(user(a),id,input.username()))media.endMeeting(id);}return Map.of("ok",true); }
    @PostMapping("/rooms/{source}/members/{account}/transfer") public Map<String,Object> transfer(Authentication a,@PathVariable long source,@PathVariable long account,@RequestBody Move input)throws SQLException{ synchronized(media){String username=rooms.transferMember(user(a),source,account,input.targetRoomId());media.disconnectMember(source,username);}return Map.of("ok",true); }
    @GetMapping("/users") public Map<String,Object> users(Authentication a)throws SQLException{return Map.of("ok",true,"users",rooms.adminUsers(user(a)));}
    @PostMapping("/users/{account}/teacher") public Map<String,Object> promoteTeacher(Authentication a,@PathVariable long account)throws SQLException{rooms.promoteStudentToTeacher(user(a),account);return Map.of("ok",true);}
}
