/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.test;

import java.util.EnumSet;
import java.util.HashSet;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.calc.FloatConvertCategory;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.ShiftOp;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Test;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Exercise the various stamp folding operations by generating ranges from a set of boundary values
 * and then ensuring that the values that produced those ranges are in the resulting stamp.
 */
public class PrimitiveStampBoundaryTest extends GraalTest {

    static long[] longBoundaryValues = {Long.MIN_VALUE, Long.MIN_VALUE + 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Long.MAX_VALUE - 1,
                    Long.MAX_VALUE};

    static int[] shiftBoundaryValues = {-128, -1, 0, 1, 4, 8, 16, 31, 63, 128};

    static HashSet<IntegerStamp> shiftStamps;
    static HashSet<PrimitiveStamp> integerTestStamps;
    static HashSet<PrimitiveStamp> floatTestStamps;

    static {
        shiftStamps = new HashSet<>();
        for (long v1 : shiftBoundaryValues) {
            for (long v2 : shiftBoundaryValues) {
                shiftStamps.add(IntegerStamp.create(32, Math.min(v1, v2), Math.max(v1, v2)));
            }
        }

        integerTestStamps = new HashSet<>();
        for (long v1 : longBoundaryValues) {
            for (long v2 : longBoundaryValues) {
                if (v2 == (int) v2 && v1 == (int) v1) {
                    integerTestStamps.add(IntegerStamp.create(32, Math.min(v1, v2), Math.max(v1, v2)));
                }
                integerTestStamps.add(IntegerStamp.create(64, Math.min(v1, v2), Math.max(v1, v2)));
            }
        }
    }

    static double[] doubleBoundaryValues = {Double.NEGATIVE_INFINITY, Double.MIN_VALUE, Float.NEGATIVE_INFINITY, Float.MIN_VALUE,
                    Long.MIN_VALUE, Long.MIN_VALUE + 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1,
                    Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE,
                    Float.MAX_VALUE, Float.POSITIVE_INFINITY, Double.MAX_VALUE, Double.POSITIVE_INFINITY};

    static double[] doubleSpecialValues = {Double.NaN, -0.0, -0.0F, Float.NaN};

    static {
        floatTestStamps = new HashSet<>();

        for (double d1 : doubleBoundaryValues) {
            for (double d2 : doubleBoundaryValues) {
                float f1 = (float) d2;
                float f2 = (float) d1;
                if (d2 == f1 && d1 == f2) {
                    generateFloatingStamps(new FloatStamp(32, Math.min(f2, f1), Math.max(f2, f1), true));
                    generateFloatingStamps(new FloatStamp(32, Math.min(f2, f1), Math.max(f2, f1), false));
                }
                generateFloatingStamps(new FloatStamp(64, Math.min(d1, d2), Math.max(d1, d2), true));
                generateFloatingStamps(new FloatStamp(64, Math.min(d1, d2), Math.max(d1, d2), false));
            }
        }
    }

    private static void generateFloatingStamps(FloatStamp floatStamp) {
        floatTestStamps.add(floatStamp);
        for (double d : doubleSpecialValues) {
            FloatStamp newStamp = (FloatStamp) floatStamp.meet(floatStampForConstant(d, floatStamp.getBits()));
            if (!newStamp.isUnrestricted()) {
                floatTestStamps.add(newStamp);
            }
        }
    }

    @Test
    public void testConvertBoundaryValues() {
        testConvertBoundaryValues(IntegerStamp.OPS.getSignExtend(), 32, 64, integerTestStamps);
        testConvertBoundaryValues(IntegerStamp.OPS.getZeroExtend(), 32, 64, integerTestStamps);
        testConvertBoundaryValues(IntegerStamp.OPS.getNarrow(), 64, 32, integerTestStamps);
    }

    private static void testConvertBoundaryValues(IntegerConvertOp<?> op, int inputBits, int resultBits, HashSet<PrimitiveStamp> stamps) {
        for (PrimitiveStamp stamp : stamps) {
            if (inputBits == stamp.getBits()) {
                Stamp lower = boundaryStamp(stamp, false);
                Stamp upper = boundaryStamp(stamp, true);
                checkConvertOperation(op, inputBits, resultBits, op.foldStamp(inputBits, resultBits, stamp), lower);
                checkConvertOperation(op, inputBits, resultBits, op.foldStamp(inputBits, resultBits, stamp), upper);
            }
        }
    }

    private static void checkConvertOperation(IntegerConvertOp<?> op, int inputBits, int resultBits, Stamp result, Stamp v1stamp) {
        Stamp folded = op.foldStamp(inputBits, resultBits, v1stamp);
        assertTrue(folded.asConstant() != null, "should constant fold %s %s %s", op, v1stamp, folded);
        assertTrue(result.meet(folded).equals(result), "result out of range %s %s %s %s %s", op, v1stamp, folded, result, result.meet(folded));
    }

