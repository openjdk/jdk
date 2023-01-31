/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8298935
 * @summary Writing forward on array creates cyclic dependency
 *          which leads to wrong result, when ignored.
 * @library /test/lib
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *      -XX:CompileCommand=compileonly,TestCyclicDependency::test*
 *      TestCyclicDependency
 */

import jdk.test.lib.Asserts;

public class TestCyclicDependency {
    static int N = 200;
    static float fArr[] = new float[N];
    static int iArr[] = new int[N];

    public static void main(String[] strArr) {
        init();
        test3();
        Asserts.assertEQ(test_sum(), 16716);

        init();
        test2();
        Asserts.assertEQ(test_sum2(), 1080);

        init();
        test1();
        Asserts.assertEQ(test_sum(), 0);

        init();
        test0();
        Asserts.assertEQ(test_sum(), 0);
    }

    static void test0() {
        for (int i = 4; i < 100; i++) {
            int v = iArr[i - 1];
            iArr[i + 1] = v; // forward writing at least 2 -> cyclic dependency
            fArr[i] = v; // seems required
        }
    }

    static void test1() {
        for (int i = 4; i < 100; i++) {
            int v = iArr[i];
            iArr[i + 2] = v; // forward writing at least 2 -> cyclic dependency
            fArr[i] = v; // seems required
        }
    }

    static void test2() {
        for (int i = 4; i < 100; i++) {
            int v = iArr[i];
            iArr[i] = v + 5;
            fArr[i] = v;
        }
    }

    static void test3() {
        for (int i = 0; i < N-1; ++i) {
            iArr[i+1] = i;
            iArr[i] -= 15;
        }
    }

    public static int test_sum() {
        int sum = 0;
        for (int j = 0; j < iArr.length; j++) {
            sum += iArr[j];
        }
        return sum;
    }

    // reduction example - this self-cycle is allowed
    public static int test_sum2() {
        int sum = 0;
        for (int j = 0; j < iArr.length; j++) {
            sum += iArr[j]*2;
        }
        return sum;
    }

    public static void init() {
        for (int j = 0; j < iArr.length; j++) {
            iArr[j] = (j >= 20 && j < 80) ? 1 : 0;
        }
    }
}
