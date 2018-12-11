/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.replacements.nodes.BitScanReverseNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.sparc.SPARC;

public class BitOpNodesTest extends GraalCompilerTest {

    private static final int INT_CONSTANT_1 = 0x80100010;
    private static final int INT_CONSTANT_2 = 0x00011110;
    private static final int INT_CONSTANT_3 = 0x00000000;

    private static final long LONG_CONSTANT_1 = 0x8000000000100010L;
    private static final long LONG_CONSTANT_2 = 0x0000000000011110L;
    private static final long LONG_CONSTANT_3 = 0x0000000000000000L;

    public static long dummyField;

    /*
     * Tests for BitCountNode canonicalizations.
     */

    /**
     * Determines if the current VM context supports intrinsics for the {@code bitCount} methods in
     * {@link Integer} and {@link Long}.
     */
    public static boolean isBitCountIntrinsicSupported(Architecture arch) {
        if (arch instanceof AMD64) {
            AMD64 amd64 = (AMD64) arch;
            return amd64.getFeatures().contains(AMD64.CPUFeature.POPCNT);
        } else {
            // Even though there are AArch64 intrinsics for bitCount, they do
            // not use BitCountNode.
            return arch instanceof SPARC;
        }
    }

    /**
     * Determines if the current VM context supports intrinsics for the {@code numberOfLeadingZeros}
     * methods in {@link Integer} and {@link Long}.
     */
    public static boolean isNumberLeadingZerosIntrinsicSupported(Architecture arch) {
        if (arch instanceof AMD64) {
            AMD64 amd64 = (AMD64) arch;
            return amd64.getFeatures().contains(AMD64.CPUFeature.LZCNT) && amd64.getFlags().contains(AMD64.Flag.UseCountLeadingZerosInstruction);
        } else {
            return arch instanceof SPARC || arch instanceof AArch64;
        }
    }

    /**
     * Determines if the current VM context supports intrinsics for the
     * {@code numberOfTrailingZeros} methods in {@link Integer} and {@link Long}.
     */
    public static boolean isNumberTrailingZerosIntrinsicSupported(Architecture arch) {
        if (arch instanceof AMD64) {
            AMD64 amd64 = (AMD64) arch;
            return amd64.getFeatures().contains(AMD64.CPUFeature.BMI1) && amd64.getFlags().contains(AMD64.Flag.UseCountTrailingZerosInstruction);
        } else {
            return arch instanceof SPARC || arch instanceof AArch64;
        }
    }

    public static int bitCountIntConstantSnippet() {
        return Integer.bitCount(INT_CONSTANT_1) + Integer.bitCount(INT_CONSTANT_2) + Integer.bitCount(INT_CONSTANT_3);
    }

    @Test
    public void testBitCountIntConstant() {
        ValueNode result = parseAndInline("bitCountIntConstantSnippet");
        Assert.assertEquals(7, result.asJavaConstant().asInt());
    }

    public static int bitCountLongConstantSnippet() {
        return Long.bitCount(LONG_CONSTANT_1) + Long.bitCount(LONG_CONSTANT_2) + Long.bitCount(LONG_CONSTANT_3);
    }

    public static int bitCountIntSnippet(int v) {
        return Integer.bitCount(v & 0xFFFFFF | 0xFF);
    }

