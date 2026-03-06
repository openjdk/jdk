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
import java.util.concurrent.locks.ReentrantLock;

/*
 * @test
 * @bug 8323792
 * @summary Make sure that jmm_GetThreadInfo() call does not crash JVM
 * @library /test/lib
 * @run main/othervm ThreadInfoTest
 */

public class ThreadInfoTest {
    private static com.sun.management.ThreadMXBean mbean =
        (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();

    private static final int NUM_THREADS = 2;
    static long [] ids = new long[NUM_THREADS];
    static ThreadInfo [] infos = new ThreadInfo[NUM_THREADS];
    static volatile int count = 0;
    static int ITERATIONS = 4;

    public static void main(String[] argv)
        throws Exception {

        boolean replacing = false;
        int prevCount = -1;

        startThreads(ids, NUM_THREADS);

        new MyGetThreadInfoThread(ids).start();
        new MyReplacerThread(ids).start();
        new MyBusyThread().start();
        for (int index = 0; index < ITERATIONS; index++) {
            do {
                if (count == 0) {
                    startThreads(ids, NUM_THREADS);
                }
                count = showInfo(ids, infos);
                if (count != prevCount) {
                    System.out.println("ThreadInfos found (Threads alive): " + count);
                }
                prevCount = count;
                goSleep(100);
            } while (count > 0);
        }
    }

    private static Thread newThread(int i) {
        return new MyThread(i);
    }

    private static void startThreads(long [] ids, int count) {
        System.out.println("Starting " + count + " Threads...");
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            threads[i] = newThread(i);
            threads[i].start();
            ids[i] = threads[i].getId();
        }
        System.out.println(ids);
    }

    // Show ThreadInfo from array, return how many are non-null.
    private static int showInfo(long [] ids, ThreadInfo [] info) {
        int count = 0;
        if (info != null) {
        int i = 0;
        int maxToReplace = 1;
            for (ThreadInfo ti: info) {
                if (ti != null) {
                    count++;
                }
                i++;
            }
        }
        return count;
    }
    private static int replaceThreads(long [] ids, ThreadInfo [] info, int maxToReplace) {
        int replaced = 0;
        int count = 0;
        if (info != null) {
            int i = 0;
            for (ThreadInfo ti: info) {
                if (ti == null && replaced < maxToReplace) {
                    Thread thread = newThread(i);
                    thread.start();
                    ids[i] = thread.getId();
                    count++;
                    replaced++;
                }
                i++;
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

    static class MyReplacerThread extends Thread {
        long [] ids;

        public MyReplacerThread(long []ids) {
            this.ids = ids;
        }

        public void run() {
            boolean replacing = false;
            while (true) {
                if (replacing) {
                    int replaced = replaceThreads(ids, infos, 10);
                }
                if (count < 100) {
                    replacing = true;
                }
                if (count > 200) {
                    replacing = false;
                }
                goSleep(1);
            }
        }
    }

    static class MyThread extends Thread {
        long lifeMs;
        long endTimeMs;

        public MyThread(long lifeMs) {
            super("MyThread-" + lifeMs);
            this.lifeMs = lifeMs * lifeMs * 10;
            endTimeMs = lifeMs + System.currentTimeMillis();
        }

        public void run() {
            try {
                ReentrantLock lock = new ReentrantLock();
                lock.lock();
                Exception myException = new Exception("Test exception");
                long sleep = Math.max(1, endTimeMs - System.currentTimeMillis());
                goSleep(sleep);
            } catch (Exception e) {
                System.out.println(Thread.currentThread().getName() + ": " + e);
            }
        }
    }

    static class MySteadyThread extends Thread {
        long lifeMs;

        public MySteadyThread(long i) {
            super("MySteadyThread-" + i);
            this.lifeMs = i * 10;
        }

        public void run() {
            while (true) {
                try {
                Object o = new Object();
                synchronized (o) {
                    goSleep(lifeMs);
                    o.wait(lifeMs);
                }
                } catch (Exception e) {
                    System.err.println(e);
                }
                goSleep(1);
            }
        }
    }

    static class MyGetThreadInfoThread extends Thread {
        long [] ids;

        public MyGetThreadInfoThread(long [] ids) {
            this.ids = ids;
        }

        public void run() {
            while (true) {
                infos = mbean.getThreadInfo(ids, 0);
                goSleep(10);
            }
        }
    }

    static class MyBusyThread extends Thread {
        List<Object> list;

        public MyBusyThread() {
            super("MyBusyThread");
            list = new ArrayList<Object>();
        }

        public void run() {
            long i = 0;
            while (true) {
                if (i % 1000 == 0) {
                  list = new ArrayList<Object>();
                }
                byte [] junk = new byte[1024*104*10];
                list.add(junk);
                goSleep(10);
                i++;
            }
        }
    }
}

