/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8351045 8351996
 * @enablePreview
 * @comment Remove preview if ScopedValue is finalized
 * @summary tests for class-specific values
 * @library /test/lib
 * @run junit ClassValueTest
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jdk.test.lib.util.ForceGC;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jrose
 */
final class ClassValueTest {
    static String nameForCV1(Class<?> type) {
        return "CV1:" + type.getName();
    }
    int countForCV1;
    final ClassValue<String> CV1 = new CV1();
    private class CV1 extends ClassValue<String> {
        protected String computeValue(Class<?> type) {
            countForCV1++;
            return nameForCV1(type);
        }
    }

    static final Class<?>[] CLASSES = {
        String.class,
        Integer.class,
        int.class,
        boolean[].class,
        char[][].class,
        ClassValueTest.class
    };

    @Test
    public void testGet() {
        countForCV1 = 0;
        for (Class<?> c : CLASSES) {
            assertEquals(nameForCV1(c), CV1.get(c));
        }
        assertEquals(CLASSES.length, countForCV1);
        for (Class<?> c : CLASSES) {
            assertEquals(nameForCV1(c), CV1.get(c));
        }
        assertEquals(CLASSES.length, countForCV1);
    }

    @Test
    public void testRemove() {
        for (Class<?> c : CLASSES) {
            CV1.get(c);
        }
        countForCV1 = 0;
        int REMCOUNT = 3;
        for (int i = 0; i < REMCOUNT; i++) {
            CV1.remove(CLASSES[i]);
        }
        assertEquals(0, countForCV1);  // no change
        for (Class<?> c : CLASSES) {
            assertEquals(nameForCV1(c), CV1.get(c));
        }
        assertEquals(REMCOUNT, countForCV1);
    }

    static String nameForCVN(Class<?> type, int n) {
        return "CV[" + n + "]" + type.getName();
    }
    int countForCVN;
    class CVN extends ClassValue<String> {
        final int n;
        CVN(int n) { this.n = n; }
        protected String computeValue(Class<?> type) {
            countForCVN++;
            return nameForCVN(type, n);
        }
    };

    @Test
    public void testGetMany() {
        int CVN_COUNT1 = 100, CVN_COUNT2 = 100;
        CVN cvns[] = new CVN[CVN_COUNT1 * CVN_COUNT2];
        for (int n = 0; n < cvns.length; n++) {
            cvns[n] = new CVN(n);
        }
        countForCVN = 0;
        for (int pass = 0; pass <= 2; pass++) {
            for (int i1 = 0; i1 < CVN_COUNT1; i1++) {
                eachClass:
                for (Class<?> c : CLASSES) {
                    for (int i2 = 0; i2 < CVN_COUNT2; i2++) {
                        int n = i1*CVN_COUNT2 + i2;
                        assertEquals(0, countForCVN);
                        assertEquals(nameForCVN(c, n), cvns[n].get(c));
                        cvns[n].get(c);  //get it again
                        //System.out.println("getting "+n+":"+cvns[n].get(c));
                        boolean doremove = (((i1 + i2) & 3) == 0);
                        switch (pass) {
                        case 0:
                            assertEquals(1, countForCVN);
                            break;
                        case 1:
                            // remove on middle pass
                            assertEquals(0, countForCVN);
                            if (doremove) {
                                //System.out.println("removing "+n+":"+cvns[n].get(c));
                                cvns[n].remove(c);
                                assertEquals(0, countForCVN);
                            }
                            break;
                        case 2:
                            assertEquals(doremove ? 1 : 0, countForCVN);
                            break;
                        }
                        countForCVN = 0;
                        if (i1 > i2 && i1 < i2+5)  continue eachClass;  // leave diagonal gap
                    }
                }
            }
        }
        assertEquals(0, countForCVN);
        System.out.println("[rechecking values]");
        for (int i = 0; i < cvns.length * 10; i++) {
            int n = i % cvns.length;
            for (Class<?> c : CLASSES) {
                assertEquals(nameForCVN(c, n), cvns[n].get(c));
            }
        }
    }

