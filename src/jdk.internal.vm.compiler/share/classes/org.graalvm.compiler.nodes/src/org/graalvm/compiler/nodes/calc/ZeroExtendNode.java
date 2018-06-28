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


package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.Narrow;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.ZeroExtend;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;

/**
 * The {@code ZeroExtendNode} converts an integer to a wider integer using zero extension.
 */
@NodeInfo(cycles = CYCLES_1)
public final class ZeroExtendNode extends IntegerConvertNode<ZeroExtend, Narrow> {

    public static final NodeClass<ZeroExtendNode> TYPE = NodeClass.create(ZeroExtendNode.class);

    private final boolean inputAlwaysPositive;

    public ZeroExtendNode(ValueNode input, int resultBits) {
        this(input, PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)), resultBits, false);
        assert 0 < PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) && PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) <= resultBits;
    }

    public ZeroExtendNode(ValueNode input, int inputBits, int resultBits, boolean inputAlwaysPositive) {
        super(TYPE, ArithmeticOpTable::getZeroExtend, ArithmeticOpTable::getNarrow, inputBits, resultBits, input);
        this.inputAlwaysPositive = inputAlwaysPositive;
    }

    public static ValueNode create(ValueNode input, int resultBits, NodeView view) {
        return create(input, PrimitiveStamp.getBits(input.stamp(view)), resultBits, view, false);
    }

    public static ValueNode create(ValueNode input, int inputBits, int resultBits, NodeView view) {
        return create(input, inputBits, resultBits, view, false);
    }

    public static ValueNode create(ValueNode input, int inputBits, int resultBits, NodeView view, boolean alwaysPositive) {
        IntegerConvertOp<ZeroExtend> signExtend = ArithmeticOpTable.forStamp(input.stamp(view)).getZeroExtend();
        ValueNode synonym = findSynonym(signExtend, input, inputBits, resultBits, signExtend.foldStamp(inputBits, resultBits, input.stamp(view)));
        if (synonym != null) {
            return synonym;
        }
        return canonical(null, input, inputBits, resultBits, view, alwaysPositive);
    }

    @Override
    public boolean isLossless() {
        return true;
    }

    public boolean isInputAlwaysPositive() {
        return inputAlwaysPositive;
    }

    @Override
    public boolean preservesOrder(CanonicalCondition cond) {
        switch (cond) {
            case LT:
                return false;
            default:
                return true;
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        return canonical(this, forValue, getInputBits(), getResultBits(), view, inputAlwaysPositive);
    }

    private static ValueNode canonical(ZeroExtendNode zeroExtendNode, ValueNode forValue, int inputBits, int resultBits, NodeView view, boolean alwaysPositive) {
        ZeroExtendNode self = zeroExtendNode;
        if (forValue instanceof ZeroExtendNode) {
            // xxxx -(zero-extend)-> 0000 xxxx -(zero-extend)-> 00000000 0000xxxx
            // ==> xxxx -(zero-extend)-> 00000000 0000xxxx
            ZeroExtendNode other = (ZeroExtendNode) forValue;
            return new ZeroExtendNode(other.getValue(), other.getInputBits(), resultBits, other.isInputAlwaysPositive());
        }
        if (forValue instanceof NarrowNode) {
            NarrowNode narrow = (NarrowNode) forValue;
            Stamp inputStamp = narrow.getValue().stamp(view);
            if (inputStamp instanceof IntegerStamp) {
                IntegerStamp istamp = (IntegerStamp) inputStamp;
                long mask = CodeUtil.mask(PrimitiveStamp.getBits(narrow.stamp(view)));

                if ((istamp.upMask() & ~mask) == 0) {
                    // The original value cannot change because of the narrow and zero extend.

                    if (istamp.getBits() < resultBits) {
                        // Need to keep the zero extend, skip the narrow.
                        return create(narrow.getValue(), resultBits, view);
                    } else if (istamp.getBits() > resultBits) {
                        // Need to keep the narrow, skip the zero extend.
                        return NarrowNode.create(narrow.getValue(), resultBits, view);
                    } else {
                        assert istamp.getBits() == resultBits;
                        // Just return the original value.
                        return narrow.getValue();
                    }
                }
            }
        }

        if (self == null) {
            self = new ZeroExtendNode(forValue, inputBits, resultBits, alwaysPositive);
        }
        return self;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitZeroExtend(nodeValueMap.operand(getValue()), getInputBits(), getResultBits()));
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return true;
    }
}
