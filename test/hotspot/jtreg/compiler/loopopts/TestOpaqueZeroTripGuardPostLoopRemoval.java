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
 * @bug 8298176
 * @summary Must remove OpaqueZeroTripGuardPostLoop after main loop disappears else
 *          the zero-trip-guard of the post loop cannot die and leaves an inconsistent
 *          graph behind.
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,TestOpaqueZeroTripGuardPostLoopRemoval::test*
 *      -XX:CompileCommand=dontinline,TestOpaqueZeroTripGuardPostLoopRemoval::*
 *      TestOpaqueZeroTripGuardPostLoopRemoval
 */

public class TestOpaqueZeroTripGuardPostLoopRemoval {
    static long x;

    public static void main(String[] strArr) {
        test_001();
        test_002();
        try {
            test_003();
        } catch (Exception e) {
            // Expected
        }
        test_004();
        test_005();
    }

    static void test_001() {
        int b = 6;
        for (long l = 1; l < 9; l++) {
            b++;
        }
        for (int i = 1; i < 1000; i*=2) {
            for (int j = 1; j < 2; j++) {
                x = b + 1;
            }
        }
    }

    static void test_002() {
        int b = 6;
        for (long l = 60; l < 3000; l+=3) {
            // bounds of loop: no work for post loop
            b += 33; // any multiple of iv step
        }
        for (int i = 1; i < 1000; i*=2) {
            for (int j = 1; j < 2; j++) {
                x = b + 1;
            }
        }
    }

    static void dontInline() {
        throw new RuntimeException();
    }

    static int test_003() {
        int y = 3;
        for (int i = 0; i < 9; ) {
            for (long l = 1; l < 5; l++) {
                y *= 2;
            }
            while (true) {
                dontInline();
            }
        }
        return y;
    }

    static void test_004() {
        for (int i2 = 4; i2 < 13; i2++) {
            double d = 56;
            for (long l = 1; l < 5; l++) {
                d = d + 3;
            }
            for (int i = 0; i < 10; i++) {
                for (int d2 = i2; d2 < 2; d2 = 3) {
                }
            }
        }
    }

    public static int test_005() {
        long arr[]=new long[400];
        for (int i = 3; i < 177; i++) {
            for (int j = 0; j < 10; j++){}
        }
        int y = 0;
        for (int i = 15; i < 356; i++) {
            // Inner loop prevents strip-mining of outer loop
            // later, inner loop is removed, so outer does pre-main-post without strip-mining
            for (int j = 0; j < 10; j++){
                y |= 1;
            }
        }
        return y;
    }
}

