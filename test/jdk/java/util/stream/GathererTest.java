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
import static java.util.stream.DefaultMethodStreams.delegateTo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * @test
 * @summary Testing the Gatherer contract
 * @enablePreview
 * @library /lib/testlibrary/bootlib
 * @build java.base/java.util.stream.DefaultMethodStreams
 * @run junit GathererTest
 */

public class GathererTest {

    record Config(int streamSize, boolean parallel, boolean defaultImpl) {

        Stream<Integer> countTo(int n) {
            return Stream.iterate(1, i -> i + 1).limit(n);
        }

        Stream<Integer> stream() {
            return wrapStream(countTo(streamSize));
        }

        <R> Stream<R> wrapStream(Stream<R> stream) {
            stream = parallel ? stream.parallel() : stream.sequential();
            stream = defaultImpl ? delegateTo(stream) : stream;
            return stream;
        }

        List<Integer> list() {
            return stream().toList();
        }
    }

    final static Stream<Config> configurations() {
        return Stream.of(0,1,10,33,99,9999)
                .flatMap(size ->
                        Stream.of(false, true)
                                .flatMap(parallel ->
                                        Stream.of(false, true).map( defaultImpl ->
                                                new Config(size, parallel,
                                                        defaultImpl)) )
                );
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

        void copyFrom(InvocationTracker other) {
            initialize = other.initialize;
            integrate = other.integrate;
            combine = other.combine;
            finish = other.finish;
        }

        void combine(InvocationTracker other) {
            if (other != this) {
                initialize += other.initialize;
                integrate += other.integrate;
                combine += other.combine + 1; // track this merge
                finish += other.finish;
            }
        }
    }

    final Gatherer<Integer,Void,Integer> addOne = Gatherer.of(
            Gatherer.Integrator.<Void,Integer,Integer>ofGreedy((vöid, element, downstream) -> downstream.push(element + 1))
    );

    final Gatherer<Integer,Void,Integer> timesTwo = Gatherer.of(
            Gatherer.Integrator.<Void,Integer,Integer>ofGreedy((vöid, element, downstream) -> downstream.push(element * 2))
    );

