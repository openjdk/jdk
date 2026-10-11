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

package compiler.c2.igvn;

/*
 * @test
 * @bug 8393645 8393608 8393644
 * @summary C2 IGVN should idealize: (x * c1) * c2 -> x * (c1 * c2)
 * @library /test/lib /
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:+StressIGVN
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

// The test is somewhat intermittent, more stable: -XX:RepeatCompilation=50
public class TestMissingNestedMulIdealization {

    public static void main(String[] args) {
        testI(0);
        testL(0);
    }

    // Failed to optimize MulI(MulI(x, c1), c2).
    // Optimization from MulNode::Ideal
    static int testI(int x) {
        for (int i = 0; i < 17; i++) {
            x *= ~i;
        }
        return x;
    }

    // Failed to optimize MulL(MulL(x, c1), c2).
    // Optimization from MulNode::Ideal
    static long testL(long x) {
        for (long i = 0; i < 17; i++) {
            x *= ~i;
        }
        return x;
    }
}
