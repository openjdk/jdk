/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug 8376400
 * @summary C2: folding ifs may cause incorrect execution when trap is taken
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:-OptimizeUnstableIf ${test.main.class}
 * @run main ${test.main.class}
 *
 */

package compiler.rangechecks;

public class TestFoldedIfsWrongReexec {
    private static int taken1;
    private static int taken2;
    private static int taken3;
    private static int taken4;
    private static int taken5;
    private static int taken6;
    private static int taken7;
    private static int MIN_VALUE = Integer.MIN_VALUE;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(12);
            if (taken1 != 0) {
                throw new RuntimeException("branch shouldn't have been taken");
            }
            test1Helper1(16, 0);
            test2(12);
            if (taken2 != 0) {
                throw new RuntimeException("branch shouldn't have been taken");
            }
            test2Helper1(16, 0);
            test3(12);
            if (taken3 != 0) {
                throw new RuntimeException("branch shouldn't have been taken");
            }
            test3Helper1(16, 0);
            test4(12, 1, 2);
            if (taken4 != 0) {
                throw new RuntimeException("branch shouldn't have been taken");
            }
            test4Helper1(16, 0, 1, 2);
            test5(12);
            if (taken5 != 0) {
                throw new RuntimeException("branch shouldn't have been taken");
            }
            test5Helper1(16, 0);
            test6(12, 1, 2);
            if (taken6 != 0) {
                throw new RuntimeException("branch shouldn't have been taken");
            }
            test6Helper1(16, 0, 1, 2);
            test7(12);
            if (taken7 != 0) {
                throw new RuntimeException("branch shouldn't have been taken");
            }
            test7Helper1(16, 0);
            test7Helper2(o1);
            test7Helper2(a);
            test7Helper2(b);
        }
        test1(0);
        if (taken1 == 0) {
            throw new RuntimeException("branch should have been taken");
        }
        test2(0);
        if (taken2 == 0) {
            throw new RuntimeException("branch should have been taken");
        }
        test3(0);
        if (taken3 == 0) {
            throw new RuntimeException("branch should have been taken");
        }
        test4(0, 1, 2);
        if (taken4 == 0) {
            throw new RuntimeException("branch should have been taken");
        }
        test5(0);
        if (taken5 == 0) {
            throw new RuntimeException("branch should have been taken");
        }
        test6(0, 1, 2);
        if (taken6 == 0) {
            throw new RuntimeException("branch should have been taken");
        }
        test7(0);
        if (taken7 == 0) {
            throw new RuntimeException("branch should have been taken");
        }
    }

    private static void test1(int i) {
        if (test1Helper1(i, 16) == 0) {
            throw new RuntimeException("never taken");
        }
        if (i + MIN_VALUE < 8 + Integer.MIN_VALUE) {
            taken1++;
        }
        for (int j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {

            }
        }
    }

    private static int test1Helper1(int i, int j) {
        if (i + MIN_VALUE >= j + Integer.MIN_VALUE) {
            for (int k = 0; k < 100; k++) {
            }
            return 0;
        }
        return 1;
    }

    private static void test2(int i) {
        if (test2Helper1(i, 16) == 42) {
            throw new RuntimeException("never taken");
        }
        if (i + MIN_VALUE < 8 + Integer.MIN_VALUE) {
            taken2++;
        }
        for (int j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {

            }
        }
    }

    private static int test2Helper1(int i, int j) {
        if (i + MIN_VALUE >= j + Integer.MIN_VALUE) {
            for (int k = 0; k < 100; k++) {
            }
            return 42;
        }
        return 0x42;
    }

    private static void test3(int i) {
        if (test3Helper1(i, 16) == 42L) {
            throw new RuntimeException("never taken");
        }
        if (i + MIN_VALUE < 8 + Integer.MIN_VALUE) {
            taken3++;
        }
        for (int j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {

            }
        }
    }

    private static long test3Helper1(int i, int j) {
        if (i + MIN_VALUE >= j + Integer.MIN_VALUE) {
            for (int k = 0; k < 100; k++) {
            }
            return 42L;
        }
        return 0x42L;
    }

    private static void test4(int i, int x, int y) {
        if (x == y) {
            throw new RuntimeException("never taken");
        }
        if (test4Helper1(i, 16, x, y) == y) {
            throw new RuntimeException("never taken");
        }
        if (i + MIN_VALUE < 8 + Integer.MIN_VALUE) {
            taken4++;
        }
        for (int j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {

            }
        }
    }

    private static int test4Helper1(int i, int j, int x, int y) {
        if (i + MIN_VALUE >= j + Integer.MIN_VALUE) {
            for (int k = 0; k < 100; k++) {
            }
            return y;
        }
        return x;
    }

    static final Object o1 = new Object();
    static final Object o2 = new Object();

    private static void test5(int i) {
        if (test5Helper1(i, 16) == o1) {
            throw new RuntimeException("never taken");
        }
        if (i + MIN_VALUE < 8 + Integer.MIN_VALUE) {
            taken5++;
        }
        for (int j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {

            }
        }
    }

    private static Object test5Helper1(int i, int j) {
        if (i + MIN_VALUE >= j + Integer.MIN_VALUE) {
            for (int k = 0; k < 100; k++) {
            }
            return o1;
        }
        return o2;
    }

    private static void test6(int i, int x, int y) {
        if (x < y) {
            if (test6Helper1(i, 16, x, y) < y) {
                throw new RuntimeException("never taken");
            }
            if (i + MIN_VALUE < 8 + Integer.MIN_VALUE) {
                taken6++;
            }
        }
        for (int j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {

            }
        }
    }

    private static int test6Helper1(int i, int j, int x, int y) {
        if (i + MIN_VALUE >= j + Integer.MIN_VALUE) {
            for (int k = 0; k < 100; k++) {
            }
            return x;
        }
        return y;
    }

    static final Object a = new A();
    static final Object b = new B();

    private static void test7(int i) {
        if (test7Helper2(test7Helper1(i, 16))) {
            throw new RuntimeException("never taken");
        }
        if (i + MIN_VALUE < 8 + Integer.MIN_VALUE) {
            taken7++;
        }
        for (int j = 0; j < 10; j++) {
            for (int k = 0; k < 10; k++) {

            }
        }
    }

    private static Object test7Helper1(int i, int j) {
        if (i + MIN_VALUE >= j + Integer.MIN_VALUE) {
            for (int k = 0; k < 100; k++) {
            }
            return a;
        }
        return b;
    }

    private static boolean test7Helper2(Object o) {
        return o instanceof A;
    }

    private static class A {
    }

    private static class B {
    }
}