    @ParameterizedTest
    @MethodSource("configurations")
    public void testInvocationSemanticsGreedy(Config config) {
        var result = new InvocationTracker();
        var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    var t = new InvocationTracker();
                    t.initialize++;
                    return t;
                },
                Gatherer.Integrator.<InvocationTracker,Integer,Integer>ofGreedy((t, e, d) -> {
                    t.integrate++;
                    return d.push(e);
                }),
                (t1, t2) -> {
                    t1.combine(t2);
                    return t1;
                },
                (t, d) -> {
                    t.finish++;
                    result.copyFrom(t);
                });
        var res = config.stream().gather(g).toList();
        assertEquals(config.countTo(config.streamSize).toList(), res);
        if (config.parallel) {
            assertTrue(result.initialize > 0);
            assertEquals(config.streamSize, result.integrate);
            assertTrue(config.streamSize < 2 || result.combine > 0);
            assertEquals(1, result.finish);
        } else {
            assertEquals(1, result.initialize);
            assertEquals(config.streamSize, result.integrate);
            assertEquals(0, result.combine);
            assertEquals(1, result.finish);
        }
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testInvocationSemanticsShortCircuit(Config config) {
        final int CONSUME_UNTIL = Math.min(config.streamSize, 5);
        var result = new InvocationTracker();
        var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    var t = new InvocationTracker();
                    t.initialize++;
                    return t;
                },
                (t, e, d) -> {
                    ++t.integrate;
                    return e <= CONSUME_UNTIL && d.push(e) && e != CONSUME_UNTIL;
                },
                (t1, t2) -> {
                    t1.combine(t2);
                    return t1;
                },
                (t, d) -> {
                    t.finish++;
                    result.copyFrom(t);
                });
        var res = config.stream().gather(g).toList();
        assertEquals(config.countTo(CONSUME_UNTIL).toList(), res);
        if (config.parallel) {
            assertTrue(result.initialize > 0);
            assertEquals(CONSUME_UNTIL, result.integrate);
            assertTrue(result.combine >= 0); // We can't guarantee split sizes
            assertEquals(1, result.finish);
        } else {
            assertEquals(1, result.initialize);
            assertEquals(CONSUME_UNTIL, result.integrate);
            assertEquals(0, result.combine);
            assertEquals(1, result.finish);
        }
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testEmissionDuringFinisher(Config config) {
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
                    t1.combine(t2);
                    return t1;
                },
                (t, d) -> {
                    t.finish++;
                    d.push(t);
                });
        var resultList = config.stream().gather(g).collect(Collectors.toList());
        assertEquals(resultList.size(), 1);

        var t = resultList.get(0);

        if (config.parallel) {
            assertTrue(t.initialize > 0);
            assertEquals(config.streamSize, t.integrate);
            assertTrue(config.streamSize < 2 || t.combine > 0);
            assertEquals(1, t.finish);
        } else {
            assertEquals(1, t.initialize);
            assertEquals(config.streamSize, t.integrate);
            assertEquals(0, t.combine);
            assertEquals(1, t.finish);
        }
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testInvocationSemanticsShortCircuitDuringCollect(Config config) {
        final int CONSUME_UNTIL = Math.min(config.streamSize, 5);
        var result = new InvocationTracker();
        var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    var t = new InvocationTracker();
                    t.initialize++;
                    return t;
                },
                (t, e, d) -> {
                    t.integrate++;
                    return e <= CONSUME_UNTIL && d.push(e) && e != CONSUME_UNTIL;
                },
                (t1, t2) -> {
                    t1.combine(t2);
                    return t1;
                },
                (t, d) -> {
                    t.finish++;
                    result.copyFrom(t);
                });
        var res = config.stream().gather(g).collect(Collectors.toList());
        assertEquals(config.countTo(CONSUME_UNTIL).toList(), res);
        if (config.parallel) {
            assertTrue(result.initialize > 0);
            assertEquals(CONSUME_UNTIL, result.integrate);
            assertTrue(result.combine >= 0); // We can't guarantee split sizes
            assertEquals(result.finish, 1);
        } else {
            assertEquals(result.initialize, 1);
            assertEquals(CONSUME_UNTIL, result.integrate);
            assertEquals(result.combine, 0);
            assertEquals(result.finish, 1);
        }
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testCompositionOfStatelessGatherers(Config config) {
        var range = config.stream().toList();
        var gRes = range.stream().gather(addOne.andThen(timesTwo)).toList();
        var rRes = range.stream().map(j -> j + 1).map(j -> j * 2).toList();
        assertEquals(config.streamSize, gRes.size());
        assertEquals(config.streamSize, rRes.size());
        assertEquals(gRes, rRes);
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testCompositionOfStatefulGatherers(Config config) {
        var t1 = new InvocationTracker();
        var g1 = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    var t = new InvocationTracker();
                    t.initialize++;
                    return t;
                },
                (t, e, d) -> {
                    t.integrate++;
                    return d.push(e);
                },
                (l, r) -> {
                    l.combine(r);
                    return l;
                },
                (t, d) -> {
                    t.finish++;
                    t1.copyFrom(t);
                });

        var t2 = new InvocationTracker();
        var g2 = Gatherer.<Integer, InvocationTracker, Integer>of(
                () -> {
                    var t = new InvocationTracker();
                    t.initialize++;
                    return t;
                },
                (t, e, d) -> {
                    t.integrate++;
                    return d.push(e);
                },
                (l, r) -> {
                    l.combine(r);
                    return l;
                },
                (t, d) -> {
                    t.finish++;
                    t2.copyFrom(t);
                });

        var res = config.stream().gather(g1.andThen(g2)).toList();
        assertEquals(config.stream().toList(), res);

        if (config.parallel) {
            assertTrue(t1.initialize > 0);
            assertEquals(config.streamSize, t1.integrate);
            assertTrue(config.streamSize < 2 || t1.combine > 0);
            assertEquals(1, t1.finish);

            assertTrue(t2.initialize > 0);
            assertEquals(config.streamSize, t2.integrate);
            assertTrue(config.streamSize < 2 || t2.combine > 0);
            assertEquals(1, t2.finish);
        } else {
            assertEquals(1, t1.initialize);
            assertEquals(config.streamSize, t1.integrate);
            assertEquals(0, t1.combine);
            assertEquals(1, t1.finish);

            assertEquals(1, t2.initialize);
            assertEquals(config.streamSize, t2.integrate);
            assertEquals(0, t2.combine);
            assertEquals(1, t2.finish);
        }
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testMassivelyComposedGatherers(Config config) {
        final int ITERATIONS = 256; // Total number of compositions is 1 + (iterations*2)
        Gatherer<Integer,?,Integer> g = addOne;
        for(int i = 0;i < ITERATIONS;++i) {
            g = g.andThen(timesTwo).andThen(addOne);
        }

        g = g.andThen(timesTwo);

        var ref = config.stream().map(n -> n + 1);
        for(int c = 0; c < ITERATIONS; ++c) {
            ref = ref.map(n -> n * 2).map(n -> n + 1);
        }
        ref = ref.map(n -> n * 2);

        var gatherered = config.stream().gather(g).toList();
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
    @MethodSource("configurations")
    public void testCompositionSymmetry(Config config) {
            var consecutiveResult = config.stream().gather(addOne).gather(timesTwo).toList();
            var interspersedResult = config.stream().gather(addOne).map(id -> id).gather(timesTwo).toList();
            var composedResult = config.stream().gather(addOne.andThen(timesTwo)).toList();

            var reference = config.stream().map(j -> j + 1).map(j -> j * 2).toList();

            assertEquals(config.streamSize, consecutiveResult.size());
            assertEquals(config.streamSize, interspersedResult.size());
            assertEquals(config.streamSize, composedResult.size());
            assertEquals(config.streamSize, reference.size());

            assertEquals(consecutiveResult, reference);
            assertEquals(interspersedResult, reference);
            assertEquals(composedResult, reference);
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testExceptionInInitializer(Config config) {
        final var expectedMessage = "testExceptionInInitializer()";
        assertThrowsTestException(() ->
            config.stream().gather(
                    Gatherer.<Integer,Integer,Integer>of(
                            () -> { throw new TestException(expectedMessage); },
                            (i, e, d) -> true,
                            (l,r) -> l,
                            (i,d) -> {}
                    )
            ).toList(), expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testExceptionInIntegrator(Config config) {
        if (config.streamSize < 1) return; // No exceptions expected

        final var expectedMessage = "testExceptionInIntegrator()";
        assertThrowsTestException(() ->
            config.stream().gather(
                    Gatherer.<Integer,Integer,Integer>of(
                            () -> 1,
                            (i, e, d) -> { throw new TestException(expectedMessage); },
                            (l,r) -> l,
                            (i,d) -> {}
                    )
            ).toList()
        , expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testExceptionInCombiner(Config config) {
        if (config.streamSize < 2 || !config.parallel) return; // No exceptions expected

        final var expectedMessage = "testExceptionInCombiner()";
        assertThrowsTestException(() ->
            config.stream().gather(
                    Gatherer.<Integer,Integer,Integer>of(
                            () -> 1,
                            (i, e, d) -> true,
                            (l,r) -> { throw new TestException(expectedMessage); },
                            (i,d) -> {}
                    )
            ).toList()
        , expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testExceptionInFinisher(Config config) {
        final var expectedMessage = "testExceptionInFinisher()";
        assertThrowsTestException(() ->
            config.stream().gather(
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
