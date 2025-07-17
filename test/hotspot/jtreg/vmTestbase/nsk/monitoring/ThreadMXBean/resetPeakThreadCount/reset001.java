/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary converted from VM Testbase nsk/monitoring/ThreadMXBean/resetPeakThreadCount/reset001..reset005
 * VM Testbase keywords: [quick, monitoring]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks that
 *         ThreadMXBean.resetPeakThreadCount()
 *     returns correct result.
 *     The test starts a couple of user threads and waits until they finish. After
 *     that, resetPeakThreadCount() is invoked to reset the peak. Then
 *     getPeakThreadCount() and getThreadCount() must return the same values. The
 *     expectation is that no threads are created, or terminated, between
 *     "getPeakThreadCount()" and getThreadCount()" calls.
 *     The test implements defferent ways to access to the metrics.
 *
 * @comment Direct access to the metrics.
 * @library /vmTestbase
 *          /test/lib
 * @run main/othervm nsk.monitoring.ThreadMXBean.resetPeakThreadCount.reset001
 */

/*
 * @test
 * @comment Access to the metrics via default MBean server.
 * @library /vmTestbase
 *          /test/lib
 * @run main/othervm nsk.monitoring.ThreadMXBean.resetPeakThreadCount.reset001 -testMode=server
 */

/*
 * @test
 * @comment Access to the metrics via custom MBean server.
 * @library /vmTestbase
 *          /test/lib
 * @run main/othervm
 *      nsk.monitoring.ThreadMXBean.resetPeakThreadCount.reset001
 *      -testMode=server
 *      -MBeanServer=custom
 */

/*
 * @test
 * @comment Access to the metrics via default MBean server proxy.
 * @library /vmTestbase
 *          /test/lib
 * @run main/othervm nsk.monitoring.ThreadMXBean.resetPeakThreadCount.reset001 -testMode=proxy
 */

/*
 * @test
 * @comment Access to the metrics via custom MBean server proxy.
 * @library /vmTestbase
 *          /test/lib
 * @run main/othervm
 *      nsk.monitoring.ThreadMXBean.resetPeakThreadCount.reset001
 *      -testMode=proxy
 *      -MBeanServer=custom
 */

package nsk.monitoring.ThreadMXBean.resetPeakThreadCount;

import java.io.*;
import nsk.share.*;
import nsk.monitoring.share.*;

public class reset001 {
    private static boolean testFailed = false;

    public static void main(String[] argv) {
        System.exit(Consts.JCK_STATUS_BASE + run(argv, System.out));
    }

    public static int run(String[] argv, PrintStream out) {
        ArgumentHandler argHandler = new ArgumentHandler(argv);
        Log log = new Log(out, argHandler);
        ThreadMonitor monitor = Monitor.getThreadMonitor(log, argHandler);

        // Start a couple of threads and wait until they exit
        Thread left = new Thread();
        Thread right = new Thread();
        left.start();
        right.start();

        try {
            left.join();
            right.join();
        } catch (InterruptedException e) {
            log.complain("Unexpected exception.");
            e.printStackTrace(log.getOutStream());
            testFailed = true;
        }

        // The test assumes that no threads are created or terminated between
        // "getPeakThreadCount()" and "getThreadCount()" calls
        monitor.resetPeakThreadCount();
        int peak = monitor.getPeakThreadCount();
        int live = monitor.getThreadCount();

        if (peak != live) {
            log.complain("getPeakThreadCount() returned " + peak + ", but "
                      + "getThreadCount() returned " + live + " after "
                      + "resetPeakThreadCount().");
            testFailed = true;
        }

        if (testFailed)
            out.println("TEST FAILED");
        return (testFailed) ? Consts.TEST_FAILED : Consts.TEST_PASSED;
    }
}
