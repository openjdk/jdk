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
 * @bug 8358889
 * @summary Test that a spilled uncommon trap request is handled properly.
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @run main ${test.main.class}
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -Xcomp
 *                   -XX:StressSeed=403 -XX:+StressGCM
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:CompileCommand=dontinline,${test.main.class}::dontInline
 *                   ${test.main.class}
 */

import jdk.test.lib.Asserts;

public class TestSpilledUncommonTrapRequest {
    static long sum;
    static final int[] array = new int[64];

    static int max(int first, int second) {
        return first > second ? first : second;
    }

    static int dontInline() {
        return 42;
    }

    static void test(double[][] doubles) {
        // Vectorize a loop such that Compile::current()->max_vector_size() > 0 holds
        for (int i = 0; i < 64; i++) {
            array[i] = i;
        }

        // C2 adds an uncommon trap with 'Reason_null_check == 1' and 'Action_maybe_recompile == 1' for the
        // null-check of the inner array doubles[0] here. This is encoded as
        // ~((reason << 3) + action) = ~((1 << 3) + 1) = ~9 = -10
        // which is then shared with the explicit constant -10 passed as argument here.
        doubles[0][0] = max(dontInline(), -10);

        // Keep the shared -10 live in a loop phi and create enough register pressure for RA to spill it.
        int product = -10;
        for (int i = 0; i < 64; i++) {
            product *= 79;
            sum++;
            array[i] = 0;
        }
        sum += product;
    }

    public static void main(String[] args) {
        test(new double[1][1]);
        Asserts.assertEQ(sum, 1573390390L);
    }
}

