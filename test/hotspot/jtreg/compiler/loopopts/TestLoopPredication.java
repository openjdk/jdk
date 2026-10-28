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
package compiler.loopopts;

import compiler.lib.ir_framework.*;
import java.util.Objects;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8395711
 * @summary Verify the expected behavior of loop predication.
 * @requires vm.compiler2.enabled
 * @requires vm.flagless
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestLoopPredication {
    public static void main(String[] args) {
        var framework = new TestFramework();
        // Since each loop runs around 100 iterations, we need to limit the warmup invocations in a
        // way that still ensures adequate profiling
        framework.setDefaultWarmup(20);
        framework.addFlags("-XX:-UseOnStackReplacement");
        framework.start();
    }

    // Init check fails
    // After loop predication, we have 1 zero-trip If, 1 IC loop predicated If, 2 RC loop
    // predicated Ifs, 2 Template Assertion Predicate Ifs, 1 loop-limit If, 1 If in the loop that
    // is going to be removed in the next IGVN.
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC1(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = 1;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC1")
    public void runIntRC1(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC1(0, 100, 0, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC1(0, 100, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            // Normally, the zero-trip guard would protect the cases when init_iv > final_iv
            Asserts.assertEQ(0, testIntRC1(0, 0, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC1(-1, 100, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    // Final check fails
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC2(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = 1;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC2")
    public void runIntRC2(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC2(0, 100, 0, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC2(0, 100, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC2(0, 101, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    // Final check fails, lhs is outside the int range
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC3(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = 100;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC3")
    public void runIntRC3(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC3(0, 100, 0, 10000));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC3(0, 100, 0, 10000));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC3(Integer.MAX_VALUE >> 2, (Integer.MAX_VALUE >> 2) + 100, 0, 10000));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    // The multiplication overflows, but the addition cancels that
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC4(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = 2;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC4")
    public void runIntRC4(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC4(Integer.MIN_VALUE >>> 1, (Integer.MIN_VALUE >>> 1) + 100, Integer.MIN_VALUE, 200));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC4(Integer.MIN_VALUE >>> 1, (Integer.MIN_VALUE >>> 1) + 100, Integer.MIN_VALUE, 200));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC4((Integer.MIN_VALUE >>> 1) - 1, (Integer.MIN_VALUE >>> 1) + 100, Integer.MIN_VALUE, 200));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    // Same as the previous one, but check the other bound
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC5(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = 2;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC5")
    public void runIntRC5(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC5(Integer.MIN_VALUE >>> 1, (Integer.MIN_VALUE >>> 1) + 100, Integer.MIN_VALUE, 200));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC5(Integer.MIN_VALUE >>> 1, (Integer.MIN_VALUE >>> 1) + 100, Integer.MIN_VALUE, 200));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC5((Integer.MIN_VALUE >>> 1), (Integer.MIN_VALUE >>> 1) + 101, Integer.MIN_VALUE, 200));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    // The range check passes, but the check generated by loop predication fails
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC6(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = 6;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC6")
    public void runIntRC6(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC6(Integer.MIN_VALUE >>> 1, (Integer.MIN_VALUE >>> 1) + 100, Integer.MIN_VALUE, 600));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC6(Integer.MIN_VALUE >>> 1, (Integer.MIN_VALUE >>> 1) + 100, Integer.MIN_VALUE, 600));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    // The do-while loop does not have a zero-trip guard, so init_iv and final_iv may not be
    // ordered correctly
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC7(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = 1;
        int i = begin;
        int res = 1;
        do {
            // Prevent the parser from rotating the loop, keep it free of zero-trip guard
            if (begin < 0) {
                res = 2;
            }
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return res;
            }
            i += stride;
        } while (i < end);

        return 0;
    }

    @Run(test = "testIntRC7")
    public void runIntRC7(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC7(0, 100, 0, 100));
            Asserts.assertEQ(0, testIntRC7(-1, 99, 1, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC7(0, 100, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC7(100, 100, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC8(int begin, int end, int offset, int range) {
        int stride = -1;
        int scale = 1;
        for (int i = begin; i > end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC8")
    public void runIntRC8(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC8(99, -1, 0, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC8(99, -1, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC8(100, -1, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC9(int begin, int end, int offset, int range) {
        int stride = -1;
        int scale = 1;
        for (int i = begin; i > end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC9")
    public void runIntRC9(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC9(99, -1, 0, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC9(99, -1, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC9(99, -2, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC10(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = -1;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC10")
    public void runIntRC10(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC10(-99, 1, 0, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC10(-99, 1, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC10(-100, 1, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC11(int begin, int end, int offset, int range) {
        int stride = 1;
        int scale = -1;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC11")
    public void runIntRC11(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC11(-99, 1, 0, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC11(-99, 1, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC11(-99, 2, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC12(int begin, int end, int offset, int range) {
        int stride = -1;
        int scale = -1;
        for (int i = begin; i > end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC12")
    public void runIntRC12(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC12(0, -100, 0, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC12(0, -100, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC12(1, -100, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC13(int begin, int end, int offset, int range) {
        int stride = -1;
        int scale = -1;
        for (int i = begin; i > end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC13")
    public void runIntRC13(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC13(0, -100, 0, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC13(0, -100, 0, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC13(0, -101, 0, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    // Large stride and scale, there is another loop-limit check as the 10-th If
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "9"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC14(int begin, int end, int offset, int range) {
        int stride = 100;
        int scale = 100;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC14")
    public void runIntRC14(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC14(0, 10000, 0, 1000000));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC14(0, 10001, 0, 1000001));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC14(0, 10001, 0, 1000000));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "9"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC15(int begin, int end, int offset, int range) {
        int stride = -100;
        int scale = -100;
        for (int i = begin; i > end; i += stride) {
            try {
                Objects.checkIndex(i * scale + offset, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC15")
    public void runIntRC15(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC15(0, -10000, 0, 1000000));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC15(0, -10001, 0, 1000001));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC15(0, -10001, 0, 1000000));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    // Because i * scale does not overflow, we can use int arithmetic
    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    @IR(failOn = {IRNode.CMP_L, IRNode.CMP_UL}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC16(int begin, int end, int range) {
        int stride = 1;
        int scale = 1;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC16")
    public void runIntRC16(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC16(0, 100, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC16(0, 100, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC16(-1, 100, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.IF, "8"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    @IR(failOn = {IRNode.CMP_L, IRNode.CMP_UL}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC17(int begin, int end, int range) {
        int stride = 1;
        int scale = 1;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC17")
    public void runIntRC17(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC17(0, 100, 100));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC17(0, 100, 100));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC17(0, 101, 100));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }

    @Test
    @IR(failOn = {IRNode.LOOP, IRNode.COUNTED_LOOP})
    // There is no loop-limit check, so there are only 8 Ifs
    @IR(counts = {IRNode.IF, "7"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    // 1 CmpL for final_iv and 1 for OpaqueInit in the Template Assertion Predicate
    @IR(counts = {IRNode.CMP_UL, "2"}, phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    private static int testIntRC18(int begin, int end, int range) {
        int stride = 1;
        int scale = 4;
        begin &= 0xFF;
        end &= 0xFF;
        for (int i = begin; i < end; i += stride) {
            try {
                Objects.checkIndex(i * scale, range);
            } catch (IndexOutOfBoundsException _) {
                return 1;
            }
        }

        return 0;
    }

    @Run(test = "testIntRC18")
    public void runIntRC18(RunInfo info) {
        if (info.isWarmUp()) {
            Asserts.assertEQ(0, testIntRC18(0, 100, 400));
        } else {
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(0, testIntRC18(0, 100, 400));
            Asserts.assertTrue(info.isTestC2Compiled());
            Asserts.assertEQ(1, testIntRC18(0, 101, 400));
            Asserts.assertFalse(info.isTestC2Compiled());
        }
    }
}
