/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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


package org.graalvm.compiler.core.aarch64;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(nameTemplate = "AArch64PointerAdd", cycles = CYCLES_1, size = SIZE_1)
public class AArch64PointerAddNode extends FloatingNode implements ArithmeticLIRLowerable {

    public static final NodeClass<AArch64PointerAddNode> TYPE = NodeClass.create(AArch64PointerAddNode.class);

    @Input ValueNode base;
    @Input ValueNode offset;

    public AArch64PointerAddNode(ValueNode base, ValueNode offset) {
        super(TYPE, StampFactory.pointer());
        this.base = base;
        this.offset = offset;
        assert base != null && (base.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp ||
                        IntegerStamp.getBits(base.stamp(NodeView.DEFAULT)) == 64);
        assert offset != null && offset.getStackKind().isNumericInteger();
    }

    public ValueNode getBase() {
        return base;
    }

    public ValueNode getOffset() {
        return offset;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        LIRGeneratorTool tool = builder.getLIRGeneratorTool();
        Value x = builder.operand(base);
        Value y = builder.operand(offset);
        AllocatableValue baseValue = tool.asAllocatable(x);
        AllocatableValue baseReference = LIRKind.derivedBaseFromValue(baseValue);
        LIRKind kind = LIRKind.combineDerived(tool.getLIRKind(stamp(NodeView.DEFAULT)), baseReference, null);
        builder.setResult(this, ((AArch64ArithmeticLIRGenerator) gen).emitBinary(kind, AArch64ArithmeticOp.ADD, true, x, y));
    }
}