    @Test
    public void testFloatConvertBoundaryValues() {
        for (FloatConvert op : EnumSet.allOf(FloatConvert.class)) {
            ArithmeticOpTable.FloatConvertOp floatConvert = IntegerStamp.OPS.getFloatConvert(op);
            if (floatConvert == null) {
                continue;
            }
            assert op.getCategory() == FloatConvertCategory.IntegerToFloatingPoint : op;
            testConvertBoundaryValues(floatConvert, op.getInputBits(), integerTestStamps);
        }
        for (FloatConvert op : EnumSet.allOf(FloatConvert.class)) {
            ArithmeticOpTable.FloatConvertOp floatConvert = FloatStamp.OPS.getFloatConvert(op);
            if (floatConvert == null) {
                continue;
            }
            assert op.getCategory() == FloatConvertCategory.FloatingPointToInteger || op.getCategory() == FloatConvertCategory.FloatingPointToFloatingPoint : op;
            testConvertBoundaryValues(floatConvert, op.getInputBits(), floatTestStamps);
        }
    }

    private static void testConvertBoundaryValues(ArithmeticOpTable.FloatConvertOp op, int bits, HashSet<PrimitiveStamp> stamps) {
        for (PrimitiveStamp stamp : stamps) {
            if (bits == stamp.getBits()) {
                Stamp lower = boundaryStamp(stamp, false);
                Stamp upper = boundaryStamp(stamp, true);
                checkConvertOperation(op, op.foldStamp(stamp), lower);
                checkConvertOperation(op, op.foldStamp(stamp), upper);
            }
        }
    }

    private static void checkConvertOperation(ArithmeticOpTable.FloatConvertOp op, Stamp result, Stamp v1stamp) {
        Stamp folded = op.foldStamp(v1stamp);
        assertTrue(folded.asConstant() != null, "should constant fold %s %s %s", op, v1stamp, folded);
        assertTrue(result.meet(folded).equals(result), "result out of range %s %s %s %s %s", op, v1stamp, folded, result, result.meet(folded));
    }

    @Test
    public void testShiftBoundaryValues() {
        for (ShiftOp<?> op : IntegerStamp.OPS.getShiftOps()) {
            testShiftBoundaryValues(op, integerTestStamps, shiftStamps);
        }
    }

    private static void testShiftBoundaryValues(ShiftOp<?> shiftOp, HashSet<PrimitiveStamp> stamps, HashSet<IntegerStamp> shifts) {
        for (PrimitiveStamp testStamp : stamps) {
            if (testStamp instanceof IntegerStamp) {
                IntegerStamp stamp = (IntegerStamp) testStamp;
                for (IntegerStamp shiftStamp : shifts) {
                    IntegerStamp foldedStamp = (IntegerStamp) shiftOp.foldStamp(stamp, shiftStamp);
                    checkShiftOperation(stamp.getBits(), shiftOp, foldedStamp, stamp.lowerBound(), shiftStamp.lowerBound());
                    checkShiftOperation(stamp.getBits(), shiftOp, foldedStamp, stamp.lowerBound(), shiftStamp.upperBound());
                    checkShiftOperation(stamp.getBits(), shiftOp, foldedStamp, stamp.upperBound(), shiftStamp.lowerBound());
                    checkShiftOperation(stamp.getBits(), shiftOp, foldedStamp, stamp.upperBound(), shiftStamp.upperBound());
                }
            }
        }
    }

    private static void checkShiftOperation(int bits, ShiftOp<?> op, IntegerStamp result, long v1, long v2) {
        IntegerStamp v1stamp = IntegerStamp.create(bits, v1, v1);
        IntegerStamp v2stamp = IntegerStamp.create(32, v2, v2);
        IntegerStamp folded = (IntegerStamp) op.foldStamp(v1stamp, v2stamp);
        Constant constant = op.foldConstant(JavaConstant.forPrimitiveInt(bits, v1), (int) v2);
        assertTrue(constant != null);
        assertTrue(folded.asConstant() != null, "should constant fold %s %s %s %s", op, v1stamp, v2stamp, folded);
        assertTrue(result.meet(folded).equals(result), "result out of range %s %s %s %s %s %s", op, v1stamp, v2stamp, folded, result, result.meet(folded));
    }

    private static void checkBinaryOperation(ArithmeticOpTable.BinaryOp<?> op, Stamp result, Stamp v1stamp, Stamp v2stamp) {
        Stamp folded = op.foldStamp(v1stamp, v2stamp);
        Constant constant = op.foldConstant(v1stamp.asConstant(), v2stamp.asConstant());
        if (constant != null) {
            Constant constant2 = folded.asConstant();
            if (constant2 == null && v1stamp instanceof FloatStamp) {
                JavaConstant c = (JavaConstant) constant;
                assertTrue((c.getJavaKind() == JavaKind.Double && Double.isNaN(c.asDouble())) ||
                                (c.getJavaKind() == JavaKind.Float && Float.isNaN(c.asFloat())));
            } else {
                assertTrue(constant2 != null, "should constant fold %s %s %s %s", op, v1stamp, v2stamp, folded);
                if (!constant.equals(constant2)) {
                    op.foldConstant(v1stamp.asConstant(), v2stamp.asConstant());
                    op.foldStamp(v1stamp, v2stamp);
                }
                assertTrue(constant.equals(constant2), "should produce same constant %s %s %s %s %s", op, v1stamp, v2stamp, constant, constant2);
            }
            assertTrue(result.meet(folded).equals(result), "result out of range %s %s %s %s %s %s", op, v1stamp, v2stamp, folded, result, result.meet(folded));
        }
    }

