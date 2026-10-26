package com.example.summary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "JWT_SECRET=test-only-jwt-secret-that-is-at-least-32-bytes",
        "spring.datasource.url=jdbc:h2:mem:summary_controller_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.kafka.listener.auto-startup=false"
})
@AutoConfigureMockMvc
class SummaryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SummaryService summaries;

    @BeforeEach
    void seedOwnerScopedEvents() {
        summaries.onExpenseEvent(new ExpenseEvent("UPSERT", "alice", 1L, "Food", "Food", 500,
                LocalDate.of(2025, 3, 1)));
        summaries.onExpenseEvent(new ExpenseEvent("UPSERT", "bob", 1L, "Shopping", "Shopping", 1000,
                LocalDate.of(2025, 3, 2)));
    }

    @Test
    void userCanOnlyReadTheirOwnCategoryAndMonthlyTotals() throws Exception {
        mockMvc.perform(get("/api/summaries/monthly?month=2025-03")
                        .with(jwt().jwt(token -> token.subject("alice"))
                                .authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(500.00))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.categories.Food").value(500.00));

        mockMvc.perform(get("/api/summaries/monthly?month=2025-03")
                        .with(jwt().jwt(token -> token.subject("bob"))
                                .authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1000.00))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.categories.Shopping").value(1000.00));

        mockMvc.perform(get("/api/summaries/categories?month=2025-03")
                        .with(jwt().jwt(token -> token.subject("alice"))
                                .authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].category").value("Food"));
    }

    @Test
    void summaryEndpointsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/summaries/categories"))
                .andExpect(status().isUnauthorized());
    }
}