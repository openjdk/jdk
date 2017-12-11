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

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.FloatLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.NormalizeCompareNode;
import org.graalvm.compiler.phases.Phase;

public class ExpandLogicPhase extends Phase {
    private static final double EPSILON = 1E-6;

    @Override
    protected void run(StructuredGraph graph) {
        for (ShortCircuitOrNode logic : graph.getNodes(ShortCircuitOrNode.TYPE)) {
            processBinary(logic);
        }
        assert graph.getNodes(ShortCircuitOrNode.TYPE).isEmpty();

        for (NormalizeCompareNode logic : graph.getNodes(NormalizeCompareNode.TYPE)) {
            processNormalizeCompareNode(logic);
        }
        graph.setAfterExpandLogic();
    }

    private static void processNormalizeCompareNode(NormalizeCompareNode normalize) {
        LogicNode equalComp;
        LogicNode lessComp;
        StructuredGraph graph = normalize.graph();
        ValueNode x = normalize.getX();
        ValueNode y = normalize.getY();
        if (x.stamp(NodeView.DEFAULT) instanceof FloatStamp) {
            equalComp = graph.addOrUniqueWithInputs(FloatEqualsNode.create(x, y, NodeView.DEFAULT));
            lessComp = graph.addOrUniqueWithInputs(FloatLessThanNode.create(x, y, normalize.isUnorderedLess(), NodeView.DEFAULT));
        } else {
            equalComp = graph.addOrUniqueWithInputs(IntegerEqualsNode.create(x, y, NodeView.DEFAULT));
            lessComp = graph.addOrUniqueWithInputs(IntegerLessThanNode.create(x, y, NodeView.DEFAULT));
        }

        Stamp stamp = normalize.stamp(NodeView.DEFAULT);
        ConditionalNode equalValue = graph.unique(
                        new ConditionalNode(equalComp, ConstantNode.forIntegerStamp(stamp, 0, graph), ConstantNode.forIntegerStamp(stamp, 1, graph)));
        ConditionalNode value = graph.unique(new ConditionalNode(lessComp, ConstantNode.forIntegerStamp(stamp, -1, graph), equalValue));
        normalize.replaceAtUsagesAndDelete(value);
    }

    private static void processBinary(ShortCircuitOrNode binary) {
        while (binary.usages().isNotEmpty()) {
            Node usage = binary.usages().first();
            if (usage instanceof ShortCircuitOrNode) {
                processBinary((ShortCircuitOrNode) usage);
            } else if (usage instanceof IfNode) {
                processIf(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (IfNode) usage, binary.getShortCircuitProbability());
            } else if (usage instanceof ConditionalNode) {
                processConditional(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (ConditionalNode) usage);
            } else {
                throw GraalError.shouldNotReachHere();
            }
        }
        binary.safeDelete();
    }

    private static void processIf(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, IfNode ifNode, double shortCircuitProbability) {
        /*
         * this method splits an IfNode, which has a ShortCircuitOrNode as its condition, into two
         * separate IfNodes: if(X) and if(Y)
         *
         * for computing the probabilities P(X) and P(Y), we use two different approaches. The first
         * one assumes that the shortCircuitProbability and the probability on the IfNode were
         * created with each other in mind. If this assumption does not hold, we fall back to
         * another mechanism for computing the probabilities.
         */
        AbstractBeginNode trueTarget = ifNode.trueSuccessor();
        AbstractBeginNode falseTarget = ifNode.falseSuccessor();

        // 1st approach
        // assumption: P(originalIf.trueSuccessor) == P(X) + ((1 - P(X)) * P(Y))
        double firstIfTrueProbability = shortCircuitProbability;
        double secondIfTrueProbability = sanitizeProbability((ifNode.getTrueSuccessorProbability() - shortCircuitProbability) / (1 - shortCircuitProbability));
        double expectedOriginalIfTrueProbability = firstIfTrueProbability + (1 - firstIfTrueProbability) * secondIfTrueProbability;

        if (!doubleEquals(ifNode.getTrueSuccessorProbability(), expectedOriginalIfTrueProbability)) {
            /*
             * 2nd approach
             *
             * the assumption above did not hold, so we either used an artificial probability as
             * shortCircuitProbability or the ShortCircuitOrNode was moved to some other IfNode.
             *
             * so, we distribute the if's trueSuccessorProbability between the newly generated if
             * nodes according to the shortCircuitProbability. the following invariant is always
             * true in this case: P(originalIf.trueSuccessor) == P(X) + ((1 - P(X)) * P(Y))
             */
            firstIfTrueProbability = ifNode.getTrueSuccessorProbability() * shortCircuitProbability;
            secondIfTrueProbability = sanitizeProbability(1 - (ifNode.probability(falseTarget) / (1 - firstIfTrueProbability)));
        }

        ifNode.clearSuccessors();
        Graph graph = ifNode.graph();
        AbstractMergeNode trueTargetMerge = graph.add(new MergeNode());
        trueTargetMerge.setNext(trueTarget);
        EndNode firstTrueEnd = graph.add(new EndNode());
        EndNode secondTrueEnd = graph.add(new EndNode());
        trueTargetMerge.addForwardEnd(firstTrueEnd);
        trueTargetMerge.addForwardEnd(secondTrueEnd);
        AbstractBeginNode firstTrueTarget = BeginNode.begin(firstTrueEnd);
        AbstractBeginNode secondTrueTarget = BeginNode.begin(secondTrueEnd);
        if (yNegated) {
            secondIfTrueProbability = 1.0 - secondIfTrueProbability;
        }
        if (xNegated) {
            firstIfTrueProbability = 1.0 - firstIfTrueProbability;
        }
        AbstractBeginNode secondIf = BeginNode.begin(graph.add(new IfNode(y, yNegated ? falseTarget : secondTrueTarget, yNegated ? secondTrueTarget : falseTarget, secondIfTrueProbability)));
        IfNode firstIf = graph.add(new IfNode(x, xNegated ? secondIf : firstTrueTarget, xNegated ? firstTrueTarget : secondIf, firstIfTrueProbability));
        ifNode.replaceAtPredecessor(firstIf);
        ifNode.safeDelete();
    }

    private static boolean doubleEquals(double a, double b) {
        assert !Double.isNaN(a) && !Double.isNaN(b) && !Double.isInfinite(a) && !Double.isInfinite(b);
        return a - EPSILON < b && a + EPSILON > b;
    }

    private static double sanitizeProbability(double value) {
        double newValue = Math.min(1.0, Math.max(0.0, value));
        if (Double.isNaN(newValue)) {
            newValue = 0.5;
        }
        return newValue;
    }

    private static void processConditional(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, ConditionalNode conditional) {
        ValueNode trueTarget = conditional.trueValue();
        ValueNode falseTarget = conditional.falseValue();
        Graph graph = conditional.graph();
        ConditionalNode secondConditional = graph.unique(new ConditionalNode(y, yNegated ? falseTarget : trueTarget, yNegated ? trueTarget : falseTarget));
        ConditionalNode firstConditional = graph.unique(new ConditionalNode(x, xNegated ? secondConditional : trueTarget, xNegated ? trueTarget : secondConditional));
        conditional.replaceAndDelete(firstConditional);
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
