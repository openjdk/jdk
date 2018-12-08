/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.bytecode.BytecodeDisassembler;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.bytecode.Bytes;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.NormalizeCompareNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The {@code IfNode} represents a branch that can go one of two directions depending on the outcome
 * of a comparison.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_2, sizeRationale = "2 jmps")
public final class IfNode extends ControlSplitNode implements Simplifiable, LIRLowerable {
    public static final NodeClass<IfNode> TYPE = NodeClass.create(IfNode.class);

    private static final CounterKey CORRECTED_PROBABILITIES = DebugContext.counter("CorrectedProbabilities");

    @Successor AbstractBeginNode trueSuccessor;
    @Successor AbstractBeginNode falseSuccessor;
    @Input(InputType.Condition) LogicNode condition;
    protected double trueSuccessorProbability;

    public LogicNode condition() {
        return condition;
    }

    public void setCondition(LogicNode x) {
        updateUsages(condition, x);
        condition = x;
    }

    public IfNode(LogicNode condition, FixedNode trueSuccessor, FixedNode falseSuccessor, double trueSuccessorProbability) {
        this(condition, BeginNode.begin(trueSuccessor), BeginNode.begin(falseSuccessor), trueSuccessorProbability);
    }

    public IfNode(LogicNode condition, AbstractBeginNode trueSuccessor, AbstractBeginNode falseSuccessor, double trueSuccessorProbability) {
        super(TYPE, StampFactory.forVoid());
        this.condition = condition;
        this.falseSuccessor = falseSuccessor;
        this.trueSuccessor = trueSuccessor;
        setTrueSuccessorProbability(trueSuccessorProbability);
    }

    /**
     * Gets the true successor.
     *
     * @return the true successor
     */
    public AbstractBeginNode trueSuccessor() {
        return trueSuccessor;
    }

    /**
     * Gets the false successor.
     *
     * @return the false successor
     */
    public AbstractBeginNode falseSuccessor() {
        return falseSuccessor;
    }

    public double getTrueSuccessorProbability() {
        return this.trueSuccessorProbability;
    }

    public void setTrueSuccessor(AbstractBeginNode node) {
        updatePredecessor(trueSuccessor, node);
        trueSuccessor = node;
    }

    public void setFalseSuccessor(AbstractBeginNode node) {
        updatePredecessor(falseSuccessor, node);
        falseSuccessor = node;
    }

