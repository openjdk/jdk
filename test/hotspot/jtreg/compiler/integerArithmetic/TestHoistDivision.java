/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package compiler.integerArithmetic;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8347365
 * @summary Tests that divisions are hoisted when their zero checks are elided.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestHoistDivision {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    private static void dontInline() {}

    @Run(test = {"testCommon", "testHoistOutOfLoop"})
    public void run() {
        Asserts.assertEQ(2, testCommon(1, 1));
        Asserts.assertEQ(0, testHoistOutOfLoop(1, 1, true, 1, 4, 2));
        Asserts.assertEQ(0, testHoistOutOfLoop(1, 1, false, 1, 4, 2));
    }

    @Test
    @IR(counts = {IRNode.DIV_I, "1", IRNode.DIV_BY_ZERO_TRAP, "1"})
    public int testCommon(int x, int y) {
        // The 2 divisions should be commoned
        int result = x / y;
        dontInline();
        return result + x / y;
    }

    @Test
    @IR(failOn = IRNode.DIV_BY_ZERO_TRAP, counts = {IRNode.DIV_I, "1", IRNode.TRAP, "1"})
    public int testHoistOutOfLoop(int x, int y, boolean b, int start, int limit, int step) {
        // The divisions should be hoisted out of the loop, allowing them to be commoned
        int result = 0;
        for (int i = start; i < limit; i *= step) {
            if (b) {
                dontInline();
                result += x / y;
            } else {
                result -= x / y;
            }
            b = !b;
        }
        return result;
    }
}
