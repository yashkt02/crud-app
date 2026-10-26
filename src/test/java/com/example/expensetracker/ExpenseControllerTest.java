package com.example.expensetracker;

import com.example.expensetracker.service.ExpenseEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(properties = "JWT_SECRET=test-only-jwt-secret-that-is-at-least-32-bytes")
@AutoConfigureMockMvc
class ExpenseControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

        @MockBean
        private ExpenseEventPublisher eventPublisher;

        @Test
    void expenseCanBeCreatedListedUpdatedAndDeleted() throws Exception {
        String request = """
                {"description":"Weekly groceries","category":"Food","amount":84.50,"date":"2020-10-20"}
                """;

        String response = mockMvc.perform(post("/api/expenses").with(user("owner-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ownerId").value("owner-a"))
                .andExpect(jsonPath("$.description").value("Weekly groceries"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/expenses").with(user("owner-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put("/api/expenses/{id}", id).with(user("owner-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Market groceries\",\"category\":\"Food\",\"amount\":92.25,\"date\":\"2020-10-21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Market groceries"))
                .andExpect(jsonPath("$.amount").value(92.25));

        mockMvc.perform(delete("/api/expenses/{id}", id).with(user("owner-a")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/expenses/{id}", id).with(user("owner-a")))
                .andExpect(status().isNotFound());
    }

        @Test
    void invalidExpenseIsRejected() throws Exception {
                mockMvc.perform(post("/api/expenses").with(user("invalid-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\",\"category\":\"Food\",\"amount\":0,\"date\":\"2020-10-20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

                @Test
            void futureExpenseDateIsRejected() throws Exception {
                String tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).toString();

                mockMvc.perform(post("/api/expenses").with(user("future-user"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\":\"Future expense\",\"category\":\"Other\",\"amount\":10,\"date\":\"" + tomorrow + "\"}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.error").exists());
            }

            @Test
            void expenseCrudRequiresAuthentication() throws Exception {
                mockMvc.perform(get("/api/expenses"))
                        .andExpect(status().isUnauthorized());
            }

        private static org.springframework.test.web.servlet.request.RequestPostProcessor user(String subject) {
                return jwt().jwt(token -> token.subject(subject))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }

            @Test
            void usersCanOnlyAccessTheirOwnExpenses() throws Exception {
                String aliceResponse = mockMvc.perform(post("/api/expenses").with(user("alice"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\":\"Alice food\",\"category\":\"Food\",\"amount\":500,\"date\":\"2025-03-01\",\"ownerId\":\"bob\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.ownerId").value("alice"))
                        .andReturn().getResponse().getContentAsString();
                long aliceId = objectMapper.readTree(aliceResponse).get("id").asLong();

                String bobResponse = mockMvc.perform(post("/api/expenses").with(user("bob"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\":\"Bob travel\",\"category\":\"Travel\",\"amount\":1000,\"date\":\"2025-03-02\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.ownerId").value("bob"))
                        .andReturn().getResponse().getContentAsString();
                long bobId = objectMapper.readTree(bobResponse).get("id").asLong();

                mockMvc.perform(get("/api/expenses").with(user("alice")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", hasSize(1)))
                        .andExpect(jsonPath("$[0].amount").value(500));
                mockMvc.perform(get("/api/expenses").with(user("bob")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", hasSize(1)))
                        .andExpect(jsonPath("$[0].amount").value(1000));

                mockMvc.perform(get("/api/expenses/{id}", aliceId).with(user("bob")))
                        .andExpect(status().isNotFound());
                mockMvc.perform(put("/api/expenses/{id}", aliceId).with(user("bob"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\":\"Hijack\",\"category\":\"Other\",\"amount\":1,\"date\":\"2025-03-03\"}"))
                        .andExpect(status().isNotFound());
                mockMvc.perform(delete("/api/expenses/{id}", aliceId).with(user("bob")))
                        .andExpect(status().isNotFound());

                mockMvc.perform(put("/api/expenses/{id}", aliceId).with(user("alice"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\":\"Alice updated\",\"category\":\"Food\",\"amount\":700,\"date\":\"2025-03-01\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.amount").value(700));
                mockMvc.perform(get("/api/expenses/{id}", bobId).with(user("bob")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.amount").value(1000));

                mockMvc.perform(delete("/api/expenses/{id}", aliceId).with(user("alice")))
                        .andExpect(status().isNoContent());
                mockMvc.perform(get("/api/expenses").with(user("bob")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", hasSize(1)));
                mockMvc.perform(delete("/api/expenses/{id}", bobId).with(user("bob")))
                        .andExpect(status().isNoContent());
            }
}