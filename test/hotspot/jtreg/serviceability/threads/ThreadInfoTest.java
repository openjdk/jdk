/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Utils;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @bug 8323792
 * @summary Make sure that jmm_GetThreadInfo() call does not crash JVM
 * @library /test/lib
 * @modules java.management
 * @run main/othervm ThreadInfoTest
 *
 * @comment Exercise getThreadInfo(ids, 0).  Depth parameter of zero means
 * no VM operation, which could crash with Threads starting and ending.
 */

public class ThreadInfoTest {
    private static com.sun.management.ThreadMXBean mbean =
        (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();

    private static final int NUM_THREADS = 2;
    static long[] ids = new long[NUM_THREADS];
    static ThreadInfo[] infos = new ThreadInfo[NUM_THREADS];
    static volatile int count = 0;
    static int ITERATIONS = 4;

    public static void main(String[] argv) throws Exception {
        boolean replacing = false;

        startThreads(ids, NUM_THREADS);
        new MyGetThreadInfoThread(ids).start();
        new MyReplacerThread(ids).start();
        for (int i = 0; i < ITERATIONS; i++) {
            do {
                count = countInfo(infos);
                System.out.println("Iteration " + i + ": ThreadInfos found (Threads alive): " + count);
                goSleep(100);
            } while (count > 0);
        }
    }

    private static Thread newThread(int i) {
        Thread thread = new MyThread(i);
        thread.setDaemon(true);
        return thread;
    }

   private static void startThreads(long[] ids, int count) {
        System.out.println("Starting " + count + " Threads...");
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            threads[i] = newThread(i);
            threads[i].start();
            ids[i] = threads[i].getId();
        }
        System.out.println(ids);
    }

    // Count ThreadInfo from array, return how many are non-null.
    private static int countInfo(ThreadInfo[] info) {
        int count = 0;
        if (info != null) {
            int i = 0;
            for (ThreadInfo ti: info) {
                if (ti != null) {
                    count++;
                }
                i++;
            }
        }
        return count;
    }

    private static int replaceThreads(long[] ids, ThreadInfo[] info) {
        int replaced = 0;
        if (info != null) {
            for (int i = 0; i < info.length; i++) {
                ThreadInfo ti = info[i];
                if (ti == null) {
                    Thread thread = newThread(i);
                    thread.start();
                    ids[i] = thread.getId();
                    replaced++;
                }
            }
        }
        return replaced;
    }

    private static void goSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            System.out.println("Unexpected exception is thrown: " + e);
        }
    }

    // A Thread which replaces Threads in the shared array of threads.
    static class MyReplacerThread extends Thread {
        long[] ids;

        public MyReplacerThread(long[] ids) {
            this.ids = ids;
            this.setDaemon(true);
        }

        public void run() {
            boolean replacing = false;
            while (true) {
                if (replacing) {
                    replaceThreads(ids, infos);
                }
                if (count < 10) {
                    replacing = true;
                }
                if (count > 20) {
                    replacing = false;
                }
                goSleep(1);
            }
        }
    }

    // A Thread which lives for a short while.
    static class MyThread extends Thread {
        long endTimeMs;

        public MyThread(long n) {
            super("MyThread-" + n);
            endTimeMs = (n * n * 10) + System.currentTimeMillis();
        }

        public void run() {
            try {
                long sleep = Math.max(1, endTimeMs - System.currentTimeMillis());
                goSleep(sleep);
            } catch (Exception e) {
                System.out.println(Thread.currentThread().getName() + ": " + e);
            }
        }
    }

    // A Thread to continually call getThreadInfo on a shared array of thread ids.
    static class MyGetThreadInfoThread extends Thread {
        long[] ids;

        public MyGetThreadInfoThread(long[] ids) {
            this.ids = ids;
            this.setDaemon(true);
        }

        public void run() {
            while (true) {
                infos = mbean.getThreadInfo(ids, 0);
                goSleep(10);
            }
        }
    }
}
