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
 * @test id=coh
 * @summary Exercise minimal int array fill with i - c index and forced fill optimization.
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation
 *                   -XX:+OptimizeFill
 *                   -XX:+UseCompactObjectHeaders
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestArrayFillWithSubIndexMin::test_coh
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestArrayFillWithSubIndexMin::test_nocoh
 *                   compiler.loopopts.TestArrayFillWithSubIndexMin
 */

/**
 * @test id=no-coh
 * @summary Exercise minimal int array fill with i - c index and disabled fill optimization.
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation
 *                   -XX:+OptimizeFill
 *                   -XX:-UseCompactObjectHeaders
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestArrayFillWithSubIndexMin::test_coh
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestArrayFillWithSubIndexMin::test_nocoh
 *                   compiler.loopopts.TestArrayFillWithSubIndexMin
 */

package compiler.loopopts;

public class TestArrayFillWithSubIndexMin {
    static final int ARRAY_SIZE = 100;
    static final int[] ARRAY = new int[ARRAY_SIZE];

    public static void test_coh() {
        for (int i = 3; i < ARRAY_SIZE; i++) {
            ARRAY[i - 3] = 42;
        }
    }

    public static void test_nocoh() {
        for (int i = 4; i < ARRAY_SIZE; i++) {
            ARRAY[i - 4] = 42;
        }
    }

    public static void main(String[] args) {
        test_coh();
        test_nocoh();
    }
}
