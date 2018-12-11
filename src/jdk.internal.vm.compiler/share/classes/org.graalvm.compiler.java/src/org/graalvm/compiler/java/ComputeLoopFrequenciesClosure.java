/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.java;

import static org.graalvm.compiler.nodes.cfg.ControlFlowGraph.multiplyRelativeFrequencies;

import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;

public final class ComputeLoopFrequenciesClosure extends ReentrantNodeIterator.NodeIteratorClosure<Double> {

    private static final ComputeLoopFrequenciesClosure INSTANCE = new ComputeLoopFrequenciesClosure();

    private ComputeLoopFrequenciesClosure() {
        // nothing to do
    }

    @Override
    protected Double processNode(FixedNode node, Double currentState) {
        // normal nodes never change the probability of a path
        return currentState;
    }

    @Override
    protected Double merge(AbstractMergeNode merge, List<Double> states) {
        // a merge has the sum of all predecessor probabilities
        double result = 0.0;
        for (double d : states) {
            result += d;
        }
        return result;
    }

    @Override
    protected Double afterSplit(AbstractBeginNode node, Double oldState) {
        // a control split splits up the probability
        ControlSplitNode split = (ControlSplitNode) node.predecessor();
        return oldState * split.probability(node);
    }

    @Override
    protected EconomicMap<LoopExitNode, Double> processLoop(LoopBeginNode loop, Double initialState) {
        EconomicMap<LoopExitNode, Double> exitStates = ReentrantNodeIterator.processLoop(this, loop, 1D).exitStates;

        double exitRelativeFrequency = 0.0;
        for (double d : exitStates.getValues()) {
            exitRelativeFrequency += d;
        }
        exitRelativeFrequency = Math.min(1.0, exitRelativeFrequency);
        exitRelativeFrequency = Math.max(ControlFlowGraph.MIN_RELATIVE_FREQUENCY, exitRelativeFrequency);
        double loopFrequency = 1.0 / exitRelativeFrequency;
        loop.setLoopFrequency(loopFrequency);

        double adjustmentFactor = initialState * loopFrequency;
        exitStates.replaceAll((exitNode, frequency) -> multiplyRelativeFrequencies(frequency, adjustmentFactor));

        return exitStates;
    }

    /**
     * Computes the frequencies of all loops in the given graph. This is done by performing a
     * reverse postorder iteration and computing the probability of all fixed nodes. The combined
     * probability of all exits of a loop can be used to compute the loop's expected frequency.
     */
    public static void compute(StructuredGraph graph) {
        if (graph.hasLoops()) {
            ReentrantNodeIterator.apply(INSTANCE, graph.start(), 1D);
        }
    }

    public static class ComputeLoopFrequencyPhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            compute(graph);
        }
    }

    public static final ComputeLoopFrequencyPhase PHASE_INSTANCE = new ComputeLoopFrequencyPhase();

}
