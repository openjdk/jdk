/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.OptImplicitNullChecks;
import static org.graalvm.compiler.core.common.GraalOptions.OptScheduleOutOfLoops;
import static org.graalvm.compiler.graph.test.matchers.NodeIterableCount.hasCount;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * In these test the FrameStates are explicitly cleared out, so that the scheduling of
 * FloatingReadNodes depends solely on the scheduling algorithm. The FrameStates normally keep the
 * FloatingReadNodes above a certain point, so that they (most of the time...) magically do the
 * right thing.
 *
 * The scheduling shouldn't depend on FrameStates, which is tested by this class.
 */
public class MemoryScheduleTest extends GraphScheduleTest {

    private enum TestMode {
        WITH_FRAMESTATES,
        WITHOUT_FRAMESTATES,
        INLINED_WITHOUT_FRAMESTATES
    }

    public static class Container {

        public int a;
        public int b;
        public int c;

        public Object obj;
    }

    private static final Container container = new Container();
    private static final List<Container> containerList = new ArrayList<>();
    private static final double LOOP_ENTRY_PROBABILITY = 0.9;

    /**
     * In this test the read should be scheduled before the write.
     */
    public static int testSimpleSnippet() {
        try {
            return container.a;
        } finally {
            container.a = 15;
        }
    }

    @Test
    public void testSimple() {
        for (TestMode mode : TestMode.values()) {
            ScheduleResult schedule = getFinalSchedule("testSimpleSnippet", mode);
            StructuredGraph graph = schedule.getCFG().graph;
            assertReadAndWriteInSameBlock(schedule, true);
            assertOrderedAfterSchedule(schedule, graph.getNodes().filter(FloatingReadNode.class).first(), graph.getNodes().filter(WriteNode.class).first());
        }
    }

    /**
     * In this case the read should be scheduled in the first block.
     */
    public static int testSplit1Snippet(int a) {
        try {
            return container.a;
        } finally {
            if (a < 0) {
                container.a = 15;
            } else {
                container.b = 15;
            }
        }
    }

    @Test
    public void testSplit1() {
        for (TestMode mode : TestMode.values()) {
            ScheduleResult schedule = getFinalSchedule("testSplit1Snippet", mode);
            assertReadWithinStartBlock(schedule, true);
            assertReadWithinAllReturnBlocks(schedule, false);
        }
    }

    /**
     * Here the read should float to the end.
     */
    public static int testSplit2Snippet(int a) {
        try {
            return container.a;
        } finally {
            if (a < 0) {
                container.c = 15;
            } else {
                container.b = 15;
            }
            container.obj = null;
        }
    }

    @Test
    public void testSplit2() {
        ScheduleResult schedule = getFinalSchedule("testSplit2Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, true);
    }

    /**
     * Here the read should not float to the end.
     */
    public static int testLoop1Snippet(int a, int b) {
        try {
            return container.a;
        } finally {
            for (int i = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < a); i++) {
                if (b < 0) {
                    container.b = 10;
                } else {
                    container.a = 15;
                }
            }
        }
    }

