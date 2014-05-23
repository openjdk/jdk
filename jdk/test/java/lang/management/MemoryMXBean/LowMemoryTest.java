/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4530538
 * @summary Basic unit test of memory management testing:
 *          1) setUsageThreshold() and getUsageThreshold()
 *          2) test low memory detection on the old generation.
 *
 * @author  Mandy Chung
 *
 * @library /lib/testlibrary/
 * @build jdk.testlibrary.* LowMemoryTest MemoryUtil RunUtil
 * @run main/timeout=600 LowMemoryTest
 */

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.Phaser;
import javax.management.*;
import javax.management.openmbean.CompositeData;

public class LowMemoryTest {
    private static final MemoryMXBean mm = ManagementFactory.getMemoryMXBean();
    private static final List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
    private static final Phaser phaser = new Phaser(2);
    private static MemoryPoolMXBean mpool = null;
    private static boolean trace = false;
    private static boolean testFailed = false;
    private static final int NUM_TRIGGERS = 5;
    private static final int NUM_CHUNKS = 2;
    private static long chunkSize;

    /**
     * Run the test multiple times with different GC versions.
     * First with default command line specified by the framework.
     * Then with GC versions specified by the test.
     */
    public static void main(String a[]) throws Throwable {
        final String main = "LowMemoryTest$TestMain";
        RunUtil.runTestKeepGcOpts(main);
        RunUtil.runTestClearGcOpts(main, "-XX:+UseSerialGC");
        RunUtil.runTestClearGcOpts(main, "-XX:+UseParallelGC");
        RunUtil.runTestClearGcOpts(main, "-XX:+UseG1GC");
        RunUtil.runTestClearGcOpts(main, "-XX:+UseConcMarkSweepGC");
    }

    private static volatile boolean listenerInvoked = false;
    static class SensorListener implements NotificationListener {
        @Override
        public void handleNotification(Notification notif, Object handback) {
            String type = notif.getType();
            if (type.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED) ||
                type.equals(MemoryNotificationInfo.
                    MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) {

                MemoryNotificationInfo minfo = MemoryNotificationInfo.
                    from((CompositeData) notif.getUserData());

                MemoryUtil.printMemoryNotificationInfo(minfo, type);
                listenerInvoked = true;
            }
        }
    }

    static class TestListener implements NotificationListener {
        private int triggers = 0;
        private final long[] count = new long[NUM_TRIGGERS * 2];
        private final long[] usedMemory = new long[NUM_TRIGGERS * 2];
        @Override
        public void handleNotification(Notification notif, Object handback) {
            MemoryNotificationInfo minfo = MemoryNotificationInfo.
                from((CompositeData) notif.getUserData());
            count[triggers] = minfo.getCount();
            usedMemory[triggers] = minfo.getUsage().getUsed();
            triggers++;
        }
        public void checkResult() throws Exception {
            if (triggers != NUM_TRIGGERS) {
                throw new RuntimeException("Unexpected number of triggers = " +
                    triggers + " but expected to be " + NUM_TRIGGERS);
            }

            for (int i = 0; i < triggers; i++) {
                if (count[i] != i+1) {
                    throw new RuntimeException("Unexpected count of" +
                        " notification #" + i +
                        " count = " + count[i] +
                        " but expected to be " + (i+1));
                }
                if (usedMemory[i] < newThreshold) {
                    throw new RuntimeException("Used memory = " +
                        usedMemory[i] + " is less than the threshold = " +
                        newThreshold);
                }
            }
        }
    }

    private static long newThreshold;

