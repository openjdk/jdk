/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies specific JVMTI functions work with current virtual thread passed as NULL.
 * @compile --enable-preview -source ${jdk.version} NullAsCurrentThreadTest.java
 * @run main/othervm/native --enable-preview -agentlib:NullAsCurrentThreadTest=EnableVirtualThreadSupport NullAsCurrentThreadTest
 */

public class NullAsCurrentThreadTest {
    private static final String AGENT_LIB = "NullAsCurrentThreadTest";
    private static void log (String msg) { System.out.println(msg); }
    private static Object lock = new Object();

    native static boolean failedStatus();
    native static void testJvmtiFunctions();

    static int factorial(int num) {
        int fact = 1;
        if (num > 1) {
            fact = num * factorial(num - 1);
        } else {
            testJvmtiFunctions();
        }
        return fact;
    }

    final Runnable pinnedTask = () -> {
        synchronized (lock) {
            int fact = factorial(10);
            log("Java App: Factorial(10) = " + fact);
        }
    };

    void runTest() throws Exception {
        Thread vthread = Thread.ofVirtual().name("TestedVThread").start(pinnedTask);
        vthread.join();
    }

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(AGENT_LIB);
        } catch (UnsatisfiedLinkError ex) {
            log("Java App: Failed to load " + AGENT_LIB + " lib");
            log("Java App: java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }
        NullAsCurrentThreadTest tst = new NullAsCurrentThreadTest();
        tst.runTest();
        boolean failed = failedStatus();
        if (failed) {
            throw new RuntimeException("NullAsCurrentThreadTest FAILED: failed status from native agent");
        }
    }
}
