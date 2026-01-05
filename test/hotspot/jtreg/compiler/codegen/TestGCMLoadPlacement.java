/*
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

package compiler.codegen;

/**
 * @test
 * @bug 8333393
 * @summary Test that loads are not scheduled too late.
 * @run main/othervm -XX:CompileCommand=compileonly,*Test*::test
 *                   -XX:-TieredCompilation -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=dontinline,*::dontInline
 *                   compiler.codegen.TestGCMLoadPlacement
 * @run main/othervm -XX:CompileCommand=compileonly,*Test*::test
 *                   -XX:-TieredCompilation -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=dontinline,*::dontInline
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   compiler.codegen.TestGCMLoadPlacement
 * @run main/othervm -XX:LoopMaxUnroll=0
 *                   -XX:CompileCommand=compileonly,*Test*::test
 *                   -XX:-TieredCompilation -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   compiler.codegen.TestGCMLoadPlacement
 * @run main/othervm -XX:LoopMaxUnroll=0
 *                   -XX:CompileCommand=compileonly,*Test*::test
 *                   -XX:-TieredCompilation -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   compiler.codegen.TestGCMLoadPlacement
 * @run main compiler.codegen.TestGCMLoadPlacement
 */

public class TestGCMLoadPlacement {

    public static void main(String[] args) {
        int c = 0;
        try {
            Test1.run();
        } catch (Exception e) {
            c++; System.out.println("Test1 failure");
        }
        try {
            Test2.run();
        } catch (Exception e) {
            c++; System.out.println("Test2 failure");
        }
        try {
            Test3.run();
        } catch (Exception e) {
            c++; System.out.println("Test3 failure");
        }
        try {
            Test4.run();
        } catch (Exception e) {
            c++; System.out.println("Test4 failure");
        }
        try {
            Test5.run();
        } catch (Exception e) {
            c++; System.out.println("Test5 failure");
        }
        if (c > 0) {
            throw new RuntimeException("Test failures: " + c);
        }
    }

    static class Test1 {
        static boolean flag;
        volatile byte volFld;
        int iFld;

        int test() {
            for (int i = 0; i < 50; ++i) {
                for (int j = 0; j < 50; ++j) {
                    if (flag) { return 0; } // Forces peeling
                    iFld = 0;
                    for (int k = 0; k < 1; ++k) {
                    }
                }
            }
            int res = iFld; // This load needs to schedule before the loop below ...
            for (int i = 0; i < 50; ++i) {
                volFld = 0;
                iFld -= 42;
            }
            // ... and was incorrectly scheduled here.
            return res;
        }

        static void run() {
            Test1 t = new Test1();
            for (int i = 0; i < 10; i++) {
                int res = t.test();
                if (res != 0) {
                    throw new RuntimeException("Unexpected result: " + res);
                }
            }
        }
    }

    static class Test2 {
        static void run() {
            for (int i = 0; i < 500; i++) {
                int res = test();
                if (res != 0) {
                    throw new RuntimeException("res = " + res);
                }
            }
        }

        static int test() {
            int res = 0;
            int array[] = new int[50];
            for (int j = 0; j < array.length; j++) {
                array[j] = 0;
            }
            int x = array[0];
            for (int i = 5; i < 10; i++) {
                array[0] = 42;
                for (int j = 0; j < 10; j++) {
                    dontInline();
                    res = x;
                }
            }
            return res;
        }

        static void dontInline() {}
    }

    static class Test3 {
        static boolean flag;
        static int N = 400;
        long instanceCount;
        float fFld = 2.957F;
        volatile short sFld;
        int iArrFld[] = new int[N];

        int test() {
            int i22 = 7, i25, i27, i28 = 5, i29, i31, i33;
            for (i25 = 229; i25 > 2; --i25) {
                if (flag) { return 9; }
                iArrFld[1] *= instanceCount;
                for (i27 = 4; i27 < 116; ++i27) {
                }
            }
            i22 += fFld;
            for (i29 = 23; 8 < i29; i29--) {
                for (i31 = 2; i31 < 17; i31++) {
                    if (flag) { return 9; }
                    i28 = sFld;
                }
                for (i33 = 1; 7 > i33; ++i33) {
                    if (flag) { return 9; }
                    fFld = instanceCount;
                }
            }
            return i22;
        }

        static void run() {
            Test3 r = new Test3();
            int result = r.test();
            if (result != 9) {
                throw new RuntimeException("Expected 9 but found " + result);
            }
        }
    }

    static class Test4 {
        static boolean flag;
        volatile byte volFld;
        int iFld;

        int test() {
            for (int j = 0; j < 50; ++j) {
                iFld = 0;
                if (flag) { return 0; }

                for (int k = 0; k < 2000; ++k) {
                }
            }

            int res = iFld;
            for (int i = 0; i < 50; ++i) {
                volFld = 0;
                iFld -= 1;
            }
            return res;
        }

        static void run() {
            Test4 t = new Test4();
            for (int i = 0; i < 10; i++) {
                int res = t.test();
                if (res != 0) {
                    throw new RuntimeException("Unexpected result: " + res);
                }
            }
        }
    }

    static class Test5 {
        static boolean flag;
        volatile byte volFld;
        int iFld, iFld2;

        int test() {
            for (int j = 0; j < 50; ++j) {
                iFld2 = 0;
                if (flag) { return 0; }

                for (int k = 0; k < 2000; ++k) {
                }
            }

            int res = iFld;
            for (int i = 0; i < 50; ++i) {
                volFld = 0;
                iFld -= 1;
            }
            return res;
        }

        static void run() {
            Test5 t = new Test5();
            for (int i = 0; i < 10; i++) {
                t.iFld = 0;
                int res = t.test();
                if (res != 0) {
                    throw new RuntimeException("Unexpected result: " + res);
                }
            }
        }
    }
}
