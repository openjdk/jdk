/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop;

import static org.graalvm.compiler.graph.Node.newIdentityMap;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.loop.InductionVariable.Direction;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.code.BytecodeFrame;

public class LoopEx {

    private final Loop<Block> loop;
    private LoopFragmentInside inside;
    private LoopFragmentWhole whole;
    private CountedLoopInfo counted;
    private LoopsData data;
    private Map<Node, InductionVariable> ivs;

    LoopEx(Loop<Block> loop, LoopsData data) {
        this.loop = loop;
        this.data = data;
    }

    public Loop<Block> loop() {
        return loop;
    }

    public LoopFragmentInside inside() {
        if (inside == null) {
            inside = new LoopFragmentInside(this);
        }
        return inside;
    }

    public LoopFragmentWhole whole() {
        if (whole == null) {
            whole = new LoopFragmentWhole(this);
        }
        return whole;
    }

    public void invalidateFragments() {
        inside = null;
        whole = null;
    }

    @SuppressWarnings("unused")
    public LoopFragmentInsideFrom insideFrom(FixedNode point) {
        // TODO (gd)
        return null;
    }

    @SuppressWarnings("unused")
    public LoopFragmentInsideBefore insideBefore(FixedNode point) {
        // TODO (gd)
        return null;
    }

    public boolean isOutsideLoop(Node n) {
        return !whole().contains(n);
    }

    public LoopBeginNode loopBegin() {
        return (LoopBeginNode) loop().getHeader().getBeginNode();
    }

    public FixedNode predecessor() {
        return (FixedNode) loopBegin().forwardEnd().predecessor();
    }

    public FixedNode entryPoint() {
        return loopBegin().forwardEnd();
    }

    public boolean isCounted() {
        return counted != null;
    }

    public CountedLoopInfo counted() {
        return counted;
    }

    public LoopEx parent() {
        if (loop.getParent() == null) {
            return null;
        }
        return data.loop(loop.getParent());
    }

    public int size() {
        return whole().nodes().count();
    }

    @Override
    public String toString() {
        return (isCounted() ? "CountedLoop [" + counted() + "] " : "Loop ") + "(depth=" + loop().getDepth() + ") " + loopBegin();
    }

    private class InvariantPredicate implements NodePredicate {

        @Override
        public boolean apply(Node n) {
            return isOutsideLoop(n);
        }
    }

    public void reassociateInvariants() {
        InvariantPredicate invariant = new InvariantPredicate();
        StructuredGraph graph = loopBegin().graph();
        for (BinaryArithmeticNode<?> binary : whole().nodes().filter(BinaryArithmeticNode.class)) {
            if (!binary.isAssociative()) {
                continue;
            }
            BinaryArithmeticNode<?> result = BinaryArithmeticNode.reassociate(binary, invariant, binary.getX(), binary.getY());
            if (result != binary) {
                if (Debug.isLogEnabled()) {
                    Debug.log("%s : Reassociated %s into %s", graph.method().format("%H::%n"), binary, result);
                }
                if (!result.isAlive()) {
                    assert !result.isDeleted();
                    result = graph.addOrUniqueWithInputs(result);
                }
                binary.replaceAtUsages(result);
                GraphUtil.killWithUnusedFloatingInputs(binary);
            }
        }
    }

