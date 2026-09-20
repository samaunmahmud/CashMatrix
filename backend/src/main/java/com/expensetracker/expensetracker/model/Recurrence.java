package com.expensetracker.expensetracker.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * How often a calendar entry repeats.
 *
 * Every occurrence is calculated from the original start date rather than by
 * stepping from the previous occurrence. That keeps month-end dates stable:
 * a monthly item starting on 31 Jan falls on 28 Feb and then 31 Mar, instead
 * of drifting to the 28th for good.
 */
public enum Recurrence {
    NONE,
    WEEKLY,
    MONTHLY,
    YEARLY;

    public boolean isRecurring() {
        return this != NONE;
    }

    /** The n-th occurrence counted from start (n = 0 is the start date itself). */
    public LocalDate occurrence(LocalDate start, long n) {
        return switch (this) {
            case NONE -> start;
            case WEEKLY -> start.plusWeeks(n);
            case MONTHLY -> start.plusMonths(n);
            case YEARLY -> start.plusYears(n);
        };
    }

    /** The first occurrence strictly after the given date. Only valid for recurring types. */
    public LocalDate firstAfter(LocalDate start, LocalDate date) {
        if (!isRecurring()) {
            throw new IllegalStateException("A non-recurring entry has no next occurrence");
        }
        long n = 0;
        LocalDate candidate = start;
        while (!candidate.isAfter(date)) {
            n++;
            candidate = occurrence(start, n);
        }
        return candidate;
    }

    /** The first occurrence on or after the given date. Only valid for recurring types. */
    public LocalDate firstOnOrAfter(LocalDate start, LocalDate date) {
        return firstAfter(start, date.minusDays(1));
    }

    /** Every occurrence that falls within [from, to], inclusive. */
    public List<LocalDate> occurrencesBetween(LocalDate start, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        if (!isRecurring()) {
            if (!start.isBefore(from) && !start.isAfter(to)) {
                dates.add(start);
            }
            return dates;
        }
        for (long n = 0; ; n++) {
            LocalDate date = occurrence(start, n);
            if (date.isAfter(to)) {
                break;
            }
            if (!date.isBefore(from)) {
                dates.add(date);
            }
        }
        return dates;
    }
}
