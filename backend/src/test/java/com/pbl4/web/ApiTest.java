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
class ApiTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        String path=Files.createTempDirectory("pbl4-api-").resolve("api.db").toString();
        registry.add("app.database",()->path);
    }
    @Autowired MockMvc mvc;
    @Autowired MediaTicketService mediaTickets;
    @Autowired AuthService auth;
    @Test void mediaTicketsAreOneUseAndRespectLogout() throws Exception {
        String username="mediauser"+System.nanoTime();
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\""+username+"\",\"displayName\":\"Media User\",\"password\":\"Strong!23\"}"))
            .andExpect(status().isCreated());
        var login=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\""+username+"\",\"password\":\"Strong!23\"}"))
            .andExpect(status().isOk()).andReturn();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        String token=mapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
        var ticketReply=mvc.perform(post("/api/media/ticket").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andReturn();
        String ticket=mapper.readTree(ticketReply.getResponse().getContentAsString()).get("ticket").asText();
        org.junit.jupiter.api.Assertions.assertEquals(username,mediaTickets.consume(ticket).username());
        org.junit.jupiter.api.Assertions.assertNull(mediaTickets.consume(ticket));
        String revokedTicket=mediaTickets.issue(AuthService.tokenHash(token));
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNull(mediaTickets.consume(revokedTicket));
    }    @Test void apiRejectsUnauthenticatedAndRoleInjection() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"teacher\",\"displayName\":\"Teacher\",\"password\":\"Strong!23\",\"role\":\"TEACHER\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.role").exists());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{broken")).andExpect(status().isBadRequest());
    }
}
