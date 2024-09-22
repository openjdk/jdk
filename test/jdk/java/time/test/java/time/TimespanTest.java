package test.java.time;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.Timespan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// mock bug id
/*
 * @test
 * @bug 8304400
 * @summary Mock summary
 * @run junit TimespanTest
 */
public class TimespanTest {

    private static final LocalDateTime START = LocalDateTime.of(2023, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final LocalDateTime INSTANT_BEFORE = LocalDateTime.of(2022, 12, 31, 23, 59);
    private static final LocalDateTime INTERSECTING_START = LocalDateTime.of(2023, 6, 1, 0, 0);

    static Stream<Arguments> containsTestCases() {
        return Stream.of(
            Arguments.of("Given bounded timespan when contains instant then true if within bounds",
                Timespan.of(START, END), START.plusMonths(6), true),
            Arguments.of("Given bounded timespan when contains instant then false if before bounds",
                Timespan.of(START, END), INSTANT_BEFORE, false),
            Arguments.of("Given start-unbounded starting timespan when contains instant then true for all before end",
                Timespan.unboundedEndingAt(END), START.minusYears(1), true),
            Arguments.of("Given end-unbounded timespan when contains instant then true for all after start",
                Timespan.unboundedStartAt(START), END.plusYears(1), true),
            Arguments.of("Given unbounded timespan when contains instant then always true",
                Timespan.unbounded(), START, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("containsTestCases")
    @DisplayName("Test contains method")
    void testContains(String description, Timespan timespan, ChronoLocalDateTime<?> instant, boolean expected) {
        assertEquals(expected, timespan.contains(instant), description);
    }

    static Stream<Arguments> overlapsTestCases() {
        var list = List.of(
            Arguments.of("Given two overlapping bounded timespans when overlaps then true",
                Timespan.of(START, END), Timespan.of(INTERSECTING_START, END), true),
            Arguments.of("Given two non-overlapping bounded timespans when overlaps then false",
                Timespan.of(START, END), Timespan.of(END.plusYears(1), END.plusYears(2)), false),
            Arguments.of("Given start-unbounded timespan when overlaps with bounded then true if overlaps",
                Timespan.unboundedEndingAt(END), Timespan.of(INTERSECTING_START, END), true),
            Arguments.of("Given unbounded timespan when overlaps with anything then always true",
                Timespan.unbounded(), Timespan.of(START, END), true),
            Arguments.of("Given end-unbounded timespan when overlaps with bounded then true",
                Timespan.unboundedStartAt(START), Timespan.of(START, END), true),
            Arguments.of("Given end-unbounded timespan when starts when bounded starts then true",
                Timespan.unboundedStartAt(START), Timespan.of(START, END), true),
            Arguments.of("Given end-unbounded timespan when starts when bounded ends then true",
                Timespan.unboundedStartAt(END), Timespan.of(START, END), true),
            Arguments.of("Given start-unbounded timespan when end when bounded ends then true",
                Timespan.unboundedEndingAt(END), Timespan.of(START, END), true),
            Arguments.of("Given start-unbounded timespan when end when bounded starts then true",
                Timespan.unboundedEndingAt(START), Timespan.of(START, END), true)
        );

        return Stream.concat(list.stream(), createViceVersa(list));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("overlapsTestCases")
    @DisplayName("Test overlaps method")
    void testOverlaps(String description, Timespan timespan, Range<LocalDateTime> other, boolean expected) {
        assertEquals(expected, timespan.overlaps(other), description);
    }

    static Stream<Arguments> isBeforeTestCases() {
        var list = List.of(
            Arguments.of("Given bounded timespan when before another then true if completely before",
                Timespan.of(START, END), Timespan.of(END.plusDays(1), END.plusYears(1)), true)
        );

        var nonInverse = Stream.of(
            Arguments.of("Given two equal timespans when isAfter then false",
                Timespan.of(START, END), Timespan.of(START, END), false),
            Arguments.of("Given bounded timespan when before another then false if overlaps",
                Timespan.of(START, END), Timespan.of(INTERSECTING_START, END), false),
            Arguments.of("Given unbounded start timespan when isBefore bounded then false",
                Timespan.unboundedEndingAt(END), Timespan.of(START, END), false)
        );

        return Stream.concat(
            Stream.concat(list.stream(), createViceVersaInverse(list)),
            nonInverse
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isBeforeTestCases")
    @DisplayName("Test isBefore method")
    void testIsBefore(String description, Timespan timespan, Range<LocalDateTime> other, boolean expected) {
        assertEquals(expected, timespan.isBefore(other), description);
    }

    static Stream<Arguments> isAfterTestCases() {
        var list = List.of(
            Arguments.of("Given bounded timespan when after another then true if completely after",
                Timespan.of(END.plusDays(1), END.plusYears(1)), Timespan.of(START, END), true)
        );

        var nonInverse = Stream.of(
            Arguments.of("Given two equal timespans when isAfter then false",
                Timespan.of(START, END), Timespan.of(START, END), false),
            Arguments.of("Given bounded timespan when after another then false if overlaps",
                Timespan.of(INTERSECTING_START, END), Timespan.of(START, END), false),
            Arguments.of("Given unbounded end timespan when isAfter bounded then always false",
                Timespan.unboundedStartAt(START), Timespan.of(START, END), false)
        );

        return Stream.concat(
            Stream.concat(list.stream(), createViceVersaInverse(list)),
            nonInverse
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isAfterTestCases")
    @DisplayName("Test isAfter method")
    void testIsAfter(String description, Timespan timespan, Range<LocalDateTime> other, boolean expected) {
        assertEquals(expected, timespan.isAfter(other), description);
    }

    static Stream<Arguments> gapTestCases() {
        var list = List.of(
            Arguments.of("Given bounded timespans when gap exists then return gap",
                Timespan.of(START, END), Timespan.of(END.plusDays(1), END.plusYears(1)),
                Optional.of(Timespan.of(END, END.plusDays(1)))),
            Arguments.of("Given overlapping bounded timespans when gap then return empty",
                Timespan.of(START, END), Timespan.of(INTERSECTING_START, END), Optional.empty())
        );

        return Stream.concat(list.stream(), createViceVersa(list));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gapTestCases")
    @DisplayName("Test gap method")
    void testGap(String description, Timespan timespan, Range<LocalDateTime> other, Optional<Range<LocalDateTime>> expected) {
        assertEquals(expected, timespan.gap(other), description);
    }

    static Stream<Arguments> intersectionTestCases() {
        var list = List.of(
            Arguments.of("Given overlapping bounded timespans when intersect then return intersection",
                Timespan.of(START, END), Timespan.of(INTERSECTING_START, END),
                Optional.of(Timespan.of(INTERSECTING_START, END))),
            Arguments.of("Given non-overlapping bounded timespans when intersect then return empty",
                Timespan.of(START, END), Timespan.of(END.plusDays(1), END.plusYears(1)), Optional.empty())
        );

        return Stream.concat(list.stream(), createViceVersa(list));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intersectionTestCases")
    @DisplayName("Test intersection method")
    void testIntersection(String description, Timespan timespan, Range<LocalDateTime> other, Optional<Range<LocalDateTime>> expected) {
        assertEquals(expected, timespan.intersection(other), description);
    }

    static Stream<Arguments> unionTestCases() {
        var list = List.of(
            Arguments.of("Given overlapping bounded timespans when union then return combined span",
                Timespan.of(START, END), Timespan.of(INTERSECTING_START, END),
                new Range[] {Timespan.of(START, END)}),
            Arguments.of("Given non-overlapping bounded timespans when union then return separate spans",
                Timespan.of(START, END), Timespan.of(END.plusDays(1), END.plusYears(1)),
                new Range[] {Timespan.of(START, END), Timespan.of(END.plusDays(1), END.plusYears(1))})
        );

        return Stream.concat(list.stream(), createViceVersa(list));
    }

    @ParameterizedTest
    @MethodSource("unionTestCases")
    @DisplayName("Test union method")
    void testUnion(String description, Timespan timespan, Range<LocalDateTime> other, Range<?>[] expected) {
        assertIterableEquals(Arrays.stream(expected).collect(Collectors.toSet()), Arrays.stream(timespan.union(other)).collect(Collectors.toSet()), description);
    }

    private static Stream<Arguments> createViceVersa(List<Arguments> testCases) {
        return testCases.stream().map(a -> Arguments.of(
            a.get()[0] + " (vice versa test)",
            a.get()[2],
            a.get()[1],
            a.get()[3]
        ));
    }

    private static Stream<Arguments> createViceVersaInverse(List<Arguments> testCases) {
        return testCases.stream().map(a -> Arguments.of(
            a.get()[0] + " (vice versa inverse test)",
            a.get()[2],
            a.get()[1],
            !((boolean) a.get()[3])
        ));
    }
}
