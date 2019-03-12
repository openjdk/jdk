/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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


package org.graalvm.compiler.hotspot.gc.shared;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ArrayRangeWrite;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.HeapAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.type.StampTool;

public class CardTableBarrierSet extends BarrierSet {

    @Override
    public void addReadNodeBarriers(ReadNode node, StructuredGraph graph) {
        assert node.getBarrierType() == HeapAccess.BarrierType.NONE : "Non precise read barrier has been attached to read node.";
    }

    @Override
    public void addWriteNodeBarriers(WriteNode node, StructuredGraph graph) {
        HeapAccess.BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case IMPRECISE:
            case PRECISE:
                boolean precise = barrierType == HeapAccess.BarrierType.PRECISE;
                addSerialPostWriteBarrier(node, node.getAddress(), node.value(), precise, graph);
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    @Override
    public void addAtomicReadWriteNodeBarriers(LoweredAtomicReadAndWriteNode node, StructuredGraph graph) {
        HeapAccess.BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case IMPRECISE:
            case PRECISE:
                boolean precise = barrierType == HeapAccess.BarrierType.PRECISE;
                addSerialPostWriteBarrier(node, node.getAddress(), node.getNewValue(), precise, graph);
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    @Override
    public void addCASBarriers(AbstractCompareAndSwapNode node, StructuredGraph graph) {
        HeapAccess.BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case IMPRECISE:
            case PRECISE:
                boolean precise = barrierType == HeapAccess.BarrierType.PRECISE;
                addSerialPostWriteBarrier(node, node.getAddress(), node.getNewValue(), precise, graph);
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    @Override
    public void addArrayRangeBarriers(ArrayRangeWrite write, StructuredGraph graph) {
        SerialArrayRangeWriteBarrier serialArrayRangeWriteBarrier = graph.add(new SerialArrayRangeWriteBarrier(write.getAddress(), write.getLength(), write.getElementStride()));
        graph.addAfterFixed(write.asNode(), serialArrayRangeWriteBarrier);
    }

    protected void addSerialPostWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean precise, StructuredGraph graph) {
        final boolean alwaysNull = StampTool.isPointerAlwaysNull(value);
        if (alwaysNull) {
            // Serial barrier isn't needed for null value
            return;
        }
        graph.addAfterFixed(node, graph.add(new SerialWriteBarrier(address, precise)));
    }
}
