/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6399443 8302899 8362123
 * @summary Test that Executors.newSingleThreadExecutor wraps an ExecutorService that
 *    automatically shuts down and terminates when the wrapper is GC'ed
 * @library /test/lib/
 * @modules java.base/java.util.concurrent:+open
 * @run junit AutoShutdown
 */

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.IntStream;

import jdk.test.lib.Utils;
import jdk.test.lib.util.ForceGC;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import static org.junit.jupiter.api.Assertions.*;

class AutoShutdown {

    private static Stream<Supplier<ExecutorService>> executors() {
        return Stream.of(
            () -> Executors.newSingleThreadExecutor(),
            () -> Executors.newSingleThreadExecutor(Executors.defaultThreadFactory())
        );
    }

    private static Stream<Arguments> executorAndQueuedTaskCounts() {
        int[] queuedTaskCounts = { 0, 1, 2 };
        return executors().flatMap(s -> IntStream.of(queuedTaskCounts)
                .mapToObj(i -> Arguments.of(s, i)));
    }

    private static Stream<Arguments> shutdownMethods() {
        return Stream.<Consumer<ExecutorService>>of(
                e -> e.shutdown(),
                e -> e.shutdownNow()
            ).map(Arguments::of);
    }

    /**
     * SingleThreadExecutor with no worker threads.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testNoWorker(Supplier<ExecutorService> supplier) throws Exception {
        ExecutorService executor = supplier.get();
        ExecutorService delegate = getDelegate(executor);
        executor = null;
        gcAndAwaitTermination(delegate);
    }

    /**
     * SingleThreadExecutor with an idle worker thread.
     */
    @ParameterizedTest
    @MethodSource("executors")
    void testIdleWorker(Supplier<ExecutorService> supplier) throws Exception {
        ExecutorService executor = supplier.get();
        // submit a task to get a worker to start
        executor.submit(() -> null).get();
        ExecutorService delegate = getDelegate(executor);
        executor = null;
        gcAndAwaitTermination(delegate);
    }

    /**
     * SingleThreadExecutor with an active worker and queued tasks.
     */
    @ParameterizedTest
    @MethodSource("executorAndQueuedTaskCounts")
    void testActiveWorker(Supplier<ExecutorService> supplier,int queuedTaskCount) throws Exception {
        ExecutorService executor = supplier.get();
        // the worker will execute one task, the other tasks will be queued
        int ntasks = 1 + queuedTaskCount;
        AtomicInteger completedTaskCount = new AtomicInteger();
        for (int i = 0; i < ntasks; i++) {
            executor.submit(() -> {
                Thread.sleep(Duration.ofMillis(500));
                completedTaskCount.incrementAndGet();
                return null;
            });
        }
        ExecutorService delegate = getDelegate(executor);
        executor = null;
        gcAndAwaitTermination(delegate);
        assertEquals(ntasks, completedTaskCount.get());
    }

    @ParameterizedTest
    @MethodSource("shutdownMethods")
    void testShutdownUnlinksCleaner(Consumer<ExecutorService> shutdown) throws Exception {
        ClassLoader classLoader =
            Utils.getTestClassPathURLClassLoader(ClassLoader.getPlatformClassLoader());

        ReferenceQueue<?> queue = new ReferenceQueue<>();
        Reference<?> reference = new PhantomReference(classLoader, queue);
        try {
            Class<?> isolatedClass = classLoader.loadClass("AutoShutdown$IsolatedClass");
            assertSame(isolatedClass.getClassLoader(), classLoader);
            isolatedClass.getDeclaredMethod("shutdown", Consumer.class).invoke(null, shutdown);

            isolatedClass = null;
            classLoader = null;

            assertTrue(ForceGC.wait(() -> queue.poll() != null));
        } finally {
            Reference.reachabilityFence(reference);
        }
    }

    /**
     * Returns the delegate for the given ExecutorService. The given ExecutorService
     * must be a Executors$DelegatedExecutorService.
     */
    private ExecutorService getDelegate(ExecutorService executor) throws Exception {
        Field eField = Class.forName("java.util.concurrent.Executors$DelegatedExecutorService")
                .getDeclaredField("e");
        eField.setAccessible(true);
        return (ExecutorService) eField.get(executor);
    }

    /**
     * Invokes System.gc and waits for the given ExecutorService to terminate.
     */
    private void gcAndAwaitTermination(ExecutorService executor) throws Exception {
        System.err.println(executor);
        boolean terminated = false;
        while (!terminated) {
            System.gc();
            terminated = executor.awaitTermination(100, TimeUnit.MILLISECONDS);
        }
    }

    public static class IsolatedClass {

        private static final ExecutorService executor =
            Executors.newSingleThreadExecutor(new IsolatedThreadFactory());

        public static void shutdown(Consumer<ExecutorService> shutdown) {
            shutdown.accept(executor);
        }
    }

    public static class IsolatedThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r);
        }
    }
}
