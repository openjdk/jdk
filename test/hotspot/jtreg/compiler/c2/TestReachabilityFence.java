/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2;

import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import jdk.internal.misc.Unsafe;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8290892
 * @summary Tests to ensure that reachabilityFence() correctly keeps objects from being collected prematurely.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main/othervm -Xbatch compiler.c2.TestReachabilityFence
 */
public class TestReachabilityFence {
    private static final int SIZE = 100;

    static final boolean[] STATUS = new boolean[2];

    interface MyBuffer {
        byte get(int offset);
    }
    static class MyBufferOnHeap implements MyBuffer {

        private static int current = 0;
        private final static byte[][] payload = new byte[10][];

        private final int id;

        public MyBufferOnHeap() {
            // Get a unique id, allocate memory, and save the address in the payload array.
            id = current++;
            payload[id] = new byte[SIZE];

            // Initialize buffer
            for (int i = 0; i < SIZE; ++i) {
                put(i, (byte) 42);
            }

            // Register a cleaner to free the memory when the buffer is garbage collected.
            int lid = id; // Capture current value
            Cleaner.create().register(this, () -> { free(lid); });

            System.out.println("Created new buffer of size = " + SIZE + " with id = " + id);
        }

        private static void free(int id) {
            System.out.println("Freeing buffer with id = " + id);
            for (int i = 0; i < SIZE; ++i) {
                payload[id][i] = (byte)0;
            }
            payload[id] = null;

            synchronized (STATUS) {
                STATUS[0] = true;
                STATUS.notifyAll();
            }
        }

        public void put(int offset, byte b) {
            payload[id][offset] = b;
        }

        public byte get(int offset) {
            try {
                return payload[id][offset];
            } finally {
                Reference.reachabilityFence(this);
            }
        }
    }

    static class MyBufferOffHeap implements MyBuffer {
        private static Unsafe UNSAFE = Unsafe.getUnsafe();

        private static int current = 0;
        private static long payload[] = new long[10];

        private final int id;

        public MyBufferOffHeap() {
            // Get a unique id, allocate memory, and save the address in the payload array.
            id = current++;
            payload[id] = UNSAFE.allocateMemory(SIZE);

            // Initialize buffer
            for (int i = 0; i < SIZE; ++i) {
                put(i, (byte) 42);
            }

            // Register a cleaner to free the memory when the buffer is garbage collected
            int lid = id; // Capture current value
            Cleaner.create().register(this, () -> { free(lid); });

            System.out.println("Created new buffer of size = " + SIZE + " with id = " + id);
        }

        private static void free(int id) {
            System.out.println("Freeing buffer with id = " + id);
            for (int i = 0; i < SIZE; ++i) {
                UNSAFE.putByte(payload[id] + i, (byte)0);
            }
            // UNSAFE.freeMemory(payload[id]); // don't deallocate backing memory to avoid crashes
            payload[id] = 0;

            synchronized (STATUS) {
                STATUS[1] = true;
                STATUS.notifyAll();
            }
        }

        public void put(int offset, byte b) {
            UNSAFE.putByte(payload[id] + offset, b);
        }

        public byte get(int offset) {
            try {
                return UNSAFE.getByte(payload[id] + offset);
            } finally {
                Reference.reachabilityFence(this);
            }
        }
    }

    static MyBufferOffHeap bufferOff = new MyBufferOffHeap();
    static MyBufferOnHeap bufferOn = new MyBufferOnHeap();

    static long[] counters = new long[4];

    static boolean test(MyBuffer buf) {
        if (buf == null) {
            return false;
        }
        for (int i = 0; i < SIZE; i++) {
            // The access is split into base address load (payload[id]), offset computation, and data load.
            // While offset is loop-variant, payload[id] is not and can be hoisted.
            // If bufferOff and payload[id] loads are hoisted outside outermost loop, it eliminates all usages of
            // myBuffer oop inside the loop and bufferOff can be GCed at the safepoint on outermost loop back branch.
            byte b = buf.get(i); // inlined
            if (b != 42) {
                String msg = "Unexpected value = " + b + ". Buffer was garbage collected before reachabilityFence was reached!";
                throw new AssertionError(msg);
            }
        }
        return true;
    }

