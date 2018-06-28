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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * This test checks the combined action of
 * {@link org.graalvm.compiler.phases.common.ConditionalEliminationPhase} and
 * {@link org.graalvm.compiler.phases.common.LoweringPhase}. The lowering phase needs to introduce
 * the null checks at the correct places for the dominator conditional elimination phase to pick
 * them up.
 */
public class ConditionalEliminationTest10 extends ConditionalEliminationTestBase {

    private static boolean condition1;
    private static boolean condition2;

    private static class TestClass {
        int x;
    }

    @SuppressWarnings("all")
    public static int testSnippet1(TestClass t) {
        int result = 0;
        if (condition1) {
            GraalDirectives.controlFlowAnchor();
            result = t.x;
        }
        GraalDirectives.controlFlowAnchor();
        return result + t.x;
    }

    @Test
    public void test1() {
        test("testSnippet1", 1);
    }

    @SuppressWarnings("all")
    public static int testSnippet2(TestClass t) {
        int result = 0;
        if (condition1) {
            GraalDirectives.controlFlowAnchor();
            result = t.x;
        } else {
            GraalDirectives.controlFlowAnchor();
            result = t.x;
        }

        if (condition2) {
            result = t.x;
            GraalDirectives.controlFlowAnchor();
        }

        return result;
    }

    @Test
    public void test2() {
        test("testSnippet2", 1);
    }

    private void test(String snippet, int guardCount) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        PhaseContext context = new PhaseContext(getProviders());
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        new ConditionalEliminationPhase(true).apply(graph, context);
        Assert.assertEquals(guardCount, graph.getNodes().filter(GuardNode.class).count());
    }
}
