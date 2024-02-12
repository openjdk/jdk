/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
/*
 * @test
 * @bug 8304487
 * @summary Compiler Implementation for Primitive types in patterns, instanceof, and switch (Preview)
 * @enablePreview
 * @compile PrimitivePatternsSwitch.java
 * @run main/othervm PrimitivePatternsSwitch
 */
public class PrimitivePatternsSwitch {
    public static void main(String[] args) {
        assertEquals(1,  primitiveSwitch(42));
        assertEquals(2,  primitiveSwitch(123));
        assertEquals(1,  primitiveSwitchUnnamed(42));
        assertEquals(2,  primitiveSwitchUnnamed(123));
        assertEquals(42, primitiveSwitch2());
        assertEquals(42, primitiveSwitch3());
        assertEquals(1,  primitiveSwitch4(0.0f));
        assertEquals(2,  primitiveSwitch4(1.0f));
        assertEquals(1,  primitiveSwitchUnconditionallyExact(Byte.MAX_VALUE));
        assertEquals(42, exhaustive0());
        assertEquals(1,  exhaustive1WithDefault());
        assertEquals(2,  exhaustive2WithDefault());
        assertEquals(1,  exhaustive1());
        assertEquals(1,  exhaustive2());
        assertEquals(1,  exhaustive3());
        assertEquals(1,  exhaustive4());
        assertEquals(2,  exhaustive5());
        assertEquals(1,  exhaustive6());
        assertEquals(1,  exhaustive7(true));
        assertEquals(1,  exhaustive7s(true));
        assertEquals(1,  exhaustive8(true));
        assertEquals(1,  exhaustive9(true));
        assertEquals(1,  exhaustive9(false));
        assertEquals(1,  exhaustiveWithRecords1());
        assertEquals(1,  exhaustiveWithRecords2());
        assertEquals(1,  exhaustiveWithRecords4());
        assertEquals(1,  exhaustiveWithRecords5());
        assertEquals(1,  exhaustiveWithRecords6());
        assertEquals(2,  ensureProperSelectionWithRecords());
        assertEquals(1,  ensureProperSelectionWithRecords2());
        assertEquals(3,  ensureProperSelectionWithRecords3());
        assertEquals(42, switchAndDowncastFromObjectPrimitive());
        assertEquals(42, dominationBetweenBoxedAndPrimitive());
        assertEquals(2,  wideningAndUnboxing());
        assertEquals(2,  wideningAndUnboxingInRecord());
        assertEquals(2,  wideningAndInferredUnboxingInRecord());
        assertEquals(5f, switchOverBoxedFloat(0f));
        assertEquals(7f, switchOverBoxedFloat(1f));
        assertEquals(9f, switchOverBoxedFloat(2f));
        assertEquals(9f, switchOverBoxedFloat(2f));
        assertEquals(5f, switchOverPrimitiveDouble(0d));
        assertEquals(7f, switchOverPrimitiveDouble(1d));
        assertEquals(9f, switchOverPrimitiveDouble(2d));
        assertEquals(1, switchOverPrimitiveChar('a'));
        assertEquals(-1, switchOverPrimitiveChar('x'));
        assertTrue(switchOverBoxedBooleanWithUnconditional(Boolean.valueOf(true)));
        assertTrue(switchOverBoxedBooleanWithUnconditional(true));
        assertTrue(!switchOverBoxedBooleanWithUnconditional(false));
        assertEquals(1, switchOverPrimitiveBooleanWithDefault(true));
        assertEquals(2, switchOverPrimitiveBooleanWithDefault(false));
        assertEquals(1, switchOverPrimitiveBoolean(true));
        assertEquals(2, switchOverPrimitiveBoolean(false));
        assertEquals(1, switchOverPrimitiveFloat(0.0f/0.0f));
        assertEquals(2, switchOverPrimitiveFloat((float) Math.pow(0.0f/0.0f, 0)));
        assertEquals(3, switchOverPrimitiveFloat(0.0f));
        assertEquals(4, switchOverPrimitiveFloat(-0.0f));
        assertEquals(1, switchRedirectedExactnessMethods1('a'));
        assertEquals(-1, switchRedirectedExactnessMethods1('\u03A9'));
        assertEquals(1, switchRedirectedExactnessMethods2('\u03A9'));
        assertEquals(-1, switchRedirectedExactnessMethods2('\uFFFF'));
        assertEquals(1, switchLongAndUnconditional(32778L));
        assertEquals(2, switchLongAndUnconditional(42L));
        assertEquals(1, switchByte((byte) 128));
        assertEquals(2, switchByte((byte) 42));
        assertEquals(1, switchShort((short) 32778));
        assertEquals(2, switchShort((short) 42));
        assertEquals(1, switchInt(32778));
        assertEquals(2, switchInt(42));
        assertEquals(1, switchChar( '\u0010'));
        assertEquals(2, switchChar('a'));
        assertEquals(1, testIntInNonEnhancedSwitchStatement(1));
        assertEquals(0, testIntInNonEnhancedSwitchStatement(0));
        assertEquals(1, testFloatInEnhancedSwitchStatement(1.0f));
        assertEquals(0, testFloatInEnhancedSwitchStatement(0.0f));
        assertEquals(1, testDoubleInEnhancedSwitchStatement(1.0d));
        assertEquals(0, testDoubleInEnhancedSwitchStatement(0.0d));
        assertEquals(1, testLongInEnhancedSwitchStatement(1l));
        assertEquals(0, testLongInEnhancedSwitchStatement(0l));
        assertEquals(1, testBooleanInEnhancedSwitchStatement(true));
        assertEquals(0, testBooleanInEnhancedSwitchStatement(false));
        assertEquals(1, testByteWrapperToIntUnconditionallyExact());
        assertEquals(1, testIntegerWrapperToFloat());
        assertEquals(-1, testIntegerWrapperToFloatInexact());
    }

