/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test ForkJoinPool.shutdownNow with threads blocked in invokeXXX and Future.get
 * @run junit AsyncShutdownNow
 */

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import static java.lang.Thread.State.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class AsyncShutdownNow {

    // long running interruptible task
    private static final Callable<Void> SLEEP_FOR_A_DAY = () -> {
        Thread.sleep(Duration.ofDays(1));
        return null;
    };

    static Stream<ForkJoinPool> pools() {
        return Stream.of(
                new ForkJoinPool(),
                new ForkJoinPool(1)
        );
    }

    /**
     * Test shutdownNow with a running task and main thread blocked in Future::get.
     */
    @ParameterizedTest
    @MethodSource("pools")
    void testFutureGet(ForkJoinPool pool) throws Exception {
        try (pool) {
            Future<?> future = pool.submit(SLEEP_FOR_A_DAY);

            // shutdownNow when main thread waits in ForkJoinTask.awaitDone
            onWait("java.util.concurrent.ForkJoinTask.awaitDone", pool::shutdownNow);
            try {
                future.get();
                fail();
            } catch (ExecutionException | CancellationException e) {
                // expected
            }
        }
    }

    /**
     * Test shutdownNow with a running task and main thread blocked in a timed Future::get.
     */
    @ParameterizedTest
    @MethodSource("pools")
    void testTimedFutureGet(ForkJoinPool pool) throws Exception {
        try (pool) {
            Future<?> future = pool.submit(SLEEP_FOR_A_DAY);

            // shutdownNow when main thread waits in ForkJoinTask.awaitDone
            onWait("java.util.concurrent.ForkJoinTask.awaitDone", pool::shutdownNow);
            try {
                future.get(1, TimeUnit.HOURS);
                fail();
            } catch (ExecutionException | CancellationException e) {
                // expected
            }
        }
    }

    /**
     * Test shutdownNow with running tasks and main thread blocked in invokeAll.
     */
    @ParameterizedTest
    @MethodSource("pools")
    void testInvokeAll(ForkJoinPool pool) throws Exception {
        try (pool) {
            // shutdownNow when main thread waits in ForkJoinTask.awaitDone
            onWait("java.util.concurrent.ForkJoinTask.awaitDone", pool::shutdownNow);
            List<Future<Void>> futures = pool.invokeAll(List.of(SLEEP_FOR_A_DAY, SLEEP_FOR_A_DAY));
            for (Future<Void> f : futures) {
                assertTrue(f.isDone());
                try {
                    Object result = f.get();
                    fail();
                } catch (ExecutionException | CancellationException e) {
                    // expected
                }
            }
        }
    }

    /**
     * Test shutdownNow with running tasks and main thread blocked in invokeAny.
     */
    @ParameterizedTest
    @MethodSource("pools")
    void testInvokeAny(ForkJoinPool pool) throws Exception {
        try (pool) {
            // shutdownNow when main thread waits in ForkJoinTask.get
            onWait("java.util.concurrent.ForkJoinTask.get", pool::shutdownNow);
            try {
                pool.invokeAny(List.of(SLEEP_FOR_A_DAY, SLEEP_FOR_A_DAY));
                fail();
            } catch (ExecutionException e) {
                // expected
            }
        }
    }

    /**
     * Runs the given action when the current thread is sampled as waiting (timed or
     * untimed) at the given location. The location takes the form "{@code c.m}" where
     * {@code c} is the fully qualified class name and {@code m} is the method name.
     */
    private void onWait(String location, Runnable action) {
        int index = location.lastIndexOf('.');
        String className = location.substring(0, index);
        String methodName = location.substring(index + 1);
        Thread target = Thread.currentThread();
        var thread = new Thread(() -> {
            try {
                boolean found = false;
                while (!found) {
                    Thread.State state = target.getState();
                    assertTrue(state != TERMINATED);
                    if ((state == WAITING || state == TIMED_WAITING)
                            && contains(target.getStackTrace(), className, methodName)) {
                        found = true;
                    } else {
                        Thread.sleep(20);
                    }
                }
                action.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Returns true if the given stack trace contains an element for the given class
     * and method name.
     */
    private boolean contains(StackTraceElement[] stack, String className, String methodName) {
        return Arrays.stream(stack)
                .anyMatch(e -> className.equals(e.getClassName())
                        && methodName.equals(e.getMethodName()));
    }
}
