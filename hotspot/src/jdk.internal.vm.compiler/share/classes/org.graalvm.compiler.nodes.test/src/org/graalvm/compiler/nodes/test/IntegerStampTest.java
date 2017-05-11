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

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.ShiftOp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;

/**
 * This class tests that integer stamps are created correctly for constants.
 */
public class IntegerStampTest {

    private StructuredGraph graph;

    private static Stamp addIntStamp(Stamp a, Stamp b) {
        return IntegerStamp.OPS.getAdd().foldStamp(a, b);
    }

    @Before
    public void before() {
        graph = new StructuredGraph(AllowAssumptions.YES, INVALID_COMPILATION_ID);
    }

    @Test
    public void testBooleanConstant() {
        assertEquals(new IntegerStamp(32, 1, 1, 0x1, 0x1), ConstantNode.forBoolean(true, graph).stamp());
        assertEquals(new IntegerStamp(32, 0, 0, 0x0, 0x0), ConstantNode.forBoolean(false, graph).stamp());
    }

    @Test
    public void testByteConstant() {
        assertEquals(new IntegerStamp(32, 0, 0, 0x0, 0x0), ConstantNode.forByte((byte) 0, graph).stamp());
        assertEquals(new IntegerStamp(32, 16, 16, 0x10, 0x10), ConstantNode.forByte((byte) 16, graph).stamp());
        assertEquals(new IntegerStamp(32, -16, -16, 0xfffffff0L, 0xfffffff0L), ConstantNode.forByte((byte) -16, graph).stamp());
        assertEquals(new IntegerStamp(32, 127, 127, 0x7f, 0x7f), ConstantNode.forByte((byte) 127, graph).stamp());
        assertEquals(new IntegerStamp(32, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forByte((byte) -128, graph).stamp());
    }

    @Test
    public void testShortConstant() {
        assertEquals(new IntegerStamp(32, 0, 0, 0x0, 0x0), ConstantNode.forShort((short) 0, graph).stamp());
        assertEquals(new IntegerStamp(32, 128, 128, 0x80, 0x80), ConstantNode.forShort((short) 128, graph).stamp());
        assertEquals(new IntegerStamp(32, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forShort((short) -128, graph).stamp());
        assertEquals(new IntegerStamp(32, 32767, 32767, 0x7fff, 0x7fff), ConstantNode.forShort((short) 32767, graph).stamp());
        assertEquals(new IntegerStamp(32, -32768, -32768, 0xffff8000L, 0xffff8000L), ConstantNode.forShort((short) -32768, graph).stamp());
    }

    @Test
    public void testCharConstant() {
        assertEquals(new IntegerStamp(32, 0, 0, 0x0, 0x0), ConstantNode.forChar((char) 0, graph).stamp());
        assertEquals(new IntegerStamp(32, 'A', 'A', 'A', 'A'), ConstantNode.forChar('A', graph).stamp());
        assertEquals(new IntegerStamp(32, 128, 128, 0x80, 0x80), ConstantNode.forChar((char) 128, graph).stamp());
        assertEquals(new IntegerStamp(32, 65535, 65535, 0xffff, 0xffff), ConstantNode.forChar((char) 65535, graph).stamp());
    }

    @Test
    public void testIntConstant() {
        assertEquals(new IntegerStamp(32, 0, 0, 0x0, 0x0), ConstantNode.forInt(0, graph).stamp());
        assertEquals(new IntegerStamp(32, 128, 128, 0x80, 0x80), ConstantNode.forInt(128, graph).stamp());
        assertEquals(new IntegerStamp(32, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forInt(-128, graph).stamp());
        assertEquals(new IntegerStamp(32, Integer.MAX_VALUE, Integer.MAX_VALUE, 0x7fffffff, 0x7fffffff), ConstantNode.forInt(Integer.MAX_VALUE, graph).stamp());
        assertEquals(new IntegerStamp(32, Integer.MIN_VALUE, Integer.MIN_VALUE, 0x80000000L, 0x80000000L), ConstantNode.forInt(Integer.MIN_VALUE, graph).stamp());
    }

    @Test
    public void testLongConstant() {
        assertEquals(new IntegerStamp(64, 0, 0, 0x0, 0x0), ConstantNode.forLong(0, graph).stamp());
        assertEquals(new IntegerStamp(64, 128, 128, 0x80, 0x80), ConstantNode.forLong(128, graph).stamp());
        assertEquals(new IntegerStamp(64, -128, -128, 0xffffffffffffff80L, 0xffffffffffffff80L), ConstantNode.forLong(-128, graph).stamp());
        assertEquals(new IntegerStamp(64, Long.MAX_VALUE, Long.MAX_VALUE, 0x7fffffffffffffffL, 0x7fffffffffffffffL), ConstantNode.forLong(Long.MAX_VALUE, graph).stamp());
        assertEquals(new IntegerStamp(64, Long.MIN_VALUE, Long.MIN_VALUE, 0x8000000000000000L, 0x8000000000000000L), ConstantNode.forLong(Long.MIN_VALUE, graph).stamp());
    }

    @Test
    public void testPositiveRanges() {
        assertEquals(new IntegerStamp(32, 0, 0, 0, 0), StampFactory.forInteger(JavaKind.Int, 0, 0));
        assertEquals(new IntegerStamp(32, 0, 1, 0, 1), StampFactory.forInteger(JavaKind.Int, 0, 1));
        assertEquals(new IntegerStamp(32, 0, 0x123, 0, 0x1ff), StampFactory.forInteger(JavaKind.Int, 0, 0x123));
        assertEquals(new IntegerStamp(32, 0x120, 0x123, 0x120, 0x123), StampFactory.forInteger(JavaKind.Int, 0x120, 0x123));
        assertEquals(new IntegerStamp(32, 10000, 15000, 0x2000, 0x3fff), StampFactory.forInteger(JavaKind.Int, 10000, 15000));
        assertEquals(new IntegerStamp(64, 0, 1, 0, 1), StampFactory.forInteger(JavaKind.Long, 0, 1));
        assertEquals(new IntegerStamp(64, 10000, 15000, 0x2000, 0x3fff), StampFactory.forInteger(JavaKind.Long, 10000, 15000));
        assertEquals(new IntegerStamp(64, 140000000000L, 150000000000L, 0x2000000000L, 0x23ffffffffL), StampFactory.forInteger(JavaKind.Long, 140000000000L, 150000000000L));
    }

    @Test
    public void testNegativeRanges() {
        assertEquals(new IntegerStamp(32, -2, -1, 0xfffffffeL, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -2, -1));
        assertEquals(new IntegerStamp(32, -20, -10, 0xffffffe0L, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -20, -10));
        assertEquals(new IntegerStamp(32, -10000, 0, 0, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -10000, 0));
        assertEquals(new IntegerStamp(32, -10000, -1, 0xffffc000L, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -10000, -1));
        assertEquals(new IntegerStamp(32, -10010, -10000, 0xffffd8e0L, 0xffffd8ffL), StampFactory.forInteger(JavaKind.Int, -10010, -10000));
        assertEquals(new IntegerStamp(64, -2, -1, 0xfffffffffffffffeL, 0xffffffffffffffffL), StampFactory.forInteger(JavaKind.Long, -2, -1));
        assertEquals(new IntegerStamp(64, -10010, -10000, 0xffffffffffffd8e0L, 0xffffffffffffd8ffL), StampFactory.forInteger(JavaKind.Long, -10010, -10000));
        assertEquals(new IntegerStamp(64, -150000000000L, -140000000000L, 0xffffffdc00000000L, 0xffffffdfffffffffL), StampFactory.forInteger(JavaKind.Long, -150000000000L, -140000000000L));
    }

    @Test
    public void testMixedRanges() {
        assertEquals(new IntegerStamp(32, -1, 0, 0, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -1, 0));
        assertEquals(new IntegerStamp(32, -10000, 1000, 0, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -10000, 1000));
        assertEquals(new IntegerStamp(64, -10000, 1000, 0, 0xffffffffffffffffL), StampFactory.forInteger(JavaKind.Long, -10000, 1000));
    }

    private static Stamp narrowingKindConversion(IntegerStamp stamp, JavaKind kind) {
        Stamp narrow = IntegerStamp.OPS.getNarrow().foldStamp(stamp.getBits(), kind.getBitCount(), stamp);
        IntegerConvertOp<?> implicitExtend = kind.isUnsigned() ? IntegerStamp.OPS.getZeroExtend() : IntegerStamp.OPS.getSignExtend();
        return implicitExtend.foldStamp(kind.getBitCount(), 32, narrow);
    }

    @Test
    public void testNarrowingConversions() {
        // byte cases
        assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 0), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 0, 0), JavaKind.Byte));
        assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 10), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 0, 10), JavaKind.Byte));
        assertEquals(StampFactory.forInteger(JavaKind.Int, 10, 20), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 10, 20), JavaKind.Byte));
        assertEquals(StampFactory.forInteger(JavaKind.Int, -10, 0), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, -10, 0), JavaKind.Byte));
        assertEquals(StampFactory.forInteger(JavaKind.Int, -20, -10), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, -20, -10), JavaKind.Byte));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Byte.MIN_VALUE, Byte.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 100, 200), JavaKind.Byte));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Byte.MIN_VALUE, Byte.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, -100, 200), JavaKind.Byte));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Byte.MIN_VALUE, Byte.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, -200, -100), JavaKind.Byte));
        // char cases
        assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 10), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 0, 10), JavaKind.Char));
        assertEquals(StampFactory.forInteger(JavaKind.Int, 10, 20), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 10, 20), JavaKind.Char));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Character.MIN_VALUE, Character.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 20000, 80000), JavaKind.Char));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Character.MIN_VALUE, Character.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, -10000, 40000), JavaKind.Char));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Character.MIN_VALUE, Character.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, -40000, -10000), JavaKind.Char));
        // short cases
        assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 10), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 0, 10), JavaKind.Short));
        assertEquals(StampFactory.forInteger(JavaKind.Int, 10, 20), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 10, 20), JavaKind.Short));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Short.MIN_VALUE, Short.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, 20000, 40000), JavaKind.Short));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Short.MIN_VALUE, Short.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, -10000, 40000), JavaKind.Short));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Short.MIN_VALUE, Short.MAX_VALUE), narrowingKindConversion(StampFactory.forInteger(JavaKind.Int, -40000, -10000), JavaKind.Short));
        // int cases
        assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 10), narrowingKindConversion(StampFactory.forInteger(JavaKind.Long, 0, 10), JavaKind.Int));
        assertEquals(StampFactory.forInteger(JavaKind.Int, 10, 20), narrowingKindConversion(StampFactory.forInteger(JavaKind.Long, 10, 20), JavaKind.Int));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        narrowingKindConversion(StampFactory.forInteger(JavaKind.Long, 20000000000L, 40000000000L), JavaKind.Int));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        narrowingKindConversion(StampFactory.forInteger(JavaKind.Long, -10000000000L, 40000000000L), JavaKind.Int));
        assertEquals(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        narrowingKindConversion(StampFactory.forInteger(JavaKind.Long, -40000000000L, -10000000000L), JavaKind.Int));
    }

    @Test
    public void testXor() {
        assertEquals(new IntegerStamp(32, 0, 0xff, 0, 0xff), IntegerStamp.OPS.getXor().foldStamp(new IntegerStamp(32, 0, 0, 0, 0), new IntegerStamp(32, 0, 0xff, 0, 0xff)));
        assertEquals(new IntegerStamp(32, 0x10, 0x1f, 0x10, 0x1f), IntegerStamp.OPS.getXor().foldStamp(new IntegerStamp(32, 0, 0, 0, 0), new IntegerStamp(32, 0x10, 0x1f, 0x10, 0x1f)));
        assertEquals(new IntegerStamp(32, 0x0, 0xf, 0x0, 0xf), IntegerStamp.OPS.getXor().foldStamp(new IntegerStamp(32, 0x10, 0x10, 0x10, 0x10), new IntegerStamp(32, 0x10, 0x1f, 0x10, 0x1f)));
        assertEquals(new IntegerStamp(32, 0x10, 0x1f, 0x10, 0x1f), IntegerStamp.OPS.getXor().foldStamp(new IntegerStamp(32, 0x10, 0x10, 0x10, 0x10), new IntegerStamp(32, 0x0, 0xf, 0x0, 0xf)));
    }

    @Test
    public void testNot() {
        assertEquals(new IntegerStamp(32, -11, -1, 0xffff_fff0L, 0xffff_ffffL), IntegerStamp.OPS.getNot().foldStamp(new IntegerStamp(32, 0, 10, 0, 0xf)));
    }

    @Test
    public void testAddIntSimple() {
        assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 30, 0, 31), addIntStamp(StampFactory.forInteger(JavaKind.Int, 0, 10), StampFactory.forInteger(JavaKind.Int, 0, 20)));
    }

    @Test
    public void testAddNegativeOverFlowInt1() {
        assertEquals(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0xffff_ffffL),
                        addIntStamp(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, 0), StampFactory.forInteger(JavaKind.Int, -1, 0)));
    }

    @Test
    public void testAddNegativeOverFlowInt2() {
        assertEquals(StampFactory.forInteger(JavaKind.Int, Integer.MAX_VALUE - 2, Integer.MAX_VALUE, 0x7fff_fffcL, 0x7fff_ffffL),
                        addIntStamp(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, Integer.MIN_VALUE + 1), StampFactory.forInteger(JavaKind.Int, -3, -2)));
    }

    @Test
    public void testAddPositiveOverFlowInt1() {
        assertEquals(StampFactory.forKind(JavaKind.Int), addIntStamp(StampFactory.forInteger(JavaKind.Int, 0, 1), StampFactory.forInteger(JavaKind.Int, 0, Integer.MAX_VALUE)));
    }

    @Test
    public void testAddPositiveOverFlowInt2() {
        assertEquals(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, Integer.MIN_VALUE + 2),
                        addIntStamp(StampFactory.forInteger(JavaKind.Int, Integer.MAX_VALUE - 1, Integer.MAX_VALUE), StampFactory.forInteger(JavaKind.Int, 2, 3)));
    }

    @Test
    public void testAddOverFlowsInt() {
        assertEquals(StampFactory.forKind(JavaKind.Int), addIntStamp(StampFactory.forInteger(JavaKind.Int, -1, 1), StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    public void testAddLongSimple() {
        assertEquals(StampFactory.forInteger(JavaKind.Long, 0, 30, 0, 31), addIntStamp(StampFactory.forInteger(JavaKind.Long, 0, 10), StampFactory.forInteger(JavaKind.Long, 0, 20)));
    }

    @Test
    public void testAddNegativOverFlowLong1() {
        assertEquals(StampFactory.forInteger(JavaKind.Long, Long.MIN_VALUE, Long.MAX_VALUE, 0, 0xffff_ffff_ffff_ffffL),
                        addIntStamp(StampFactory.forInteger(JavaKind.Long, Long.MIN_VALUE, Long.MIN_VALUE + 1), StampFactory.forInteger(JavaKind.Long, Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }

    @Test
    public void testAddNegativeOverFlowLong2() {
        assertEquals(StampFactory.forInteger(JavaKind.Long, Long.MAX_VALUE - 2, Long.MAX_VALUE),
                        addIntStamp(StampFactory.forInteger(JavaKind.Long, Long.MIN_VALUE, Long.MIN_VALUE + 1), StampFactory.forInteger(JavaKind.Long, -3, -2)));
    }

    @Test
    public void testAddPositiveOverFlowLong1() {
        assertEquals(StampFactory.forKind(JavaKind.Long), addIntStamp(StampFactory.forInteger(JavaKind.Long, 0, 1), StampFactory.forInteger(JavaKind.Long, 0, Long.MAX_VALUE)));
    }

    @Test
    public void testAddPositiveOverFlowLong2() {
        assertEquals(StampFactory.forInteger(JavaKind.Long, Long.MIN_VALUE, Long.MIN_VALUE + 2),
                        addIntStamp(StampFactory.forInteger(JavaKind.Long, Long.MAX_VALUE - 1, Long.MAX_VALUE), StampFactory.forInteger(JavaKind.Long, 2, 3)));
    }

    @Test
    public void testAddOverFlowsLong() {
        assertEquals(StampFactory.forKind(JavaKind.Long), addIntStamp(StampFactory.forInteger(JavaKind.Long, -1, 1), StampFactory.forInteger(JavaKind.Long, Long.MIN_VALUE, Long.MAX_VALUE)));
    }

    @Test
    public void testAdd1() {
        assertEquals(StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE + 1, 31 + (Integer.MIN_VALUE + 1)),
                        addIntStamp(StampFactory.forInteger(JavaKind.Int, 0, 31), StampFactory.forInteger(JavaKind.Int, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1)));
    }

    @Test
    public void testAdd2() {
        assertEquals(StampFactory.forInteger(JavaKind.Int, 0x8000_007e, 0x8000_007f, 0x8000_007eL, 0x8000_007fL),
                        addIntStamp(StampFactory.forInteger(JavaKind.Int, 0x7fff_fffe, 0x7fff_ffff, 0x7fff_fffeL, 0x7ffff_fffL), StampFactory.forInteger(JavaKind.Int, 128, 128)));
    }

    @Test
    public void testAdd3() {
        assertEquals(StampFactory.forInteger(JavaKind.Long, Long.MIN_VALUE, Long.MAX_VALUE - 1, 0, 0xffff_ffff_ffff_fffeL),
                        addIntStamp(StampFactory.forInteger(JavaKind.Long, Long.MIN_VALUE, Long.MAX_VALUE - 1, 0, 0xffff_ffff_ffff_fffeL),
                                        StampFactory.forInteger(JavaKind.Long, Long.MIN_VALUE, Long.MAX_VALUE - 1, 0, 0xffff_ffff_ffff_fffeL)));

    }

    @Test
    public void testAnd() {
        assertEquals(new IntegerStamp(32, Integer.MIN_VALUE, 0x40000000L, 0, 0xc0000000L),
                        IntegerStamp.OPS.getAnd().foldStamp(StampFactory.forKind(JavaKind.Int), StampFactory.forConstant(JavaConstant.forInt(0xc0000000))));
    }

    private static void testSignExtendShort(long lower, long upper) {
        Stamp shortStamp = StampFactory.forInteger(16, lower, upper);
        Stamp intStamp = IntegerStamp.OPS.getSignExtend().foldStamp(16, 32, shortStamp);
        assertEquals(StampFactory.forInteger(32, lower, upper), intStamp);
    }

    @Test
    public void testSignExtend() {
        testSignExtendShort(5, 7);
        testSignExtendShort(0, 42);
        testSignExtendShort(-42, -1);
        testSignExtendShort(-42, 0);
        testSignExtendShort(-1, 1);
        testSignExtendShort(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private static void testZeroExtendShort(long lower, long upper, long newLower, long newUpper) {
        Stamp shortStamp = StampFactory.forInteger(16, lower, upper);
        Stamp intStamp = IntegerStamp.OPS.getZeroExtend().foldStamp(16, 32, shortStamp);
        assertEquals(StampFactory.forInteger(32, newLower, newUpper), intStamp);
    }

    @Test
    public void testZeroExtend() {
        testZeroExtendShort(5, 7, 5, 7);
        testZeroExtendShort(0, 42, 0, 42);
        testZeroExtendShort(-42, -1, 0xFFFF - 41, 0xFFFF);
        testZeroExtendShort(-42, 0, 0, 0xFFFF);
        testZeroExtendShort(-1, 1, 0, 0xFFFF);
        testZeroExtendShort(Short.MIN_VALUE, Short.MAX_VALUE, 0, 0xFFFF);
    }

    @Test
    public void testIllegalJoin() {
        assertFalse(new IntegerStamp(32, 0, 0xff00, 0, 0xff00).join(new IntegerStamp(32, 1, 0xff, 0x00, 0xff)).hasValues());
        assertFalse(new IntegerStamp(32, 0x100, 0xff00, 0, 0xff00).join(new IntegerStamp(32, 0, 0xff, 0x00, 0xff)).hasValues());
    }

    @Test
    public void testShiftLeft() {
        ShiftOp<?> shl = IntegerStamp.OPS.getShl();
        assertEquals(new IntegerStamp(32, 0, 0x1ff, 0, 0x1ff), shl.foldStamp(new IntegerStamp(32, 0, 0xff, 0, 0xff), new IntegerStamp(32, 0, 1, 0, 1)));
        assertEquals(new IntegerStamp(32, 0, 0x1fe0, 0, 0x1fe0), shl.foldStamp(new IntegerStamp(32, 0, 0xff, 0, 0xff), new IntegerStamp(32, 5, 5, 5, 5)));
        assertEquals(new IntegerStamp(32, 0x1e0, 0x1fe0, 0, 0x1fe0), shl.foldStamp(new IntegerStamp(32, 0xf, 0xff, 0, 0xff), new IntegerStamp(32, 5, 5, 5, 5)));

        assertEquals(new IntegerStamp(64, 0, 0x1ff, 0, 0x1ff), shl.foldStamp(new IntegerStamp(64, 0, 0xff, 0, 0xff), new IntegerStamp(32, 0, 1, 0, 1)));
        assertEquals(new IntegerStamp(64, 0, 0x1fe0, 0, 0x1fe0), shl.foldStamp(new IntegerStamp(64, 0, 0xff, 0, 0xff), new IntegerStamp(32, 5, 5, 5, 5)));
        assertEquals(new IntegerStamp(64, 0x1e0, 0x1fe0, 0, 0x1fe0), shl.foldStamp(new IntegerStamp(64, 0xf, 0xff, 0, 0xff), new IntegerStamp(32, 5, 5, 5, 5)));

    }
}
