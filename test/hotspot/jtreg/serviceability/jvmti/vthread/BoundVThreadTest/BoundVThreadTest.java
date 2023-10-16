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
 * @summary Verifies correct JVMTI behavior for BoundVirtualThreads.
 * @run main/othervm/native -agentlib:BoundVThreadTest -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations BoundVThreadTest
 */

import java.util.concurrent.atomic.AtomicBoolean;

public class BoundVThreadTest {
    private static final String AGENT_LIB = "BoundVThreadTest";
    final Object lock = new Object();
    final AtomicBoolean shouldFinish = new AtomicBoolean(false);

    static native boolean testJvmtiFunctions(Thread vthread, ThreadGroup group);
    static native boolean check();

    final Runnable pinnedTask = () -> {
        synchronized (lock) {
            do {
                try {
                    lock.wait(10);
                } catch (InterruptedException ie) {}
            } while (!shouldFinish.get());
        }
        testJvmtiFunctions(Thread.currentThread(), Thread.currentThread().getThreadGroup());
    };

    void runTest() throws Exception {
        Thread vthread = Thread.ofVirtual().name("VThread").start(pinnedTask);
        testJvmtiFunctions(vthread, Thread.currentThread().getThreadGroup());
        shouldFinish.set(true);
        vthread.join();
        if (!check()) {
            throw new RuntimeException("BoundVThreadTest failed!");
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(AGENT_LIB);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + AGENT_LIB + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }
        BoundVThreadTest t = new BoundVThreadTest();
        t.runTest();
    }
}
