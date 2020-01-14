/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8229855
 * @summary Test jump table with key value that gets out of bounds after loop unrolling.
 * @run main/othervm -XX:CompileCommand=dontinline,compiler.c2.TestJumpTable::test*
 *                   -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -XX:-UseSwitchProfiling
 *                   compiler.c2.TestJumpTable
 */

package compiler.c2;

public class TestJumpTable {

    public static int test() {
        int res = 0;
        for (int i = 10; i < 50; ++i) {
            switch (i * 5) {
                case 15:
                case 25:
                case 40:
                case 101:
                    return 42;
                case 45:
                case 51:
                case 60:
                    res++;
                    break;
            }
        }
        return res;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; ++i) {
            test();
        }
    }
}
