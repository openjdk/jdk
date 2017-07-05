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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @test
 * @run testng RandomStreamTest
 * @summary test stream methods on Random
 * @author Brian Goetz
 */
public class RandomStreamTest {

    private static final int SIZE = 1000;

    @DataProvider(name = "suppliers")
    public Object[][] randomSuppliers() {
        return new Object[][] {
            {new Random(), SIZE},
            {new SecureRandom(), SIZE}
        };
    }

    @Test(dataProvider = "suppliers")
    public void testRandomIntStream(final Random random, final int count) {
        final List<Integer> destination = new ArrayList<>(count);
        random.ints().limit(count).forEach(destination::add);
        assertEquals(destination.size(), count);
    }

    @Test(dataProvider = "suppliers")
    public void testRandomLongStream(final Random random, final int count) {
        final List<Long> destination = new ArrayList<>(count);
        random.longs().limit(count).forEach(destination::add);
        assertEquals(destination.size(), count);
    }

    @Test(dataProvider = "suppliers")
    public void testRandomDoubleStream(final Random random, final int count) {
        final List<Double> destination = new ArrayList<>(count);
        random.doubles().limit(count).forEach(destination::add);
        random.doubles().limit(count).forEach(d -> assertTrue(d >= 0.0 && d < 1.0));
        assertEquals(destination.size(), count);
    }

    @Test(dataProvider = "suppliers")
    public void testRandomGaussianStream(final Random random, final int count) {
        final List<Double> destination = new ArrayList<>(count);
        random.gaussians().limit(count).forEach(destination::add);
        assertEquals(destination.size(), count);
    }

    @Test
    public void testIntStream() {
        final long seed = System.currentTimeMillis();
        final Random r1 = new Random(seed);
        final int[] a = new int[SIZE];
        for (int i=0; i < SIZE; i++) {
            a[i] = r1.nextInt();
        }

        final Random r2 = new Random(seed); // same seed
        final int[] b = r2.ints().limit(SIZE).toArray();
        assertEquals(a, b);
    }

    @Test
    public void testLongStream() {
        final long seed = System.currentTimeMillis();
        final Random r1 = new Random(seed);
        final long[] a = new long[SIZE];
        for (int i=0; i < SIZE; i++) {
            a[i] = r1.nextLong();
        }

        final Random r2 = new Random(seed); // same seed
        final long[] b = r2.longs().limit(SIZE).toArray();
        assertEquals(a, b);
    }

    @Test
    public void testDoubleStream() {
        final long seed = System.currentTimeMillis();
        final Random r1 = new Random(seed);
        final double[] a = new double[SIZE];
        for (int i=0; i < SIZE; i++) {
            a[i] = r1.nextDouble();
        }

        final Random r2 = new Random(seed); // same seed
        final double[] b = r2.doubles().limit(SIZE).toArray();
        assertEquals(a, b);
    }

    @Test
    public void testGaussianStream() {
        final long seed = System.currentTimeMillis();
        final Random r1 = new Random(seed);
        final double[] a = new double[SIZE];
        for (int i=0; i < SIZE; i++) {
            a[i] = r1.nextGaussian();
        }

        final Random r2 = new Random(seed); // same seed
        final double[] b = r2.gaussians().limit(SIZE).toArray();
        assertEquals(a, b);
    }

    @Test
    public void testThreadLocalIntStream() throws InterruptedException {
        final ExecutorService e = Executors.newFixedThreadPool(10);
        final ThreadLocalRandom tlr = ThreadLocalRandom.current();

        final class RandomTask implements Runnable {
            int[] randoms;

            @Override
            public void run() {
                randoms = tlr.ints().limit(SIZE).toArray();
            }
        }
        final RandomTask[] tasks = new RandomTask[10];
        for (int i=0; i < tasks.length; i++) {
            tasks[i] = new RandomTask();
        }
        for (int i=0; i < tasks.length; i++) {
            e.submit(tasks[i]);
        }
        e.shutdown();
        e.awaitTermination(3, TimeUnit.SECONDS);
        for (int i=1; i < tasks.length; i++) {
            assertFalse(Arrays.equals(tasks[0].randoms, tasks[i].randoms));
        }
    }

    @Test
    public void testThreadLocalLongStream() throws InterruptedException {
        final ExecutorService e = Executors.newFixedThreadPool(10);
        final ThreadLocalRandom tlr = ThreadLocalRandom.current();

        final class RandomTask implements Runnable {
            long[] randoms;

            @Override
            public void run() {
                randoms = tlr.longs().limit(SIZE).toArray();
            }
        }
        final RandomTask[] tasks = new RandomTask[10];
        for (int i=0; i < tasks.length; i++) {
            tasks[i] = new RandomTask();
        }
        for (int i=0; i < tasks.length; i++) {
            e.submit(tasks[i]);
        }
        e.shutdown();
        e.awaitTermination(3, TimeUnit.SECONDS);
        for (int i=1; i < tasks.length; i++) {
            assertFalse(Arrays.equals(tasks[0].randoms, tasks[i].randoms));
        }
    }

    @Test
    public void testThreadLocalDoubleStream() throws InterruptedException {
        final ExecutorService e = Executors.newFixedThreadPool(10);
        final ThreadLocalRandom tlr = ThreadLocalRandom.current();

        final class RandomTask implements Runnable {
            double[] randoms;

            @Override
            public void run() {
                randoms = tlr.doubles().limit(SIZE).toArray();
            }
        }
        final RandomTask[] tasks = new RandomTask[10];
        for (int i=0; i < tasks.length; i++) {
            tasks[i] = new RandomTask();
        }
        for (int i=0; i < tasks.length; i++) {
            e.submit(tasks[i]);
        }
        e.shutdown();
        e.awaitTermination(3, TimeUnit.SECONDS);
        for (int i=1; i < tasks.length; i++) {
            assertFalse(Arrays.equals(tasks[0].randoms, tasks[i].randoms));
        }
    }

    @Test
    public void testThreadLocalGaussianStream() throws InterruptedException {
        final ExecutorService e = Executors.newFixedThreadPool(10);
        final ThreadLocalRandom tlr = ThreadLocalRandom.current();

        final class RandomTask implements Runnable {
            double[] randoms;

            @Override
            public void run() {
                randoms = tlr.gaussians().limit(SIZE).toArray();
            }
        }
        final RandomTask[] tasks = new RandomTask[10];
        for (int i=0; i < tasks.length; i++) {
            tasks[i] = new RandomTask();
        }
        for (int i=0; i < tasks.length; i++) {
            e.submit(tasks[i]);
        }
        e.shutdown();
        e.awaitTermination(3, TimeUnit.SECONDS);
        for (int i=1; i < tasks.length; i++) {
            assertFalse(Arrays.equals(tasks[0].randoms, tasks[i].randoms));
        }
    }

}
