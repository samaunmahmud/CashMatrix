package com.expensetracker.expensetracker;

import com.expensetracker.expensetracker.model.Recurrence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceTest {

    @Test
    void monthlyItemStartingOnThe31stDoesNotDriftToThe28th() {
        LocalDate start = LocalDate.of(2026, 1, 31);

        assertThat(Recurrence.MONTHLY.occurrence(start, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(Recurrence.MONTHLY.occurrence(start, 2)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(Recurrence.MONTHLY.occurrence(start, 3)).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void firstAfterIsStrictlyAfter() {
        LocalDate start = LocalDate.of(2026, 9, 1);

        assertThat(Recurrence.MONTHLY.firstAfter(start, LocalDate.of(2026, 9, 1))).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(Recurrence.MONTHLY.firstAfter(start, LocalDate.of(2026, 9, 15))).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(Recurrence.MONTHLY.firstAfter(start, LocalDate.of(2026, 8, 1))).isEqualTo(start);
    }

    @Test
    void firstOnOrAfterKeepsTodayIfItIsAnOccurrence() {
        LocalDate start = LocalDate.of(2025, 1, 20);

        assertThat(Recurrence.MONTHLY.firstOnOrAfter(start, LocalDate.of(2026, 9, 20))).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(Recurrence.MONTHLY.firstOnOrAfter(start, LocalDate.of(2026, 9, 21))).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void occurrencesBetweenIncludesBothEnds() {
        List<LocalDate> weekly = Recurrence.WEEKLY.occurrencesBetween(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 22));

        assertThat(weekly).containsExactly(
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 22));
    }

    @Test
    void oneOffItemAppearsOnlyInsideItsRange() {
        LocalDate day = LocalDate.of(2026, 9, 10);

        assertThat(Recurrence.NONE.occurrencesBetween(day, day, day)).containsExactly(day);
        assertThat(Recurrence.NONE.occurrencesBetween(day, day.plusDays(1), day.plusDays(9))).isEmpty();
    }

    @Test
    void oneOffItemHasNoNextOccurrence() {
        assertThatThrownBy(() -> Recurrence.NONE.firstAfter(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
