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

import static org.graalvm.compiler.core.test.GraalCompilerTest.getInitialOptions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.ShiftOp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.test.GraphTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * This class tests that integer stamps are created correctly for constants.
 */
public class IntegerStampTest extends GraphTest {

    private StructuredGraph graph;

    private static Stamp addIntStamp(Stamp a, Stamp b) {
        return IntegerStamp.OPS.getAdd().foldStamp(a, b);
    }

    @Before
    public void before() {
        OptionValues options = getInitialOptions();
        DebugContext debug = getDebug(options);
        graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).build();
    }

    @Test
    public void testBooleanConstant() {
        assertEquals(IntegerStamp.create(32, 1, 1, 0x1, 0x1), ConstantNode.forBoolean(true, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 0, 0, 0x0, 0x0), ConstantNode.forBoolean(false, graph).stamp(NodeView.DEFAULT));
    }

    @Test
    public void testByteConstant() {
        assertEquals(IntegerStamp.create(32, 0, 0, 0x0, 0x0), ConstantNode.forByte((byte) 0, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 16, 16, 0x10, 0x10), ConstantNode.forByte((byte) 16, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, -16, -16, 0xfffffff0L, 0xfffffff0L), ConstantNode.forByte((byte) -16, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 127, 127, 0x7f, 0x7f), ConstantNode.forByte((byte) 127, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forByte((byte) -128, graph).stamp(NodeView.DEFAULT));
    }

    @Test
    public void testShortConstant() {
        assertEquals(IntegerStamp.create(32, 0, 0, 0x0, 0x0), ConstantNode.forShort((short) 0, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 128, 128, 0x80, 0x80), ConstantNode.forShort((short) 128, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forShort((short) -128, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 32767, 32767, 0x7fff, 0x7fff), ConstantNode.forShort((short) 32767, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, -32768, -32768, 0xffff8000L, 0xffff8000L), ConstantNode.forShort((short) -32768, graph).stamp(NodeView.DEFAULT));
    }

    @Test
    public void testCharConstant() {
        assertEquals(IntegerStamp.create(32, 0, 0, 0x0, 0x0), ConstantNode.forChar((char) 0, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 'A', 'A', 'A', 'A'), ConstantNode.forChar('A', graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 128, 128, 0x80, 0x80), ConstantNode.forChar((char) 128, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 65535, 65535, 0xffff, 0xffff), ConstantNode.forChar((char) 65535, graph).stamp(NodeView.DEFAULT));
    }

    @Test
    public void testIntConstant() {
        assertEquals(IntegerStamp.create(32, 0, 0, 0x0, 0x0), ConstantNode.forInt(0, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, 128, 128, 0x80, 0x80), ConstantNode.forInt(128, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, -128, -128, 0xffffff80L, 0xffffff80L), ConstantNode.forInt(-128, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, Integer.MAX_VALUE, Integer.MAX_VALUE, 0x7fffffff, 0x7fffffff), ConstantNode.forInt(Integer.MAX_VALUE, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(32, Integer.MIN_VALUE, Integer.MIN_VALUE, 0x80000000L, 0x80000000L), ConstantNode.forInt(Integer.MIN_VALUE, graph).stamp(NodeView.DEFAULT));
    }

    @Test
    public void testLongConstant() {
        assertEquals(IntegerStamp.create(64, 0, 0, 0x0, 0x0), ConstantNode.forLong(0, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(64, 128, 128, 0x80, 0x80), ConstantNode.forLong(128, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(64, -128, -128, 0xffffffffffffff80L, 0xffffffffffffff80L), ConstantNode.forLong(-128, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(64, Long.MAX_VALUE, Long.MAX_VALUE, 0x7fffffffffffffffL, 0x7fffffffffffffffL), ConstantNode.forLong(Long.MAX_VALUE, graph).stamp(NodeView.DEFAULT));
        assertEquals(IntegerStamp.create(64, Long.MIN_VALUE, Long.MIN_VALUE, 0x8000000000000000L, 0x8000000000000000L), ConstantNode.forLong(Long.MIN_VALUE, graph).stamp(NodeView.DEFAULT));
    }

    @Test
    public void testPositiveRanges() {
        assertEquals(IntegerStamp.create(32, 0, 0, 0, 0), StampFactory.forInteger(JavaKind.Int, 0, 0));
        assertEquals(IntegerStamp.create(32, 0, 1, 0, 1), StampFactory.forInteger(JavaKind.Int, 0, 1));
        assertEquals(IntegerStamp.create(32, 0, 0x123, 0, 0x1ff), StampFactory.forInteger(JavaKind.Int, 0, 0x123));
        assertEquals(IntegerStamp.create(32, 0x120, 0x123, 0x120, 0x123), StampFactory.forInteger(JavaKind.Int, 0x120, 0x123));
        assertEquals(IntegerStamp.create(32, 10000, 15000, 0x2000, 0x3fff), StampFactory.forInteger(JavaKind.Int, 10000, 15000));
        assertEquals(IntegerStamp.create(64, 0, 1, 0, 1), StampFactory.forInteger(JavaKind.Long, 0, 1));
        assertEquals(IntegerStamp.create(64, 10000, 15000, 0x2000, 0x3fff), StampFactory.forInteger(JavaKind.Long, 10000, 15000));
        assertEquals(IntegerStamp.create(64, 140000000000L, 150000000000L, 0x2000000000L, 0x23ffffffffL), StampFactory.forInteger(JavaKind.Long, 140000000000L, 150000000000L));
    }

    @Test
    public void testNegativeRanges() {
        assertEquals(IntegerStamp.create(32, -2, -1, 0xfffffffeL, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -2, -1));
        assertEquals(IntegerStamp.create(32, -20, -10, 0xffffffe0L, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -20, -10));
        assertEquals(IntegerStamp.create(32, -10000, 0, 0, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -10000, 0));
        assertEquals(IntegerStamp.create(32, -10000, -1, 0xffffc000L, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -10000, -1));
        assertEquals(IntegerStamp.create(32, -10010, -10000, 0xffffd8e0L, 0xffffd8ffL), StampFactory.forInteger(JavaKind.Int, -10010, -10000));
        assertEquals(IntegerStamp.create(64, -2, -1, 0xfffffffffffffffeL, 0xffffffffffffffffL), StampFactory.forInteger(JavaKind.Long, -2, -1));
        assertEquals(IntegerStamp.create(64, -10010, -10000, 0xffffffffffffd8e0L, 0xffffffffffffd8ffL), StampFactory.forInteger(JavaKind.Long, -10010, -10000));
        assertEquals(IntegerStamp.create(64, -150000000000L, -140000000000L, 0xffffffdc00000000L, 0xffffffdfffffffffL), StampFactory.forInteger(JavaKind.Long, -150000000000L, -140000000000L));
    }

    @Test
    public void testMixedRanges() {
        assertEquals(IntegerStamp.create(32, -1, 0, 0, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -1, 0));
        assertEquals(IntegerStamp.create(32, -10000, 1000, 0, 0xffffffffL), StampFactory.forInteger(JavaKind.Int, -10000, 1000));
        assertEquals(IntegerStamp.create(64, -10000, 1000, 0, 0xffffffffffffffffL), StampFactory.forInteger(JavaKind.Long, -10000, 1000));
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
    public void testMaskBasedNarrowing() {
        IntegerStamp stamp = IntegerStamp.create(32, 1, 2, 0x2, 0x3);
        IntegerStamp resultStamp = IntegerStamp.create(32, 2, 2);
        assertEquals(resultStamp, stamp);
    }

    @Test
    public void testJoinWeirdMasks() {
        IntegerStamp minusOneOrThree = IntegerStamp.create(32, -1, 3, 0x3, 0xFFFFFFFFL);
        IntegerStamp twoOrThree = IntegerStamp.create(32, 2, 3, 0x2, 0x3);
        IntegerStamp three = IntegerStamp.create(32, 3, 3, 0x3, 0x3);
        assertEquals(three, minusOneOrThree.join(twoOrThree));

        IntegerStamp minusOneOrThreeOrOne = IntegerStamp.create(32, -1, 3, 0x1, 0xFFFFFFFFL);
        assertEquals(three, minusOneOrThreeOrOne.join(twoOrThree));

        IntegerStamp a = IntegerStamp.create(32, 0b101, 0b110, 0b100, 0b111);
        IntegerStamp b = IntegerStamp.create(32, 0b011, 0b110, 0b010, 0b111);

        // This exercises a special case:
        // The new lowest bound is max(0b101, 0b011) = 0b101
        // The new down mask is (0b100 | 0b010) = 0b110
        // Now based on lowest bound and down mask, we know that the new lowest bound is 0b110
        // Just making an or with the new down mask would give however (0b110 | 0b101) = 0b111 and
        // would therefore be wrong.
        // New upper bound is 0b110.

        IntegerStamp result = IntegerStamp.create(32, 0b110, 0b110, 0b110, 0b110);
        assertEquals(result, a.join(b));
    }

    @Test
    public void testXor() {
        assertEquals(IntegerStamp.create(32, 0, 0xff, 0, 0xff), IntegerStamp.OPS.getXor().foldStamp(IntegerStamp.create(32, 0, 0, 0, 0), IntegerStamp.create(32, 0, 0xff, 0, 0xff)));
        assertEquals(IntegerStamp.create(32, 0x10, 0x1f, 0x10, 0x1f), IntegerStamp.OPS.getXor().foldStamp(IntegerStamp.create(32, 0, 0, 0, 0), IntegerStamp.create(32, 0x10, 0x1f, 0x10, 0x1f)));
        assertEquals(IntegerStamp.create(32, 0x0, 0xf, 0x0, 0xf),
                        IntegerStamp.OPS.getXor().foldStamp(IntegerStamp.create(32, 0x10, 0x10, 0x10, 0x10), IntegerStamp.create(32, 0x10, 0x1f, 0x10, 0x1f)));
        assertEquals(IntegerStamp.create(32, 0x10, 0x1f, 0x10, 0x1f),
                        IntegerStamp.OPS.getXor().foldStamp(IntegerStamp.create(32, 0x10, 0x10, 0x10, 0x10), IntegerStamp.create(32, 0x0, 0xf, 0x0, 0xf)));
    }

    @Test
    public void testNot() {
        assertEquals(IntegerStamp.create(32, -11, -1, 0xffff_fff0L, 0xffff_ffffL), IntegerStamp.OPS.getNot().foldStamp(IntegerStamp.create(32, 0, 10, 0, 0xf)));
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
        assertEquals(IntegerStamp.create(32, Integer.MIN_VALUE, 0x40000000L, 0, 0xc0000000L),
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
        assertFalse(IntegerStamp.create(32, 0, 0xff00, 0, 0xff00).join(IntegerStamp.create(32, 1, 0xff, 0x00, 0xff)).hasValues());
        assertFalse(IntegerStamp.create(32, 0x100, 0xff00, 0, 0xff00).join(IntegerStamp.create(32, 0, 0xff, 0x00, 0xff)).hasValues());
    }

    @Test
    public void testShiftLeft() {
        ShiftOp<?> shl = IntegerStamp.OPS.getShl();
        assertEquals(IntegerStamp.create(32, 0, 0x1ff, 0, 0x1ff), shl.foldStamp(IntegerStamp.create(32, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 0, 1, 0, 1)));
        assertEquals(IntegerStamp.create(32, 0, 0x1fe0, 0, 0x1fe0), shl.foldStamp(IntegerStamp.create(32, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(32, 0x1e0, 0x1fe0, 0, 0x1fe0), shl.foldStamp(IntegerStamp.create(32, 0xf, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(32, -4096, -4096, -4096, -4096), shl.foldStamp(IntegerStamp.create(32, -16, -16, -16, -16), IntegerStamp.create(32, 8, 8, 8, 8)));
        assertEquals(StampFactory.empty(JavaKind.Int), shl.foldStamp(StampFactory.empty(JavaKind.Int), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(StampFactory.empty(JavaKind.Int), shl.foldStamp(IntegerStamp.create(32, 0xf, 0xff, 0, 0xff), (IntegerStamp) StampFactory.empty(JavaKind.Int)));

        assertEquals(IntegerStamp.create(64, 0, 0x1ff, 0, 0x1ff), shl.foldStamp(IntegerStamp.create(64, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 0, 1, 0, 1)));
        assertEquals(IntegerStamp.create(64, 0, 0x1fe0, 0, 0x1fe0), shl.foldStamp(IntegerStamp.create(64, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(64, 0x1e0, 0x1fe0, 0, 0x1fe0), shl.foldStamp(IntegerStamp.create(64, 0xf, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(64, -4096, -4096, -4096, -4096), shl.foldStamp(IntegerStamp.create(64, -16, -16, -16, -16), IntegerStamp.create(32, 8, 8, 8, 8)));
        assertEquals(StampFactory.empty(JavaKind.Long), shl.foldStamp(StampFactory.empty(JavaKind.Long), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(StampFactory.empty(JavaKind.Long), shl.foldStamp(IntegerStamp.create(64, 0xf, 0xff, 0, 0xff), (IntegerStamp) StampFactory.empty(JavaKind.Int)));
    }

    @Test
    public void testUnsignedShiftRight() {
        ShiftOp<?> ushr = IntegerStamp.OPS.getUShr();
        assertEquals(IntegerStamp.create(32, 0, 0xff, 0, 0xff), ushr.foldStamp(IntegerStamp.create(32, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 0, 1, 0, 1)));
        assertEquals(IntegerStamp.create(32, 0, 0x07, 0, 0x07), ushr.foldStamp(IntegerStamp.create(32, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(32, 0x0, 0x07, 0, 0x07), ushr.foldStamp(IntegerStamp.create(32, 0xf, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(32, 0xffffff, 0xffffff, 0xffffff, 0xffffff), ushr.foldStamp(IntegerStamp.create(32, -16, -16, -16, -16), IntegerStamp.create(32, 8, 8, 8, 8)));
        assertEquals(StampFactory.empty(JavaKind.Int), ushr.foldStamp(StampFactory.empty(JavaKind.Int), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(StampFactory.empty(JavaKind.Int), ushr.foldStamp(IntegerStamp.create(32, 0xf, 0xff, 0, 0xff), (IntegerStamp) StampFactory.empty(JavaKind.Int)));

        assertEquals(IntegerStamp.create(64, 0, 0xff, 0, 0xff), ushr.foldStamp(IntegerStamp.create(64, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 0, 1, 0, 1)));
        assertEquals(IntegerStamp.create(64, 0, 0x07, 0, 0x07), ushr.foldStamp(IntegerStamp.create(64, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(64, 0x0, 0x07, 0, 0x07), ushr.foldStamp(IntegerStamp.create(64, 0xf, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(64, 0xffffffffffffffL, 0xffffffffffffffL, 0xffffffffffffffL, 0xffffffffffffffL),
                        ushr.foldStamp(IntegerStamp.create(64, -16, -16, -16, -16), IntegerStamp.create(32, 8, 8, 8, 8)));
        assertEquals(StampFactory.empty(JavaKind.Long), ushr.foldStamp(StampFactory.empty(JavaKind.Long), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(StampFactory.empty(JavaKind.Long), ushr.foldStamp(IntegerStamp.create(64, 0xf, 0xff, 0, 0xff), (IntegerStamp) StampFactory.empty(JavaKind.Int)));
    }

    @Test
    public void testShiftRight() {
        ShiftOp<?> shr = IntegerStamp.OPS.getShr();
        assertEquals(IntegerStamp.create(32, 0, 0xff, 0, 0xff), shr.foldStamp(IntegerStamp.create(32, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 0, 1, 0, 1)));
        assertEquals(IntegerStamp.create(32, 0, 0x07, 0, 0x07), shr.foldStamp(IntegerStamp.create(32, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(32, 0x0, 0x07, 0, 0x07), shr.foldStamp(IntegerStamp.create(32, 0xf, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(32, -1, -1, -1, -1), shr.foldStamp(IntegerStamp.create(32, -16, -16, -16, -16), IntegerStamp.create(32, 8, 8, 8, 8)));
        assertEquals(StampFactory.empty(JavaKind.Int), shr.foldStamp(StampFactory.empty(JavaKind.Int), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(StampFactory.empty(JavaKind.Int), shr.foldStamp(IntegerStamp.create(32, 0xf, 0xff, 0, 0xff), (IntegerStamp) StampFactory.empty(JavaKind.Int)));

        assertEquals(IntegerStamp.create(64, 0, 0xff, 0, 0xff), shr.foldStamp(IntegerStamp.create(64, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 0, 1, 0, 1)));
        assertEquals(IntegerStamp.create(64, 0, 0x07, 0, 0x07), shr.foldStamp(IntegerStamp.create(64, 0, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(64, 0x0, 0x07, 0, 0x07), shr.foldStamp(IntegerStamp.create(64, 0xf, 0xff, 0, 0xff), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(IntegerStamp.create(64, -1, -1, -1, -1), shr.foldStamp(IntegerStamp.create(64, -16, -16, -16, -16), IntegerStamp.create(32, 8, 8, 8, 8)));
        assertEquals(StampFactory.empty(JavaKind.Long), shr.foldStamp(StampFactory.empty(JavaKind.Long), IntegerStamp.create(32, 5, 5, 5, 5)));
        assertEquals(StampFactory.empty(JavaKind.Long), shr.foldStamp(IntegerStamp.create(64, 0xf, 0xff, 0, 0xff), (IntegerStamp) StampFactory.empty(JavaKind.Int)));
    }

    @Test
    public void testMulHigh() {
        testSomeMulHigh(IntegerStamp.OPS.getMulHigh());
    }

    @Test
    public void testUMulHigh() {
        testSomeMulHigh(IntegerStamp.OPS.getUMulHigh());
    }

    private static void testSomeMulHigh(BinaryOp<?> someMulHigh) {
        // 32 bits
        testMulHigh(someMulHigh, 0, 0, 32);

        testMulHigh(someMulHigh, 1, 1, 32);
        testMulHigh(someMulHigh, 1, 5, 32);
        testMulHigh(someMulHigh, 256, 256, 32);
        testMulHigh(someMulHigh, 0xFFFFFFF, 0xFFFFFFA, 32);
        testMulHigh(someMulHigh, Integer.MAX_VALUE, 2, 32);

        testMulHigh(someMulHigh, -1, -1, 32);
        testMulHigh(someMulHigh, -1, -5, 32);
        testMulHigh(someMulHigh, -256, -256, 32);
        testMulHigh(someMulHigh, -0xFFFFFFF, -0xFFFFFFA, 32);
        testMulHigh(someMulHigh, Integer.MIN_VALUE, -2, 32);

        testMulHigh(someMulHigh, -1, 1, 32);
        testMulHigh(someMulHigh, -1, 5, 32);
        testMulHigh(someMulHigh, -256, 256, 32);
        testMulHigh(someMulHigh, -0xFFFFFFF, 0xFFFFFFA, 32);
        testMulHigh(someMulHigh, Integer.MIN_VALUE, 2, 32);

        testMulHigh(someMulHigh, Integer.MIN_VALUE, Integer.MIN_VALUE, 32);
        testMulHigh(someMulHigh, Integer.MAX_VALUE, Integer.MAX_VALUE, 32);

        assertEquals(StampFactory.forKind(JavaKind.Int).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).empty(), StampFactory.forKind(JavaKind.Int).empty()));
        assertEquals(StampFactory.forKind(JavaKind.Int).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).empty(), StampFactory.forKind(JavaKind.Int).unrestricted()));
        assertEquals(StampFactory.forKind(JavaKind.Int).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).empty(), IntegerStamp.create(32, 0, 0)));
        assertEquals(StampFactory.forKind(JavaKind.Int).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).empty(), IntegerStamp.create(32, 1, 1)));
        assertEquals(StampFactory.forKind(JavaKind.Int).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).empty(), IntegerStamp.create(32, -1, -1)));

        assertEquals(StampFactory.forKind(JavaKind.Int).unrestricted(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).unrestricted(), StampFactory.forKind(JavaKind.Int).unrestricted()));
        assertEquals(StampFactory.forKind(JavaKind.Int).unrestricted(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).unrestricted(), IntegerStamp.create(32, 0, 0)));
        assertEquals(StampFactory.forKind(JavaKind.Int).unrestricted(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).unrestricted(), IntegerStamp.create(32, 1, 1)));
        assertEquals(StampFactory.forKind(JavaKind.Int).unrestricted(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Int).unrestricted(), IntegerStamp.create(32, -1, -1)));

        // 64 bits
        testMulHigh(someMulHigh, 0, 0, 64);

        testMulHigh(someMulHigh, 1, 1, 64);
        testMulHigh(someMulHigh, 1, 5, 64);
        testMulHigh(someMulHigh, 256, 256, 64);
        testMulHigh(someMulHigh, 0xFFFFFFF, 0xFFFFFFA, 64);
        testMulHigh(someMulHigh, 0xFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFAL, 64);
        testMulHigh(someMulHigh, Integer.MAX_VALUE, 2, 64);
        testMulHigh(someMulHigh, Long.MAX_VALUE, 2, 64);

        testMulHigh(someMulHigh, -1, -1, 64);
        testMulHigh(someMulHigh, -1, -5, 64);
        testMulHigh(someMulHigh, -256, -256, 64);
        testMulHigh(someMulHigh, -0xFFFFFFF, -0xFFFFFFA, 64);
        testMulHigh(someMulHigh, -0xFFFFFFFFFFFFFFL, -0xFFFFFFFFFFFFFAL, 64);
        testMulHigh(someMulHigh, Integer.MIN_VALUE, -2, 64);
        testMulHigh(someMulHigh, Long.MIN_VALUE, -2, 64);

        testMulHigh(someMulHigh, -1, 1, 64);
        testMulHigh(someMulHigh, -1, 5, 64);
        testMulHigh(someMulHigh, -256, 256, 64);
        testMulHigh(someMulHigh, -0xFFFFFFF, 0xFFFFFFA, 64);
        testMulHigh(someMulHigh, -0xFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFAL, 64);
        testMulHigh(someMulHigh, Integer.MIN_VALUE, 2, 64);
        testMulHigh(someMulHigh, Long.MIN_VALUE, 2, 64);

        testMulHigh(someMulHigh, Integer.MIN_VALUE, Integer.MIN_VALUE, 64);
        testMulHigh(someMulHigh, Long.MIN_VALUE, Long.MIN_VALUE, 64);
        testMulHigh(someMulHigh, Integer.MAX_VALUE, Integer.MAX_VALUE, 64);
        testMulHigh(someMulHigh, Long.MAX_VALUE, Long.MAX_VALUE, 64);

        assertEquals(StampFactory.forKind(JavaKind.Long).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).empty(), StampFactory.forKind(JavaKind.Long).empty()));
        assertEquals(StampFactory.forKind(JavaKind.Long).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).empty(), StampFactory.forKind(JavaKind.Long).unrestricted()));
        assertEquals(StampFactory.forKind(JavaKind.Long).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).empty(), IntegerStamp.create(64, 0, 0)));
        assertEquals(StampFactory.forKind(JavaKind.Long).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).empty(), IntegerStamp.create(64, 1, 1)));
        assertEquals(StampFactory.forKind(JavaKind.Long).empty(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).empty(), IntegerStamp.create(64, -1, -1)));

        assertEquals(StampFactory.forKind(JavaKind.Long).unrestricted(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).unrestricted(), StampFactory.forKind(JavaKind.Long).unrestricted()));
        assertEquals(StampFactory.forKind(JavaKind.Long).unrestricted(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).unrestricted(), IntegerStamp.create(64, 0, 0)));
        assertEquals(StampFactory.forKind(JavaKind.Long).unrestricted(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).unrestricted(), IntegerStamp.create(64, 1, 1)));
        assertEquals(StampFactory.forKind(JavaKind.Long).unrestricted(), someMulHigh.foldStamp(StampFactory.forKind(JavaKind.Long).unrestricted(), IntegerStamp.create(64, -1, -1)));
    }

    private static void testMulHigh(BinaryOp<?> someMulHigh, long a, long b, int bits) {
        long expectedResult = getExpectedValue(someMulHigh, a, b, bits);
        assertEquals(IntegerStamp.create(bits, expectedResult, expectedResult), someMulHigh.foldStamp(IntegerStamp.create(bits, a, a), IntegerStamp.create(bits, b, b)));
    }

    private static long getExpectedValue(BinaryOp<?> someMulHigh, long a, long b, int bits) {
        if (someMulHigh == IntegerStamp.OPS.getMulHigh()) {
            return mulHigh(a, b, bits);
        } else {
            assertEquals(IntegerStamp.OPS.getUMulHigh(), someMulHigh);
            return umulHigh(a, b, bits);
        }
    }

    private static long mulHigh(long a, long b, int bits) {
        BigInteger valA = BigInteger.valueOf(a);
        BigInteger valB = BigInteger.valueOf(b);
        BigInteger result = valA.multiply(valB).shiftRight(bits);
        if (bits == 32) {
            return result.intValue();
        } else {
            assertEquals(64, bits);
            return result.longValue();
        }
    }

    private static long umulHigh(long a, long b, int bits) {
        Assert.assertTrue(bits == 32 || bits == 64);
        BigInteger valA = BigInteger.valueOf(a);
        if (valA.compareTo(BigInteger.valueOf(0)) < 0) {
            valA = valA.add(BigInteger.ONE.shiftLeft(bits));
        }
        BigInteger valB = BigInteger.valueOf(b);
        if (valB.compareTo(BigInteger.valueOf(0)) < 0) {
            valB = valB.add(BigInteger.ONE.shiftLeft(bits));
        }

        BigInteger result = valA.multiply(valB).shiftRight(bits);
        if (bits == 32) {
            return result.intValue();
        } else {
            return result.longValue();
        }
    }

    @Test
    public void testDiv() {
        testDiv(32, Integer.MIN_VALUE, Integer.MAX_VALUE);
        testDiv(64, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private static void testDiv(int bits, long min, long max) {
        BinaryOp<?> div = IntegerStamp.OPS.getDiv();
        assertEquals(IntegerStamp.create(bits, -50, 50), div.foldStamp(IntegerStamp.create(bits, -100, 100), IntegerStamp.create(bits, 2, 5)));
        assertEquals(IntegerStamp.create(bits, 20, 500), div.foldStamp(IntegerStamp.create(bits, 100, 1000), IntegerStamp.create(bits, 2, 5)));
        assertEquals(IntegerStamp.create(bits, -500, -20), div.foldStamp(IntegerStamp.create(bits, -1000, -100), IntegerStamp.create(bits, 2, 5)));
        assertEquals(IntegerStamp.create(bits, min, max), div.foldStamp(IntegerStamp.create(bits, min, max), IntegerStamp.create(bits, 1, max)));
        assertEquals(IntegerStamp.create(bits, -100, 100), div.foldStamp(IntegerStamp.create(bits, -100, 100), IntegerStamp.create(bits, 1, max)));
        assertEquals(IntegerStamp.create(bits, 0, 1000), div.foldStamp(IntegerStamp.create(bits, 100, 1000), IntegerStamp.create(bits, 1, max)));
        assertEquals(IntegerStamp.create(bits, -1000, 0), div.foldStamp(IntegerStamp.create(bits, -1000, -100), IntegerStamp.create(bits, 1, max)));
    }

    @Test
    public void testEmpty() {
        IntegerStamp intStamp = StampFactory.forInteger(32);
        IntegerStamp longStamp = StampFactory.forInteger(64);
        Stamp intEmpty = StampFactory.empty(JavaKind.Int);
        Stamp longEmpty = StampFactory.empty(JavaKind.Long);
        assertEquals(intStamp.join(intEmpty), intEmpty);
        assertEquals(intStamp.meet(intEmpty), intStamp);
        assertEquals(longStamp.join(longEmpty), longEmpty);
        assertEquals(longStamp.meet(longEmpty), longStamp);
    }

    @Test
    public void testUnaryOpFoldEmpty() {
        // boolean?, byte, short, int, long
        Stream.of(1, 8, 16, 32, 64).map(bits -> StampFactory.forInteger(bits).empty()).forEach(empty -> {
            for (ArithmeticOpTable.UnaryOp<?> op : IntegerStamp.OPS.getUnaryOps()) {
                if (op != null) {
                    Assert.assertTrue(op.foldStamp(empty).isEmpty());
                }
            }
        });
    }

    @Test
    public void testIntegerConvertOpWithEmpty() {
        int[] bits = new int[]{1, 8, 16, 32, 64};

        List<IntegerConvertOp<?>> extendOps = Arrays.asList(
                        IntegerStamp.OPS.getSignExtend(),
                        IntegerStamp.OPS.getZeroExtend());

        for (int inputBits : bits) {
            IntegerStamp emptyIn = StampFactory.forInteger(inputBits).empty();
            for (int outputBits : bits) {
                IntegerStamp emptyOut = StampFactory.forInteger(outputBits).empty();
                if (inputBits <= outputBits) {
                    for (IntegerConvertOp<?> stamp : extendOps) {
                        IntegerStamp folded = (IntegerStamp) stamp.foldStamp(inputBits, outputBits, emptyIn);
                        Assert.assertTrue(folded.isEmpty());
                        Assert.assertEquals(outputBits, folded.getBits());

                        // Widening is lossless, inversion is well-defined.
                        IntegerStamp inverted = (IntegerStamp) stamp.invertStamp(inputBits, outputBits, emptyOut);
                        Assert.assertTrue(inverted.isEmpty());
                        Assert.assertEquals(inputBits, inverted.getBits());
                    }
                }

                if (inputBits >= outputBits) {
                    IntegerConvertOp<?> narrow = IntegerStamp.OPS.getNarrow();
                    IntegerStamp folded = (IntegerStamp) narrow.foldStamp(inputBits, outputBits, emptyIn);
                    Assert.assertTrue(folded.isEmpty());
                    Assert.assertEquals(outputBits, folded.getBits());

                    // Narrowing is lossy, inversion can potentially yield empty or unknown (null).
                    IntegerStamp inverted = (IntegerStamp) narrow.invertStamp(inputBits, outputBits, emptyOut);
                    Assert.assertTrue(inverted == null || inverted.isEmpty());
                    if (inverted != null) {
                        Assert.assertEquals(inputBits, inverted.getBits());
                    }
                }
            }
        }
    }
}
