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
 * @summary Missing notification patterns for AddNode::Ideal_collapse_variable_times_con.
 * @library /test/lib /
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:+UnlockDiagnosticVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:+StressIGVN
 *                   ${test.main.class}
 * @run main/othervm --enable-preview
 *                   -XX:-TieredCompilation -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   -XX:+StressIGVN
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingVariableTimesConIdealization {
    static short sFld;
    static int iFld;
    static long lFld;

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test1I();
            test1L();
        }
        test2I();
    }

    static void test1I() {
        iFld = iFld - Byte.valueOf((byte) -6) * iFld;
    }

    static void test1L() {
        lFld = lFld - Byte.valueOf((byte) -6) * lFld;
    }

    // Only reproduced with --enable-preview.
    static int test2I() {
        return sFld + Short.valueOf((short) 16388) * sFld;
    }
}
