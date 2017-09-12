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

package org.graalvm.compiler.hotspot.phases;

import java.util.Iterator;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeFlood;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.nodes.ArrayRangeWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1PostWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.ObjectWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.SerialWriteBarrier;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ArrayRangeWriteNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.java.LogicCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.HeapAccess;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;

/**
 * Verification phase that checks if, for every write, at least one write barrier is present at all
 * paths leading to the previous safepoint. For every write, necessitating a write barrier, a
 * bottom-up traversal of the graph is performed up to the previous safepoints via all possible
 * paths. If, for a certain path, no write barrier satisfying the processed write is found, an
 * assertion is generated.
 */
public class WriteBarrierVerificationPhase extends Phase {

    private final GraalHotSpotVMConfig config;

    public WriteBarrierVerificationPhase(GraalHotSpotVMConfig config) {
        this.config = config;
    }

    @Override
    protected void run(StructuredGraph graph) {
        processWrites(graph);
    }

    private void processWrites(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (isObjectWrite(node) || isObjectArrayRangeWrite(node)) {
                if (node instanceof WriteNode) {
                    WriteNode writeNode = (WriteNode) node;
                    if (StampTool.isPointerAlwaysNull(writeNode.value())) {
                        continue;
                    }
                }
                validateWrite(node);
            }
        }
    }

    private void validateWrite(Node write) {
        /*
         * The currently validated write is checked in order to discover if it has an appropriate
         * attached write barrier.
         */
        if (hasAttachedBarrier((FixedWithNextNode) write)) {
            return;
        }
        NodeFlood frontier = write.graph().createNodeFlood();
        expandFrontier(frontier, write);
        Iterator<Node> iterator = frontier.iterator();
        while (iterator.hasNext()) {
            Node currentNode = iterator.next();
            if (isSafepoint(currentNode)) {
                throw new AssertionError("Write barrier must be present " + write.toString(Verbosity.All) + " / " + write.inputs());
            }
            if (useG1GC()) {
                if (!(currentNode instanceof G1PostWriteBarrier) || (!validateBarrier((FixedAccessNode) write, (ObjectWriteBarrier) currentNode))) {
                    expandFrontier(frontier, currentNode);
                }
            } else {
                if (!(currentNode instanceof SerialWriteBarrier) || (!validateBarrier((FixedAccessNode) write, (ObjectWriteBarrier) currentNode)) ||
                                ((currentNode instanceof SerialWriteBarrier) && !validateBarrier((FixedAccessNode) write, (ObjectWriteBarrier) currentNode))) {
                    expandFrontier(frontier, currentNode);
                }
            }
        }
    }

    private boolean useG1GC() {
        return config.useG1GC;
    }

    private boolean hasAttachedBarrier(FixedWithNextNode node) {
        final Node next = node.next();
        final Node previous = node.predecessor();
        boolean validatePreBarrier = useG1GC() && (isObjectWrite(node) || !((ArrayRangeWriteNode) node).isInitialization());
        if (node instanceof WriteNode) {
            WriteNode writeNode = (WriteNode) node;
            if (writeNode.getLocationIdentity().isInit()) {
                validatePreBarrier = false;
            }
        }
        if (isObjectWrite(node)) {
            return (isObjectBarrier(node, next) || StampTool.isPointerAlwaysNull(getValueWritten(node))) && (!validatePreBarrier || isObjectBarrier(node, previous));
        } else if (isObjectArrayRangeWrite(node)) {
            return (isArrayBarrier(node, next) || StampTool.isPointerAlwaysNull(getValueWritten(node))) && (!validatePreBarrier || isArrayBarrier(node, previous));
        } else {
            return true;
        }
    }

    private static boolean isObjectBarrier(FixedWithNextNode node, final Node next) {
        return next instanceof ObjectWriteBarrier && validateBarrier((FixedAccessNode) node, (ObjectWriteBarrier) next);
    }

    private static boolean isArrayBarrier(FixedWithNextNode node, final Node next) {
        return (next instanceof ArrayRangeWriteBarrier) && ((ArrayRangeWriteNode) node).getArray() == ((ArrayRangeWriteBarrier) next).getObject();
    }

    private static boolean isObjectWrite(Node node) {
        // Read nodes with barrier attached (G1 Ref field) are not validated yet.
        return node instanceof FixedAccessNode && ((HeapAccess) node).getBarrierType() != BarrierType.NONE && !(node instanceof ReadNode);
    }

    private static boolean isObjectArrayRangeWrite(Node node) {
        return node instanceof ArrayRangeWriteNode && ((ArrayRangeWriteNode) node).isObjectArray();
    }

    private static void expandFrontier(NodeFlood frontier, Node node) {
        for (Node previousNode : node.cfgPredecessors()) {
            if (previousNode != null) {
                frontier.add(previousNode);
            }
        }
    }

    private static boolean isSafepoint(Node node) {
        if (node instanceof FixedAccessNode) {
            // Implicit null checks on reads or writes do not count.
            return false;
        }
        /*
         * LoopBegin nodes are also treated as safepoints since a bottom-up analysis is performed
         * and loop safepoints are placed before LoopEnd nodes. Possible elimination of write
         * barriers inside loops, derived from writes outside loops, can not be permitted.
         */
        return ((node instanceof DeoptimizingNode) && ((DeoptimizingNode) node).canDeoptimize()) || (node instanceof LoopBeginNode);
    }

    private static ValueNode getValueWritten(FixedWithNextNode write) {
        if (write instanceof WriteNode) {
            return ((WriteNode) write).value();
        } else if (write instanceof LogicCompareAndSwapNode) {
            return ((LogicCompareAndSwapNode) write).getNewValue();
        } else if (write instanceof LoweredAtomicReadAndWriteNode) {
            return ((LoweredAtomicReadAndWriteNode) write).getNewValue();
        } else {
            throw GraalError.shouldNotReachHere(String.format("unexpected write node %s", write));
        }
    }

    private static boolean validateBarrier(FixedAccessNode write, ObjectWriteBarrier barrier) {
        assert write instanceof WriteNode || write instanceof LogicCompareAndSwapNode || write instanceof LoweredAtomicReadAndWriteNode : "Node must be of type requiring a write barrier " + write;
        if (!barrier.usePrecise()) {
            if (barrier.getAddress() instanceof OffsetAddressNode && write.getAddress() instanceof OffsetAddressNode) {
                return GraphUtil.unproxify(((OffsetAddressNode) barrier.getAddress()).getBase()) == GraphUtil.unproxify(((OffsetAddressNode) write.getAddress()).getBase());
            }
        }
        return barrier.getAddress() == write.getAddress();
    }
}
