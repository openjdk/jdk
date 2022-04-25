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
 * @summary Test JVMTI Monitor functions for virtual threads
 * @compile --enable-preview -source ${jdk.version} VThreadMonitorTest.java
 * @run main/othervm/native --enable-preview -agentlib:VThreadMonitorTest VThreadMonitorTest
 */

import java.io.PrintStream;

class MonitorClass0 {}
class MonitorClass2 {}

public class VThreadMonitorTest {

    static {
        try {
            System.loadLibrary("VThreadMonitorTest");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load VThreadMonitorTest library");
            System.err.println("java.library.path: "
                               + System.getProperty("java.library.path"));
            throw ule;
        }
    }
    private static native boolean hasEventPosted();
    private static native void checkContendedMonitor(Thread thread, Object mon1, Object mon2);
    private static native int check();

    private static void log(String str) { System.out.println(str); }
    private static String thrName() { return Thread.currentThread().getName(); }

    private static final Object lock0 = new MonitorClass0();
    private static final Object lock1 = new Object();
    private static final Object lock2 = new MonitorClass2();

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
        }
    }

    static void m0() {
        synchronized (lock0) {
            // log(thrName() +" entered sync section with lock0\n");
        }
    }
    static void m1() {
        synchronized (lock1) {
            // log(thrName() +" entered sync section with lock1");
            m0();
        }
    }
    static void m2() {
        synchronized (lock2) {
            // log(thrName() +" entered sync section with lock2");
            m1();
        }
    }

    static final Runnable VT = () -> {
        m2();
    };

    static private int counter = 0;

    static final Runnable SLEEPING_VT = () -> {
        for (int i = 0; i < 40; i++) {
            for (int j = 0; j < 1000; j++) {
                counter += j;
                counter %= 100;
            }
            sleep(1);
        }
    };

    static final int VT_TOTAL = 10;
    static final int VT_COUNT = 2;

    public static void main(String[] args) throws Exception {
        Thread[] vthreads = new Thread[VT_TOTAL];
        Thread.Builder builder = Thread.ofVirtual().name("VirtualThread-", 0);

        // Create VT threads.
        for (int i = 0; i < VT_COUNT; i++) {
            vthreads[i] = builder.unstarted(VT);
        }
        // Create SLEEPING_VT threads.
        for (int i = VT_COUNT; i < VT_TOTAL; i++) {
            vthreads[i] = builder.unstarted(SLEEPING_VT);
        }

        // Make sure one of the VT threads is blocked on monitor lock0.
        synchronized (lock0) {
            log("Main starting VT virtual threads");
            for (int i = 0; i < VT_TOTAL; i++) {
                vthreads[i].start();
            }
            // Wait for the MonitorContendedEnter event.
            while (!hasEventPosted()) {
                log("Main thread is waiting for event\n");
                sleep(10);
            }
            // One of the VT threads is blocked at lock0, another - at lock2.
            for (int i = 0; i < VT_COUNT; i++) {
                checkContendedMonitor(vthreads[i], lock0, lock2);
            }
            // SLEEPING_VT threads can be contended on some system  monitors,
            // so we should not check they have no contention.
        }

        for (int i = 0; i < VT_TOTAL; i++) {
           vthreads[i].join();
        }

        if (check() != 0) {
            throw new RuntimeException("FAILED status returned from the agent");
        }
    }
}
