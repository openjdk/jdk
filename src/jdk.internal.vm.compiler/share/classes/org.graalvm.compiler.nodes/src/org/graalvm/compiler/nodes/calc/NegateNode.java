/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.UnaryOp.Neg;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.StampInverter;

/**
 * The {@code NegateNode} node negates its operand.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class NegateNode extends UnaryArithmeticNode<Neg> implements NarrowableArithmeticNode, StampInverter {

    public static final NodeClass<NegateNode> TYPE = NodeClass.create(NegateNode.class);

    public NegateNode(ValueNode value) {
        super(TYPE, getArithmeticOpTable(value).getNeg(), value);
    }

    public static ValueNode create(ValueNode value, NodeView view) {
        ValueNode synonym = findSynonym(value, view);
        if (synonym != null) {
            return synonym;
        }
        return new NegateNode(value);
    }

    @Override
    protected UnaryOp<Neg> getOp(ArithmeticOpTable table) {
        return table.getNeg();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode synonym = findSynonym(forValue, getOp(forValue));
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    protected static ValueNode findSynonym(ValueNode forValue, NodeView view) {
        ArithmeticOpTable.UnaryOp<Neg> negOp = ArithmeticOpTable.forStamp(forValue.stamp(view)).getNeg();

        // Folds constants
        ValueNode synonym = UnaryArithmeticNode.findSynonym(forValue, negOp);
        if (synonym != null) {
            return synonym;
        }
        if (forValue instanceof NegateNode) {
            return ((NegateNode) forValue).getValue();
        }
        if (forValue instanceof SubNode && !(forValue.stamp(view) instanceof FloatStamp)) {
            SubNode sub = (SubNode) forValue;
            return SubNode.create(sub.getY(), sub.getX(), view);
        }
        // e.g. -(x >> 31) => x >>> 31
        if (forValue instanceof RightShiftNode) {
            RightShiftNode shift = (RightShiftNode) forValue;
            Stamp stamp = forValue.stamp(view);
            if (shift.getY().isConstant() && stamp instanceof IntegerStamp) {
                int shiftAmount = shift.getY().asJavaConstant().asInt();
                if (shiftAmount == ((IntegerStamp) stamp).getBits() - 1) {
                    return UnsignedRightShiftNode.create(shift.getX(), shift.getY(), view);
                }
            }
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitNegate(nodeValueMap.operand(getValue())));
    }

    @Override
    public Stamp invertStamp(Stamp outStamp) {
        return getArithmeticOp().foldStamp(outStamp);
    }
}
