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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        jdbc.update("DELETE FROM lend_borrow_repayments");
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
                .andExpect(jsonPath("$.summary.pendingSent").value(0))
                .andExpect(jsonPath("$.outstandingByPerson").isEmpty());
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

    @Test
    void partialAndFullRepaymentsArePersistedAndKeepLifetimeTotals() throws Exception {
        long id = approvedTransaction("alice", "bob", "alice", "bob", "LEND", "100.00");

        repayment(id, "bob", "25.00", "repay-1").andExpect(status().isCreated());
        repayment(id, "bob", "25.00", "repay-1").andExpect(status().isOk());
        repayment(id, "bob", "15.00", "repay-2").andExpect(status().isCreated());

        mockMvc.perform(get("/api/lend-borrow/overview")
                        .with(jwt().jwt(token -> token.subject("alice")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalLent").value(100.00))
                .andExpect(jsonPath("$.summary.outstandingToReceive").value(60.00))
                .andExpect(jsonPath("$.confirmedHistory[0].totalRepaid").value(40.00))
                .andExpect(jsonPath("$.confirmedHistory[0].outstandingBalance").value(60.00))
                .andExpect(jsonPath("$.confirmedHistory[0].repaymentStatus").value("PARTIALLY_REPAID"))
                .andExpect(jsonPath("$.confirmedHistory[0].repaymentHistory.length()").value(2))
                .andExpect(jsonPath("$.outstandingByPerson[0].username").value("bob"))
                .andExpect(jsonPath("$.outstandingByPerson[0].outstandingToReceive").value(60.00))
                .andExpect(jsonPath("$.outstandingByPerson[0].outstandingToPay").value(0.00));

        repayment(id, "bob", "60.00", "repay-3").andExpect(status().isCreated());
        mockMvc.perform(get("/api/lend-borrow/overview")
                        .with(jwt().jwt(token -> token.subject("alice")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalLent").value(100.00))
                .andExpect(jsonPath("$.summary.outstandingToReceive").value(0.00))
                .andExpect(jsonPath("$.confirmedHistory[0].repaymentStatus").value("FULLY_REPAID"))
                .andExpect(jsonPath("$.outstandingByPerson").isEmpty());

        repayment(id, "bob", "0.01", "after-full").andExpect(status().isConflict());
        mockMvc.perform(get("/api/lend-borrow/requests/{id}", id)
                        .with(jwt().jwt(token -> token.subject("alice")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingBalance").value(0.00))
                .andExpect(jsonPath("$.repaymentHistory.length()").value(3));
    }

    @Test
    void repaymentRejectsInvalidAmountsAndOnlyBorrowerCanRepay() throws Exception {
        long id = approvedTransaction("alice", "bob", "alice", "bob", "LEND", "100.00");
        repayment(id, "alice", "10.00", "lender-attempt").andExpect(status().isForbidden());
        repayment(id, "bob", "0.00", "zero").andExpect(status().isBadRequest());
        repayment(id, "bob", "-1.00", "negative").andExpect(status().isBadRequest());
        repayment(id, "bob", "100.01", "excessive").andExpect(status().isBadRequest());
        repayment(id, "bob", "10.001", "precision").andExpect(status().isBadRequest());
        repayment(id, "bob", "10.00", "").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/lend-borrow/requests/{id}", id)
                        .with(jwt().jwt(token -> token.subject("charlie")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbc.queryForObject("SELECT COUNT(*) FROM lend_borrow_repayments", Integer.class));
    }

    @Test
    void samePersonKeepsLentAndBorrowedOutstandingSeparateAndAggregatesTransactions() throws Exception {
        approvedTransaction("alice", "bob", "alice", "bob", "LEND", "100.00");
        approvedTransaction("alice", "bob", "alice", "bob", "LEND", "50.00");
        long borrowed = approvedTransaction("alice", "bob", "bob", "alice", "BORROW", "80.00");
        repayment(borrowed, "alice", "30.00", "borrow-repay").andExpect(status().isCreated());

        mockMvc.perform(get("/api/lend-borrow/overview")
                        .with(jwt().jwt(token -> token.subject("alice")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalLent").value(150.00))
                .andExpect(jsonPath("$.summary.totalBorrowed").value(80.00))
                .andExpect(jsonPath("$.summary.outstandingToReceive").value(150.00))
                .andExpect(jsonPath("$.summary.outstandingToPay").value(50.00))
                .andExpect(jsonPath("$.outstandingByPerson.length()").value(1))
                .andExpect(jsonPath("$.outstandingByPerson[0].username").value("bob"))
                .andExpect(jsonPath("$.outstandingByPerson[0].outstandingToReceive").value(150.00))
                .andExpect(jsonPath("$.outstandingByPerson[0].outstandingToPay").value(50.00));

        mockMvc.perform(get("/api/lend-borrow/overview")
                        .with(jwt().jwt(token -> token.subject("bob")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalLent").value(80.00))
                .andExpect(jsonPath("$.summary.totalBorrowed").value(150.00))
                .andExpect(jsonPath("$.summary.outstandingToReceive").value(50.00))
                .andExpect(jsonPath("$.summary.outstandingToPay").value(150.00))
                .andExpect(jsonPath("$.outstandingByPerson[0].outstandingToReceive").value(50.00))
                .andExpect(jsonPath("$.outstandingByPerson[0].outstandingToPay").value(150.00));
    }

    @Test
    void concurrentRepaymentsCannotExceedOutstandingBalance() throws Exception {
        long id = approvedTransaction("alice", "bob", "alice", "bob", "LEND", "100.00");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = CompletableFuture.supplyAsync(() -> performRepayment(id, "60.00", "race-1"), executor);
            var second = CompletableFuture.supplyAsync(() -> performRepayment(id, "60.00", "race-2"), executor);
            int firstStatus = first.get(15, TimeUnit.SECONDS);
            int secondStatus = second.get(15, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertEquals(1,
                    List.of(firstStatus, secondStatus).stream().filter(code -> code == 201).count());
            org.junit.jupiter.api.Assertions.assertTrue(firstStatus == 400 || firstStatus == 201);
            org.junit.jupiter.api.Assertions.assertTrue(secondStatus == 400 || secondStatus == 201);
            org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("60.00"),
                    jdbc.queryForObject("SELECT SUM(amount) FROM lend_borrow_repayments WHERE transaction_id = ?", BigDecimal.class, id));
            mockMvc.perform(get("/api/lend-borrow/overview")
                            .with(jwt().jwt(token -> token.subject("alice")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.outstandingToReceive").value(40.00));
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.ResultActions repayment(long id, String username,
                                                                          String amount, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/lend-borrow/requests/{id}/repayments", id)
                .with(jwt().jwt(token -> token.subject(username)).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"" + amount + "\"}"));
    }

    private int performRepayment(long id, String amount, String idempotencyKey) {
        try {
            return repayment(id, "bob", amount, idempotencyKey).andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long approvedTransaction(String requester, String recipient, String lender, String borrower,
                                     String direction, String amount) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO lend_borrow_requests (requester_username, recipient_username, lender_username, borrower_username, direction, amount, status, decided_at) VALUES (?, ?, ?, ?, ?, ?, 'APPROVED', CURRENT_TIMESTAMP(6))", new String[]{"id"});
            statement.setString(1, requester);
            statement.setString(2, recipient);
            statement.setString(3, lender);
            statement.setString(4, borrower);
            statement.setString(5, direction);
            statement.setBigDecimal(6, new BigDecimal(amount));
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
