package com.example.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "JWT_SECRET=test-only-jwt-secret-that-is-at-least-32-bytes",
        "spring.datasource.url=jdbc:h2:mem:lend_borrow_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class LendBorrowControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private UserDirectoryClient users;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM lend_borrow_requests");
        when(users.users(anyString())).thenReturn(List.of(
                new LendBorrowDtos.UserView("alice", "USER"),
                new LendBorrowDtos.UserView("bob", "USER"),
                new LendBorrowDtos.UserView("charlie", "USER")));
        when(users.users("Bearer alice-token")).thenReturn(List.of(
                new LendBorrowDtos.UserView("alice", "USER"),
                new LendBorrowDtos.UserView("bob", "USER"),
                new LendBorrowDtos.UserView("charlie", "USER")));
    }

    @Test
    void transactionIsCreatedOnlyAfterRecipientApprovalAndVisibleToBothUsers() throws Exception {
        String request = mockMvc.perform(post("/api/lend-borrow/requests")
                        .with(jwt().jwt(token -> token.subject("alice").tokenValue("alice-token"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"bob\",\"direction\":\"LEND\",\"amount\":\"125.50\",\"description\":\"Train ticket\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(request).get("id").asLong();

        mockMvc.perform(get("/api/lend-borrow/overview")
                        .with(jwt().jwt(token -> token.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalLent").value(0.00))
                .andExpect(jsonPath("$.summary.pendingSent").value(1));

        mockMvc.perform(post("/api/lend-borrow/requests/{id}/decision", id)
                        .with(jwt().jwt(token -> token.subject("charlie"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/lend-borrow/requests/{id}/decision", id)
                        .with(jwt().jwt(token -> token.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/lend-borrow/requests/{id}/decision", id)
                        .with(jwt().jwt(token -> token.subject("bob"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/lend-borrow/requests/{id}/decision", id)
                        .with(jwt().jwt(token -> token.subject("bob"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVE\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/lend-borrow/overview")
                        .with(jwt().jwt(token -> token.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalLent").value(125.50))
                .andExpect(jsonPath("$.summary.outstandingToReceive").value(125.50))
                .andExpect(jsonPath("$.confirmedHistory[0].id").value(id));

        mockMvc.perform(get("/api/lend-borrow/overview")
                        .with(jwt().jwt(token -> token.subject("bob"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalBorrowed").value(125.50))
                .andExpect(jsonPath("$.summary.outstandingToPay").value(125.50))
                .andExpect(jsonPath("$.confirmedHistory[0].id").value(id));
    }

    @Test
    void rejectedRequestDoesNotAffectTotalsAndSelfRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/lend-borrow/requests")
                        .with(jwt().jwt(token -> token.subject("alice").tokenValue("alice-token"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"bob\",\"direction\":\"BORROW\",\"amount\":\"20.00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/lend-borrow/requests")
                        .with(jwt().jwt(token -> token.subject("alice").tokenValue("alice-token"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"alice\",\"direction\":\"LEND\",\"amount\":\"5.00\"}"))
                .andExpect(status().isBadRequest());

        long id = jdbc.queryForObject("SELECT id FROM lend_borrow_requests", Long.class);
        mockMvc.perform(post("/api/lend-borrow/requests/{id}/decision", id)
                        .with(jwt().jwt(token -> token.subject("bob"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/lend-borrow/overview")
                        .with(jwt().jwt(token -> token.subject("alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalBorrowed").value(0.00))
                .andExpect(jsonPath("$.summary.pendingSent").value(0));
    }

    @Test
    void recipientDirectoryAndAmountValidationAreEnforced() throws Exception {
        mockMvc.perform(get("/api/lend-borrow/users")
                        .with(jwt().jwt(token -> token.subject("alice").tokenValue("alice-token"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        mockMvc.perform(post("/api/lend-borrow/requests")
                        .with(jwt().jwt(token -> token.subject("alice").tokenValue("alice-token"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"unknown\",\"direction\":\"LEND\",\"amount\":\"10.00\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/lend-borrow/requests")
                        .with(jwt().jwt(token -> token.subject("alice").tokenValue("alice-token"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"bob\",\"direction\":\"LEND\",\"amount\":\"10.001\"}"))
                .andExpect(status().isBadRequest());
    }
}