/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.util.List;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;

/**
 * This phase will find branches which always end with a {@link DeoptimizeNode} and replace their
 * {@link ControlSplitNode ControlSplitNodes} with {@link FixedGuardNode FixedGuardNodes}.
 *
 * This is useful because {@link FixedGuardNode FixedGuardNodes} will be lowered to {@link GuardNode
 * GuardNodes} which can later be optimized more aggressively than control-flow constructs.
 *
 * This is currently only done for branches that start from a {@link IfNode}. If it encounters a
 * branch starting at an other kind of {@link ControlSplitNode}, it will only bring the
 * {@link DeoptimizeNode} as close to the {@link ControlSplitNode} as possible.
 *
 */
public class ConvertDeoptimizeToGuardPhase extends BasePhase<PhaseContext> {
    private static AbstractBeginNode findBeginNode(FixedNode startNode) {
        return GraphUtil.predecessorIterable(startNode).filter(AbstractBeginNode.class).first();
    }

    @Override
    protected void run(final StructuredGraph graph, PhaseContext context) {
        assert graph.hasValueProxies() : "ConvertDeoptimizeToGuardPhase always creates proxies";
        if (graph.getNodes(DeoptimizeNode.TYPE).isEmpty()) {
            return;
        }
        for (DeoptimizeNode d : graph.getNodes(DeoptimizeNode.TYPE)) {
            assert d.isAlive();
            visitDeoptBegin(AbstractBeginNode.prevBegin(d), d.action(), d.reason(), d.getSpeculation(), graph, context != null ? context.getLowerer() : null);
        }

        if (context != null) {
            for (FixedGuardNode fixedGuard : graph.getNodes(FixedGuardNode.TYPE)) {
                trySplitFixedGuard(fixedGuard, context);
            }
        }

        new DeadCodeEliminationPhase(Optional).apply(graph);
    }

    private void trySplitFixedGuard(FixedGuardNode fixedGuard, PhaseContext context) {
        LogicNode condition = fixedGuard.condition();
        if (condition instanceof CompareNode) {
            CompareNode compare = (CompareNode) condition;
            ValueNode x = compare.getX();
            ValuePhiNode xPhi = (x instanceof ValuePhiNode) ? (ValuePhiNode) x : null;
            if (x instanceof ConstantNode || xPhi != null) {
                ValueNode y = compare.getY();
                ValuePhiNode yPhi = (y instanceof ValuePhiNode) ? (ValuePhiNode) y : null;
                if (y instanceof ConstantNode || yPhi != null) {
                    processFixedGuardAndPhis(fixedGuard, context, compare, x, xPhi, y, yPhi);
                }
            }
        }
    }

