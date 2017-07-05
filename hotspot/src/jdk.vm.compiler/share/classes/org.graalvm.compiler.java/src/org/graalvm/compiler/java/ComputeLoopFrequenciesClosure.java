/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return states.stream().collect(Collectors.summingDouble(d -> d));
    }

    @Override
    protected Double afterSplit(AbstractBeginNode node, Double oldState) {
        // a control split splits up the probability
        ControlSplitNode split = (ControlSplitNode) node.predecessor();
        return oldState * split.probability(node);
    }

    @Override
    protected Map<LoopExitNode, Double> processLoop(LoopBeginNode loop, Double initialState) {
        Map<LoopExitNode, Double> exitStates = ReentrantNodeIterator.processLoop(this, loop, 1D).exitStates;

        double exitProbability = exitStates.values().stream().mapToDouble(d -> d).sum();
        exitProbability = Math.min(1D, exitProbability);
        if (exitProbability < ControlFlowGraph.MIN_PROBABILITY) {
            exitProbability = ControlFlowGraph.MIN_PROBABILITY;
        }
        assert exitProbability <= 1D && exitProbability >= 0D;
        double loopFrequency = 1D / exitProbability;
        loop.setLoopFrequency(loopFrequency);

        double adjustmentFactor = initialState * loopFrequency;
        exitStates.replaceAll((exitNode, probability) -> multiplySaturate(probability, adjustmentFactor));

        return exitStates;
    }

    /**
     * Multiplies a and b and saturates the result to {@link ControlFlowGraph#MAX_PROBABILITY}.
     */
    public static double multiplySaturate(double a, double b) {
        double r = a * b;
        if (r > ControlFlowGraph.MAX_PROBABILITY) {
            return ControlFlowGraph.MAX_PROBABILITY;
        }
        return r;
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
