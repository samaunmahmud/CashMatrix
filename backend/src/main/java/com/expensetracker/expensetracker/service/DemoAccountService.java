package com.expensetracker.expensetracker.service;

import com.expensetracker.expensetracker.config.DemoProperties;
import com.expensetracker.expensetracker.config.ResourceNotFoundException;
import com.expensetracker.expensetracker.dto.AuthResponse;
import com.expensetracker.expensetracker.model.*;
import com.expensetracker.expensetracker.repository.*;
import com.expensetracker.expensetracker.security.JwtService;
import com.expensetracker.expensetracker.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The shared demo account: created the first time someone asks for the demo, then kept up to
 * date so it always looks like an account that was used this morning.
 *
 * Its accounts never touch Plaid. Transactions come from {@link DemoData} instead.
 */
@Service
@RequiredArgsConstructor
public class DemoAccountService {

    static final String FULL_NAME = "Sam Carter";
    static final int HISTORY_MONTHS = 24;
    // How far back a top-up looks for days it hasn't filled in yet.
    static final int TOP_UP_DAYS = 10;
    private static final String PLAID_PLACEHOLDER = "demo";

    private final DemoProperties demo;
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final CalendarEventRepository eventRepository;
    private final SavingsGoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PriceChangeService priceChangeService;
    private final TransactionTemplate transactions;
    private final Clock clock;

    // The day the data was last brought up to, so most demo logins cost nothing.
    private volatile LocalDate freshOn;
    private volatile int lastAdded;

    public boolean isDemo(User user) {
        return demo.isDemoEmail(user.getEmail());
    }

    /** Logs a visitor in to the demo account, creating or refreshing it first if needed. */
    public AuthResponse login() {
        if (!demo.isEnabled()) {
            throw new ResourceNotFoundException("The demo isn't available");
        }
        User user = refresh();
        return new AuthResponse(jwtService.generateToken(new UserPrincipal(user)), user.getEmail(), user.getFullName(), true);
    }

    /** Brings the demo account up to today. @return how many transactions were added */
    public int topUp() {
        refresh();
        return lastAdded;
    }

    // One at a time, and committed before the lock is released, so two visitors arriving together
    // can't both create the account.
    private synchronized User refresh() {
        LocalDate today = LocalDate.now(clock);
        User existing = userRepository.findByEmail(demo.getEmail()).orElse(null);
        if (existing != null && today.equals(freshOn)) {
            lastAdded = 0;
            return existing;
        }
        User user = transactions.execute(status -> {
            User account = existing != null ? existing : createUser();
            List<BankAccount> accounts = bankAccountRepository.findByUser(account);
            if (accounts.isEmpty()) {
                accounts = createAccounts(account);
                List<Transaction> added = fill(accounts, today.minusMonths(HISTORY_MONTHS).withDayOfMonth(1), today);
                createBudgets(account);
                createCalendar(account, today);
                createGoals(account, today);
                // The calendar starts at last year's prices, so the latest charges show what changed.
                priceChangeService.check(account, added, true);
                lastAdded = added.size();
            } else {
                List<Transaction> added = fill(accounts, today.minusDays(TOP_UP_DAYS), today);
                moveCalendarOn(account, today);
                priceChangeService.check(account, added, true);
                lastAdded = added.size();
            }
            bankAccountRepository.markSynced(account, clock.instant());
            return account;
        });
        freshOn = today;
        return user;
    }

    private User createUser() {
        User user = new User();
        user.setEmail(demo.getEmail());
        user.setFullName(FULL_NAME);
        // A password nobody knows: the only way in is the demo button.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        return userRepository.save(user);
    }

    private List<BankAccount> createAccounts(User user) {
        return bankAccountRepository.saveAll(List.of(
                account(user, DemoData.Account.CURRENT, "Current Account", "Classic Current Account",
                        "depository", "checking", "4821", "1842.37", "1842.37"),
                account(user, DemoData.Account.SAVINGS, "Easy Saver", "Easy Access Saver",
                        "depository", "savings", "7730", "6250.00", "6250.00"),
                account(user, DemoData.Account.CARD, "Rewards Credit Card", "Rewards Credit Card",
                        "credit", "credit card", "1194", "412.86", "2587.14")));
    }

