package com.pbl4.web;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AdminApiTest {
    private static String path;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        path=Files.createTempDirectory("pbl4-admin-api-").resolve("api.db").toString();
        registry.add("app.database",()->path);
    }
    @Autowired MockMvc mvc;
    @Autowired Database db;
    @Autowired AuthService auth;
    @Autowired ClassroomService rooms;

    @Test void onlyAdminCanUseAdminRoutesAndAdminStillNeedsMembershipForMedia() throws Exception {
        for(String name:new String[]{"adminapi","teachapi","studentapi","promoteapi"})auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        db.setRole("adminapi","ADMIN");
        var teacher=TestAccounts.asTeacher(db,"teachapi");long id=rooms.create(teacher,"Admin API class").id();
        String admin=auth.login(new AuthService.Login("adminapi","Strong!23")).token();
        String teacherToken=auth.login(new AuthService.Login("teachapi","Strong!23")).token();
        String student=auth.login(new AuthService.Login("studentapi","Strong!23")).token();
        String teacherHeader="Bearer "+teacherToken,adminHeader="Bearer "+admin,studentHeader="Bearer "+student;

        mvc.perform(post("/api/rooms").header("Authorization",studentHeader).contentType("application/json").content("{\"name\":\"Student room\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms").header("Authorization",adminHeader).contentType("application/json").content("{\"name\":\"Unassigned admin room\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms").header("Authorization",teacherHeader).contentType("application/json").content("{\"name\":\"Teacher room\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/admin/rooms").header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rooms[0].ownerUsername").value("teachapi"));
        mvc.perform(get("/api/admin/rooms").header("Authorization",studentHeader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/rooms/"+id).header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.room.members[0].id").exists());
        mvc.perform(post("/api/admin/rooms/"+id+"/archive").header("Authorization","Bearer "+admin))
            .andExpect(status().isOk());
        mvc.perform(get("/api/admin/rooms").header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rooms[0].status").value("ARCHIVED"));
        mvc.perform(get("/api/admin/rooms/"+id).header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.room.status").value("ARCHIVED"));
        mvc.perform(post("/api/admin/rooms/"+id+"/restore").header("Authorization",studentHeader))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rooms/"+id+"/restore").header("Authorization","Bearer "+admin))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(db.findAccount("adminapi"),id));

        mvc.perform(get("/api/admin/rooms").header("Authorization",teacherHeader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/rooms/"+id).header("Authorization",teacherHeader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rooms/"+id+"/archive").header("Authorization",teacherHeader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rooms/"+id+"/restore").header("Authorization",teacherHeader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rooms/"+id+"/owner").header("Authorization",teacherHeader).contentType("application/json").content("{\"username\":\"teachapi\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rooms/"+id+"/members/999/transfer").header("Authorization",teacherHeader).contentType("application/json").content("{\"targetRoomId\":999}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization",teacherHeader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rooms").header("Authorization",teacherHeader).contentType("application/json")
                .content("{\"name\":\"Teacher cannot create as admin\",\"teacherUsername\":\"teachapi\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/users").header("Authorization",adminHeader))
            .andExpect(status().isOk()).andExpect(jsonPath("$.users[?(@.username=='studentapi')].role").value("STUDENT"));
        long promoteId=db.findAccount("promoteapi").id();
        mvc.perform(post("/api/admin/users/"+promoteId+"/teacher").header("Authorization",teacherHeader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/"+promoteId+"/teacher").header("Authorization",adminHeader)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("TEACHER",db.findAccount("promoteapi").role());
        mvc.perform(delete("/api/admin/users/"+promoteId+"/teacher").header("Authorization",teacherHeader)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/"+promoteId+"/teacher").header("Authorization",adminHeader)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("STUDENT",db.findAccount("promoteapi").role());
        mvc.perform(delete("/api/admin/users/"+promoteId+"/teacher").header("Authorization",adminHeader)).andExpect(status().isConflict());

        var adminCreated=mvc.perform(post("/api/admin/rooms").header("Authorization",adminHeader).contentType("application/json")
                .content("{\"name\":\"Admin assigned room\",\"teacherUsername\":\"teachapi\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roomId").isNumber()).andReturn();
        long assignedRoomId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(adminCreated.getResponse().getContentAsString()).get("roomId").asLong();
        mvc.perform(get("/api/admin/rooms/"+assignedRoomId).header("Authorization",adminHeader))
            .andExpect(status().isOk()).andExpect(jsonPath("$.room.ownerUsername").value("teachapi"));
        mvc.perform(delete("/api/admin/users/"+db.findAccount("teachapi").id()+"/teacher").header("Authorization",adminHeader))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/rooms").header("Authorization",adminHeader).contentType("application/json")
                .content("{\"name\":\"Bad owner room\",\"teacherUsername\":\"studentapi\"}"))
            .andExpect(status().isBadRequest());
    }
}