    /**
     * Gets the node corresponding to the specified outcome of the branch.
     *
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public AbstractBeginNode successor(boolean istrue) {
        return istrue ? trueSuccessor : falseSuccessor;
    }

    public void setTrueSuccessorProbability(double prob) {
        assert prob >= -0.000000001 && prob <= 1.000000001 : "Probability out of bounds: " + prob;
        trueSuccessorProbability = Math.min(1.0, Math.max(0.0, prob));
    }

    @Override
    public double probability(AbstractBeginNode successor) {
        return successor == trueSuccessor ? trueSuccessorProbability : 1 - trueSuccessorProbability;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.emitIf(this);
    }

    @Override
    public boolean verify() {
        assertTrue(condition() != null, "missing condition");
        assertTrue(trueSuccessor() != null, "missing trueSuccessor");
        assertTrue(falseSuccessor() != null, "missing falseSuccessor");
        return super.verify();
    }

    private boolean compareCallContext(NodeSourcePosition successorPosition) {
        NodeSourcePosition position = getNodeSourcePosition();
        NodeSourcePosition successor = successorPosition;
        while (position != null) {
            assertTrue(Objects.equals(position.getMethod(), successor.getMethod()), "method mismatch");
            position = position.getCaller();
            successor = successor.getCaller();
        }
        assertTrue(successor == null, "successor position has more methods");
        return true;
    }

    @Override
    public boolean verifySourcePosition() {
        NodeSourcePosition sourcePosition = getNodeSourcePosition();
        assertTrue(sourcePosition != null, "missing IfNode source position");

        NodeSourcePosition trueSuccessorPosition = trueSuccessor.getNodeSourcePosition();
        assertTrue(trueSuccessorPosition != null, "missing IfNode true successor source position");

        NodeSourcePosition falseSuccessorPosition = falseSuccessor.getNodeSourcePosition();
        assertTrue(falseSuccessorPosition != null, "missing IfNode false successor source position");

        int bci = sourcePosition.getBCI();
        ResolvedJavaMethod method = sourcePosition.getMethod();
        int bytecode = BytecodeDisassembler.getBytecodeAt(method, bci);

        if (!Bytecodes.isIfBytecode(bytecode)) {
            return true;
        }

        byte[] code = (new ResolvedJavaMethodBytecode(method)).getCode();
        int targetBCI = bci + Bytes.beS2(code, bci + 1);
        int nextBCI = bci + Bytecodes.lengthOf(bytecode);

        // At least one successor should have the correct BCI to indicate any possible negation that
        // occurred after bytecode parsing
        boolean matchingSuccessorFound = false;
        if (trueSuccessorPosition.getBCI() == nextBCI || trueSuccessorPosition.getBCI() == targetBCI) {
            assertTrue(compareCallContext(trueSuccessorPosition), "call context different from IfNode in trueSuccessor");
            matchingSuccessorFound = true;
        }

        if (falseSuccessorPosition.getBCI() == nextBCI || falseSuccessorPosition.getBCI() == targetBCI) {
            assertTrue(compareCallContext(falseSuccessorPosition), "call context different from IfNode in falseSuccessor");
            matchingSuccessorFound = true;
        }

        assertTrue(matchingSuccessorFound, "no matching successor position found in IfNode");
        assertTrue(trueSuccessorPosition.getBCI() != falseSuccessorPosition.getBCI(), "successor positions same in IfNode");

        return true;
    }

    public void eliminateNegation() {
        AbstractBeginNode oldTrueSuccessor = trueSuccessor;
        AbstractBeginNode oldFalseSuccessor = falseSuccessor;
        trueSuccessor = oldFalseSuccessor;
        falseSuccessor = oldTrueSuccessor;
        trueSuccessorProbability = 1 - trueSuccessorProbability;
        setCondition(((LogicNegationNode) condition).getValue());
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (trueSuccessor().next() instanceof DeoptimizeNode) {
            if (trueSuccessorProbability != 0) {
                CORRECTED_PROBABILITIES.increment(getDebug());
                trueSuccessorProbability = 0;
            }
        } else if (falseSuccessor().next() instanceof DeoptimizeNode) {
            if (trueSuccessorProbability != 1) {
                CORRECTED_PROBABILITIES.increment(getDebug());
                trueSuccessorProbability = 1;
            }
        }

        if (condition() instanceof LogicNegationNode) {
            eliminateNegation();
        }
        if (condition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition();
            if (c.getValue()) {
                tool.deleteBranch(falseSuccessor());
                tool.addToWorkList(trueSuccessor());
                graph().removeSplit(this, trueSuccessor());
            } else {
                tool.deleteBranch(trueSuccessor());
                tool.addToWorkList(falseSuccessor());
                graph().removeSplit(this, falseSuccessor());
            }
            return;
        }
        if (tool.allUsagesAvailable() && trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages()) {

            pushNodesThroughIf(tool);

            if (checkForUnsignedCompare(tool) || removeOrMaterializeIf(tool)) {
                return;
            }
        }

        if (removeIntermediateMaterialization(tool)) {
            return;
        }

        if (splitIfAtPhi(tool)) {
            return;
        }

        if (conditionalNodeOptimization(tool)) {
            return;
        }

        if (falseSuccessor().hasNoUsages() && (!(falseSuccessor() instanceof LoopExitNode)) && falseSuccessor().next() instanceof IfNode) {
            AbstractBeginNode intermediateBegin = falseSuccessor();
            IfNode nextIf = (IfNode) intermediateBegin.next();
            double probabilityB = (1.0 - this.trueSuccessorProbability) * nextIf.trueSuccessorProbability;
            if (this.trueSuccessorProbability < probabilityB) {
                // Reordering of those two if statements is beneficial from the point of view of
                // their probabilities.
                if (prepareForSwap(tool, condition(), nextIf.condition())) {
                    // Reordering is allowed from (if1 => begin => if2) to (if2 => begin => if1).
                    assert intermediateBegin.next() == nextIf;
                    AbstractBeginNode bothFalseBegin = nextIf.falseSuccessor();
                    nextIf.setFalseSuccessor(null);
                    intermediateBegin.setNext(null);
                    this.setFalseSuccessor(null);

                    this.replaceAtPredecessor(nextIf);
                    nextIf.setFalseSuccessor(intermediateBegin);
                    intermediateBegin.setNext(this);
                    this.setFalseSuccessor(bothFalseBegin);

                    NodeSourcePosition intermediateBeginPosition = intermediateBegin.getNodeSourcePosition();
                    intermediateBegin.setNodeSourcePosition(bothFalseBegin.getNodeSourcePosition());
                    bothFalseBegin.setNodeSourcePosition(intermediateBeginPosition);

                    nextIf.setTrueSuccessorProbability(probabilityB);
                    if (probabilityB == 1.0) {
                        this.setTrueSuccessorProbability(0.0);
                    } else {
                        double newProbability = this.trueSuccessorProbability / (1.0 - probabilityB);
                        this.setTrueSuccessorProbability(Math.min(1.0, newProbability));
                    }
                    return;
                }
            }
        }

        if (tryEliminateBoxedReferenceEquals(tool)) {
            return;
        }
    }

    private boolean isUnboxedFrom(MetaAccessProvider meta, NodeView view, ValueNode x, ValueNode src) {
        if (x == src) {
            return true;
        } else if (x instanceof UnboxNode) {
            return isUnboxedFrom(meta, view, ((UnboxNode) x).getValue(), src);
        } else if (x instanceof PiNode) {
            PiNode pi = (PiNode) x;
            return isUnboxedFrom(meta, view, pi.getOriginalNode(), src);
        } else if (x instanceof LoadFieldNode) {
            LoadFieldNode load = (LoadFieldNode) x;
            ResolvedJavaType integerType = meta.lookupJavaType(Integer.class);
            if (load.getValue().stamp(view).javaType(meta).equals(integerType)) {
                return isUnboxedFrom(meta, view, load.getValue(), src);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Attempts to replace the following pattern:
     *
     * <pre>
     * Integer x = ...;
     * Integer y = ...;
     * if ((x == y) || x.equals(y)) { ... }
     * </pre>
     *
     * with:
     *
     * <pre>
     * Integer x = ...;
     * Integer y = ...;
     * if (x.equals(y)) { ... }
     * </pre>
     *
     * whenever the probability that the reference check will pass is relatively small.
     *
     * See GR-1315 for more information.
     */
    private boolean tryEliminateBoxedReferenceEquals(SimplifierTool tool) {
        if (!(condition instanceof ObjectEqualsNode)) {
            return false;
        }

        MetaAccessProvider meta = tool.getMetaAccess();
        ObjectEqualsNode equalsCondition = (ObjectEqualsNode) condition;
        ValueNode x = equalsCondition.getX();
        ValueNode y = equalsCondition.getY();
        ResolvedJavaType integerType = meta.lookupJavaType(Integer.class);

        // At least one argument for reference equal must be a boxed primitive.
        NodeView view = NodeView.from(tool);
        if (!x.stamp(view).javaType(meta).equals(integerType) && !y.stamp(view).javaType(meta).equals(integerType)) {
            return false;
        }

        // The reference equality check is usually more efficient compared to a boxing check.
        // The success of the reference equals must therefore be relatively rare, otherwise it makes
        // no sense to eliminate it.
        if (getTrueSuccessorProbability() > 0.4) {
            return false;
        }

        // True branch must be empty.
        if (trueSuccessor instanceof BeginNode || trueSuccessor instanceof LoopExitNode) {
            if (trueSuccessor.next() instanceof EndNode) {
                // Empty true branch.
            } else {
                return false;
            }
        } else {
            return false;
        }

        // False branch must only check the unboxed values.
        UnboxNode unbox = null;
        FixedGuardNode unboxCheck = null;
        for (FixedNode node : falseSuccessor.getBlockNodes()) {
            if (!(node instanceof BeginNode || node instanceof UnboxNode || node instanceof FixedGuardNode || node instanceof EndNode ||
                            node instanceof LoadFieldNode || node instanceof LoopExitNode)) {
                return false;
            }
            if (node instanceof UnboxNode) {
                if (unbox == null) {
                    unbox = (UnboxNode) node;
                } else {
                    return false;
                }
            }
            if (!(node instanceof FixedGuardNode)) {
                continue;
            }
            FixedGuardNode fixed = (FixedGuardNode) node;
            if (!(fixed.condition() instanceof IntegerEqualsNode)) {
                continue;
            }
            IntegerEqualsNode equals = (IntegerEqualsNode) fixed.condition();
            if ((isUnboxedFrom(meta, view, equals.getX(), x) && isUnboxedFrom(meta, view, equals.getY(), y)) ||
                            (isUnboxedFrom(meta, view, equals.getX(), y) && isUnboxedFrom(meta, view, equals.getY(), x))) {
                unboxCheck = fixed;
            }
        }
        if (unbox == null || unboxCheck == null) {
            return false;
        }

        // Falsify the reference check.
        setCondition(graph().addOrUniqueWithInputs(LogicConstantNode.contradiction()));

        return true;
    }

    /**
     * Try to optimize this as if it were a {@link ConditionalNode}.
     */
    private boolean conditionalNodeOptimization(SimplifierTool tool) {
        if (trueSuccessor().next() instanceof AbstractEndNode && falseSuccessor().next() instanceof AbstractEndNode) {
            AbstractEndNode trueEnd = (AbstractEndNode) trueSuccessor().next();
            AbstractEndNode falseEnd = (AbstractEndNode) falseSuccessor().next();
            if (trueEnd.merge() != falseEnd.merge()) {
                return false;
            }
            if (!(trueEnd.merge() instanceof MergeNode)) {
                return false;
            }
            MergeNode merge = (MergeNode) trueEnd.merge();
            if (merge.usages().count() != 1 || merge.phis().count() != 1) {
                return false;
            }

            if (trueSuccessor().anchored().isNotEmpty() || falseSuccessor().anchored().isNotEmpty()) {
                return false;
            }

            PhiNode phi = merge.phis().first();
            ValueNode falseValue = phi.valueAt(falseEnd);
            ValueNode trueValue = phi.valueAt(trueEnd);

            NodeView view = NodeView.from(tool);
            ValueNode result = ConditionalNode.canonicalizeConditional(condition, trueValue, falseValue, phi.stamp(view), view);
            if (result != null) {
                /*
                 * canonicalizeConditional returns possibly new nodes so add them to the graph.
                 */
                if (result.graph() == null) {
                    result = graph().addOrUniqueWithInputs(result);
                }
                result = proxyReplacement(result);
                /*
                 * This optimization can be performed even if multiple values merge at this phi
                 * since the two inputs get simplified into one.
                 */
                phi.setValueAt(trueEnd, result);
                removeThroughFalseBranch(tool, merge);
                return true;
            }
        }

        return false;
    }

