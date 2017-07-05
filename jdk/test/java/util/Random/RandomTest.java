/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

import static org.testng.Assert.*;

/**
 * @test
 * @run testng RandomTest
 * @summary test methods on Random
 * @key randomness
 */
@Test
public class RandomTest {

    // Note: this test was adapted from the 166 TCK ThreadLocalRandomTest test
    // and modified to be a TestNG test

    /*
     * Testing coverage notes:
     *
     * We don't test randomness properties, but only that repeated
     * calls, up to NCALLS tries, produce at least one different
     * result.  For bounded versions, we sample various intervals
     * across multiples of primes.
     */

    // max numbers of calls to detect getting stuck on one value
    static final int NCALLS = 10000;

    // max sampled int bound
    static final int MAX_INT_BOUND = (1 << 28);

    // max sampled long bound
    static final long MAX_LONG_BOUND = (1L << 42);

    // Number of replications for other checks
    static final int REPS = 20;

    /**
     * Repeated calls to nextInt produce at least two distinct results
     */
    public void testNextInt() {
        Random r = new Random();
        int f = r.nextInt();
        int i = 0;
        while (i < NCALLS && r.nextInt() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextLong produce at least two distinct results
     */
    public void testNextLong() {
        Random r = new Random();
        long f = r.nextLong();
        int i = 0;
        while (i < NCALLS && r.nextLong() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextBoolean produce at least two distinct results
     */
    public void testNextBoolean() {
        Random r = new Random();
        boolean f = r.nextBoolean();
        int i = 0;
        while (i < NCALLS && r.nextBoolean() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextFloat produce at least two distinct results
     */
    public void testNextFloat() {
        Random r = new Random();
        float f = r.nextFloat();
        int i = 0;
        while (i < NCALLS && r.nextFloat() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextDouble produce at least two distinct results
     */
    public void testNextDouble() {
        Random r = new Random();
        double f = r.nextDouble();
        int i = 0;
        while (i < NCALLS && r.nextDouble() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextGaussian produce at least two distinct results
     */
    public void testNextGaussian() {
        Random r = new Random();
        double f = r.nextGaussian();
        int i = 0;
        while (i < NCALLS && r.nextGaussian() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * nextInt(negative) throws IllegalArgumentException
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNextIntBoundedNeg() {
        Random r = new Random();
        int f = r.nextInt(-17);
    }

    /**
     * nextInt(bound) returns 0 <= value < bound; repeated calls produce at
     * least two distinct results
     */
    public void testNextIntBounded() {
        Random r = new Random();
        // sample bound space across prime number increments
        for (int bound = 2; bound < MAX_INT_BOUND; bound += 524959) {
            int f = r.nextInt(bound);
            assertTrue(0 <= f && f < bound);
            int i = 0;
            int j;
            while (i < NCALLS &&
                   (j = r.nextInt(bound)) == f) {
                assertTrue(0 <= j && j < bound);
                ++i;
            }
            assertTrue(i < NCALLS);
        }
    }

    /**
     * Invoking sized ints, long, doubles, with negative sizes throws
     * IllegalArgumentException
     */
    public void testBadStreamSize() {
        Random r = new Random();
        executeAndCatchIAE(() -> r.ints(-1L));
        executeAndCatchIAE(() -> r.ints(-1L, 2, 3));
        executeAndCatchIAE(() -> r.longs(-1L));
        executeAndCatchIAE(() -> r.longs(-1L, -1L, 1L));
        executeAndCatchIAE(() -> r.doubles(-1L));
        executeAndCatchIAE(() -> r.doubles(-1L, .5, .6));
    }

    /**
     * Invoking bounded ints, long, doubles, with illegal bounds throws
     * IllegalArgumentException
     */
    public void testBadStreamBounds() {
        Random r = new Random();
        executeAndCatchIAE(() -> r.ints(2, 1));
        executeAndCatchIAE(() -> r.ints(10, 42, 42));
        executeAndCatchIAE(() -> r.longs(-1L, -1L));
        executeAndCatchIAE(() -> r.longs(10, 1L, -2L));

        testDoubleBadOriginBound((o, b) -> r.doubles(10, o, b));
    }

    // An arbitrary finite double value
    static final double FINITE = Math.PI;

    void testDoubleBadOriginBound(BiConsumer<Double, Double> bi) {
        executeAndCatchIAE(() -> bi.accept(17.0, 2.0));
        executeAndCatchIAE(() -> bi.accept(0.0, 0.0));
        executeAndCatchIAE(() -> bi.accept(Double.NaN, FINITE));
        executeAndCatchIAE(() -> bi.accept(FINITE, Double.NaN));
        executeAndCatchIAE(() -> bi.accept(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));

        // Returns NaN
//        executeAndCatchIAE(() -> bi.accept(Double.NEGATIVE_INFINITY, FINITE));
//        executeAndCatchIAE(() -> bi.accept(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));

        executeAndCatchIAE(() -> bi.accept(FINITE, Double.NEGATIVE_INFINITY));

        // Returns Double.MAX_VALUE
//        executeAndCatchIAE(() -> bi.accept(FINITE, Double.POSITIVE_INFINITY));

        executeAndCatchIAE(() -> bi.accept(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY));
        executeAndCatchIAE(() -> bi.accept(Double.POSITIVE_INFINITY, FINITE));
        executeAndCatchIAE(() -> bi.accept(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
    }

    private void executeAndCatchIAE(Runnable r) {
        executeAndCatch(IllegalArgumentException.class, r);
    }

    private void executeAndCatch(Class<? extends Exception> expected, Runnable r) {
        Exception caught = null;
        try {
            r.run();
        }
        catch (Exception e) {
            caught = e;
        }

        assertNotNull(caught,
                      String.format("No Exception was thrown, expected an Exception of %s to be thrown",
                                    expected.getName()));
        Assert.assertTrue(expected.isInstance(caught),
                          String.format("Exception thrown %s not an instance of %s",
                                        caught.getClass().getName(), expected.getName()));
    }

    /**
     * A sequential sized stream of ints generates the given number of values
     */
    public void testIntsCount() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.ints(size).forEach(x -> {
                counter.increment();
            });
            assertEquals(counter.sum(), size);
            size += 524959;
        }
    }

    /**
     * A sequential sized stream of longs generates the given number of values
     */
    public void testLongsCount() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.longs(size).forEach(x -> {
                counter.increment();
            });
            assertEquals(counter.sum(), size);
            size += 524959;
        }
    }

    /**
     * A sequential sized stream of doubles generates the given number of values
     */
    public void testDoublesCount() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.doubles(size).forEach(x -> {
                counter.increment();
            });
            assertEquals(counter.sum(), size);
            size += 524959;
        }
    }

    /**
     * Each of a sequential sized stream of bounded ints is within bounds
     */
    public void testBoundedInts() {
        AtomicInteger fails = new AtomicInteger(0);
        Random r = new Random();
        long size = 12345L;
        for (int least = -15485867; least < MAX_INT_BOUND; least += 524959) {
            for (int bound = least + 2; bound > least && bound < MAX_INT_BOUND; bound += 67867967) {
                final int lo = least, hi = bound;
                r.ints(size, lo, hi).
                        forEach(x -> {
                            if (x < lo || x >= hi)
                                fails.getAndIncrement();
                        });
            }
        }
        assertEquals(fails.get(), 0);
    }

    /**
     * Each of a sequential sized stream of bounded longs is within bounds
     */
    public void testBoundedLongs() {
        AtomicInteger fails = new AtomicInteger(0);
        Random r = new Random();
        long size = 123L;
        for (long least = -86028121; least < MAX_LONG_BOUND; least += 1982451653L) {
            for (long bound = least + 2; bound > least && bound < MAX_LONG_BOUND; bound += Math.abs(bound * 7919)) {
                final long lo = least, hi = bound;
                r.longs(size, lo, hi).
                        forEach(x -> {
                            if (x < lo || x >= hi)
                                fails.getAndIncrement();
                        });
            }
        }
        assertEquals(fails.get(), 0);
    }

    /**
     * Each of a sequential sized stream of bounded doubles is within bounds
     */
    public void testBoundedDoubles() {
        AtomicInteger fails = new AtomicInteger(0);
        Random r = new Random();
        long size = 456;
        for (double least = 0.00011; least < 1.0e20; least *= 9) {
            for (double bound = least * 1.0011; bound < 1.0e20; bound *= 17) {
                final double lo = least, hi = bound;
                r.doubles(size, lo, hi).
                        forEach(x -> {
                            if (x < lo || x >= hi)
                                fails.getAndIncrement();
                        });
            }
        }
        assertEquals(fails.get(), 0);
    }

    /**
     * A parallel unsized stream of ints generates at least 100 values
     */
    public void testUnsizedIntsCount() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 100;
        r.ints().limit(size).parallel().forEach(x -> {
            counter.increment();
        });
        assertEquals(counter.sum(), size);
    }

    /**
     * A parallel unsized stream of longs generates at least 100 values
     */
    public void testUnsizedLongsCount() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 100;
        r.longs().limit(size).parallel().forEach(x -> {
            counter.increment();
        });
        assertEquals(counter.sum(), size);
    }

    /**
     * A parallel unsized stream of doubles generates at least 100 values
     */
    public void testUnsizedDoublesCount() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 100;
        r.doubles().limit(size).parallel().forEach(x -> {
            counter.increment();
        });
        assertEquals(counter.sum(), size);
    }

    /**
     * A sequential unsized stream of ints generates at least 100 values
     */
    public void testUnsizedIntsCountSeq() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 100;
        r.ints().limit(size).forEach(x -> {
            counter.increment();
        });
        assertEquals(counter.sum(), size);
    }

    /**
     * A sequential unsized stream of longs generates at least 100 values
     */
    public void testUnsizedLongsCountSeq() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 100;
        r.longs().limit(size).forEach(x -> {
            counter.increment();
        });
        assertEquals(counter.sum(), size);
    }

    /**
     * A sequential unsized stream of doubles generates at least 100 values
     */
    public void testUnsizedDoublesCountSeq() {
        LongAdder counter = new LongAdder();
        Random r = new Random();
        long size = 100;
        r.doubles().limit(size).forEach(x -> {
            counter.increment();
        });
        assertEquals(counter.sum(), size);
    }

}
