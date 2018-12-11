/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * Check that multiple bounds checks are correctly grouped together.
 */
public class ConditionalEliminationTest14 extends ConditionalEliminationTestBase {

    public static void test1Snippet(Object[] args) {
        Object a5 = args[5];
        Object a7 = args[7];
        Object a6 = args[6];

        /*
         * The order of the conditions matters: The scheduler processes the floating reads for the
         * array loads in the order of the conditions here, and we want the index 7 access to be
         * processed before the index 6 access.
         */
        if (a5 != null && a7 != null && a6 != null) {
            sink1 = 1;
        }
        sink0 = 0;
    }

    @Test
    public void test1() {
        StructuredGraph graph = parseEager("test1Snippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        PhaseContext context = new PhaseContext(getProviders());

        /* Convert the LoadIndexNode to ReadNode with floating guards. */
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        /* Convert the ReadNode to FloatingReadNode. */
        new FloatingReadPhase().apply(graph);
        /* Apply the phase that we want to test. */
        new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);

        Assert.assertEquals("All guards must be floating", 0, graph.getNodes(FixedGuardNode.TYPE).count());
        Assert.assertEquals("All array accesses must have been lowered", 0, graph.getNodes().filter(LoadIndexedNode.class).count());
        Assert.assertEquals("All reads must be floating", 0, graph.getNodes().filter(ReadNode.class).count());
        Assert.assertEquals("Must have floating reads (3 array accesses, 1 array length)", 4, graph.getNodes().filter(FloatingReadNode.class).count());

        NodeIterable<GuardNode> boundsChecks = graph.getNodes(GuardNode.TYPE).filter(n -> ((GuardNode) n).getReason() == DeoptimizationReason.BoundsCheckException);
        Assert.assertEquals("Must have only 1 bounds check remaining", 1, boundsChecks.count());
        LogicNode condition = boundsChecks.first().getCondition();
        Assert.assertTrue("Bounds check must check for array length 8", condition instanceof IntegerBelowNode && ((IntegerBelowNode) condition).getY().valueEquals(ConstantNode.forInt(8)));
    }
}