    @Test
    public void testLoop1() {
        ScheduleResult schedule = getFinalSchedule("testLoop1Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(6, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, true);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    /**
     * Here the read should float to the end.
     */
    public static int testLoop2Snippet(int a, int b) {
        try {
            return container.a;
        } finally {
            for (int i = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < a); i++) {
                if (b < 0) {
                    container.b = 10;
                } else {
                    container.c = 15;
                }
            }
        }
    }

    @Test
    public void testLoop2() {
        ScheduleResult schedule = getFinalSchedule("testLoop2Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(6, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, true);
    }

    /**
     * Here the read should float out of the loop.
     */
    public static int testLoop3Snippet(int a) {
        int j = 0;
        for (int i = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < a); i++) {
            if (i - container.a == 0) {
                break;
            }
            j++;
        }
        return j;
    }

    @Test
    public void testLoop3() {
        ScheduleResult schedule = getFinalSchedule("testLoop3Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(6, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, true);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    public String testStringReplaceSnippet(String input) {
        return input.replace('a', 'b');
    }

    @Test
    public void testStringReplace() {
        getFinalSchedule("testStringReplaceSnippet", TestMode.INLINED_WITHOUT_FRAMESTATES);
        test("testStringReplaceSnippet", "acbaaa");
    }

    /**
     * Here the read should float out of the loop.
     */
    public static int testLoop5Snippet(int a, int b, MemoryScheduleTest obj) {
        int ret = 0;
        int bb = b;
        for (int i = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < a); i++) {
            ret = obj.hash;
            if (a > 10) {
                bb++;
            } else {
                bb--;
            }
            ret = ret / 10;
        }
        return ret + bb;
    }

    @Test
    public void testLoop5() {
        ScheduleResult schedule = getFinalSchedule("testLoop5Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(10, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    /**
     * Here the read should not float out of the loop.
     */
    public static int testLoop6Snippet(int a, int b, MemoryScheduleTest obj) {
        int ret = 0;
        int bb = b;
        for (int i = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < a); i++) {
            ret = obj.hash;
            if (a > 10) {
                bb++;
            } else {
                bb--;
                for (int j = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, j < b); ++j) {
                    obj.hash = 3;
                }
            }
            ret = ret / 10;
        }
        return ret + bb;
    }

    @Test
    public void testLoop6() {
        ScheduleResult schedule = getFinalSchedule("testLoop6Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(13, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    /**
     * Here the read should not float out of the loop.
     */
    public static int testLoop7Snippet(int a, int b, MemoryScheduleTest obj) {
        int ret = 0;
        int bb = b;
        for (int i = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < a); i++) {
            ret = obj.hash;
            if (a > 10) {
                bb++;
            } else {
                bb--;
                for (int k = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, k < a); ++k) {
                    if (k % 2 == 1) {
                        for (int j = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, j < b); ++j) {
                            obj.hash = 3;
                        }
                    }
                }
            }
            ret = ret / 10;
        }
        return ret + bb;
    }

    @Test
    public void testLoop7() {
        ScheduleResult schedule = getFinalSchedule("testLoop7Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(18, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    /**
     * Here the read should not float to the end.
     */
    public static int testLoop8Snippet(int a, int b) {
        int result = container.a;
        for (int i = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < a); i++) {
            if (b < 0) {
                container.b = 10;
                break;
            } else {
                for (int j = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, j < b); j++) {
                    container.a = 0;
                }
            }
        }
        GraalDirectives.controlFlowAnchor();
        return result;
    }

