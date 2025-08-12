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
 * @summary Test loop invariant code motion of add/sub through reassociation
 * @library /test/lib /
 * @run driver compiler.c2.loopopts.InvariantCodeMotionReassociateAddSub
 */
public class InvariantCodeMotionReassociateAddSub {
    private static final Random RANDOM = Utils.getRandomInstance();
    private int size;
    private int inv1;
    private int inv2;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    private int blackhole(int i) { return i; }
    @DontInline
    private int blackhole(long i) { return (int)i; }

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

    public void check(int returnValue, int expected) {
        if (returnValue != expected) {
            fail(returnValue);
        }
    }

    public void checkAdd(int returnValue) {
        check(returnValue, inv1 + inv2 + size - 1);
    }

    public void checkSubAdd(int returnValue) {
        check(returnValue, inv1 - inv2 + size - 1);
    }

    public void checkNegSubAdd(int returnValue) {
        check(returnValue, -inv1 - inv2 + size - 1);
    }

    public void checkAddSub(int returnValue) {
        check(returnValue, inv1 + inv2 - (size - 1));
    }

    public void checkSubSub(int returnValue) {
        check(returnValue, inv1 - inv2 - (size - 1));
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "3"})
    public int addInt(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 + i`
            result = blackhole(inv1 + i + inv2);
        }
        return result;
    }

    @Check(test = "addInt")
    public void checkAddInt(int returnValue) {
        checkAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "2"})
    public int addLong(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 + i`
            result = blackhole(inv1 + i + inv2);
        }
        return result;
    }

    @Check(test = "addLong")
    public void checkAddLong(int returnValue) {
        checkAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "3"})
    public int addInt2(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 + i`
            result = blackhole(inv1 + (i + inv2));
        }
        return result;
    }

    @Check(test = "addInt2")
    public void checkAddInt2(int returnValue) {
        checkAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "2"})
    public int addLong2(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 + i`
            result = blackhole(inv1 + (i + inv2));
        }
        return result;
    }

    @Check(test = "addLong2")
    public void checkAddLong2(int returnValue) {
        checkAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "2"})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int minusAddInt(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 + i`
            result = blackhole(inv1 + (i - inv2));
        }
        return result;
    }

    @Check(test = "minusAddInt")
    public void checkSubAddInt(int returnValue) {
        checkSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    @IR(counts = {IRNode.SUB_L, "1"})
    public int minusAddLong(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 + i`
            result = blackhole(inv1 + (i - inv2));
        }
        return result;
    }

    @Check(test = "minusAddLong")
    public void checkSubAddLong(int returnValue) {
        checkSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "2"})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int minusAddInt2(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 + i`
            result = blackhole(inv1 - (inv2 - i));
        }
        return result;
    }

    @Check(test = "minusAddInt2")
    public void checkSubAddInt2(int returnValue) {
        checkSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    @IR(counts = {IRNode.SUB_L, "1"})
    public int minusAddLong2(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 + i`
            result = blackhole(inv1 - (inv2 - i));
        }
        return result;
    }

    @Check(test = "minusAddLong2")
    public void checkSubAddLong2(int returnValue) {
        checkSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "2"})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int minusAddInt3(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 + i`
            result = blackhole(i - inv2 + inv1);
        }
        return result;
    }

    @Check(test = "minusAddInt3")
    public void checkSubAddInt3(int returnValue) {
        checkSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    @IR(counts = {IRNode.SUB_L, "1"})
    public int minusAddLong3(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 + i`
            result = blackhole(i - inv2 + inv1);
        }
        return result;
    }

    @Check(test = "minusAddLong3")
    public void checkSubAddLong3(int returnValue) {
        checkSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "2"})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int negAddInt(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `-inv2 + inv1 + i`
            result = blackhole(i + inv1 - inv2);
        }
        return result;
    }

    @Check(test = "negAddInt")
    public void checkNegAddInt(int returnValue) {
        checkSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    @IR(counts = {IRNode.SUB_L, "1"})
    public int negAddLong(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `-inv2 + inv1 + i`
            result = blackhole(i + inv1 - inv2);
        }
        return result;
    }

    @Check(test = "negAddLong")
    public void checkNegAddLong(int returnValue) {
        checkSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "2"})
    @IR(counts = {IRNode.SUB_I, "2"})
    public int negSubAddInt(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `-inv1 - inv2 + i`
            result = blackhole(i - inv1 - inv2);
        }
        return result;
    }

    @Check(test = "negSubAddInt")
    public void checkNegSubAddInt(int returnValue) {
        checkNegSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    @IR(counts = {IRNode.SUB_L, "2"})
    public int negSubAddLong(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `-inv1 - inv2 + i`
            result = blackhole(i - inv1 - inv2);
        }
        return result;
    }

    @Check(test = "negSubAddLong")
    public void checkNegSubAddLong(int returnValue) {
        checkNegSubAdd(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "3"})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int addSubInt(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 - i`
            result = blackhole(inv1 + (inv2 - i));
        }
        return result;
    }

    @Check(test = "addSubInt")
    public void checkAddSubInt(int returnValue) {
        checkAddSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    @IR(counts = {IRNode.SUB_L, "1"})
    public int addSubLong(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 - i`
            result = blackhole(inv1 + (inv2 - i));
        }
        return result;
    }

    @Check(test = "addSubLong")
    public void checkAddSubLong(int returnValue) {
        checkAddSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "3"})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int addSubInt2(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 - i`
            result = blackhole(inv1 - (i - inv2));
        }
        return result;
    }

    @Check(test = "addSubInt2")
    public void checkAddSubInt2(int returnValue) {
        checkAddSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    @IR(counts = {IRNode.SUB_L, "1"})
    public int addSubLong2(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 - i`
            result = blackhole(inv1 - (i - inv2));
        }
        return result;
    }

    @Check(test = "addSubLong2")
    public void checkAddSubLong2(int returnValue) {
        checkAddSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "3"})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int addSubInt3(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 - i`
            result = blackhole(inv2 - i + inv1);
        }
        return result;
    }

    @Check(test = "addSubInt3")
    public void checkAddSubInt3(int returnValue) {
        checkAddSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_L, "1"})
    @IR(counts = {IRNode.SUB_L, "1"})
    public int addSubLong3(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 + inv2 - i`
            result = blackhole(inv2 - i + inv1);
        }
        return result;
    }

    @Check(test = "addSubLong3")
    public void checkAddSubLong3(int returnValue) {
        checkAddSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "2"})
    @IR(counts = {IRNode.SUB_I, "2"})
    public int subSubInt(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 - i`
            result = blackhole(inv1 - (i + inv2));
        }
        return result;
    }

    @Check(test = "subSubInt")
    public void checkSubSubInt(int returnValue) {
        checkSubSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.ADD_L})
    @IR(counts = {IRNode.SUB_L, "2"})
    public int subSubLong(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 - i`
            result = blackhole(inv1 - (i + inv2));
        }
        return result;
    }

    @Check(test = "subSubLong")
    public void checkSubSubLong(int returnValue) {
        checkSubSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(counts = {IRNode.ADD_I, "2"})
    @IR(counts = {IRNode.SUB_I, "2"})
    public int subSubInt2(int inv1, int inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 - i`
            result = blackhole(inv1 - i - inv2);
        }
        return result;
    }

    @Check(test = "subSubInt2")
    public void checkSubSubInt2(int returnValue) {
        checkSubSub(returnValue);
    }

    @Test
    @Arguments(setup = "setup")
    @IR(failOn = {IRNode.ADD_L})
    @IR(counts = {IRNode.SUB_L, "2"})
    public int subSubLong2(long inv1, long inv2, int size) {
        int result = -1;
        for (int i = 0; i < size; ++i) {
            // Reassociate to `inv1 - inv2 - i`
            result = blackhole(inv1 - i - inv2);
        }
        return result;
    }

    @Check(test = "subSubLong2")
    public void checkSubSubLong2(int returnValue) {
        checkSubSub(returnValue);
    }
}