    private BankAccount account(User user, DemoData.Account which, String name, String officialName,
                                String type, String subtype, String mask, String current, String available) {
        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setPlaidAccessToken(PLAID_PLACEHOLDER);
        account.setPlaidItemId(PLAID_PLACEHOLDER);
        account.setPlaidAccountId(plaidAccountId(which));
        account.setName(name);
        account.setOfficialName(officialName);
        account.setType(type);
        account.setSubtype(subtype);
        account.setMask(mask);
        account.setCurrentBalance(new BigDecimal(current));
        account.setAvailableBalance(new BigDecimal(available));
        account.setCurrency("GBP");
        account.setBalanceUpdatedAt(clock.instant());
        return account;
    }

    private static String plaidAccountId(DemoData.Account which) {
        return "demo-" + which.name().toLowerCase();
    }

    /** Adds the transactions of every day in the range that has none yet. @return the ones added */
    private List<Transaction> fill(List<BankAccount> accounts, LocalDate from, LocalDate to) {
        Map<DemoData.Account, BankAccount> byKind = new EnumMap<>(DemoData.Account.class);
        for (DemoData.Account kind : DemoData.Account.values()) {
            accounts.stream()
                    .filter(a -> plaidAccountId(kind).equals(a.getPlaidAccountId()))
                    .findFirst()
                    .ifPresent(a -> byKind.put(kind, a));
        }
        Set<LocalDate> filled = transactionRepository.findByBankAccountInAndTransactionDateBetween(accounts, from, to).stream()
                .map(Transaction::getTransactionDate)
                .collect(Collectors.toSet());

        List<Transaction> added = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (filled.contains(date)) continue;
            int index = 0;
            for (DemoData.Entry entry : DemoData.on(date)) {
                BankAccount account = byKind.get(entry.account());
                if (account == null) continue;
                Transaction tx = new Transaction();
                tx.setBankAccount(account);
                tx.setPlaidTransactionId("demo-" + date + "-" + index++);
                tx.setName(entry.name());
                tx.setAmount(entry.amount());
                tx.setPlaidCategory(entry.category());
                tx.setTransactionDate(date);
                added.add(tx);
            }
        }
        return transactionRepository.saveAll(added);
    }

    private void createBudgets(User user) {
        budgetRepository.saveAll(List.of(
                budget(user, "Groceries", "380"),
                budget(user, "Eating out", "260"),
                budget(user, "Transport", "150"),
                budget(user, "Shopping", "160"),
                budget(user, "Entertainment", "45")));
    }

    private static Budget budget(User user, String category, String limit) {
        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory(category);
        budget.setMonthlyLimit(new BigDecimal(limit));
        return budget;
    }

    private void createCalendar(User user, LocalDate today) {
        LocalDate anchor = today.minusMonths(6);
        eventRepository.saveAll(List.of(
                repeating(user, "Hartley Lettings rent", EventType.PAYMENT, "950.00", anchor.withDayOfMonth(1), 3, today),
                repeating(user, "PureGym", EventType.SUBSCRIPTION, "24.99", anchor.withDayOfMonth(2), 2, today),
                repeating(user, "Camden Council Tax", EventType.PAYMENT, "142.00", anchor.withDayOfMonth(3), 3, today),
                repeating(user, "Octopus Energy", EventType.PAYMENT, "82.00", anchor.withDayOfMonth(5), 2, today),
                repeating(user, "Netflix", EventType.SUBSCRIPTION, "10.99", anchor.withDayOfMonth(8), 2, today),
                repeating(user, "BT Broadband", EventType.SUBSCRIPTION, "32.99", anchor.withDayOfMonth(12), 2, today),
                repeating(user, "Spotify", EventType.SUBSCRIPTION, "11.99", anchor.withDayOfMonth(15), 2, today),
                repeating(user, "Submit meter reading", EventType.TASK, null, anchor.withDayOfMonth(25), 1, today),
                oneOff(user, "Car insurance renewal", EventType.PAYMENT, "412.00", today.plusDays(16), 7),
                oneOff(user, "Book the car's MOT", EventType.TASK, null, today.plusDays(9), 3)));
    }

    private static CalendarEvent repeating(User user, String title, EventType type, String amount,
                                           LocalDate start, int remindDaysBefore, LocalDate today) {
        CalendarEvent event = oneOff(user, title, type, amount, start, remindDaysBefore);
        event.setRecurrence(Recurrence.MONTHLY);
        event.setNextDueDate(Recurrence.MONTHLY.firstOnOrAfter(start, today));
        return event;
    }

    private static CalendarEvent oneOff(User user, String title, EventType type, String amount,
                                        LocalDate due, int remindDaysBefore) {
        CalendarEvent event = new CalendarEvent();
        event.setUser(user);
        event.setTitle(title);
        event.setType(type);
        event.setAmount(amount == null ? null : new BigDecimal(amount));
        event.setStartDate(due);
        event.setNextDueDate(due);
        event.setRemindDaysBefore(remindDaysBefore);
        return event;
    }

    /**
     * Nobody can mark the demo's bills as paid, so they are moved on once their day has passed.
     * Otherwise the calendar would slowly fill with things that look overdue.
     */
    private void moveCalendarOn(User user, LocalDate today) {
        for (CalendarEvent event : eventRepository.findByUserOrderByNextDueDateAsc(user)) {
            if (!event.getNextDueDate().isBefore(today)) continue;
            if (event.getRecurrence().isRecurring()) {
                event.setNextDueDate(event.getRecurrence().firstOnOrAfter(event.getStartDate(), today));
            } else {
                LocalDate due = event.getNextDueDate();
                while (due.isBefore(today)) {
                    due = due.plusDays(28);
                }
                event.setStartDate(due);
                event.setNextDueDate(due);
            }
            eventRepository.save(event);
        }
    }

    private void createGoals(User user, LocalDate today) {
        // Saved towards every month by standing order, which the app records by itself.
        SavingsGoal holiday = goal(user, "Holiday in Lisbon", "✈️", "1500", today.plusMonths(6).withDayOfMonth(1));
        LocalDate planStart = today.minusMonths(7).withDayOfMonth(28);
        LocalDate planNext = Recurrence.MONTHLY.firstOnOrAfter(planStart, today);
        holiday.setPlanAmount(new BigDecimal("120.00"));
        holiday.setPlanFrequency(Recurrence.MONTHLY);
        holiday.setPlanStartDate(planStart);
        holiday.setPlanNextDate(planNext);
        holiday.setPlanAutoRecord(true);
        List<GoalContribution> holidaySaved = new ArrayList<>();
        holidaySaved.add(contribution(holiday, "270.00", planStart.minusMonths(1)));
        for (int monthsBack = 5; monthsBack >= 1; monthsBack--) {
            holidaySaved.add(contribution(holiday, "120.00", planNext.minusMonths(monthsBack)));
        }
        save(holiday, holidaySaved);

        SavingsGoal emergency = goal(user, "Emergency fund", "🛟", "5000", null);
        save(emergency, List.of(
                contribution(emergency, "2000.00", today.minusMonths(10)),
                contribution(emergency, "700.00", today.minusMonths(4)),
                contribution(emergency, "500.00", today.minusMonths(1))));

        SavingsGoal laptop = goal(user, "New laptop", "💻", "1200", today.plusMonths(8).withDayOfMonth(1));
        save(laptop, List.of(contribution(laptop, "300.00", today.minusDays(14))));
    }

    private static SavingsGoal goal(User user, String name, String emoji, String target, LocalDate targetDate) {
        SavingsGoal goal = new SavingsGoal();
        goal.setUser(user);
        goal.setName(name);
        goal.setEmoji(emoji);
        goal.setTargetAmount(new BigDecimal(target));
        goal.setTargetDate(targetDate);
        return goal;
    }

    private static GoalContribution contribution(SavingsGoal goal, String amount, LocalDate madeOn) {
        GoalContribution contribution = new GoalContribution();
        contribution.setGoal(goal);
        contribution.setAmount(new BigDecimal(amount));
        contribution.setMadeOn(madeOn);
        return contribution;
    }

    private void save(SavingsGoal goal, List<GoalContribution> contributions) {
        goal.setSavedAmount(contributions.stream().map(GoalContribution::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        goalRepository.save(goal);
        contributionRepository.saveAll(contributions);
    }
}
