/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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


package org.graalvm.compiler.core.aarch64.test;

import org.graalvm.compiler.lir.LIRInstruction;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64RotationTest extends AArch64MatchRuleTest {

    private static final Predicate<LIRInstruction> ROR_PRED = op -> op.name().equals("ROR");
    private static final Predicate<LIRInstruction> RORV_PRED = op -> op.name().equals("RORV");
    private static final Predicate<LIRInstruction> NEG_PRED = op -> op.name().equals("NEG");
    private static final int CONST = 10;

    private static final int[] intInput = {-1, 0, 0x12, 0x23, 0x34, 0x45, 0xf1, 0xf2, Integer.MAX_VALUE, Integer.MIN_VALUE};
    private static final long[] longInput = {-1, 0, 0x1234, 0x2345, 0x3456, 0xdead, 0xbeaf, Long.MAX_VALUE, Long.MIN_VALUE};

    // ror expander
    public int rorIntC0(int x, int shift) {
        // same as `x >>> shift | x << (0 - shift)`
        return x >>> shift | x << (-shift);
    }

    public int rorIntC32(int x, int shift) {
        return x >>> shift | x << (32 - shift);
    }

    public int rorIntC32Add(int x, int shift) {
        return x >>> -shift | x << (32 + shift);
    }

    public long rorLongC0(long x, int shift) {
        return x >>> shift | x << (-shift);
    }

    public long rorLongC64(long x, int shift) {
        return x >>> shift | x << (64 - shift);
    }

    public long rorLongC64Add(long x, int shift) {
        return x >>> -shift | x << (64 + shift);
    }

    @Test
    public void testRorExpand() {
        final String[] intCases = {"rorIntC0", "rorIntC32", "rolIntC32Add"};
        for (String name : intCases) {
            for (int shift = 0; shift <= Integer.SIZE; shift++) {
                for (int value : intInput) {
                    test(name, value, shift);
                    checkLIR(name, RORV_PRED, 1);
                }
            }
        }

        final String[] longCases = {"rorLongC0", "rorLongC64", "rolLongC64Add"};
        for (String name : longCases) {
            for (int shift = 0; shift <= Long.SIZE; shift++) {
                for (long value : longInput) {
                    test(name, value, shift);
                    checkLIR(name, RORV_PRED, 1);
                }
            }
        }
    }

    // rol expander
    public int rolIntC0(int x, int shift) {
        return x << shift | x >>> (-shift);
    }

    public int rolIntC32(int x, int shift) {
        return x << shift | x >>> (32 - shift);
    }

    public int rolIntC32Add(int x, int shift) {
        return x << -shift | x >>> (32 + shift);
    }

    public long rolLongC0(long x, int shift) {
        return x << shift | x >>> (-shift);
    }

    public long rolLongC64(long x, int shift) {
        return x << shift | x >>> (64 - shift);
    }

    public long rolLongC64Add(long x, int shift) {
        return x << -shift | x >>> (64 + shift);
    }

    @Test
    public void testRolExpand() {
        final String[] intCases = {"rolIntC0", "rolIntC32", "rorIntC32Add"};
        for (String name : intCases) {
            for (int shift = 0; shift <= Integer.SIZE; shift++) {
                for (int value : intInput) {
                    test(name, value, shift);
                    checkLIR(name, RORV_PRED, 1);
                    checkLIR(name, NEG_PRED, 1);
                }
            }
        }

        final String[] longCases = {"rolLongC0", "rolLongC64", "rorLongC64Add"};
        for (String name : longCases) {
            for (int shift = 0; shift <= Long.SIZE; shift++) {
                for (long value : longInput) {
                    test(name, value, shift);
                    checkLIR(name, RORV_PRED, 1);
                    checkLIR(name, NEG_PRED, 1);
                }
            }
        }
    }

    // rotation const
    public int rorInt0Const(int x) {
        return x >>> CONST | x << (0 - CONST);
    }

    public int rorInt0ConstAdd(int x) {
        return (x >>> CONST) + (x << (0 - CONST));
    }

    public int rorInt32Const(int x) {
        return x >>> CONST | x << (32 - CONST);
    }

    public int rorInt32ConstAdd(int x) {
        return (x >>> CONST) + (x << (32 - CONST));
    }

    public int rolInt0Const(int x) {
        return x << CONST | x >>> (0 - CONST);
    }

    public int rolInt0ConstAdd(int x) {
        return (x << CONST) + (x >>> (0 - CONST));
    }

    public int rolInt32Const(int x) {
        return x << CONST | x >>> (32 - CONST);
    }

    public int rolInt32ConstAdd(int x) {
        return (x << CONST) + (x >>> (32 - CONST));
    }

    public long rolLong0Const(long x) {
        return x << CONST | x >>> (0 - CONST);
    }

    public long rolLong0ConstAdd(long x) {
        return (x << CONST) + (x >>> (0 - CONST));
    }

    public long rolLong64Const(long x) {
        return x << CONST | x >>> (64 - CONST);
    }

    public long rolLong64ConstAdd(long x) {
        return (x << CONST) + (x >>> (64 - CONST));
    }

    public long rorLong0Const(long x) {
        return x >>> CONST | x << (0 - CONST);
    }

    public long rorLong0ConstAdd(long x) {
        return (x >>> CONST) + (x << (0 - CONST));
    }

    public long rorLong64Const(long x) {
        return x >>> CONST | x << (64 - CONST);
    }

    public long rorLong64ConstAdd(long x) {
        return (x >>> CONST) + (x << (64 - CONST));
    }

    @Test
    public void testRotationConst() {
        final String[] intCases = {"rolInt0Const",
                        "rolInt0ConstAdd",
                        "rolInt32Const",
                        "rolInt32ConstAdd",
                        "rorInt0Const",
                        "rorInt0ConstAdd",
                        "rorInt32Const",
                        "rorInt32ConstAdd"};
        for (String name : intCases) {
            for (int value : intInput) {
                test(name, value);
                checkLIR(name, ROR_PRED, 1);
            }
        }

        final String[] longCases = {"rolLong0Const",
                        "rolLong0ConstAdd",
                        "rolLong64Const",
                        "rolLong64ConstAdd",
                        "rorLong0Const",
                        "rorLong0ConstAdd",
                        "rorLong64Const",
                        "rorLong64ConstAdd"};
        for (String name : longCases) {
            for (long value : longInput) {
                test(name, value);
                checkLIR(name, ROR_PRED, 1);
            }
        }
    }
}
