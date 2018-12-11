/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat Inc. All rights reserved.
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

import jdk.vm.ci.aarch64.AArch64Kind;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

/**
 * AArch64-specific subclass of ReadNode that knows how to merge ZeroExtend and SignExtend into the
 * read.
 */

@NodeInfo
public class AArch64ReadNode extends ReadNode {
    public static final NodeClass<AArch64ReadNode> TYPE = NodeClass.create(AArch64ReadNode.class);
    private final IntegerStamp accessStamp;
    private final boolean isSigned;

    public AArch64ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck,
                    FrameState stateBefore, IntegerStamp accessStamp, boolean isSigned) {
        super(TYPE, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
        this.accessStamp = accessStamp;
        this.isSigned = isSigned;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        AArch64LIRGenerator lirgen = (AArch64LIRGenerator) gen.getLIRGeneratorTool();
        AArch64ArithmeticLIRGenerator arithgen = (AArch64ArithmeticLIRGenerator) lirgen.getArithmetic();
        AArch64Kind readKind = (AArch64Kind) lirgen.getLIRKind(accessStamp).getPlatformKind();
        int resultBits = ((IntegerStamp) stamp(NodeView.DEFAULT)).getBits();
        gen.setResult(this, arithgen.emitExtendMemory(isSigned, readKind, resultBits, (AArch64AddressValue) gen.operand(getAddress()), gen.state(this)));
    }

    /**
     * replace a ReadNode with an AArch64-specific variant which knows how to merge a downstream
     * zero or sign extend into the read operation.
     *
     * @param readNode
     */
    public static void replace(ReadNode readNode) {
        assert readNode.getUsageCount() == 1;
        assert readNode.getUsageAt(0) instanceof ZeroExtendNode || readNode.getUsageAt(0) instanceof SignExtendNode;

        ValueNode usage = (ValueNode) readNode.getUsageAt(0);
        boolean isSigned = usage instanceof SignExtendNode;
        IntegerStamp accessStamp = ((IntegerStamp) readNode.getAccessStamp());

        AddressNode address = readNode.getAddress();
        LocationIdentity location = readNode.getLocationIdentity();
        Stamp stamp = usage.stamp(NodeView.DEFAULT);
        GuardingNode guard = readNode.getGuard();
        BarrierType barrierType = readNode.getBarrierType();
        boolean nullCheck = readNode.getNullCheck();
        FrameState stateBefore = readNode.stateBefore();
        AArch64ReadNode clone = new AArch64ReadNode(address, location, stamp, guard, barrierType, nullCheck, stateBefore, accessStamp, isSigned);
        StructuredGraph graph = readNode.graph();
        graph.add(clone);
        // splice out the extend node
        usage.replaceAtUsagesAndDelete(readNode);
        // swap the clone for the read
        graph.replaceFixedWithFixed(readNode, clone);
    }
}
