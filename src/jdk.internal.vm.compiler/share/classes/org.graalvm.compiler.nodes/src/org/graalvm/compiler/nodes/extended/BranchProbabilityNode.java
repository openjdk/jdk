/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodePredicates;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

/**
 * Instances of this node class will look for a preceding if node and put the given probability into
 * the if node's taken probability. Then the branch probability node will be removed. This node is
 * intended primarily for snippets, so that they can define their fast and slow paths.
 */
@NodeInfo(cycles = CYCLES_0, cyclesRationale = "Artificial Node", size = SIZE_0)
public final class BranchProbabilityNode extends FloatingNode implements Simplifiable, Lowerable {

    public static final NodeClass<BranchProbabilityNode> TYPE = NodeClass.create(BranchProbabilityNode.class);
    public static final double LIKELY_PROBABILITY = 0.6;
    public static final double NOT_LIKELY_PROBABILITY = 1 - LIKELY_PROBABILITY;

    public static final double FREQUENT_PROBABILITY = 0.9;
    public static final double NOT_FREQUENT_PROBABILITY = 1 - FREQUENT_PROBABILITY;

    public static final double FAST_PATH_PROBABILITY = 0.99;
    public static final double SLOW_PATH_PROBABILITY = 1 - FAST_PATH_PROBABILITY;

    public static final double VERY_FAST_PATH_PROBABILITY = 0.999;
    public static final double VERY_SLOW_PATH_PROBABILITY = 1 - VERY_FAST_PATH_PROBABILITY;

    /*
     * This probability may seem excessive, but it makes a difference in long running loops. Lets
     * say a loop is executed 100k times and it has a few null checks with probability 0.999. As
     * these probabilities multiply for every loop iteration, the overall loop frequency will be
     * calculated as approximately 30 while it should be 100k.
     */
    public static final double LUDICROUSLY_FAST_PATH_PROBABILITY = 0.999999;
    public static final double LUDICROUSLY_SLOW_PATH_PROBABILITY = 1 - LUDICROUSLY_FAST_PATH_PROBABILITY;

    @Input ValueNode probability;
    @Input ValueNode condition;

    public BranchProbabilityNode(ValueNode probability, ValueNode condition) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.probability = probability;
        this.condition = condition;
    }

    public ValueNode getProbability() {
        return probability;
    }

    public ValueNode getCondition() {
        return condition;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (!hasUsages()) {
            return;
        }
        if (probability.isConstant()) {
            double probabilityValue = probability.asJavaConstant().asDouble();
            if (probabilityValue < 0.0) {
                throw new GraalError("A negative probability of " + probabilityValue + " is not allowed!");
            } else if (probabilityValue > 1.0) {
                throw new GraalError("A probability of more than 1.0 (" + probabilityValue + ") is not allowed!");
            } else if (Double.isNaN(probabilityValue)) {
                /*
                 * We allow NaN if the node is in unreachable code that will eventually fall away,
                 * or else an error will be thrown during lowering since we keep the node around.
                 */
                return;
            }
            boolean usageFound = false;
            for (IntegerEqualsNode node : this.usages().filter(IntegerEqualsNode.class)) {
                assert node.condition() == CanonicalCondition.EQ;
                ValueNode other = node.getX();
                if (node.getX() == this) {
                    other = node.getY();
                }
                if (other.isConstant()) {
                    double probabilityToSet = probabilityValue;
                    if (other.asJavaConstant().asInt() == 0) {
                        probabilityToSet = 1.0 - probabilityToSet;
                    }
                    for (IfNode ifNodeUsages : node.usages().filter(IfNode.class)) {
                        usageFound = true;
                        ifNodeUsages.setTrueSuccessorProbability(probabilityToSet);
                    }
                    if (!usageFound) {
                        usageFound = node.usages().filter(NodePredicates.isA(FixedGuardNode.class).or(ConditionalNode.class)).isNotEmpty();
                    }
                }
            }
            if (usageFound) {
                ValueNode currentCondition = condition;
                IntegerStamp currentStamp = (IntegerStamp) currentCondition.stamp(NodeView.DEFAULT);
                if (currentStamp.lowerBound() < 0 || 1 < currentStamp.upperBound()) {
                    ValueNode narrow = graph().maybeAddOrUnique(NarrowNode.create(currentCondition, 1, NodeView.DEFAULT));
                    currentCondition = graph().maybeAddOrUnique(ZeroExtendNode.create(narrow, 32, NodeView.DEFAULT));
                }
                replaceAndDelete(currentCondition);
                if (tool != null) {
                    tool.addToWorkList(currentCondition.usages());
                }
            } else {
                if (!isSubstitutionGraph()) {
                    throw new GraalError("Wrong usage of branch probability injection!");
                }
            }
        }
    }

    private boolean isSubstitutionGraph() {
        return hasExactlyOneUsage() && usages().first() instanceof ReturnNode;
    }

    /**
     * This intrinsic should only be used for the condition of an if statement. The parameter
     * condition should also only denote a simple condition and not a combined condition involving
     * &amp;&amp; or || operators. It injects the probability of the condition into the if
     * statement.
     *
     * @param probability the probability that the given condition is true as a double value between
     *            0.0 and 1.0.
     * @param condition the simple condition without any &amp;&amp; or || operators
     * @return the condition
     */
    @NodeIntrinsic
    public static native boolean probability(double probability, boolean condition);

    @Override
    public void lower(LoweringTool tool) {
        throw new GraalError("Branch probability could not be injected, because the probability value did not reduce to a constant value.");
    }
}
