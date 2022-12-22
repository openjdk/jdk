/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies setting a breakpoint in Continuation.yield() followed by single stepping
 * @requires vm.continuations
 * @compile ContYieldBreakPointTest.java
 * @modules java.base/jdk.internal.vm
 * @run main/othervm/native --enable-preview -agentlib:ContYieldBreakPointTest ContYieldBreakPointTest
 */

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

public class ContYieldBreakPointTest {
    private static final String agentLib = "ContYieldBreakPointTest";
    private static ContinuationScope scope = new ContinuationScope("YieldTest") {};
    private static Continuation cont;
    private static boolean done = false;

    static void log(String str) { System.out.println(str); }

    static native void enableEvents(Thread thread, Class breakpointClass);
    static native boolean check();

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            log("Failed to load " + agentLib + " lib");
            log("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }
        log("\n######   main: started   #####\n");

        ContYieldBreakPointTest obj = new ContYieldBreakPointTest();
        obj.runTest();

        log("ContYieldBreakPointTest passed\n");
        log("\n#####   main: finished  #####\n");
    }

    public void runTest() {
        log("\n####  runTest: started  ####\n");
        yieldTest();
        log("\n####  runTest: finished ####\n");
    }

    static final Runnable YEILD = () -> {
        while (true) {
            if (done) return;
            Continuation.yield(scope);
        }
    };

    public static void yieldTest() {
        cont = new Continuation(scope, YEILD);
        log("\n####  yieldTest: started  ####\n");

        // We first need to warmup before reproducing the assert issue that this test uncovered.
        for (int i = 0; i < 500; i++) {
            cont.run();
        }
        log("\n####  yieldTest: done warming up ####\n");

        // Enable the breakpoint on Continuation.yield0(). Once hit, single stepping will be enabled.
        enableEvents(Thread.currentThread(), jdk.internal.vm.Continuation.class);

        cont.run();
        cont.run();
        done = true;
        cont.run();

        try {
            cont.run();
        } catch (IllegalStateException e) {
        }

        check();
        log("\n####  yieldTest: finished ####\n");
    }
}
