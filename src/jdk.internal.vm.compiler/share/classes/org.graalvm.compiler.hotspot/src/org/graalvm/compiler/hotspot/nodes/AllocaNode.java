/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.BitSet;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Reserves a block of memory in the stack frame of a method. The block is reserved in the frame for
 * the entire execution of the associated method.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class AllocaNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<AllocaNode> TYPE = NodeClass.create(AllocaNode.class);
    /**
     * The number of slots in block.
     */
    protected final int slots;

    /**
     * The indexes of the object pointer slots in the block. Each such object pointer slot must be
     * initialized before any safepoint in the method otherwise the garbage collector will see
     * garbage values when processing these slots.
     */
    protected final BitSet objects;

    public AllocaNode(@InjectedNodeParameter WordTypes wordTypes, int slots) {
        this(slots, wordTypes.getWordKind(), new BitSet());
    }

    public AllocaNode(int slots, JavaKind wordKind, BitSet objects) {
        super(TYPE, StampFactory.forKind(wordKind));
        this.slots = slots;
        this.objects = objects;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        VirtualStackSlot array = gen.getLIRGeneratorTool().getResult().getFrameMapBuilder().allocateStackSlots(slots, objects, null);
        Value result = gen.getLIRGeneratorTool().emitAddress(array);
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native Word alloca(@ConstantNodeParameter int slots);
}
