/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/*
 * @test
 * @bug 8318757
 * @summary Test concurrent monitor deflation by MonitorDeflationThread and thread dumping
 * @library /test/lib
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedAsyncDeflationInterval=2000 -XX:LockingMode=0 ConcurrentDeflation
 */

public class ConcurrentDeflation {
    public static final int TOTAL_RUN_TIME = 10 * 1000;
    public static Object[] monitors = new Object[1000];

    public static void main(String[] args) throws Exception {
        Thread thread_dumper  = new Thread(() -> dumpThreads());
        thread_dumper.start();
        Thread monitor_creator  = new Thread(() -> createMonitors());
        monitor_creator.start();

        thread_dumper.join();
        monitor_creator.join();
    }

    static private void dumpThreads() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long startTime = System.currentTimeMillis();
        int dumpCount = 0;

        while (System.currentTimeMillis() - startTime < TOTAL_RUN_TIME) {
            threadBean.dumpAllThreads(true, false);
            dumpCount++;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {}
        }
        System.out.println("Dumped all thread info " + dumpCount + " times");
    }

    static private void createMonitors() {
        monitors = new Object[1000];
        int monitorCount = 0;

        int index = 0;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < TOTAL_RUN_TIME) {
            index = index++ % 1000;
            monitors[index] = new Object();
            synchronized (monitors[index]) {
            }
            monitorCount++;
        }
        System.out.println("Created " + monitorCount + " monitors");
    }
}
