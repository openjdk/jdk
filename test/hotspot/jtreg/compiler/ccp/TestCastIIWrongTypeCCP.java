/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297264
 * @summary Test that CastII nodes are added to the CCP worklist if they could have been
 *          optimized due to a CmpI/If pattern.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.ccp.TestCastIIWrongTypeCCP::*
 *                   compiler.ccp.TestCastIIWrongTypeCCP
 */
package compiler.ccp;

public class TestCastIIWrongTypeCCP {

    static int x;

    public static void main(String[] args) {
        test();
    }

    static void test() {
        int iArr[] = new int[400];
        int i = 0;
        do {
            for (int i5 = 1; i5 < 4; i5++) {
                for (int i9 = 2; i9 > i5; i9 -= 3) {
                    if (x != 0) {
                        A.unloaded(); // unloaded UCT
                    }
                    x = 1;
                    iArr[5] = 1;
                }
            }
            i++;
        } while (i < 10000);
    }
}

class A {
    public static void unloaded() {
    }
}
