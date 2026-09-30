package com.expensetracker.expensetracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The made-up life behind the demo account: a salary, rent, bills, subscriptions and everyday
 * spending around London.
 *
 * What happened on a day depends only on the date, so the history can be topped up a day at a
 * time and always comes out the same. Amounts follow Plaid: money out is positive, money in negative.
 */
final class DemoData {

    enum Account { CURRENT, SAVINGS, CARD }

    record Entry(Account account, String name, BigDecimal amount, String category) {
    }

    private static final String[] TOP_UP_SHOPS = {"Tesco Express", "Sainsbury's Local", "Co-op", "Waitrose"};
    private static final String[] BIG_SHOPS = {"Tesco Superstore", "Sainsbury's", "Aldi", "Tesco Superstore"};
    private static final String[] RESTAURANTS = {"Dishoom", "Nando's", "Wagamama", "Franco Manca", "Honest Burgers"};
    private static final String[] SHOPS = {"Uniqlo", "Boots", "John Lewis", "Waterstones", "IKEA", "Decathlon"};

    // So the demo has a price change to show.
    static final LocalDate SPOTIFY_PRICE_RISE = LocalDate.of(2026, 9, 1);

    private DemoData() {
    }

    static List<Entry> on(LocalDate date) {
        List<Entry> entries = new ArrayList<>();
        Random random = new Random(date.toEpochDay() * 7919L);
        regularPayments(date, entries);
        everydaySpending(date, random, entries);
        return entries;
    }

    /** The salary arrives on the 28th, or the Friday before when that falls on a weekend. */
    static LocalDate payday(LocalDate anyDayInMonth) {
        LocalDate day = anyDayInMonth.withDayOfMonth(28);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.minusDays(1);
        }
        return day;
    }

    private static void regularPayments(LocalDate date, List<Entry> entries) {
        if (date.equals(payday(date))) {
            entries.add(new Entry(Account.CURRENT, "Northway Digital Ltd Salary", money("-2650.00"), "Income"));
        }
        boolean winter = date.getMonth().getValue() >= 11 || date.getMonth().getValue() <= 2;
        switch (date.getDayOfMonth()) {
            case 1 -> {
                entries.add(new Entry(Account.CURRENT, "Hartley Lettings Rent", money("950.00"), "Rent"));
                entries.add(new Entry(Account.SAVINGS, "Interest", money("-13.42"), "Interest"));
            }
            case 2 -> entries.add(new Entry(Account.CURRENT, "PureGym Ltd", money("24.99"), "Health & fitness"));
            case 3 -> entries.add(new Entry(Account.CURRENT, "Camden Council Tax", money("142.00"), "Bills"));
            case 5 -> entries.add(new Entry(Account.CURRENT, "Octopus Energy", money(winter ? "91.00" : "82.00"), "Bills"));
            case 8 -> entries.add(new Entry(Account.CARD, "NETFLIX.COM", money("10.99"), "Entertainment"));
            case 10 -> entries.add(new Entry(Account.CARD, "APPLE.COM/BILL", money("2.99"), "Entertainment"));
            case 12 -> entries.add(new Entry(Account.CURRENT, "BT Broadband", money("32.99"), "Bills"));
            case 15 -> entries.add(new Entry(Account.CARD, "Spotify AB", money(date.isBefore(SPOTIFY_PRICE_RISE) ? "11.99" : "12.99"), "Entertainment"));
            case 18 -> entries.add(new Entry(Account.CURRENT, "giffgaff", money("12.00"), "Bills"));
            case 20 -> entries.add(new Entry(Account.CURRENT, "Thames Water", money("38.50"), "Bills"));
            case 22 -> entries.add(new Entry(Account.CARD, "Disney Plus", money("4.99"), "Entertainment"));
            default -> { }
        }
        if (date.getMonth() == Month.MARCH && date.getDayOfMonth() == 14) {
            entries.add(new Entry(Account.CARD, "Amazon Prime", money("95.00"), "Shopping"));
        }
    }

    private static void everydaySpending(LocalDate date, Random random, List<Entry> entries) {
        DayOfWeek day = date.getDayOfWeek();
        boolean weekday = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
        boolean eveningOut = day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;

        double commute = random.nextDouble();
        double lunch = random.nextDouble();
        double coffee = random.nextDouble();
        double topUp = random.nextDouble();
        double dinner = random.nextDouble();
        double pub = random.nextDouble();
        double takeaway = random.nextDouble();
        double taxi = random.nextDouble();
        double train = random.nextDouble();
        double amazon = random.nextDouble();
        double shop = random.nextDouble();
        double cinema = random.nextDouble();

        if (weekday && commute < 0.72) {
            entries.add(new Entry(Account.CURRENT, "TfL Travel Charge", between(random, 2.80, 8.40), "Transport"));
        }
        if (weekday && lunch < 0.34) {
            entries.add(new Entry(Account.CURRENT, "Pret A Manger", between(random, 3.60, 8.20), "Eating out"));
        }
        if (coffee < 0.16) {
            entries.add(new Entry(Account.CURRENT, "Costa Coffee", between(random, 3.10, 5.40), "Eating out"));
        }
        if (day == DayOfWeek.SATURDAY) {
            entries.add(new Entry(Account.CURRENT, pick(random, BIG_SHOPS), between(random, 46, 84), "Groceries"));
        } else if (topUp < 0.36) {
            entries.add(new Entry(Account.CURRENT, pick(random, TOP_UP_SHOPS), between(random, 4, 18.50), "Groceries"));
        }
        if (eveningOut && dinner < 0.45) {
            entries.add(new Entry(Account.CARD, pick(random, RESTAURANTS), between(random, 14, 46), "Eating out"));
        }
        if (day == DayOfWeek.FRIDAY && pub < 0.40) {
            entries.add(new Entry(Account.CURRENT, "The Crown Tavern", between(random, 9, 27), "Eating out"));
        }
        if (takeaway < 0.11) {
            entries.add(new Entry(Account.CARD, "Deliveroo", between(random, 15, 31), "Eating out"));
        }
        if (taxi < 0.05) {
            entries.add(new Entry(Account.CARD, "Uber", between(random, 7, 22), "Transport"));
        }
        if (train < 0.03) {
            entries.add(new Entry(Account.CARD, "Trainline", between(random, 18, 58), "Transport"));
        }
        if (amazon < 0.09) {
            entries.add(new Entry(Account.CARD, "Amazon.co.uk", between(random, 6, 58), "Shopping"));
        }
        if (shop < 0.07) {
            entries.add(new Entry(Account.CARD, pick(random, SHOPS), between(random, 8, 64), "Shopping"));
        }
        if (cinema < 0.04) {
            entries.add(new Entry(Account.CARD, "Odeon Cinemas", between(random, 9, 24), "Entertainment"));
        }
    }

    private static String pick(Random random, String[] names) {
        return names[random.nextInt(names.length)];
    }

    private static BigDecimal between(Random random, double from, double to) {
        return BigDecimal.valueOf(from + random.nextDouble() * (to - from)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