    private static final long COMPUTE_TIME_MILLIS = 100;
    // Adjust this timeout to fail faster for test stalls
    private static final Duration TIMEOUT = Duration.ofNanos((long) (
            Duration.of(1, ChronoUnit.MINUTES).toNanos()
                    * Double.parseDouble(System.getProperty("test.timeout.factor", "1.0"))));

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
                fail("No signal received");
            }
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    private static void awaitThreads(Thread... threads) {
        awaitThreads(Arrays.asList(threads));
    }

    private static void awaitThreads(Iterable<Thread> threads) {
        for (var t : threads) {
            try {
                if (!t.join(TIMEOUT)) {
                    fail("Thread not stopping " + t);
                }
            } catch (InterruptedException ex) {
                fail(ex);
            }
        }
    }

    /**
     * Tests that get() + remove() can prevent stale value from being installed.
     * Uses junit to do basic stress.
     */
    @Test
    void testRemoveStale() throws InterruptedException {
        CountDownLatch oldInputUsed = new CountDownLatch(1);
        CountDownLatch inputUpdated = new CountDownLatch(1);
        AtomicInteger input = new AtomicInteger(0);
        ClassValue<Integer> cv = new ClassValue<>() {
            @Override
            protected Integer computeValue(Class<?> type) {
                // must get early to represent using outdated input
                int v = input.get();
                oldInputUsed.countDown();
                // ensure input is updated when we return
                await(inputUpdated);
                return v;
            }
        };
        var innocuous = Thread.startVirtualThread(() -> cv.get(int.class));
        var refreshInput = Thread.startVirtualThread(() -> {
            await(oldInputUsed);
            input.incrementAndGet();
            cv.remove(int.class); // Let's recompute with updated inputs!
            inputUpdated.countDown();
        });
        awaitThreads(innocuous, refreshInput);
        assertEquals(1, input.get(), "input not updated");
        assertEquals(1, cv.get(int.class), "CV not using up-to-date input");
    }

    /**
     * Tests that calling get() from computeValue() terminates.
     */
    @Test
    void testGetInCompute() {
        ClassValue<Object> cv = new ClassValue<>() {
            @Override
            protected Object computeValue(Class<?> type) {
                get(type);
                get(type);
                get(type);
                return Boolean.TRUE;
            }
        };
        try {
            cv.get(int.class);
        } catch (Throwable ex) {
            // swallow if any
        }
    }

    /**
     * Tests that calling remove() from computeValue() terminates.
     */
    @Test
    void testRemoveInCompute() {
        ClassValue<Boolean> cv = new ClassValue<>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                remove(type);
                remove(type);
                remove(type);
                return Boolean.TRUE;
            }
        };
        assertTrue(cv.get(int.class));
    }

    private static Class<?> createWeakClass() {
        var bytes = ClassFile.of().build(ClassDesc.of("WeakTest"), _ -> {});
        try {
            return MethodHandles.lookup().defineHiddenClass(bytes, true).lookupClass();
        } catch (IllegalAccessException ex) {
            return fail(ex);
        }
    }

    @Test
    void testWeakAgainstClass() {
        Class<?> hidden = createWeakClass();
        ClassValue<int[]> cv = new ClassValue<>() {
            @Override
            protected int[] computeValue(Class<?> type) {
                return new int[23];
            }
        };

        WeakReference<?> ref = new WeakReference<>(cv.get(hidden));
        hidden = null; // Remove reference for interpreter
        if (!ForceGC.wait(() -> ref.refersTo(null))) {
            fail("Timeout");
        }
    }

    @Test
    @Disabled // JDK-8352622
    void testWeakAgainstClassValue() {
        ClassValue<int[]> cv = new ClassValue<>() {
            @Override
            protected int[] computeValue(Class<?> type) {
                return new int[23];
            }
        };

        WeakReference<?> ref = new WeakReference<>(cv.get(int.class));
        cv = null; // Remove reference for interpreter
        if (!ForceGC.wait(() -> {
            CV1.get(int.class); // flush the weak maps
            return ref.refersTo(null);
        })) {
            fail("Timeout");
        }
    }

    @Test
    void testSingletonWinner() {
        CountDownLatch raceStart = new CountDownLatch(1);
        ClassValue<int[]> cv = new ClassValue<>() {
            @Override
            protected int[] computeValue(Class<?> type) {
                await(raceStart);
                return new int[] {ThreadLocalRandom.current().nextInt()};
            }
        };
        AtomicReference<int[]> truth = new AtomicReference<>(null);
        AtomicInteger truthSwapCount = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>(100);
        Runnable job = () -> {
            var res = cv.get(ClassValueTest.class);
            var item = truth.compareAndExchange(null, res);
            if (item != null) {
                assertSame(item, res);
            } else {
                truthSwapCount.incrementAndGet();
            }
        };
        for (int i = 0; i < 100; i++) {
            threads.add(Thread.startVirtualThread(job));
        }
        raceStart.countDown();
        awaitThreads(threads);
        assertEquals(1, truthSwapCount.get());
    }

    @Test
    void testRacyRemoveInCompute() {
        ClassValue<Object> cv = new ClassValue<>() {
            @Override
            protected Object computeValue(Class<?> type) {
                remove(type);
                try {
                    Thread.sleep(COMPUTE_TIME_MILLIS);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                remove(type);
                return Boolean.TRUE;
            }
        };

        var threads = Arrays.stream(CLASSES)
                .map(clz -> Thread.startVirtualThread(() -> cv.get(clz)))
                .toList();
        awaitThreads(threads);
    }

    @Test
    void testRecursiveInitialization() {
        record Holder() {
            static final ClassValue<Object> clv = new ClassValue<>() {
                @Override
                protected Object computeValue(Class<?> type) {
                    return new One();
                }
            };

            record One() {
                static {
                    Holder.clv.get(One.class);
                }
            }

        }

        Holder.clv.get(Holder.One.class);
    }

    private static final ScopedValue<Integer> THREAD_ID = ScopedValue.newInstance();

    @Test
    void testNoRecomputeOnUnrelatedRemoval() throws InterruptedException {
        CountDownLatch t1Started = new CountDownLatch(1);
        CountDownLatch removeTamper = new CountDownLatch(1);
        CountDownLatch t2Started = new CountDownLatch(1);
        CountDownLatch t1Returned = new CountDownLatch(1);
        AtomicInteger t1Tries = new AtomicInteger();
        ClassValue<Object> clv = new ClassValue<>() {
            @Override
            protected Object computeValue(Class<?> type) {
                int id = THREAD_ID.get();
                if (id == 1) {
                    t1Tries.incrementAndGet();
                    t1Started.countDown();
                    await(t2Started); // implies unrelated changes
                } else if (id == 2) {
                    t2Started.countDown();
                    // Don't race to install - we want to check computeValue tampers
                    await(t1Returned);
                } else if (id != -1) {
                    fail("Id: " + id);
                }
                return "";
            }
        };

        ScopedValue.where(THREAD_ID, -1).run(() -> clv.get(long.class)); // set up unrelated class
        var t1 = Thread.startVirtualThread(() ->
                ScopedValue.where(THREAD_ID, 1).run(() -> {
                    clv.get(int.class);
                    t1Returned.countDown(); // returned after x calls to computeValue
                }));
        var t2 = Thread.startVirtualThread(() ->
                ScopedValue.where(THREAD_ID, 2).run(() -> {
                    await(removeTamper);
                    clv.get(int.class); // clv version diff from that of promise
                }));
        await(t1Started);
        clv.remove(long.class);
        removeTamper.countDown(); // removed unrelated class
        awaitThreads(t1, t2);
        assertEquals(1, t1Tries.get(), "Redundant computeValue retries");
    }

    @Test
    void testNoObsoleteInstallation() throws InterruptedException {
        CountDownLatch slowComputationStart = new CountDownLatch(1);
        CountDownLatch slowComputationContinue = new CountDownLatch(1);
        ClassValue<Integer> clv = new ClassValue<>() {
            @Override
            protected Integer computeValue(Class<?> type) {
                if (!THREAD_ID.isBound()) {
                    return 5;
                }
                int threadId = THREAD_ID.get();
                if (threadId == 1) {
                    if (slowComputationContinue.getCount() == 0) {
                        return 42;
                    } else {
                        // First invocation
                        slowComputationStart.countDown();
                        await(slowComputationContinue);
                        return -1;
                    }
                } else {
                    return fail("Unknown thread " + threadId);
                }
            }
        };
        var t = Thread.startVirtualThread(() -> ScopedValue.where(THREAD_ID, 1).run(() -> {
            int v = clv.get(int.class);
            assertEquals(42, v, "recomputed value");
        }));
        await(slowComputationStart);
        assertEquals(5, clv.get(int.class), "fast computation installed value");
        clv.remove(int.class);
        slowComputationContinue.countDown();
        awaitThreads(t);
        assertEquals(42, clv.get(int.class), "slow computation reinstalled value");
    }
}
