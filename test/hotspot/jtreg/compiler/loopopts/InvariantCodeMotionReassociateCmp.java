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

package compiler.c2.loopopts;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8323220
 * @summary Test loop invariant code motion for cmp nodes through reassociation
 * @library /test/lib /
 * @run driver compiler.c2.loopopts.InvariantCodeMotionReassociateCmp
 */
public class InvariantCodeMotionReassociateCmp {
    private static final Random RANDOM = Utils.getRandomInstance();
    private int size;
    private int inv1;
    private int inv2;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    private void blackhole() {}

    @Setup
    public Object[] setup(SetupInfo info) {
        int count = info.invocationCounter();
        size = count + 500;
        inv1 = count;
        if (RANDOM.nextInt() % 7 == 0) {
            // Setup inputs to be equals sometimes to avoid uncommon traps
            inv2 = inv1;
        } else {
            inv2 = count * 2;
        }
        return new Object[] { inv1, inv2, size };
    }

    public void fail(int returnValue) {
        throw new RuntimeException("Illegal reassociation: returnValue=" + returnValue + ", inv1=" + inv1
                + ", inv2=" + inv2 + ", size=" + size);
    }

    public void checkEq(int returnValue) {
        int invDiff = inv2 - inv1;
        if ((invDiff < size && returnValue != invDiff) || (invDiff >= size && returnValue != size)) {
            fail(returnValue);
        }
    }

    public void checkNe(int returnValue) {
        int invDiff = inv2 - inv1;
        if ((invDiff != 0 && returnValue != 0) || (invDiff == 0 && returnValue != 1)) {
            fail(returnValue);
        }
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_I, "1"})
    public int equalsAddInt(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv2 - inv1 == i`
            if (inv1 + i == inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "equalsAddInt")
    public void checkEqualsAddInt(int returnValue) {
        checkEq(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_L, "1"})
    public int equalsAddLong(long inv1, long inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv2 - inv1 == i`
            if (inv1 + i == inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "equalsAddLong")
    public void checkEqualsAddLong(int returnValue) {
        checkEq(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_I, "1"})
    public int equalsInvariantSubVariantInt(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv1 - inv2 == i`
            if (inv2 - i == inv1) {
                break;
            }
        }
        return i;
    }

    @Check(test = "equalsInvariantSubVariantInt")
    public void checkEqualsInvariantSubVariantInt(int returnValue) {
        checkEq(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_L, "1"})
    public int equalsInvariantSubVariantLong(long inv1, long inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv2 - inv1 == i`
            if (inv2 - i == inv1) {
                break;
            }
        }
        return i;
    }

    @Check(test = "equalsInvariantSubVariantLong")
    public void checkEqualsInvariantSubVariantLong(int returnValue) {
        checkEq(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_I, "1"})
    public int equalsVariantSubInvariantInt(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv2 - inv1 == i`
            if (i - inv2 == -inv1) {
                break;
            }
        }
        return i;
    }

    @Check(test = "equalsVariantSubInvariantInt")
    public void checkEqualsVariantSubInvariantInt(int returnValue) {
        checkEq(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_L, "1"})
    public int equalsVariantSubInvariantLong(long inv1, long inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv2 - inv1 == i`
            if (i - inv2 == -inv1) {
                break;
            }
        }
        return i;
    }

    @Check(test = "equalsVariantSubInvariantLong")
    public void checkEqualsVariantSubInvariantLong(int returnValue) {
        checkEq(returnValue);
    }


    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_I, "1"})
    public int notEqualsAddInt(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < 500; ++i) {
            blackhole();
            // Reassociate to `inv1 - inv2 != i`
            if (inv1 + i != inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "notEqualsAddInt")
    public void checkNotEqualsAddInt(int returnValue) {
        checkNe(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_L, "1"})
    public int notEqualsAddLong(long inv1, long inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv1 - inv2 != i`
            if (inv1 + i != inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "notEqualsAddLong")
    public void checkNotEqualsAddLong(int returnValue) {
        checkNe(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_I, "1"})
    public int notEqualsInvariantSubVariantInt(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv1 - inv2 != i`
            if (inv1 - i != inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "notEqualsInvariantSubVariantInt")
    public void checkNotEqualsInvariantSubVariantInt(int returnValue) {
        checkNe(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_L, "1"})
    public int notEqualsInvariantSubVariantLong(long inv1, long inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv1 - inv2 != i`
            if (inv1 - i != inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "notEqualsInvariantSubVariantLong")
    public void checkNotEqualsInvariantSubVariantLong(int returnValue) {
        checkNe(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_I, "1"})
    public int notEqualsVariantSubInvariantInt(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < 500; ++i) {
            blackhole();
            // Reassociate to `inv2 - inv1 != i`
            if (i - inv2 != -inv1) {
                break;
            }
        }
        return i;
    }

    @Check(test = "notEqualsVariantSubInvariantInt")
    public void checkNotEqualsVariantSubInvariantInt(int returnValue) {
        checkNe(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.SUB_L, "1"})
    public int notEqualsVariantSubInvariantLong(long inv1, long inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            // Reassociate to `inv1 - inv1 != i`
            if (i - inv2 != -inv1) {
                break;
            }
        }
        return i;
    }

    @Check(test = "notEqualsVariantSubInvariantLong")
    public void checkNotEqualsVariantSubInvariantLong(int returnValue) {
        checkNe(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.SUB_I})
    public int ltDontReassociate(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            if (inv1 + i < inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "ltDontReassociate")
    public void checkLtDontReassociate(int returnValue) {
        int sum = inv1 + returnValue;
        if ((returnValue < size && sum >= inv2) || (returnValue > size && sum < inv2)) {
            fail(returnValue);
        }
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.SUB_I})
    public int leDontReassociate(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            if (inv1 + i <= inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "leDontReassociate")
    public void checkLeDontReassociate(int returnValue) {
        int sum = inv1 + returnValue;
        if ((returnValue < size && sum > inv2) || (returnValue > size && sum <= inv2)) {
            fail(returnValue);
        }
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.SUB_I})
    public int gtDontReassociate(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            if (inv1 + i > inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "gtDontReassociate")
    public void checkGtDontReassociate(int returnValue) {
        int sum = inv1 + returnValue;
        if ((returnValue < size && sum <= inv2) || (returnValue > size && sum > inv2)) {
            fail(returnValue);
        }
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.SUB_I})
    public int geDontReassociate(int inv1, int inv2, int size) {
        int i = 0;
        for (; i < size; ++i) {
            blackhole();
            if (inv1 + i >= inv2) {
                break;
            }
        }
        return i;
    }

    @Check(test = "geDontReassociate")
    public void checkGeDontReassociate(int returnValue) {
        int sum = inv1 + returnValue;
        if ((returnValue < size && sum < inv2) || (returnValue > size && sum >= inv2)) {
            fail(returnValue);
        }
    }
}
