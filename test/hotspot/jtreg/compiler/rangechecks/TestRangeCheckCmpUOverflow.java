/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress randomness
 * @requires vm.compiler2.enabled
 * @bug 8286638
 * @summary Dominator failure because CastII node becomes TOP while skeleton predicate cannot be folded
 *          due to insufficient overflow/underflow handling in CmpUNode::Value.

 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbatch -XX:-UseLoopPredicate -XX:+StressIGVN -XX:StressSeed=680585904
 *                   -XX:CompileCommand=compileonly,compiler.rangechecks.TestRangeCheckCmpUOverflow::test
 *                   -XX:CompileCommand=inline,compiler.rangechecks.TestRangeCheckCmpUOverflow::inlined
 *                   -XX:-DoEscapeAnalysis compiler.rangechecks.TestRangeCheckCmpUOverflow
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbatch -XX:-UseLoopPredicate -XX:+StressIGVN
 *                   -XX:CompileCommand=compileonly,compiler.rangechecks.TestRangeCheckCmpUOverflow::test
 *                   -XX:CompileCommand=inline,compiler.rangechecks.TestRangeCheckCmpUOverflow::inlined
 *                   -XX:-DoEscapeAnalysis compiler.rangechecks.TestRangeCheckCmpUOverflow
 */

package compiler.rangechecks;


public class TestRangeCheckCmpUOverflow {
    static volatile int barrier;

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            run();
        }
    }

    static void run() {
        double[] array = new double[1000];
        inlined(1000, array);
        test(11);
    }

    static void inlined(int stop, double[] array1) {
        for (int i = 8; i < stop; i++) {
            if ((i % 2) == 0) {
                array1[i] = 42.42;
            } else {
                barrier = 0x42;
            }
        }
    }

    static void test(int stop) {
        double[] array1 = new double[10];
        for (int j = 0; j < stop; j++) {
            inlined(j, array1);
        }
    }
}
