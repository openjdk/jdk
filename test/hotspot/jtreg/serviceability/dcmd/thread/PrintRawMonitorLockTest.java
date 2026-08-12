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

/*
 * @test
 * @bug 8253442
 * @summary Test of diagnostic command Thread.print prints a deadlock of only JVMTI raw monitors.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @requires vm.jvmti
 * @run testng/othervm/native -agentlib:PrintRawMonitorLockTest PrintRawMonitorLockTest
 */

import org.testng.SkipException;
import org.testng.annotations.Test;
import org.testng.Assert;

import jdk.test.lib.process.OutputAnalyzer;

import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class PrintRawMonitorLockTest {

    private static void log(String s) { System.out.println(s); }

    static native int createRawMonitors();
    static native int rawMonitorEnter(int id);

    CyclicBarrier readyBarrier = new CyclicBarrier(3);

    private void waitForBarrier(CyclicBarrier b) {
        try {
            b.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            Assert.fail("Test error: Caught unexpected exception:", e);
        }
    }

    class RawMonitorThread extends Thread {
        int id;
        int otherid;

        RawMonitorThread(int id, int otherid) {
            this.id = id;
            this.otherid = otherid;
            setDaemon(true);
        }

        public void run() {
            int retCode = rawMonitorEnter(id);
            if (retCode != 0) {
                throw new RuntimeException("error in JVMTI RawMonitorEnter: " +
                                           "retCode=" + retCode);
            }
            log("entered my lock");

            // Signal that we're ready for thread dump.
            waitForBarrier(readyBarrier);

            log("trying to enter the other lock");
            retCode = rawMonitorEnter(otherid);
            if (retCode != 0) {
                throw new RuntimeException("error in JVMTI RawMonitorEnter: " +
                                           "retCode=" + retCode);
            }
            log("shouldn't get here since the threads are deadlocked");
        }
    }

    private void setupRawMonitors() {
        int retCode = createRawMonitors();
        if (retCode != 0) {
            throw new RuntimeException("error in JVMTI CreateRawMonitor: " +
                                       "retCode=" + retCode);
        }
        log("created JVM TI raw monitors");
    }

    public void run(CommandExecutor executor) {
        setupRawMonitors();

        RawMonitorThread aThread = new RawMonitorThread(1, 2);
        aThread.start();

        RawMonitorThread bThread = new RawMonitorThread(2, 1);
        bThread.start();

        // Wait for threads to get ready.
        waitForBarrier(readyBarrier);

        OutputAnalyzer output = executor.execute("Thread.print -l=true");
        if (!output.getOutput().contains("Found 1 deadlock")) {
            // Execute dcmd in a timed loop in case the threads haven't deadlocked yet.
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                }
                // try again, otherwise java thinks output is not initialized.
                output = executor.execute("Thread.print -l=true");
                if (output.getOutput().contains("Found 1 deadlock")) {
                    break;
                }
            }
        }
        output.shouldContain("waiting to lock JVM TI raw monitor");
    }

    @Test
    public void jmx() {
        if (Thread.currentThread().isVirtual()) {
            throw new SkipException("skipping test since current thread is virtual thread");
        }
        run(new JMXExecutor());
    }
}
