/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.loop.phases;

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.util.List;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
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
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StaticDeoptimizingNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.LazyValue;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;

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
public class ConvertDeoptimizeToGuardPhase extends BasePhase<CoreProviders> {

    private static final Providers EMPTY_PROVIDERS = new Providers(null, null, null, null, null, null, null, null, null, null);

    @Override
    @SuppressWarnings("try")
    protected void run(final StructuredGraph graph, CoreProviders context) {
        assert graph.hasValueProxies() : "ConvertDeoptimizeToGuardPhase always creates proxies";
        assert !graph.getGuardsStage().areFrameStatesAtDeopts() : graph.getGuardsStage();
        LazyValue<LoopsData> lazyLoops = new LazyValue<>(() -> new LoopsData(graph));

        for (DeoptimizeNode d : graph.getNodes(DeoptimizeNode.TYPE)) {
            assert d.isAlive();
            if (d.getAction() == DeoptimizationAction.None) {
                continue;
            }
            try (DebugCloseable closable = d.withNodeSourcePosition()) {
                propagateFixed(d, d, context, lazyLoops);
            }
        }

        if (context != null) {
            for (FixedGuardNode fixedGuard : graph.getNodes(FixedGuardNode.TYPE)) {
                try (DebugCloseable closable = fixedGuard.withNodeSourcePosition()) {
                    trySplitFixedGuard(fixedGuard, context, lazyLoops);
                }
            }
        }

        new DeadCodeEliminationPhase(Optional).apply(graph);
    }

    private static void trySplitFixedGuard(FixedGuardNode fixedGuard, CoreProviders context, LazyValue<LoopsData> lazyLoops) {
        LogicNode condition = fixedGuard.condition();
        if (condition instanceof CompareNode) {
            CompareNode compare = (CompareNode) condition;
            ValueNode x = compare.getX();
            ValuePhiNode xPhi = (x instanceof ValuePhiNode) ? (ValuePhiNode) x : null;
            if (x instanceof ConstantNode || xPhi != null) {
                ValueNode y = compare.getY();
                ValuePhiNode yPhi = (y instanceof ValuePhiNode) ? (ValuePhiNode) y : null;
                if (y instanceof ConstantNode || yPhi != null) {
                    processFixedGuardAndPhis(fixedGuard, context, compare, x, xPhi, y, yPhi, lazyLoops);
                }
            }
        }
    }

