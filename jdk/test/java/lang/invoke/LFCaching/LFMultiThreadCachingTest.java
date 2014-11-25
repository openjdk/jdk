/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test LFMultiThreadCachingTest
 * @bug 8046703
 * @summary Test verifies that lambda forms are cached when run with multiple threads
 * @author kshefov
 * @library /lib/testlibrary/jsr292 /lib/testlibrary
 * @build TestMethods
 * @build LambdaFormTestCase
 * @build LFCachingTestCase
 * @build LFMultiThreadCachingTest
 * @run main/othervm LFMultiThreadCachingTest
 */

import java.lang.invoke.MethodHandle;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * Multiple threaded lambda forms caching test class.
 */
public final class LFMultiThreadCachingTest extends LFCachingTestCase {
    private static final TestMethods.Kind[] KINDS;
    static {
        EnumSet<TestMethods.Kind> set = EnumSet.complementOf(EnumSet.of(TestMethods.Kind.EXCEPT));
        KINDS = set.toArray(new TestMethods.Kind[set.size()]);
        if (KINDS.length < 2) {
            throw new Error("TESTBUG: KINDS.length[" + KINDS.length + "] should be at least 2");
        }
    }
    private static final int CORES = Math.max(KINDS.length, Runtime.getRuntime().availableProcessors());

    /**
     * Constructor a for multiple threaded lambda forms caching test case.
     *
     * @param testMethod A method from {@code j.l.i.MethodHandles} class that
     * returns a {@code j.l.i.MethodHandle} instance.
     */
    public LFMultiThreadCachingTest(TestMethods testMethod) {
        super(testMethod);
    }

    @Override
    public void doTest() {
        Map<String, Object> data = getTestMethod().getTestCaseData();
        ConcurrentLinkedQueue<MethodHandle> adapters = new ConcurrentLinkedQueue<>();
        CyclicBarrier begin = new CyclicBarrier(CORES);
        CountDownLatch end = new CountDownLatch(CORES);
        for (int i = 0; i < CORES; ++i) {
            TestMethods.Kind kind = KINDS[i % KINDS.length];
            new Thread(() -> {
                try {
                    begin.await();
                    adapters.add(getTestMethod().getTestCaseMH(data, kind));
                } catch (InterruptedException | BrokenBarrierException | IllegalAccessException | NoSuchMethodException ex) {
                    throw new Error("Unexpected exception: ", ex);
                } finally {
                    end.countDown();
                }
            }).start();
        }
        try {
            end.await();
        } catch (InterruptedException ex) {
            throw new Error("Unexpected exception: ", ex);
        }
        if (adapters.size() < CORES) {
            throw new Error("adapters size[" + adapters.size() + "] is less than " + CORES);
        }
        MethodHandle prev = adapters.poll();
        for (MethodHandle current : adapters) {
            checkLFCaching(prev, current);
            prev = current;
        }
    }

    /**
     * Main routine for multiple threaded lambda forms caching test.
     *
     * @param args Accepts no arguments.
     */
    public static void main(String[] args) {
        LambdaFormTestCase.runTests(LFMultiThreadCachingTest::new, EnumSet.allOf(TestMethods.class));
    }
}
