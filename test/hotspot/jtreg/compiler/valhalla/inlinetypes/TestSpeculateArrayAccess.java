/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress randomness
 * @bug 8333889
 * @summary Test that speculative array access checks do not cause a load to be wrongly hoisted before its range check.
 * @run main/othervm -XX:CompileCommand=dontinline,*::* -XX:CompileCommand=compileonly,*TestSpeculateArrayAccess::test
 *                   -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=1202682944
 *                   compiler.valhalla.inlinetypes.TestSpeculateArrayAccess
 * @run main/othervm -XX:CompileCommand=dontinline,*::* -XX:CompileCommand=compileonly,*TestSpeculateArrayAccess::test
 *                   -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM
 *                   compiler.valhalla.inlinetypes.TestSpeculateArrayAccess
 */

package compiler.valhalla.inlinetypes;

public class TestSpeculateArrayAccess {
    static Object[] oArr = new Object[100];
    static int iFld = 100;

    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            oArr[i] = new Object();
        }
        iFld = 1;
        for (int i = 0; i < 1000; i++) {
            test();
        }
        iFld = -1;

        try {
            test();
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected.
        }
    }

    static void test() {
        Object[] oA = oArr; // Load here to avoid G1 barriers being expanded inside loop which prevents Loop Predication.
        for (float i = 0; i < 100; i++) {
            // RangeCheck -> If-Speculative-Array-Type -> CastII
            //
            // At Loop Predication:
            // - RangeCheck not hoisted because loop dependent
            // - If-Speculative-Array-Type hoisted and CastII and LoadN ends up before loop
            //
            // Running with -XX:+StressGCM: We could execute the LoadN before entering the loop.
            // This crashes when iFld = -1 because we then access an out-of-bounds element.
            Object o = oA[(int)i*iFld];
            o.toString(); // Use the object with its speculated type.
        }
    }
}

