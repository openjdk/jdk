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

package compiler.regalloc;

/**
 * @test
 * @bug 8338094
 * @summary Test C1's computation of local live ranges across exception jumps.
 * @run main/othervm -Xbatch
 *                   -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

class TestExceptionBranchWithLiveRangeHole {

    // Test that liveness information is computed correctly by C1 for intervals
    // that are live at an exception throwing operation solely because they are
    // used within the exception handler block.
    static void testThrowIOBE() {
        int i = 0;
        int[] array = new int[1];
        try {
            for (;;) {
                i = i << 32;
                // The pre-shift value of i should be live here, because it is
                // used below in the exception handler code, after the
                // canonicalization (i << 32) >>> 32 => i is applied.
                array[1] = 0;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            array[i >>> 32] = 42;
        }
    }

    // Variant of the above test using a different type of exception.
    // Illustrates the need of extending the live range of i beyond the
    // o.toString() call to model the interference of i with the killed
    // caller-saved registers.
    static void testThrowNPE(Object o) {
        int i = 0;
        int[] array = new int[1];
        try {
            for (;;) {
                i = i << 32;
                o.toString();
            }
        } catch (NullPointerException e) {
            array[i >>> 32] = 42;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            testThrowIOBE();
        }
        for (int i = 0; i < 10_000; i++) {
            testThrowNPE(null);
        }
    }
}
