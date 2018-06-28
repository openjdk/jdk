/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;
import org.junit.Assert;
import org.junit.Test;

/**
 * Collection of tests for {@link org.graalvm.compiler.phases.common.ConditionalEliminationPhase}
 * including those that triggered bugs in this phase.
 */
public class ConditionalEliminationTest15 extends ConditionalEliminationTestBase {

    private void checkNodeCount(String methodName, Class<? extends Node> nodeClass, int count) {
        StructuredGraph graph = parseEager(methodName, AllowAssumptions.YES);

        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        PhaseContext context = new PhaseContext(getProviders());

        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);

        // Merge arr.length reads.
        new EarlyReadEliminationPhase(canonicalizer).apply(graph, context);
        new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);

        getDebugContext().dump(DebugContext.BASIC_LEVEL, graph, "After ConditionalEliminationPhase");

        Assert.assertEquals(count, graph.getNodes().filter(nodeClass).count());
    }

    public static int testRedundantIntegerLessThanNode(int index, int[] arr) {
        while (arr[index] != 42) {
            if (index >= 0) { // redundant
                return 1;
            }
        }
        return 2;
    }

    public static int testRedundantIntegerLessThanNode2(int index, int[] arr) {
        while (arr[index] != 42) {
            if (index < arr.length) { // redundant
                return 1;
            }
        }
        return 2;
    }

    @Test
    public void testRedundantSignedLessThanNode() {
        checkNodeCount("testRedundantIntegerLessThanNode", IntegerLessThanNode.class, 0);
        checkNodeCount("testRedundantIntegerLessThanNode2", IntegerLessThanNode.class, 0);
    }
}
