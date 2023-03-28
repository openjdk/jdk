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

/**
 * @test
 * @summary Verifies JVMTI works for agents loaded into running VM
 * @requires vm.jvmti
 * @requires vm.continuations
 * @enablePreview
 * @library /test/lib /test/hotspot/jtreg
 * @build jdk.test.whitebox.WhiteBox
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -XX:+WhiteBoxAPI -Xbootclasspath/a:. -agentlib:ToggleNotifyJvmtiTest ToggleNotifyJvmtiTest
 * @run main/othervm/native -XX:+WhiteBoxAPI -Xbootclasspath/a:. -Djdk.attach.allowAttachSelf=true ToggleNotifyJvmtiTest attach
 */

import com.sun.tools.attach.VirtualMachine;
import java.util.concurrent.ThreadFactory;
import jdk.test.whitebox.WhiteBox;

// The TestedThread just mimics some thread activity, but it is important
// to have sleep() calls to provide yielding as some frequency of virtual
// thread mount state transitions is needed for this test scenario.
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
        int n = 1000;
        while (!shouldFinish) {
            if (n <= 0) {
                n = 1000;
                ToggleNotifyJvmtiTest.sleep(1);
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
        while (!threadReady) {
            ToggleNotifyJvmtiTest.sleep(1);
        }
    }

    public void letFinish() {
        shouldFinish = true;
    }
}

/*
 * The testing scenario consists of a number of serialized test cycles.
 * Each cycle has initially zero virtual threads and has the following steps:
 *  - disable notifyJvmti events mode
 *  - start the platform launcher thread which starts N of virtual thread
 *  - enable notifyJvmti events mode after about hapf of virtual thread started
 *  - then shut the virtual threads down
 * The JVMTI agent is loaded at a start-up or at a dynamic attach.
 * It collects events:
 *  - VirtualThreadStart, VirtualThreadEnd, ThreadStart and ThreadEnd
 */
public class ToggleNotifyJvmtiTest {
    private static final int VTHREADS_CNT = 20;
    private static final String AGENT_LIB = "ToggleNotifyJvmtiTest";
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static native boolean IsAgentStarted();
    private static native int VirtualThreadStartedCount();
    private static native int VirtualThreadEndedCount();
    private static native int ThreadStartedCount();
    private static native int ThreadEndedCount();

    static void log(String str) { System.out.println(str); }

    static public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interruption in TestedThread.sleep: \n\t" + e);
        }
    }

    static TestedThread[] threads = new TestedThread[VTHREADS_CNT];
    static Thread vts[] = new Thread[VTHREADS_CNT];

    static private synchronized void startThread(int i) {
        String name = "TestedThread" + i;
        TestedThread thread = new TestedThread(name);
        vts[i] = Thread.ofVirtual().name(name).start(thread);
        thread.ensureReady();
        threads[i] = thread;
        log("# Java: started vthread: " + name);
    }

    static private void startThreads() {
        log("\n# Java: Starting vthreads");
        for (int i = 0; i < VTHREADS_CNT; i++) {
            sleep(1);
            startThread(i);
        }
    }

    static private synchronized void finishThreads() {
        try {
            for (int i = 0; i < VTHREADS_CNT; i++) {
                TestedThread thread = threads[i];
                if (thread == null) {
                    break;
                }
                thread.letFinish();
                vts[i].join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static private void setVirtualThreadsNotifyJvmtiMode(int iter, boolean enable) {
        boolean status = WB.setVirtualThreadsNotifyJvmtiMode(enable);
        if (!status) {
            throw new RuntimeException("Java: failed to set VirtualThreadsNotifyJvmtiMode: " + enable);
        }
        log("# main: SetNotifyJvmtiEvents: #" + iter + " enable: " + enable);
    }

    // Accumulative results after each finished test cycle.
    static private void printResults() {
        log("  VirtualThreadStart events: " + VirtualThreadStartedCount());
        log("  VirtualThreadEnd events:   " + VirtualThreadEndedCount());
        log("  ThreadStart events:        " + ThreadStartedCount());
        log("  ThreadEnd events:          " + ThreadEndedCount());
    }

    static private void run_test_cycle(int iter) throws Exception {
        log("\n# Java: Started test cycle #" + iter);

        // Disable notifyJvmti events mode at test cycle start.
        // It is unsafe to do so if any virtual threads are executed.
        setVirtualThreadsNotifyJvmtiMode(iter, false);

        Thread tt = Thread.ofPlatform().name("StartThreadsTest").start(ToggleNotifyJvmtiTest::startThreads);
        sleep(20); // give some time for launcher thread to start

        // We want this somewhere in the middle of virtual threads execution.
        setVirtualThreadsNotifyJvmtiMode(iter, true);
        sleep(20); // give some time for virtual threads to execute

        finishThreads();
        tt.join();
        sleep(20); // give some time for virtual threads to finish

        log("\n# Java: Finished test cycle #" + iter);
        printResults();
    }
 
    public static void main(String[] args) throws Exception {
        log("# main: loading " + AGENT_LIB + " lib");

        if (args.length > 0 && args[0].equals("attach")) { // agent loaded into running VM case
            String arg = args.length == 2 ? args[1] : "";
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
            vm.loadAgentLibrary(AGENT_LIB, arg);
        }
        int waitCount = 0;
        while (!IsAgentStarted()) {
            log("# main: waiting for native agent to start: #" + waitCount++);
            sleep(20);
        }

        // The testing scenario consists of a number of sequential testing cycles.
        for (int iter = 0; iter < 10; iter++) {
            run_test_cycle(iter);
        }
    }
}
