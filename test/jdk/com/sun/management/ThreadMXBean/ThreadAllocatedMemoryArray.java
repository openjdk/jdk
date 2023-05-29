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
 * @test
 * @bug     6173675
 * @summary Basic test of ThreadMXBean.getThreadAllocatedBytes(long[])
 * @author  Paul Hohensee
 */

import java.lang.management.*;

public class ThreadAllocatedMemoryArray {
    private static com.sun.management.ThreadMXBean mbean =
        (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static boolean testFailed = false;
    private static boolean done = false;
    private static boolean done1 = false;
    private static Object obj = new Object();
    private static final int NUM_THREADS = 10;
    private static Thread[] threads = new Thread[NUM_THREADS];

    public static void main(String[] argv)
        throws Exception {

        if (!mbean.isThreadAllocatedMemorySupported()) {
            return;
        }

        // start threads, wait for them to block
        long[] ids = new long[NUM_THREADS];

        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new MyThread("MyThread-" + i);
            threads[i].start();
            ids[i] = threads[i].getId();
        }

        waitUntilThreadBlocked();

        // disable allocated memory measurement
        if (mbean.isThreadAllocatedMemoryEnabled()) {
            mbean.setThreadAllocatedMemoryEnabled(false);
        }

        if (mbean.isThreadAllocatedMemoryEnabled()) {
            throw new RuntimeException(
                "ThreadAllocatedMemory is expected to be disabled");
        }

        long sizes[] = mbean.getThreadAllocatedBytes(ids);

        if (sizes == null) {
          throw new RuntimeException("Null ThreadAllocatedBytes array returned");
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            long s = sizes[i];
            if (s != -1) {
                throw new RuntimeException(
                    "Invalid ThreadAllocatedBytes returned for thread " +
                    threads[i].getName() + " = " +  s + " expected = -1");
            }
        }

        // Enable allocated memory measurement
        if (!mbean.isThreadAllocatedMemoryEnabled()) {
            mbean.setThreadAllocatedMemoryEnabled(true);
        }

        if (!mbean.isThreadAllocatedMemoryEnabled()) {
            throw new RuntimeException(
                "ThreadAllocatedMemory is expected to be enabled");
        }

        sizes = mbean.getThreadAllocatedBytes(ids);

        for (int i = 0; i < NUM_THREADS; i++) {
            long s = sizes[i];
            if (s < 0) {
                throw new RuntimeException(
                    "Invalid allocated bytes returned for thread " +
                    threads[i].getName() + " = " + s);
            }
        }

        // let threads go and do some more allocation
        synchronized (obj) {
            done = true;
            obj.notifyAll();
        }

        // wait for threads to get going again.  we don't care if we
        // catch them in mid-execution or if some of them haven't
        // restarted after we're done sleeping.
        goSleep(400);

        long[] afterSizes = mbean.getThreadAllocatedBytes(ids);
        for (int i = 0; i < NUM_THREADS; i++) {
            checkResult(threads[i], sizes[i], afterSizes[i]);
        }

        try {
            sizes = mbean.getThreadAllocatedBytes(null);
        } catch (NullPointerException e) {
            System.out.println(
                "Caught expected NullPointerException: " + e.getMessage());
        }

        try {
            ids[0] = 0;
            sizes = mbean.getThreadAllocatedBytes(ids);
        } catch (IllegalArgumentException e) {
            System.out.println(
                "Caught expected IllegalArgumentException: " + e.getMessage());
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

        if (testFailed) {
            throw new RuntimeException("TEST FAILED");
        }

        System.out.println("Test passed");
    }


    private static void checkResult(Thread curThread,
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

    private static void waitUntilThreadBlocked()
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
            ThreadAllocatedMemoryArray.doit();

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

            ThreadAllocatedMemoryArray.doit();

            synchronized (obj) {
                while (!done1) {
                    try {
                        obj.wait();
                    } catch (InterruptedException e) {
                        reportUnexpected(e, "while !done");
                        break;
                    }
                }
            }

        }
    }
}
