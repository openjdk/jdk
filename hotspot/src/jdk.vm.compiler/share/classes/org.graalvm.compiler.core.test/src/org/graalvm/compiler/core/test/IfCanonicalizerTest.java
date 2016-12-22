/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.ValueAnchorCleanupPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant
 * 0. Then canonicalization is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class IfCanonicalizerTest extends GraalCompilerTest {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    @SuppressWarnings("all")
    public static int referenceSnippet(int a) {
        return 1;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    @SuppressWarnings("all")
    public static int test1Snippet(int a) {
        if (a == 0) {
            return 1;
        } else {
            return 2;
        }
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    @SuppressWarnings("all")
    public static int test2Snippet(int a) {
        if (a == 0) {
            if (a == 0) {
                if (a == 0) {
                    return 1;
                }
            }
        } else {
            return 2;
        }
        return 3;
    }

    @Test
    public void test3() {
        test("test3Snippet");
    }

    @SuppressWarnings("all")
    public static int test3Snippet(int a) {
        if (a == 0) {
            if (a != 1) {
                if (a == 1) {
                    return 3;
                } else {
                    if (a >= 0) {
                        if (a <= 0) {
                            if (a > -1) {
                                if (a < 1) {
                                    return 1;
                                }
                            }
                        }
                    }
                }
            }
        } else {
            return 2;
        }
        return 3;
    }

    @Test
    public void test4() {
        test("test4Snippet");
    }

    public static int test4Snippet(int a) {
        if (a == 0) {
            return 1;
        }
        return 1;
    }

    @Test
    public void test5() {
        test("test5Snippet");
    }

    public static int test5Snippet(int a) {
        int val = 2;
        if (a == 0) {
            val = 1;
        }
        if (a * (3 + val) == 0) {
            return 1;
        }
        return 1;
    }

    @Test
    public void test6() {
        testCombinedIf("test6Snippet", 3);
        test("test6Snippet", new int[]{0});
    }

    public static int test6Snippet(int[] a) {
        int i = a[0];
        if (i >= 0 && i < a.length) {
            return a[i];
        }
        return 1;
    }

    @Test
    public void test7() {
        testCombinedIf("test7Snippet", 1);
        test("test7Snippet", -1);
    }

    public static int test7Snippet(int v) {
        if (v >= 0 && v < 1024) {
            return v + 1;
        }
        return v - 1;
    }

    @Test
    public void test8() {
        testCombinedIf("test8Snippet", 1);
        test("test8Snippet", -1);
    }

    public static int test8Snippet(int v) {
        if (v >= 0 && v <= 1024) {
            return v + 1;
        }
        return v - 1;
    }

    @Test
    public void test9() {
        testCombinedIf("test9Snippet", 2);
        test("test9Snippet", -1);
        test("test9Snippet", 1025);
    }

    public static int test9Snippet(int n) {
        return (n < 0) ? 1 : (n >= 1024) ? 1024 : n + 1;
    }

    private void testCombinedIf(String snippet, int count) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        PhaseContext context = new PhaseContext(getProviders());
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        new FloatingReadPhase().apply(graph);
        MidTierContext midContext = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, graph.getProfilingInfo());
        new GuardLoweringPhase().apply(graph, midContext);
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, midContext);
        new ValueAnchorCleanupPhase().apply(graph);
        new CanonicalizerPhase().apply(graph, context);
        assertDeepEquals(count, graph.getNodes().filter(IfNode.class).count());
    }

    private void test(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        ParameterNode param = graph.getNodes(ParameterNode.TYPE).iterator().next();
        ConstantNode constant = ConstantNode.forInt(0, graph);
        for (Node n : param.usages().snapshot()) {
            if (!(n instanceof FrameState)) {
                n.replaceFirstInput(param, constant);
            }
        }
        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "Graph");
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        for (FrameState fs : param.usages().filter(FrameState.class).snapshot()) {
            fs.replaceFirstInput(param, null);
            param.safeDelete();
        }
        StructuredGraph referenceGraph = parseEager(REFERENCE_SNIPPET, AllowAssumptions.YES);
        assertEquals(referenceGraph, graph);
    }
}
