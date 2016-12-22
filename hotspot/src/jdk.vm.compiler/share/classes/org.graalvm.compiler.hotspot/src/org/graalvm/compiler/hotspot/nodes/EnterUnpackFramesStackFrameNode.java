/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_20;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_10;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.meta.Value;

/**
 * Emits code to enter a low-level stack frame specifically to call out to the C++ method
 * {@link HotSpotBackend#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@NodeInfo(cycles = CYCLES_20, size = SIZE_10)
public final class EnterUnpackFramesStackFrameNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<EnterUnpackFramesStackFrameNode> TYPE = NodeClass.create(EnterUnpackFramesStackFrameNode.class);

    @Input ValueNode framePc;
    @Input ValueNode senderSp;
    @Input ValueNode senderFp;
    @Input SaveAllRegistersNode registerSaver;

    public EnterUnpackFramesStackFrameNode(ValueNode framePc, ValueNode senderSp, ValueNode senderFp, ValueNode registerSaver) {
        super(TYPE, StampFactory.forVoid());
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.senderFp = senderFp;
        this.registerSaver = (SaveAllRegistersNode) registerSaver;
    }

    private SaveRegistersOp getSaveRegistersOp() {
        return registerSaver.getSaveRegistersOp();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value operandValue = gen.operand(framePc);
        Value senderSpValue = gen.operand(senderSp);
        Value senderFpValue = gen.operand(senderFp);
        ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitEnterUnpackFramesStackFrame(operandValue, senderSpValue, senderFpValue, getSaveRegistersOp());
    }

    @NodeIntrinsic
    public static native void enterUnpackFramesStackFrame(Word framePc, Word senderSp, Word senderFp, long registerSaver);
}
