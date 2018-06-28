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


package org.graalvm.compiler.hotspot.phases.profiling;

import static org.graalvm.compiler.hotspot.nodes.profiling.ProfileInvokeNode.getProfileInvokeNodes;
import static org.graalvm.compiler.hotspot.nodes.profiling.ProfileNode.getProfileNodes;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.hotspot.nodes.profiling.ProfileInvokeNode;
import org.graalvm.compiler.hotspot.nodes.profiling.ProfileNode;
import org.graalvm.compiler.hotspot.nodes.profiling.RandomSeedNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class FinalizeProfileNodesPhase extends BasePhase<PhaseContext> {
    private int inlineeInvokeNotificationFreqLog;

    public static class Options {
        @Option(help = "Profile simple methods", type = OptionType.Expert)//
        public static final OptionKey<Boolean> ProfileSimpleMethods = new OptionKey<>(true);
        @Option(help = "Maximum number of nodes in a graph for a simple method", type = OptionType.Expert)//
        public static final OptionKey<Integer> SimpleMethodGraphSize = new OptionKey<>(256);
        @Option(help = "Maximum number of calls in a simple method", type = OptionType.Expert)//
        public static final OptionKey<Integer> SimpleMethodCalls = new OptionKey<>(1);
        @Option(help = "Maximum number of indirect calls in a simple moethod", type = OptionType.Expert)//
        public static final OptionKey<Integer> SimpleMethodIndirectCalls = new OptionKey<>(0);

    }

    public FinalizeProfileNodesPhase(int inlineeInvokeNotificationFreqLog) {
        this.inlineeInvokeNotificationFreqLog = inlineeInvokeNotificationFreqLog;
    }

    private static void removeAllProfilingNodes(StructuredGraph graph) {
        getProfileNodes(graph).forEach((n) -> GraphUtil.removeFixedWithUnusedInputs(n));
    }

    private void assignInlineeInvokeFrequencies(StructuredGraph graph) {
        for (ProfileInvokeNode node : getProfileInvokeNodes(graph)) {
            ResolvedJavaMethod profiledMethod = node.getProfiledMethod();
            if (!profiledMethod.equals(graph.method())) {
                // Some inlinee, reassign the inlinee frequency
                node.setNotificationFreqLog(inlineeInvokeNotificationFreqLog);
            }
        }
    }

    // Hacky heuristic to determine whether we want any profiling in this method.
    // The heuristic is applied after the graph is fully formed and before the first lowering.
    private static boolean simpleMethodHeuristic(StructuredGraph graph) {
        if (Options.ProfileSimpleMethods.getValue(graph.getOptions())) {
            return false;
        }

        // Check if the graph is smallish..
        if (graph.getNodeCount() > Options.SimpleMethodGraphSize.getValue(graph.getOptions())) {
            return false;
        }

        // Check if method has loops
        if (graph.hasLoops()) {
            return false;
        }

        // Check if method has calls
        if (graph.getNodes().filter(InvokeNode.class).count() > Options.SimpleMethodCalls.getValue(graph.getOptions())) {
            return false;
        }

        // Check if method has calls that need profiling
        if (graph.getNodes().filter(InvokeNode.class).filter((n) -> ((InvokeNode) n).getInvokeKind().isIndirect()).count() > Options.SimpleMethodIndirectCalls.getDefaultValue()) {
            return false;
        }

        return true;
    }

    private static void assignRandomSources(StructuredGraph graph) {
        ValueNode seed = graph.unique(new RandomSeedNode());
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, false, true, false, false);
        Map<LoopBeginNode, ValueNode> loopRandomValueCache = new HashMap<>();

        for (ProfileNode node : getProfileNodes(graph)) {
            ValueNode random;
            Block block = cfg.blockFor(node);
            Loop<Block> loop = block.getLoop();
            // Inject <a href=https://en.wikipedia.org/wiki/Linear_congruential_generator>LCG</a>
            // pseudo-random number generator into the loop
            if (loop != null) {
                LoopBeginNode loopBegin = (LoopBeginNode) loop.getHeader().getBeginNode();
                random = loopRandomValueCache.get(loopBegin);
                if (random == null) {
                    PhiNode phi = graph.addWithoutUnique(new ValuePhiNode(seed.stamp(NodeView.DEFAULT), loopBegin));
                    phi.addInput(seed);
                    // X_{n+1} = a*X_n + c, using glibc-like constants
                    ValueNode a = ConstantNode.forInt(1103515245, graph);
                    ValueNode c = ConstantNode.forInt(12345, graph);
                    ValueNode next = graph.addOrUniqueWithInputs(new AddNode(c, new MulNode(phi, a)));
                    for (int i = 0; i < loopBegin.getLoopEndCount(); i++) {
                        phi.addInput(next);
                    }
                    random = phi;
                    loopRandomValueCache.put(loopBegin, random);
                }
            } else {
                // Graal doesn't compile methods with irreducible loops. So all profile nodes that
                // are not in a loop are guaranteed to be executed at most once. We feed the seed
                // value to such nodes directly.
                random = seed;
            }
            node.setRandom(random);
        }
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        if (simpleMethodHeuristic(graph)) {
            removeAllProfilingNodes(graph);
            return;
        }

        assignInlineeInvokeFrequencies(graph);
        if (ProfileNode.Options.ProbabilisticProfiling.getValue(graph.getOptions())) {
            assignRandomSources(graph);
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
