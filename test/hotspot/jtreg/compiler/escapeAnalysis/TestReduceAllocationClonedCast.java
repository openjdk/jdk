/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8385993
 * @summary C2: Incorrect escape analysis in reduce allocation merges causes NPE
 * @library /test/lib
 * @run main/othervm -Xbatch -XX:+IgnoreUnrecognizedVMOptions -XX:-UseDeepIGVNRevisit -XX:CompileCommand=compileonly::${test.main.class},* ${test.main.class}
 * @run main ${test.main.class} ${test.main.class} ${test.file}
 */

package compiler.escapeAnalysis;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.IOException;

public class TestReduceAllocationClonedCast {
    public static void main(String[] args) throws IOException {
        // For some reason, test failure is only observed when running:
        // java TestReduceAllocationClonedCast.java
        // with some extra command flags rather than building and then running:
        // java TestReduceAllocationClonedCast
        if (args.length == 0) {
            for (int i = 0; i < 8_000; i++) {
                test1();
                test2();
            }
            System.out.println("DONE");
        } else {
            String mainClass = args[0];
            String mainSrc = args[1];
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-Xbatch", "-XX:+IgnoreUnrecognizedVMOptions",
                                                                                 "-XX:-UseDeepIGVNRevisit", "-XX:CompileCommand=compileonly," + mainClass + "::*",
                                                                                 mainSrc);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
        }
    }

    static int test1() {
        int val = 0;
        A1 a = getA1(Integer.valueOf(14));
        if (a != null) {
            val += a.getA1();
        }
        return val;
    }

    static int test2() {
        int val = 0;
        A2 a = getA2(Integer.valueOf(14));
        if (a != null) {
            val += a.getA1();
        }
        return val;
    }

    static A1 getA1(Object obj) {
        java.util.Random rnd = new java.util.Random();
        int rndInt= rnd.nextInt(100);
        if (rndInt < 15) {
            return null;
        }

        A1 retA = new A1(rndInt);
        if (obj != null) {
            B1 b = new B1(retA);
            retA = b.getAFromB(obj);
        }

        return retA;
    }

    static A2 getA2(Object obj) {
        java.util.Random rnd = new java.util.Random();
        int rndInt= rnd.nextInt(100);
        if (rndInt < 15) {
            return null;
        }

        A2 retA = new A2(rndInt, rnd.nextInt(100), rnd.nextInt(100));
        if (obj != null) {
            B2 b = new B2(retA);
            retA = b.getAFromB(obj);
        }

        return retA;
    }

    static class A1 {
        final Integer a1;

        A1(int a1) {
            this.a1 = a1;
        }

        int getA1() {
            return a1.intValue();
        }

    }

    static class A2 {
        final Integer a1;
        final int a2;
        final long a3;

        A2(int a1, int a2, long a3) {
            this.a1 = a1;
            this.a2 = a2;
            this.a3 = a3;
        }

        int getA1() {
            return a1.intValue();
        }

    }

    static class B1 {
        final A1 a;
        B1(A1 a) {
            this.a = a;
        }

        A1 getAFromB(Object obj) {
            return a;
        }
    }

    static class B2 {
        final A2 a;
        B2(A2 a) {
            this.a = a;
        }

        A2 getAFromB(Object obj) {
            return a;
        }
    }
}