    public static int primitiveSwitch(int i) {
        return switch (i) {
            case int j when j == 42-> 1;
            case int j -> 2;
        };
    }

    public static int primitiveSwitchUnnamed(int i) {
        return switch (i) {
            case int _ when i == 42-> 1;
            case int _ -> 2;
        };
    }

    public static int primitiveSwitch2() {
        Object o = Integer.valueOf(42);
        switch (o) {
            case int i: return i;
            default: break;
        }
        return -1;
    }

    public static int primitiveSwitch3() {
        int i = 42;
        switch (i) {
            case Integer ii: return ii;
        }
    }

    public static int primitiveSwitch4(float f) {
        return switch (f) {
            case 0.0f -> 1;
            case Float fi when fi == 1f -> 2;
            case Float fi -> 3;
        };
    }

    public static int primitiveSwitchUnconditionallyExact(byte c) {
        return switch (c) {
            case short _ -> 1;
        };
    }

    public static int exhaustive0() {
        Integer i = 42;
        switch (i) {
            case int j: return j;
        }
    }

    public static int exhaustive1WithDefault() {
        int i = 42;
        return switch (i) {
            case byte  b -> 1;
            default -> 2;
        };
    }

    public static int exhaustive2WithDefault() {
        int i = 30000;
        return switch (i) {
            case byte  b -> 1;
            case short s -> 2;
            default -> 3;
        };
    }

    public static int exhaustive1() {
        int i = 42;
        return switch (i) {
            case Integer p -> 1;
        };
    }

    public static int exhaustive2() {
        int i = 42;
        return switch (i) {
            case long d -> 1;
        };
    }

    public static int exhaustive3() {
        int i = 42;
        return switch (i) {
            case double d -> 1;
        };
    }

    public static int exhaustive4() {
        int i = 127;
        return switch (i) {
            case byte b -> 1;
            case double d -> 2;
        };
    }

    public static int exhaustive5() {
        int i = 127 + 1;
        return switch (i) {
            case byte b -> 1;
            case double d -> 2;
        };
    }

    public static int exhaustive6() {
        Integer i = Integer.valueOf(42);
        return switch (i) {
            case int p -> 1;
        };
    }

    public static int exhaustive7(Boolean b) {
        switch (b) {
            case true: return 1;
            case false: return 2;  // with reminder, null, OK
        }
    }

