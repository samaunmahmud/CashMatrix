package com.expensetracker.expensetracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class CalendarApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String token;

    @BeforeEach
    void signUpFreshUser() throws Exception {
        token = signUp();
    }

    // --- creating and viewing -------------------------------------------------

    @Test
    void monthlySubscriptionAppearsOnEachMonthInTheRange() throws Exception {
        create(event("Netflix", "SUBSCRIPTION", "2026-09-25", "MONTHLY", 3, "9.99"));

        JsonNode entries = getJson("/api/calendar?from=2026-09-01&to=2026-11-30");

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).get("date").asText()).isEqualTo("2026-09-25");
        assertThat(entries.get(1).get("date").asText()).isEqualTo("2026-10-25");
        assertThat(entries.get(2).get("date").asText()).isEqualTo("2026-11-25");
        assertThat(entries.get(0).get("amount").decimalValue()).isEqualByComparingTo("9.99");
    }

    @Test
    void recurringItemWithOldStartDateWaitsForItsNextOccurrenceFromToday() throws Exception {
        // Started 20 Jan 2025, monthly: 20 Sep 2026 is an occurrence and "today".
        JsonNode created = create(event("Gym", "SUBSCRIPTION", "2025-01-20", "MONTHLY", 1, "30.00"));

        assertThat(created.get("nextDueDate").asText()).isEqualTo("2026-09-20");
    }

    @Test
    void entriesAreOrderedByDate() throws Exception {
        create(event("Zebra", "TASK", "2026-09-22", null, null, null));
        create(event("Alpha", "TASK", "2026-09-21", null, null, null));

        JsonNode entries = getJson("/api/calendar?from=2026-09-01&to=2026-09-30");

        assertThat(entries.get(0).get("title").asText()).isEqualTo("Alpha");
        assertThat(entries.get(1).get("title").asText()).isEqualTo("Zebra");
    }

    // --- completing -----------------------------------------------------------

    @Test
    void completingARecurringItemAdvancesToTheNextOccurrence() throws Exception {
        long id = create(event("Rent", "PAYMENT", "2026-09-25", "MONTHLY", 3, "800.00")).get("id").asLong();

        JsonNode after = postJson("/api/calendar/events/" + id + "/complete");

        assertThat(after.get("nextDueDate").asText()).isEqualTo("2026-10-25");
        assertThat(after.get("completed").asBoolean()).isFalse();

        JsonNode entries = getJson("/api/calendar?from=2026-09-01&to=2026-10-31");
        assertThat(entries.get(0).get("completed").asBoolean()).as("September is paid").isTrue();
        assertThat(entries.get(1).get("completed").asBoolean()).as("October is still due").isFalse();
    }

    @Test
    void completingAOneOffItemFinishesIt() throws Exception {
        long id = create(event("Renew passport", "TASK", "2026-09-30", null, null, null)).get("id").asLong();

        JsonNode after = postJson("/api/calendar/events/" + id + "/complete");

        assertThat(after.get("completed").asBoolean()).isTrue();
    }

    @Test
    void editingOnlyTheTitleKeepsProgressOfARepeatingItem() throws Exception {
        long id = create(event("Rent", "PAYMENT", "2026-09-25", "MONTHLY", 3, "800.00")).get("id").asLong();
        postJson("/api/calendar/events/" + id + "/complete"); // now waiting on 25 Oct

        JsonNode edited = putJson("/api/calendar/events/" + id, event("Rent (flat)", "PAYMENT", "2026-09-25", "MONTHLY", 3, "800.00"));

        assertThat(edited.get("title").asText()).isEqualTo("Rent (flat)");
        assertThat(edited.get("nextDueDate").asText()).isEqualTo("2026-10-25");
    }

    @Test
    void changingTheDateRestartsARepeatingItem() throws Exception {
        long id = create(event("Rent", "PAYMENT", "2026-09-25", "MONTHLY", 3, "800.00")).get("id").asLong();
        postJson("/api/calendar/events/" + id + "/complete");

        JsonNode edited = putJson("/api/calendar/events/" + id, event("Rent", "PAYMENT", "2026-09-28", "MONTHLY", 3, "800.00"));

        assertThat(edited.get("nextDueDate").asText()).isEqualTo("2026-09-28");
    }

    // --- validation -----------------------------------------------------------

    @Test
    void rejectsAnItemWithoutATitle() throws Exception {
        Map<String, Object> body = event("", "TASK", "2026-09-22", null, null, null);

        mvc.perform(auth(post("/api/calendar/events")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Title is required"));
    }

    @Test
    void rejectsUnknownTypeBadDateAndOversizedRange() throws Exception {
        Map<String, Object> badType = event("X", "HOLIDAY", "2026-09-22", null, null, null);
        mvc.perform(auth(post("/api/calendar/events")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(badType)))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(get("/api/calendar?from=not-a-date&to=2026-09-30"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/calendar?from=2026-09-01"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/calendar?from=2024-01-01&to=2026-09-30"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/calendar?from=2026-09-30&to=2026-09-01"))).andExpect(status().isBadRequest());
    }

    // --- privacy --------------------------------------------------------------

    @Test
    void usersCannotSeeOrChangeEachOthersItems() throws Exception {
        long id = create(event("Private", "TASK", "2026-09-22", null, null, null)).get("id").asLong();
        String otherToken = signUp();

        mvc.perform(post("/api/calendar/events/" + id + "/complete").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/calendar/events/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/calendar?from=2026-09-01&to=2026-09-30").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void endpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/calendar?from=2026-09-01&to=2026-09-30")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/notifications")).andExpect(status().is4xxClientError());
    }

    // --- reminders through the API ------------------------------------------------

    @Test
    void reminderAppearsOnceWhenTheDueDateIsInsideTheWindow() throws Exception {
        create(event("Council tax", "PAYMENT", "2026-09-22", null, 3, "150.00")); // due in 2 days

        JsonNode first = getJson("/api/notifications");
        JsonNode second = getJson("/api/notifications");

        assertThat(first).hasSize(1);
        assertThat(second).as("no duplicate on the second look").hasSize(1);
        assertThat(first.get(0).get("title").asText()).isEqualTo("Council tax due in 2 days");
        assertThat(first.get(0).get("message").asText()).contains("150.00");
        assertThat(first.get(0).get("read").asBoolean()).isFalse();
    }

    @Test
    void noReminderBeforeTheWindowOpens() throws Exception {
        create(event("Car tax", "PAYMENT", "2026-09-30", null, 3, "50.00")); // window opens 27 Sep

        assertThat(getJson("/api/notifications")).isEmpty();
    }

    @Test
    void notificationsCanBeMarkedRead() throws Exception {
        create(event("A", "PAYMENT", "2026-09-21", null, 2, null));
        create(event("B", "PAYMENT", "2026-09-21", null, 2, null));

        assertThat(getJson("/api/notifications/unread-count").get("count").asLong()).isEqualTo(2);

        long firstId = getJson("/api/notifications").get(0).get("id").asLong();
        mvc.perform(auth(post("/api/notifications/" + firstId + "/read"))).andExpect(status().isNoContent());
        assertThat(getJson("/api/notifications/unread-count").get("count").asLong()).isEqualTo(1);

        JsonNode all = postJson("/api/notifications/read-all");
        assertThat(all.get("updated").asInt()).isEqualTo(1);
        assertThat(getJson("/api/notifications/unread-count").get("count").asLong()).isZero();
    }

    @Test
    void deletingAnItemAlsoRemovesItsNotifications() throws Exception {
        long id = create(event("Gone soon", "PAYMENT", "2026-09-21", null, 2, null)).get("id").asLong();
        assertThat(getJson("/api/notifications")).hasSize(1);

        mvc.perform(auth(delete("/api/calendar/events/" + id))).andExpect(status().isNoContent());

        assertThat(getJson("/api/notifications")).isEmpty();
        assertThat(getJson("/api/calendar?from=2026-09-01&to=2026-09-30")).isEmpty();
    }

    // --- helpers --------------------------------------------------------------

    private String signUp() throws Exception {
        Map<String, Object> body = Map.of(
                "email", UUID.randomUUID() + "@example.com",
                "password", "password123",
                "fullName", "Test User");
        MvcResult result = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Map<String, Object> event(String title, String type, String startDate,
                                      String recurrence, Integer remindDaysBefore, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("type", type);
        body.put("startDate", startDate);
        if (recurrence != null) body.put("recurrence", recurrence);
        if (remindDaysBefore != null) body.put("remindDaysBefore", remindDaysBefore);
        if (amount != null) body.put("amount", amount);
        return body;
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        return read(mvc.perform(auth(post("/api/calendar/events")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode putJson(String url, Map<String, Object> body) throws Exception {
        return read(mvc.perform(auth(put(url)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn());
    }

    private JsonNode getJson(String url) throws Exception {
        return read(mvc.perform(auth(get(url))).andExpect(status().isOk()).andReturn());
    }

    private JsonNode postJson(String url) throws Exception {
        return read(mvc.perform(auth(post(url))).andExpect(status().isOk()).andReturn());
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
