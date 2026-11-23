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
    public record Activity(String title, String prompt) {}
    public record Answer(String answer) {}
    public record ActivityReview(Integer score, String feedback) {}
    private final ClassroomService rooms;private final MediaWebSocketHandler media;
    public ClassroomController(ClassroomService rooms,MediaWebSocketHandler media){this.rooms=rooms;this.media=media;}
    private Database.Account user(Authentication a){return (Database.Account)a.getPrincipal();}
    @PostMapping public Map<String,Object> create(Authentication a,@RequestBody Create input)throws SQLException{return Map.of("ok",true,"room",rooms.create(user(a),input.name()));}
    @GetMapping("/{id}") public Map<String,Object> detail(Authentication a,@PathVariable long id)throws SQLException{return Map.of("ok",true,"room",rooms.detail(user(a),id));}
    @GetMapping("/{id}/meetings") public Map<String,Object> history(Authentication a,@PathVariable long id,@RequestParam(defaultValue="9223372036854775807") long before)throws SQLException{return Map.of("ok",true,"meetings",rooms.history(user(a),id,before));}
    @GetMapping("/{id}/messages") public Map<String,Object> messages(Authentication a,@PathVariable long id,@RequestParam(defaultValue="-1") long meeting,@RequestParam(defaultValue="9223372036854775807") long before)throws SQLException{return Map.of("ok",true,"messages",rooms.messages(user(a),id,meeting,before));}
    @GetMapping("/{id}/activities") public Map<String,Object> activities(Authentication a,@PathVariable long id)throws SQLException{return Map.of("ok",true,"activities",rooms.activities(user(a),id));}
    @PostMapping("/{id}/activities") public Map<String,Object> createActivity(Authentication a,@PathVariable long id,@RequestBody Activity input)throws SQLException{return Map.of("ok",true,"activity",rooms.createActivity(user(a),id,input.title(),input.prompt()));}
    @PostMapping("/{id}/activities/{activity}/submissions") public Map<String,Object> submitActivity(Authentication a,@PathVariable long id,@PathVariable long activity,@RequestBody Answer input)throws SQLException{return Map.of("ok",true,"submission",rooms.submitActivity(user(a),id,activity,input.answer()));}
    @GetMapping("/{id}/activities/{activity}/submissions") public Map<String,Object> activitySubmissions(Authentication a,@PathVariable long id,@PathVariable long activity)throws SQLException{return Map.of("ok",true,"submissions",rooms.activitySubmissions(user(a),id,activity));}
    @PostMapping("/{id}/activities/{activity}/submissions/{account}/review") public Map<String,Object> reviewActivity(Authentication a,@PathVariable long id,@PathVariable long activity,@PathVariable long account,@RequestBody ActivityReview input)throws SQLException{return Map.of("ok",true,"submission",rooms.reviewActivity(user(a),id,activity,account,input.score(),input.feedback()));}
    @PostMapping("/{id}/members") public Map<String,Object> add(Authentication a,@PathVariable long id,@RequestBody Add input)throws SQLException{rooms.add(user(a),id,input.username());return Map.of("ok",true);}
    @DeleteMapping("/{id}/members/{account}") public Map<String,Object> remove(Authentication a,@PathVariable long id,@PathVariable long account)throws SQLException{rooms.remove(user(a),id,account);return Map.of("ok",true);}
    @PostMapping("/{id}/leave") public Map<String,Object> leave(Authentication a,@PathVariable long id)throws SQLException{rooms.leave(user(a),id);return Map.of("ok",true);}
    @PostMapping("/{id}/meeting/start") public Map<String,Object> start(Authentication a,@PathVariable long id)throws SQLException{synchronized(media){rooms.setMeeting(user(a),id,true);}return Map.of("ok",true);}
    @PostMapping("/{id}/meeting/end") public Map<String,Object> end(Authentication a,@PathVariable long id)throws SQLException{synchronized(media){rooms.setMeeting(user(a),id,false);media.endMeeting(id);}return Map.of("ok",true);}
    @PostMapping("/{id}/archive") public Map<String,Object> archive(Authentication a,@PathVariable long id)throws SQLException{synchronized(media){rooms.archive(user(a),id);media.endMeeting(id);}return Map.of("ok",true);}
    @PostMapping("/join-requests") public Map<String,Object> request(Authentication a,@RequestBody Join input)throws SQLException{rooms.requestJoin(user(a),input.code());return Map.of("ok",true);}
    @GetMapping("/join-requests") public Map<String,Object> myRequests(Authentication a)throws SQLException{return Map.of("ok",true,"joinStatuses",rooms.myRequests(user(a)));}
    @GetMapping("/{id}/requests") public Map<String,Object> requests(Authentication a,@PathVariable long id)throws SQLException{return Map.of("ok",true,"requests",rooms.requests(user(a),id));}
    @PostMapping("/{id}/requests/{account}") public Map<String,Object> review(Authentication a,@PathVariable long id,@PathVariable long account,@RequestBody Review input)throws SQLException{rooms.review(user(a),id,account,input.approve());return Map.of("ok",true);}
}
