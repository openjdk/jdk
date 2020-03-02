/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited. All rights reserved.
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
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp.BinaryConstOp;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64MergeNarrowWithExtendTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> PRED_AND = op -> (op instanceof BinaryConstOp && op.name().toUpperCase().equals("AND"));

    private static final long[] VALUES = {-1L, 0L, 0x1234567812345678L, 0xFFFFFFFFL, 0x12L, 0x1234L, Long.MIN_VALUE, Long.MAX_VALUE};

    public long l2i2l(long x) {
        return (int) x;
    }

    public long l2s2l(long x) {
        return (short) x;
    }

    public int l2s2i(long x) {
        return (short) x;
    }

    public long l2b2l(long x) {
        return (byte) x;
    }

    public int l2b2i(long x) {
        return (byte) x;
    }

    @Test
    public void testSignedExtendedNarrow() {
        String[] testCases = {"l2i2l", "l2i2l", "l2s2l", "l2s2i", "l2b2l", "l2b2i"};
        for (String fname : testCases) {
            for (long value : VALUES) {
                test(fname, value);
                checkLIR(fname, PRED_AND, 0);
            }
        }
    }

    public long l2c2l(long x) {
        return (char) x;
    }

    public int l2c2i(long x) {
        return (char) x;
    }

    @Test
    public void testZeroExtendedNarrow() {
        String[] testCases = {"l2c2l", "l2c2i"};
        for (String fname : testCases) {
            for (long value : VALUES) {
                test(fname, value);
                checkLIR(fname, PRED_AND, 1);
            }
        }
    }
}
