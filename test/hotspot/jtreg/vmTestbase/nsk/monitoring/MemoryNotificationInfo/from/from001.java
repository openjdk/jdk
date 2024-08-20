/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.monitoring.MemoryNotificationInfo.from;

import java.lang.management.*;
import javax.management.*;
import javax.management.openmbean.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import nsk.share.*;
import nsk.share.gc.Algorithms;
import nsk.share.gc.Memory;
import nsk.share.gc.gp.GarbageUtils;
import nsk.monitoring.share.*;
import nsk.share.test.Stresser;

public class from001 {

    private static boolean testFailed = false;
    private static final int MAX_TRIES = 6; // limit attempts to receive Notification data

    public static void main(String[] args) {

        ArgumentHandler argHandler = new ArgumentHandler(args);
        Log log = new Log(System.out, argHandler);

        log.display("MemoryNotificationInfo/from/from001/from001.java test started.");

        MemoryMonitor monitor = Monitor.getMemoryMonitor(log, argHandler);
        MBeanServer mbs = Monitor.getMBeanServer();

        // 1. Check null CompositeData - null must be returned
        MemoryNotificationInfo result = MemoryNotificationInfo.from(null);

        if (result != null) {
            log.complain("FAILURE 1.");
            log.complain("MemoryNotificationInfo.from(null) returned " + result
                      + ", expected: null.");
            testFailed = true;
        }

        log.display("null CompositeData check passed.");

        // 2. Check CompositeData that does not represent MemoryNotificationInfo
        // throws IllegalArgumentException

        ObjectName mbeanObjectName = null;
        CompositeData cdata = null;
        try {
            mbeanObjectName = new ObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
            cdata = (CompositeData )mbs.getAttribute(mbeanObjectName,
                                                                "HeapMemoryUsage");
        } catch (Exception e) {
            log.complain("Unexpected exception " + e);
            e.printStackTrace(log.getOutStream());
            testFailed = true;
        }

        try {
            result = MemoryNotificationInfo.from(cdata);
            log.complain("FAILURE 2.");
            log.complain("MemoryNotificationInfo.from(CompositeData) returned "
                      + result + ", expected: IllegalArgumentException.");
            testFailed = true;
        } catch (IllegalArgumentException e) {

            // Expected: CompositeData does not represent MemoryNotificationInfo
        }

        log.display("check that CompositeData does not represent MemoryNotificationInfo passed.");

        // 3. Check correct CompositeData usage:
        // First try to provoke a Notification on a MemoryPool.
        Object poolObject = null;
        try {
            mbeanObjectName = new ObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
            mbs.addNotificationListener(mbeanObjectName, new from001Listener(),
                                                              null, null);
            List<?> pools = monitor.getMemoryPoolMBeans();
            if (pools.isEmpty()) {
               log.complain("No Memory Pool Beans found. Test case will hang/fail.");
               testFailed = true;
            }

            for (int i = 0; i < pools.size(); i++) {
                Object pool = pools.get(i);
                if (monitor.isUsageThresholdSupported(pool)) {
                    if (monitor.getType(pool).equals(MemoryType.HEAP)) {
                        poolObject = pool;
                        monitor.setUsageThreshold(pool, 1);
                        log.display("Usage threshold set for pool :" + poolObject);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.complain("Unexpected exception " + e);
            e.printStackTrace(log.getOutStream());
            testFailed = true;
        }

        if (testFailed) {
            throw new TestFailure("TEST FAILED. See log.");
        }

        if (poolObject == null) {
            throw new TestFailure("No memory pool found to test.");
        }

        // eat memory just to emit notification
        Stresser stresser = new Stresser(args) {

            @Override
            public boolean continueExecution() {
                return from001Listener.data.get() == null
                        && super.continueExecution();
            }
        };
        stresser.start(0);// we use timeout, not iterations
        int oomCount = GarbageUtils.eatMemory(stresser);
        log.display("eatMemory returns OOM count: " + oomCount);

        // Check for the message.  Poll on queue to avoid waiting forver on failure.
        // Notification is known to fail, very rarely, with -Xcomp where the allocations
        // do not affect the monitored pool. Possibly a timing issue, where the "eatMemory"
        // is done before Notification/threshold processing happens.
        // The Notification is quite immediate, other than that problem.
        boolean messageReceived = false;
        int tries = 0;
        while (!messageReceived && ++tries < MAX_TRIES) {
            try {
                Object r = from001Listener.queue.poll(10000, TimeUnit.MILLISECONDS);
                if (r == null) {
                    log.display("poll for Notification data returns null...");
                    continue;
                } else {
                    messageReceived = true;
                    break;
                }
            } catch (InterruptedException e) {
                // ignored, continue
            }
        }

        // If we got a Notification, test that the CompositeData can create a MemoryNotificationInfo
        if (!messageReceived) {
            throw new TestFailure("No Notification received.");
        }
        result = MemoryNotificationInfo.from(from001Listener.data.get());
        try {
            ObjectName poolObjectName = new ObjectName(monitor.getName(poolObject));
            ObjectName resultObjectName = new ObjectName(
                        ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE +
                        ",name=" + result.getPoolName());

            log.display("poolObjectName : " + poolObjectName +
                        " resultObjectName : " + resultObjectName);

            if (!poolObjectName.equals(resultObjectName)) {
                log.complain("FAILURE 3.");
                log.complain("Wrong pool name : " + resultObjectName +
                             ", expected : " + poolObjectName);
                testFailed = true;
            }

        } catch (Exception e) {
            log.complain("Unexpected exception " + e);
            e.printStackTrace(log.getOutStream());
            testFailed = true;
        }
        if (testFailed) {
            throw new TestFailure("TEST FAILED. See log.");
        }

        log.display("Test passed.");
    }
}


class from001Listener implements NotificationListener {

    static AtomicReference<CompositeData> data = new AtomicReference<CompositeData>();
    static SynchronousQueue<Object> queue = new SynchronousQueue<Object>();

    public void handleNotification(Notification notification, Object handback) {
        if (data.get() != null) {
            System.out.println("handleNotification: ignoring");
            return;
        }
        System.out.println("handleNotification: getting data");
        CompositeData d = (CompositeData) notification.getUserData();
        data.set(d);

        boolean messageNotSent = true;
        while(messageNotSent){
            try {
                queue.put(new Object());
                messageNotSent = false;
            } catch(InterruptedException e) {
                // ignore, retry
            }
        }
    }

}
