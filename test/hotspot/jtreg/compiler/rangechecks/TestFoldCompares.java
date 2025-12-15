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
 * @test id=vanilla
 * @bug 8346420
 * @summary Test logic in IfNode::fold_compares, which folds 2 signed comparisons
 *          into a single comparison.
 * @library /test/lib /
 * @run main ${test.main.class} vanilla
 */

/*
 * @test id=Xcomp
 * @bug 8346420
 * @library /test/lib /
 * @run main ${test.main.class} Xcomp
 */

package compiler.rangechecks;

// TODO: do we really need all?
import jdk.test.lib.Utils;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import static compiler.lib.generators.Generators.G;
import compiler.lib.generators.Generator;

public class TestFoldCompares {
    public static boolean FLAG_FALSE = false;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        switch (args[0]) {
            case "vanilla" -> { /* no extra flags */ }
            case "Xcomp"   -> { framework.addFlags("-Xcomp"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    // Reported overflow case with wrong result in JDK-8346420
    public static void test_Case3a_LTLE_overflow(int i) {
        int minimum, maximum;
        if (FLAG_FALSE) {
            minimum = 0;
            maximum = 1;
        } else {
            // Always goes to else-path
            minimum = Integer.MIN_VALUE;
            maximum = Integer.MAX_VALUE;
        }
        // i  < INT_MIN    || i  > MAX_INT
        // 42 < INT_MIN    || 42 > MAX_INT
        //    false           false
        // => false
        //
        // C2 transforms this into:
        // i  - minimum >=u (maximum - minimum) + 1
        // 42 - INT_MIN >=u (INT_MAX - INT_MIN) + 1
        // 42 + MIN_INT >=u -1                  + 1
        //                  ------ overflow -------
        // 42 + MIN_INT >=u 0
        // => true
        if (i < minimum || i > maximum) {
            throw new RuntimeException("i can never be outside [min_int, max_int]");
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    // Same as  test_Case3a_LTLE_overflow, just with swapped conditions.
    public static void test_Case3b_LTLE_overflow(int i) {
        int minimum, maximum;
        if (FLAG_FALSE) {
            minimum = 0;
            maximum = 1;
        } else {
            // Always goes to else-path
            minimum = Integer.MIN_VALUE;
            maximum = Integer.MAX_VALUE;
        }
        if (i > maximum || i < minimum) {
            throw new RuntimeException("i can never be outside [min_int, max_int]");
        }
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    //  22  ConI  === 0  [[ 25 37 ]]  #int:0
    //  35  ConI  === 0  [[ 37 ]]  #int:minint
    //  33  ConI  === 0  [[ 38 81 ]]  #int:1
    //  37  Phi  === 34 35 22  [[ 42 80 81 84 ]]  #int:minint..0, 0u..maxint+1
    //  81  AddI  === _ 37 33  [[ 82 ]]
    //  82  Node  === 81  [[ ]]                      <----- hook
    //
    // We hit this assert:
    // "fatal error: no reachable node should have no use"
    //
    // Because we compute:
    //   lo = lo + 1
    //   hook = Node(lo)
    //   adjusted_val = i - lo
    //   -> gvn transformed to: (i - lo) + -1
    //   -> the "lo = lo + 1" AddI now is only used by the hook,
    //      but once the hook is destroyed, it has no use any more,
    //      and we hit the assert.
    static void test_Case4a_LELE_assert(int i) {
        int minimum, maximum;
        if (FLAG_FALSE) {
            minimum = 0;
            maximum = 1;
        } else {
            minimum = Integer.MIN_VALUE;
            maximum = Integer.MAX_VALUE;
        }
        if (i <= minimum || i > maximum) {
            throw new RuntimeException("should never be reached");
        }
    }
}
