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
 * @test
 * @bug 8350971
 * @summary C2 compilation fails with assert(idx == alias_idx) failed: Following Phi nodes should be on the same memory slice
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -Xbatch -XX:CompileCommand=compileonly,compiler.escapeAnalysis.TestMemoryPhiAlias::main
 *                   compiler.escapeAnalysis.TestMemoryPhiAlias
 */

package compiler.escapeAnalysis;

import java.util.function.*;

public class TestMemoryPhiAlias {
    static int[] iArrFld = new int[400];
    static int counter = 0;
    static int doWork() {
        int[] more = {94};
        java.util.function.Predicate<Integer> check = m -> m == 0;
        java.util.function.IntConsumer decrement = x -> more[0]--;
        java.util.function.BooleanSupplier innerLoop = () -> {
            while (!check.test(more[0])) {
                decrement.accept(0);
            }
            return true;
        };
        counter++;
        if (counter == 10000000) {
            throw new RuntimeException("excepted");
        }
        innerLoop.getAsBoolean();
        java.util.function.BooleanSupplier process = () -> check.test(more[0]);
        while (!process.getAsBoolean()) {
        }
        return 0;
    }

    public static void main(String[] strArr) {
        int i14 = 1;
        do {
            iArrFld[i14] = 211;
            for (int i15 = 1; i15 < 4; ++i15)
                try {
                    TestMemoryPhiAlias.doWork();
                } catch (RuntimeException e) {
                    return;
                }
        } while (i14 < 5);
    }
}