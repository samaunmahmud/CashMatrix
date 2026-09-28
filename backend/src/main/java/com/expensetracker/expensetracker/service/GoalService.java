package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.ResourceNotFoundException;
import com.expensetracker.expensetracker.dto.CalendarEntryResponse;
import com.expensetracker.expensetracker.dto.ContributionRequest;
import com.expensetracker.expensetracker.dto.GoalRequest;
import com.expensetracker.expensetracker.dto.GoalResponse;
import com.expensetracker.expensetracker.dto.GoalResponse.Contribution;
import com.expensetracker.expensetracker.dto.GoalResponse.GoalStatus;
import com.expensetracker.expensetracker.dto.GoalResponse.Plan;
import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.GoalContributionRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import com.expensetracker.expensetracker.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Savings goals: what the user is putting money aside for, how far along they are, and what
 * they need to save each month to get there on time. A message congratulates them the first
 * time a goal is reached.
 *
 * A goal can have a regular saving (say 50 every month). On each saving day the app either records
 * the money itself, for someone with a standing order doing it, or reminds them to put it aside.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    static final int MAX_GOALS = 20;
    // Missed saving days recorded in one go, for a server that was asleep for a while.
    static final int MAX_CATCH_UP = 12;
    // Keeps a very small saving against a very large goal from projecting a date centuries away.
    static final long MAX_PLAN_PAYMENTS = 100L * 52;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SavingsGoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final NotificationRepository notificationRepository;
    private final BankAccountRepository bankAccountRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<GoalResponse> list(User user) {
        return goalRepository.findByUserOrderByCreatedAtAscIdAsc(user).stream().map(this::toResponse).toList();
    }

    @Transactional
    public GoalResponse create(User user, GoalRequest request) {
        if (goalRepository.countByUser(user) >= MAX_GOALS) {
            throw new IllegalArgumentException("You can have up to " + MAX_GOALS + " goals");
        }
        SavingsGoal goal = new SavingsGoal();
        goal.setUser(user);
        apply(goal, request);
        goalRepository.save(goal);
        return toResponse(goal);
    }

    @Transactional
    public GoalResponse update(User user, Long id, GoalRequest request) {
        SavingsGoal goal = find(user, id);
        apply(goal, request);
        celebrateIfReached(goal, true);
        return toResponse(goal);
    }

    @Transactional
    public void delete(User user, Long id) {
        SavingsGoal goal = find(user, id);
        contributionRepository.deleteByGoal(goal);
        goalRepository.delete(goal);
    }

    /** Adds money to a goal, or takes it out when the amount is negative (never below nothing saved). */
    @Transactional
    public GoalResponse contribute(User user, Long id, ContributionRequest request) {
        SavingsGoal goal = find(user, id);
        BigDecimal amount = request.getAmount();
        if (amount.signum() == 0) throw new IllegalArgumentException("Enter an amount to add or take out");

        BigDecimal saved = goal.getSavedAmount().add(amount);
        if (saved.signum() < 0) {
            throw new IllegalArgumentException("You can take out at most "
                    + Money.format(goal.getSavedAmount(), currencyOf(user)));
        }
        goal.setSavedAmount(saved);

        GoalContribution contribution = new GoalContribution();
        contribution.setGoal(goal);
        contribution.setAmount(amount);
        contribution.setMadeOn(LocalDate.now(clock));
        contributionRepository.save(contribution);

        celebrateIfReached(goal, true);
        return toResponse(goal);
    }

    /**
     * Deals with every regular saving that has come due: records the money (or reminds the user to put it
     * aside), raises one alert per goal, and moves the plan on to its next saving day. Called by the scheduler.
     *
     * @return how many goals had a saving day
     */
    @Transactional
    public int processPlans() {
        LocalDate today = LocalDate.now(clock);
        int processed = 0;
        for (SavingsGoal goal : goalRepository.findPlansDueBy(today)) {
            try {
                processPlan(goal, today);
                processed++;
            } catch (RuntimeException ex) {
                log.warn("Could not process the saving plan for goal {}: {}", goal.getId(), ex.getMessage());
            }
        }
        return processed;
    }

    private void processPlan(SavingsGoal goal, LocalDate today) {
        Recurrence frequency = goal.getPlanFrequency();
        List<LocalDate> due = new ArrayList<>();
        LocalDate next = goal.getPlanNextDate();
        while (!next.isAfter(today)) {
            if (due.size() < MAX_CATCH_UP) due.add(next);
            next = frequency.firstAfter(goal.getPlanStartDate(), next);
        }
        goal.setPlanNextDate(next);

        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getSavedAmount());
        if (remaining.signum() <= 0) return; // already reached: nothing to save or remind about
        LocalDate latest = due.get(due.size() - 1);
        String currency = currencyOf(goal.getUser());

        Notification notification = new Notification();
        notification.setUser(goal.getUser());
        notification.setAlertKey("goal-" + goal.getId() + "-plan-" + latest);
        notification.setLink("/goals");
        notification.setDueDate(latest);

        if (goal.isPlanAutoRecord()) {
            BigDecimal added = BigDecimal.ZERO;
            for (LocalDate day : due) {
                BigDecimal amount = goal.getPlanAmount().min(goal.getTargetAmount().subtract(goal.getSavedAmount()));
                if (amount.signum() <= 0) break;
                goal.setSavedAmount(goal.getSavedAmount().add(amount));
                GoalContribution contribution = new GoalContribution();
                contribution.setGoal(goal);
                contribution.setAmount(amount);
                contribution.setMadeOn(day);
                contributionRepository.save(contribution);
                added = added.add(amount);
            }
            notification.setTitle("Saved " + Money.format(added, currency) + " for " + goal.getName());
            notification.setMessage("Your regular saving went in. You now have "
                    + Money.format(goal.getSavedAmount(), currency) + " of " + Money.format(goal.getTargetAmount(), currency) + ".");
        } else {
            notification.setTitle("Time to save for " + goal.getName());
            notification.setMessage("Put " + Money.format(goal.getPlanAmount().min(remaining), currency)
                    + " aside for " + goal.getName() + " today, then record it in CashMatrix.");
        }
        if (!notificationRepository.existsByAlertKey(notification.getAlertKey())) {
            notificationRepository.save(notification);
        }
        celebrateIfReached(goal, false);
    }

    /** The regular savings still to come between two dates, for the calendar. */
    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> calendarEntries(User user, LocalDate from, LocalDate to) {
        List<CalendarEntryResponse> entries = new ArrayList<>();
        for (SavingsGoal goal : goalRepository.findByUserAndPlanNextDateIsNotNull(user)) {
            if (!goal.hasPlan()) continue;
            LocalDate finish = finishDate(goal);
            if (finish == null) continue; // reached: nothing left to save
            LocalDate start = goal.getPlanNextDate().isAfter(from) ? goal.getPlanNextDate() : from;
            LocalDate end = finish.isBefore(to) ? finish : to;
            if (end.isBefore(start)) continue;
            for (LocalDate date : goal.getPlanFrequency().occurrencesBetween(goal.getPlanStartDate(), start, end)) {
                entries.add(new CalendarEntryResponse(null, "Save for " + goal.getName(),
                        goal.isPlanAutoRecord() ? "Recorded for you (standing order)" : null,
                        EventType.SAVING, goal.getPlanAmount(), date, goal.getPlanFrequency(), false, goal.getId()));
            }
        }
        return entries;
    }

    /** The saving day that completes the goal if the plan is kept up; null once reached. */
    static LocalDate finishDate(SavingsGoal goal) {
        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getSavedAmount());
        if (remaining.signum() <= 0) return null;
        long payments = remaining.divide(goal.getPlanAmount(), 0, RoundingMode.CEILING).longValue();
        Recurrence frequency = goal.getPlanFrequency();
        LocalDate start = goal.getPlanStartDate();
        // Straight to the right saving day by counting, rather than stepping through each one.
        long index = occurrenceIndex(frequency, start, goal.getPlanNextDate());
        return frequency.occurrence(start, index + Math.min(payments - 1, MAX_PLAN_PAYMENTS));
    }

    /** Which occurrence (0 = the start) a saving day is. Monthly plans from the 31st land on shorter months' last day. */
    static long occurrenceIndex(Recurrence frequency, LocalDate start, LocalDate date) {
        long n = frequency == Recurrence.WEEKLY
                ? ChronoUnit.WEEKS.between(start, date)
                : ChronoUnit.MONTHS.between(start, date);
        return frequency.occurrence(start, n).isBefore(date) ? n + 1 : n;
    }

    private GoalResponse toResponse(SavingsGoal goal) {
        LocalDate today = LocalDate.now(clock);
        BigDecimal target = goal.getTargetAmount();
        BigDecimal saved = goal.getSavedAmount();
        BigDecimal remaining = target.subtract(saved).max(BigDecimal.ZERO);
        int percent = Math.min(100, saved.multiply(HUNDRED).divide(target, 0, RoundingMode.DOWN).intValue());

        GoalStatus status;
        BigDecimal monthlyNeeded = null;
        if (remaining.signum() == 0) {
            status = GoalStatus.REACHED;
        } else if (goal.getTargetDate() != null && goal.getTargetDate().isBefore(today)) {
            status = GoalStatus.PAST_DATE;
        } else {
            status = GoalStatus.IN_PROGRESS;
            if (goal.getTargetDate() != null) {
                monthlyNeeded = remaining.divide(BigDecimal.valueOf(monthsLeft(today, goal.getTargetDate())), 2, RoundingMode.CEILING);
            }
        }

        List<Contribution> recent = contributionRepository.findTop5ByGoalOrderByMadeOnDescIdDesc(goal).stream()
                .map(c -> new Contribution(c.getId(), c.getAmount(), c.getMadeOn()))
                .toList();
        Plan plan = goal.hasPlan()
                ? new Plan(goal.getPlanAmount(), goal.getPlanFrequency(), goal.getPlanStartDate(), goal.getPlanNextDate(),
                        goal.isPlanAutoRecord(), finishDate(goal))
                : null;
        return new GoalResponse(goal.getId(), goal.getName(), goal.getEmoji(), target, saved, remaining, percent,
                goal.getTargetDate(), monthlyNeeded, status, recent, plan);
    }

    /** Months to save in, counting this one: from September 20th to a December date is four (Sep, Oct, Nov, Dec). */
    static long monthsLeft(LocalDate today, LocalDate targetDate) {
        return YearMonth.from(today).until(YearMonth.from(targetDate), ChronoUnit.MONTHS) + 1;
    }

    private void apply(SavingsGoal goal, GoalRequest request) {
        goal.setName(request.getName().trim().replaceAll("\\s+", " "));
        String emoji = request.getEmoji() == null ? null : request.getEmoji().trim();
        goal.setEmoji(emoji == null || emoji.isEmpty() ? null : emoji);
        goal.setTargetAmount(request.getTargetAmount());
        goal.setTargetDate(request.getTargetDate());
        applyPlan(goal, request);
    }

    private void applyPlan(SavingsGoal goal, GoalRequest request) {
        if (request.getPlanAmount() == null && request.getPlanFrequency() == null) {
            goal.setPlanAmount(null);
            goal.setPlanFrequency(null);
            goal.setPlanStartDate(null);
            goal.setPlanNextDate(null);
            goal.setPlanAutoRecord(false);
            return;
        }
        if (request.getPlanAmount() == null || request.getPlanFrequency() == null) {
            throw new IllegalArgumentException("A regular saving needs both an amount and how often");
        }
        if (request.getPlanFrequency() != Recurrence.WEEKLY && request.getPlanFrequency() != Recurrence.MONTHLY) {
            throw new IllegalArgumentException("A regular saving can be weekly or monthly");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = request.getPlanStartDate() != null ? request.getPlanStartDate() : today;

        // Changing only the amount keeps the plan where it has got to; a new schedule starts from its next day.
        boolean newSchedule = !goal.hasPlan() || goal.getPlanFrequency() != request.getPlanFrequency()
                || !start.equals(goal.getPlanStartDate());
        goal.setPlanAmount(request.getPlanAmount());
        goal.setPlanFrequency(request.getPlanFrequency());
        goal.setPlanStartDate(start);
        goal.setPlanAutoRecord(request.isPlanAutoRecord());
        if (newSchedule) {
            goal.setPlanNextDate(request.getPlanFrequency().firstOnOrAfter(start, today));
        }
    }

    /**
     * The first time a goal is reached, a message says so (never twice for one goal). If the user is in the
     * app it isn't sent anywhere else; if a regular saving got there in the background, it goes out too.
     */
    private void celebrateIfReached(SavingsGoal goal, boolean userIsPresent) {
        if (goal.getSavedAmount().compareTo(goal.getTargetAmount()) < 0) return;
        String key = "goal-" + goal.getId() + "-reached";
        if (notificationRepository.existsByAlertKey(key)) return;

        Notification notification = new Notification();
        notification.setUser(goal.getUser());
        notification.setAlertKey(key);
        notification.setLink("/goals");
        notification.setDueDate(LocalDate.now(clock));
        notification.setTitle("Goal reached: " + goal.getName());
        notification.setMessage("You've saved " + Money.format(goal.getSavedAmount(), currencyOf(goal.getUser()))
                + " for " + goal.getName() + ". Well done!");
        notification.setEmailSent(userIsPresent);
        notification.setPushSent(userIsPresent);
        notificationRepository.save(notification);
    }

    private SavingsGoal find(User user, Long id) {
        return goalRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
    }

    /** The currency of the user's accounts, or pounds if they don't say. */
    private String currencyOf(User user) {
        return bankAccountRepository.findByUser(user).stream()
                .map(BankAccount::getCurrency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Money.DEFAULT_CURRENCY);
    }
}
