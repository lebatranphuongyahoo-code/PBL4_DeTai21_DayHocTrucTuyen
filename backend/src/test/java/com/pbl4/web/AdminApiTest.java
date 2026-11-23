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
        for(String name:new String[]{"adminapi","teachapi","studentapi"})auth.register(new AuthService.Registration(name,name,"Strong!23",null));
        db.setRole("adminapi","ADMIN");
        var teacher=db.findAccount("teachapi");long id=rooms.create(teacher,"Admin API class").id();
        String admin=auth.login(new AuthService.Login("adminapi","Strong!23")).token();
        String student=auth.login(new AuthService.Login("studentapi","Strong!23")).token();
        mvc.perform(get("/api/admin/rooms").header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rooms[0].ownerUsername").value("teachapi"));
        mvc.perform(get("/api/admin/rooms").header("Authorization","Bearer "+student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/rooms/"+id).header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.room.members[0].id").exists());
        mvc.perform(post("/api/admin/rooms/"+id+"/archive").header("Authorization","Bearer "+admin))
            .andExpect(status().isOk());
        mvc.perform(get("/api/admin/rooms").header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rooms[0].status").value("ARCHIVED"));
        mvc.perform(get("/api/admin/rooms/"+id).header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.room.status").value("ARCHIVED"));
        mvc.perform(post("/api/admin/rooms/"+id+"/restore").header("Authorization","Bearer "+student))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rooms/"+id+"/restore").header("Authorization","Bearer "+admin))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertThrows(AuthService.Failure.class,()->rooms.mediaAccess(db.findAccount("adminapi"),id));
    }
}
