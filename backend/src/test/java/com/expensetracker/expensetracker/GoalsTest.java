package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.GoalContributionRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Today" is 2026-09-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class GoalsTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired GoalContributionRepository contributionRepository;

    User user;

    @BeforeEach
    void seed() {
        user = newUser();
    }

    @Test
    void aNewGoalSaysHowMuchToSaveEachMonth() throws Exception {
        // September to December counts as four months to save in.
        goal("{\"name\":\"  Christmas   presents \",\"emoji\":\"🎁\",\"targetAmount\":400,\"targetDate\":\"2026-12-15\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Christmas presents"))
                .andExpect(jsonPath("$.emoji").value("🎁"))
                .andExpect(jsonPath("$.savedAmount").value(0))
                .andExpect(jsonPath("$.remaining").value(400))
                .andExpect(jsonPath("$.percentSaved").value(0))
                .andExpect(jsonPath("$.monthlyNeeded").value(100.0))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        goal("{\"name\":\"Rainy day\",\"targetAmount\":1000}")
                .andExpect(jsonPath("$.monthlyNeeded").doesNotExist())
                .andExpect(jsonPath("$.targetDate").doesNotExist());

        mvc.perform(get("/api/goals").header("Authorization", auth()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Christmas presents"))
                .andExpect(jsonPath("$[1].name").value("Rainy day"));
    }

    @Test
    void addingAndTakingOutMoneyTracksProgressAndHistory() throws Exception {
        long id = createdId(goal("{\"name\":\"Holiday\",\"targetAmount\":1200,\"targetDate\":\"2027-02-01\"}"));

        contribute(id, "250").andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(250))
                .andExpect(jsonPath("$.percentSaved").value(20))
                // 950 left over Sep..Feb = 6 months, rounded up to the penny
                .andExpect(jsonPath("$.monthlyNeeded").value(158.34));
        contribute(id, "-50").andExpect(status().isOk())
                .andExpect(jsonPath("$.savedAmount").value(200))
                .andExpect(jsonPath("$.recent.length()").value(2))
                .andExpect(jsonPath("$.recent[0].amount").value(-50))
                .andExpect(jsonPath("$.recent[0].madeOn").value(FixedClockTestConfig.TODAY));

        contribute(id, "-500").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You can take out at most £200.00"));
        contribute(id, "0").andExpect(status().isBadRequest());
        contribute(id, "1.234").andExpect(status().isBadRequest());
    }

    @Test
    void reachingAGoalSaysWellDoneOnce() throws Exception {
        long id = createdId(goal("{\"name\":\"New bike\",\"targetAmount\":300}"));

        contribute(id, "300").andExpect(jsonPath("$.status").value("REACHED"))
                .andExpect(jsonPath("$.percentSaved").value(100))
                .andExpect(jsonPath("$.remaining").value(0));
        contribute(id, "-10").andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        contribute(id, "50").andExpect(jsonPath("$.status").value("REACHED"))
                .andExpect(jsonPath("$.percentSaved").value(100));

        List<Notification> alerts = notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user);
        assertThat(alerts).singleElement().satisfies(n -> {
            assertThat(n.getTitle()).isEqualTo("Goal reached: New bike");
            assertThat(n.getMessage()).isEqualTo("You've saved £300.00 for New bike. Well done!");
            assertThat(n.getLink()).isEqualTo("/goals");
            assertThat(n.isEmailSent()).as("they are in the app").isTrue();
        });
    }

    @Test
    void loweringTheTargetBelowWhatIsSavedAlsoCountsAsReached() throws Exception {
        long id = createdId(goal("{\"name\":\"Laptop\",\"targetAmount\":900}"));
        contribute(id, "600");

        mvc.perform(put("/api/goals/" + id).header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Laptop\",\"targetAmount\":550,\"targetDate\":\"2026-08-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REACHED"));
        assertThat(notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user)).hasSize(1);
    }

    @Test
    void aGoalPastItsDateIsFlagged() throws Exception {
        goal("{\"name\":\"Summer\",\"targetAmount\":500,\"targetDate\":\"2026-08-31\"}")
                .andExpect(jsonPath("$.status").value("PAST_DATE"))
                .andExpect(jsonPath("$.monthlyNeeded").doesNotExist());
    }

    @Test
    void aGoalDueThisMonthNeedsEverythingNow() throws Exception {
        goal("{\"name\":\"Rent top-up\",\"targetAmount\":120,\"targetDate\":\"2026-09-30\"}")
                .andExpect(jsonPath("$.monthlyNeeded").value(120.0));
    }

    @Test
    void deletingAGoalRemovesItsHistory() throws Exception {
        long id = createdId(goal("{\"name\":\"Car\",\"targetAmount\":5000}"));
        contribute(id, "100");

        mvc.perform(delete("/api/goals/" + id).header("Authorization", auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/goals").header("Authorization", auth())).andExpect(jsonPath("$").isEmpty());
        assertThat(contributionRepository.findAll()).noneMatch(c -> c.getGoal().getId().equals(id));
    }

    @Test
    void validatesGoals() throws Exception {
        goal("{\"name\":\"\",\"targetAmount\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Name is required"))
                .andExpect(jsonPath("$.targetAmount").value("Target must be at least 1"));
    }

    @Test
    void goalsArePrivate() throws Exception {
        long id = createdId(goal("{\"name\":\"Secret\",\"targetAmount\":100}"));
        User other = newUser();
        String otherAuth = "Bearer " + jwtService.generateToken(new UserPrincipal(other));

        mvc.perform(get("/api/goals").header("Authorization", otherAuth)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/goals/" + id + "/contributions").header("Authorization", otherAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":5}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/goals/" + id).header("Authorization", otherAuth)).andExpect(status().isNotFound());
    }

    private ResultActions goal(String json) throws Exception {
        return mvc.perform(post("/api/goals").header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions contribute(long id, String amount) throws Exception {
        return mvc.perform(post("/api/goals/" + id + "/contributions").header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"));
    }

    private long createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private User newUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Goals Test");
        return userRepository.save(u);
    }

    private String auth() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }
}
