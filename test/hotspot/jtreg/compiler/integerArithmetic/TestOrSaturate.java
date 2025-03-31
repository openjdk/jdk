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
 *
 */

package compiler.integerArithmetic;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8352893
 * @summary Test that an or with all bits set is folded to all bits (x | -1 == -1).
 * @key randomness
 * @library / /test/lib
 * @run driver compiler.integerArithmetic.TestOrSaturate
 */

public class TestOrSaturate {
    public static void main(String[] args) {
        TestFramework.run();
    }

    private static final Random random = Utils.getRandomInstance();

    @Run(test = {"testL", "testI", "testDelayed"})
    public static void check() {
        Asserts.assertEQ(-1L, testL(random.nextLong()));
        Asserts.assertEQ(-1, testI(random.nextInt()));
        Asserts.assertEQ(-1, testDelayed(random.nextInt()));
    }

    @Test
    @IR(failOn = { IRNode.OR_L })
    // Tests that the OrLNode is folded if one operand is -1.
    private static long testL(long x) {
        return x | -1L;
    }

    @Test
    @IR(failOn = { IRNode.OR_I })
    // Tests that the OrINode is folded if one operand is -1.
    private static int testI(int x) {
        return x | -1;
    }

    @Test
    @IR(counts = {IRNode.OR_I, "1"},
        phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = { IRNode.OR_I })
    // Tests that the OrI node is folded after parsing if one operand is -1.
    private static int testDelayed(int x) {
        int min1 = 42;
        int limit = 2;

        // Ensure that min1 == -1 is only known after some constant propagation.
        for (; limit < 4; limit *= 2) {}
        for (int i = 2; i < limit; i++) {
            min1 = -1;
        }

        // Will only be folded after min1 == -1 is established.
        return x | min1;
    }
}
