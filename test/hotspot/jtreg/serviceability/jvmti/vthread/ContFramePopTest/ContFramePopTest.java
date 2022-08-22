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
 * @requires vm.continuations
 * @compile ContFramePopTest.java
 * @modules java.base/jdk.internal.vm
 * @run main/othervm/native --enable-preview -agentlib:ContFramePopTest ContFramePopTest
 */

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

public class ContFramePopTest {
    private static final String agentLib = "ContFramePopTest";
    private static final ContinuationScope FOO = new ContinuationScope() {};

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

        ContFramePopTest obj = new ContFramePopTest();
        obj.runTest();

        if (!check()) {
            throw new RuntimeException("ContFramePopTest failed: miscounted FramePop or MethodExit events!");
        }
        log("ContFramePopTest passed\n");
        log("\n#####   main: finished  #####\n");
    }

    public void runTest() {
        log("\n####  runTest: started  ####\n");
        Continuation cont = new Continuation(FOO, ()-> {
            double dval = 0;

            log("\n##    cont: started     ##\n");
            for (int k = 1; k < 3; k++) {
                int ival = 3;
                String str = "abc";

                log("\n cont: iteration #" + (k - 1));

                log("\n<<<< runTest: before foo(): " + ival + ", " + str + ", " + dval + " <<<<");
                dval += foo(k);
            log(  ">>>> runTest:  after foo(): " + ival + ", " + str + ", " + dval + " >>>>");
            }
            log("\n##    cont: finished    ##\n");
        });
        int i = 0;
        while (!cont.isDone()) {
            log("\n##   runTest: iteration #" + (i++));
            cont.run();
            System.gc();
        }
        log("\n####  runTest: finished ####\n");
    }

    static double foo(int iarg) {
        long lval = 8;
        String str1 = "yyy";

        log("\n####   foo: started  ####\n");
        log("foo: before bar(): " + lval + ", " + str1 + ", " + iarg);
        String str2 = bar(iarg + 1);
    log("foo:  after bar(): " + lval + ", " + str1 + ", " + str2);

        log("\n####   foo: finished ####\n");
        return Integer.parseInt(str2) + 1;
    }

    static String bar(long larg) {
        double dval = 9.99;
        String str = zzz();

        log("\n####   bar: started  ####\n");
        log("bar: before yield(): " + dval + ", " + str + ", " + larg);
        Continuation.yield(FOO);

        long lval = larg + 1;
        log("bar:  after yield(): " + dval + ", " + str + ", " + lval);

        str = zzz();

        log("\n####   bar: finished: str = " + str + " ####\n");
        return "" + lval;
    }

    static String zzz() { return "zzz"; }
}
