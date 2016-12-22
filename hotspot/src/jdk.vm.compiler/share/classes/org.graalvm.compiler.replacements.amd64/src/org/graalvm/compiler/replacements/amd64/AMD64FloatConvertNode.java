/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_5;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.FloatConvertOp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.UnaryArithmeticNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * This node has the semantics of the AMD64 floating point conversions. It is used in the lowering
 * of the {@link FloatConvertNode} which, on AMD64 needs a {@link AMD64FloatConvertNode} plus some
 * fixup code that handles the corner cases that differ between AMD64 and Java.
 */
@NodeInfo(cycles = CYCLES_5, size = SIZE_1)
public final class AMD64FloatConvertNode extends UnaryArithmeticNode<FloatConvertOp> implements ArithmeticLIRLowerable {
    public static final NodeClass<AMD64FloatConvertNode> TYPE = NodeClass.create(AMD64FloatConvertNode.class);

    protected final FloatConvert op;

    public AMD64FloatConvertNode(FloatConvert op, ValueNode value) {
        super(TYPE, table -> table.getFloatConvert(op), value);
        this.op = op;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        // nothing to do
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitFloatConvert(op, nodeValueMap.operand(getValue())));
    }
}