    @Test
    public void testBinaryBoundaryValues() {
        for (BinaryOp<?> op : IntegerStamp.OPS.getBinaryOps()) {
            if (op != null) {
                testBinaryBoundaryValues(op, integerTestStamps);
            }
        }
        for (BinaryOp<?> op : FloatStamp.OPS.getBinaryOps()) {
            if (op != null) {
                testBinaryBoundaryValues(op, floatTestStamps);
            }
        }
    }

    private static Stamp boundaryStamp(Stamp v1, boolean upper) {
        if (v1 instanceof IntegerStamp) {
            IntegerStamp istamp = (IntegerStamp) v1;
            long bound = upper ? istamp.upperBound() : istamp.lowerBound();
            return IntegerStamp.create(istamp.getBits(), bound, bound);
        } else if (v1 instanceof FloatStamp) {
            FloatStamp floatStamp = (FloatStamp) v1;
            double bound = upper ? floatStamp.upperBound() : floatStamp.lowerBound();
            int bits = floatStamp.getBits();
            return floatStampForConstant(bound, bits);
        } else {
            throw new InternalError("unexpected stamp type " + v1);
        }
    }

    private static FloatStamp floatStampForConstant(double bound, int bits) {
        if (bits == 32) {
            float fbound = (float) bound;
            return new FloatStamp(bits, fbound, fbound, !Float.isNaN(fbound));
        } else {
            return new FloatStamp(bits, bound, bound, !Double.isNaN(bound));
        }
    }

    private static void testBinaryBoundaryValues(ArithmeticOpTable.BinaryOp<?> op, HashSet<PrimitiveStamp> stamps) {
        for (PrimitiveStamp v1 : stamps) {
            for (PrimitiveStamp v2 : stamps) {
                if (v1.getBits() == v2.getBits() && v1.getClass() == v2.getClass()) {
                    Stamp result = op.foldStamp(v1, v2);
                    Stamp v1lower = boundaryStamp(v1, false);
                    Stamp v1upper = boundaryStamp(v1, true);
                    Stamp v2lower = boundaryStamp(v2, false);
                    Stamp v2upper = boundaryStamp(v2, true);
                    checkBinaryOperation(op, result, v1lower, v2lower);
                    checkBinaryOperation(op, result, v1lower, v2upper);
                    checkBinaryOperation(op, result, v1upper, v2lower);
                    checkBinaryOperation(op, result, v1upper, v2upper);
                }
            }
        }
    }

    @Test
    public void testUnaryBoundaryValues() {
        for (ArithmeticOpTable.UnaryOp<?> op : IntegerStamp.OPS.getUnaryOps()) {
            if (op != null) {
                testUnaryBoundaryValues(op, integerTestStamps);
            }
        }
        for (ArithmeticOpTable.UnaryOp<?> op : FloatStamp.OPS.getUnaryOps()) {
            if (op != null) {
                testUnaryBoundaryValues(op, floatTestStamps);
            }
        }
    }

    private static void testUnaryBoundaryValues(ArithmeticOpTable.UnaryOp<?> op, HashSet<PrimitiveStamp> stamps) {
        for (PrimitiveStamp v1 : stamps) {
            Stamp result = op.foldStamp(v1);
            checkUnaryOperation(op, result, boundaryStamp(v1, false));
            checkUnaryOperation(op, result, boundaryStamp(v1, true));
        }
    }

    private static void checkUnaryOperation(ArithmeticOpTable.UnaryOp<?> op, Stamp result, Stamp v1stamp) {
        Stamp folded = op.foldStamp(v1stamp);
        Constant v1constant = v1stamp.asConstant();
        if (v1constant != null) {
            Constant constant = op.foldConstant(v1constant);
            if (constant != null) {
                Constant constant2 = folded.asConstant();
                if (constant2 == null && v1stamp instanceof FloatStamp) {
                    JavaConstant c = (JavaConstant) constant;
                    assertTrue((c.getJavaKind() == JavaKind.Double && Double.isNaN(c.asDouble())) ||
                                    (c.getJavaKind() == JavaKind.Float && Float.isNaN(c.asFloat())));
                } else {
                    assertTrue(constant2 != null, "should constant fold %s %s %s", op, v1stamp, folded);
                    assertTrue(constant.equals(constant2), "should produce same constant %s %s %s %s", op, v1stamp, constant, constant2);
                }
            }
        } else {
            assert v1stamp instanceof FloatStamp;
        }
        assertTrue(result.meet(folded).equals(result), "result out of range %s %s %s %s %s", op, v1stamp, folded, result, result.meet(folded));
    }
}
