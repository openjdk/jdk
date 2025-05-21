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

/*
 * @test
 * @bug 8327963
 * @summary C2: fix construction of memory graph around Initialize node to prevent incorrect execution if allocation is removed
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement TestEarlyEliminationOfAllocationWithoutUse
 * @run main/othervm TestEarlyEliminationOfAllocationWithoutUse
 */

import java.util.Arrays;

public class TestEarlyEliminationOfAllocationWithoutUse {
    private static volatile int volatileField;

    public static void main(String[] args) {
        boolean[] allTrue = new boolean[3];
        Arrays.fill(allTrue, true);
        A a = new A();
        boolean[] allFalse = new boolean[3];
        for (int i = 0; i < 20_000; i++) {
            a.field1 = 0;
            test1(a, allTrue);
            test1(a, allFalse);
            if (a.field1 != 42) {
                throw new RuntimeException("Lost Store");
            }
        }
    }

    private static void test1(A otherA, boolean[] flags) {
        if (flags == null) {
        }
        otherA.field1 = 42;
        // Fully unrolled before EA
        for (int i = 0; i < 3; i++) {
            A a = new A(); // removed right after EA
            if (flags[i]) {
                break;
            }
        }
    }

    private static class A {
        int field1;
    }
}
