/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsification for getting the address of an object. The code path(s) between a call to
 * {@link #get(Object)} and all uses of the returned value must not contain safepoints. This can
 * only be guaranteed if used in a snippet that is instantiated after frame state assignment.
 * {@link ComputeObjectAddressNode} should generally be used in preference to this node.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class GetObjectAddressNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<GetObjectAddressNode> TYPE = NodeClass.create(GetObjectAddressNode.class);

    @Input ValueNode object;

    public GetObjectAddressNode(ValueNode obj) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.object = obj;
    }

    @NodeIntrinsic
    public static native long get(Object array);

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        AllocatableValue obj = gen.getLIRGeneratorTool().newVariable(LIRKind.unknownReference(gen.getLIRGeneratorTool().target().arch.getWordKind()));
        gen.getLIRGeneratorTool().emitMove(obj, gen.operand(object));
        gen.setResult(this, obj);
    }

    @Override
    public boolean verify() {
        assert graph().getGuardsStage().areFrameStatesAtDeopts() || graph().isSubstitution() : "GetObjectAddressNode can't be used directly until frame states are fixed";
        return super.verify();
    }
}
