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

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.Narrow;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.SignExtend;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * The {@code SignExtendNode} converts an integer to a wider integer using sign extension.
 */
@NodeInfo(cycles = CYCLES_1)
public final class SignExtendNode extends IntegerConvertNode<SignExtend, Narrow> {

    public static final NodeClass<SignExtendNode> TYPE = NodeClass.create(SignExtendNode.class);

    public SignExtendNode(ValueNode input, int resultBits) {
        this(input, PrimitiveStamp.getBits(input.stamp()), resultBits);
        assert 0 < PrimitiveStamp.getBits(input.stamp()) && PrimitiveStamp.getBits(input.stamp()) <= resultBits;
    }

    public SignExtendNode(ValueNode input, int inputBits, int resultBits) {
        super(TYPE, ArithmeticOpTable::getSignExtend, ArithmeticOpTable::getNarrow, inputBits, resultBits, input);
    }

    public static ValueNode create(ValueNode input, int resultBits) {
        return create(input, PrimitiveStamp.getBits(input.stamp()), resultBits);
    }

    public static ValueNode create(ValueNode input, int inputBits, int resultBits) {
        IntegerConvertOp<SignExtend> signExtend = ArithmeticOpTable.forStamp(input.stamp()).getSignExtend();
        ValueNode synonym = findSynonym(signExtend, input, inputBits, resultBits, signExtend.foldStamp(inputBits, resultBits, input.stamp()));
        if (synonym != null) {
            return synonym;
        } else {
            return new SignExtendNode(input, inputBits, resultBits);
        }
    }

    @Override
    public boolean isLossless() {
        return true;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        if (forValue instanceof SignExtendNode) {
            // sxxx -(sign-extend)-> ssss sxxx -(sign-extend)-> ssssssss sssssxxx
            // ==> sxxx -(sign-extend)-> ssssssss sssssxxx
            SignExtendNode other = (SignExtendNode) forValue;
            return new SignExtendNode(other.getValue(), other.getInputBits(), getResultBits());
        } else if (forValue instanceof ZeroExtendNode) {
            ZeroExtendNode other = (ZeroExtendNode) forValue;
            if (other.getResultBits() > other.getInputBits()) {
                // sxxx -(zero-extend)-> 0000 sxxx -(sign-extend)-> 00000000 0000sxxx
                // ==> sxxx -(zero-extend)-> 00000000 0000sxxx
                return new ZeroExtendNode(other.getValue(), other.getInputBits(), getResultBits());
            }
        }

        if (forValue.stamp() instanceof IntegerStamp) {
            IntegerStamp inputStamp = (IntegerStamp) forValue.stamp();
            if ((inputStamp.upMask() & (1L << (getInputBits() - 1))) == 0L) {
                // 0xxx -(sign-extend)-> 0000 0xxx
                // ==> 0xxx -(zero-extend)-> 0000 0xxx
                return new ZeroExtendNode(forValue, getInputBits(), getResultBits());
            }
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitSignExtend(nodeValueMap.operand(getValue()), getInputBits(), getResultBits()));
    }
}
