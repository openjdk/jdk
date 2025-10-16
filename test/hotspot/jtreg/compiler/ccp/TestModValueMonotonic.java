/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8367967
 * @summary Ensure ModI/LNode::Value is monotonic with potential division by 0
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:CompileOnly=compiler.ccp.TestModValueMonotonic::test*
 *                   -XX:+StressCCP -XX:RepeatCompilation=100 -Xcomp compiler.ccp.TestModValueMonotonic
 * @run main compiler.ccp.TestModValueMonotonic
 */
package compiler.ccp;

public class TestModValueMonotonic {
    static int iFld;
    static long lFld;
    static int limit = 1000;
    static boolean flag;

    public static void main(String[] args) {
        testInt();
        testLong();
    }

    static void testInt() {
        int zero = 0;

        // Make sure loop is not counted such that it is not removed. Created a more complex graph for CCP.
        for (int i = 1; i < limit; i*=4) {
            zero = 34;
        }
        int three = flag ? 0 : 3;
        iFld = three % zero; // phi[0..3] % phi[0..34]
    }

    static void testLong() {
        long zero = 0;

        // Make sure loop is not counted such that it is not removed. Created a more complex graph for CCP.
        for (int i = 1; i < limit; i*=4) {
            zero = 34;
        }
        long three = flag ? 0 : 3;
        lFld = three % zero; // phi[0..3] % phi[0..34]
    }
}
