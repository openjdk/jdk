/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Zicond conditional arithmetic and logical instruction sequences
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*zicond.*"
 * @requires vm.debug & vm.compiler2.enabled
 * @run driver compiler.c2.riscv64.TestZicondConditionalArithmetic
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.*;

public class TestZicondConditionalArithmetic {
    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags("-XX:+UseCMoveUnconditionally", "-XX:-UseRVV",
                           "-XX:-UseSuperWord");
        framework.addScenarios(new Scenario(0, "-XX:+UseZicond"),
                               new Scenario(1, "-XX:-UseZicond"));
        framework.setDefaultWarmup(1000);
        framework.start();
    }

    private static final String CMOVE_I_ZERO_COND = "cmovI_cmpI_zero_cond";
    private static final String CMOVE_I_LONG_ZERO_COND = "cmovI_cmpL_zero_cond";
    private static final String CMOVE_L_ZERO_COND = "cmovL_cmpL_zero_cond";
    private static final String CMOVE_L_INT_ZERO_COND = "cmovL_cmpI_zero_cond";

    @Test
    @IR(counts = {CMOVE_I_ZERO_COND, "1"}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "true"})
    @IR(failOn = {CMOVE_I_ZERO_COND}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "false"})
    static int addEqI(int base, int value, int condition) {
        return condition == 0 ? base + value : base;
    }

    @Test
    @IR(counts = {CMOVE_I_ZERO_COND, "1"}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "true"})
    static int subNeI(int base, int value, int condition) {
        return condition != 0 ? base : base - value;
    }

    @Test
    @IR(counts = {CMOVE_I_LONG_ZERO_COND, "1"}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "true"})
    @IR(failOn = {CMOVE_I_LONG_ZERO_COND}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "false"})
    static int orEqILongCondition(int base, int value, long condition) {
        return condition == 0 ? value | base : base;
    }

    @Test
    @IR(counts = {CMOVE_I_LONG_ZERO_COND, "1"}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "true"})
    static int xorNeILongCondition(int base, int value, long condition) {
        return condition != 0 ? base : value ^ base;
    }

    @Test
    @IR(counts = {CMOVE_L_INT_ZERO_COND, "1"}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "true"})
    @IR(failOn = {CMOVE_L_INT_ZERO_COND}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "false"})
    static long andEqLIntCondition(long base, long value, int condition) {
        return condition == 0 ? base & value : base;
    }

    @Test
    @IR(counts = {CMOVE_L_INT_ZERO_COND, "1"}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "true"})
    static long addNeLIntCondition(long base, long value, int condition) {
        return condition != 0 ? base : base + value;
    }

    @Test
    @IR(counts = {CMOVE_L_ZERO_COND, "1"}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "true"})
    @IR(failOn = {CMOVE_L_ZERO_COND}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "false"})
    static long subEqL(long base, long value, long condition) {
        return condition == 0 ? base - value : base;
    }

    @Test
    @IR(counts = {CMOVE_L_ZERO_COND, "1"}, phase = CompilePhase.MATCHING, applyIf = {"UseZicond", "true"})
    static long andNeL(long base, long value, long condition) {
        return condition != 0 ? base : value & base;
    }

    @Test
    @IR(failOn = {CMOVE_I_ZERO_COND}, phase = CompilePhase.MATCHING)
    static int sharedOperationI(int base, int value, int condition) {
        int operation = base + value;
        return (condition == 0 ? operation : base) + operation;
    }

    @Test
    @IR(failOn = {CMOVE_I_ZERO_COND}, phase = CompilePhase.MATCHING)
    static int compareNonZeroI(int base, int value, int condition) {
        return condition == 42 ? base + value : base;
    }

    @Test
    @IR(failOn = {CMOVE_I_ZERO_COND}, phase = CompilePhase.MATCHING)
    static int valueBaseSubI(int base, int value, int condition) {
        return condition == 0 ? value - base : base;
    }

    @Test
    @IR(failOn = {CMOVE_I_ZERO_COND}, phase = CompilePhase.MATCHING)
    static int unsupportedMulI(int base, int value, int condition) {
        return condition == 0 ? base * value : base;
    }

    @Run(test = {"addEqI", "subNeI", "orEqILongCondition", "xorNeILongCondition",
                 "andEqLIntCondition", "addNeLIntCondition", "subEqL", "andNeL",
                 "sharedOperationI", "compareNonZeroI", "valueBaseSubI",
                 "unsupportedMulI"})
    static void run() {
        int[] basesI = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE,
                        0x5555_5555, 0xaaaa_aaaa, 123_456_789};
        int[] valuesI = {0, -1, 1, Integer.MAX_VALUE, Integer.MIN_VALUE,
                         0xaaaa_aaaa, 0x5555_5555, -123_456_789};
        int[] conditionsI = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 42, 0, -42};
        long[] basesL = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
                         0x5555_5555_5555_5555L, 0xaaaa_aaaa_aaaa_aaaaL, 123_456_789L};
        long[] valuesL = {0L, -1L, 1L, Long.MAX_VALUE, Long.MIN_VALUE,
                          0xaaaa_aaaa_aaaa_aaaaL, 0x5555_5555_5555_5555L, -123_456_789L};
        long[] conditionsL = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
                              42L, 0L, 0x1_0000_0000L};

        for (int i = 0; i < basesI.length; i++) {
            int baseI = basesI[i];
            int valueI = valuesI[i];
            int conditionI = conditionsI[i];
            long baseL = basesL[i];
            long valueL = valuesL[i];
            long conditionL = conditionsL[i];

            check(addEqI(baseI, valueI, conditionI), conditionI == 0 ? baseI + valueI : baseI);
            check(subNeI(baseI, valueI, conditionI), conditionI != 0 ? baseI : baseI - valueI);
            check(orEqILongCondition(baseI, valueI, conditionL),
                  conditionL == 0 ? valueI | baseI : baseI);
            check(xorNeILongCondition(baseI, valueI, conditionL),
                  conditionL != 0 ? baseI : valueI ^ baseI);
            check(andEqLIntCondition(baseL, valueL, conditionI),
                  conditionI == 0 ? baseL & valueL : baseL);
            check(addNeLIntCondition(baseL, valueL, conditionI),
                  conditionI != 0 ? baseL : baseL + valueL);
            check(subEqL(baseL, valueL, conditionL), conditionL == 0 ? baseL - valueL : baseL);
            check(andNeL(baseL, valueL, conditionL), conditionL != 0 ? baseL : valueL & baseL);

            int operation = baseI + valueI;
            check(sharedOperationI(baseI, valueI, conditionI),
                  (conditionI == 0 ? operation : baseI) + operation);
            check(compareNonZeroI(baseI, valueI, conditionI),
                  conditionI == 42 ? baseI + valueI : baseI);
            check(valueBaseSubI(baseI, valueI, conditionI),
                  conditionI == 0 ? valueI - baseI : baseI);
            check(unsupportedMulI(baseI, valueI, conditionI),
                  conditionI == 0 ? baseI * valueI : baseI);
        }
    }

    private static void check(int actual, int expected) {
        if (actual != expected) {
            throw new RuntimeException(actual + " != " + expected);
        }
    }

    private static void check(long actual, long expected) {
        if (actual != expected) {
            throw new RuntimeException(actual + " != " + expected);
        }
    }
}