    private void processFixedGuardAndPhis(FixedGuardNode fixedGuard, PhaseContext context, CompareNode compare, ValueNode x, ValuePhiNode xPhi, ValueNode y, ValuePhiNode yPhi) {
        AbstractBeginNode pred = AbstractBeginNode.prevBegin(fixedGuard);
        if (pred instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) pred;
            if (xPhi != null && xPhi.merge() != merge) {
                return;
            }
            if (yPhi != null && yPhi.merge() != merge) {
                return;
            }

            processFixedGuardAndMerge(fixedGuard, context, compare, x, xPhi, y, yPhi, merge);
        }
    }

    private void processFixedGuardAndMerge(FixedGuardNode fixedGuard, PhaseContext context, CompareNode compare, ValueNode x, ValuePhiNode xPhi, ValueNode y, ValuePhiNode yPhi,
                    AbstractMergeNode merge) {
        List<EndNode> mergePredecessors = merge.cfgPredecessors().snapshot();
        for (int i = 0; i < mergePredecessors.size(); ++i) {
            AbstractEndNode mergePredecessor = mergePredecessors.get(i);
            if (!mergePredecessor.isAlive()) {
                break;
            }
            Constant xs;
            if (xPhi == null) {
                xs = x.asConstant();
            } else {
                xs = xPhi.valueAt(mergePredecessor).asConstant();
            }
            Constant ys;
            if (yPhi == null) {
                ys = y.asConstant();
            } else {
                ys = yPhi.valueAt(mergePredecessor).asConstant();
            }
            if (xs != null && ys != null && compare.condition().foldCondition(xs, ys, context.getConstantReflection(), compare.unorderedIsTrue()) == fixedGuard.isNegated()) {
                visitDeoptBegin(AbstractBeginNode.prevBegin(mergePredecessor), fixedGuard.getAction(), fixedGuard.getReason(), fixedGuard.getSpeculation(), fixedGuard.graph(), context.getLowerer());
            }
        }
    }

    private void visitDeoptBegin(AbstractBeginNode deoptBegin, DeoptimizationAction deoptAction, DeoptimizationReason deoptReason, JavaConstant speculation, StructuredGraph graph,
                    LoweringProvider loweringProvider) {
        if (deoptBegin.predecessor() instanceof AbstractBeginNode) {
            /*
             * Walk up chains of LoopExitNodes to the "real" BeginNode that leads to deoptimization.
             */
            visitDeoptBegin((AbstractBeginNode) deoptBegin.predecessor(), deoptAction, deoptReason, speculation, graph, loweringProvider);
            return;
        }

        if (deoptBegin instanceof AbstractMergeNode) {
            AbstractMergeNode mergeNode = (AbstractMergeNode) deoptBegin;
            Debug.log("Visiting %s", mergeNode);
            FixedNode next = mergeNode.next();
            while (mergeNode.isAlive()) {
                AbstractEndNode end = mergeNode.forwardEnds().first();
                AbstractBeginNode newBeginNode = findBeginNode(end);
                visitDeoptBegin(newBeginNode, deoptAction, deoptReason, speculation, graph, loweringProvider);
            }
            assert next.isAlive();
            AbstractBeginNode newBeginNode = findBeginNode(next);
            visitDeoptBegin(newBeginNode, deoptAction, deoptReason, speculation, graph, loweringProvider);
            return;
        } else if (deoptBegin.predecessor() instanceof IfNode) {
            IfNode ifNode = (IfNode) deoptBegin.predecessor();
            AbstractBeginNode otherBegin = ifNode.trueSuccessor();
            LogicNode conditionNode = ifNode.condition();
            FixedGuardNode guard = graph.add(new FixedGuardNode(conditionNode, deoptReason, deoptAction, speculation, deoptBegin == ifNode.trueSuccessor()));
            FixedWithNextNode pred = (FixedWithNextNode) ifNode.predecessor();
            AbstractBeginNode survivingSuccessor;
            if (deoptBegin == ifNode.trueSuccessor()) {
                survivingSuccessor = ifNode.falseSuccessor();
            } else {
                survivingSuccessor = ifNode.trueSuccessor();
            }
            graph.removeSplitPropagate(ifNode, survivingSuccessor);

            Node newGuard = guard;
            if (survivingSuccessor instanceof LoopExitNode) {
                newGuard = ProxyNode.forGuard(guard, (LoopExitNode) survivingSuccessor, graph);
            }
            survivingSuccessor.replaceAtUsages(InputType.Guard, newGuard);

            Debug.log("Converting deopt on %-5s branch of %s to guard for remaining branch %s.", deoptBegin == ifNode.trueSuccessor() ? "true" : "false", ifNode, otherBegin);
            FixedNode next = pred.next();
            pred.setNext(guard);
            guard.setNext(next);
            SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(null, null, null, false, graph.getAssumptions(), loweringProvider);
            survivingSuccessor.simplify(simplifierTool);
            return;
        }

        // We could not convert the control split - at least cut off control flow after the split.
        FixedWithNextNode deoptPred = deoptBegin;
        FixedNode next = deoptPred.next();

        if (!(next instanceof DeoptimizeNode)) {
            DeoptimizeNode newDeoptNode = graph.add(new DeoptimizeNode(deoptAction, deoptReason, speculation));
            deoptPred.setNext(newDeoptNode);
            assert deoptPred == newDeoptNode.predecessor();
            GraphUtil.killCFG(next);
        }
    }
}
