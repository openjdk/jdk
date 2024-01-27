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

/*
 * @test
 * @bug 8323220
 * @summary Test loop invariant code motion for cmp nodes through reassociation
 * @library /test/lib /
 * @run driver compiler.c2.loopopts.InvariantCodeMotionReassociateCmp
 */
public class InvariantCodeMotionReassociateCmp {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    private void blackhole() {}

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.SUB_I, "1"})
    public void equalsAddInt(int inv1, int inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv2 - inv1 == i`
            if (inv1 + i == inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.SUB_L, "1"})
    public void equalsAddLong(long inv1, long inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv2 - inv1 == i`
            if (inv1 + i == inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.SUB_I, "1"})
    public void equalsInvariantSubVariantInt(int inv1, int inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv1 - inv2 == i`
            if (inv1 - i == inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.SUB_L, "1"})
    public void equalsInvariantSubVariantLong(long inv1, long inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv1 - inv2 == i`
            if (inv1 - i == inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.ADD_I, "2"})
    public void equalsVariantSubInvariantInt(int inv1, int inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv1 + inv2 == i`
            if (i - inv1 == inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.ADD_L, "1"})
    public void equalsVariantSubInvariantLong(long inv1, long inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv1 + inv2 == i`
            if (i - inv1 == inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.SUB_I, "1"})
    public void notEqualsAddInt(int inv1, int inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv1 - inv2 != i`
            if (inv1 + i != inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.SUB_L, "1"})
    public void notEqualsAddLong(long inv1, long inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv1 - inv2 != i`
            if (inv1 + i != inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.SUB_I, "1"})
    public void notEqualsInvariantSubVariantInt(int inv1, int inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv2 - inv1 != i`
            if (inv1 - i != inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(counts = {IRNode.SUB_L, "1"})
    public void notEqualsInvariantSubVariantLong(long inv1, long inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv2 - inv1 != i`
            if (inv1 - i != inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.ADD_I, "2"})
    public void notEqualsVariantSubInvariantInt(int inv1, int inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv1 + inv2 != i`
            if (i - inv1 != inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.ADD_L, "1"})
    public void notEqualsVariantSubInvariantLong(long inv1, long inv2) {
        for (int i = 0; i < 500; ++i) {
            // Reassociate to `inv1 + inv2 != i`
            if (i - inv1 != inv2) {
                blackhole();
            }
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.NUMBER_42})
    @IR(failOn = {IRNode.SUB_I})
    public int leDontReassociate(int inv1, int inv2) {
        int i = 0;
        for (; i < 500; ++i) {
            if (inv1 + i <= inv2) {
                blackhole();
                break;
            }
        }
        return i;
    }

    @Check(test = "leDontReassociate")
    public void checkLeDontReassociate(int returnValue, TestInfo info) {
        if (returnValue != 0) {
            throw new RuntimeException("Illegal reassociation");
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.MIN})
    @IR(failOn = {IRNode.SUB_I})
    public int gtDontReassociate(int inv1, int inv2) {
        int i = 0;
        for (; i < 500; ++i) {
            if (inv1 + i > inv2) {
                blackhole();
                break;
            }
        }
        return i;
    }

    @Check(test = "gtDontReassociate")
    public void checkGtDontReassociate(int returnValue, TestInfo info) {
        if (returnValue != 0) {
            throw new RuntimeException("Illegal reassociation");
        }
    }

    @Test
    @Arguments({Argument.NUMBER_42, Argument.MIN})
    @IR(failOn = {IRNode.SUB_I})
    public int geDontReassociate(int inv1, int inv2) {
        int i = 0;
        for (; i < 500; ++i) {
            if (inv1 + i >= inv2) {
                blackhole();
                break;
            }
        }
        return i;
    }

    @Check(test = "geDontReassociate")
    public void checkGeDontReassociate(int returnValue, TestInfo info) {
        if (returnValue != 0) {
            throw new RuntimeException("Illegal reassociation");
        }
    }

}

