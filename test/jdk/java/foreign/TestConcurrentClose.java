/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.compiler2.enabled
 * @modules java.base/jdk.internal.vm.annotation java.base/jdk.internal.misc
 * @key randomness
 * @library /test/lib
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run testng/othervm
 *   -Xbootclasspath/a:.
 *   -XX:+UnlockDiagnosticVMOptions
 *   -XX:+WhiteBoxAPI
 *   -XX:CompileCommand=dontinline,TestConcurrentClose$SegmentAccessor::doAccess
 *   -Xbatch
 *   TestConcurrentClose
 */

import jdk.test.whitebox.WhiteBox;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestConcurrentClose {
    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final Method DO_ACCESS_METHOD;
    static final int C2_COMPILED_LEVEL = 4;

    static {
        try {
            DO_ACCESS_METHOD = SegmentAccessor.class.getDeclaredMethod("doAccess");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final int ITERATIONS = 5;
    static final int SEGMENT_SIZE = 10_000;
    static final int MAX_EXECUTOR_WAIT_SECONDS = 60;
    static final int NUM_ACCESSORS = 50;

    static final AtomicLong start = new AtomicLong();
    static final AtomicBoolean started = new AtomicBoolean();

    @Test
    public void testHandshake() throws InterruptedException {
        for (int it = 0; it < ITERATIONS; it++) {
            System.out.println("ITERATION " + it + " - starting");
            ExecutorService accessExecutor = Executors.newCachedThreadPool();
            start.set(System.currentTimeMillis());
            started.set(false);
            CountDownLatch startClosureLatch = new CountDownLatch(1);

            for (int i = 0; i < NUM_ACCESSORS ; i++) {
                Arena arena = Arena.ofShared();
                MemorySegment segment = arena.allocate(SEGMENT_SIZE, 1);
                accessExecutor.execute(new SegmentAccessor(i, segment));
                accessExecutor.execute(new Closer(i, startClosureLatch, arena));
            }

            awaitCompilation();

            long closeDelay = System.currentTimeMillis() - start.get();
            System.out.println("Starting closers after delay of " + closeDelay + " millis");
            startClosureLatch.countDown();
            accessExecutor.shutdown();
            assertTrue(accessExecutor.awaitTermination(MAX_EXECUTOR_WAIT_SECONDS, TimeUnit.SECONDS));
            long finishDelay = System.currentTimeMillis() - start.get();
            System.out.println("ITERATION " + it + " - finished, after " + finishDelay + "milis");
        }
    }

    static class SegmentAccessor implements Runnable {
        final MemorySegment segment;
        final int id;
        boolean hasFailed = false;

        SegmentAccessor(int id, MemorySegment segment) {
            this.id = id;
            this.segment = segment;
        }

        @Override
        public final void run() {
            start("Accessor #" + id);
            while (segment.scope().isAlive()) {
                try {
                    doAccess();
                } catch (IllegalStateException ex) {
                    // scope was closed, loop should exit
                    assertFalse(hasFailed);
                    hasFailed = true;
                }
            }
            long delay = System.currentTimeMillis() - start.get();
            System.out.println("Accessor #" + id + " terminated - elapsed (ms): " + delay);
        }

        // keep this out of line, so it has a chance to be fully C2 compiled
        private int doAccess() {
            int sum = 0;
            for (int i = 0; i < segment.byteSize(); i++) {
                sum += segment.get(JAVA_BYTE, i);
            }
            return sum;
        }
    }

    static class Closer implements Runnable {
        final int id;
        final Arena arena;
        final CountDownLatch startLatch;

        Closer(int id, CountDownLatch startLatch, Arena arena) {
            this.id = id;
            this.arena = arena;
            this.startLatch = startLatch;
        }

        @Override
        public void run() {
            start("Closer #" + id);
            try {
                // try to close all at the same time, to simulate concurrent
                // closures of unrelated arenas
                startLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpected interruption", e);
            }
            arena.close(); // This should NOT throw
            long delay = System.currentTimeMillis() - start.get();
            System.out.println("Closer #" + id + "terminated - elapsed (ms): " + delay);
        }
    }

    static void start(String name) {
        if (started.compareAndSet(false, true)) {
            long delay = System.currentTimeMillis() - start.get();
            System.out.println("Started first thread: " + name + " ; elapsed (ms): " + delay);
        }
    }

    private static void awaitCompilation() throws InterruptedException {
        while (WB.getMethodCompilationLevel(DO_ACCESS_METHOD, false) != C2_COMPILED_LEVEL) {
            Thread.sleep(1000);
        }
    }
}