    public static int exhaustive7s(Boolean b) {
        return switch (b) {
            case true -> 1;
            case false -> 2;      // with reminder, null, OK
        };
    }

    public static int exhaustive8(Boolean b) {
        switch (b) {
            case boolean bb: return 1;
        }
    }

    public static int exhaustive9(boolean b) {
        switch (b) {
            case Boolean bb: return 1;
        }
    }

    public static int exhaustiveWithRecords1() {
        R_int r = new R_int(42);
        return switch (r) {
            // exhaustive, because Integer exhaustive at type int
            case R_int(Integer x) -> 1;
        };
    }

    public static int exhaustiveWithRecords2() {
        R_int r = new R_int(42);
        return switch (r) {
            // exhaustive, because double unconditional at int
            case R_int(double x) -> 1;
        };
    }

    public static int exhaustiveWithRecords4() {
        R_Integer r = new R_Integer(42);
        return switch (r) {
            // exhaustive, because R_Integer(int) exhaustive at type R_Integer(Integer), because int exhaustive at type Integer
            case R_Integer(int x) -> 1;
        };
    }

    public static int exhaustiveWithRecords5() {
        R_Integer r = new R_Integer(42);
        return switch (r) {
            // exhaustive, because double exhaustive at Integer
            case R_Integer(double x) -> 1;
        };
    }

    public static int exhaustiveWithRecords6() {
        R_int r = new R_int(42);
        return switch (r) {
            case R_int(byte x) -> 1;
            case R_int(int x) -> 2;
        };
    }

    public static int ensureProperSelectionWithRecords() {
        R_int r = new R_int(4242);
        return switch (r) {
            case R_int(byte x) -> 1;
            case R_int(int x) -> 2;
        };
    }

    public static int ensureProperSelectionWithRecords2() {
        R_double r = new R_double(42);
        switch (r) {
            case R_double(int i):
                return meth_int(i);
            case R_double(double x):
                return meth_double(x);
        }
    }

    public static int ensureProperSelectionWithRecords3() {
        R_int r = new R_int(4242);
        return switch (r) {
            case R_int(byte x) -> 1;
            case R_int(int x) when x == 236 -> 2;
            case R_int(int x) -> 3;
        };
    }

    public static int meth_int(int i) { return 1; }
    public static int meth_double(double d) { return 2;}

    public static int switchAndDowncastFromObjectPrimitive() {
        Object i = 42;
        return switch (i) {
            case Integer ib  -> ib;
            default -> -1;
        };
    }

    public static int dominationBetweenBoxedAndPrimitive() {
        Object i = 42;
        return switch (i) {
            case Integer ib  -> ib;
            case byte ip     -> ip;
            default -> -1;
        };
    }

    static int wideningAndUnboxing() {
        Number o = Integer.valueOf(42);
        return switch (o) {
            case byte b -> 1;
            case int i -> 2;
            case float f -> 3;
            default -> 4;
        };
    }

    static int wideningAndUnboxingInRecord() {
        Box<Number> box = new Box<>(Integer.valueOf(42));
        return switch (box) {
            case Box<Number>(byte b) -> 1;
            case Box<Number>(int i) -> 2;
            case Box<Number>(float f) -> 3;
            default -> 4;
        };
    }

    static int wideningAndInferredUnboxingInRecord() {
        Box<Number> box = new Box<>(Integer.valueOf(42));
        return switch (box) {
            case Box(byte b) -> 1;
            case Box(int i) -> 2;
            case Box(float f) -> 3;
            default -> 4;
        };
    }

    public static float switchOverBoxedFloat(Float f) {
        return switch (f) {
            case 0f -> 5f + 0f;
            case Float fi when fi == 1f -> 6f + fi;
            case Float fi -> 7f + fi;
        };
    }

    public static double switchOverPrimitiveDouble(Double d) {
        return switch (d) {
            case 0d -> 5d + 0d;
            case Double di when di == 1d -> 6d + di;
            case Double di -> 7d + di;
        };
    }

    public static boolean switchOverBoxedBooleanWithUnconditional(Boolean b) {
        return switch (b) {
            case true -> true;
            case Boolean bi -> bi;
        };
    }

