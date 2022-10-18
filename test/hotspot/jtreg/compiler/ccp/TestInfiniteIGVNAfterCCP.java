/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8290711
 * @summary assert(false) failed: infinite loop in PhaseIterGVN::optimize
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:-TieredCompilation TestInfiniteIGVNAfterCCP
 */


import java.util.function.BooleanSupplier;

public class TestInfiniteIGVNAfterCCP {
    private static int inc;
    private static volatile boolean barrier;

    static class A {
        int field1;
        int field2;
    }

    public static void main(String[] args) {
        A a = new A();
        for (int i = 0; i < 20_000; i++) {
            test(false, a, false);
            inc = 0;
            testHelper(true, () -> inc < 10, a, 4, true);
            inc = 0;
            testHelper(true, () -> inc < 10, a, 4, false);
            testHelper(false, () -> inc < 10, a, 42, false);
        }
    }

    private static void test(boolean flag2, A a, boolean flag1) {
        int i = 2;
        for (; i < 4; i *= 2);
        testHelper(flag2, () -> true, a, i, flag1);
    }

    private static void testHelper(boolean flag2, BooleanSupplier f, A a, int i, boolean flag1) {
        if (i == 4) {
            if (a == null) {

            }
        } else {
            a = null;
        }
        if (flag2) {
            while (true) {
                synchronized (new Object()) {

                }
                if (!f.getAsBoolean()) {
                    break;
                }
                if (flag1) {
                    if (a == null) {

                    }
                }
                barrier = true;
                inc++;
                if (inc % 2 == 0) {
                    a.field1++;
                }
            }
        }
    }
}
