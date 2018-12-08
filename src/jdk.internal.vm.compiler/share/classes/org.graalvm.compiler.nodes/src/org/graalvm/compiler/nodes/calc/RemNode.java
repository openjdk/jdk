/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_32;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Rem;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName = "%", cycles = CYCLES_32/* div */)
public class RemNode extends BinaryArithmeticNode<Rem> implements Lowerable {

    public static final NodeClass<RemNode> TYPE = NodeClass.create(RemNode.class);

    protected RemNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected RemNode(NodeClass<? extends RemNode> c, ValueNode x, ValueNode y) {
        super(c, ArithmeticOpTable::getRem, x, y);
    }

    public static ValueNode create(ValueNode forX, ValueNode forY, NodeView view) {
        BinaryOp<Rem> op = ArithmeticOpTable.forStamp(forX.stamp(view)).getRem();
        Stamp stamp = op.foldStamp(forX.stamp(view), forY.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, forX, forY, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return new RemNode(forX, forY);
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitRem(nodeValueMap.operand(getX()), nodeValueMap.operand(getY()), null));
    }
}