    public static int switchOverPrimitiveBooleanWithDefault(boolean b) {
        return switch (b) {
            case true -> 1;
            default -> 2;
        };
    }

    public static int switchOverPrimitiveBoolean(boolean b) {
        return switch (b) {
            case true -> 1;
            case false -> 2;
        };
    }

    public static int switchOverPrimitiveChar(char c) {
        return switch (c) {
            case 'a' -> 1;
            default -> -1;
        };
    }

    public static final float NaNconstant = Float.NaN;
    public static int switchOverPrimitiveFloat(float f) {
        return switch (f) {
            case NaNconstant -> 1;
            case 1.0f -> 2;
            case 0.0f -> 3;
            case -0.0f -> 4;
            default -> -1;
        };
    }

    // tests that Exactness.char_byte is properly redirected to int_byte
    public static int switchRedirectedExactnessMethods1(char c) {
        return switch (c) {
            case byte _ -> 1;
            default -> -1;
        };
    }

    // tests that Exactness.char_short is properly redirected to int_short
    public static int switchRedirectedExactnessMethods2(char c) {
        return switch (c) {
            case short _ -> 1;
            default -> -1;
        };
    }

    // tests that Exactness.short_byte is properly redirected to int_byte
    public static int switchRedirectedExactnessMethods2(short c) {
        return switch (c) {
            case byte _ -> 1;
            default -> -1;
        };
    }

    public static int switchLongAndUnconditional(long l) {
        return switch (l) {
            case 32778L -> 1;
            case long c -> 2;
        };
    }

    public static int switchByte(byte b) {
        return switch (b) {
            case (byte)128 -> 1;
            case byte c -> 2;
        };
    }

    public static int switchShort(short s) {
        return switch (s) {
            case (short)32778 -> 1;
            case short c -> 2;
        };
    }

    public static int switchInt(int i) {
        return switch (i) {
            case 32778 -> 1;
            case int c -> 2;
        };
    }

    public static int switchChar(char c) {
        return switch (c) {
            case '\u0010' -> 1;
            case char cc -> 2;
        };
    }

    public static int testIntInNonEnhancedSwitchStatement(int v1) {
        int i = 0;
        switch (v1) {
            case 1:
                i = 1;
                break;
        }
        return i;
    }

    public static int testFloatInEnhancedSwitchStatement(float v1) {
        int i = 0;
        switch (v1) {
            case 1.0f:
                i = 1;
                break;
            default:
                i = 0;
        }
        return i;
    }

    public static int testDoubleInEnhancedSwitchStatement(double v1) {
        int i = 0;
        switch (v1) {
            case 1d:
                i = 1;
                break;
            default:
                i = 0;
        }
        return i;
    }

    public static int testLongInEnhancedSwitchStatement(long v1) {
        int i = 0;
        switch (v1) {
            case 1l:
                i = 1;
                break;
            default:
                i = 0;
        }
        return i;
    }

    public static int testBooleanInEnhancedSwitchStatement(boolean v1) {
        int i = 0;
        switch (v1) {
            case true:
                i = 1;
                break;
            default:
                i = 0;
        }
        return i;
    }

    public static int testByteWrapperToIntUnconditionallyExact() {
        Byte b = Byte.valueOf((byte) 42);
        return switch (b) {
            case int p -> 1;
        };
    }

    public static int testIntegerWrapperToFloat() {
        Integer i = Integer.valueOf(42);
        return switch (i) {
            case float p -> 1;
            default -> -1;
        };
    }

    public static int testIntegerWrapperToFloatInexact() {
        Integer i = Integer.valueOf(Integer.MAX_VALUE);
        return switch (i) {
            case float p -> 1;
            default -> -1;
        };
    }


    record R_Integer(Integer x) {}
    record R_int(int x) {}
    record R_double(double x) {}
    record Box<N extends Number>(N num) {}

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }

    static void assertEquals(float expected, float actual) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    static void assertEquals(double expected, double actual) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    static void assertTrue(boolean actual) {
        if (!actual) {
            throw new AssertionError("Expected: true, but got false");
        }
    }
}
