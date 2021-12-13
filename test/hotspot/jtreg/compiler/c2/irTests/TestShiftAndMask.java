/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
 * @bug 8277850
 * @summary C2: optimize mask checks in counted loops
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestShiftAndMask
 */

public class TestShiftAndMask {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = { IRNode.AND_I, IRNode.LSHIFT_I })
    public static int shiftMaskInt(int i) {
        return (i << 2) & 3; // transformed to: return 0;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_L })
    public static long shiftMaskLong(long i) {
        return (i << 2) & 3; // transformed to: return 0;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = { IRNode.AND_I, "1" })
    @IR(failOn = { IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftMaskInt(int i, int j) {
        return (j + (i << 2)) & 3; // transformed to: return j & 3;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftMaskLong(long i, long j) {
        return (j + (i << 2)) & 3; // transformed to: return j & 3;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_I, IRNode.ADD_I, IRNode.LSHIFT_I })
    public static int addShiftMaskInt2(int i, int j) {
        return ((j << 2) + (i << 2)) & 3; // transformed to: return 0;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftMaskLong2(long i, long j) {
        return ((j << 2) + (i << 2)) & 3; // transformed to: return 0;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = { IRNode.AND_L, IRNode.LSHIFT_I })
    public static long shiftConvMask(int i) {
        return ((long)(i << 2)) & 3; // transformed to: return 0;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = { IRNode.AND_L, "1" })
    @IR(failOn = { IRNode.ADD_L, IRNode.LSHIFT_I, IRNode.CONV_I2L })
    public static long addShiftConvMask(int i, long j) {
        return (j + (i << 2)) & 3; // transformed to: return j & 3;
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = { IRNode.AND_L, IRNode.ADD_L, IRNode.LSHIFT_L })
    public static long addShiftConvMask2(int i, int j) {
        return (((long)(j << 2)) + ((long)(i << 2))) & 3; // transformed to: return 0;
    }

}

