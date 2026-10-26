package com.example.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "JWT_SECRET=test-only-jwt-secret-that-is-at-least-32-bytes",
        "ADMIN_USERNAME=test-admin",
        "ADMIN_PASSWORD=test-admin-password"
})
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void usersCanRegisterAndLoginButOnlyAdminCanListUsers() throws Exception {
        String username = "test-user-" + System.nanoTime();
        String credentials = objectMapper.writeValueAsString(new AuthRequests.Credentials(username, "user-password-123"));
        String userToken = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        String userAccessToken = objectMapper.readTree(userToken).get("accessToken").asText();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userAccessToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + userAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        String adminCredentials = objectMapper.writeValueAsString(
                new AuthRequests.Credentials("test-admin", "test-admin-password"));
        String adminToken = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminCredentials))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String adminAccessToken = objectMapper.readTree(adminToken).get("accessToken").asText();

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/users/{username}", username)
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isNoContent());
    }
}