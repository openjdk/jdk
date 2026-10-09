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
 * @bug 8393645 8393641 8393586
 * @summary C2 IGVN should idealize: (x | c1) & c2 -> (x & c2) | (c1 & c2)
 * @library /test/lib /
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:+StressIGVN
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingAndOrIdealization {
    public static void main(String[] args) {
        testI(0, 0.0, false);
        testL(0, 0.0, false);
    }

    // Failed to optimize AndI(OrI(x, c1), c2).
    // Optimization from MulNode::Ideal
    static int testI(int x, double i, boolean b) {
        while (i < 4) {
            x |= b ? -126 : -30;
            x &= 32;
            i++;
        }
        return x;
    }

    // Failed to optimize AndL(OrL(x, c1), c2).
    // Optimization from MulNode::Ideal
    static long testL(long x, double i, boolean b) {
        while (i < 4) {
            x |= b ? -126 : -30;
            x &= 32;
            i++;
        }
        return x;
    }
}
