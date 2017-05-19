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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_4;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_4;

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

@NodeInfo(shortName = "|*H|", cycles = CYCLES_4, cyclesRationale = "mul + mov", size = SIZE_4)
public final class UnsignedMulHighNode extends BinaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<UnsignedMulHighNode> TYPE = NodeClass.create(UnsignedMulHighNode.class);

    public UnsignedMulHighNode(ValueNode x, ValueNode y) {
        this((IntegerStamp) x.stamp().unrestricted(), x, y);
    }

    public UnsignedMulHighNode(IntegerStamp stamp, ValueNode x, ValueNode y) {
        super(TYPE, stamp, x, y);
    }

    private static long[] getUnsignedExtremes(IntegerStamp stamp) {
        if (stamp.lowerBound() < 0 && stamp.upperBound() >= 0) {
            /*
             * If -1 and 0 are both in the signed range, then we can't say anything about the
             * unsigned range, so we have to return [0, MAX_UNSIGNED].
             */
            return new long[]{0, -1L};
        } else {
            return new long[]{stamp.lowerBound(), stamp.upperBound()};
        }
    }

    /**
     * Determines the minimum and maximum result of this node for the given inputs and returns the
     * result of the given BiFunction on the minimum and maximum values. Note that the minima and
     * maxima are calculated using signed min/max functions, while the values themselves are
     * unsigned.
     */
    private <T> T processExtremes(Stamp forX, Stamp forY, BiFunction<Long, Long, T> op) {
        IntegerStamp xStamp = (IntegerStamp) forX;
        IntegerStamp yStamp = (IntegerStamp) forY;

        JavaKind kind = getStackKind();
        assert kind == JavaKind.Int || kind == JavaKind.Long;
        long[] xExtremes = getUnsignedExtremes(xStamp);
        long[] yExtremes = getUnsignedExtremes(yStamp);
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long a : xExtremes) {
            for (long b : yExtremes) {
                long result = kind == JavaKind.Int ? multiplyHighUnsigned((int) a, (int) b) : multiplyHighUnsigned(a, b);
                min = Math.min(min, result);
                max = Math.max(max, result);
            }
        }
        return op.apply(min, max);
    }

    @SuppressWarnings("cast")
    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        // if min is negative, then the value can reach into the unsigned range
        return processExtremes(stampX, stampY, (min, max) -> (min == (long) max || min >= 0) ? StampFactory.forInteger(getStackKind(), min, max) : StampFactory.forKind(getStackKind()));
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
        nodeValueMap.setResult(this, gen.emitUMulHigh(a, b));
    }

    public static int multiplyHighUnsigned(int x, int y) {
        long xl = x & 0xFFFFFFFFL;
        long yl = y & 0xFFFFFFFFL;
        long r = xl * yl;
        return (int) (r >> 32);
    }

    public static long multiplyHighUnsigned(long x, long y) {
        // Checkstyle: stop
        long x0, y0, z0;
        long x1, y1, z1, z2, t;
        // Checkstyle: resume

        x0 = x & 0xFFFFFFFFL;
        x1 = x >>> 32;

        y0 = y & 0xFFFFFFFFL;
        y1 = y >>> 32;

        z0 = x0 * y0;
        t = x1 * y0 + (z0 >>> 32);
        z1 = t & 0xFFFFFFFFL;
        z2 = t >>> 32;
        z1 += x0 * y1;

        return x1 * y1 + z2 + (z1 >>> 32);
    }
}