    /* ===================================== Off-heap versions ===================================== */

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.REACHABILITY_FENCE, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "1"}, phase = CompilePhase.AFTER_LOOP_OPTS)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "0"}, phase = CompilePhase.EXPAND_REACHABILITY_FENCES)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testOffHeap1(int limit) {
        for (long j = 0; j < limit; j++) {
            MyBufferOffHeap myBuffer = bufferOff; // local
            if (!test(myBuffer)) {
                return j;
            }
            counters[0] = j;
        } // safepoint on loop backedge does NOT contain myBuffer local as part of its JVM state
        return limit;
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.REACHABILITY_FENCE, "2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "2"}, phase = CompilePhase.AFTER_LOOP_OPTS)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "0"}, phase = CompilePhase.EXPAND_REACHABILITY_FENCES)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testOffHeap2(int limit) {
        for (long j = 0; j < limit; j++) {
            MyBufferOffHeap myBuffer = bufferOff; // local
            try {
                if (!test(myBuffer)) {
                    return j;
                }
                counters[1] = j;
            } finally {
                Reference.reachabilityFence(myBuffer);
            }
        } // safepoint on loop backedge does NOT contain myBuffer local as part of its JVM state
        return limit;
    }

    /* ===================================== On-heap versions ===================================== */

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.REACHABILITY_FENCE, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "1"}, phase = CompilePhase.AFTER_LOOP_OPTS)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "0"}, phase = CompilePhase.EXPAND_REACHABILITY_FENCES)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testOnHeap1(int limit) {
        for (long j = 0; j < limit; j++) {
            MyBufferOnHeap myBuffer = bufferOn; // local
            if (!test(myBuffer)) {
                return j;
            }
            counters[2] = j;
        } // safepoint on loop backedge does NOT contain myBuffer local as part of its JVM state
        return limit;
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(counts = {IRNode.REACHABILITY_FENCE, "2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "2"}, phase = CompilePhase.AFTER_LOOP_OPTS)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "0"}, phase = CompilePhase.EXPAND_REACHABILITY_FENCES)
    @IR(counts = {IRNode.REACHABILITY_FENCE, "1"}, phase = CompilePhase.FINAL_CODE)
    static long testOnHeap2(int limit) {
        for (long j = 0; j < limit; j++) {
            MyBufferOnHeap myBuffer = bufferOn; // local
            try {
                if (!test(myBuffer)) {
                    return j;
                }
                counters[3] = j;
            } finally {
                Reference.reachabilityFence(myBuffer);
            }
        } // safepoint on loop backedge does NOT contain myBuffer local as part of its JVM state
        return limit;
    }

    static void runJavaTestCases() throws Throwable {
        // Warmup to trigger compilations.
        for (int i = 0; i < 10_000; i++) {
            testOffHeap1(10);
            testOffHeap2(10);
            testOnHeap1(10);
            testOnHeap2(10);
        }

        @SuppressWarnings("unchecked")
        Callable<Long>[] tasks = new Callable[] {
                () -> testOffHeap1(100_000_000),
                () -> testOffHeap2(100_000_000),
                () -> testOnHeap1(100_000_000),
                () -> testOnHeap2(100_000_000),
        };
        int taskCount = tasks.length;
        CountDownLatch latch = new CountDownLatch(taskCount + 1);
        final Thread[] workers = new Thread[taskCount];
        final Throwable[] result = new Throwable[taskCount];

        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            workers[id] = new Thread(() -> {
                latch.countDown(); // synchronize with main thread
                try {
                    System.out.printf("Computation thread #%d has started\n", id);
                    long cnt = tasks[id].call();
                    System.out.printf("#%d Finished after %d iterations\n", id, cnt);
                } catch (Throwable e) {
                    System.out.printf("#%d Finished with an exception %s\n", id, e);
                    result[id] = e;
                }
            });
        }

        for (Thread worker : workers) {
            worker.start();
        }

        latch.countDown(); // synchronize with worker threads

        Thread.sleep(100); // let workers proceed

        // Clear references to buffers and make sure it's garbage collected.
        System.out.printf("Buffers set to null. Waiting for garbage collection. (counters = %s)\n", Arrays.toString(counters));
        bufferOn = null;
        bufferOff = null;

        System.gc();

        synchronized (STATUS) {
            do {
                if (STATUS[0] && STATUS[1]) {
                    break;
                } else {
                    System.out.printf("Waiting for cleanup... (counters = %s)\n", Arrays.toString(counters));
                    System.gc();
                    STATUS.wait(100);
                }
            } while (true);
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.printf("Results: %s\n", Arrays.deepToString(result));

        for (Throwable e : result) {
            if (e != null) {
                throw e;
            }
        }
    }

    static void runIRTestCases() {
        TestFramework framework = new TestFramework();
        framework.addFlags("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        framework.start();
    }

    public static void main(String[] args) throws Throwable {
        try {
            runIRTestCases();
            runJavaTestCases();
            System.out.println("TEST PASSED");
        } catch (Throwable e) {
            System.out.println("TEST FAILED");
            throw e;
        }
    }
}