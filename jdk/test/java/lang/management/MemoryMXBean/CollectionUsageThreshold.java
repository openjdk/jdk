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
 * @bug     4959889 6992968
 * @summary Basic unit test of memory management testing:
 *          1) setCollectionUsageThreshold() and getCollectionUsageThreshold()
 *          2) test notification emitted for two different memory pools.
 *
 * @author  Mandy Chung
 *
 * @build CollectionUsageThreshold MemoryUtil
 * @run main/timeout=300 CollectionUsageThreshold
 */

import java.lang.Thread.*;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import javax.management.*;
import javax.management.openmbean.CompositeData;

public class CollectionUsageThreshold {
    private static MemoryMXBean mm = ManagementFactory.getMemoryMXBean();
    private static List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
    private static List<MemoryManagerMXBean> managers = ManagementFactory.getMemoryManagerMXBeans();
    private static Map<String, PoolRecord> result = new HashMap<>();
    private static boolean trace = false;
    private static boolean testFailed = false;
    private static final int EXPECTED_NUM_POOLS = 2;
    private static final int NUM_GCS = 3;
    private static final int THRESHOLD = 10;
    private static Checker checker;
    private static int numGCs = 0;

    // semaphore to signal the arrival of a low memory notification
    private static Semaphore signals = new Semaphore(0);
    // barrier for the main thread to wait until the checker thread
    // finishes checking the low memory notification result
    private static CyclicBarrier barrier = new CyclicBarrier(2);

    static class PoolRecord {
        private MemoryPoolMXBean pool;
        private int listenerInvoked = 0;
        private long notifCount = 0;
        PoolRecord(MemoryPoolMXBean p) {
            this.pool = p;
        }
        int getListenerInvokedCount() {
            return listenerInvoked;
        }
        long getNotifCount() {
            return notifCount;
        }
        MemoryPoolMXBean getPool() {
            return pool;
        }
        void addNotification(MemoryNotificationInfo minfo) {
            listenerInvoked++;
            notifCount = minfo.getCount();
        }
    }

    static class SensorListener implements NotificationListener {
        private int numNotifs = 0;
        public void handleNotification(Notification notif, Object handback) {
            String type = notif.getType();
            if (type.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED) ||
                type.equals(MemoryNotificationInfo.
                    MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) {
                MemoryNotificationInfo minfo = MemoryNotificationInfo.
                    from((CompositeData) notif.getUserData());

                MemoryUtil.printMemoryNotificationInfo(minfo, type);
                PoolRecord pr = (PoolRecord) result.get(minfo.getPoolName());
                if (pr == null) {
                    throw new RuntimeException("Pool " + minfo.getPoolName() +
                        " is not selected");
                }
                if (type != MemoryNotificationInfo.
                        MEMORY_COLLECTION_THRESHOLD_EXCEEDED) {
                    throw new RuntimeException("Pool " + minfo.getPoolName() +
                        " got unexpected notification type: " +
                        type);
                }
                pr.addNotification(minfo);
                synchronized (this) {
                    System.out.println("notifying the checker thread to check result");
                    numNotifs++;
                    signals.release();
                }
            }
        }
    }

    private static long newThreshold;
    public static void main(String args[]) throws Exception {
        if (args.length > 0 && args[0].equals("trace")) {
            trace = true;
        }

        if (trace) {
            MemoryUtil.printMemoryPools(pools);
            MemoryUtil.printMemoryManagers(managers);
        }

        // Find the Old generation which supports low memory detection
        for (MemoryPoolMXBean p : pools) {
            MemoryUsage u = p.getUsage();
            if (p.isUsageThresholdSupported() && p.isCollectionUsageThresholdSupported()) {
                PoolRecord pr = new PoolRecord(p);
                result.put(p.getName(), pr);
                if (result.size() == EXPECTED_NUM_POOLS) {
                    break;
                }
            }
        }
        if (result.size() != EXPECTED_NUM_POOLS) {
            throw new RuntimeException("Unexpected number of selected pools");
        }

        try {
            // This test creates a checker thread responsible for checking
            // the low memory notifications.  It blocks until a permit
            // from the signals semaphore is available.
            checker = new Checker("Checker thread");
            checker.setDaemon(true);
            checker.start();

            for (PoolRecord pr : result.values()) {
                pr.getPool().setCollectionUsageThreshold(THRESHOLD);
                System.out.println("Collection usage threshold of " +
                    pr.getPool().getName() + " set to " + THRESHOLD);
            }

            SensorListener listener = new SensorListener();
            NotificationEmitter emitter = (NotificationEmitter) mm;
            emitter.addNotificationListener(listener, null, null);

            // The main thread invokes GC to trigger the VM to perform
            // low memory detection and then waits until the checker thread
            // finishes its work to check for a low-memory notification.
            //
            // At GC time, VM will issue low-memory notification and invoke
            // the listener which will release a permit to the signals semaphore.
            // When the checker thread acquires the permit and finishes
            // checking the low-memory notification, it will also call
            // barrier.await() to signal the main thread to resume its work.
            for (int i = 0; i < NUM_GCS; i++) {
                invokeGC();
                barrier.await();
            }
        } finally {
            // restore the default
            for (PoolRecord pr : result.values()) {
                pr.getPool().setCollectionUsageThreshold(0);
            }
        }

        if (testFailed)
            throw new RuntimeException("TEST FAILED.");

        System.out.println("Test passed.");

    }


    private static void invokeGC() {
        System.out.println("Calling System.gc()");
        numGCs++;
        mm.gc();

        if (trace) {
            for (PoolRecord pr : result.values()) {
                System.out.println("Usage after GC for: " + pr.getPool().getName());
                MemoryUtil.printMemoryUsage(pr.getPool().getUsage());
            }
        }
    }

    static class Checker extends Thread {
        private boolean checkerReady = false;
        private int waiters = 0;
        private boolean readyToCheck = false;
        Checker(String name) {
            super(name);
        };
        public void run() {
            while (true) {
                try {
                    signals.acquire(EXPECTED_NUM_POOLS);
                    checkResult();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        private void checkResult() throws InterruptedException, BrokenBarrierException {
            for (PoolRecord pr : result.values()) {
                if (pr.getListenerInvokedCount() != numGCs) {
                    fail("Listeners invoked count = " +
                         pr.getListenerInvokedCount() + " expected to be " +
                         numGCs);
                }
                if (pr.getNotifCount() != numGCs) {
                    fail("Notif Count = " +
                         pr.getNotifCount() + " expected to be " +
                         numGCs);
                }

                long count = pr.getPool().getCollectionUsageThresholdCount();
                if (count != numGCs) {
                    fail("CollectionUsageThresholdCount = " +
                         count + " expected to be " + numGCs);
                }
                if (!pr.getPool().isCollectionUsageThresholdExceeded()) {
                    fail("isCollectionUsageThresholdExceeded" +
                         " expected to be true");
                }
            }
            // wait until the main thread is waiting for notification
            barrier.await();
            System.out.println("notifying main thread to continue - result checking finished");
        }

        private void fail(String msg) {
            // reset the barrier to cause BrokenBarrierException to avoid hanging
            barrier.reset();
            throw new RuntimeException(msg);
        }
    }
}
