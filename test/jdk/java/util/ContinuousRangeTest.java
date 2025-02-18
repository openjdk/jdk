/**
 * @test
 * @bug 8340711
 * @summary Tests for Range implementations in java.util.ContinuousRange class
 * @run junit ContinuousRangeTest
 */

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ContinuousRangeTest {

    private static final LocalDateTime START = LocalDateTime.of(2023, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final LocalDateTime INSTANT_BEFORE = LocalDateTime.of(2022, 12, 31, 23, 59);
    private static final LocalDateTime INTERSECTING_START = LocalDateTime.of(2023, 6, 1, 0, 0);

    static Stream<Arguments> containsTestCases() {
        return Stream.of(
            Arguments.of("Given bounded continuous range when contains instant then true if within bounds",
                ContinuousRange.of(START, END), START.plusMonths(6), true),
            Arguments.of("Given bounded continuous range when contains instant then false if before bounds",
                ContinuousRange.of(START, END), INSTANT_BEFORE, false),
            Arguments.of("Given start-unbounded starting continuous range when contains instant then true for all before end",
                ContinuousRange.unboundedEndingAt(END), START.minusYears(1), true),
            Arguments.of("Given end-unbounded continuous range when contains instant then true for all after start",
                ContinuousRange.unboundedStartingAt(START), END.plusYears(1), true),
            Arguments.of("Given unbounded continuous range when contains instant then always true",
                ContinuousRange.unbounded(), START, true),
            Arguments.of("Given start-unbounded exclusive continuous range when contains instant then false if equal to start",
                ContinuousRange.unboundedEndingAt(START, true), START, false),
            Arguments.of("Given end-unbounded exclusive continuous range when contains instant then false if equal to end",
                ContinuousRange.unboundedStartingAt(END, true), END, false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("containsTestCases")
    @DisplayName("Test contains method")
    void testContains(String description, ContinuousRange<ChronoLocalDateTime<?>> range, ChronoLocalDateTime<?> instant, boolean expected) {
        assertEquals(expected, range.contains(instant), description);
    }

    static Stream<Arguments> overlapsTestCases() {
        var list = List.of(
            Arguments.of("Given two overlapping bounded continuous ranges when overlaps then true",
                ContinuousRange.of(START, END), ContinuousRange.of(INTERSECTING_START, END), true),
            Arguments.of("Given two non-overlapping bounded continuous ranges when overlaps then false",
                ContinuousRange.of(START, END), ContinuousRange.of(END.plusYears(1), END.plusYears(2)), false),
            Arguments.of("Given start-unbounded continuous range when overlaps with bounded then true if overlaps",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.of(INTERSECTING_START, END), true),
            Arguments.of("Given unbounded continuous range when overlaps with anything then always true",
                ContinuousRange.unbounded(), ContinuousRange.of(START, END), true),
            Arguments.of("Given end-unbounded continuous range when overlaps with bounded then true",
                ContinuousRange.unboundedStartingAt(START), ContinuousRange.of(START, END), true),
            Arguments.of("Given end-unbounded continuous range when starts when bounded starts then true",
                ContinuousRange.unboundedStartingAt(START), ContinuousRange.of(START, END), true),
            Arguments.of("Given end-unbounded continuous range when starts when bounded ends then true",
                ContinuousRange.unboundedStartingAt(END), ContinuousRange.of(START, END), true),
            Arguments.of("Given start-unbounded continuous range when end when bounded ends then true",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.of(START, END), true),
            Arguments.of("Given start-unbounded continuous range when end when bounded starts then true",
                ContinuousRange.unboundedEndingAt(START), ContinuousRange.of(START, END), true),
            Arguments.of("Given start-unbounded exclusive continuous range when end when bounded starts then false",
                ContinuousRange.unboundedEndingAt(START, true), ContinuousRange.of(START, END), false),
            Arguments.of("Given end-unbounded exclusive continuous range when starts when bounded ends then false",
                ContinuousRange.unboundedStartingAt(END, true), ContinuousRange.of(START, END), false),
            Arguments.of("Given start-unbounded exclusive continuous range when end of end-unbounded then false",
                ContinuousRange.unboundedEndingAt(START, true), ContinuousRange.unboundedStartingAt(END), false),
            Arguments.of("Given end-unbounded exclusive continuous range when start of start-unbounded then false",
                ContinuousRange.unboundedStartingAt(END, true), ContinuousRange.unboundedEndingAt(START), false)
        );

        return Stream.concat(list.stream(), createViceVersa(list));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("overlapsTestCases")
    @DisplayName("Test overlaps method")
    void testOverlaps(String description, ContinuousRange<ChronoLocalDateTime<?>> range, ContinuousRange<ChronoLocalDateTime<?>> other, boolean expected) {
        assertEquals(expected, range.overlaps(other), description);
    }

    static Stream<Arguments> isBeforeTestCases() {
        var list = List.of(
            Arguments.of("Given bounded continuous range when before another then true if completely before",
                ContinuousRange.of(START, END), ContinuousRange.of(END.plusDays(1), END.plusYears(1)), true)
        );

        var nonInverse = Stream.of(
            Arguments.of("Given two equal continuous ranges when isAfter then false",
                ContinuousRange.of(START, END), ContinuousRange.of(START, END), false),
            Arguments.of("Given bounded continuous range when before another then false if overlaps",
                ContinuousRange.of(START, END), ContinuousRange.of(INTERSECTING_START, END), false),
            Arguments.of("Given unbounded start continuous range when isBefore bounded then false",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.of(START, END), false),
            Arguments.of("Given start-unbounded continuous range when isBefore end-unbounded exclusive starting at end then true",
                ContinuousRange.unboundedEndingAt(START), ContinuousRange.unboundedStartingAt(END, true), true)
        );

        return Stream.concat(
            Stream.concat(list.stream(), createViceVersaInverse(list)),
            nonInverse
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isBeforeTestCases")
    @DisplayName("Test isBefore method")
    void testIsBefore(String description, ContinuousRange.BoundedAtEnd<ChronoLocalDateTime<?>> range,
                      ContinuousRange.BoundedAtStart<ChronoLocalDateTime<?>> other, boolean expected) {
        assertEquals(expected, range.isBefore(other), description);
    }

    static Stream<Arguments> isAfterTestCases() {
        var list = List.of(
            Arguments.of("Given bounded continuous range when after another then true if completely after",
                ContinuousRange.of(END.plusDays(1), END.plusYears(1)), ContinuousRange.of(START, END), true)
        );

        var nonInverse = Stream.of(
            Arguments.of("Given two equal continuous ranges when isAfter then false",
                ContinuousRange.of(START, END), ContinuousRange.of(START, END), false),
            Arguments.of("Given bounded continuous range when after another then false if overlaps",
                ContinuousRange.of(INTERSECTING_START, END), ContinuousRange.of(START, END), false),
            Arguments.of("Given unbounded end continuous range when isAfter bounded then always false",
                ContinuousRange.unboundedStartingAt(START), ContinuousRange.of(START, END), false),
            Arguments.of("Given end-unbounded exclusive continuous range when isAfter bounded then false",
                ContinuousRange.unboundedStartingAt(START, true), ContinuousRange.of(START, END), false),
            Arguments.of("Given end-unbounded continuous range when isAfter start-unbounded exclusive ending at start then true",
                ContinuousRange.unboundedStartingAt(START, true), ContinuousRange.unboundedEndingAt(START), true)
        );

        return Stream.concat(
            Stream.concat(list.stream(), createViceVersaInverse(list)),
            nonInverse
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isAfterTestCases")
    @DisplayName("Test isAfter method")
    void testIsAfter(String description, ContinuousRange.BoundedAtStart<ChronoLocalDateTime<?>> range, ContinuousRange.BoundedAtEnd<ChronoLocalDateTime<?>> other, boolean expected) {
        assertEquals(expected, range.isAfter(other), description);
    }

    static Stream<Arguments> intersectionTestCases() {
        var list = List.of(
            Arguments.of("Given overlapping bounded continuous ranges when intersect then return intersection",
                ContinuousRange.of(START, END), ContinuousRange.of(INTERSECTING_START, END),
                ContinuousRange.of(INTERSECTING_START, END)),
            Arguments.of("Given two equal ranges when intersect then return same continuous range",
                ContinuousRange.of(START, END), ContinuousRange.of(START, END), ContinuousRange.of(START, END)),
            Arguments.of("Given start-unbounded continuous range when intersect with bounded then return bounded",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.of(START, END), ContinuousRange.of(START, END)),
            Arguments.of("Given end-unbounded continuous range when intersect with bounded then return bounded",
                ContinuousRange.unboundedStartingAt(START), ContinuousRange.of(START, END), ContinuousRange.of(START, END)),
            Arguments.of("Given unbounded continuous range when intersect with bounded then return bounded",
                ContinuousRange.unbounded(), ContinuousRange.of(START, END), ContinuousRange.of(START, END)),
            Arguments.of("Given start-unbounded range when intersect with end-unbounded then return bounded continuous range",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.unboundedStartingAt(START), ContinuousRange.of(START, END)),
            Arguments.of("Given non-overlapping bounded continuous ranges when intersect then return empty",
                ContinuousRange.of(START, END), ContinuousRange.of(END.plusDays(1), END.plusYears(1)), new EmptyRange<ChronoLocalDateTime<?>>())
        );

        return Stream.concat(list.stream(), createViceVersa(list));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intersectionTestCases")
    @DisplayName("Test intersection method")
    void testIntersection(
        String description,
        ContinuousRange<ChronoLocalDateTime<?>> range,
        ContinuousRange<ChronoLocalDateTime<?>> other,
        Range<ChronoLocalDateTime<?>> expected
    ) {
        assertEquals(expected, range.intersection(other), description);
    }

    static Stream<Arguments> unionTestCases() {
        var list = List.of(
            Arguments.of("Given overlapping bounded continuous ranges when union then return union",
                ContinuousRange.of(START, END), ContinuousRange.of(INTERSECTING_START, END), ContinuousRange.of(START, END)),
            Arguments.of("Given two equal ranges when union then return same continuous range",
                ContinuousRange.of(START, END), ContinuousRange.of(START, END), ContinuousRange.of(START, END)),
            Arguments.of("Given start-unbounded continuous range when union with bounded ending at end then return unbounded",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.of(START, END), ContinuousRange.unboundedEndingAt(END)),
            Arguments.of("Given end-unbounded continuous range when union with bounded then return unbounded",
                ContinuousRange.unboundedStartingAt(START), ContinuousRange.of(START, END), ContinuousRange.unboundedStartingAt(START)),
            Arguments.of("Given unbounded continuous range when union with bounded then return unbounded",
                ContinuousRange.unbounded(), ContinuousRange.of(START, END), ContinuousRange.unbounded()),
            Arguments.of("Given start-unbounded continuous range when union with end-unbounded then return unbounded",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.unboundedStartingAt(START), ContinuousRange.unbounded()),
            Arguments.of("Given non-overlapping bounded continuous ranges when union then return union",
                ContinuousRange.of(START, END), ContinuousRange.of(END.plusDays(1), END.plusYears(1)),
                new Union<>(ContinuousRange.of(START, END), ContinuousRange.of(END.plusDays(1), END.plusYears(1)))
            )
        );

        return Stream.concat(list.stream(), createViceVersa(list));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unionTestCases")
    @DisplayName("Test union method")
    void testUnion(
        String description,
        ContinuousRange<ChronoLocalDateTime<?>> range,
        ContinuousRange<ChronoLocalDateTime<?>> other,
        Range<ChronoLocalDateTime<?>> expected
    ) {
        assertEquals(expected, range.union(other), description);
    }

    static Stream<Arguments> differenceTestCases() {
        var list = List.of(
            Arguments.of("Given overlapping bounded continuous ranges when difference then return difference",
                ContinuousRange.of(START, END), ContinuousRange.of(INTERSECTING_START, END), ContinuousRange.of(START, INTERSECTING_START)),
            Arguments.of("Given two equal continuous ranges when difference then return empty",
                ContinuousRange.of(START, END), ContinuousRange.of(START, END), new EmptyRange<ChronoLocalDateTime<?>>()),
            Arguments.of("Given start-unbounded continuous range when difference with bounded then return unbounded",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.of(START, END), ContinuousRange.unboundedEndingAt(START)),
            Arguments.of("Given end-unbounded continuous range when difference with bounded then return unbounded",
                ContinuousRange.unboundedStartingAt(START), ContinuousRange.of(START, END), ContinuousRange.unboundedStartingAt(END)),
            Arguments.of("Given unbounded continuous range when difference with bounded then return empty",
                ContinuousRange.unbounded(), ContinuousRange.of(START, END), new Union<>(ContinuousRange.unboundedEndingAt(START), ContinuousRange.unboundedStartingAt(END))),
            Arguments.of("Given start-unbounded continuous range when difference with overlapping end-unbounded then return unbounded ending at start of other",
                ContinuousRange.unboundedEndingAt(END), ContinuousRange.unboundedStartingAt(START), ContinuousRange.unboundedEndingAt(START)),
            Arguments.of("Given non-overlapping bounded continuous ranges when difference then return original",
                ContinuousRange.of(START, END), ContinuousRange.of(END.plusDays(1), END.plusYears(1)), ContinuousRange.of(START, END))
        );

        return list.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("differenceTestCases")
    @DisplayName("Test difference method")
    void testDifference(
        String description,
        ContinuousRange<ChronoLocalDateTime<?>> range,
        ContinuousRange<ChronoLocalDateTime<?>> other,
        Range<ChronoLocalDateTime<?>> expected
    ) {
        assertEquals(expected, range.difference(other), description);
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
