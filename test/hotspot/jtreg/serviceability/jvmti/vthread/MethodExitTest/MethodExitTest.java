/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies that MethodExit events are delivered on both carrier and virtual threads.
 * @requires vm.continuations
 * @compile --enable-preview -source ${jdk.version} MethodExitTest.java
 * @run main/othervm/native --enable-preview -agentlib:MethodExitTest -Djdk.defaultScheduler.parallelism=2 MethodExitTest 150
 */

import java.util.concurrent.*;

public class MethodExitTest {
    private static final String agentLib = "MethodExitTest";

    static native void enableEvents(Thread thread, Class testClass);
    static native boolean check();

    static int brkptCount = 0;
    static int MSG_COUNT; // Passed as an argument
    static final SynchronousQueue<String> QUEUE = new SynchronousQueue<>();

    // method to set a breakpoint
    static int brkpt() {
        return brkptCount++;
    }

    static void qPut(String msg) throws InterruptedException {
        QUEUE.put(msg);
    }

    static final Runnable PRODUCER = () -> {
        try {
            for (int i = 0; i < MSG_COUNT; i++) {
                qPut("msg"+i);
                qPut("msg"+i);
                if (i == MSG_COUNT - 10) {
                    // Once we have warmed up, enable the first breakpoint which eventually will
                    // lead to enabling single stepping.
                    enableEvents(Thread.currentThread(), MethodExitTest.class);
                }
                brkpt();
            }
        } catch (InterruptedException e) { }
    };

    static final Runnable CONSUMER = () -> {
        try {
            for (int i = 0; i < MSG_COUNT; i++) {
                String s = QUEUE.take();
            }
        } catch (InterruptedException e) { }
    };

    public static void test1() throws Exception {
        Thread p1 = Thread.ofVirtual().name("VT-PRODUCER#0").start(PRODUCER);
        Thread c1 = Thread.ofVirtual().name("VT-CONSUMER#1").start(CONSUMER);
        Thread c2 = Thread.ofVirtual().name("VT-CONSUMER#2").start(CONSUMER);
        p1.join();
        c1.join();
        c2.join();
    }

    void runTest() throws Exception {
        test1();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Invalid # of arguments.");
        } else {
            MSG_COUNT = Integer.valueOf(args[0]);
        }

        try {
            System.out.println("loading " + agentLib + " lib");
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + agentLib + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }

        MethodExitTest obj = new MethodExitTest();
        obj.runTest();
        if (!check()) {
            throw new RuntimeException("MethodExitTest failed!");
        }
        System.out.println("MethodExitTest passed\n");
        System.out.println("\n#####   main: finished  #####\n");
    }
}
