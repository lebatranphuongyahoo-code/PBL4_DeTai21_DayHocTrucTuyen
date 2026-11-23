package com.pbl4.web;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ActivityApiTest {
    private static final String path;
    static {
        try { path=Files.createTempDirectory("pbl4-activity-api-").resolve("api.db").toString(); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("app.database",()->path); }
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired ClassroomService rooms;
    @Autowired Database db;

    @Test void activityEndpointsEnforceClassRolesAndReturnFeedback() throws Exception {
        String suffix=Long.toString(System.nanoTime());
        String teacherName="teach"+suffix,studentName="stud"+suffix,outsideName="out"+suffix;
        for(String name:new String[]{teacherName,studentName,outsideName})auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        TestAccounts.asTeacher(db,teacherName);
        var teacher=auth.login(new AuthService.Login(teacherName,"Strong!23"));
        var student=auth.login(new AuthService.Login(studentName,"Strong!23"));
        var outside=auth.login(new AuthService.Login(outsideName,"Strong!23"));
        var teacherAccount=db.findAccount(teacherName);var studentAccount=db.findAccount(studentName);
        long roomId=rooms.create(teacherAccount,"API Activity").id();rooms.add(teacherAccount,roomId,studentName);
        String teacherHeader="Bearer "+teacher.token(), studentHeader="Bearer "+student.token(), outsideHeader="Bearer "+outside.token();

        mvc.perform(get("/api/rooms/{room}/activities",roomId).header("Authorization",teacherHeader))
            .andExpect(status().isOk()).andExpect(jsonPath("$.activities").isEmpty());
        mvc.perform(post("/api/rooms/{room}/activities",roomId).header("Authorization",teacherHeader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Reading\",\"prompt\":\"Explain the main idea\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.activity.id").isNumber());
        long activityId=rooms.activities(teacherAccount,roomId).getFirst().id();
        mvc.perform(post("/api/rooms/{room}/activities/{activity}/submissions",roomId,activityId).header("Authorization",studentHeader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"My answer\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.submission.answer").value("My answer"));
        mvc.perform(get("/api/rooms/{room}/activities/{activity}/submissions",roomId,activityId).header("Authorization",teacherHeader))
            .andExpect(status().isOk()).andExpect(jsonPath("$.submissions[0].answer").value("My answer"));
        mvc.perform(post("/api/rooms/{room}/activities/{activity}/submissions/{account}/review",roomId,activityId,studentAccount.id())
                .header("Authorization",teacherHeader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":9,\"feedback\":\"Clear explanation\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.submission.score").value(9));
        mvc.perform(get("/api/rooms/{room}/activities",roomId).header("Authorization",studentHeader))
            .andExpect(status().isOk()).andExpect(jsonPath("$.activities[0].submission.feedback").value("Clear explanation"));
        mvc.perform(get("/api/rooms/{room}/activities",roomId).header("Authorization",outsideHeader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms/{room}/activities",roomId).header("Authorization",studentHeader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"No\",\"prompt\":\"No\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms/{room}/activities/{activity}/submissions",roomId,activityId).header("Authorization",studentHeader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms/{room}/activities/{activity}/submissions/{account}/review",roomId,activityId,studentAccount.id())
                .header("Authorization",studentHeader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"score\":10,\"feedback\":\"No\"}")).andExpect(status().isForbidden());
    }
}
