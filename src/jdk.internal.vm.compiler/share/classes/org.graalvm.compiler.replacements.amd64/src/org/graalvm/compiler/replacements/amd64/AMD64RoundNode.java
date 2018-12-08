/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.amd64.AMD64ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.amd64.AMD64ArithmeticLIRGeneratorTool.RoundingMode;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Round floating-point value.
 */
@NodeInfo(cycles = CYCLES_1)
public final class AMD64RoundNode extends UnaryNode implements ArithmeticLIRLowerable {
    public static final NodeClass<AMD64RoundNode> TYPE = NodeClass.create(AMD64RoundNode.class);

    private final RoundingMode mode;

    public AMD64RoundNode(ValueNode value, RoundingMode mode) {
        super(TYPE, roundStamp((FloatStamp) value.stamp(NodeView.DEFAULT), mode), value);
        this.mode = mode;
    }

    private static double round(RoundingMode mode, double input) {
        switch (mode) {
            case DOWN:
                return Math.floor(input);
            case NEAREST:
                return Math.rint(input);
            case UP:
                return Math.ceil(input);
            case TRUNCATE:
                return (long) input;
            default:
                throw GraalError.unimplemented("unimplemented RoundingMode " + mode);
        }
    }

    private static FloatStamp roundStamp(FloatStamp stamp, RoundingMode mode) {
        double min = stamp.lowerBound();
        min = Math.min(min, round(mode, min));

        double max = stamp.upperBound();
        max = Math.max(max, round(mode, max));

        return new FloatStamp(stamp.getBits(), min, max, stamp.isNonNaN());
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        return roundStamp((FloatStamp) newStamp, mode);
    }

    public ValueNode tryFold(ValueNode input) {
        if (input.isConstant()) {
            JavaConstant c = input.asJavaConstant();
            if (c.getJavaKind() == JavaKind.Double) {
                return ConstantNode.forDouble(round(mode, c.asDouble()));
            } else if (c.getJavaKind() == JavaKind.Float) {
                return ConstantNode.forFloat((float) round(mode, c.asFloat()));
            }
        }
        return null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode folded = tryFold(forValue);
        return folded != null ? folded : this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, ((AMD64ArithmeticLIRGeneratorTool) gen).emitRound(builder.operand(getValue()), mode));
    }
}
