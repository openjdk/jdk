/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @summary Test SuspendAllVirtualThreads/ResumeAllVirtualThreads
 * @requires vm.debug != true
 * @library /test/lib
 * @compile SuspendResume2.java
 * @run driver jdk.test.lib.FileInstaller . .
 * @run main/othervm/native
 *      -Djdk.virtualThreadScheduler.maxPoolSize=1
 *      -agentlib:SuspendResume2
 *      SuspendResume2
 */

/*
 * @test id=debug
 * @requires vm.debug == true
 * @library /test/lib
 * @compile SuspendResume2.java
 * @run driver jdk.test.lib.FileInstaller . .
 * @run main/othervm/native
 *      -Djdk.virtualThreadScheduler.maxPoolSize=1
 *      -agentlib:SuspendResume2
 *      -XX:-VerifyContinuations
 *      SuspendResume2
 */

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @library /test/lib
 * @compile SuspendResume2.java
 * @run driver jdk.test.lib.FileInstaller . .
 * @run main/othervm/native
 *      -agentlib:SuspendResume2
 *      -XX:+UnlockExperimentalVMOptions
 *      -XX:-VMContinuations
 *      SuspendResume2
 */

import java.io.PrintStream;
import java.util.concurrent.*;
import jdk.test.lib.jvmti.DebugeeClass;

public class SuspendResume2 extends DebugeeClass {

    // load native library if required
    static {
        System.loadLibrary("SuspendResume2");
    }

    native static void TestSuspendResume();
    native static int GetStatus();

    static public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interruption in TestedThread.sleep: \n\t" + e);
        }
    }

    // run test from command line
    public static void main(String argv[]) {
        int status = run(argv, System.out);
        if (status != DebugeeClass.TEST_PASSED) {
            throw new RuntimeException("FAILED: unexpected status: " + status);
        }
    }

    public static int run(String argv[], PrintStream out) {
        return new SuspendResume2().runIt(argv, out);
    }

    private static final int VTHREADS_CNT = 20;
    int status = DebugeeClass.TEST_PASSED;

    // run debuggee
    public int runIt(String argv[], PrintStream out) {
        System.out.println("\n## Java: runIt: Starting threads");
        status = test_vthreads();
        if (status != DebugeeClass.TEST_PASSED) {
            System.out.println("\n## Java: runIt FAILED: status from native Agent: " + status);
        }
        return status;
    }

    private int test_vthreads() {
        TestedThread[] threads = new TestedThread[VTHREADS_CNT];
        Thread vts[] = new Thread[VTHREADS_CNT];

        for (int i = 0; i < VTHREADS_CNT; i++) {
            String name = "TestedThread" + i;
            TestedThread thread = new TestedThread(name);
            threads[i] = thread;
            vts[i] = start_thread(name, thread);
        }

        System.out.println("\n## Java: runIt: testing Suspend/Resume");
        TestSuspendResume();

        System.out.println("\n## Java: runIt: Finishing vthreads");
        try {
            for (int i = 0; i < VTHREADS_CNT; i++) {
                // let thread to finish
                TestedThread thread = threads[i];
                thread.letFinish();
                vts[i].join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return GetStatus();
    }

    Thread start_thread(String name, TestedThread thread) {
        Thread vthread =  Thread.ofVirtual().name(name).start(thread);
        thread.ensureReady(); // testing sync
        System.out.println("## Java: started thread: " + name);
        return vthread;
    }
}

// class for tested threads
class TestedThread extends Thread {
    private volatile boolean threadReady = false;
    private volatile boolean shouldFinish = false;

    // make thread with specific name
    public TestedThread(String name) {
        super(name);
    }

    // run thread continuously
    public void run() {
        // run in a loop
        threadReady = true;
        int i = 0;
        int n = 100;
        while (!shouldFinish) {
            if (n <= 0) {
                n = 100;
                SuspendResume2.sleep(50);
            }
            if (i > n) {
                i = 0;
                n = n - 1;
            }
            i = i + 1;
        }
    }

    // ensure thread is ready
    public void ensureReady() {
        try {
            while (!threadReady) {
                sleep(100);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interruption while preparing tested thread: \n\t" + e);
        }
    }

    // let thread to finish
    public void letFinish() {
        shouldFinish = true;
    }
}
