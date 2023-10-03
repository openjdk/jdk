/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

package nsk.monitoring.MemoryPoolMBean.isUsageThresholdExceeded;

import java.lang.management.*;
import java.io.*;
import java.util.*;
import nsk.share.*;
import nsk.monitoring.share.*;

import javax.management.InstanceNotFoundException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

public class isexceeded001 {
    private static boolean testFailed = false;
    private static final int INCREMENT = 100 * 1024; // 100kb
    public static void main(String[] argv) {
        System.exit(Consts.JCK_STATUS_BASE + run(argv, System.out));
    }

    static byte[] b;

    public static int run(String[] argv, PrintStream out) {
        ArgumentHandler argHandler = new ArgumentHandler(argv);
        Log log = new Log(out, argHandler);
        log.enableVerbose(true); // show log output

        MemoryMonitor monitor = Monitor.getMemoryMonitor(log, argHandler);
        List pools = monitor.getMemoryPoolMBeans();

        for (int i = 0; i < pools.size(); i++) {
            Object pool = pools.get(i);
            log.display(i + " pool " + monitor.getName(pool) + " of type: " + monitor.getType(pool));
            if (!monitor.isUsageThresholdSupported(pool)) {
                log.display("  does not support usage thresholds: skip");
                continue;
            }

            // Set a threshold that is greater than used value
            MemoryUsage usage = monitor.getUsage(pool);
            boolean isExceeded = monitor.isUsageThresholdExceeded(pool);
            long used = usage.getUsed();
            long max = usage.getMax();
            long threshold = used + 1;

            if ( (max > -1) && (threshold > max) ) {
                // we can't test threshold - not enough memory
                log.display("not enough memory for testing threshold:" +
                 " used=" + used + ", max=" + max + ": skip");
                continue;
            }

            monitor.setUsageThreshold(pool, threshold);
            log.display("     used value is " + used     + "      max is " + max + " isExceeded = " + isExceeded);
            log.display("  threshold set to " + threshold);
            log.display("  threshold count  " + monitor.getUsageThresholdCount(pool));

            // Reset peak usage so we can use it:
            monitor.resetPeakUsage(pool);
            isExceeded = monitor.isUsageThresholdExceeded(pool);
            log.display("  reset peak usage. peak usage = " + monitor.getPeakUsage(pool).getUsed()
                        + " isExceeded = " + isExceeded);

            // Eat some memory - _may_ cause usage of the pool to cross threshold,
            // but cannot assume this affects the pool we are testing.
            b = new byte[INCREMENT];

            isExceeded = monitor.isUsageThresholdExceeded(pool);
            log.display("  Allocated heap.  isExceeded = " + isExceeded);

            // Fetch usage information: use peak usage in comparisons below, in case usage went up and then down.
            // Log used and peak used in case of failure.
            usage = monitor.getUsage(pool);
            MemoryUsage peakUsage = monitor.getPeakUsage(pool);
            used = usage.getUsed();
            max = usage.getMax();
            long peakUsed = usage.getUsed();
            long peakMax = usage.getMax();

            log.display("     used value is " + used     + "      max is " + max + " isExceeded = " + isExceeded);
            log.display("peak used value is " + peakUsed + " peak max is " + peakMax);
            log.display("  threshold set to " + threshold);
            long thresholdCount = monitor.getUsageThresholdCount(pool);
            log.display("  threshold count  " + thresholdCount);

            // Test can be imprecise, particularly with CodeHeap: usage changes outside our control.
            if (thresholdCount > 0 && monitor.getType(pool) != MemoryType.HEAP) {
                log.display("  thresholdCount increasing outside our control for non-heap Pool: skip");
                continue;
            }

            // If peak used value is less than threshold, then isUsageThresholdExceeded()
            // is expected to return false.
            if (peakUsed < threshold && isExceeded) {
                // used is commonly less than threshold, but isExceeded should not be true:
                log.complain("isUsageThresholdExceeded() returned "
                    + "true, while threshold = " + threshold
                    + " and used peak = " + peakUsed);
                isExceeded = monitor.isUsageThresholdExceeded(pool);
                if (isExceeded) {
                    testFailed = true;
                } else {
                    log.complain("isUsageThresholdExceeded() now says false.");
                }
            } else
            // If peak used value is greater or equal than threshold, then
            // isUsageThresholdExceeded() is expected to return true.
            if (peakUsed >= threshold && !isExceeded) {
                isExceeded = monitor.isUsageThresholdExceeded(pool);
                if (isExceeded) {
                    log.display("isUsageThresholdExceeded() returned false, then true,"
                        + " while threshold = " + threshold + " and "
                        + "used peak = " + peakUsed);
                } else {
                    // Failure:
                    log.complain("isUsageThresholdExceeded() returned false, and is still false,"
                        + " while threshold = " + threshold + " and "
                        + "used peak = " + peakUsed);
                        testFailed = true;
                }
            }

        } // for i

        if (testFailed)
            out.println("TEST FAILED");
        return (testFailed) ? Consts.TEST_FAILED : Consts.TEST_PASSED;
    }
}
