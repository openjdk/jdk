/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.OptImplicitNullChecks;

import java.util.List;

import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.DynamicDeoptimizeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.NullCheckNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.LowTierContext;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

public class UseTrappingNullChecksPhase extends BasePhase<LowTierContext> {

    private static final CounterKey counterTrappingNullCheck = DebugContext.counter("TrappingNullCheck");
    private static final CounterKey counterTrappingNullCheckExistingRead = DebugContext.counter("TrappingNullCheckExistingRead");
    private static final CounterKey counterTrappingNullCheckUnreached = DebugContext.counter("TrappingNullCheckUnreached");
    private static final CounterKey counterTrappingNullCheckDynamicDeoptimize = DebugContext.counter("TrappingNullCheckDynamicDeoptimize");

    public static class Options {

        // @formatter:off
        @Option(help = "Use traps for null checks instead of explicit null-checks", type = OptionType.Expert)
        public static final OptionKey<Boolean> UseTrappingNullChecks = new OptionKey<>(true);
        // @formatter:on
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        if (!Options.UseTrappingNullChecks.getValue(graph.getOptions()) || context.getTarget().implicitNullCheckLimit <= 0) {
            return;
        }
        assert graph.getGuardsStage().areFrameStatesAtDeopts();

        long implicitNullCheckLimit = context.getTarget().implicitNullCheckLimit;
        for (DeoptimizeNode deopt : graph.getNodes(DeoptimizeNode.TYPE)) {
            tryUseTrappingNullCheck(deopt, deopt.predecessor(), deopt.getReason(), deopt.getSpeculation(), implicitNullCheckLimit);
        }
        for (DynamicDeoptimizeNode deopt : graph.getNodes(DynamicDeoptimizeNode.TYPE)) {
            tryUseTrappingNullCheck(context.getMetaAccess(), deopt, implicitNullCheckLimit);
        }

    }

