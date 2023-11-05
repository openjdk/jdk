/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test    id=G1
 * @bug     6173675 8231209 8304074 8313081
 * @summary Basic test of ThreadMXBean.getThreadAllocatedBytes
 * @requires vm.gc.G1
 * @run main/othervm -XX:+UseG1GC ThreadAllocatedMemory
 */

/*
 * @test    id=Serial
 * @bug     6173675 8231209 8304074 8313081
 * @summary Basic test of ThreadMXBean.getThreadAllocatedBytes
 * @requires vm.gc.Serial
 * @run main/othervm -XX:+UseSerialGC ThreadAllocatedMemory
 */

import java.lang.management.*;

public class ThreadAllocatedMemory {
    private static com.sun.management.ThreadMXBean mbean =
        (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static boolean testFailed = false;
    private static volatile boolean done = false;
    private static volatile boolean done1 = false;
    private static Object obj = new Object();
    private static final int NUM_THREADS = 10;
    private static Thread[] threads = new Thread[NUM_THREADS];
    private static long[] sizes = new long[NUM_THREADS];

    public static void main(String[] argv)
        throws Exception {

        testSupportEnableDisable();

        // Test current thread two ways
        testGetCurrentThreadAllocatedBytes();
        testCurrentThreadGetThreadAllocatedBytes();

        // Test a single thread that is not this one
        testGetThreadAllocatedBytes();

        // Test many threads that are not this one
        testGetThreadsAllocatedBytes();

        // Test cumulative Java thread allocation since JVM launch
        testGetTotalThreadAllocatedBytes();

        if (testFailed) {
            throw new RuntimeException("TEST FAILED");
        }

        System.out.println("Test passed");
    }

    private static void testSupportEnableDisable() {
        if (!mbean.isThreadAllocatedMemorySupported()) {
            return;
        }

        // disable allocated memory measurement
        if (mbean.isThreadAllocatedMemoryEnabled()) {
            mbean.setThreadAllocatedMemoryEnabled(false);
        }

        if (mbean.isThreadAllocatedMemoryEnabled()) {
            throw new RuntimeException(
                "ThreadAllocatedMemory is expected to be disabled");
        }

        long s = mbean.getCurrentThreadAllocatedBytes();
        if (s != -1) {
            throw new RuntimeException(
                "Invalid ThreadAllocatedBytes returned = " +
                s + " expected = -1");
        }

        // enable allocated memory measurement
        if (!mbean.isThreadAllocatedMemoryEnabled()) {
            mbean.setThreadAllocatedMemoryEnabled(true);
        }

        if (!mbean.isThreadAllocatedMemoryEnabled()) {
            throw new RuntimeException(
                "ThreadAllocatedMemory is expected to be enabled");
        }
    }

    private static void testGetCurrentThreadAllocatedBytes() {
        Thread curThread = Thread.currentThread();

        long size = mbean.getCurrentThreadAllocatedBytes();
        ensureValidSize(curThread, size);

        // do some more allocation
        doit();

        checkResult(curThread, size,
                    mbean.getCurrentThreadAllocatedBytes());
    }

    private static void testCurrentThreadGetThreadAllocatedBytes() {
        Thread curThread = Thread.currentThread();
        long id = curThread.getId();

        long size = mbean.getThreadAllocatedBytes(id);
        ensureValidSize(curThread, size);

        // do some more allocation
        doit();

        checkResult(curThread, size, mbean.getThreadAllocatedBytes(id));
    }

    private static void testGetThreadAllocatedBytes()
        throws Exception {

        // start a thread
        done = false;
        done1 = false;
        Thread curThread = new MyThread("MyThread");
        curThread.start();
        long id = curThread.getId();

        // wait for thread to block after doing some allocation
        waitUntilThreadBlocked(curThread);

        long size = mbean.getThreadAllocatedBytes(id);
        ensureValidSize(curThread, size);

        // let thread go to do some more allocation
        synchronized (obj) {
            done = true;
            obj.notifyAll();
        }

        // wait for thread to get going again. we don't care if we
        // catch it in mid-execution or if it hasn't
        // restarted after we're done sleeping.
        goSleep(400);

        checkResult(curThread, size, mbean.getThreadAllocatedBytes(id));

        // let thread exit
        synchronized (obj) {
            done1 = true;
            obj.notifyAll();
        }

        try {
            curThread.join();
        } catch (InterruptedException e) {
            reportUnexpected(e, "during join");
        }
    }

    private static void testGetThreadsAllocatedBytes()
        throws Exception {

        // start threads
        done = false;
        done1 = false;
        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new MyThread("MyThread-" + i);
            threads[i].start();
        }

        // wait for threads to block after doing some allocation
        waitUntilThreadsBlocked();

        for (int i = 0; i < NUM_THREADS; i++) {
            sizes[i] = mbean.getThreadAllocatedBytes(threads[i].getId());
            ensureValidSize(threads[i], sizes[i]);
        }

        // let threads go to do some more allocation
        synchronized (obj) {
            done = true;
            obj.notifyAll();
        }

        // wait for threads to get going again. we don't care if we
        // catch them in mid-execution or if some of them haven't
        // restarted after we're done sleeping.
        goSleep(400);

        for (int i = 0; i < NUM_THREADS; i++) {
            checkResult(threads[i], sizes[i],
                        mbean.getThreadAllocatedBytes(threads[i].getId()));
        }

        // let threads exit
        synchronized (obj) {
            done1 = true;
            obj.notifyAll();
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                reportUnexpected(e, "during join");
                break;
            }
        }
    }

    private static void testGetTotalThreadAllocatedBytes()
        throws Exception {

        // baseline should be positive
        Thread curThread = Thread.currentThread();
        long cumulativeSize = mbean.getTotalThreadAllocatedBytes();
        if (cumulativeSize <= 0) {
            throw new RuntimeException(
                "Invalid allocated bytes returned for " + curThread.getName() + " = " + cumulativeSize);
        }

        // start threads
        done = false;
        done1 = false;
        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new MyThread("MyThread-" + i);
            threads[i].start();
        }

        // wait for threads to block after doing some allocation
        waitUntilThreadsBlocked();

        // check after threads are blocked
        cumulativeSize = checkResult(curThread, cumulativeSize, mbean.getTotalThreadAllocatedBytes());

        // let threads go to do some more allocation
        synchronized (obj) {
            done = true;
            obj.notifyAll();
        }

        // wait for threads to get going again. we don't care if we
        // catch them in mid-execution or if some of them haven't
        // restarted after we're done sleeping.
        goSleep(400);

        System.out.println("Done sleeping");

        // check while threads are running
        cumulativeSize = checkResult(curThread, cumulativeSize, mbean.getTotalThreadAllocatedBytes());

        // let threads exit
        synchronized (obj) {
            done1 = true;
            obj.notifyAll();
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                reportUnexpected(e, "during join");
                break;
            }
        }

        // check after threads exit
        checkResult(curThread, cumulativeSize, mbean.getTotalThreadAllocatedBytes());
    }

    private static void ensureValidSize(Thread curThread, long size) {
        // implementation could have started measurement when
        // measurement was enabled, in which case size can be 0
        if (size < 0) {
            throw new RuntimeException(
                "Invalid allocated bytes returned for thread " +
                curThread.getName() + " = " + size);
        }
    }

    private static long checkResult(Thread curThread,
                                    long prevSize, long currSize) {
        System.out.println(curThread.getName() +
                           " Previous allocated bytes = " + prevSize +
                           " Current allocated bytes = " + currSize);
        if (currSize < prevSize) {
            throw new RuntimeException("TEST FAILED: " +
                                       curThread.getName() +
                                       " previous allocated bytes = " + prevSize +
                                       " > current allocated bytes = " + currSize);
        }
        return currSize;
    }

    private static void reportUnexpected(Exception e, String when) {
        System.out.println("Unexpected exception thrown " + when + ".");
        e.printStackTrace(System.out);
        testFailed = true;
    }

    private static void goSleep(long ms) throws Exception {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw e;
        }
    }

    private static void waitUntilThreadBlocked(Thread thread)
        throws Exception {
        while (true) {
            goSleep(100);
            ThreadInfo info = mbean.getThreadInfo(thread.getId());
            if (info.getThreadState() == Thread.State.WAITING) {
                break;
            }
        }
    }

    private static void waitUntilThreadsBlocked()
        throws Exception {
        int count = 0;
        while (count != NUM_THREADS) {
            goSleep(100);
            count = 0;
            for (int i = 0; i < NUM_THREADS; i++) {
                ThreadInfo info = mbean.getThreadInfo(threads[i].getId());
                if (info.getThreadState() == Thread.State.WAITING) {
                    count++;
                }
            }
        }
    }

    public static void doit() {
        String tmp = "";
        long hashCode = 0;
        for (int counter = 0; counter < 1000; counter++) {
            tmp += counter;
            hashCode = tmp.hashCode();
        }
        System.out.println(Thread.currentThread().getName() +
                           " hashcode: " + hashCode);
    }

    static class MyThread extends Thread {
        public MyThread(String name) {
            super(name);
        }

        public void run() {
            ThreadAllocatedMemory.doit();

            synchronized (obj) {
                while (!done) {
                    try {
                        obj.wait();
                    } catch (InterruptedException e) {
                        reportUnexpected(e, "while !done");
                        break;
                    }
                }
            }

            long prevSize = mbean.getThreadAllocatedBytes(getId());
            ThreadAllocatedMemory.doit();
            long currSize = mbean.getThreadAllocatedBytes(getId());
            checkResult(this, prevSize, currSize);

            synchronized (obj) {
                while (!done1) {
                    try {
                        obj.wait();
                    } catch (InterruptedException e) {
                        reportUnexpected(e, "while !done1");
                        break;
                    }
                }
            }
        }
    }
}
