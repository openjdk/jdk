/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_10;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_6;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Emits code to leave (pop) the current low-level stack frame. This operation also removes the
 * return address if its location is on the stack.
 */
@NodeInfo(cycles = CYCLES_10, size = SIZE_6)
public final class LeaveCurrentStackFrameNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<LeaveCurrentStackFrameNode> TYPE = NodeClass.create(LeaveCurrentStackFrameNode.class);
    @Input SaveAllRegistersNode registerSaver;

    public LeaveCurrentStackFrameNode(ValueNode registerSaver) {
        super(TYPE, StampFactory.forVoid());
        this.registerSaver = (SaveAllRegistersNode) registerSaver;
    }

    private SaveRegistersOp getSaveRegistersOp() {
        return registerSaver.getSaveRegistersOp();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitLeaveCurrentStackFrame(getSaveRegistersOp());
    }

    @NodeIntrinsic
    public static native void leaveCurrentStackFrame(long registerSaver);
}
