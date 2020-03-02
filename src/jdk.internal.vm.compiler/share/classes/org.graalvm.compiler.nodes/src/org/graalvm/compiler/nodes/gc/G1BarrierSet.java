/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.gc;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ArrayRangeWrite;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.type.StampTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class G1BarrierSet implements BarrierSet {

    private final ResolvedJavaType referenceType;
    private final long referentFieldOffset;

    public G1BarrierSet(ResolvedJavaType referenceType, long referentFieldOffset) {
        this.referenceType = referenceType;
        this.referentFieldOffset = referentFieldOffset;
    }

    @Override
    public BarrierType readBarrierType(RawLoadNode load) {
        if (load.object().getStackKind() == JavaKind.Object &&
                        load.accessKind() == JavaKind.Object &&
                        !StampTool.isPointerAlwaysNull(load.object())) {
            if (load.offset().isJavaConstant() && referentFieldOffset != load.offset().asJavaConstant().asLong()) {
                // Reading at a constant offset which is different than the referent field.
                return BarrierType.NONE;
            }
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && referenceType.isAssignableFrom(type)) {
                // It's definitely a field of a Reference type
                if (load.offset().isJavaConstant() && referentFieldOffset == load.offset().asJavaConstant().asLong()) {
                    // Exactly Reference.referent
                    return BarrierType.WEAK_FIELD;
                }
                // An unknown offset into Reference
                return BarrierType.MAYBE_WEAK_FIELD;
            }
            if (type == null || type.isAssignableFrom(referenceType)) {
                // The object is a supertype of Reference with an unknown offset or a constant
                // offset which is the same as Reference.referent.
                return BarrierType.MAYBE_WEAK_FIELD;
            }
        }
        return BarrierType.NONE;
    }

    @Override
    public void addBarriers(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            addReadNodeBarriers((ReadNode) n);
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            addWriteBarriers(write, write.value(), null, true, write.getNullCheck());
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            addWriteBarriers(atomic, atomic.getNewValue(), null, true, atomic.getNullCheck());
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            addWriteBarriers(cmpSwap, cmpSwap.getNewValue(), cmpSwap.getExpectedValue(), false, false);
        } else if (n instanceof ArrayRangeWrite) {
            addArrayRangeBarriers((ArrayRangeWrite) n);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
        }
    }

    private static void addReadNodeBarriers(ReadNode node) {
        if (node.getBarrierType() == BarrierType.WEAK_FIELD || node.getBarrierType() == BarrierType.MAYBE_WEAK_FIELD) {
            StructuredGraph graph = node.graph();
            G1ReferentFieldReadBarrier barrier = graph.add(new G1ReferentFieldReadBarrier(node.getAddress(), node, node.getBarrierType() == BarrierType.MAYBE_WEAK_FIELD));
            graph.addAfterFixed(node, barrier);
        }
    }

    private void addWriteBarriers(FixedAccessNode node, ValueNode writtenValue, ValueNode expectedValue, boolean doLoad, boolean nullCheck) {
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case FIELD:
            case ARRAY:
            case UNKNOWN:
                if (isObjectValue(writtenValue)) {
                    StructuredGraph graph = node.graph();
                    boolean init = node.getLocationIdentity().isInit();
                    if (!init) {
                        // The pre barrier does nothing if the value being read is null, so it can
                        // be explicitly skipped when this is an initializing store.
                        addG1PreWriteBarrier(node, node.getAddress(), expectedValue, doLoad, nullCheck, graph);
                    }
                    if (writeRequiresPostBarrier(node, writtenValue)) {
                        boolean precise = barrierType != BarrierType.FIELD;
                        addG1PostWriteBarrier(node, node.getAddress(), writtenValue, precise, graph);
                    }
                }
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    @SuppressWarnings("unused")
    protected boolean writeRequiresPostBarrier(FixedAccessNode initializingWrite, ValueNode writtenValue) {
        // Without help from the runtime all writes require an explicit post barrier.
        return true;
    }

    private static void addArrayRangeBarriers(ArrayRangeWrite write) {
        if (write.writesObjectArray()) {
            StructuredGraph graph = write.asNode().graph();
            if (!write.isInitialization()) {
                // The pre barrier does nothing if the value being read is null, so it can
                // be explicitly skipped when this is an initializing store.
                G1ArrayRangePreWriteBarrier g1ArrayRangePreWriteBarrier = graph.add(new G1ArrayRangePreWriteBarrier(write.getAddress(), write.getLength(), write.getElementStride()));
                graph.addBeforeFixed(write.asNode(), g1ArrayRangePreWriteBarrier);
            }
            G1ArrayRangePostWriteBarrier g1ArrayRangePostWriteBarrier = graph.add(new G1ArrayRangePostWriteBarrier(write.getAddress(), write.getLength(), write.getElementStride()));
            graph.addAfterFixed(write.asNode(), g1ArrayRangePostWriteBarrier);
        }
    }

    private static void addG1PreWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean doLoad, boolean nullCheck, StructuredGraph graph) {
        G1PreWriteBarrier preBarrier = graph.add(new G1PreWriteBarrier(address, value, doLoad, nullCheck));
        preBarrier.setStateBefore(node.stateBefore());
        node.setNullCheck(false);
        node.setStateBefore(null);
        graph.addBeforeFixed(node, preBarrier);
    }

    private static void addG1PostWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean precise, StructuredGraph graph) {
        final boolean alwaysNull = StampTool.isPointerAlwaysNull(value);
        graph.addAfterFixed(node, graph.add(new G1PostWriteBarrier(address, value, precise, alwaysNull)));
    }

    private static boolean isObjectValue(ValueNode value) {
        return value.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp;
    }
}
