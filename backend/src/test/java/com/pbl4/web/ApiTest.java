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

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        String path=Files.createTempDirectory("pbl4-api-").resolve("api.db").toString();
        registry.add("app.database",()->path);
    }
    @Autowired MockMvc mvc;
    @Test void apiRejectsUnauthenticatedAndRoleInjection() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"teacher\",\"displayName\":\"Teacher\",\"password\":\"Strong!23\",\"role\":\"TEACHER\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.role").exists());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{broken")).andExpect(status().isBadRequest());
    }
}
