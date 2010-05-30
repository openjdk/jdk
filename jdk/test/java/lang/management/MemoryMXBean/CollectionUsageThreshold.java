/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4959889
 * @summary Basic unit test of memory management testing:
 *          1) setCollectionUsageThreshold() and getCollectionUsageThreshold()
 *          2) test notification emitted for two different memory pools.
 *
 * @author  Mandy Chung
 *
 * @build CollectionUsageThreshold MemoryUtil
 * @run main/timeout=300 CollectionUsageThreshold
 */

import java.lang.management.*;
import java.util.*;
import javax.management.*;
import javax.management.openmbean.CompositeData;

public class CollectionUsageThreshold {
    private static MemoryMXBean mm = ManagementFactory.getMemoryMXBean();
    private static List pools = ManagementFactory.getMemoryPoolMXBeans();
    private static List managers = ManagementFactory.getMemoryManagerMXBeans();
    private static Map result = new HashMap();
    private static boolean trace = false;
    private static boolean testFailed = false;
    private static final int EXPECTED_NUM_POOLS = 2;
    private static final int NUM_GCS = 3;
    private static final int THRESHOLD = 10;
    private static Checker checker;
    private static int numGCs = 0;

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
                    numNotifs++;
                    if (numNotifs > 0 && (numNotifs % EXPECTED_NUM_POOLS) == 0) {
                        checker.goCheckResult();
                    }
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
        for (ListIterator iter = pools.listIterator(); iter.hasNext(); ) {
            MemoryPoolMXBean p = (MemoryPoolMXBean) iter.next();
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

        checker = new Checker("Checker thread");
        checker.setDaemon(true);
        checker.start();

        for (Iterator iter = result.values().iterator(); iter.hasNext();) {
            PoolRecord pr = (PoolRecord) iter.next();
            pr.getPool().setCollectionUsageThreshold(THRESHOLD);
            System.out.println("Collection usage threshold of " +
                pr.getPool().getName() + " set to " + THRESHOLD);
        }

        SensorListener listener = new SensorListener();
        NotificationEmitter emitter = (NotificationEmitter) mm;
        emitter.addNotificationListener(listener, null, null);

        mm.setVerbose(true);
        for (int i = 0; i < NUM_GCS; i++) {
            invokeGC();
            checker.waitForCheckResult();
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
            for (Iterator iter = result.values().iterator(); iter.hasNext();) {
                PoolRecord pr = (PoolRecord) iter.next();
                System.out.println("Usage after GC for: " + pr.getPool().getName());
                MemoryUtil.printMemoryUsage(pr.getPool().getUsage());
            }
        }
    }

    static class Checker extends Thread {
        private Object lock = new Object();
        private Object go = new Object();
        private boolean checkerReady = false;
        private int waiters = 0;
        private boolean readyToCheck = false;
        Checker(String name) {
            super(name);
        };
        public void run() {
            while (true) {
                synchronized (lock) {
                    checkerReady = true;
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    checkResult();
                    checkerReady = false;
                }
            }
        }
        private void checkResult() {
            for (Iterator iter = result.values().iterator(); iter.hasNext();) {
                PoolRecord pr = (PoolRecord) iter.next();
                if (pr.getListenerInvokedCount() != numGCs) {
                    throw new RuntimeException("Listeners invoked count = " +
                         pr.getListenerInvokedCount() + " expected to be " +
                         numGCs);
                }
                if (pr.getNotifCount() != numGCs) {
                    throw new RuntimeException("Notif Count = " +
                         pr.getNotifCount() + " expected to be " +
                         numGCs);
                }

                long count = pr.getPool().getCollectionUsageThresholdCount();
                if (count != numGCs) {
                    throw new RuntimeException("CollectionUsageThresholdCount = " +
                         count + " expected to be " + numGCs);
                }
                if (!pr.getPool().isCollectionUsageThresholdExceeded()) {
                    throw new RuntimeException("isCollectionUsageThresholdExceeded" +
                         " expected to be true");
                }
            }
            synchronized (go) {
                // wait until the main thread is waiting for notification
                while (waiters == 0) {
                    try {
                        go.wait(50);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }

                System.out.println(Thread.currentThread().getName() +
                    " notifying main thread to continue - result checking finished");
                go.notify();
            }
        }
        public void goCheckResult() {
            System.out.println(Thread.currentThread().getName() +
                " notifying to check result");
            synchronized (lock) {
                while (!checkerReady) {
                    try {
                        lock.wait(50);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                lock.notify();
            }
        }

        public void waitForCheckResult() {
            System.out.println(Thread.currentThread().getName() +
                " waiting for result checking finishes");
            synchronized (go) {
                waiters++;
                try {
                    go.wait();
                } catch (InterruptedException e) {
                    // ignore
                }
                waiters--;
            }
        }
    }
}
