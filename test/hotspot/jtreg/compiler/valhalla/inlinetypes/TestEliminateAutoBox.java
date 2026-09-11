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

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test that EliminateAutoBox works with value classes.
 * @bug 8328675
 * @library /test/lib /
 * @requires vm.compiler2.enabled & vm.flagless
 * @enablePreview
 * @run driver ${test.main.class}
 */
public class TestEliminateAutoBox {
    private static final int INT_VALUE = 1000;
    private static final long LONG_VALUE = 1_000_000_000_000L;
    private static final double DOUBLE_VALUE = 1234.5D;

    private static final String[] BASE_FLAGS = {
            "--enable-preview",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-XX:CompileCommand=quiet"
    };

    private static final String[] DONT_INLINE_BOXES = {
            "-XX:CompileCommand=dontinline,java.lang.*::valueOf",
            "-XX:CompileCommand=dontinline,java.lang.*::*Value"
    };

    private static final String[] DONT_INLINE_VALUEOF = {
            "-XX:CompileCommand=dontinline,java.lang.*::valueOf",
            "-XX:CompileCommand=inline,java.lang.*::*Value"
    };

    private static final String[] INLINE_VALUEOF = {
            "-XX:CompileCommand=inline,java.lang.*::valueOf",
            "-XX:CompileCommand=inline,java.lang.*::*Value"
    };

    public static void main(String[] args) {
        new TestFramework()
                .addScenarios(
                        scenario(0, "-XX:+InlineTypePassFieldsAsArgs", "-XX:+InlineTypeReturnedAsFields", DONT_INLINE_BOXES),
                        scenario(1, "-XX:-InlineTypePassFieldsAsArgs", "-XX:+InlineTypeReturnedAsFields", DONT_INLINE_BOXES),
                        scenario(2, "-XX:+InlineTypePassFieldsAsArgs", "-XX:-InlineTypeReturnedAsFields", DONT_INLINE_BOXES),
                        scenario(3, "-XX:-InlineTypePassFieldsAsArgs", "-XX:-InlineTypeReturnedAsFields", DONT_INLINE_BOXES),
                        scenario(4, "-XX:-InlineTypePassFieldsAsArgs", "-XX:-InlineTypeReturnedAsFields", DONT_INLINE_VALUEOF),
                        scenario(5, "-XX:+InlineTypePassFieldsAsArgs", "-XX:+InlineTypeReturnedAsFields", INLINE_VALUEOF),
                        scenario(6, "-XX:-InlineTypePassFieldsAsArgs", "-XX:-InlineTypeReturnedAsFields", INLINE_VALUEOF))
                .start();
    }

    private static Scenario scenario(int index, String flag1, String flag2) {
        return scenario(index, flag1, flag2, new String[0]);
    }

    private static Scenario scenario(int index, String flag1, String flag2, String[] extraFlags) {
        String[] flags = new String[BASE_FLAGS.length + 2 + extraFlags.length];
        System.arraycopy(BASE_FLAGS, 0, flags, 0, BASE_FLAGS.length);
        flags[BASE_FLAGS.length] = flag1;
        flags[BASE_FLAGS.length + 1] = flag2;
        System.arraycopy(extraFlags, 0, flags, BASE_FLAGS.length + 2, extraFlags.length);
        return new Scenario(index, flags);
    }

