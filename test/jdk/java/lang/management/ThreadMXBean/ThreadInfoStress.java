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

/*
 * @test
 * @bug 8323792
 * @summary Stress ThreadMXBean.getThreadInfo with Threads terminating
 * @requires test.thread.factory != "Virtual"
 * @run main/othervm ThreadInfoStress
 */

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadInfoStress {

    // The assert in Thread::check_for_dangling_thread_pointer happens very quickly.
    // Run for some seconds as a short stress test:
    private static final int DURATION_MS = 5 * 1000;
    private static final int NUM_THREADS = 10;

    private static com.sun.management.ThreadMXBean mbean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    static long [] ids = new long[NUM_THREADS];
    static ThreadInfo [] infos = new ThreadInfo[NUM_THREADS];
    static volatile int count = 0;

    public static void main(String[] argv)
        throws Exception {

        int totalCount = 0;

        startThreads(NUM_THREADS, ids);
        new MyGetThreadInfoThread(ids).start();

        long t1 = System.currentTimeMillis();
        long t2 = t1 + DURATION_MS;

        do {
            // Threads are running and will finish at different times.
            count = countThreadInfo(infos);
            totalCount += count;
            System.out.println("ThreadInfos found (Threads alive): " + count);
            if (count == 0) {
                startThreads(NUM_THREADS, ids);
            }
            goSleep(100);
        } while (System.currentTimeMillis() < t2);

        if (totalCount == 0) {
            // If mitigations for the assert are extremely cautious, and no ThreadInfo are gathered, fail:
            throw new RuntimeException("Failed: No ThreadInfos found.");
        }
        System.out.println("Done.");
    }

    // Start threads, store ids in shared array.
    private static void startThreads(int count, long [] ids) {
        System.out.println("Starting " + count + " Threads...");
        for (int i = 0; i < count; i++) {
            Thread thread = new MyThread(i);
            thread.start();
            ids[i] = thread.threadId();
        }
    }

    // Scan array for non-null ThreadInfo, return count.
    private static int countThreadInfo(ThreadInfo [] infos) {
        int count = 0;
        System.out.print("ThreadInfos found:");
        for (ThreadInfo ti: infos) {
            if (ti != null) {
                System.out.print(" ");
                System.out.print(ti.getThreadId());
                count++;
            }
        }
        System.out.println();
        return count;
    }

    private static void goSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            System.out.println("goSleep: " + e);
        }
    }

    // Thread that sleeps, then ends.
    static class MyThread extends Thread {
        long lifeMs;

        public MyThread(long lifeMs) {
            super("MyThread-" + lifeMs);
            this.lifeMs = lifeMs * lifeMs * 10;
        }

        public void run() {
            goSleep(lifeMs);
        }
    }

    // Continually call getThreadInfo on a shared array of Thread ids,
    // storing in the shared array of ThreadInfo.
    // The ids will get stale, using ids of new or ending or ended threads is part of the test.
    static class MyGetThreadInfoThread extends Thread {
        long [] ids;

        public MyGetThreadInfoThread(long [] ids) {
            this.ids = ids;
        }

        public void run() {
            while (true) {
                infos = mbean.getThreadInfo(ids, 0 /* maxDepth */);
                goSleep(10);
            }
        }
    }
}
