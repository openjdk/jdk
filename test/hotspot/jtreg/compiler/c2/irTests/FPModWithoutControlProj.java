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
package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8353341
 * @summary Test that Ideal transformations of Mod[DF]Node are not crashing
 *          when control proj is absent, and the node is still removed.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.FPModWithoutControlProj
 */
public class FPModWithoutControlProj {
    static int y;
    static public boolean flag;
    static int iArr[];

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+StressIGVN");
    }

    @Run(test = {"testD", "testF"})
    public void runMethod() {
        testD();
        testF();
    }

    @Test
    @IR(failOn = {IRNode.MOD_D, IRNode.MOD_F})
    public void testD() {
        int x = 243;
        double f1 = 119.303D, f2 = 2.637D;

        for (int i11 = 0; i11 < 100; i11++) {
            if (flag) {
            } else if (flag) {
                do {
                    for (f2 = 3; ; f2--) {
                        if (f2 % 3 == 1) {
                            x -= y;
                        }
                        iArr[1] += 5;
                    }
                } while (f1 < 234);
            }
        }
    }

    @Test
    @IR(failOn = {IRNode.MOD_D, IRNode.MOD_F})
    public void testF() {
        int x = 243;
        float f1 = 119.303F, f2 = 2.637F;

        for (int i11 = 0; i11 < 100; i11++) {
            if (flag) {
            } else if (flag) {
                do {
                    for (f2 = 3; ; f2--) {
                        if (f2 % 3 == 1) {
                            x -= y;
                        }
                        iArr[1] += 5;
                    }
                } while (f1 < 234);
            }
        }
    }
}