    private void pushNodesThroughIf(SimplifierTool tool) {
        assert trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages();
        // push similar nodes upwards through the if, thereby deduplicating them
        do {
            AbstractBeginNode trueSucc = trueSuccessor();
            AbstractBeginNode falseSucc = falseSuccessor();
            if (trueSucc instanceof BeginNode && falseSucc instanceof BeginNode && trueSucc.next() instanceof FixedWithNextNode && falseSucc.next() instanceof FixedWithNextNode) {
                FixedWithNextNode trueNext = (FixedWithNextNode) trueSucc.next();
                FixedWithNextNode falseNext = (FixedWithNextNode) falseSucc.next();
                NodeClass<?> nodeClass = trueNext.getNodeClass();
                if (trueNext.getClass() == falseNext.getClass()) {
                    if (trueNext instanceof AbstractBeginNode) {
                        // Cannot do this optimization for begin nodes, because it could
                        // move guards above the if that need to stay below a branch.
                    } else if (nodeClass.equalInputs(trueNext, falseNext) && trueNext.valueEquals(falseNext)) {
                        falseNext.replaceAtUsages(trueNext);
                        graph().removeFixed(falseNext);
                        GraphUtil.unlinkFixedNode(trueNext);
                        graph().addBeforeFixed(this, trueNext);
                        for (Node usage : trueNext.usages().snapshot()) {
                            if (usage.isAlive()) {
                                NodeClass<?> usageNodeClass = usage.getNodeClass();
                                if (usageNodeClass.valueNumberable() && !usageNodeClass.isLeafNode()) {
                                    Node newNode = graph().findDuplicate(usage);
                                    if (newNode != null) {
                                        usage.replaceAtUsagesAndDelete(newNode);
                                    }
                                }
                                if (usage.isAlive()) {
                                    tool.addToWorkList(usage);
                                }
                            }
                        }
                        continue;
                    }
                }
            }
            break;
        } while (true);
    }

