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

/*
 * @test
 * @bug 8389218 8392563
 * @summary Test that PhaseCCP reaches a fixpoint for CmpU with a wrapped
 *          AddI range or SubI range.
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 */

package compiler.ccp;

public class TestCmpUCCPFixpoint {
    static int iFld;
    static int limit;
    static float f;

    public static void main(String[] args) {
        test1();
        test2();
    }

    // CmpU with a wrapped AddI range.
    static void test1() {
        short x = -100;

        for (int i = 0; i < limit; i++) {
            x++;
        }
        x++;

        switch (x) {
            case Short.MIN_VALUE + 1:
            case Short.MAX_VALUE:
                iFld = 2;
        }
    }

    // CmpU with a wrapped SubI range.
    static void test2() {
        byte b;
        for (b = 0; b < 6; b++) {}
        switch (-b) {
            case 966:
            case -126:
                f = 0;
        }
    }
}