    private static class TestMain {
        public static void main(String args[]) throws Exception {
            if (args.length > 0 && args[0].equals("trace")) {
                trace = true;
            }

            // Find the Old generation which supports low memory detection
            ListIterator iter = pools.listIterator();
            while (iter.hasNext()) {
                MemoryPoolMXBean p = (MemoryPoolMXBean) iter.next();
                if (p.getType() == MemoryType.HEAP &&
                    p.isUsageThresholdSupported()) {
                    mpool = p;
                    if (trace) {
                        System.out.println("Selected memory pool for low memory " +
                            "detection.");
                        MemoryUtil.printMemoryPool(mpool);
                    }
                    break;
                }
            }

            TestListener listener = new TestListener();
            SensorListener l2 = new SensorListener();
            NotificationEmitter emitter = (NotificationEmitter) mm;
            emitter.addNotificationListener(listener, null, null);
            emitter.addNotificationListener(l2, null, null);

            Thread allocator = new AllocatorThread();
            Thread sweeper = new SweeperThread();

            // Now set threshold
            MemoryUsage mu = mpool.getUsage();
            chunkSize = (mu.getMax() - mu.getUsed()) / 20;
            newThreshold = mu.getUsed() + (chunkSize * NUM_CHUNKS);

            System.out.println("Setting threshold for " + mpool.getName() +
                " from " + mpool.getUsageThreshold() + " to " + newThreshold +
                ".  Current used = " + mu.getUsed());
            mpool.setUsageThreshold(newThreshold);

            if (mpool.getUsageThreshold() != newThreshold) {
                throw new RuntimeException("TEST FAILED: " +
                "Threshold for Memory pool " + mpool.getName() +
                "is " + mpool.getUsageThreshold() + " but expected to be" +
                newThreshold);
            }


            allocator.start();
            // Force Allocator start first
            phaser.arriveAndAwaitAdvance();
            sweeper.start();


            try {
                allocator.join();
                // Wait until AllocatorThread's done
                phaser.arriveAndAwaitAdvance();
                sweeper.join();
            } catch (InterruptedException e) {
                System.out.println("Unexpected exception:" + e);
                testFailed = true;
            }

            listener.checkResult();

            if (testFailed)
                throw new RuntimeException("TEST FAILED.");

            System.out.println(RunUtil.successMessage);

        }
    }

    private static void goSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            System.out.println("Unexpected exception:" + e);
            testFailed = true;
        }
    }

    private static final List<Object> objectPool = new ArrayList<>();
    static class AllocatorThread extends Thread {
        public void doTask() {
            int iterations = 0;
            int numElements = (int) (chunkSize / 4); // minimal object size
            while (!listenerInvoked || mpool.getUsage().getUsed() < mpool.getUsageThreshold()) {
                iterations++;
                if (trace) {
                    System.out.println("   Iteration " + iterations +
                        ": before allocation " +
                        mpool.getUsage().getUsed());
                }

                Object[] o = new Object[numElements];
                if (iterations <= NUM_CHUNKS) {
                    // only hold a reference to the first NUM_CHUNKS
                    // allocated objects
                    objectPool.add(o);
                }

                if (trace) {
                    System.out.println("               " +
                        "  after allocation " +
                        mpool.getUsage().getUsed());
                }
                goSleep(100);
            }
        }
        @Override
        public void run() {
            for (int i = 1; i <= NUM_TRIGGERS; i++) {
                // Sync with SweeperThread's second phase.
                phaser.arriveAndAwaitAdvance();
                System.out.println("AllocatorThread is doing task " + i +
                    " phase " + phaser.getPhase());
                doTask();
                // Sync with SweeperThread's first phase.
                phaser.arriveAndAwaitAdvance();
                System.out.println("AllocatorThread done task " + i +
                    " phase " + phaser.getPhase());
                if (testFailed) {
                    return;
                }
            }
        }
    }

    static class SweeperThread extends Thread {
        private void doTask() {
            for (; mpool.getUsage().getUsed() >=
                       mpool.getUsageThreshold();) {
                // clear all allocated objects and invoke GC
                objectPool.clear();
                mm.gc();
                goSleep(100);
            }
        }
        @Override
        public void run() {
            for (int i = 1; i <= NUM_TRIGGERS; i++) {
                // Sync with AllocatorThread's first phase.
                phaser.arriveAndAwaitAdvance();
                System.out.println("SweepThread is doing task " + i +
                    " phase " + phaser.getPhase());
                doTask();

                listenerInvoked = false;

                // Sync with AllocatorThread's second phase.
                phaser.arriveAndAwaitAdvance();
                System.out.println("SweepThread done task " + i +
                    " phase " + phaser.getPhase());
                if (testFailed) return;
            }
        }
    }
}
