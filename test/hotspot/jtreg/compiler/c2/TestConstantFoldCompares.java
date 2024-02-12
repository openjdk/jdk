/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package compiler.c2;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8325674
 * @summary Test constant folding compares
 * @library /test/lib /
 * @run driver compiler.c2.TestConstantFoldCompares
 */
public class TestConstantFoldCompares {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final int NARROW = 1000;
    int input;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Setup
    public Object[] setup() {
        input = RANDOM.nextInt();
        return new Object[] { input };
    }

    @Setup
    public Object[] setupEquality() {
        int r = RANDOM.nextInt();
        // Make sure equality results are true sometimes to prevent generation of
        // uncommon traps.
        input = r % 2 == 0 ? 58 : r;
        return new Object[] { input };
    }

    public void checkAddLtCommon(boolean returnValue) {
        if ((input < 58 && !returnValue) || (input >= 58 && returnValue)) {
            throw new RuntimeException("Illegal constant folding of add lt comparisonInt");
        }
    }

    public void checkSubLtCommon(boolean returnValue) {
        if ((input > 58 && !returnValue) || (input <= 58 && returnValue)) {
            throw new RuntimeException("Illegal constant folding of sub lt comparisonInt");
        }
    }

    public void checkEqCommon(boolean returnValue) {
        if ((input == 58 && !returnValue) || input != 58 && returnValue) {
            throw new RuntimeException("Illegal constant folding of eq comparisonInt");
        }
    }

    public void checkNeCommon(boolean returnValue) {
        if ((input == 58 && returnValue) || input != 58 && !returnValue) {
            throw new RuntimeException("Illegal constant folding of ne comparisonInt");
        }
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "1"})
    public boolean dontFoldAddInt(int x) {
        return 42 + x < 100;
    }

    @Check(test = "dontFoldAddInt")
    public void checkDontFoldAddInt(boolean returnValue) {
        checkAddLtCommon(returnValue);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {IRNode.ADD_I, "1"})
    public boolean dontFoldAdd2Int(int x) {
        return (Integer.MAX_VALUE - 1) + Integer.max(x, -100) < -100;
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_I, "1"})
    public boolean dontFoldSubInt(int x) {
        return 100 - x < 42;
    }

    @Check(test = "dontFoldSubInt")
    public void checkDontFoldSubInt(boolean returnValue) {
        checkSubLtCommon(returnValue);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {IRNode.SUB_I, "1"})
    public boolean dontFoldSub2Int(int x) {
        return 42 - Integer.min(x, -100) < (Integer.MIN_VALUE + 1);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.ADD_I})
    public boolean foldAddLtInt(int x) {
        return 42 + Integer.min(x, NARROW) < 100;
    }

    @Check(test = "foldAddLtInt")
    public void checkFoldAddLtInt(boolean returnValue) {
        checkAddLtCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.SUB_I})
    public boolean foldSubLtInt(int x) {
        return 100 - Integer.max(x, -NARROW) < 42;
    }

    @Check(test = "foldSubLtInt")
    public void checkFoldSubLtInt(boolean returnValue) {
        checkSubLtCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setupEquality")
    @IR(failOn = {IRNode.ADD_I})
    @IR(failOn = {IRNode.SUB_I})
    public boolean foldAddEqInt(int x) {
        return 42 + x == 100;
    }

    @Check(test = "foldAddEqInt")
    public void checkFoldAddEqInt(boolean returnValue) {
        checkEqCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setupEquality")
    @IR(failOn = {IRNode.ADD_I})
    @IR(failOn = {IRNode.SUB_I})
    public boolean foldSubEqInt(int x) {
        return 42 - x == -16;
    }

    @Check(test = "foldAddNeInt")
    public void checkFoldAddNeInt(boolean returnValue) {
        checkNeCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setupEquality")
    @IR(failOn = {IRNode.ADD_I})
    @IR(failOn = {IRNode.SUB_I})
    public boolean foldAddNeInt(int x) {
        return 42 + x != 100;
    }

    @Check(test = "foldSubEqInt")
    public void checkFoldSubEqInt(boolean returnValue) {
        checkEqCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setupEquality")
    @IR(failOn = {IRNode.ADD_I})
    @IR(failOn = {IRNode.SUB_I})
    public boolean foldSubNeInt(int x) {
        return 42 - x != -16;
    }

    @Check(test = "foldSubNeInt")
    public void checkFoldSubNeInt(boolean returnValue) {
        checkNeCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    public boolean dontFoldAddLong(long x) {
        return 42 + x < 100;
    }

    @Check(test = "dontFoldAddLong")
    public void checkDontFoldAddLong(boolean returnValue) {
        checkAddLtCommon(returnValue);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {IRNode.ADD_L, "1"})
    public boolean dontFoldAdd2Long(long x) {
        return (Long.MAX_VALUE - 1) + Long.max(x, -100) < -100;
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_L, "1"})
    public boolean dontFoldSubLong(long x) {
        return 100 - x < 42;
    }

    @Check(test = "dontFoldSubLong")
    public void checkDontFoldSubLong(boolean returnValue) {
        checkSubLtCommon(returnValue);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {IRNode.SUB_L, "1"})
    public boolean dontFoldSub2Long(long x) {
        return 42 - Long.min(x, -100) < (Long.MIN_VALUE + 1);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.ADD})
    public boolean foldAddLtLong(int x) {
        return 42 + Long.min(x, NARROW) < 100;
    }

    @Check(test = "foldAddLtLong")
    public void checkFoldAddLt(boolean returnValue) {
        checkAddLtCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.SUB})
    public boolean foldSubLtLong(int x) {
        return 100 - Long.max(x, -NARROW) < 42;
    }

    @Check(test = "foldSubLtLong")
    public void checkFoldSubLt(boolean returnValue) {
        checkSubLtCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setupEquality")
    @IR(failOn = {IRNode.ADD})
    @IR(failOn = {IRNode.SUB})
    public boolean foldAddEqLong(long x) {
        return 42 + x == 100;
    }

    @Check(test = "foldAddEqLong")
    public void checkFoldAddEq(boolean returnValue) {
        checkEqCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setupEquality")
    @IR(failOn = {IRNode.ADD})
    @IR(failOn = {IRNode.SUB})
    public boolean foldSubEqLong(long x) {
        return 42 - x == -16;
    }

    @Check(test = "foldAddNeLong")
    public void checkFoldAddNe(boolean returnValue) {
        checkNeCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setupEquality")
    @IR(failOn = {IRNode.ADD_I})
    @IR(failOn = {IRNode.SUB_I})
    public boolean foldAddNeLong(long x) {
        return 42 + x != 100;
    }

    @Check(test = "foldSubEqLong")
    public void checkFoldSubEq(boolean returnValue) {
        checkEqCommon(returnValue);
    }

    @Test
    @Arguments(setup = "setupEquality")
    @IR(failOn = {IRNode.ADD_I})
    @IR(failOn = {IRNode.SUB_I})
    public boolean foldSubNeLong(long x) {
        return 42 - x != -16;
    }

    @Check(test = "foldSubNeLong")
    public void checkFoldSubNe(boolean returnValue) {
        checkNeCommon(returnValue);
    }
}