    private static void processFixedGuardAndPhis(FixedGuardNode fixedGuard, CoreProviders context, CompareNode compare, ValueNode x, ValuePhiNode xPhi, ValueNode y, ValuePhiNode yPhi,
                    LazyValue<LoopsData> lazyLoops) {
        AbstractBeginNode pred = AbstractBeginNode.prevBegin(fixedGuard);
        if (pred instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) pred;
            if (xPhi != null && xPhi.merge() != merge) {
                return;
            }
            if (yPhi != null && yPhi.merge() != merge) {
                return;
            }

            processFixedGuardAndMerge(fixedGuard, context, compare, x, xPhi, y, yPhi, merge, lazyLoops);
        }
    }

    @SuppressWarnings("try")
    private static void processFixedGuardAndMerge(FixedGuardNode fixedGuard, CoreProviders context, CompareNode compare, ValueNode x, ValuePhiNode xPhi, ValueNode y, ValuePhiNode yPhi,
                    AbstractMergeNode merge, LazyValue<LoopsData> lazyLoops) {
        List<EndNode> mergePredecessors = merge.cfgPredecessors().snapshot();
        for (AbstractEndNode mergePredecessor : mergePredecessors) {
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
                try (DebugCloseable position = fixedGuard.withNodeSourcePosition()) {
                    propagateFixed(mergePredecessor, fixedGuard, context, lazyLoops);
                }
            }
        }
    }

    @SuppressWarnings("try")
    private static void propagateFixed(FixedNode from, StaticDeoptimizingNode deopt, CoreProviders providers, LazyValue<LoopsData> lazyLoops) {
        Node current = from;
        while (current != null) {
            if (GraalOptions.GuardPriorities.getValue(from.getOptions()) && current instanceof FixedGuardNode) {
                FixedGuardNode otherGuard = (FixedGuardNode) current;
                if (otherGuard.computePriority().isHigherPriorityThan(deopt.computePriority())) {
                    moveAsDeoptAfter(otherGuard, deopt);
                    return;
                }
            } else if (current instanceof AbstractBeginNode) {
                if (current instanceof AbstractMergeNode) {
                    AbstractMergeNode mergeNode = (AbstractMergeNode) current;
                    FixedNode next = mergeNode.next();
                    while (mergeNode.isAlive()) {
                        AbstractEndNode end = mergeNode.forwardEnds().first();
                        propagateFixed(end, deopt, providers, lazyLoops);
                    }
                    if (next.isAlive()) {
                        propagateFixed(next, deopt, providers, lazyLoops);
                    }
                    return;
                } else if (current.predecessor() instanceof IfNode) {
                    AbstractBeginNode begin = (AbstractBeginNode) current;
                    IfNode ifNode = (IfNode) current.predecessor();
                    if (isOsrLoopExit(begin) || isCountedLoopExit(ifNode, lazyLoops)) {
                        moveAsDeoptAfter(begin, deopt);
                    } else {
                        // Prioritize the source position of the IfNode
                        try (DebugCloseable closable = ifNode.withNodeSourcePosition()) {
                            StructuredGraph graph = ifNode.graph();
                            LogicNode conditionNode = ifNode.condition();
                            boolean negateGuardCondition = current == ifNode.trueSuccessor();
                            NodeSourcePosition survivingSuccessorPosition = negateGuardCondition ? ifNode.falseSuccessor().getNodeSourcePosition() : ifNode.trueSuccessor().getNodeSourcePosition();
                            FixedGuardNode guard = graph.add(
                                            new FixedGuardNode(conditionNode, deopt.getReason(), deopt.getAction(), deopt.getSpeculation(), negateGuardCondition, survivingSuccessorPosition));
                            FixedWithNextNode pred = (FixedWithNextNode) ifNode.predecessor();
                            AbstractBeginNode survivingSuccessor;
                            if (negateGuardCondition) {
                                survivingSuccessor = ifNode.falseSuccessor();
                            } else {
                                survivingSuccessor = ifNode.trueSuccessor();
                            }
                            graph.removeSplitPropagate(ifNode, survivingSuccessor);

                            Node newGuard = guard;
                            if (survivingSuccessor instanceof LoopExitNode) {
                                newGuard = ProxyNode.forGuard(guard, (LoopExitNode) survivingSuccessor);
                            }
                            survivingSuccessor.replaceAtUsages(newGuard, InputType.Guard);

                            graph.getDebug().log("Converting deopt on %-5s branch of %s to guard for remaining branch %s.", negateGuardCondition, ifNode, survivingSuccessor);
                            FixedNode next = pred.next();
                            pred.setNext(guard);
                            guard.setNext(next);
                            SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(providers != null ? providers : EMPTY_PROVIDERS, false, graph.getAssumptions(), graph.getOptions());
                            survivingSuccessor.simplify(simplifierTool);
                        }
                    }
                    return;
                } else if (current.predecessor() == null || current.predecessor() instanceof ControlSplitNode) {
                    assert current.predecessor() != null || (current instanceof StartNode && current == ((AbstractBeginNode) current).graph().start());
                    moveAsDeoptAfter((AbstractBeginNode) current, deopt);
                    return;
                }
            }
            current = current.predecessor();
        }
    }

    @SuppressWarnings("try")
    private static void moveAsDeoptAfter(FixedWithNextNode node, StaticDeoptimizingNode deopt) {
        try (DebugCloseable position = deopt.asNode().withNodeSourcePosition()) {
            FixedNode next = node.next();
            if (next != deopt.asNode()) {
                node.setNext(node.graph().add(new DeoptimizeNode(deopt.getAction(), deopt.getReason(), deopt.getSpeculation())));
                GraphUtil.killCFG(next);
            }
        }
    }

    private static boolean isOsrLoopExit(AbstractBeginNode node) {
        if (!(node instanceof LoopExitNode)) {
            return false;
        }
        return ((LoopExitNode) node).loopBegin().isOsrLoop();
    }

    private static boolean isCountedLoopExit(IfNode ifNode, LazyValue<LoopsData> lazyLoops) {
        LoopsData loopsData = lazyLoops.get();
        Loop<Block> loop = loopsData.getCFG().getNodeToBlock().get(ifNode).getLoop();
        if (loop != null) {
            LoopEx loopEx = loopsData.loop(loop);
            if (loopEx.detectCounted()) {
                return ifNode == loopEx.counted().getLimitTest();
            }
        }
        return false;
    }
}
