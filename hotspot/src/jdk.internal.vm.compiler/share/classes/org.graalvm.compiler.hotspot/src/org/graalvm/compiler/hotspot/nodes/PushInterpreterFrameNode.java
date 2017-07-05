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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_6;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.meta.Value;

/**
 * A call to the runtime code implementing the uncommon trap logic.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_6)
public final class PushInterpreterFrameNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<PushInterpreterFrameNode> TYPE = NodeClass.create(PushInterpreterFrameNode.class);
    @Input ValueNode framePc;
    @Input ValueNode frameSize;
    @Input ValueNode senderSp;
    @Input ValueNode initialInfo;

    public PushInterpreterFrameNode(ValueNode frameSize, ValueNode framePc, ValueNode senderSp, ValueNode initialInfo) {
        super(TYPE, StampFactory.forVoid());
        this.frameSize = frameSize;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.initialInfo = initialInfo;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value frameSizeValue = gen.operand(frameSize);
        Value framePcValue = gen.operand(framePc);
        Value senderSpValue = gen.operand(senderSp);
        Value initialInfoValue = gen.operand(initialInfo);
        ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitPushInterpreterFrame(frameSizeValue, framePcValue, senderSpValue, initialInfoValue);
    }

    @NodeIntrinsic
    public static native void pushInterpreterFrame(Word frameSize, Word framePc, Word senderSp, Word initialInfo);

}
