/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8267532
 * @summary check that uncommon trap is generated for unhandled catch block
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestPrunedExHandler
 */

public class TestPrunedExHandler {
    public static void main(String[] args) {
        TestFramework.runWithFlags(
            "-XX:CompileCommand=dontinline,compiler.c2.irTests.TestPrunedExHandler::outOfLine");
    }

    @Test
    @IR(counts = {IRNode.UNREACHED_TRAP, "1"})
    public static void testTrap() {
        try {
            outOfLine(false);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private static void outOfLine(boolean shouldThrow) {
        if (shouldThrow) {
            throw new IllegalStateException();
        }
    }

    @Test
    @IR(counts = {IRNode.UNREACHED_TRAP, "0"})
    public static void testNoTrap(boolean shouldThrow) {
        try {
            outOfLine(shouldThrow);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    @Run(test = "testNoTrap", mode = RunMode.STANDALONE)
    public static void runNoTrap() {
        for (int i = 0; i < 2_000; i++) { // tier 3
            testNoTrap(false);
        }

        testNoTrap(true); // mark ex handler as entered

        for (int i = 0; i < 20_000; i++) { // tier 4
            testNoTrap(false); // should have no trap
        }
    }
}
