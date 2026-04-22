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
 * @bug 8352681
 * @summary Check that RAM does not crash when split load through phi
 *          tries to register an old node twice with IGVN.
 * @run main/othervm -XX:CompileCommand=compileonly,*TestReduceAllocationAndSetTypeTwice*::*
 *                   -XX:CompileCommand=dontinline,*TestReduceAllocationAndSetTypeTwice*::*
 *                   -Xcomp compiler.escapeAnalysis.TestReduceAllocationAndSetTypeTwice
 * @run main compiler.escapeAnalysis.TestReduceAllocationAndSetTypeTwice
 */

package compiler.escapeAnalysis;

public class TestReduceAllocationAndSetTypeTwice {
    public static double dummy() {
        return 3.1415;
    }

    public static double test(double param) {
        Double double_1 = -26.335025324149626D;
        Double double_2 = 87.9546734116494D;

        for (int i = 0, j = 0; i < 256; i++) {
            if (param != param) {
                j--;
            } else if (dummy() > 0) {
                return (j < 1234 ? double_1 : double_2);
            }
        }

        return 10.0;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 512; i++) {
            test(-3.1415);
        }
    }
}