    public boolean detectCounted() {
        LoopBeginNode loopBegin = loopBegin();
        FixedNode next = loopBegin.next();
        while (next instanceof FixedGuardNode || next instanceof ValueAnchorNode || next instanceof FullInfopointNode) {
            next = ((FixedWithNextNode) next).next();
        }
        if (next instanceof IfNode) {
            IfNode ifNode = (IfNode) next;
            boolean negated = false;
            if (!loopBegin.isLoopExit(ifNode.falseSuccessor())) {
                if (!loopBegin.isLoopExit(ifNode.trueSuccessor())) {
                    return false;
                }
                negated = true;
            }
            LogicNode ifTest = ifNode.condition();
            if (!(ifTest instanceof IntegerLessThanNode) && !(ifTest instanceof IntegerEqualsNode)) {
                if (ifTest instanceof IntegerBelowNode) {
                    Debug.log("Ignored potential Counted loop at %s with |<|", loopBegin);
                }
                return false;
            }
            CompareNode lessThan = (CompareNode) ifTest;
            Condition condition = null;
            InductionVariable iv = null;
            ValueNode limit = null;
            if (isOutsideLoop(lessThan.getX())) {
                iv = getInductionVariables().get(lessThan.getY());
                if (iv != null) {
                    condition = lessThan.condition().mirror();
                    limit = lessThan.getX();
                }
            } else if (isOutsideLoop(lessThan.getY())) {
                iv = getInductionVariables().get(lessThan.getX());
                if (iv != null) {
                    condition = lessThan.condition();
                    limit = lessThan.getY();
                }
            }
            if (condition == null) {
                return false;
            }
            if (negated) {
                condition = condition.negate();
            }
            boolean oneOff = false;
            switch (condition) {
                case EQ:
                    return false;
                case NE: {
                    if (!iv.isConstantStride() || Math.abs(iv.constantStride()) != 1) {
                        return false;
                    }
                    IntegerStamp initStamp = (IntegerStamp) iv.initNode().stamp();
                    IntegerStamp limitStamp = (IntegerStamp) limit.stamp();
                    if (iv.direction() == Direction.Up) {
                        if (initStamp.upperBound() > limitStamp.lowerBound()) {
                            return false;
                        }
                    } else if (iv.direction() == Direction.Down) {
                        if (initStamp.lowerBound() < limitStamp.upperBound()) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                    break;
                }
                case LE:
                    oneOff = true;
                    if (iv.direction() != Direction.Up) {
                        return false;
                    }
                    break;
                case LT:
                    if (iv.direction() != Direction.Up) {
                        return false;
                    }
                    break;
                case GE:
                    oneOff = true;
                    if (iv.direction() != Direction.Down) {
                        return false;
                    }
                    break;
                case GT:
                    if (iv.direction() != Direction.Down) {
                        return false;
                    }
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
            counted = new CountedLoopInfo(this, iv, limit, oneOff, negated ? ifNode.falseSuccessor() : ifNode.trueSuccessor());
            return true;
        }
        return false;
    }

    public LoopsData loopsData() {
        return data;
    }

    public void nodesInLoopBranch(NodeBitMap branchNodes, AbstractBeginNode branch) {
        Collection<AbstractBeginNode> blocks = new LinkedList<>();
        Collection<LoopExitNode> exits = new LinkedList<>();
        Queue<Block> work = new LinkedList<>();
        ControlFlowGraph cfg = loopsData().getCFG();
        work.add(cfg.blockFor(branch));
        while (!work.isEmpty()) {
            Block b = work.remove();
            if (loop().getExits().contains(b)) {
                exits.add((LoopExitNode) b.getBeginNode());
            } else {
                blocks.add(b.getBeginNode());
                for (Block d : b.getDominated()) {
                    if (loop.getBlocks().contains(d)) {
                        work.add(d);
                    }
                }
            }
        }
        LoopFragment.computeNodes(branchNodes, branch.graph(), blocks, exits);
    }

    public Map<Node, InductionVariable> getInductionVariables() {
        if (ivs == null) {
            ivs = findInductionVariables(this);
        }
        return ivs;
    }

    /**
     * Collect all the basic induction variables for the loop and the find any induction variables
     * which are derived from the basic ones.
     *
     * @param loop
     * @return a map from node to induction variable
     */
    private static Map<Node, InductionVariable> findInductionVariables(LoopEx loop) {
        Map<Node, InductionVariable> ivs = newIdentityMap();

        Queue<InductionVariable> scanQueue = new LinkedList<>();
        LoopBeginNode loopBegin = loop.loopBegin();
        AbstractEndNode forwardEnd = loopBegin.forwardEnd();
        for (PhiNode phi : loopBegin.phis().filter(ValuePhiNode.class)) {
            ValueNode backValue = phi.singleBackValue();
            if (backValue == PhiNode.MULTIPLE_VALUES) {
                continue;
            }
            ValueNode stride = addSub(loop, backValue, phi);
            if (stride != null) {
                BasicInductionVariable biv = new BasicInductionVariable(loop, (ValuePhiNode) phi, phi.valueAt(forwardEnd), stride, (BinaryArithmeticNode<?>) backValue);
                ivs.put(phi, biv);
                scanQueue.add(biv);
            }
        }

        while (!scanQueue.isEmpty()) {
            InductionVariable baseIv = scanQueue.remove();
            ValueNode baseIvNode = baseIv.valueNode();
            for (ValueNode op : baseIvNode.usages().filter(ValueNode.class)) {
                if (loop.isOutsideLoop(op)) {
                    continue;
                }
                if (op.usages().count() == 1 && op.usages().first() == baseIvNode) {
                    /*
                     * This is just the base induction variable increment with no other uses so
                     * don't bother reporting it.
                     */
                    continue;
                }
                InductionVariable iv = null;
                ValueNode offset = addSub(loop, op, baseIvNode);
                ValueNode scale;
                if (offset != null) {
                    iv = new DerivedOffsetInductionVariable(loop, baseIv, offset, (BinaryArithmeticNode<?>) op);
                } else if (op instanceof NegateNode) {
                    iv = new DerivedScaledInductionVariable(loop, baseIv, (NegateNode) op);
                } else if ((scale = mul(loop, op, baseIvNode)) != null) {
                    iv = new DerivedScaledInductionVariable(loop, baseIv, scale, op);
                } else {
                    boolean isValidConvert = op instanceof PiNode || op instanceof SignExtendNode;
                    if (!isValidConvert && op instanceof ZeroExtendNode) {
                        IntegerStamp inputStamp = (IntegerStamp) ((ZeroExtendNode) op).getValue().stamp();
                        isValidConvert = inputStamp.isPositive();
                    }

                    if (isValidConvert) {
                        iv = new DerivedConvertedInductionVariable(loop, baseIv, op.stamp(), op);
                    }
                }

                if (iv != null) {
                    ivs.put(op, iv);
                    scanQueue.offer(iv);
                }
            }
        }
        return Collections.unmodifiableMap(ivs);
    }

    private static ValueNode addSub(LoopEx loop, ValueNode op, ValueNode base) {
        if (op.stamp() instanceof IntegerStamp && (op instanceof AddNode || op instanceof SubNode)) {
            BinaryArithmeticNode<?> aritOp = (BinaryArithmeticNode<?>) op;
            if (aritOp.getX() == base && loop.isOutsideLoop(aritOp.getY())) {
                return aritOp.getY();
            } else if (aritOp.getY() == base && loop.isOutsideLoop(aritOp.getX())) {
                return aritOp.getX();
            }
        }
        return null;
    }

    private static ValueNode mul(LoopEx loop, ValueNode op, ValueNode base) {
        if (op instanceof MulNode) {
            MulNode mul = (MulNode) op;
            if (mul.getX() == base && loop.isOutsideLoop(mul.getY())) {
                return mul.getY();
            } else if (mul.getY() == base && loop.isOutsideLoop(mul.getX())) {
                return mul.getX();
            }
        }
        if (op instanceof LeftShiftNode) {
            LeftShiftNode shift = (LeftShiftNode) op;
            if (shift.getX() == base && shift.getY().isConstant()) {
                return ConstantNode.forIntegerStamp(base.stamp(), 1 << shift.getY().asJavaConstant().asInt(), base.graph());
            }
        }
        return null;
    }

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public void deleteUnusedNodes() {
        if (ivs != null) {
            for (InductionVariable iv : ivs.values()) {
                iv.deleteUnusedNodes();
            }
        }
    }

    /**
     * @return true if all nodes in the loop can be duplicated.
     */
    public boolean canDuplicateLoop() {
        for (Node node : inside().nodes()) {
            if (node instanceof ControlFlowAnchorNode) {
                return false;
            }
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;
                if (frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI || frameState.bci == BytecodeFrame.UNWIND_BCI) {
                    return false;
                }
            }
        }
        return true;
    }
}
