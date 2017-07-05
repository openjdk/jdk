/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.util.List;

import org.junit.Test;

import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.DeoptimizingNode.DeoptDuring;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class SchedulingTest2 extends GraphScheduleTest {

    public static int testSnippet() {
        return test() + 2;
    }

    public static int test() {
        return 40;
    }

    @Test
    public void testValueProxyInputs() {
        StructuredGraph graph = parseEager("testSnippet", AllowAssumptions.YES);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        BeginNode beginNode = graph.add(new BeginNode());
        returnNode.replaceAtPredecessor(beginNode);
        beginNode.setNext(returnNode);
        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "Graph");
        SchedulePhase schedulePhase = new SchedulePhase(SchedulingStrategy.EARLIEST);
        schedulePhase.apply(graph);
        ScheduleResult schedule = graph.getLastSchedule();
        BlockMap<List<Node>> blockToNodesMap = schedule.getBlockToNodesMap();
        NodeMap<Block> nodeToBlock = schedule.getNodeToBlockMap();
        assertDeepEquals(2, schedule.getCFG().getBlocks().length);
        for (BinaryArithmeticNode<?> node : graph.getNodes().filter(BinaryArithmeticNode.class)) {
            if (node instanceof AddNode) {
                assertTrue(node.toString() + " expected: " + nodeToBlock.get(beginNode) + " but was: " + nodeToBlock.get(node), nodeToBlock.get(node) != nodeToBlock.get(beginNode));
            }
        }

        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            Block block = nodeToBlock.get(fs);
            assertTrue(fs.toString(), block == schedule.getCFG().getStartBlock());
            for (Node usage : fs.usages()) {
                if (usage instanceof StateSplit && ((StateSplit) usage).stateAfter() == fs) {
                    assertTrue(usage.toString(), nodeToBlock.get(usage) == block);
                    if (usage != block.getBeginNode()) {
                        List<Node> map = blockToNodesMap.get(block);
                        assertTrue(map.indexOf(fs) + " < " + map.indexOf(usage), map.indexOf(fs) < map.indexOf(usage));
                    }
                }
            }
        }

        PhaseContext context = new PhaseContext(getProviders());
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, context);
        MidTierContext midContext = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, graph.getProfilingInfo());

        new GuardLoweringPhase().apply(graph, midContext);
        FrameStateAssignmentPhase phase = new FrameStateAssignmentPhase();
        phase.apply(graph);

        schedulePhase.apply(graph);
        schedule = graph.getLastSchedule();
        blockToNodesMap = schedule.getBlockToNodesMap();
        nodeToBlock = schedule.getNodeToBlockMap();
        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            Block block = nodeToBlock.get(fs);
            assertTrue(fs.toString(), block == schedule.getCFG().getStartBlock());
            for (Node usage : fs.usages()) {
                if ((usage instanceof StateSplit && ((StateSplit) usage).stateAfter() == fs) || (usage instanceof DeoptDuring && ((DeoptDuring) usage).stateDuring() == fs)) {
                    assertTrue(usage.toString(), nodeToBlock.get(usage) == block);
                    if (usage != block.getBeginNode()) {
                        List<Node> map = blockToNodesMap.get(block);
                        assertTrue(map.indexOf(fs) + " < " + map.indexOf(usage), map.indexOf(fs) < map.indexOf(usage));
                    }
                }
            }
        }
    }
}
