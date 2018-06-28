/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.BitSet;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.meta.Value;

/**
 * Node that is used to maintain a stack based counter of how many locks are currently held.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2, size = SIZE_1)
public final class MonitorCounterNode extends FloatingNode implements LIRLowerable, Node.ValueNumberable {
    public static final NodeClass<MonitorCounterNode> TYPE = NodeClass.create(MonitorCounterNode.class);

    public MonitorCounterNode(@InjectedNodeParameter WordTypes wordTypes) {
        super(TYPE, StampFactory.forKind(wordTypes.getWordKind()));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert graph().getNodes().filter(MonitorCounterNode.class).count() == 1 : "monitor counters not canonicalized to single instance";
        VirtualStackSlot counter = gen.getLIRGeneratorTool().getResult().getFrameMapBuilder().allocateStackSlots(1, new BitSet(0), null);
        Value result = gen.getLIRGeneratorTool().emitAddress(counter);
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native Word counter();
}
