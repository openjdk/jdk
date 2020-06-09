/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.junit.Assert;
import org.junit.Test;

public class SimpleCFGTest extends GraalCompilerTest {

    private static void dumpGraph(final StructuredGraph graph) {
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
    }

    @Test
    public void testImplies() {
        OptionValues options = getInitialOptions();
        DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(getSnippetReflection())).build();
        StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).build();

        EndNode trueEnd = graph.add(new EndNode());
        EndNode falseEnd = graph.add(new EndNode());

        AbstractBeginNode trueBegin = graph.add(new BeginNode());
        trueBegin.setNext(trueEnd);
        AbstractBeginNode falseBegin = graph.add(new BeginNode());
        falseBegin.setNext(falseEnd);

        IfNode ifNode = graph.add(new IfNode(null, trueBegin, falseBegin, 0.5));
        graph.start().setNext(ifNode);

        AbstractMergeNode merge = graph.add(new MergeNode());
        merge.addForwardEnd(trueEnd);
        merge.addForwardEnd(falseEnd);
        ReturnNode returnNode = graph.add(new ReturnNode(null));
        merge.setNext(returnNode);

        dumpGraph(graph);

        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, true);

        Block[] blocks = cfg.getBlocks();
        // check number of blocks
        assertDeepEquals(4, blocks.length);

        // check block - node assignment
        assertDeepEquals(blocks[0], cfg.blockFor(graph.start()));
        assertDeepEquals(blocks[0], cfg.blockFor(ifNode));
        assertDeepEquals(blocks[1], cfg.blockFor(trueBegin));
        assertDeepEquals(blocks[1], cfg.blockFor(trueEnd));
        assertDeepEquals(blocks[2], cfg.blockFor(falseBegin));
        assertDeepEquals(blocks[2], cfg.blockFor(falseEnd));
        assertDeepEquals(blocks[3], cfg.blockFor(merge));
        assertDeepEquals(blocks[3], cfg.blockFor(returnNode));

        // check dominators
        assertDominator(blocks[0], null);
        assertDominator(blocks[1], blocks[0]);
        assertDominator(blocks[2], blocks[0]);
        assertDominator(blocks[3], blocks[0]);

        // check dominated
        assertDominatedSize(blocks[0], 3);
        assertDominatedSize(blocks[1], 0);
        assertDominatedSize(blocks[2], 0);
        assertDominatedSize(blocks[3], 0);

        // check postdominators
        assertPostdominator(blocks[0], blocks[3]);
        assertPostdominator(blocks[1], blocks[3]);
        assertPostdominator(blocks[2], blocks[3]);
        assertPostdominator(blocks[3], null);
    }

    public static void assertDominator(Block block, Block expectedDominator) {
        Assert.assertEquals("dominator of " + block, expectedDominator, block.getDominator());
    }

    public static void assertDominatedSize(Block block, int size) {
        int count = 0;
        Block domChild = block.getFirstDominated();
        while (domChild != null) {
            count++;
            domChild = domChild.getDominatedSibling();
        }
        Assert.assertEquals("number of dominated blocks of " + block, size, count);
    }

    public static void assertPostdominator(Block block, Block expectedPostdominator) {
        Assert.assertEquals("postdominator of " + block, expectedPostdominator, block.getPostdominator());
    }

}
