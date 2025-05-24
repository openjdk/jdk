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
import java.util.concurrent.CountDownLatch;

import jdk.internal.misc.Unsafe;


/*
 * @test
 * @bug 8290892
 * @summary Tests to ensure that reachabilityFence() correctly keeps objects from being collected prematurely.
 * @modules java.base/jdk.internal.misc
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
            // Get a unique id, allocate memory and save the address in the payload array
            id = current++;
            payload[id] = new byte[SIZE];

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
            // Get a unique id, allocate memory and save the address in the payload array
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
            //UNSAFE.freeMemory(payload[id]);
            for (int i = 0; i < SIZE; ++i) {
                UNSAFE.putByte(payload[id] + i, (byte)0);
            }
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

    static MyBuffer bufferOff = new MyBufferOffHeap();
    static MyBuffer bufferOn = new MyBufferOnHeap();

    static long counter1 = 0;
    static long counter2 = 0;

    static long test1(int limit) {
        for (long j = 0; j < limit; j++) {
            MyBuffer myBuffer = bufferOff;
            if (myBuffer == null) {
                return j;
            }
            for (int i = 0; i < 100; i++) {
                byte b = myBuffer.get(i);
                if (b != 42) {
                    String msg = "Unexpected value = " + b + ". Buffer was garbage collected before reachabilityFence was reached!";
                    throw new AssertionError(msg);
                }
            }
            counter1 = j;
            // Keep the buffer live while we read from it
            Reference.reachabilityFence(myBuffer);
        }
        return limit;
    }

    static long test2(int limit) {
        for (long j = 0; j < limit; j++) {
            MyBuffer myBuffer = bufferOn;
            if (myBuffer == null) {
                return j;
            }
            for (int i = 0; i < 100; i++) {
                byte b = myBuffer.get(i);
                if (b != 42) {
                    String msg = "Unexpected value = " + b + ". Buffer was garbage collected before reachabilityFence was reached!";
                    throw new AssertionError(msg);
                }
            }
            counter2 = j;
            // Keep the buffer live while we read from it
            Reference.reachabilityFence(myBuffer);
        }
        return limit;
    }

    public static void main(String[] args) throws Throwable {
        // Warmup to trigger compilation
        for (int i = 0; i < 10_000; i++) {
            test1(10);
            test2(10);
        }

        CountDownLatch latch = new CountDownLatch(3);
        final Throwable[] result = new Throwable[2];

        Thread compThread1 = new Thread(() -> {
            latch.countDown(); // synchronize with main thread
            try {
                System.out.printf("Computation thread #1 has started\n");
                long cnt = test1(100_000_000);
                System.out.printf("#1 Finished after %d iterations\n", cnt);
            } catch (Throwable e) {
                System.out.printf("#1 Finished with an exception %s\n", e);
                result[0] = e;
            }
        });

        Thread compThread2 = new Thread(() -> {
            latch.countDown(); // synchronize with main thread
            try {
                System.out.printf("Computation thread #2 has started\n");
                long cnt = test2(100_000_000);
                System.out.printf("#2 Finished after %d iterations\n", cnt);
            } catch (Throwable e) {
                System.out.printf("#2 Finished with an exception %s\n", e);
                result[1] = e;
            }
        });

        compThread1.start();
        compThread2.start();

        latch.countDown(); // synchronize with comp thread

        Thread.sleep(100); // let compThread proceed

        // Clear reference to 'buffer' and make sure it's garbage collected
        System.out.printf("Buffer set to null. Waiting for garbage collection. (counter1 = %d; counter2 = %d)\n",
                          counter1, counter2);
        bufferOn = null;
        bufferOff = null;

        System.gc();

        synchronized (STATUS) {
            do {
                if (STATUS[0] && STATUS[1] ) {
                    break;
                } else {
                    System.out.printf("Waiting for cleanup... (counter1 = %d; counter2 = %d)\n", counter1, counter2);
                    System.gc();
                    STATUS.wait(100);
                }
            } while (true);
        }

        compThread1.join();
        compThread2.join();
        if (result[0] != null) {
            System.out.println("TEST FAILED");
            throw result[0];
        }
        if (result[1] != null) {
            System.out.println("TEST FAILED");
            throw result[0];
        }

        System.out.println("TEST PASSED");
    }
}
