/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import org.graalvm.compiler.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.loop.phases.LoopTransformations;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.EntryMarkerNode;
import org.graalvm.compiler.nodes.EntryProxyNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.OSRLocalNode;
import org.graalvm.compiler.nodes.extended.OSRStartNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;

import jdk.vm.ci.runtime.JVMCICompiler;

public class OnStackReplacementPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.getEntryBCI() == JVMCICompiler.INVOCATION_ENTRY_BCI) {
            // This happens during inlining in a OSR method, because the same phase plan will be
            // used.
            assert graph.getNodes(EntryMarkerNode.TYPE).isEmpty();
            return;
        }
        Debug.dump(Debug.INFO_LOG_LEVEL, graph, "OnStackReplacement initial");
        EntryMarkerNode osr;
        int maxIterations = -1;
        int iterations = 0;
        do {
            osr = getEntryMarker(graph);
            LoopsData loops = new LoopsData(graph);
            // Find the loop that contains the EntryMarker
            Loop<Block> l = loops.getCFG().getNodeToBlock().get(osr).getLoop();
            if (l == null) {
                break;
            }
            iterations++;
            if (maxIterations == -1) {
                maxIterations = l.getDepth();
            } else if (iterations > maxIterations) {
                throw GraalError.shouldNotReachHere();
            }
            // Peel the outermost loop first
            while (l.getParent() != null) {
                l = l.getParent();
            }

            LoopTransformations.peel(loops.loop(l));
            osr.replaceAtUsages(InputType.Guard, AbstractBeginNode.prevBegin((FixedNode) osr.predecessor()));
            for (Node usage : osr.usages().snapshot()) {
                EntryProxyNode proxy = (EntryProxyNode) usage;
                proxy.replaceAndDelete(proxy.value());
            }
            GraphUtil.removeFixedWithUnusedInputs(osr);
            Debug.dump(Debug.INFO_LOG_LEVEL, graph, "OnStackReplacement loop peeling result");
        } while (true);

        FrameState osrState = osr.stateAfter();
        osr.setStateAfter(null);
        OSRStartNode osrStart = graph.add(new OSRStartNode());
        StartNode start = graph.start();
        FixedNode next = osr.next();
        osr.setNext(null);
        osrStart.setNext(next);
        graph.setStart(osrStart);
        osrStart.setStateAfter(osrState);

        for (int i = 0; i < osrState.localsSize(); i++) {
            ValueNode value = osrState.localAt(i);
            if (value instanceof EntryProxyNode) {
                EntryProxyNode proxy = (EntryProxyNode) value;
                /*
                 * we need to drop the stamp since the types we see during OSR may be too precise
                 * (if a branch was not parsed for example).
                 */
                proxy.replaceAndDelete(graph.addOrUnique(new OSRLocalNode(i, proxy.stamp().unrestricted())));
            } else {
                assert value == null || value instanceof OSRLocalNode;
            }
        }
        osr.replaceAtUsages(InputType.Guard, osrStart);
        assert osr.usages().isEmpty();

        GraphUtil.killCFG(start);

        Debug.dump(Debug.INFO_LOG_LEVEL, graph, "OnStackReplacement result");
        new DeadCodeEliminationPhase(Required).apply(graph);
    }

    private static EntryMarkerNode getEntryMarker(StructuredGraph graph) {
        NodeIterable<EntryMarkerNode> osrNodes = graph.getNodes(EntryMarkerNode.TYPE);
        EntryMarkerNode osr = osrNodes.first();
        if (osr == null) {
            throw new PermanentBailoutException("No OnStackReplacementNode generated");
        }
        if (osrNodes.count() > 1) {
            throw new GraalError("Multiple OnStackReplacementNodes generated");
        }
        if (osr.stateAfter().locksSize() != 0) {
            throw new PermanentBailoutException("OSR with locks not supported");
        }
        if (osr.stateAfter().stackSize() != 0) {
            throw new PermanentBailoutException("OSR with stack entries not supported: %s", osr.stateAfter().toString(Verbosity.Debugger));
        }
        return osr;
    }

    @Override
    public float codeSizeIncrease() {
        return 5.0f;
    }
}
