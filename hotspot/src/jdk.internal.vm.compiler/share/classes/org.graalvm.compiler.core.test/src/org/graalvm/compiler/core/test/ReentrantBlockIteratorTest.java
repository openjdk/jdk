/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import org.graalvm.compiler.phases.schedule.SchedulePhase;

public class ReentrantBlockIteratorTest extends GraalCompilerTest {

    public static int IntSideEffect;

    public static int oneBlock() {
        return 0;
    }

    public static int fourBlock(int a) {
        if (a > 0) {
            IntSideEffect = a;
        } else {
            IntSideEffect = 0;
        }
        GraalDirectives.controlFlowAnchor();
        return 0;
    }

    public static int loopBlocks(int a) {
        int phi = 0;
        for (int i = 0; i < a; i++) {
            phi += i;
        }
        return phi;
    }

    public static int loopBlocks2(int a) {
        int phi = 0;
        for (int i = 0; i < a; i++) {
            phi += i;
        }
        // first loop exit, second loop will not be visited at all AFTER_FSA
        for (int i = 0; i < a; i++) {
            phi += i;
        }
        return phi;
    }

    // from String.indexof
    @SuppressWarnings("all")
    public static int loopBlocks3(char[] source, int sourceOffset, int sourceCount, char[] target, int targetOffset, int targetCount,
                    int fromIndex) {

        // Checkstyle: stop
        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (targetCount == 0) {
            return fromIndex;
        }

        char first = target[targetOffset];
        int max = sourceOffset + (sourceCount - targetCount);

        for (int i = sourceOffset + fromIndex; i <= max; i++) {
            /* Look for first character. */
            if (source[i] != first) {
                while (++i <= max && source[i] != first) {

                }
            }

            /* Found first character, now look at the rest of v2 */
            if (i <= max) {
                int j = i + 1;
                int end = j + targetCount - 1;
                for (int k = targetOffset + 1; j < end && source[j] == target[k]; j++, k++) {

                }

                if (j == end) {
                    /* Found whole string. */
                    return i - sourceOffset;
                }
            }
        }
        return -1;
        // Checkstyle: resume
    }

    public static int loopBlocks4(int a, int c, int d) {
        int phi = 0;
        l1: for (int i = 0; i < a; i++) {
            l3: for (int k = 0; k < c; k++) {
                for (int l = 0; l < d; l++) {
                    phi += i * k * l;
                    if (phi == 5) {
                        break l3;
                    }
                }
            }
            if (phi > 100) {
                break l1;
            }
        }
        return phi;
    }

    @Test

    public void test01() {
        List<Block> blocks = getVisitedBlocksInOrder("oneBlock");
        assertOrder(blocks, 0);
    }

    @Test
    public void test02() {
        List<Block> blocks = getVisitedBlocksInOrder("fourBlock");
        assertOrder(blocks, 0, 1, 2, 3);
    }

    @Test
    public void test03() {
        List<Block> blocks = getVisitedBlocksInOrder("loopBlocks");
        assertOrder(blocks, 0, 1, 2, 3);
    }

    @Test
    public void test04() {
        List<Block> blocks = getVisitedBlocksInOrder("loopBlocks2");
        assertOrder(blocks, 0, 1, 2, 3, 4, 5, 6);
    }

    @Test
    public void test05() {
        List<Block> blocks = getVisitedBlocksInOrder("loopBlocks3");
        assertVisited(blocks, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32);
    }

    @Test
    public void test06() {
        getVisitedBlocksInOrder("loopBlocks4");
    }

    private static void assertOrder(List<Block> blocks, int... ids) {
        if (blocks.size() != ids.length) {
            Assert.fail("Different length of blocks " + Arrays.toString(blocks.toArray()) + " ids:" + Arrays.toString(ids));
        }
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId() != ids[i]) {
                Assert.fail("Different id for block " + blocks.get(i) + " and associated id " + ids[i]);
            }
        }
    }

    private static void assertVisited(List<Block> blocks, int... ids) {
        if (blocks.size() != ids.length) {
            Assert.fail("Different length of blocks " + Arrays.toString(blocks.toArray()) + " ids:" + Arrays.toString(ids));
        }
        outer: for (int i = 0; i < blocks.size(); i++) {
            for (int j = 0; j < blocks.size(); j++) {
                if (blocks.get(i).getId() == ids[j]) {
                    continue outer;
                }
            }
            Assert.fail("Id for block " + blocks.get(i) + " not found");
        }
    }

    private List<Block> getVisitedBlocksInOrder(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        // after FSA to ensure HIR loop data structure does not contain loop exits
        graph.setGuardsStage(GuardsStage.AFTER_FSA);
        ArrayList<Block> blocks = new ArrayList<>();
        class VoidState {
        }
        final VoidState voidState = new VoidState();
        BlockIteratorClosure<VoidState> closure = new BlockIteratorClosure<VoidState>() {

            @Override
            protected VoidState getInitialState() {
                return voidState;
            }

            @Override
            protected VoidState processBlock(Block block, VoidState currentState) {
                // remember the visit order
                blocks.add(block);
                return currentState;
            }

            @Override
            protected VoidState merge(Block merge, List<VoidState> states) {
                return voidState;
            }

            @Override
            protected VoidState cloneState(VoidState oldState) {
                return voidState;
            }

            @Override
            protected List<VoidState> processLoop(Loop<Block> loop, VoidState initialState) {
                return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
            }
        };
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, false);
        ReentrantBlockIterator.apply(closure, cfg.getStartBlock());
        // schedule for IGV
        new SchedulePhase(graph.getOptions()).apply(graph);
        return blocks;
    }

}
