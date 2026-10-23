package com.example.expensetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void expenseCanBeCreatedListedUpdatedAndDeleted() throws Exception {
        String request = """
                {"description":"Weekly groceries","category":"Food","amount":84.50,"date":"2020-10-20"}
                """;

        String response = mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.description").value("Weekly groceries"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put("/api/expenses/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Market groceries\",\"category\":\"Food\",\"amount\":92.25,\"date\":\"2020-10-21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Market groceries"))
                .andExpect(jsonPath("$.amount").value(92.25));

        mockMvc.perform(delete("/api/expenses/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/expenses/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidExpenseIsRejected() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\",\"category\":\"Food\",\"amount\":0,\"date\":\"2020-10-20\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

        @Test
        void futureExpenseDateIsRejected() throws Exception {
                String tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).toString();

                mockMvc.perform(post("/api/expenses")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"description\":\"Future expense\",\"category\":\"Other\",\"amount\":10,\"date\":\"" + tomorrow + "\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }
}