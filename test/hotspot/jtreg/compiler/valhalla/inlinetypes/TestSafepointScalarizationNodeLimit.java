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

/**
 * @test
 * @summary Test that scalarizing value objects in safepoint debug info respects the C2 node limit.
 * @bug 8388361
 * @enablePreview
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run main/othervm -Xcomp -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation
 *                   -XX:+DeoptimizeALot
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   -XX:CompileCommand=dontinline,${test.main.class}::blackhole
 *                   -XX:CompileCommand=inline,${test.main.class}::safepoints
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

public class TestSafepointScalarizationNodeLimit {

    // Not inlined
    static void blackhole() { }

    // 30 safepoints
    static void safepoints() {
        blackhole(); blackhole(); blackhole(); blackhole(); blackhole();
        blackhole(); blackhole(); blackhole(); blackhole(); blackhole();
        blackhole(); blackhole(); blackhole(); blackhole(); blackhole();
        blackhole(); blackhole(); blackhole(); blackhole(); blackhole();
        blackhole(); blackhole(); blackhole(); blackhole(); blackhole();
        blackhole(); blackhole(); blackhole(); blackhole(); blackhole();
    }

    static int test1(int x) {
        // DeoptimizeALot keeps all locals live. Each scalar value is therefore present
        // in the debug info at every safepoint below.
        Integer i0 = x, i1 = x + 1, i2 = x + 2, i3 = x + 3, i4 = x + 4,
            i5 = x + 5, i6 = x + 6, i7 = x + 7, i8 = x + 8, i9 = x + 9,
            i10 = x + 10, i11 = x + 11, i12 = x + 12, i13 = x + 13, i14 = x + 14,
            i15 = x + 15, i16 = x + 16, i17 = x + 17, i18 = x + 18, i19 = x + 19,
            i20 = x + 20, i21 = x + 21, i22 = x + 22, i23 = x + 23, i24 = x + 24,
            i25 = x + 25, i26 = x + 26, i27 = x + 27, i28 = x + 28, i29 = x + 29,
            i30 = x + 30, i31 = x + 31, i32 = x + 32, i33 = x + 33, i34 = x + 34,
            i35 = x + 35, i36 = x + 36, i37 = x + 37, i38 = x + 38, i39 = x + 39,
            i40 = x + 40, i41 = x + 41, i42 = x + 42, i43 = x + 43, i44 = x + 44,
            i45 = x + 45, i46 = x + 46, i47 = x + 47, i48 = x + 48, i49 = x + 49,
            i50 = x + 50, i51 = x + 51, i52 = x + 52, i53 = x + 53, i54 = x + 54,
            i55 = x + 55, i56 = x + 56, i57 = x + 57, i58 = x + 58, i59 = x + 59,
            i60 = x + 60, i61 = x + 61, i62 = x + 62, i63 = x + 63, i64 = x + 64,
            i65 = x + 65, i66 = x + 66, i67 = x + 67, i68 = x + 68, i69 = x + 69,
            i70 = x + 70, i71 = x + 71, i72 = x + 72, i73 = x + 73, i74 = x + 74,
            i75 = x + 75, i76 = x + 76, i77 = x + 77, i78 = x + 78, i79 = x + 79,
            i80 = x + 80, i81 = x + 81, i82 = x + 82, i83 = x + 83, i84 = x + 84,
            i85 = x + 85, i86 = x + 86, i87 = x + 87, i88 = x + 88, i89 = x + 89,
            i90 = x + 90, i91 = x + 91, i92 = x + 92, i93 = x + 93, i94 = x + 94,
            i95 = x + 95, i96 = x + 96, i97 = x + 97, i98 = x + 98, i99 = x + 99;

        // 30 x 30 = 900 safepoints, each one with 100 live Integers
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        return i99;
    }

    // Same as test2 but with fewer safepoints - triggered a different assert
    static int test2(int x) {
        // DeoptimizeALot keeps all locals live. Each scalar value is therefore present
        // in the debug info at every safepoint below.
        Integer i0 = x, i1 = x + 1, i2 = x + 2, i3 = x + 3, i4 = x + 4,
            i5 = x + 5, i6 = x + 6, i7 = x + 7, i8 = x + 8, i9 = x + 9,
            i10 = x + 10, i11 = x + 11, i12 = x + 12, i13 = x + 13, i14 = x + 14,
            i15 = x + 15, i16 = x + 16, i17 = x + 17, i18 = x + 18, i19 = x + 19,
            i20 = x + 20, i21 = x + 21, i22 = x + 22, i23 = x + 23, i24 = x + 24,
            i25 = x + 25, i26 = x + 26, i27 = x + 27, i28 = x + 28, i29 = x + 29,
            i30 = x + 30, i31 = x + 31, i32 = x + 32, i33 = x + 33, i34 = x + 34,
            i35 = x + 35, i36 = x + 36, i37 = x + 37, i38 = x + 38, i39 = x + 39,
            i40 = x + 40, i41 = x + 41, i42 = x + 42, i43 = x + 43, i44 = x + 44,
            i45 = x + 45, i46 = x + 46, i47 = x + 47, i48 = x + 48, i49 = x + 49,
            i50 = x + 50, i51 = x + 51, i52 = x + 52, i53 = x + 53, i54 = x + 54,
            i55 = x + 55, i56 = x + 56, i57 = x + 57, i58 = x + 58, i59 = x + 59,
            i60 = x + 60, i61 = x + 61, i62 = x + 62, i63 = x + 63, i64 = x + 64,
            i65 = x + 65, i66 = x + 66, i67 = x + 67, i68 = x + 68, i69 = x + 69,
            i70 = x + 70, i71 = x + 71, i72 = x + 72, i73 = x + 73, i74 = x + 74,
            i75 = x + 75, i76 = x + 76, i77 = x + 77, i78 = x + 78, i79 = x + 79,
            i80 = x + 80, i81 = x + 81, i82 = x + 82, i83 = x + 83, i84 = x + 84,
            i85 = x + 85, i86 = x + 86, i87 = x + 87, i88 = x + 88, i89 = x + 89,
            i90 = x + 90, i91 = x + 91, i92 = x + 92, i93 = x + 93, i94 = x + 94,
            i95 = x + 95, i96 = x + 96, i97 = x + 97, i98 = x + 98, i99 = x + 99;

        // 20 x 30 = 600 safepoints, each one with 100 live Integers
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        safepoints(); safepoints(); safepoints(); safepoints(); safepoints();
        return i99;
    }

    public static void main(String[] args) {
        Asserts.assertEquals(test1(42), 141);
        Asserts.assertEquals(test2(42), 141);
    }
}
