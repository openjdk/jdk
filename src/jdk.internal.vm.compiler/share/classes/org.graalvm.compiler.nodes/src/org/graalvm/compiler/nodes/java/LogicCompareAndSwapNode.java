/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Represents the low-level version of an atomic compare-and-swap operation.
 *
 * This version returns a boolean indicating is the CAS was successful or not.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_8)
public final class LogicCompareAndSwapNode extends AbstractCompareAndSwapNode {
    public static final NodeClass<LogicCompareAndSwapNode> TYPE = NodeClass.create(LogicCompareAndSwapNode.class);

    public LogicCompareAndSwapNode(ValueNode address, ValueNode expectedValue, ValueNode newValue, LocationIdentity location) {
        this((AddressNode) address, location, expectedValue, newValue, BarrierType.NONE);
    }

    public LogicCompareAndSwapNode(AddressNode address, LocationIdentity location, ValueNode expectedValue, ValueNode newValue, BarrierType barrierType) {
        super(TYPE, address, location, expectedValue, newValue, barrierType, StampFactory.forInteger(JavaKind.Int, 0, 1));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert getNewValue().stamp(NodeView.DEFAULT).isCompatible(getExpectedValue().stamp(NodeView.DEFAULT));
        assert !this.canDeoptimize();
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();

        LIRKind resultKind = tool.getLIRKind(stamp(NodeView.DEFAULT));
        Value trueResult = tool.emitConstant(resultKind, JavaConstant.TRUE);
        Value falseResult = tool.emitConstant(resultKind, JavaConstant.FALSE);
        Value result = tool.emitLogicCompareAndSwap(tool.getLIRKind(getAccessStamp()), gen.operand(getAddress()), gen.operand(getExpectedValue()), gen.operand(getNewValue()), trueResult, falseResult);

        gen.setResult(this, result);
    }
}