    private static void tryUseTrappingNullCheck(MetaAccessProvider metaAccessProvider, DynamicDeoptimizeNode deopt, long implicitNullCheckLimit) {
        Node predecessor = deopt.predecessor();
        if (predecessor instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) predecessor;

            // Process each predecessor at the merge, unpacking the reasons and speculations as
            // needed.
            ValueNode reason = deopt.getActionAndReason();
            ValuePhiNode reasonPhi = null;
            List<ValueNode> reasons = null;
            int expectedPhis = 0;

            if (reason instanceof ValuePhiNode) {
                reasonPhi = (ValuePhiNode) reason;
                if (reasonPhi.merge() != merge) {
                    return;
                }
                reasons = reasonPhi.values().snapshot();
                expectedPhis++;
            } else if (!reason.isConstant()) {
                return;
            }

            ValueNode speculation = deopt.getSpeculation();
            ValuePhiNode speculationPhi = null;
            List<ValueNode> speculations = null;
            if (speculation instanceof ValuePhiNode) {
                speculationPhi = (ValuePhiNode) speculation;
                if (speculationPhi.merge() != merge) {
                    return;
                }
                speculations = speculationPhi.values().snapshot();
                expectedPhis++;
            }

            if (merge.phis().count() != expectedPhis) {
                return;
            }

            int index = 0;
            for (AbstractEndNode end : merge.cfgPredecessors().snapshot()) {
                ValueNode thisReason = reasons != null ? reasons.get(index) : reason;
                ValueNode thisSpeculation = speculations != null ? speculations.get(index++) : speculation;
                if (!thisReason.isConstant() || !thisSpeculation.isConstant()) {
                    continue;
                }
                Speculation speculationConstant = metaAccessProvider.decodeSpeculation(thisSpeculation.asJavaConstant(), deopt.graph().getSpeculationLog());
                if (!speculationConstant.equals(SpeculationLog.NO_SPECULATION)) {
                    continue;
                }
                DeoptimizationReason deoptimizationReason = metaAccessProvider.decodeDeoptReason(thisReason.asJavaConstant());
                tryUseTrappingNullCheck(deopt, end.predecessor(), deoptimizationReason, SpeculationLog.NO_SPECULATION, implicitNullCheckLimit);
            }
        }
    }

    private static void tryUseTrappingNullCheck(AbstractDeoptimizeNode deopt, Node predecessor, DeoptimizationReason deoptimizationReason, Speculation speculation, long implicitNullCheckLimit) {
        if (deoptimizationReason != DeoptimizationReason.NullCheckException && deoptimizationReason != DeoptimizationReason.UnreachedCode) {
            return;
        }
        assert speculation != null;
        if (!speculation.equals(SpeculationLog.NO_SPECULATION)) {
            return;
        }
        if (predecessor instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) predecessor;
            if (merge.phis().isEmpty()) {
                for (AbstractEndNode end : merge.cfgPredecessors().snapshot()) {
                    checkPredecessor(deopt, end.predecessor(), deoptimizationReason, implicitNullCheckLimit);
                }
            }
        } else if (predecessor instanceof AbstractBeginNode) {
            checkPredecessor(deopt, predecessor, deoptimizationReason, implicitNullCheckLimit);
        }
    }

    private static void checkPredecessor(AbstractDeoptimizeNode deopt, Node predecessor, DeoptimizationReason deoptimizationReason, long implicitNullCheckLimit) {
        Node current = predecessor;
        AbstractBeginNode branch = null;
        while (current instanceof AbstractBeginNode) {
            branch = (AbstractBeginNode) current;
            if (branch.anchored().isNotEmpty()) {
                // some input of the deopt framestate is anchored to this branch
                return;
            }
            current = current.predecessor();
        }
        if (current instanceof IfNode) {
            IfNode ifNode = (IfNode) current;
            if (branch != ifNode.trueSuccessor()) {
                return;
            }
            LogicNode condition = ifNode.condition();
            if (condition instanceof IsNullNode) {
                replaceWithTrappingNullCheck(deopt, ifNode, condition, deoptimizationReason, implicitNullCheckLimit);
            }
        }
    }

    private static void replaceWithTrappingNullCheck(AbstractDeoptimizeNode deopt, IfNode ifNode, LogicNode condition, DeoptimizationReason deoptimizationReason, long implicitNullCheckLimit) {
        DebugContext debug = deopt.getDebug();
        counterTrappingNullCheck.increment(debug);
        if (deopt instanceof DynamicDeoptimizeNode) {
            counterTrappingNullCheckDynamicDeoptimize.increment(debug);
        }
        if (deoptimizationReason == DeoptimizationReason.UnreachedCode) {
            counterTrappingNullCheckUnreached.increment(debug);
        }
        IsNullNode isNullNode = (IsNullNode) condition;
        AbstractBeginNode nonTrappingContinuation = ifNode.falseSuccessor();
        AbstractBeginNode trappingContinuation = ifNode.trueSuccessor();

        DeoptimizingFixedWithNextNode trappingNullCheck = null;
        FixedNode nextNonTrapping = nonTrappingContinuation.next();
        ValueNode value = isNullNode.getValue();
        if (OptImplicitNullChecks.getValue(ifNode.graph().getOptions()) && implicitNullCheckLimit > 0) {
            if (nextNonTrapping instanceof FixedAccessNode) {
                FixedAccessNode fixedAccessNode = (FixedAccessNode) nextNonTrapping;
                if (fixedAccessNode.canNullCheck()) {
                    AddressNode address = fixedAccessNode.getAddress();
                    ValueNode base = address.getBase();
                    ValueNode index = address.getIndex();
                    // allow for architectures which cannot fold an
                    // intervening uncompress out of the address chain
                    if (base != null && base instanceof CompressionNode) {
                        base = ((CompressionNode) base).getValue();
                    }
                    if (index != null && index instanceof CompressionNode) {
                        index = ((CompressionNode) index).getValue();
                    }
                    if (((base == value && index == null) || (base == null && index == value)) && address.getMaxConstantDisplacement() < implicitNullCheckLimit) {
                        // Opportunity for implicit null check as part of an existing read found!
                        fixedAccessNode.setStateBefore(deopt.stateBefore());
                        fixedAccessNode.setNullCheck(true);
                        deopt.graph().removeSplit(ifNode, nonTrappingContinuation);
                        trappingNullCheck = fixedAccessNode;
                        counterTrappingNullCheckExistingRead.increment(debug);
                    }
                }
            }
        }

        if (trappingNullCheck == null) {
            // Need to add a null check node.
            trappingNullCheck = deopt.graph().add(new NullCheckNode(value));
            deopt.graph().replaceSplit(ifNode, trappingNullCheck, nonTrappingContinuation);
        }

        trappingNullCheck.setStateBefore(deopt.stateBefore());

        /*
         * We now have the pattern NullCheck/BeginNode/... It's possible some node is using the
         * BeginNode as a guard input, so replace guard users of the Begin with the NullCheck and
         * then remove the Begin from the graph.
         */
        nonTrappingContinuation.replaceAtUsages(InputType.Guard, trappingNullCheck);

        if (nonTrappingContinuation instanceof BeginNode) {
            GraphUtil.unlinkFixedNode(nonTrappingContinuation);
            nonTrappingContinuation.safeDelete();
        }

        GraphUtil.killCFG(trappingContinuation);
        GraphUtil.tryKillUnused(isNullNode);
    }
}