    @Test
    public void testLoop8() {
        ScheduleResult schedule = getFinalSchedule("testLoop8Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(10, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, true);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    /**
     * Here the read should float after the loop.
     */
    public static int testLoop9Snippet(int a, int b) {
        container.a = b;
        for (int i = 0; i < a; i++) {
            container.a = i;
        }
        GraalDirectives.controlFlowAnchor();
        return container.a;
    }

    @Test
    public void testLoop9() {
        ScheduleResult schedule = getFinalSchedule("testLoop9Snippet", TestMode.WITHOUT_FRAMESTATES);
        StructuredGraph graph = schedule.getCFG().getStartBlock().getBeginNode().graph();
        assertThat(graph.getNodes(ReturnNode.TYPE), hasCount(1));
        ReturnNode ret = graph.getNodes(ReturnNode.TYPE).first();
        assertThat(ret.result(), instanceOf(FloatingReadNode.class));
        Block readBlock = schedule.getNodeToBlockMap().get(ret.result());
        Assert.assertEquals(0, readBlock.getLoopDepth());
    }

    /**
     * Here the read should not float to the end.
     */
    public static int testIfRead1Snippet(int a) {
        int res = container.a;
        if (a < 0) {
            container.a = 10;
        }
        return res;
    }

    @Test
    public void testIfRead1() {
        ScheduleResult schedule = getFinalSchedule("testIfRead1Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(3, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, true);
        assertReadAndWriteInSameBlock(schedule, false);
    }

    /**
     * Here the read should float in the else block.
     */
    public static int testIfRead2Snippet(int a) {
        int res = 0;
        if (a < 0) {
            container.a = 10;
        } else {
            res = container.a;
        }
        return res;
    }

    @Test
    public void testIfRead2() {
        ScheduleResult schedule = getFinalSchedule("testIfRead2Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(3, schedule.getCFG().getBlocks().length);
        assertDeepEquals(1, schedule.getCFG().graph.getNodes().filter(FloatingReadNode.class).count());
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
        assertReadAndWriteInSameBlock(schedule, false);
    }

    /**
     * Here the read should float to the end, right before the write.
     */
    public static int testIfRead3Snippet(int a) {
        if (a < 0) {
            container.a = 10;
        }
        int res = container.a;
        container.a = 20;
        return res;
    }

    @Test
    public void testIfRead3() {
        ScheduleResult schedule = getFinalSchedule("testIfRead3Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(4, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, true);
    }

    /**
     * Here the read should be just in the if branch (with the write).
     */
    public static int testIfRead4Snippet(int a) {
        if (a > 0) {
            int res = container.a;
            container.a = 0x20;
            return res;
        } else {
            return 0x10;
        }
    }

    @Test
    public void testIfRead4() {
        ScheduleResult schedule = getFinalSchedule("testIfRead4Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(3, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
        assertReadAndWriteInSameBlock(schedule, true);
    }

    /**
     * Here the read should float to the end.
     */
    public static int testIfRead5Snippet(int a) {
        if (a < 0) {
            container.a = 10;
        }
        return container.a;
    }

    @Test
    public void testIfRead5() {
        ScheduleResult schedule = getFinalSchedule("testIfRead5Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(4, schedule.getCFG().getBlocks().length);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, true);
        assertReadAndWriteInSameBlock(schedule, false);
    }

    public static int testAntiDependencySnippet(int a) {
        /*
         * This read must not be scheduled after the following write.
         */
        int res = container.a;
        container.a = 10;

        /*
         * Add some more basic blocks.
         */
        if (a < 0) {
            container.b = 20;
        }
        container.c = 30;
        return res;
    }

    @Test
    public void testAntiDependency() {
        ScheduleResult schedule = getFinalSchedule("testAntiDependencySnippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(4, schedule.getCFG().getBlocks().length);
        assertReadBeforeAllWritesInStartBlock(schedule);
    }

    /**
     * testing scheduling within a block.
     */
    public static int testBlockScheduleSnippet() {
        int res = 0;
        container.a = 0x00;
        container.a = 0x10;
        container.a = 0x20;
        container.a = 0x30;
        container.a = 0x40;
        res = container.a;
        container.a = 0x50;
        container.a = 0x60;
        container.a = 0x70;
        return res;
    }

    @Test
    public void testBlockSchedule() {
        ScheduleResult schedule = getFinalSchedule("testBlockScheduleSnippet", TestMode.WITHOUT_FRAMESTATES);
        StructuredGraph graph = schedule.getCFG().graph;
        NodeIterable<WriteNode> writeNodes = graph.getNodes().filter(WriteNode.class);

        assertDeepEquals(1, schedule.getCFG().getBlocks().length);
        assertDeepEquals(8, writeNodes.count());
        assertDeepEquals(1, graph.getNodes().filter(FloatingReadNode.class).count());

        FloatingReadNode read = graph.getNodes().filter(FloatingReadNode.class).first();

        WriteNode[] writes = new WriteNode[8];
        int i = 0;
        for (WriteNode n : writeNodes) {
            writes[i] = n;
            i++;
        }
        assertOrderedAfterSchedule(schedule, writes[4], read);
        assertOrderedAfterSchedule(schedule, read, writes[5]);
        for (int j = 0; j < 7; j++) {
            assertOrderedAfterSchedule(schedule, writes[j], writes[j + 1]);
        }
    }

    /**
     * read should move inside the loop (out of loop is disabled).
     */
    public static int testBlockSchedule2Snippet(int value) {
        int res = 0;

        container.a = value;
        for (int i = 0; i < 100; i++) {
            if (i == 10) {
                return container.a;
            }
            res += i;
        }
        return res;
    }

    @Test
    public void testBlockSchedule2() {
        ScheduleResult schedule = getFinalSchedule("testBlockSchedule2Snippet", TestMode.WITHOUT_FRAMESTATES, SchedulingStrategy.LATEST);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
        assertReadAndWriteInSameBlock(schedule, false);
    }

    public static void testProxySnippet() {
        while (container.a < container.b) {
            List<Container> list = new ArrayList<>(containerList);
            while (container.c < list.size()) {
                if (container.obj != null) {
                    return;
                }
                container.c++;
            }
            container.a = 0;
            container.b--;
        }
        container.b++;
    }

    @Test
    public void testProxy() {
        ScheduleResult schedule = getFinalSchedule("testProxySnippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    private int hash = 0;
    private final char[] value = new char[3];

    public int testStringHashCodeSnippet() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            char[] val = value;

            for (int i = 0; i < value.length; i++) {
                h = 31 * h + val[i];
            }
            hash = h;
        }
        return h;
    }

    @Test
    public void testStringHashCode() {
        ScheduleResult schedule = getFinalSchedule("testStringHashCodeSnippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, true);
        assertReadWithinAllReturnBlocks(schedule, false);

        hash = 0x1337;
        value[0] = 'a';
        value[1] = 'b';
        value[2] = 'c';
        test("testStringHashCodeSnippet");
    }

    public static int testLoop4Snippet(int count) {
        int[] a = new int[count];

        for (int i = 0; GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < a.length); i++) {
            a[i] = i;
        }

        int i = 0;
        int iwrap = count - 1;
        int sum = 0;

        while (GraalDirectives.injectBranchProbability(LOOP_ENTRY_PROBABILITY, i < count)) {
            sum += (a[i] + a[iwrap]) / 2;
            iwrap = i;
            i++;
        }
        return sum;
    }

    @Test
    public void testLoop4() {
        ScheduleResult schedule = getFinalSchedule("testLoop4Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    private void assertReadWithinAllReturnBlocks(ScheduleResult schedule, boolean withinReturnBlock) {
        StructuredGraph graph = schedule.getCFG().graph;
        assertTrue(graph.getNodes(ReturnNode.TYPE).isNotEmpty());

        int withRead = 0;
        int returnBlocks = 0;
        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            Block block = schedule.getCFG().getNodeToBlock().get(returnNode);
            for (Node node : schedule.getBlockToNodesMap().get(block)) {
                if (node instanceof FloatingReadNode) {
                    withRead++;
                    break;
                }
            }
            returnBlocks++;
        }
        assertDeepEquals(withRead == returnBlocks, withinReturnBlock);
    }

    private void assertReadWithinStartBlock(ScheduleResult schedule, boolean withinStartBlock) {
        boolean readEncountered = false;
        for (Node node : schedule.getBlockToNodesMap().get(schedule.getCFG().getStartBlock())) {
            if (node instanceof FloatingReadNode) {
                readEncountered = true;
            }
        }
        assertDeepEquals(withinStartBlock, readEncountered);
    }

    private static void assertReadAndWriteInSameBlock(ScheduleResult schedule, boolean inSame) {
        StructuredGraph graph = schedule.getCFG().graph;
        FloatingReadNode read = graph.getNodes().filter(FloatingReadNode.class).first();
        WriteNode write = graph.getNodes().filter(WriteNode.class).first();
        assertTrue(!(inSame ^ schedule.getCFG().blockFor(read) == schedule.getCFG().blockFor(write)));
    }

    private static void assertReadBeforeAllWritesInStartBlock(ScheduleResult schedule) {
        boolean writeNodeFound = false;
        boolean readNodeFound = false;
        for (Node node : schedule.nodesFor(schedule.getCFG().getStartBlock())) {
            if (node instanceof FloatingReadNode) {
                assertTrue(!writeNodeFound);
                readNodeFound = true;
            } else if (node instanceof WriteNode) {
                writeNodeFound = true;
            }
        }
        assertTrue(readNodeFound);
    }

    private ScheduleResult getFinalSchedule(final String snippet, final TestMode mode) {
        return getFinalSchedule(snippet, mode, SchedulingStrategy.LATEST_OUT_OF_LOOPS);
    }

    @SuppressWarnings("try")
    private ScheduleResult getFinalSchedule(final String snippet, final TestMode mode, final SchedulingStrategy schedulingStrategy) {
        OptionValues options = new OptionValues(getInitialOptions(), OptScheduleOutOfLoops, schedulingStrategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS, OptImplicitNullChecks, false);
        final StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO, options);
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope d = debug.scope("FloatingReadTest", graph)) {
            HighTierContext context = getDefaultHighTierContext();
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
            canonicalizer.apply(graph, context);
            if (mode == TestMode.INLINED_WITHOUT_FRAMESTATES) {
                createInliningPhase(canonicalizer).apply(graph, context);
            }
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            if (mode == TestMode.WITHOUT_FRAMESTATES || mode == TestMode.INLINED_WITHOUT_FRAMESTATES) {
                graph.clearAllStateAfter();
            }
            debug.dump(DebugContext.BASIC_LEVEL, graph, "after removal of framestates");

            new FloatingReadPhase().apply(graph);
            new RemoveValueProxyPhase().apply(graph);

            MidTierContext midContext = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, graph.getProfilingInfo());
            new GuardLoweringPhase().apply(graph, midContext);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, midContext);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER).apply(graph, midContext);

            SchedulePhase schedule = new SchedulePhase(schedulingStrategy);
            schedule.apply(graph);
            assertDeepEquals(1, graph.getNodes().filter(StartNode.class).count());
            return graph.getLastSchedule();
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
