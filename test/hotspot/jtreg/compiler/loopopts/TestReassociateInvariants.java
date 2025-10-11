/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @summary
 * @library /test/lib /
 * @run driver compiler.loopopts.TestReassociateInvariants
 */

package compiler.loopopts;


import compiler.lib.ir_framework.*;

import java.util.Objects;

public class TestReassociateInvariants {
    private static long longStart = 0;
    private static long longStop = 1000;
    private static int intStart = 0;
    private static int intStop = 1000;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-ShortRunningLongLoop");
    }

    @Test
    @IR(counts = {IRNode.ADD_I, "3"})
    @Arguments(values = { Argument.NUMBER_42, Argument.NUMBER_42 })
    public int test1(int a, int b) {
        int v = a + b;
        for (int i = 1; i < 100; i *= 2) {
            v += a + (b + i);
        }
        return v;
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP })
    @IR(counts = { IRNode.LOOP, "1" })
    @Arguments(values = { Argument.NUMBER_42, Argument.NUMBER_42 })
    public void test2(long a, long b) {
        for (long i = longStart; i < longStop; i++) {
            Objects.checkIndex(a + (b + i), Long.MAX_VALUE);
        }
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP })
    @IR(counts = { IRNode.LOOP, "1" })
    @Arguments(values = { Argument.NUMBER_42, Argument.NUMBER_42 })
    public void test3(long a, long b) {
        for (int i = intStart; i < intStop; i++) {
            Objects.checkIndex(a + (b + i), Long.MAX_VALUE);
        }
    }
}
