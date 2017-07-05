/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.spi;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_100;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_15;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_20;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_30;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_4;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_40;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_80;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_10;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_100;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_15;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_200;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_30;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_4;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_50;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_6;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_80;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;

/*
 * Certain node costs can not, based on the meta information encoded in the node properties,
 * be computed before a real node is instantiated. E.g. the type of a call in Java heavily
 * influences the cost of an invocation and thus must be decided dynamically.
 */
public abstract class DefaultNodeCostProvider implements NodeCostProvider {

    @Override
    public final int getEstimatedCodeSize(Node n) {
        return size(n).estimatedCodeSize;
    }

    @Override
    public final int getEstimatedCPUCycles(Node n) {
        return cycles(n).estimatedCPUCycles;
    }

    @Override
    public NodeSize size(Node n) {
        if (n instanceof Invoke) {
            /*
             * Code size for the invoke itself is a very weak approximation.
             */
            Invoke ivk = (Invoke) n;
            CallTargetNode mct = ivk.callTarget();
            switch (mct.invokeKind()) {
                case Interface:
                    return SIZE_50;
                case Special:
                case Static:
                    return SIZE_2;
                case Virtual:
                    return SIZE_4;
                default:
                    break;
            }
        } else if (n instanceof CommitAllocationNode) {
            CommitAllocationNode commit = (CommitAllocationNode) n;
            /*
             * very weak approximation, current problem is node size is an enum and we cannot
             * dynamically instantiate a new case like nrOfAllocs*allocationCodeSize
             */
            int nrOfAllocs = commit.getVirtualObjects().size();
            if (nrOfAllocs < 5) {
                return SIZE_80;
            } else if (nrOfAllocs < 10) {
                return SIZE_100;
            } else {
                return SIZE_200;
            }
        } else if (n instanceof AccessFieldNode) {
            if (((AccessFieldNode) n).field().isVolatile()) {
                // membar size is added
                return SIZE_10;
            }
        } else if (n instanceof LoopEndNode) {
            if (((LoopEndNode) n).canSafepoint()) {
                return SIZE_6;
            }
        } else if (n instanceof SwitchNode) {
            SwitchNode x = (SwitchNode) n;
            int keyCount = x.keyCount();
            if (keyCount == 0) {
                return SIZE_1;
            } else {
                if (keyCount == 1) {
                    // if
                    return SIZE_2;
                } else if (x instanceof IntegerSwitchNode && x.isSorted()) {
                    // good heuristic
                    return SIZE_15;
                } else {
                    // not so good
                    return SIZE_30;
                }
            }
        }

        return n.getNodeClass().size();
    }

    @Override
    public NodeCycles cycles(Node n) {
        if (n instanceof Invoke) {
            Invoke ivk = (Invoke) n;
            CallTargetNode mct = ivk.callTarget();
            switch (mct.invokeKind()) {
                case Interface:
                    return CYCLES_100;
                case Special:
                case Static:
                    return CYCLES_2;
                case Virtual:
                    return CYCLES_4;
                default:
                    break;
            }
        } else if (n instanceof CommitAllocationNode) {
            CommitAllocationNode commit = (CommitAllocationNode) n;
            /*
             * very weak approximation, current problem is node cycles is an enum and we cannot
             * dynamically instantiate a new case like nrOfAllocs*allocationCost
             */
            int nrOfAllocs = commit.getVirtualObjects().size();
            if (nrOfAllocs < 5) {
                return CYCLES_20;
            } else if (nrOfAllocs < 10) {
                return CYCLES_40;
            } else {
                return CYCLES_80;
            }
        } else if (n instanceof AccessFieldNode) {
            if (((AccessFieldNode) n).field().isVolatile()) {
                // membar cycles is added
                return CYCLES_30;
            }
        } else if (n instanceof SwitchNode) {
            SwitchNode x = (SwitchNode) n;
            int keyCount = x.keyCount();
            if (keyCount == 0) {
                return CYCLES_1;
            } else {
                if (keyCount == 1) {
                    // if
                    return CYCLES_2;
                } else if (x instanceof IntegerSwitchNode && x.isSorted()) {
                    // good heuristic
                    return CYCLES_15;
                } else {
                    // not so good
                    return CYCLES_30;
                }
            }
        }

        return n.getNodeClass().cycles();
    }

}
