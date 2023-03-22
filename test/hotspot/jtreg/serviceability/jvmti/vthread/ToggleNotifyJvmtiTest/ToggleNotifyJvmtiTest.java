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

//import compiler.whitebox.CompilerWhiteBoxTest;
import com.sun.tools.attach.VirtualMachine;
import java.util.concurrent.ThreadFactory;
import jdk.test.whitebox.WhiteBox;

class TestedThread extends Thread {
    private volatile boolean threadReady = false;
    private volatile boolean shouldFinish = false;

    // make thread with specific name
    public TestedThread(String name) {
        super(name);
    }

    // We will temporarily set a breakpoint on this method when the thread should be suspended.
    // If we hit the breakpoint, then something is wrong.
    public void breakpointCheck() {
        return;
    }

    // run thread continuously
    public void run() {
        // run in a loop
        threadReady = true;
        int i = 0;
        int n = 1000;
        while (!shouldFinish) {
            breakpointCheck();
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
        try {
            while (!threadReady) {
                sleep(1);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interruption while preparing tested thread: \n\t" + e);
        }
    }

    public void letFinish() {
        shouldFinish = true;
    }
}

public class ToggleNotifyJvmtiTest {
    private static final int VTHREADS_CNT = 60;
    private static final String AGENT_LIB = "ToggleNotifyJvmtiTest";
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static native boolean IsAgentStarted();
    private static native int VirtualThreadStartedCount();

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
        log("## Java: started vthread: " + name);
    }

    static private void startThreads() {
        log("\n## Java: Starting vthreads");
        for (int i = 0; i < VTHREADS_CNT; i++) {
            startThread(i);
        }
    }

    static private synchronized void finishThreads() {
        log("\n## Java: runIt: Finishing vthreads");
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
        sleep(5);
        WB.setVirtualThreadsNotifyJvmtiMode(enable);
        log("# main: SetNotifyJvmtiEvents: #" + iter + " enable: " + enable);
    }

    public static void main(String[] args) throws Exception {
        log("# main: loading " + AGENT_LIB + " lib");

        if (args.length > 0 && args[0].equals("attach")) { // agent loaded into running VM case
            String arg = args.length == 2 ? args[1] : "";
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
            vm.loadAgentLibrary(AGENT_LIB, arg);
        } else {
            System.loadLibrary(AGENT_LIB);
        }
        int waitCount = 0;
        while (!IsAgentStarted()) {
            log("# main: waiting for native agent to start: #" + waitCount++);
            sleep(20);
        }
        Thread tt = Thread.ofPlatform().name("StartThreadsTest").start(ToggleNotifyJvmtiTest::startThreads);
        sleep(20);

        for (int iter = 0; VirtualThreadStartedCount() < VTHREADS_CNT; iter++) {
            setVirtualThreadsNotifyJvmtiMode(iter, false); // disable
            setVirtualThreadsNotifyJvmtiMode(iter, true);  // enable
        }
        finishThreads();
        tt.join();
    }
}