    // Eliminate a scalarized Integer.valueOf result.
    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.ALLOC_OF, "Integer", IRNode.STATIC_CALL_OF_METHOD, "Integer::valueOf"})
    public static Integer testBoxingInt(int i) {
        return Integer.valueOf(i);
    }

    @Run(test = "testBoxingInt")
    public void runBoxingInt() {
        Asserts.assertEQ(testBoxingInt(INT_VALUE), INT_VALUE);
    }

    // Eliminate Integer.intValue from a scalarized receiver argument.
    @Test
    @IR(failOn = {IRNode.STATIC_CALL_OF_METHOD, "Integer::intValue"})
    public static int testUnboxingInt(Integer i) {
        return i.intValue();
    }

    @Run(test = "testUnboxingInt")
    public void runUnboxingInt(RunInfo info) {
        Asserts.assertEQ(testUnboxingInt(Integer.valueOf(INT_VALUE)), INT_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxingInt(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Eliminate an implicit Integer boxing followed by an implicit unboxing.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Integer", IRNode.STATIC_CALL_OF_METHOD, "Integer::valueOf",
                  IRNode.STATIC_CALL_OF_METHOD, "Integer::intValue"})
    public static int testBoxUnboxImplicitInt(int i) {
        Integer res = i;
        return res;
    }

    @Run(test = "testBoxUnboxImplicitInt")
    public void runBoxUnboxImplicitInt() {
        Asserts.assertEQ(testBoxUnboxImplicitInt(INT_VALUE), INT_VALUE);
    }

    // Eliminate an explicit Integer.valueOf followed by intValue.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Integer", IRNode.STATIC_CALL_OF_METHOD, "Integer::valueOf",
                  IRNode.STATIC_CALL_OF_METHOD, "Integer::intValue"})
    public static int testBoxUnboxExplicitInt(int i) {
        Integer res = Integer.valueOf(i);
        return res.intValue();
    }

    @Run(test = "testBoxUnboxExplicitInt")
    public void runBoxUnboxExplicitInt() {
        Asserts.assertEQ(testBoxUnboxExplicitInt(INT_VALUE), INT_VALUE);
    }

    // Eliminate an implicit Integer unbox followed by an implicit box.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Integer", IRNode.STATIC_CALL_OF_METHOD, "Integer::intValue"})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.STATIC_CALL_OF_METHOD, "Integer::valueOf"})
    public static Integer testUnboxBoxImplicitInt(Integer i) {
        int res = i;
        return res;
    }

    @Run(test = "testUnboxBoxImplicitInt")
    public void runUnboxBoxImplicitInt(RunInfo info) {
        Asserts.assertEQ(testUnboxBoxImplicitInt(Integer.valueOf(INT_VALUE)), INT_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxBoxImplicitInt(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Eliminate an explicit Integer.intValue followed by valueOf.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Integer", IRNode.STATIC_CALL_OF_METHOD, "Integer::intValue"})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.STATIC_CALL_OF_METHOD, "Integer::valueOf"})
    public static Integer testUnboxBoxExplicitInt(Integer i) {
        int res = i.intValue();
        return Integer.valueOf(res);
    }

    @Run(test = "testUnboxBoxExplicitInt")
    public void runUnboxBoxExplicitInt(RunInfo info) {
        Asserts.assertEQ(testUnboxBoxExplicitInt(Integer.valueOf(INT_VALUE)), INT_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxBoxExplicitInt(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Eliminate a scalarized Long.valueOf result with a two-slot field.
    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.ALLOC_OF, "Long", IRNode.STATIC_CALL_OF_METHOD, "Long::valueOf"})
    public static Long testBoxingLong(long l) {
        return Long.valueOf(l);
    }

    @Run(test = "testBoxingLong")
    public void runBoxingLong() {
        Asserts.assertEQ(testBoxingLong(LONG_VALUE), LONG_VALUE);
    }

    // Eliminate Long.longValue from a scalarized receiver argument.
    @Test
    @IR(failOn = {IRNode.STATIC_CALL_OF_METHOD, "Long::longValue"})
    public static long testUnboxingLong(Long l) {
        return l.longValue();
    }

    @Run(test = "testUnboxingLong")
    public void runUnboxingLong(RunInfo info) {
        Asserts.assertEQ(testUnboxingLong(Long.valueOf(LONG_VALUE)), LONG_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxingLong(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Eliminate an implicit Long boxing followed by an implicit unboxing.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Long", IRNode.STATIC_CALL_OF_METHOD, "Long::valueOf",
                  IRNode.STATIC_CALL_OF_METHOD, "Long::longValue"})
    public static long testBoxUnboxImplicitLong(long l) {
        Long res = l;
        return res;
    }

    @Run(test = "testBoxUnboxImplicitLong")
    public void runBoxBoxUnboxImpliciLong() {
        Asserts.assertEQ(testBoxUnboxImplicitLong(LONG_VALUE), LONG_VALUE);
    }

    // Eliminate an explicit Long.valueOf followed by longValue.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Long", IRNode.STATIC_CALL_OF_METHOD, "Long::valueOf",
                  IRNode.STATIC_CALL_OF_METHOD, "Long::longValue"})
    public static long testBoxUnboxExplicitLong(long l) {
        Long res = Long.valueOf(l);
        return res.longValue();
    }

    @Run(test = "testBoxUnboxExplicitLong")
    public void runBoxUnboxExplicitLong() {
        Asserts.assertEQ(testBoxUnboxExplicitLong(LONG_VALUE), LONG_VALUE);
    }

    // Eliminate an implicit Long unbox followed by an implicit box.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Long", IRNode.STATIC_CALL_OF_METHOD, "Long::longValue"})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.STATIC_CALL_OF_METHOD, "Long::valueOf"})
    public static Long testUnboxBoxImplicitLong(Long l) {
        long res = l;
        return res;
    }

    @Run(test = "testUnboxBoxImplicitLong")
    public void runUnboxBoxImplicitLong(RunInfo info) {
        Asserts.assertEQ(testUnboxBoxImplicitLong(Long.valueOf(LONG_VALUE)), LONG_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxBoxImplicitLong(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Eliminate an explicit Long.longValue followed by valueOf.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Long", IRNode.STATIC_CALL_OF_METHOD, "Long::longValue"})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.STATIC_CALL_OF_METHOD, "Long::valueOf"})
    public static Long testUnboxBoxExplicitLong(Long l) {
        long res = l.longValue();
        return Long.valueOf(res);
    }

    @Run(test = "testUnboxBoxExplicitLong")
    public void runUnboxBoxExplicitLong(RunInfo info) {
        Asserts.assertEQ(testUnboxBoxExplicitLong(Long.valueOf(LONG_VALUE)), LONG_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxBoxExplicitLong(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Eliminate a scalarized Double.valueOf result with a two-slot field.
    @Test
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.ALLOC_OF, "Double", IRNode.STATIC_CALL_OF_METHOD, "Double::valueOf"})
    public static Double testBoxingDouble(double d) {
        return Double.valueOf(d);
    }

    @Run(test = "testBoxingDouble")
    public void runBoxingDouble() {
        Asserts.assertEQ(testBoxingDouble(DOUBLE_VALUE), DOUBLE_VALUE);
    }

    // Eliminate Double.doubleValue from a scalarized receiver argument.
    @Test
    @IR(failOn = {IRNode.STATIC_CALL_OF_METHOD, "Double::doubleValue"})
    public static double testUnboxingDouble(Double d) {
        return d.doubleValue();
    }

    @Run(test = "testUnboxingDouble")
    public void runUnboxingDouble(RunInfo info) {
        Asserts.assertEQ(testUnboxingDouble(Double.valueOf(DOUBLE_VALUE)), DOUBLE_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxingDouble(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Eliminate an implicit Double boxing followed by an implicit unboxing.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Double", IRNode.STATIC_CALL_OF_METHOD, "Double::valueOf",
                  IRNode.STATIC_CALL_OF_METHOD, "Double::doubleValue"})
    public static double testBoxUnboxImplicitDouble(double d) {
        Double res = d;
        return res;
    }

    @Run(test = "testBoxUnboxImplicitDouble")
    public void runBoxUnboxImplicitDouble() {
        Asserts.assertEQ(testBoxUnboxImplicitDouble(DOUBLE_VALUE), DOUBLE_VALUE);
    }

    // Eliminate an explicit Double.valueOf followed by doubleValue.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Double", IRNode.STATIC_CALL_OF_METHOD, "Double::valueOf",
                  IRNode.STATIC_CALL_OF_METHOD, "Double::doubleValue"})
    public static double testBoxUnboxExplicitDouble(double d) {
        Double res = Double.valueOf(d);
        return res.doubleValue();
    }

    @Run(test = "testBoxUnboxExplicitDouble")
    public void runBoxUnboxExplicitDouble() {
        Asserts.assertEQ(testBoxUnboxExplicitDouble(DOUBLE_VALUE), DOUBLE_VALUE);
    }

    // Eliminate an implicit Double unbox followed by an implicit box.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Double", IRNode.STATIC_CALL_OF_METHOD, "Double::doubleValue"})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.STATIC_CALL_OF_METHOD, "Double::valueOf"})
    public static Double testUnboxBoxImplicitDouble(Double d) {
        double res = d;
        return res;
    }

    @Run(test = "testUnboxBoxImplicitDouble")
    public void runUnboxBoxImplicitDouble(RunInfo info) {
        Asserts.assertEQ(testUnboxBoxImplicitDouble(Double.valueOf(DOUBLE_VALUE)), DOUBLE_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxBoxImplicitDouble(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }

    // Eliminate an explicit Double.doubleValue followed by valueOf.
    @Test
    @IR(failOn = {IRNode.ALLOC_OF, "Double", IRNode.STATIC_CALL_OF_METHOD, "Double::doubleValue"})
    @IR(applyIf = {"InlineTypeReturnedAsFields", "true"},
        failOn = {IRNode.STATIC_CALL_OF_METHOD, "Double::valueOf"})
    public static Double testUnboxBoxExplicitDouble(Double d) {
        double res = d.doubleValue();
        return Double.valueOf(res);
    }

    @Run(test = "testUnboxBoxExplicitDouble")
    public void runUnboxBoxExplicitDouble(RunInfo info) {
        Asserts.assertEQ(testUnboxBoxExplicitDouble(Double.valueOf(DOUBLE_VALUE)), DOUBLE_VALUE);
        if (!info.isWarmUp()) {
            try {
                testUnboxBoxExplicitDouble(null);
                throw new RuntimeException("No NPE thrown");
            } catch (NullPointerException e) {
                // Expected
            }
        }
    }
}
