/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common;

import java.util.LinkedList;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.DynamicDeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;

/**
 * This phase tries to find {@link AbstractDeoptimizeNode DeoptimizeNodes} which use the same
 * {@link FrameState} and merges them together.
 */
public class DeoptimizationGroupingPhase extends BasePhase<MidTierContext> {

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, MidTierContext context) {
        ControlFlowGraph cfg = null;
        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            FixedNode target = null;
            PhiNode reasonActionPhi = null;
            PhiNode speculationPhi = null;
            List<AbstractDeoptimizeNode> obsoletes = null;
            for (AbstractDeoptimizeNode deopt : fs.usages().filter(AbstractDeoptimizeNode.class)) {
                if (target == null) {
                    target = deopt;
                } else {
                    if (cfg == null) {
                        cfg = ControlFlowGraph.compute(graph, true, true, false, false);
                    }
                    AbstractMergeNode merge;
                    if (target instanceof AbstractDeoptimizeNode) {
                        merge = graph.add(new MergeNode());
                        EndNode firstEnd = graph.add(new EndNode());
                        ValueNode actionAndReason = ((AbstractDeoptimizeNode) target).getActionAndReason(context.getMetaAccess());
                        ValueNode speculation = ((AbstractDeoptimizeNode) target).getSpeculation(context.getMetaAccess());
                        reasonActionPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(actionAndReason.getStackKind()), merge));
                        speculationPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(speculation.getStackKind()), merge));
                        merge.addForwardEnd(firstEnd);
                        reasonActionPhi.addInput(actionAndReason);
                        speculationPhi.addInput(speculation);
                        target.replaceAtPredecessor(firstEnd);

                        exitLoops((AbstractDeoptimizeNode) target, firstEnd, cfg);
                        try (DebugCloseable position = target.withNodeSourcePosition()) {
                            merge.setNext(graph.add(new DynamicDeoptimizeNode(reasonActionPhi, speculationPhi)));
                        }
                        obsoletes = new LinkedList<>();
                        obsoletes.add((AbstractDeoptimizeNode) target);
                        target = merge;
                    } else {
                        merge = (AbstractMergeNode) target;
                    }
                    EndNode newEnd = graph.add(new EndNode());
                    merge.addForwardEnd(newEnd);
                    reasonActionPhi.addInput(deopt.getActionAndReason(context.getMetaAccess()));
                    speculationPhi.addInput(deopt.getSpeculation(context.getMetaAccess()));
                    deopt.replaceAtPredecessor(newEnd);
                    exitLoops(deopt, newEnd, cfg);
                    obsoletes.add(deopt);
                }
            }
            if (obsoletes != null) {
                ((DynamicDeoptimizeNode) ((AbstractMergeNode) target).next()).setStateBefore(fs);
                for (AbstractDeoptimizeNode obsolete : obsoletes) {
                    obsolete.safeDelete();
                }
            }
        }
    }

    private static void exitLoops(AbstractDeoptimizeNode deopt, EndNode end, ControlFlowGraph cfg) {
        Block block = cfg.blockFor(deopt);
        Loop<Block> loop = block.getLoop();
        while (loop != null) {
            end.graph().addBeforeFixed(end, end.graph().add(new LoopExitNode((LoopBeginNode) loop.getHeader().getBeginNode())));
            loop = loop.getParent();
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 2.5f;
    }
}
