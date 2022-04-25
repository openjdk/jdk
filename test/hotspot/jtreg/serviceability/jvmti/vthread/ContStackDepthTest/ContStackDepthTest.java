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
 * @summary Verifies JVMTI support for Continuations
 * @compile ContStackDepthTest.java
 * @modules java.base/jdk.internal.vm
 * @run main/othervm/native --enable-preview -agentlib:ContStackDepthTest ContStackDepthTest
 */

import java.math.BigInteger;
import java.math.BigInteger.*;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

public class ContStackDepthTest {
    private static final String agentLib = "ContStackDepthTest";
    private static ContinuationScope scope = new ContinuationScope("Fibonacci") {};
    private static Continuation cont;
    private static BigInteger value = BigInteger.ONE;
    private static boolean done = false;

    static void log(String str) { System.out.println(str); }

    static native void enableEvents(Thread thread);
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
        enableEvents(Thread.currentThread());

        ContStackDepthTest obj = new ContStackDepthTest();
        obj.runTest();

        if (!check()) {
            throw new RuntimeException(
                "ContStackDepthTest failed: miscounted FramePop, MethodEnter or MethodExit events!");
        }
        log("ContStackDepthTest passed\n");
        log("\n#####   main: finished  #####\n");
    }

    public void runTest() {
        log("\n####  runTest: started  ####\n");
        fibTest();
        log("\n####  runTest: finished ####\n");
    }

    static final Runnable FIB = () -> {
        Continuation.yield(scope);
        var cur = value;
        var next = BigInteger.ONE;
        int iter = 0;

        while (true) {
            log("\n  ##  FIB iteration: " + (++iter) + "  ##\n");
            if (done) return;

            value = next;
            Continuation.yield(scope);
            var tmp = cur.add(next);
            cur = next;
            next = tmp;
        }
    };

    public static void fibTest() {
        cont = new Continuation(scope, FIB);
        log("\n####  fibTest: started  ####\n");

        System.out.println("getNextFib returned value: " + getNextFib());
        System.out.println("getNextFib returned value: " + getNextFib());
        System.out.println("getNextFib returned value: " + getNextFib());

        done = true;

        System.out.println("getNextFib returned value: " + getNextFib());

        log("\n####  fibTest: finished ####\n");
    }

    public static BigInteger getNextFib() {
        cont.run();
        return value;
    }
}
