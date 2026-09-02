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
 * @test id=default
 * @summary Exercise minimal int array fill with i - c index and default fill optimization settings.
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation
 *                   -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestArrayFillWithSubIndexMin::offsetMinusLiteral
 *                   compiler.loopopts.TestArrayFillWithSubIndexMin
 */

/**
 * @test id=optimize-fill
 * @summary Exercise minimal int array fill with i - c index and forced fill optimization.
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation
 *                   -XX:LoopUnrollLimit=0 -XX:+OptimizeFill
 *                   -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestArrayFillWithSubIndexMin::offsetMinusLiteral
 *                   compiler.loopopts.TestArrayFillWithSubIndexMin
 */

/**
 * @test id=no-optimize-fill
 * @summary Exercise minimal int array fill with i - c index and disabled fill optimization.
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-TieredCompilation
 *                   -XX:-OptimizeFill
 *                   -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestArrayFillWithSubIndexMin::offsetMinusLiteral
 *                   compiler.loopopts.TestArrayFillWithSubIndexMin
 */

package compiler.loopopts;

public class TestArrayFillWithSubIndexMin {
    static final int N = 1000;
    static final int[] A = new int[N + 16];

    public static void offsetMinusLiteral() {
        for (int i = 3; i < N + 3; i++) {
            A[i - 3] = 7;
        }
    }

    public static void main(String[] args) {
        offsetMinusLiteral();
    }
}
