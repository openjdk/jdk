/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.graph.ScheduledNodeIterator;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;

/**
 * This phase lowers {@link GuardNode GuardNodes} into corresponding control-flow structure and
 * {@link DeoptimizeNode DeoptimizeNodes}.
 *
 * This allow to enter the {@link GuardsStage#FIXED_DEOPTS FIXED_DEOPTS} stage of the graph where
 * all node that may cause deoptimization are fixed.
 * <p>
 * It first makes a schedule in order to know where the control flow should be placed. Then, for
 * each block, it applies two passes. The first one tries to replace null-check guards with implicit
 * null checks performed by access to the objects that need to be null checked. The second phase
 * does the actual control-flow expansion of the remaining {@link GuardNode GuardNodes}.
 */
public class GuardLoweringPhase extends BasePhase<CoreProviders> {

    private static class LowerGuards extends ScheduledNodeIterator {

        private boolean useGuardIdAsDebugId;

        LowerGuards(boolean useGuardIdAsDebugId) {
            this.useGuardIdAsDebugId = useGuardIdAsDebugId;
        }

        @Override
        protected void processNode(Node node) {
            if (node instanceof GuardNode) {
                GuardNode guard = (GuardNode) node;
                FixedWithNextNode lowered = guard.lowerGuard();
                if (lowered != null) {
                    replaceCurrent(lowered);
                } else {
                    lowerToIf(guard);
                }
            }
        }

        @SuppressWarnings("try")
        private void lowerToIf(GuardNode guard) {
            try (DebugCloseable position = guard.withNodeSourcePosition()) {
                StructuredGraph graph = guard.graph();
                AbstractBeginNode fastPath = graph.add(new BeginNode());
                fastPath.setNodeSourcePosition(guard.getNoDeoptSuccessorPosition());
                @SuppressWarnings("deprecation")
                int debugId = useGuardIdAsDebugId ? guard.getId() : DeoptimizeNode.DEFAULT_DEBUG_ID;
                DeoptimizeNode deopt = graph.add(new DeoptimizeNode(guard.getAction(), guard.getReason(), debugId, guard.getSpeculation(), null));
                AbstractBeginNode deoptBranch = BeginNode.begin(deopt);
                AbstractBeginNode trueSuccessor;
                AbstractBeginNode falseSuccessor;
                if (guard.isNegated()) {
                    trueSuccessor = deoptBranch;
                    falseSuccessor = fastPath;
                } else {
                    trueSuccessor = fastPath;
                    falseSuccessor = deoptBranch;
                }
                IfNode ifNode = graph.add(new IfNode(guard.getCondition(), trueSuccessor, falseSuccessor, trueSuccessor == fastPath ? 1 : 0));
                guard.replaceAndDelete(fastPath);
                insert(ifNode, fastPath);
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (graph.getGuardsStage().allowsFloatingGuards()) {
            SchedulePhase schedulePhase = new SchedulePhase(SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);
            schedulePhase.apply(graph);
            ScheduleResult schedule = graph.getLastSchedule();

            for (Block block : schedule.getCFG().getBlocks()) {
                processBlock(block, schedule);
            }
            graph.setGuardsStage(GuardsStage.FIXED_DEOPTS);
        }

        assert assertNoGuardsLeft(graph);
    }

    private static boolean assertNoGuardsLeft(StructuredGraph graph) {
        assert graph.getNodes().filter(GuardNode.class).isEmpty();
        return true;
    }

    private static void processBlock(Block block, ScheduleResult schedule) {
        DebugContext debug = block.getBeginNode().getDebug();
        new LowerGuards(debug.isDumpEnabledForMethod() || debug.isLogEnabledForMethod()).processNodes(block, schedule);
    }
}
