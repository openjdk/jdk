/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @summary Tests that recursive locking doesn't cause excessive native memory usage
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xmx100M -XX:AsyncDeflationInterval=0 -XX:GuaranteedAsyncDeflationInterval=0
 *                   -Xlog:monitorinflation=trace
 *                   TestRecursiveMonitorChurn
 */

import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

public class TestRecursiveMonitorChurn {
    static class Monitor {
        public static volatile int i, j;
        synchronized void doSomething() {
            i++;
            doSomethingElse();
        }
        synchronized void doSomethingElse() {
            j++;
        }
    }

    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final int LM_MONITOR = 0;
    static final int COUNT = 100000;

    public static volatile Monitor monitor;
    public static void main(String[] args) {
        if (WB.getIntVMFlag("LockingMode") == LM_MONITOR) {
            throw new SkippedException("LM_MONITOR always inflates. Invalid test.");
        }
        final long pre_monitor_count = WB.getInUseMonitorCount();
        System.out.println(" Precount = " + pre_monitor_count);
        for (int i = 0; i < COUNT; i++) {
            monitor = new Monitor();
            monitor.doSomething();
        }
        System.out.println("i + j = " + (Monitor.i + Monitor.j));
        final long post_monitor_count = WB.getInUseMonitorCount();
        System.out.println("Postcount = " + post_monitor_count);

        if (pre_monitor_count != post_monitor_count) {
            final long monitor_count_change = post_monitor_count - pre_monitor_count;
            System.out.println("Unexpected change in monitor count: " + monitor_count_change);
            if (monitor_count_change < 0) {
                throw new RuntimeException("Unexpected Deflation");
            }
            throw new RuntimeException("Unexpected Inflation");
        }
    }
}
