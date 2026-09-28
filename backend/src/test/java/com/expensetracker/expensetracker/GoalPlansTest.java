package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.Notification;
import com.expensetracker.expensetracker.model.Recurrence;
import com.expensetracker.expensetracker.model.SavingsGoal;
import com.expensetracker.expensetracker.model.User;
import com.expensetracker.expensetracker.repository.GoalContributionRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import com.expensetracker.expensetracker.repository.SavingsGoalRepository;
import com.expensetracker.expensetracker.repository.UserRepository;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import com.expensetracker.expensetracker.service.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Today" is Sunday 2026-09-20. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
class GoalPlansTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired SavingsGoalRepository goalRepository;
    @Autowired GoalContributionRepository contributionRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired GoalService goalService;

    User user;

    @BeforeEach
    void seed() {
        // Earlier tests' plans would also be processed, so start each test from a clean slate of due plans.
        goalRepository.findAll().forEach(g -> {
            g.setPlanNextDate(null);
            goalRepository.save(g);
        });
        user = newUser();
    }

    @Test
    void aPlanSaysWhenTheGoalWillBeReached() throws Exception {
        goal("{\"name\":\"Holiday\",\"targetAmount\":200,\"planAmount\":50,\"planFrequency\":\"MONTHLY\",\"planStartDate\":\"2026-09-25\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan.amount").value(50))
                .andExpect(jsonPath("$.plan.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.plan.nextDate").value("2026-09-25"))
                .andExpect(jsonPath("$.plan.autoRecord").value(false))
                // four saving days: Sep, Oct, Nov, Dec
                .andExpect(jsonPath("$.plan.finishDate").value("2026-12-25"));
    }

    @Test
    void aPlanThatStartedInThePastWaitsForItsNextDay() throws Exception {
        // 1 August 2026 is a Saturday, so the next weekly saving is Saturday 26 September.
        goal("{\"name\":\"Bike\",\"targetAmount\":100,\"planAmount\":10,\"planFrequency\":\"WEEKLY\",\"planStartDate\":\"2026-08-01\"}")
                .andExpect(jsonPath("$.plan.nextDate").value("2026-09-26"))
                .andExpect(jsonPath("$.plan.finishDate").value("2026-11-28"));
    }

    @Test
    void monthEndPlansFinishOnTheRightDay() {
        SavingsGoal goal = new SavingsGoal();
        goal.setTargetAmount(new BigDecimal("300"));
        goal.setPlanAmount(new BigDecimal("100"));
        goal.setPlanFrequency(Recurrence.MONTHLY);
        goal.setPlanStartDate(LocalDate.parse("2026-01-31"));
        goal.setPlanNextDate(LocalDate.parse("2026-02-28"));
        // Feb 28, Mar 31, Apr 30
        assertThat(invokeFinish(goal)).isEqualTo(LocalDate.parse("2026-04-30"));
    }

    @Test
    void aStandingOrderPlanIsRecordedOnItsDayAndOnlyOnce() throws Exception {
        long id = createdId(goal("{\"name\":\"Holiday\",\"targetAmount\":500,\"planAmount\":50,\"planFrequency\":\"MONTHLY\",\"planStartDate\":\"2026-10-01\",\"planAutoRecord\":true}"));
        moveNextDate(id, "2026-08-01", "2026-09-01"); // as if the start had been in August and September 1st is due

        assertThat(goalService.processPlans()).isEqualTo(1);
        assertThat(goalService.processPlans()).as("nothing is due twice").isZero();

        SavingsGoal goal = goalRepository.findById(id).orElseThrow();
        assertThat(goal.getSavedAmount()).isEqualByComparingTo("50");
        assertThat(goal.getPlanNextDate()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(contributionRepository.findTop5ByGoalOrderByMadeOnDescIdDesc(goal))
                .singleElement().satisfies(c -> assertThat(c.getMadeOn()).isEqualTo(LocalDate.parse("2026-09-01")));

        assertThat(alerts()).singleElement().satisfies(n -> {
            assertThat(n.getTitle()).isEqualTo("Saved £50.00 for Holiday");
            assertThat(n.getMessage()).isEqualTo("Your regular saving went in. You now have £50.00 of £500.00.");
            assertThat(n.isEmailSent()).as("made in the background, so it goes out by email too").isFalse();
        });
    }

    @Test
    void missedDaysAreCaughtUpButNeverPastTheTarget() throws Exception {
        long id = createdId(goal("{\"name\":\"Laptop\",\"targetAmount\":130,\"planAmount\":50,\"planFrequency\":\"MONTHLY\",\"planAutoRecord\":true}"));
        moveNextDate(id, "2026-06-01", "2026-06-01"); // June, July, August, September all due

        goalService.processPlans();

        SavingsGoal goal = goalRepository.findById(id).orElseThrow();
        assertThat(goal.getSavedAmount()).as("50 + 50 + 30, then the goal is full").isEqualByComparingTo("130");
        assertThat(contributionRepository.findTop5ByGoalOrderByMadeOnDescIdDesc(goal)).hasSize(3);
        assertThat(alerts()).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("Saved £130.00 for Laptop", "Goal reached: Laptop");
        assertThat(alerts()).filteredOn(n -> n.getTitle().startsWith("Goal reached"))
                .singleElement().satisfies(n -> assertThat(n.isPushSent()).isFalse());
    }

    @Test
    void withoutAStandingOrderTheUserIsRemindedInstead() throws Exception {
        long id = createdId(goal("{\"name\":\"Car\",\"targetAmount\":3000,\"planAmount\":25,\"planFrequency\":\"WEEKLY\"}"));
        moveNextDate(id, "2026-09-13", "2026-09-20");

        goalService.processPlans();

        assertThat(goalRepository.findById(id).orElseThrow().getSavedAmount()).isEqualByComparingTo("0");
        assertThat(alerts()).singleElement().satisfies(n -> {
            assertThat(n.getTitle()).isEqualTo("Time to save for Car");
            assertThat(n.getMessage()).isEqualTo("Put £25.00 aside for Car today, then record it in CashMatrix.");
            assertThat(n.getLink()).isEqualTo("/goals");
        });
    }

    @Test
    void planSavingsShowOnTheCalendarUntilTheGoalWouldBeReached() throws Exception {
        long id = createdId(goal("{\"name\":\"Holiday\",\"emoji\":\"🏖️\",\"targetAmount\":150,\"planAmount\":50,\"planFrequency\":\"MONTHLY\",\"planStartDate\":\"2026-09-25\"}"));

        mvc.perform(get("/api/calendar").param("from", "2026-09-01").param("to", "2027-03-31").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026-09-25"))
                .andExpect(jsonPath("$[2].date").value("2026-11-25"))
                .andExpect(jsonPath("$[0].type").value("SAVING"))
                .andExpect(jsonPath("$[0].title").value("Save for Holiday"))
                .andExpect(jsonPath("$[0].amount").value(50))
                .andExpect(jsonPath("$[0].goalId").value(id))
                .andExpect(jsonPath("$[0].eventId").doesNotExist());
    }

    @Test
    void changingTheAmountKeepsThePlanWhereItIsAndRemovingItClearsIt() throws Exception {
        long id = createdId(goal("{\"name\":\"Fund\",\"targetAmount\":1000,\"planAmount\":20,\"planFrequency\":\"WEEKLY\",\"planStartDate\":\"2026-09-21\"}"));
        moveNextDate(id, "2026-09-21", "2026-10-05");

        update(id, "{\"name\":\"Fund\",\"targetAmount\":1000,\"planAmount\":40,\"planFrequency\":\"WEEKLY\",\"planStartDate\":\"2026-09-21\"}")
                .andExpect(jsonPath("$.plan.amount").value(40))
                .andExpect(jsonPath("$.plan.nextDate").value("2026-10-05"));
        update(id, "{\"name\":\"Fund\",\"targetAmount\":1000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").doesNotExist());
        assertThat(goalRepository.findById(id).orElseThrow().getPlanNextDate()).isNull();
    }

    @Test
    void validatesPlans() throws Exception {
        goal("{\"name\":\"X\",\"targetAmount\":100,\"planAmount\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A regular saving needs both an amount and how often"));
        goal("{\"name\":\"X\",\"targetAmount\":100,\"planAmount\":10,\"planFrequency\":\"YEARLY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A regular saving can be weekly or monthly"));
        goal("{\"name\":\"X\",\"targetAmount\":100,\"planAmount\":0,\"planFrequency\":\"WEEKLY\"}")
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/calendar/events").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\",\"type\":\"SAVING\",\"startDate\":\"2026-10-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Regular savings are set up on a savings goal"));
    }

    private LocalDate invokeFinish(SavingsGoal goal) {
        try {
            var method = GoalService.class.getDeclaredMethod("finishDate", SavingsGoal.class);
            method.setAccessible(true);
            return (LocalDate) method.invoke(null, goal);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void moveNextDate(long id, String start, String next) {
        SavingsGoal goal = goalRepository.findById(id).orElseThrow();
        goal.setPlanStartDate(LocalDate.parse(start));
        goal.setPlanNextDate(LocalDate.parse(next));
        goalRepository.save(goal);
    }

    private List<Notification> alerts() {
        return notificationRepository.findTop50ByUserOrderByCreatedAtDescIdDesc(user);
    }

    private ResultActions goal(String json) throws Exception {
        return mvc.perform(post("/api/goals").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions update(long id, String json) throws Exception {
        return mvc.perform(put("/api/goals/" + id).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*", "$1"));
    }

    private User newUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("x");
        u.setFullName("Plans Test");
        return userRepository.save(u);
    }

    private String auth() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }
}
