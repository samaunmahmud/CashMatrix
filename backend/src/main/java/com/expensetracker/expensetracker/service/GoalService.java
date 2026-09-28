package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.ResourceNotFoundException;
import com.expensetracker.expensetracker.dto.ContributionRequest;
import com.expensetracker.expensetracker.dto.GoalRequest;
import com.expensetracker.expensetracker.dto.GoalResponse;
import com.expensetracker.expensetracker.dto.GoalResponse.Contribution;
import com.expensetracker.expensetracker.dto.GoalResponse.GoalStatus;
import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.BankAccountRepository;
import com.expensetracker.expensetracker.repository.GoalContributionRepository;
import com.expensetracker.expensetracker.repository.NotificationRepository;
import com.expensetracker.expensetracker.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Savings goals: what the user is putting money aside for, how far along they are, and what
 * they need to save each month to get there on time. A message congratulates them the first
 * time a goal is reached.
 */
@Service
@RequiredArgsConstructor
public class GoalService {

    static final int MAX_GOALS = 20;
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
        celebrateIfReached(goal);
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

        celebrateIfReached(goal);
        return toResponse(goal);
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
        return new GoalResponse(goal.getId(), goal.getName(), goal.getEmoji(), target, saved, remaining, percent,
                goal.getTargetDate(), monthlyNeeded, status, recent);
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
    }

    /** The first time a goal is reached, a message says so (never twice for one goal). */
    private void celebrateIfReached(SavingsGoal goal) {
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
        // They are in the app, having just added the money, so it isn't sent anywhere else.
        notification.setEmailSent(true);
        notification.setPushSent(true);
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
