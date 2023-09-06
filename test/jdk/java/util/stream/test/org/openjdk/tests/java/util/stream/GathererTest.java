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
package org.openjdk.tests.java.util.stream;

import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.*;
import java.util.stream.Gatherer;

import static java.util.stream.LambdaTestHelpers.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * GathererTest
 *
 * @author Viktor Klang
 */
@Test
public class GathererTest extends OpTestCase {

    final static List<Integer> TEST_STREAM_SIZES = List.of(0, 1, 10, 33, 99, 9999);

    final static class InvocationTracker {
        int initialize;
        int integrate;
        int combine;
        int finish;
    }

    final Gatherer<Integer,Void,Integer> addOne = Gatherer.of(
            Gatherer.Integrator.<Void,Integer,Integer>ofGreedy((vöid, element, downstream) -> downstream.flush(element + 1)),
            (l,r) -> l
    );

    final Gatherer<Integer,Void,Integer> timesTwo = Gatherer.of(
            Gatherer.Integrator.<Void,Integer,Integer>ofGreedy((vöid, element, downstream) -> downstream.flush(element * 2)),
            (l,r) -> l
    );

    public void testInvocationSemanticsGreedy() {
        for(var i : TEST_STREAM_SIZES) {
            var t = new InvocationTracker();
            var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                    () -> {
                        t.initialize++;
                        return t;
                    },
                    Gatherer.Integrator.<InvocationTracker,Integer,Integer>ofGreedy((state, e, c) -> {
                        assertSame(t, state);
                        t.integrate++;
                        return c.flush(e);
                    }),
                    (t1, t2) -> {
                        if (t1 != t2) t2.combine++;
                        t1.combine++;
                        return t1;
                    },
                    (state, c) -> {
                        assertSame(t, state);
                        t.finish++;
                    });
            var res = countTo(i).stream().gather(g).toList();
            assertEquals(countTo(i), res);
            assertEquals(t.initialize, 1);
            assertEquals(t.integrate, i.intValue());
            assertEquals(t.combine, 0);
            assertEquals(t.finish, 1);
        }
    }

    public void testInvocationSemanticsShortCircuit() {
        final int CONSUME_AT_MOST = 5;
        for(var i : TEST_STREAM_SIZES) {
            var t = new InvocationTracker();
            var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                    () -> {
                        t.initialize++;
                        return t;
                    },
                    (state, e, c) -> {
                        assertSame(t, state);
                        t.integrate++;
                        return c.flush(e) && t.integrate < CONSUME_AT_MOST;
                    },
                    (t1, t2) -> {
                        if (t1 != t2) t2.combine++;
                        t1.combine++;
                        return t1;
                    },
                    (state, c) -> {
                        assertSame(t, state);
                        t.finish++;
                    });
            var res = countTo(i).stream().gather(g).toList();
            assertEquals(countTo(Math.min(i, CONSUME_AT_MOST)), res);
            assertEquals(t.initialize, 1);
            assertEquals(t.integrate, Math.min(i.intValue(), CONSUME_AT_MOST));
            assertEquals(t.combine, 0);
            assertEquals(t.finish, 1);
        }
    }

    public void testEmissionDuringFinisher() {
        for(var i : TEST_STREAM_SIZES) {
            var g = Gatherer.<Integer, InvocationTracker, InvocationTracker>of(
                    () -> {
                        var t = new InvocationTracker();
                        t.initialize++;
                        return t;
                    },
                    (t, e, c) -> {
                        t.integrate++;
                        return true;
                    },
                    (t1, t2) -> {
                        t1.combine++;
                        return t1;
                    },
                    (t, c) -> {
                        t.finish++;
                        c.flush(t);
                    });
            var resultList = countTo(i).stream().gather(g).collect(Collectors.toList());
            assertEquals(resultList.size(), 1);

            var t = resultList.get(0);

            assertEquals(t.initialize, 1);
            assertEquals(t.integrate, i.intValue());
            assertEquals(t.combine, 0);
            assertEquals(t.finish, 1);
        }
    }

    public void testInvocationSemanticsShortCircuitParallel() {
        for(final var i : TEST_STREAM_SIZES) {
            // short-circuit half-way into the sequence
            final Predicate<Integer> takeWhile = j -> j < (i / 2);

            var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                    () -> {
                        var t = new InvocationTracker();
                        t.initialize++;
                        return t;
                    },
                    (t, e, c) -> {
                        t.integrate++;
                        return takeWhile.test(e) && c.flush(e);
                    },
                    (t1, t2) -> {
                        t1.initialize += t2.initialize;
                        t1.integrate += t2.integrate;
                        t1.combine += t2.combine + 1; // include this combination
                        t1.finish += t2.finish;
                        return t1;
                    },
                    (t, c) -> {
                        assertTrue(t.initialize > 0);
                        assertTrue(t.integrate <= i);
                        assertEquals(t.combine + 1,t.initialize);
                        assertEquals(++t.finish, 1);
                    });

            var res = countTo(i).stream().parallel().gather(g).toList();
            assertEquals(countTo(i).stream().takeWhile(takeWhile).toList(), res);

        }
    }

    public void testInvocationSemanticsShortCircuitDuringCollect() {
        final int CONSUME_AT_MOST = 5;
        for(var i : TEST_STREAM_SIZES) {
            var t = new InvocationTracker();
            var g = Gatherer.<Integer, InvocationTracker, Integer>of(
                    () -> {
                        t.initialize++;
                        return t;
                    },
                    (state, e, c) -> {
                        assertSame(t, state);
                        t.integrate++;
                        return c.flush(e) && t.integrate < CONSUME_AT_MOST;
                    },
                    (t1, t2) -> {
                        if (t1 != t2) t2.combine++;
                        t1.combine++;
                        return t1;
                    },
                    (state, c) -> {
                        assertSame(t, state);
                        t.finish++;
                    });
            var res = countTo(i).stream().collect(g.collect(Collectors.toList()));
            assertEquals(countTo(Math.min(i, CONSUME_AT_MOST)), res);
            assertEquals(t.initialize, 1);
            assertEquals(t.integrate, Math.min(i.intValue(), CONSUME_AT_MOST));
            assertEquals(t.combine, 0);
            assertEquals(t.finish, 1);
        }
    }

    public void testCompositionOfStatelessGatherers() {
        for(var i : TEST_STREAM_SIZES) {
            var range = countTo(i);
            var gRes = range.stream().gather(addOne.andThen(timesTwo)).toList();
            var rRes = range.stream().map(j -> j + 1).map(j -> j * 2).toList();
            assertEquals(gRes.size(), i.intValue());
            assertEquals(rRes.size(), i.intValue());
            assertEquals(gRes, rRes);
        }
    }

    public void testCompositionOfStatefulGatherers() {
        for(var i : TEST_STREAM_SIZES) {
            var t1 = new InvocationTracker();
            var g1 = Gatherer.<Integer, InvocationTracker, Integer>of(
                    () -> {
                        t1.initialize++;
                        return t1;
                    },
                    (state, e, c) -> {
                        assertSame(t1, state);
                        t1.integrate++;
                        return c.flush(e);
                    },
                    (l, r) -> {
                        if (l != r) r.combine++;
                        l.combine++;
                        return l;
                    },
                    (state, c) -> {
                        assertSame(t1, state);
                        t1.finish++;
                    });

            var t2 = new InvocationTracker();
            var g2 = Gatherer.<Integer, InvocationTracker, Integer>of(
                    () -> {
                        t2.initialize++;
                        return t2;
                    },
                    (state, e, c) -> {
                        assertSame(t2, state);
                        t2.integrate++;
                        return c.flush(e);
                    },
                    (l, r) -> {
                        if (l != r) r.combine++;
                        l.combine++;
                        return l;
                    },
                    (state, c) -> {
                        assertSame(t2, state);
                        t2.finish++;
                    });

            var res = countTo(i).stream().gather(g1.andThen(g2)).toList();
            assertEquals(countTo(i), res);

            assertEquals(t1.initialize, 1);
            assertEquals(t1.integrate, i.intValue());
            assertEquals(t1.combine, 0);
            assertEquals(t1.finish, 1);

            assertEquals(t2.initialize, 1);
            assertEquals(t2.integrate, i.intValue());
            assertEquals(t2.combine, 0);
            assertEquals(t2.finish, 1);
        }
    }

    public void testMassivelyComposedGatherers() {
        final int ITERATIONS = 1000; // Total number of compositions is 1 + (iterations*2)
        Gatherer<Integer,?,Integer> g = addOne;
        for(int i = 0;i < ITERATIONS;++i) {
            g = g.andThen(timesTwo).andThen(addOne);
        }
        g = g.andThen(timesTwo);

        for(var i : TEST_STREAM_SIZES) {
            var ref = countTo(i).stream().map(n -> n + 1);
            for(int c = 0;c < ITERATIONS;++c) {
                ref = ref.map(n -> n * 2).map(n -> n + 1);
            }
            ref = ref.map(n -> n * 2);

            var gatherered = countTo(i).stream().gather(g).toList();
            var reference = ref.toList();
            assertEquals(gatherered, reference);
        }
    }

    public void testUnboundedEmissions() {
        Gatherer<Integer,?,Integer> g = Gatherer.of(
                    () -> (Void)null,
                    (v,e,d) -> { do {} while(d.flush(e)); return false; },
                    (l,r) -> l,
                    (v,d) -> {}
                );
        assertEquals(Stream.of(1).gather(g).limit(1).toList(), List.of(1));
        assertEquals(Stream.of(1).gather(g.andThen(g)).limit(1).toList(), List.of(1));
    }

    public void testCompositionSymmetry() {
        for(var i : TEST_STREAM_SIZES) {
            var range = countTo(i);
            var consecutiveResult = range.stream().gather(addOne).gather(timesTwo).toList();
            var interspersedResult = range.stream().gather(addOne).map(id -> id).gather(timesTwo).toList();
            var composedResult = range.stream().gather(addOne.andThen(timesTwo)).toList();
            var collectorResult = range.stream().collect(addOne.collect(timesTwo.collect(Collectors.toList())));

            var reference = range.stream().map(j -> j + 1).map(j -> j * 2).toList();

            assertEquals(consecutiveResult.size(), i.intValue());
            assertEquals(interspersedResult.size(), i.intValue());
            assertEquals(composedResult.size(), i.intValue());
            assertEquals(collectorResult.size(), i.intValue());
            assertEquals(reference.size(), i.intValue());

            assertEquals(consecutiveResult, reference);
            assertEquals(interspersedResult, reference);
            assertEquals(composedResult, reference);
            assertEquals(collectorResult, reference);
        }
    }

    public void testExceptionInInitializer() {
        final var expectedMessage = "testExceptionInInitializer()";
        assertThrowsRE(() ->
            Stream.of(1).gather(
                    Gatherer.<Integer,Void,Integer>ofSequential(
                            () -> { throw new RuntimeException(expectedMessage); },
                            (v, e, d) -> false
                    )
            ).toList(), expectedMessage);
    }
    

    public void testExceptionInIntegrator() {
        final var expectedMessage = "testExceptionInIntegrator()";
        assertThrowsRE(() ->
            Stream.of(1).gather(
                    Gatherer.<Integer,Integer>ofSequential(
                            (v, e, d) -> { throw new RuntimeException(expectedMessage); }
                    )
            ).toList()
        , expectedMessage);
    }

    public void testExceptionInCombiner() {
        final var expectedMessage = "testExceptionInCombiner()";
        assertThrowsRE(() ->
            Stream.of(1,2).parallel().gather(
                    Gatherer.<Integer,Integer,Integer>of(
                            () -> 1,
                            (i, e, d) -> true,
                            (l,r) -> { throw new RuntimeException(expectedMessage); },
                            (i,d) -> {}
                    )
            ).toList()
        , expectedMessage);
    }

    public void testExceptionInFinisher() {
        final var expectedMessage = "testExceptionInFinisher()";
        assertThrowsRE(() ->
            Stream.of(1).gather(
                    Gatherer.<Integer,Integer>ofSequential(
                            (v, e, d) -> true,
                            (v, d) -> { throw new RuntimeException(expectedMessage); }
                    )
            ).toList()
        , expectedMessage);
    }

    private final static void assertThrowsRE(Supplier<?> supplier, String expectedMessage) {
        try {
            var discard = supplier.get();
        } catch (RuntimeException e) {
            assertSame(e.getClass(), RuntimeException.class);
            assertEquals(e.getMessage(), expectedMessage);
            return;
        }
        fail("Expected RuntimeException but wasn't thrown!");
    }
}
