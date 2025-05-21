/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8351889
 * @summary C2 crash: assertion failed: Base pointers must match (addp 344)
 * @run main/othervm -XX:-BackgroundCompilation -XX:CompileOnly=TestMismatchedAddPAfterMaxUnroll::test1
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions -XX:-UseLoopPredicate
 *                   -XX:+StressIGVN -XX:StressSeed=1572080606 TestMismatchedAddPAfterMaxUnroll
 * @run main/othervm -XX:-BackgroundCompilation -XX:CompileOnly=TestMismatchedAddPAfterMaxUnroll::test1
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions -XX:-UseLoopPredicate
 *                   -XX:+StressIGVN TestMismatchedAddPAfterMaxUnroll
 * @run main/othervm TestMismatchedAddPAfterMaxUnroll
 */

public class TestMismatchedAddPAfterMaxUnroll {
    private static C[] arrayField = new C[4];

    public static void main(String[] args) {
        C c = new C();
        Object lock = new Object();
        for (int i = 0; i < 20_000; i++) {
            arrayField[3] = null;
            test1(3, c, arrayField, true, true, lock);
            arrayField[3] = null;
            test1(3, c, arrayField, true, false, lock);
            arrayField[3] = null;
            test1(3, c, arrayField, false, false, lock);
            arrayField[3] = c;
            test1(3, c, arrayField, false, false, lock);
        }
    }

    static class C {

    }

    private static void test1(int j, C c, C[] otherArray, boolean flag, boolean flag2, Object lock) {
        C[] array = arrayField;
        int i = 0;
        for (;;) {
            synchronized (lock) {}
            if (array[j] == null) {
                break;
            }
            otherArray[i] = c;
            i++;
            if (i >= 3) {
                return;
            }
        }
        if (flag) {
            if (flag2) {
            }
        }
        array[j] = c;
    }
}
