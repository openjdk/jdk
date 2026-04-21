/*
 * Copyright (c) 2025, Red Hat, Inc.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8371716
 * @summary Ranges can be proven to be disjoint but not orderable (thanks to unsigned range)
 *          Comparing such values in such range with != should always be true.
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:-TieredCompilation
 *                   -XX:-UseOnStackReplacement
 *                   -XX:-BackgroundCompilation
 *                   -XX:CompileOnly=${test.main.class}::test1
 *                   -XX:CompileCommand=quiet
 *                   -XX:TypeProfileLevel=222
 *                   -XX:+AlwaysIncrementalInline
 *                   -XX:VerifyIterativeGVN=10
 *                   -XX:CompileCommand=dontinline,${test.main.class}::notInlined1
 *                   ${test.main.class}
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation
 *                   -XX:-UseOnStackReplacement
 *                   -XX:-BackgroundCompilation
 *                   -XX:CompileOnly=${test.main.class}::test2
 *                   -XX:CompileOnly=${test.main.class}::inlined3
 *                   -XX:CompileCommand=quiet
 *                   -XX:TypeProfileLevel=200
 *                   -XX:+AlwaysIncrementalInline
 *                   -XX:VerifyIterativeGVN=10
 *                   -XX:CompileCommand=dontinline,${test.main.class}::notInlined1
 *                   -XX:+StressIncrementalInlining
 *                   ${test.main.class}
 *
 * @run main ${test.main.class}
 */

package compiler.igvn;

public class ClashingSpeculativeTypePhiNode {
    public static void main(String[] args) {
        main1();
        main2();
    }

    // 1st case

    static void main1() {
        for (int i = 0; i < 20_000; i++) {
            test1(false);         // returns null
            inlined1(true, true); // returns C1
            inlined2(false);      // returns C2
        }
    }

    private static Object test1(boolean flag1) {
        return inlined1(flag1, false);
        // When inlined1 is inlined
        // return Phi(flag1, inlined2(flag2), null)
        // inlined2 is speculatively returning C1, known from the calls `inlined1(true, true)` in main1
        // Phi node gets speculative type C1
        // When inline2 is inlined
        // return Phi[C1](flag1, Phi(false, new C1(), notInlined1()), null)
        // => Phi[C1](flag1, notInlined1(), null)
        // notInlined1 is speculatively returning C2, known from `inline2(false)` in main1
        // return Phi[C1](flag1, notInlined1()[C2], null)
        // Clashing speculative type between Phi's _type (C1) and union of inputs (C2).
    }

    private static Object inlined1(boolean flag1, boolean flag2) {
        if (flag1) {
            return inlined2(flag2); // C1
        }
        return null;
    }

    private static Object inlined2(boolean flag2) {
        if (flag2) {
            return new C1();
        }
        return notInlined1(); // C2
    }

    private static Object notInlined1() {
        return new C2();
    }

    // 2nd case

    static void main2() {
        for (int i = 0; i < 20_000; i++) {
            inlined3(new C1());
        }
        for (int i = 0; i < 20_000; i++) {
            test2(true, new C2());
            test2(false, new C2());
        }
    }


    private static Object test2(boolean flag1, Object o) {
        o = inlined4(o);
        if (flag1) {
            return inlined3(o);
        }
        return null;
        // We profile only parameters. Param o is speculated to be C2.
        // return Phi(flag1, inline3(inline4(o[C2])), null)
        // We inline inline3
        // return Phi(flag1, inline4(o[C2])[C1], null)
        // As input of inline3, inline4(o) is speculated to be C1. The Phi has C1 as speculative type in _type
        // return Phi[C1](flag1, o[C2], null)
        // Since o is speculated to be C2 as parameter of test2, we get a clash.
    }

    private static Object inlined3(Object o) {
        return o; // C1
    }

    private static Object inlined4(Object o) {
        return o;
    }

    static class C1 {

    }

    static class C2 {

    }
}
