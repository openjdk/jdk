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
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 *
 * @run main/othervm -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:-TieredCompilation
 *                   -XX:CompileCommand=dontinline,TestUndetectedDeadControlFlow::notInlined
 *                   TestUndetectedDeadControlFlow
 *
 */

public class TestUndetectedDeadControlFlow {
    private static int field;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(true, 0, 42);
            test1(false, 100, 42);
            test1Helper(42, 42);
            test1Helper(0, 0);
        }
    }

    private static void test1(boolean flag, int i, int k) {
        test1Helper(i, k);

        if (flag) {
            if (i >= 42) {
                throw new RuntimeException("never taken");
            }
        } else {
            if (i <= 42) {
                throw new RuntimeException("never taken");
            }
        }
        // i != 42

        if (k < 42) {
            throw new RuntimeException("never taken");
        }

        if (k > 42) {
            throw new RuntimeException("never taken");
        }

        // k = 42

        test1Helper(i, k);

        for (int j = 0; j < 10; j++) {
            notInlined();
        }
    }

    private static void notInlined() {

    }

    private static void test1Helper(int i, int k) {
        if (i == k) { // i == 42
            if (i == 42) { // i == 42 && i != 42 -> if is top
                field = 42;
            }
        }
    }
}
