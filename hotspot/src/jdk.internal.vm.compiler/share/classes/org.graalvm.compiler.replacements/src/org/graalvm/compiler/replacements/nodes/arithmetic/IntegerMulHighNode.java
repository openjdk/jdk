/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes.arithmetic;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.function.BiFunction;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "*H", cycles = CYCLES_2, size = SIZE_2)
public final class IntegerMulHighNode extends BinaryNode implements ArithmeticLIRLowerable {
    public static final NodeClass<IntegerMulHighNode> TYPE = NodeClass.create(IntegerMulHighNode.class);

    public IntegerMulHighNode(ValueNode x, ValueNode y) {
        this((IntegerStamp) x.stamp().unrestricted(), x, y);
    }

    public IntegerMulHighNode(IntegerStamp stamp, ValueNode x, ValueNode y) {
        super(TYPE, stamp, x, y);
    }

    /**
     * Determines the minimum and maximum result of this node for the given inputs and returns the
     * result of the given BiFunction on the minimum and maximum values.
     */
    private <T> T processExtremes(Stamp forX, Stamp forY, BiFunction<Long, Long, T> op) {
        IntegerStamp xStamp = (IntegerStamp) forX;
        IntegerStamp yStamp = (IntegerStamp) forY;

        JavaKind kind = getStackKind();
        assert kind == JavaKind.Int || kind == JavaKind.Long;
        long[] xExtremes = {xStamp.lowerBound(), xStamp.upperBound()};
        long[] yExtremes = {yStamp.lowerBound(), yStamp.upperBound()};
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long a : xExtremes) {
            for (long b : yExtremes) {
                long result = kind == JavaKind.Int ? multiplyHigh((int) a, (int) b) : multiplyHigh(a, b);
                min = Math.min(min, result);
                max = Math.max(max, result);
            }
        }
        return op.apply(min, max);
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return processExtremes(stampX, stampY, (min, max) -> StampFactory.forInteger(getStackKind(), min, max));
    }

    @SuppressWarnings("cast")
    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return processExtremes(forX.stamp(), forY.stamp(), (min, max) -> min == (long) max ? ConstantNode.forIntegerKind(getStackKind(), min) : this);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        Value a = nodeValueMap.operand(getX());
        Value b = nodeValueMap.operand(getY());
        nodeValueMap.setResult(this, gen.emitMulHigh(a, b));
    }

    public static int multiplyHigh(int x, int y) {
        long r = (long) x * (long) y;
        return (int) (r >> 32);
    }

    public static long multiplyHigh(long x, long y) {
        // Checkstyle: stop
        long x0, y0, z0;
        long x1, y1, z1, z2, t;
        // Checkstyle: resume

        x0 = x & 0xFFFFFFFFL;
        x1 = x >> 32;

        y0 = y & 0xFFFFFFFFL;
        y1 = y >> 32;

        z0 = x0 * y0;
        t = x1 * y0 + (z0 >>> 32);
        z1 = t & 0xFFFFFFFFL;
        z2 = t >> 32;
        z1 += x0 * y1;

        return x1 * y1 + z2 + (z1 >> 32);
    }
}
