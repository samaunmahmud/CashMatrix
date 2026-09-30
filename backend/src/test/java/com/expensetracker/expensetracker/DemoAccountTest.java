package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.TransactionRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Today" is 2026-09-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class DemoAccountTest {

    static final String DEMO_EMAIL = "sam.carter@cashmatrix.example";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;

    String token;

    @BeforeEach
    void openTheDemo() throws Exception {
        String body = mvc.perform(post("/api/auth/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEMO_EMAIL))
                .andExpect(jsonPath("$.fullName").value("Sam Carter"))
                .andExpect(jsonPath("$.demo").value(true))
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("token").asText();
    }

    @Test
    void theDemoComesWithAccountsHistoryBudgetsGoalsAndACalendar() throws Exception {
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].type", hasItem("credit")))
                .andExpect(jsonPath("$[0].currency").value("GBP"));
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(1500)))
                .andExpect(jsonPath("$[0].transactionDate").value(FixedClockTestConfig.TODAY));
        mvc.perform(get("/api/budgets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgets", hasSize(5)));
        mvc.perform(get("/api/goals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Holiday in Lisbon"))
                .andExpect(jsonPath("$[0].savedAmount").value(870.0));
        mvc.perform(get("/api/calendar/events").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
        mvc.perform(get("/api/insights").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/insights/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void subscriptionsNotYetOnTheCalendarAreSuggestedIncludingAYearlyOne() throws Exception {
        mvc.perform(get("/api/subscriptions/suggestions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Disney Plus")))
                .andExpect(jsonPath("$[*].name", hasItem("Amazon Prime")))
                .andExpect(jsonPath("$[?(@.name == 'Netflix')]").isEmpty());
    }

    @Test
    void openingTheDemoAgainReusesTheSameAccountAndData() throws Exception {
        User user = userRepository.findByEmail(DEMO_EMAIL).orElseThrow();
        long before = transactionRepository.findByBankAccountInOrderByTransactionDateDesc(bankAccountRepository.findByUser(user)).size();

        mvc.perform(post("/api/auth/demo")).andExpect(status().isOk());
        mvc.perform(post("/api/transactions/sync").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(bankAccountRepository.findByUser(user)).hasSize(3);
        assertThat(transactionRepository.findByBankAccountInOrderByTransactionDateDesc(bankAccountRepository.findByUser(user)))
                .hasSize((int) before);
    }

    @Test
    void theDemoIsReadOnly() throws Exception {
        mvc.perform(post("/api/budgets").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Pets\",\"monthlyLimit\":50}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.error").isNotEmpty());
        mvc.perform(delete("/api/goals/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/notifications").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/plaid/link-token").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/budgets").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.budgets", hasSize(5)));
    }

    @Test
    void nobodyCanSignUpAsOrLogInToTheDemoAccountWithAPassword() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", DEMO_EMAIL, "password", "password123", "fullName", "Impostor"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", DEMO_EMAIL, "password", "password123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherPeopleCanStillChangeTheirOwnData() throws Exception {
        String email = java.util.UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123", "fullName", "Real Person"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(false))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/budgets").header("Authorization", "Bearer " + json.readTree(body).get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Pets\",\"monthlyLimit\":50}"))
                .andExpect(status().isCreated());
    }
}