    /**
     * Recognize a couple patterns that can be merged into an unsigned compare.
     *
     * @param tool
     * @return true if a replacement was done.
     */
    @SuppressWarnings("try")
    private boolean checkForUnsignedCompare(SimplifierTool tool) {
        assert trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages();
        if (condition() instanceof IntegerLessThanNode) {
            NodeView view = NodeView.from(tool);
            IntegerLessThanNode lessThan = (IntegerLessThanNode) condition();
            Constant y = lessThan.getY().stamp(view).asConstant();
            if (y instanceof PrimitiveConstant && ((PrimitiveConstant) y).asLong() == 0 && falseSuccessor().next() instanceof IfNode) {
                IfNode ifNode2 = (IfNode) falseSuccessor().next();
                if (ifNode2.condition() instanceof IntegerLessThanNode) {
                    IntegerLessThanNode lessThan2 = (IntegerLessThanNode) ifNode2.condition();
                    AbstractBeginNode falseSucc = ifNode2.falseSuccessor();
                    AbstractBeginNode trueSucc = ifNode2.trueSuccessor();
                    IntegerBelowNode below = null;
                    /*
                     * Convert x >= 0 && x < positive which is represented as !(x < 0) && x <
                     * <positive> into an unsigned compare.
                     */
                    if (lessThan2.getX() == lessThan.getX() && lessThan2.getY().stamp(view) instanceof IntegerStamp &&
                                    ((IntegerStamp) lessThan2.getY().stamp(view)).isPositive() &&
                                    sameDestination(trueSuccessor(), ifNode2.falseSuccessor)) {
                        below = graph().unique(new IntegerBelowNode(lessThan2.getX(), lessThan2.getY()));
                        // swap direction
                        AbstractBeginNode tmp = falseSucc;
                        falseSucc = trueSucc;
                        trueSucc = tmp;
                    } else if (lessThan2.getY() == lessThan.getX() && sameDestination(trueSuccessor(), ifNode2.trueSuccessor)) {
                        /*
                         * Convert x >= 0 && x <= positive which is represented as !(x < 0) &&
                         * !(<positive> > x), into x <| positive + 1. This can only be done for
                         * constants since there isn't a IntegerBelowEqualThanNode but that doesn't
                         * appear to be interesting.
                         */
                        JavaConstant positive = lessThan2.getX().asJavaConstant();
                        if (positive != null && positive.asLong() > 0 && positive.asLong() < positive.getJavaKind().getMaxValue()) {
                            ConstantNode newLimit = ConstantNode.forIntegerStamp(lessThan2.getX().stamp(view), positive.asLong() + 1, graph());
                            below = graph().unique(new IntegerBelowNode(lessThan.getX(), newLimit));
                        }
                    }
                    if (below != null) {
                        try (DebugCloseable position = ifNode2.withNodeSourcePosition()) {
                            ifNode2.setTrueSuccessor(null);
                            ifNode2.setFalseSuccessor(null);

                            IfNode newIfNode = graph().add(new IfNode(below, falseSucc, trueSucc, 1 - trueSuccessorProbability));
                            // Remove the < 0 test.
                            tool.deleteBranch(trueSuccessor);
                            graph().removeSplit(this, falseSuccessor);

                            // Replace the second test with the new one.
                            ifNode2.predecessor().replaceFirstSuccessor(ifNode2, newIfNode);
                            ifNode2.safeDelete();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check it these two blocks end up at the same place. Meeting at the same merge, or
     * deoptimizing in the same way.
     */
    private static boolean sameDestination(AbstractBeginNode succ1, AbstractBeginNode succ2) {
        Node next1 = succ1.next();
        Node next2 = succ2.next();
        if (next1 instanceof EndNode && next2 instanceof EndNode) {
            EndNode end1 = (EndNode) next1;
            EndNode end2 = (EndNode) next2;
            if (end1.merge() == end2.merge()) {
                for (PhiNode phi : end1.merge().phis()) {
                    if (phi.valueAt(end1) != phi.valueAt(end2)) {
                        return false;
                    }
                }
                // They go to the same MergeNode and merge the same values
                return true;
            }
        } else if (next1 instanceof DeoptimizeNode && next2 instanceof DeoptimizeNode) {
            DeoptimizeNode deopt1 = (DeoptimizeNode) next1;
            DeoptimizeNode deopt2 = (DeoptimizeNode) next2;
            if (deopt1.getReason() == deopt2.getReason() && deopt1.getAction() == deopt2.getAction()) {
                // Same deoptimization reason and action.
                return true;
            }
        } else if (next1 instanceof LoopExitNode && next2 instanceof LoopExitNode) {
            LoopExitNode exit1 = (LoopExitNode) next1;
            LoopExitNode exit2 = (LoopExitNode) next2;
            if (exit1.loopBegin() == exit2.loopBegin() && exit1.stateAfter() == exit2.stateAfter() && exit1.stateAfter() == null && sameDestination(exit1, exit2)) {
                // Exit the same loop and end up at the same place.
                return true;
            }
        } else if (next1 instanceof ReturnNode && next2 instanceof ReturnNode) {
            ReturnNode exit1 = (ReturnNode) next1;
            ReturnNode exit2 = (ReturnNode) next2;
            if (exit1.result() == exit2.result()) {
                // Exit the same loop and end up at the same place.
                return true;
            }
        }
        return false;
    }

    private static boolean prepareForSwap(SimplifierTool tool, LogicNode a, LogicNode b) {
        DebugContext debug = a.getDebug();
        if (a instanceof InstanceOfNode) {
            InstanceOfNode instanceOfA = (InstanceOfNode) a;
            if (b instanceof IsNullNode) {
                IsNullNode isNullNode = (IsNullNode) b;
                if (isNullNode.getValue() == instanceOfA.getValue()) {
                    debug.log("Can swap instanceof and isnull if");
                    return true;
                }
            } else if (b instanceof InstanceOfNode) {
                InstanceOfNode instanceOfB = (InstanceOfNode) b;
                if (instanceOfA.getValue() == instanceOfB.getValue() && !instanceOfA.type().getType().isInterface() && !instanceOfB.type().getType().isInterface() &&
                                !instanceOfA.type().getType().isAssignableFrom(instanceOfB.type().getType()) && !instanceOfB.type().getType().isAssignableFrom(instanceOfA.type().getType())) {
                    // Two instanceof on the same value with mutually exclusive types.
                    debug.log("Can swap instanceof for types %s and %s", instanceOfA.type(), instanceOfB.type());
                    return true;
                }
            }
        } else if (a instanceof CompareNode) {
            CompareNode compareA = (CompareNode) a;
            Condition conditionA = compareA.condition().asCondition();
            if (compareA.unorderedIsTrue()) {
                return false;
            }
            if (b instanceof CompareNode) {
                CompareNode compareB = (CompareNode) b;
                if (compareA == compareB) {
                    debug.log("Same conditions => do not swap and leave the work for global value numbering.");
                    return false;
                }
                if (compareB.unorderedIsTrue()) {
                    return false;
                }
                Condition comparableCondition = null;
                Condition conditionB = compareB.condition().asCondition();
                if (compareB.getX() == compareA.getX() && compareB.getY() == compareA.getY()) {
                    comparableCondition = conditionB;
                } else if (compareB.getX() == compareA.getY() && compareB.getY() == compareA.getX()) {
                    comparableCondition = conditionB.mirror();
                }

                if (comparableCondition != null) {
                    Condition combined = conditionA.join(comparableCondition);
                    if (combined == null) {
                        // The two conditions are disjoint => can reorder.
                        debug.log("Can swap disjoint coditions on same values: %s and %s", conditionA, comparableCondition);
                        return true;
                    }
                } else if (conditionA == Condition.EQ && conditionB == Condition.EQ) {
                    boolean canSwap = false;
                    if ((compareA.getX() == compareB.getX() && valuesDistinct(tool, compareA.getY(), compareB.getY()))) {
                        canSwap = true;
                    } else if ((compareA.getX() == compareB.getY() && valuesDistinct(tool, compareA.getY(), compareB.getX()))) {
                        canSwap = true;
                    } else if ((compareA.getY() == compareB.getX() && valuesDistinct(tool, compareA.getX(), compareB.getY()))) {
                        canSwap = true;
                    } else if ((compareA.getY() == compareB.getY() && valuesDistinct(tool, compareA.getX(), compareB.getX()))) {
                        canSwap = true;
                    }

                    if (canSwap) {
                        debug.log("Can swap equality condition with one shared and one disjoint value.");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean valuesDistinct(SimplifierTool tool, ValueNode a, ValueNode b) {
        if (a.isConstant() && b.isConstant()) {
            Boolean equal = tool.getConstantReflection().constantEquals(a.asConstant(), b.asConstant());
            if (equal != null) {
                return !equal.booleanValue();
            }
        }

        NodeView view = NodeView.from(tool);
        Stamp stampA = a.stamp(view);
        Stamp stampB = b.stamp(view);
        return stampA.alwaysDistinct(stampB);
    }

    /**
     * Tries to remove an empty if construct or replace an if construct with a materialization.
     *
     * @return true if a transformation was made, false otherwise
     */
    private boolean removeOrMaterializeIf(SimplifierTool tool) {
        assert trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages();
        if (trueSuccessor().next() instanceof AbstractEndNode && falseSuccessor().next() instanceof AbstractEndNode) {
            AbstractEndNode trueEnd = (AbstractEndNode) trueSuccessor().next();
            AbstractEndNode falseEnd = (AbstractEndNode) falseSuccessor().next();
            AbstractMergeNode merge = trueEnd.merge();
            if (merge == falseEnd.merge() && trueSuccessor().anchored().isEmpty() && falseSuccessor().anchored().isEmpty()) {
                PhiNode singlePhi = null;
                int distinct = 0;
                for (PhiNode phi : merge.phis()) {
                    ValueNode trueValue = phi.valueAt(trueEnd);
                    ValueNode falseValue = phi.valueAt(falseEnd);
                    if (trueValue != falseValue) {
                        distinct++;
                        singlePhi = phi;
                    }
                }
                if (distinct == 0) {
                    /*
                     * Multiple phis but merging same values for true and false, so simply delete
                     * the path
                     */
                    removeThroughFalseBranch(tool, merge);
                    return true;
                } else if (distinct == 1) {
                    ValueNode trueValue = singlePhi.valueAt(trueEnd);
                    ValueNode falseValue = singlePhi.valueAt(falseEnd);
                    ValueNode conditional = canonicalizeConditionalCascade(tool, trueValue, falseValue);
                    if (conditional != null) {
                        conditional = proxyReplacement(conditional);
                        singlePhi.setValueAt(trueEnd, conditional);
                        removeThroughFalseBranch(tool, merge);
                        return true;
                    }
                }
            }
        }
        if (trueSuccessor().next() instanceof ReturnNode && falseSuccessor().next() instanceof ReturnNode) {
            ReturnNode trueEnd = (ReturnNode) trueSuccessor().next();
            ReturnNode falseEnd = (ReturnNode) falseSuccessor().next();
            ValueNode trueValue = trueEnd.result();
            ValueNode falseValue = falseEnd.result();
            ValueNode value = null;
            if (trueValue != null) {
                if (trueValue == falseValue) {
                    value = trueValue;
                } else {
                    value = canonicalizeConditionalCascade(tool, trueValue, falseValue);
                    if (value == null) {
                        return false;
                    }
                }
            }
            ReturnNode newReturn = graph().add(new ReturnNode(value));
            replaceAtPredecessor(newReturn);
            GraphUtil.killCFG(this);
            return true;
        }
        return false;
    }

    private ValueNode proxyReplacement(ValueNode replacement) {
        /*
         * Special case: Every empty diamond we collapse to a conditional node can potentially
         * contain loop exit nodes on both branches. See the graph below: The two loop exits
         * (instanceof begin node) exit the same loop. The resulting phi is defined outside the
         * loop, but the resulting conditional node will be inside the loop, so we need to proxy the
         * resulting conditional node. Callers of this method ensure that true and false successor
         * have no usages, therefore a and b in the graph below can never be proxies themselves.
         */
        // @formatter:off
        //              +--+
        //              |If|
        //              +--+      +-----+ +-----+
        //         +----+  +----+ |  a  | |  b  |
        //         |Lex |  |Lex | +----^+ +^----+
        //         +----+  +----+      |   |
        //           +-------+         +---+
        //           | Merge +---------+Phi|
        //           +-------+         +---+
        // @formatter:on
        if (this.graph().hasValueProxies()) {
            if (trueSuccessor instanceof LoopExitNode && falseSuccessor instanceof LoopExitNode) {
                assert ((LoopExitNode) trueSuccessor).loopBegin() == ((LoopExitNode) falseSuccessor).loopBegin();
                /*
                 * we can collapse all proxy nodes on one loop exit, the surviving one, which will
                 * be the true successor
                 */
                if (falseSuccessor.anchored().isEmpty() && falseSuccessor.usages().isNotEmpty()) {
                    for (Node n : falseSuccessor.usages().snapshot()) {
                        assert n instanceof ProxyNode;
                        ((ProxyNode) n).setProxyPoint((LoopExitNode) trueSuccessor);
                    }
                }
                /*
                 * The true successor (surviving loop exit) can have usages, namely proxy nodes, the
                 * false successor however, must not have usages any more after the code above
                 */
                assert trueSuccessor.anchored().isEmpty() && falseSuccessor.usages().isEmpty();
                return this.graph().addOrUnique(new ValueProxyNode(replacement, (LoopExitNode) trueSuccessor));
            }
        }
        return replacement;
    }

    protected void removeThroughFalseBranch(SimplifierTool tool, AbstractMergeNode merge) {
        AbstractBeginNode trueBegin = trueSuccessor();
        LogicNode conditionNode = condition();
        graph().removeSplitPropagate(this, trueBegin);
        tool.addToWorkList(trueBegin);
        if (conditionNode != null) {
            GraphUtil.tryKillUnused(conditionNode);
        }
        if (merge.isAlive() && merge.forwardEndCount() > 1) {
            for (FixedNode end : merge.forwardEnds()) {
                Node cur = end;
                while (cur != null && cur.predecessor() instanceof BeginNode) {
                    cur = cur.predecessor();
                }
                if (cur != null && cur.predecessor() instanceof IfNode) {
                    tool.addToWorkList(cur.predecessor());
                }
            }
        }
    }

    private ValueNode canonicalizeConditionalCascade(SimplifierTool tool, ValueNode trueValue, ValueNode falseValue) {
        if (trueValue.getStackKind() != falseValue.getStackKind()) {
            return null;
        }
        if (trueValue.getStackKind() != JavaKind.Int && trueValue.getStackKind() != JavaKind.Long) {
            return null;
        }
        if (trueValue.isConstant() && falseValue.isConstant()) {
            return graph().unique(new ConditionalNode(condition(), trueValue, falseValue));
        } else if (!graph().isAfterExpandLogic()) {
            ConditionalNode conditional = null;
            ValueNode constant = null;
            boolean negateCondition;
            if (trueValue instanceof ConditionalNode && falseValue.isConstant()) {
                conditional = (ConditionalNode) trueValue;
                constant = falseValue;
                negateCondition = true;
            } else if (falseValue instanceof ConditionalNode && trueValue.isConstant()) {
                conditional = (ConditionalNode) falseValue;
                constant = trueValue;
                negateCondition = false;
            } else {
                return null;
            }
            boolean negateConditionalCondition = false;
            ValueNode otherValue = null;
            if (constant == conditional.trueValue()) {
                otherValue = conditional.falseValue();
                negateConditionalCondition = false;
            } else if (constant == conditional.falseValue()) {
                otherValue = conditional.trueValue();
                negateConditionalCondition = true;
            }
            if (otherValue != null && otherValue.isConstant()) {
                double shortCutProbability = probability(trueSuccessor());
                LogicNode newCondition = LogicNode.or(condition(), negateCondition, conditional.condition(), negateConditionalCondition, shortCutProbability);
                return graph().unique(new ConditionalNode(newCondition, constant, otherValue));
            } else if (constant.isJavaConstant() && conditional.trueValue().isJavaConstant() && conditional.falseValue().isJavaConstant() && condition() instanceof CompareNode &&
                            conditional.condition() instanceof CompareNode) {
                Condition cond1 = ((CompareNode) condition()).condition().asCondition();
                if (negateCondition) {
                    cond1 = cond1.negate();
                }
                // cond1 is EQ, NE, LT, or GE
                Condition cond2 = ((CompareNode) conditional.condition()).condition().asCondition();
                ValueNode x = ((CompareNode) condition()).getX();
                ValueNode y = ((CompareNode) condition()).getY();
                ValueNode x2 = ((CompareNode) conditional.condition()).getX();
                ValueNode y2 = ((CompareNode) conditional.condition()).getY();
                // `x cond1 y ? c1 : (x2 cond2 y2 ? c2 : c3)`
                boolean sameVars = x == x2 && y == y2;
                if (!sameVars && x == y2 && y == x2) {
                    sameVars = true;
                    cond2 = cond2.mirror();
                }
                // cond2 is EQ, LT, or GT
                if (sameVars) {
                    JavaKind stackKind = conditional.trueValue().stamp(NodeView.from(tool)).getStackKind();
                    assert !stackKind.isNumericFloat();
                    long c1 = constant.asJavaConstant().asLong();
                    long c2 = conditional.trueValue().asJavaConstant().asLong();
                    long c3 = conditional.falseValue().asJavaConstant().asLong();
                    // `x cond1 y ? c1 : (x cond2 y ? c2 : c3)`
                    if (cond1 == Condition.GE && cond2 == Condition.LT) {
                        // x >= y ? v1 : (x < y ? v2 : v3) => x >= y ? v1 : v2
                        return graph().unique(new ConditionalNode(condition(), conditional.trueValue(), constant));
                    } else if (cond1 == Condition.GE && cond2 == Condition.GT) {
                        // x >= y ? v1 : (x > y ? v2 : v3) => x >= y ? v1 : v3
                        return graph().unique(new ConditionalNode(condition(), conditional.falseValue(), constant));
                    } else if (cond1 == Condition.EQ && cond2 == Condition.EQ) {
                        // x == y ? v1 : (x == y ? v2 : v3) => x == y ? v1 : v3
                        return graph().unique(new ConditionalNode(condition(), conditional.falseValue(), constant));
                    } else if (cond1 == Condition.NE && cond2 == Condition.LT) {
                        // x != y ? v1 : (x < y ? v2 : v3) => x != y ? v1 : v3
                        return graph().unique(new ConditionalNode(condition(), conditional.falseValue(), constant));
                    } else if (cond1 == Condition.LT && cond2 == Condition.EQ && c1 == -1 && c2 == 0 && c3 == 1) {
                        // x < y ? -1 : (x == y ? 0 : 1) => x cmp y
                        return graph().unique(new NormalizeCompareNode(x, y, stackKind, false));
                    } else if (cond1 == Condition.LT && cond2 == Condition.EQ && c1 == 1 && c2 == 0 && c3 == -1) {
                        // x < y ? 1 : (x == y ? 0 : -1) => y cmp x
                        return graph().unique(new NormalizeCompareNode(y, x, stackKind, false));
                    } else if (cond1 == Condition.EQ && cond2 == Condition.LT && c1 == 0 && c2 == -1 && c3 == 1) {
                        // x == y ? 0 : (x < y ? -1 : 1) => x cmp y
                        return graph().unique(new NormalizeCompareNode(x, y, stackKind, false));
                    } else if (cond1 == Condition.EQ && cond2 == Condition.LT && c1 == 0 && c2 == 1 && c3 == -1) {
                        // x == y ? 0 : (x < y ? 1 : -1) => y cmp x
                        return graph().unique(new NormalizeCompareNode(y, x, stackKind, false));
                    } else if (cond1 == Condition.EQ && cond2 == Condition.GT && c1 == 0 && c2 == -1 && c3 == 1) {
                        // x == y ? 0 : (x > y ? -1 : 1) => y cmp x
                        return graph().unique(new NormalizeCompareNode(y, x, stackKind, false));
                    } else if (cond1 == Condition.EQ && cond2 == Condition.GT && c1 == 0 && c2 == 1 && c3 == -1) {
                        // x == y ? 0 : (x > y ? 1 : -1) => x cmp y
                        return graph().unique(new NormalizeCompareNode(x, y, stackKind, false));
                    } else if (cond1 == Condition.LT && cond2 == Condition.GT && c1 == 1 && c2 == -1 && c3 == 0) {
                        // x < y ? 1 : (x > y ? -1 : 0) => y cmp x
                        return graph().unique(new NormalizeCompareNode(y, x, stackKind, false));
                    } else if (cond1 == Condition.LT && cond2 == Condition.GT && c1 == -1 && c2 == 1 && c3 == 0) {
                        // x < y ? -1 : (x > y ? 1 : 0) => x cmp y
                        return graph().unique(new NormalizeCompareNode(x, y, stackKind, false));
                    }
                }
            }
        }
        return null;
    }

    /**
     * Take an if that is immediately dominated by a merge with a single phi and split off any paths
     * where the test would be statically decidable creating a new merge below the approriate side
     * of the IfNode. Any undecidable tests will continue to use the original IfNode.
     *
     * @param tool
     */
    @SuppressWarnings("try")
    private boolean splitIfAtPhi(SimplifierTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtSideEffects()) {
            // Disabled until we make sure we have no FrameState-less merges at this stage
            return false;
        }

        if (!(predecessor() instanceof MergeNode)) {
            return false;
        }
        MergeNode merge = (MergeNode) predecessor();
        if (merge.forwardEndCount() == 1) {
            // Don't bother.
            return false;
        }
        if (merge.usages().count() != 1 || merge.phis().count() != 1) {
            return false;
        }
        if (merge.stateAfter() != null) {
            /* We'll get the chance to simplify this after frame state assignment. */
            return false;
        }
        PhiNode phi = merge.phis().first();
        if (phi.usages().count() != 1) {
            /*
             * For simplicity the below code assumes assumes the phi goes dead at the end so skip
             * this case.
             */
            return false;
        }

        /*
         * Check that the condition uses the phi and that there is only one user of the condition
         * expression.
         */
        if (!conditionUses(condition(), phi)) {
            return false;
        }

        /*
         * We could additionally filter for the case that at least some of the Phi inputs or one of
         * the condition inputs are constants but there are cases where a non-constant is
         * simplifiable, usually where the stamp allows the question to be answered.
         */

        /* Each successor of the if gets a new merge if needed. */
        MergeNode trueMerge = null;
        MergeNode falseMerge = null;
        assert merge.stateAfter() == null;

        for (EndNode end : merge.forwardEnds().snapshot()) {
            Node value = phi.valueAt(end);
            LogicNode result = computeCondition(tool, condition, phi, value);
            if (result instanceof LogicConstantNode) {
                merge.removeEnd(end);
                if (((LogicConstantNode) result).getValue()) {
                    if (trueMerge == null) {
                        trueMerge = insertMerge(trueSuccessor());
                    }
                    trueMerge.addForwardEnd(end);
                } else {
                    if (falseMerge == null) {
                        falseMerge = insertMerge(falseSuccessor());
                    }
                    falseMerge.addForwardEnd(end);
                }
            } else if (result != condition) {
                // Build a new IfNode using the new condition
                BeginNode trueBegin = graph().add(new BeginNode());
                trueBegin.setNodeSourcePosition(trueSuccessor().getNodeSourcePosition());
                BeginNode falseBegin = graph().add(new BeginNode());
                falseBegin.setNodeSourcePosition(falseSuccessor().getNodeSourcePosition());

                if (result.graph() == null) {
                    result = graph().addOrUniqueWithInputs(result);
                    result.setNodeSourcePosition(condition.getNodeSourcePosition());
                }
                IfNode newIfNode = graph().add(new IfNode(result, trueBegin, falseBegin, trueSuccessorProbability));
                newIfNode.setNodeSourcePosition(getNodeSourcePosition());
                merge.removeEnd(end);
                ((FixedWithNextNode) end.predecessor()).setNext(newIfNode);

                if (trueMerge == null) {
                    trueMerge = insertMerge(trueSuccessor());
                }
                trueBegin.setNext(graph().add(new EndNode()));
                trueMerge.addForwardEnd((EndNode) trueBegin.next());

                if (falseMerge == null) {
                    falseMerge = insertMerge(falseSuccessor());
                }
                falseBegin.setNext(graph().add(new EndNode()));
                falseMerge.addForwardEnd((EndNode) falseBegin.next());

                end.safeDelete();
            }
        }

        transferProxies(trueSuccessor(), trueMerge);
        transferProxies(falseSuccessor(), falseMerge);

        cleanupMerge(merge);
        cleanupMerge(trueMerge);
        cleanupMerge(falseMerge);

        return true;
    }

    /**
     * @param condition
     * @param phi
     * @return true if the passed in {@code condition} uses {@code phi} and the condition is only
     *         used once. Since the phi will go dead the condition using it will also have to be
     *         dead after the optimization.
     */
    private static boolean conditionUses(LogicNode condition, PhiNode phi) {
        if (condition.usages().count() != 1) {
            return false;
        }
        if (condition instanceof ShortCircuitOrNode) {
            if (condition.graph().getGuardsStage().areDeoptsFixed()) {
                /*
                 * It can be unsafe to simplify a ShortCircuitOr before deopts are fixed because
                 * conversion to guards assumes that all the required conditions are being tested.
                 * Simplfying the condition based on context before this happens may lose a
                 * condition.
                 */
                ShortCircuitOrNode orNode = (ShortCircuitOrNode) condition;
                return (conditionUses(orNode.x, phi) || conditionUses(orNode.y, phi));
            }
        } else if (condition instanceof Canonicalizable.Unary<?>) {
            Canonicalizable.Unary<?> unary = (Canonicalizable.Unary<?>) condition;
            return unary.getValue() == phi;
        } else if (condition instanceof Canonicalizable.Binary<?>) {
            Canonicalizable.Binary<?> binary = (Canonicalizable.Binary<?>) condition;
            return binary.getX() == phi || binary.getY() == phi;
        }
        return false;
    }

    /**
     * Canonicalize {@code} condition using {@code value} in place of {@code phi}.
     *
     * @param tool
     * @param condition
     * @param phi
     * @param value
     * @return an improved LogicNode or the original condition
     */
    @SuppressWarnings("unchecked")
    private static LogicNode computeCondition(SimplifierTool tool, LogicNode condition, PhiNode phi, Node value) {
        if (condition instanceof ShortCircuitOrNode) {
            if (condition.graph().getGuardsStage().areDeoptsFixed() && !condition.graph().isAfterExpandLogic()) {
                ShortCircuitOrNode orNode = (ShortCircuitOrNode) condition;
                LogicNode resultX = computeCondition(tool, orNode.x, phi, value);
                LogicNode resultY = computeCondition(tool, orNode.y, phi, value);
                if (resultX != orNode.x || resultY != orNode.y) {
                    LogicNode result = orNode.canonical(tool, resultX, resultY);
                    if (result != orNode) {
                        return result;
                    }
                    /*
                     * Create a new node to carry the optimized inputs.
                     */
                    ShortCircuitOrNode newOr = new ShortCircuitOrNode(resultX, orNode.xNegated, resultY,
                                    orNode.yNegated, orNode.getShortCircuitProbability());
                    return newOr.canonical(tool);
                }
                return orNode;
            }
        } else if (condition instanceof Canonicalizable.Binary<?>) {
            Canonicalizable.Binary<Node> compare = (Canonicalizable.Binary<Node>) condition;
            if (compare.getX() == phi) {
                return (LogicNode) compare.canonical(tool, value, compare.getY());
            } else if (compare.getY() == phi) {
                return (LogicNode) compare.canonical(tool, compare.getX(), value);
            }
        } else if (condition instanceof Canonicalizable.Unary<?>) {
            Canonicalizable.Unary<Node> compare = (Canonicalizable.Unary<Node>) condition;
            if (compare.getValue() == phi) {
                return (LogicNode) compare.canonical(tool, value);
            }
        }
        if (condition instanceof Canonicalizable) {
            return (LogicNode) ((Canonicalizable) condition).canonical(tool);
        }
        return condition;
    }

    private static void transferProxies(AbstractBeginNode successor, MergeNode falseMerge) {
        if (successor instanceof LoopExitNode && falseMerge != null) {
            LoopExitNode loopExitNode = (LoopExitNode) successor;
            for (ProxyNode proxy : loopExitNode.proxies().snapshot()) {
                proxy.replaceFirstInput(successor, falseMerge);
            }
        }
    }

    private void cleanupMerge(MergeNode merge) {
        if (merge != null && merge.isAlive()) {
            if (merge.forwardEndCount() == 0) {
                GraphUtil.killCFG(merge);
            } else if (merge.forwardEndCount() == 1) {
                graph().reduceTrivialMerge(merge);
            }
        }
    }

    @SuppressWarnings("try")
    private MergeNode insertMerge(AbstractBeginNode begin) {
        MergeNode merge = graph().add(new MergeNode());
        if (!begin.anchored().isEmpty()) {
            Object before = null;
            before = begin.anchored().snapshot();
            begin.replaceAtUsages(InputType.Guard, merge);
            begin.replaceAtUsages(InputType.Anchor, merge);
            assert begin.anchored().isEmpty() : before + " " + begin.anchored().snapshot();
        }

        AbstractBeginNode theBegin = begin;
        if (begin instanceof LoopExitNode) {
            // Insert an extra begin to make it easier.
            try (DebugCloseable position = begin.withNodeSourcePosition()) {
                theBegin = graph().add(new BeginNode());
                begin.replaceAtPredecessor(theBegin);
                theBegin.setNext(begin);
            }
        }
        FixedNode next = theBegin.next();
        next.replaceAtPredecessor(merge);
        theBegin.setNext(graph().add(new EndNode()));
        merge.addForwardEnd((EndNode) theBegin.next());
        merge.setNext(next);
        return merge;
    }

    /**
     * Tries to connect code that initializes a variable directly with the successors of an if
     * construct that switches on the variable. For example, the pseudo code below:
     *
     * <pre>
     * contains(list, e, yes, no) {
     *     if (list == null || e == null) {
     *         condition = false;
     *     } else {
     *         condition = false;
     *         for (i in list) {
     *             if (i.equals(e)) {
     *                 condition = true;
     *                 break;
     *             }
     *         }
     *     }
     *     if (condition) {
     *         return yes;
     *     } else {
     *         return no;
     *     }
     * }
     * </pre>
     *
     * will be transformed into:
     *
     * <pre>
     * contains(list, e, yes, no) {
     *     if (list == null || e == null) {
     *         return no;
     *     } else {
     *         condition = false;
     *         for (i in list) {
     *             if (i.equals(e)) {
     *                 return yes;
     *             }
     *         }
     *         return no;
     *     }
     * }
     * </pre>
     *
     * @return true if a transformation was made, false otherwise
     */
    private boolean removeIntermediateMaterialization(SimplifierTool tool) {
        if (!(predecessor() instanceof AbstractMergeNode) || predecessor() instanceof LoopBeginNode) {
            return false;
        }
        AbstractMergeNode merge = (AbstractMergeNode) predecessor();

        if (!(condition() instanceof CompareNode)) {
            return false;
        }

        CompareNode compare = (CompareNode) condition();
        if (compare.getUsageCount() != 1) {
            return false;
        }

        // Only consider merges with a single usage that is both a phi and an operand of the
        // comparison
        NodeIterable<Node> mergeUsages = merge.usages();
        if (mergeUsages.count() != 1) {
            return false;
        }
        Node singleUsage = mergeUsages.first();
        if (!(singleUsage instanceof ValuePhiNode) || (singleUsage != compare.getX() && singleUsage != compare.getY())) {
            return false;
        }

        // Ensure phi is used by at most the comparison and the merge's frame state (if any)
        ValuePhiNode phi = (ValuePhiNode) singleUsage;
        NodeIterable<Node> phiUsages = phi.usages();
        if (phiUsages.count() > 2) {
            return false;
        }
        for (Node usage : phiUsages) {
            if (usage != compare && usage != merge.stateAfter()) {
                return false;
            }
        }

        List<EndNode> mergePredecessors = merge.cfgPredecessors().snapshot();
        assert phi.valueCount() == merge.forwardEndCount();

        Constant[] xs = constantValues(compare.getX(), merge, false);
        Constant[] ys = constantValues(compare.getY(), merge, false);
        if (xs == null || ys == null) {
            return false;
        }

        // Sanity check that both ends are not followed by a merge without frame state.
        if (!checkFrameState(trueSuccessor()) && !checkFrameState(falseSuccessor())) {
            return false;
        }

        List<EndNode> falseEnds = new ArrayList<>(mergePredecessors.size());
        List<EndNode> trueEnds = new ArrayList<>(mergePredecessors.size());
        EconomicMap<AbstractEndNode, ValueNode> phiValues = EconomicMap.create(Equivalence.IDENTITY, mergePredecessors.size());

        AbstractBeginNode oldFalseSuccessor = falseSuccessor();
        AbstractBeginNode oldTrueSuccessor = trueSuccessor();

        setFalseSuccessor(null);
        setTrueSuccessor(null);

        Iterator<EndNode> ends = mergePredecessors.iterator();
        for (int i = 0; i < xs.length; i++) {
            EndNode end = ends.next();
            phiValues.put(end, phi.valueAt(end));
            if (compare.condition().foldCondition(xs[i], ys[i], tool.getConstantReflection(), compare.unorderedIsTrue())) {
                trueEnds.add(end);
            } else {
                falseEnds.add(end);
            }
        }
        assert !ends.hasNext();
        assert falseEnds.size() + trueEnds.size() == xs.length;

        connectEnds(falseEnds, phiValues, oldFalseSuccessor, merge, tool);
        connectEnds(trueEnds, phiValues, oldTrueSuccessor, merge, tool);

        if (this.trueSuccessorProbability == 0.0) {
            for (AbstractEndNode endNode : trueEnds) {
                propagateZeroProbability(endNode);
            }
        }

        if (this.trueSuccessorProbability == 1.0) {
            for (AbstractEndNode endNode : falseEnds) {
                propagateZeroProbability(endNode);
            }
        }

        /*
         * Remove obsolete ends only after processing all ends, otherwise oldTrueSuccessor or
         * oldFalseSuccessor might have been removed if it is a LoopExitNode.
         */
        if (falseEnds.isEmpty()) {
            GraphUtil.killCFG(oldFalseSuccessor);
        }
        if (trueEnds.isEmpty()) {
            GraphUtil.killCFG(oldTrueSuccessor);
        }
        GraphUtil.killCFG(merge);

        assert !merge.isAlive() : merge;
        assert !phi.isAlive() : phi;
        assert !compare.isAlive() : compare;
        assert !this.isAlive() : this;

        return true;
    }

    private void propagateZeroProbability(FixedNode startNode) {
        Node prev = null;
        for (FixedNode node : GraphUtil.predecessorIterable(startNode)) {
            if (node instanceof IfNode) {
                IfNode ifNode = (IfNode) node;
                if (ifNode.trueSuccessor() == prev) {
                    if (ifNode.trueSuccessorProbability == 0.0) {
                        return;
                    } else if (ifNode.trueSuccessorProbability == 1.0) {
                        continue;
                    } else {
                        ifNode.setTrueSuccessorProbability(0.0);
                        return;
                    }
                } else if (ifNode.falseSuccessor() == prev) {
                    if (ifNode.trueSuccessorProbability == 1.0) {
                        return;
                    } else if (ifNode.trueSuccessorProbability == 0.0) {
                        continue;
                    } else {
                        ifNode.setTrueSuccessorProbability(1.0);
                        return;
                    }
                } else {
                    throw new GraalError("Illegal state");
                }
            } else if (node instanceof AbstractMergeNode && !(node instanceof LoopBeginNode)) {
                for (AbstractEndNode endNode : ((AbstractMergeNode) node).cfgPredecessors()) {
                    propagateZeroProbability(endNode);
                }
                return;
            }
            prev = node;
        }
    }

    private static boolean checkFrameState(FixedNode start) {
        FixedNode node = start;
        while (true) {
            if (node instanceof AbstractMergeNode) {
                AbstractMergeNode mergeNode = (AbstractMergeNode) node;
                if (mergeNode.stateAfter() == null) {
                    return false;
                } else {
                    return true;
                }
            } else if (node instanceof StateSplit) {
                StateSplit stateSplitNode = (StateSplit) node;
                if (stateSplitNode.stateAfter() != null) {
                    return true;
                }
            }

            if (node instanceof ControlSplitNode) {
                ControlSplitNode controlSplitNode = (ControlSplitNode) node;
                for (Node succ : controlSplitNode.cfgSuccessors()) {
                    if (checkFrameState((FixedNode) succ)) {
                        return true;
                    }
                }
                return false;
            } else if (node instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) node;
                node = fixedWithNextNode.next();
            } else if (node instanceof AbstractEndNode) {
                AbstractEndNode endNode = (AbstractEndNode) node;
                node = endNode.merge();
            } else if (node instanceof ControlSinkNode) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Connects a set of ends to a given successor, inserting a merge node if there is more than one
     * end. If {@code ends} is not empty, then {@code successor} is added to {@code tool}'s
     * {@linkplain SimplifierTool#addToWorkList(org.graalvm.compiler.graph.Node) work list}.
     *
     * @param oldMerge the merge being removed
     * @param phiValues the values of the phi at the merge, keyed by the merge ends
     */
    private void connectEnds(List<EndNode> ends, EconomicMap<AbstractEndNode, ValueNode> phiValues, AbstractBeginNode successor, AbstractMergeNode oldMerge, SimplifierTool tool) {
        if (!ends.isEmpty()) {
            if (ends.size() == 1) {
                AbstractEndNode end = ends.get(0);
                ((FixedWithNextNode) end.predecessor()).setNext(successor);
                oldMerge.removeEnd(end);
                GraphUtil.killCFG(end);
            } else {
                // Need a new phi in case the frame state is used by more than the merge being
                // removed.
                NodeView view = NodeView.from(tool);
                AbstractMergeNode newMerge = graph().add(new MergeNode());
                PhiNode oldPhi = (PhiNode) oldMerge.usages().first();
                PhiNode newPhi = graph().addWithoutUnique(new ValuePhiNode(oldPhi.stamp(view), newMerge));

                for (EndNode end : ends) {
                    newPhi.addInput(phiValues.get(end));
                    newMerge.addForwardEnd(end);
                }

                FrameState stateAfter = oldMerge.stateAfter();
                if (stateAfter != null) {
                    stateAfter = stateAfter.duplicate();
                    stateAfter.replaceFirstInput(oldPhi, newPhi);
                    newMerge.setStateAfter(stateAfter);
                }

                newMerge.setNext(successor);
            }
            tool.addToWorkList(successor);
        }
    }

    /**
     * Gets an array of constants derived from a node that is either a {@link ConstantNode} or a
     * {@link PhiNode} whose input values are all constants. The length of the returned array is
     * equal to the number of ends terminating in a given merge node.
     *
     * @return null if {@code node} is neither a {@link ConstantNode} nor a {@link PhiNode} whose
     *         input values are all constants
     */
    public static Constant[] constantValues(ValueNode node, AbstractMergeNode merge, boolean allowNull) {
        if (node.isConstant()) {
            Constant[] result = new Constant[merge.forwardEndCount()];
            Arrays.fill(result, node.asConstant());
            return result;
        }

        if (node instanceof PhiNode) {
            PhiNode phi = (PhiNode) node;
            if (phi.merge() == merge && phi instanceof ValuePhiNode && phi.valueCount() == merge.forwardEndCount()) {
                Constant[] result = new Constant[merge.forwardEndCount()];
                int i = 0;
                for (ValueNode n : phi.values()) {
                    if (!allowNull && !n.isConstant()) {
                        return null;
                    }
                    result[i++] = n.asConstant();
                }
                return result;
            }
        }

        return null;
    }

    @Override
    public AbstractBeginNode getPrimarySuccessor() {
        return null;
    }

    public AbstractBeginNode getSuccessor(boolean result) {
        return result ? this.trueSuccessor() : this.falseSuccessor();
    }

    @Override
    public boolean setProbability(AbstractBeginNode successor, double value) {
        if (successor == this.trueSuccessor()) {
            this.setTrueSuccessorProbability(value);
            return true;
        } else if (successor == this.falseSuccessor()) {
            this.setTrueSuccessorProbability(1.0 - value);
            return true;
        }
        return false;
    }

    @Override
    public int getSuccessorCount() {
        return 2;
    }
}
