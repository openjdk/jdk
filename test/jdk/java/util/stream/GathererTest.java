/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.*;
import java.util.stream.Gatherer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * @test
 * @summary Testing the Gatherer contract
 * @enablePreview
 * @run junit GathererTest
 */

public class GathererTest {

    final static IntStream streamSizes() {
        return IntStream.of(0, 1, 10, 33, 99, 9999);
    }

    final static Stream<Integer> countTo(int to) {
        return Stream.iterate(1, i -> i + 1).limit(to);
    }

    final class TestException extends RuntimeException {
        TestException(String message) {
            super(message);
        }
    }

    final static class InvocationTracker {
        int initialize;
        int integrate;
        int combine;
        int finish;
    }

    final Gatherer<Integer,Void,Integer> addOne = Gatherer.of(
            Gatherer.Integrator.<Void,Integer,Integer>ofGreedy((vöid, element, downstream) -> downstream.push(element + 1))
    );

    final Gatherer<Integer,Void,Integer> timesTwo = Gatherer.of(
            Gatherer.Integrator.<Void,Integer,Integer>ofGreedy((vöid, element, downstream) -> downstream.push(element * 2))
    );

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testInvocationSemanticsGreedy(int streamSize) {
        var t = new InvocationTracker();
        var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    t.initialize++;
                    return t;
                },
                Gatherer.Integrator.<InvocationTracker,Integer,Integer>ofGreedy((state, e, d) -> {
                    assertSame(t, state);
                    t.integrate++;
                    return d.push(e);
                }),
                (t1, t2) -> {
                    if (t1 != t2) t2.combine++;
                    t1.combine++;
                    return t1;
                },
                (state, d) -> {
                    assertSame(t, state);
                    t.finish++;
                });
        var res = countTo(streamSize).gather(g).toList();
        assertEquals(countTo(streamSize).toList(), res);
        assertEquals(1, t.initialize);
        assertEquals(streamSize, t.integrate);
        assertEquals(0, t.combine);
        assertEquals(1, t.finish);
    }

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testInvocationSemanticsShortCircuit(int streamSize) {
        final int CONSUME_AT_MOST = 5;
        var t = new InvocationTracker();
        var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    t.initialize++;
                    return t;
                },
                (state, e, d) -> {
                    assertSame(t, state);
                    t.integrate++;
                    return d.push(e) && t.integrate < CONSUME_AT_MOST;
                },
                (t1, t2) -> {
                    if (t1 != t2) t2.combine++;
                    t1.combine++;
                    return t1;
                },
                (state, d) -> {
                    assertSame(t, state);
                    t.finish++;
                });
        var res = countTo(streamSize).gather(g).toList();
        assertEquals(countTo(Math.min(streamSize, CONSUME_AT_MOST)).toList(), res);
        assertEquals(t.initialize, 1);
        assertEquals(t.integrate, Math.min(streamSize, CONSUME_AT_MOST));
        assertEquals(t.combine, 0);
        assertEquals(t.finish, 1);
    }

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testEmissionDuringFinisher(int streamSize) {
        var g = Gatherer.<Integer, InvocationTracker, InvocationTracker>of(
                () -> {
                    var t = new InvocationTracker();
                    t.initialize++;
                    return t;
                },
                (t, e, d) -> {
                    t.integrate++;
                    return true;
                },
                (t1, t2) -> {
                    t1.combine++;
                    return t1;
                },
                (t, d) -> {
                    t.finish++;
                    d.push(t);
                });
        var resultList = countTo(streamSize).gather(g).collect(Collectors.toList());
        assertEquals(resultList.size(), 1);

        var t = resultList.get(0);

        assertEquals(t.initialize, 1);
        assertEquals(t.integrate, streamSize);
        assertEquals(t.combine, 0);
        assertEquals(t.finish, 1);
    }

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testInvocationSemanticsShortCircuitParallel(int streamSize) {
        // short-circuit half-way into the sequence
        final Predicate<Integer> takeWhile = j -> j < (streamSize / 2);

        var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    var t = new InvocationTracker();
                    t.initialize++;
                    return t;
                },
                (t, e, d) -> {
                    t.integrate++;
                    return takeWhile.test(e) && d.push(e);
                },
                (t1, t2) -> {
                    t1.initialize += t2.initialize;
                    t1.integrate += t2.integrate;
                    t1.combine += t2.combine + 1; // include this combination
                    t1.finish += t2.finish;
                    return t1;
                },
                (t, d) -> {
                    assertTrue(t.initialize > 0);
                    assertTrue(t.integrate <= streamSize);
                    assertEquals(t.combine + 1,t.initialize);
                    assertEquals(++t.finish, 1);
                });

        var res = countTo(streamSize).parallel().gather(g).toList();
        assertEquals(countTo(streamSize).takeWhile(takeWhile).toList(), res);
    }

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testInvocationSemanticsShortCircuitDuringCollect(int streamSize) {
        final int CONSUME_AT_MOST = 5;
        var t = new InvocationTracker();
        var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    t.initialize++;
                    return t;
                },
                (state, e, d) -> {
                    assertSame(t, state);
                    t.integrate++;
                    return d.push(e) && t.integrate < CONSUME_AT_MOST;
                },
                (t1, t2) -> {
                    if (t1 != t2) t2.combine++;
                    t1.combine++;
                    return t1;
                },
                (state, d) -> {
                    assertSame(t, state);
                    t.finish++;
                });
        var res = countTo(streamSize).gather(g).collect(Collectors.toList());
        assertEquals(countTo(Math.min(streamSize, CONSUME_AT_MOST)).toList(), res);
        assertEquals(t.initialize, 1);
        assertEquals(t.integrate, Math.min(streamSize, CONSUME_AT_MOST));
        assertEquals(t.combine, 0);
        assertEquals(t.finish, 1);
    }

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testCompositionOfStatelessGatherers(int streamSize) {
        var range = countTo(streamSize).toList();
        var gRes = range.stream().gather(addOne.andThen(timesTwo)).toList();
        var rRes = range.stream().map(j -> j + 1).map(j -> j * 2).toList();
        assertEquals(gRes.size(), streamSize);
        assertEquals(rRes.size(), streamSize);
        assertEquals(gRes, rRes);
    }

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testCompositionOfStatefulGatherers(int streamSize) {
        var t1 = new InvocationTracker();
        var g1 = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    t1.initialize++;
                    return t1;
                },
                (state, e, d) -> {
                    assertSame(t1, state);
                    t1.integrate++;
                    return d.push(e);
                },
                (l, r) -> {
                    if (l != r) r.combine++;
                    l.combine++;
                    return l;
                },
                (state, d) -> {
                    assertSame(t1, state);
                    t1.finish++;
                });

        var t2 = new InvocationTracker();
        var g2 = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    t2.initialize++;
                    return t2;
                },
                (state, e, d) -> {
                    assertSame(t2, state);
                    t2.integrate++;
                    return d.push(e);
                },
                (l, r) -> {
                    if (l != r) r.combine++;
                    l.combine++;
                    return l;
                },
                (state, d) -> {
                    assertSame(t2, state);
                    t2.finish++;
                });

        var res = countTo(streamSize).gather(g1.andThen(g2)).toList();
        assertEquals(countTo(streamSize).toList(), res);

        assertEquals(t1.initialize, 1);
        assertEquals(t1.integrate, streamSize);
        assertEquals(t1.combine, 0);
        assertEquals(t1.finish, 1);

        assertEquals(t2.initialize, 1);
        assertEquals(t2.integrate, streamSize);
        assertEquals(t2.combine, 0);
        assertEquals(t2.finish, 1);
    }

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testMassivelyComposedGatherers(int streamSize) {
        final int ITERATIONS = 1000; // Total number of compositions is 1 + (iterations*2)
        Gatherer<Integer,?,Integer> g = addOne;
        for(int i = 0;i < ITERATIONS;++i) {
            g = g.andThen(timesTwo).andThen(addOne);
        }

        g = g.andThen(timesTwo);

        var ref = countTo(streamSize).map(n -> n + 1);
        for(int c = 0;c < ITERATIONS;++c) {
            ref = ref.map(n -> n * 2).map(n -> n + 1);
        }
        ref = ref.map(n -> n * 2);

        var gatherered = countTo(streamSize).gather(g).toList();
        var reference = ref.toList();
        assertEquals(gatherered, reference);
    }

    @Test
    public void testUnboundedEmissions() {
        Gatherer<Integer,?,Integer> g = Gatherer.of(
                    () -> (Void)null,
                    (v,e,d) -> { do {} while(d.push(e)); return false; },
                    (l,r) -> l,
                    (v,d) -> {}
                );
        assertEquals(Stream.of(1).gather(g).limit(1).toList(), List.of(1));
        assertEquals(Stream.of(1).gather(g.andThen(g)).limit(1).toList(), List.of(1));
    }

    @ParameterizedTest
    @MethodSource("streamSizes")
    public void testCompositionSymmetry(int streamSize) {
            var range = countTo(streamSize).toList();
            var consecutiveResult = range.stream().gather(addOne).gather(timesTwo).toList();
            var interspersedResult = range.stream().gather(addOne).map(id -> id).gather(timesTwo).toList();
            var composedResult = range.stream().gather(addOne.andThen(timesTwo)).toList();

            var reference = range.stream().map(j -> j + 1).map(j -> j * 2).toList();

            assertEquals(consecutiveResult.size(), streamSize);
            assertEquals(interspersedResult.size(), streamSize);
            assertEquals(composedResult.size(), streamSize);
            assertEquals(reference.size(), streamSize);

            assertEquals(consecutiveResult, reference);
            assertEquals(interspersedResult, reference);
            assertEquals(composedResult, reference);
    }

    @Test
    public void testExceptionInInitializer() {
        final var expectedMessage = "testExceptionInInitializer()";
        assertThrowsTestException(() ->
            Stream.of(1).gather(
                    Gatherer.<Integer,Integer,Integer>of(
                            () -> { throw new TestException(expectedMessage); },
                            (i, e, d) -> true,
                            (l,r) -> l,
                            (i,d) -> {}
                    )
            ).toList(), expectedMessage);
    }

    @Test
    public void testExceptionInIntegrator() {
        final var expectedMessage = "testExceptionInIntegrator()";
        assertThrowsTestException(() ->
            Stream.of(1).gather(
                    Gatherer.<Integer,Integer,Integer>of(
                            () -> 1,
                            (i, e, d) -> { throw new TestException(expectedMessage); },
                            (l,r) -> l,
                            (i,d) -> {}
                    )
            ).toList()
        , expectedMessage);
    }

    @Test
    public void testExceptionInCombiner() {
        final var expectedMessage = "testExceptionInCombiner()";
        assertThrowsTestException(() ->
            Stream.of(1,2).parallel().gather(
                    Gatherer.<Integer,Integer,Integer>of(
                            () -> 1,
                            (i, e, d) -> true,
                            (l,r) -> { throw new TestException(expectedMessage); },
                            (i,d) -> {}
                    )
            ).toList()
        , expectedMessage);
    }

    @Test
    public void testExceptionInFinisher() {
        final var expectedMessage = "testExceptionInFinisher()";
        assertThrowsTestException(() ->
            Stream.of(1).gather(
                    Gatherer.<Integer,Integer,Integer>of(
                            () -> 1,
                            (i, e, d) -> true,
                            (l,r) -> l,
                            (v, d) -> { throw new TestException(expectedMessage); }
                    )
            ).toList()
        , expectedMessage);
    }

    private final static void assertThrowsTestException(Supplier<?> supplier, String expectedMessage) {
        try {
            var discard = supplier.get();
        } catch (TestException e) {
            assertSame(TestException.class, e.getClass());
            assertEquals(expectedMessage, e.getMessage());
            return;
        }
        fail("Expected TestException but wasn't thrown!");
    }
}