    @Test
    public void testBitCountInt() {
        Assume.assumeTrue(isBitCountIntrinsicSupported(getBackend().getTarget().arch));
        ValueNode result = parseAndInline("bitCountIntSnippet");
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 8, 24), result.stamp(NodeView.DEFAULT));
    }

    public static int bitCountIntEmptySnippet(int v) {
        return Integer.bitCount(v & 0xFFFFFF);
    }

    @Test
    public void testBitCountIntEmpty() {
        Assume.assumeTrue(isBitCountIntrinsicSupported(getBackend().getTarget().arch));
        ValueNode result = parseAndInline("bitCountIntEmptySnippet");
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 24), result.stamp(NodeView.DEFAULT));
    }

    @Test
    public void testBitCountLongConstant() {
        ValueNode result = parseAndInline("bitCountLongConstantSnippet");
        Assert.assertEquals(7, result.asJavaConstant().asInt());
    }

    public static int bitCountLongSnippet(long v) {
        return Long.bitCount(v & 0xFFFFFFFFFFL | 0xFFL);
    }

    @Test
    public void testBitCountLong() {
        Assume.assumeTrue(isBitCountIntrinsicSupported(getBackend().getTarget().arch));
        ValueNode result = parseAndInline("bitCountLongSnippet");
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 8, 40), result.stamp(NodeView.DEFAULT));
    }

    public static int bitCountLongEmptySnippet(long v) {
        return Long.bitCount(v & 0xFFFFFFFFFFL);
    }

    @Test
    public void testBitCountLongEmpty() {
        Assume.assumeTrue(isBitCountIntrinsicSupported(getBackend().getTarget().arch));
        ValueNode result = parseAndInline("bitCountLongEmptySnippet");
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 0, 40), result.stamp(NodeView.DEFAULT));
    }

    /*
     * Tests for BitScanForwardNode
     */

    public static int scanForwardIntConstantSnippet() {
        return Integer.numberOfTrailingZeros(INT_CONSTANT_1) + Integer.numberOfTrailingZeros(INT_CONSTANT_2) + Integer.numberOfTrailingZeros(INT_CONSTANT_3);
    }

    @Test
    public void testScanForwardIntConstant() {
        ValueNode result = parseAndInline("scanForwardIntConstantSnippet");
        Assert.assertEquals(40, result.asJavaConstant().asInt());
    }

    public static int scanForwardIntSnippet(int v) {
        return Integer.numberOfTrailingZeros(v & 0xFFF0 | 0xFF00);
    }

    @Test
    public void testScanForwardInt() {
        ValueNode result = parseAndInline("scanForwardIntSnippet");
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 4, 8), result.stamp(NodeView.DEFAULT));
    }

    public static int scanForwardLongConstantSnippet() {
        return Long.numberOfTrailingZeros(LONG_CONSTANT_1) + Long.numberOfTrailingZeros(LONG_CONSTANT_2) + Long.numberOfTrailingZeros(LONG_CONSTANT_3);
    }

    @Test
    public void testScanForwardLongConstant() {
        ValueNode result = parseAndInline("scanForwardLongConstantSnippet");
        Assert.assertEquals(72, result.asJavaConstant().asInt());
    }

    public static int scanForwardLongSnippet(long v) {
        return Long.numberOfTrailingZeros(v & 0xFFFF000000L | 0xFF00000000L);
    }

    @Test
    public void testScanForwardLong() {
        ValueNode result = parseAndInline("scanForwardLongSnippet");
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 24, 32), result.stamp(NodeView.DEFAULT));
    }

    public static int scanForwardLongEmptySnippet(long v) {
        int result = Long.numberOfTrailingZeros(v & 0xFFFF000000L);
        dummyField = result;
        return result;
    }

    @Test
    public void testScanForwardLongEmpty() {
        ValueNode result = parseAndInline("scanForwardLongEmptySnippet");
        Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 24, 64), result.stamp(NodeView.DEFAULT));
    }

    /*
     * Tests for BitScanReverseNode
     */

    public static int scanReverseIntConstantSnippet() {
        return Integer.numberOfLeadingZeros(INT_CONSTANT_1) + Integer.numberOfLeadingZeros(INT_CONSTANT_2) + Integer.numberOfLeadingZeros(INT_CONSTANT_3);
    }

    @Test
    public void testScanReverseIntConstant() {
        ValueNode result = parseAndInline("scanReverseIntConstantSnippet");
        Assert.assertEquals(47, result.asJavaConstant().asInt());
    }

    public static int scanReverseIntSnippet(int v) {
        return Integer.numberOfLeadingZeros(v & 0xFFF0 | 0xFF0);
    }

    @Test
    public void testScanReverseInt() {
        /* This test isn't valid unless the BitScanReverseNode intrinsic is used. */
        ValueNode result = parseAndInline("scanReverseIntSnippet", BitScanReverseNode.class);
        if (result != null) {
            Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 16, 20), result.stamp(NodeView.DEFAULT));
        }
    }

    public static int scanReverseLongConstantSnippet() {
        return Long.numberOfLeadingZeros(LONG_CONSTANT_1) + Long.numberOfLeadingZeros(LONG_CONSTANT_2) + Long.numberOfLeadingZeros(LONG_CONSTANT_3);
    }

    @Test
    public void testScanReverseLongConstant() {
        ValueNode result = parseAndInline("scanReverseLongConstantSnippet");
        Assert.assertEquals(111, result.asJavaConstant().asInt());
    }

    public static int scanReverseLongSnippet(long v) {
        int result = Long.numberOfLeadingZeros(v & 0xFFF0);
        dummyField = result;
        return result;
    }

    @Test
    public void testScanReverseLong() {
        /* This test isn't valid unless the BitScanReverseNode intrinsic is used. */
        ValueNode result = parseAndInline("scanReverseLongSnippet", BitScanReverseNode.class);
        if (result != null) {
            Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 48, 64), result.stamp(NodeView.DEFAULT));
        }
    }

    public static int scanReverseLongEmptySnippet(long v) {
        int result = Long.numberOfLeadingZeros(v & 0xFFFF000000L);
        dummyField = result;
        return result;
    }

    @Test
    public void testScanReverseLongEmpty() {
        /* This test isn't valid unless the BitScanReverseNode intrinsic is used. */
        ValueNode result = parseAndInline("scanReverseLongEmptySnippet", BitScanReverseNode.class);
        if (result != null) {
            Assert.assertEquals(StampFactory.forInteger(JavaKind.Int, 24, 64), result.stamp(NodeView.DEFAULT));
        }
    }

    private ValueNode parseAndInline(String name) {
        return parseAndInline(name, null);
    }

    /**
     * Parse and optimize {@code name}. If {@code expectedClass} is non-null and a node of that type
     * isn't found simply return null. Otherwise return the node returned by the graph.
     *
     * @param name
     * @param expectedClass
     * @return the returned value or null if {@code expectedClass} is not found in the graph.
     */
    private ValueNode parseAndInline(String name, Class<? extends ValueNode> expectedClass) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.YES);
        HighTierContext context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        canonicalizer.apply(graph, context);
        createInliningPhase(canonicalizer).apply(graph, context);
        canonicalizer.apply(graph, context);
        Assert.assertEquals(1, graph.getNodes(ReturnNode.TYPE).count());
        if (expectedClass != null) {
            if (graph.getNodes().filter(expectedClass).count() == 0) {
                return null;
            }
        }
        return graph.getNodes(ReturnNode.TYPE).first().result();
    }
}
