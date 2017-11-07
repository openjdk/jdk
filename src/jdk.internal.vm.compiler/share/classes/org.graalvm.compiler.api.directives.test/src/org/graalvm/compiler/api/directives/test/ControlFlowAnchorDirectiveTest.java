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
package org.graalvm.compiler.api.directives.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.OptimisticOptimizations.Optimization;
import org.graalvm.compiler.phases.tiers.HighTierContext;

public class ControlFlowAnchorDirectiveTest extends GraalCompilerTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(AnchorSnippet.class)
    private @interface NodeCount {

        Class<? extends Node> nodeClass();

        int expectedCount();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface AnchorSnippet {
        NodeCount[] value();
    }

    @NodeCount(nodeClass = ReturnNode.class, expectedCount = 1)
    public static int verifyMergeSnippet(int arg) {
        if (arg > 5) {
            return 1;
        } else {
            return 2;
        }
    }

    @NodeCount(nodeClass = ControlFlowAnchorNode.class, expectedCount = 2)
    @NodeCount(nodeClass = ReturnNode.class, expectedCount = 2)
    public static int preventMergeSnippet(int arg) {
        if (arg > 5) {
            GraalDirectives.controlFlowAnchor();
            return 1;
        } else {
            GraalDirectives.controlFlowAnchor();
            return 2;
        }
    }

    @Test
    public void testMerge() {
        test("verifyMergeSnippet", 42);
        test("preventMergeSnippet", 42);
    }

    @NodeCount(nodeClass = ReturnNode.class, expectedCount = 2)
    public static int verifyDuplicateSnippet(int arg) {
        int ret;
        if (arg > 5) {
            ret = 17;
        } else {
            ret = arg;
        }
        return 42 / ret;
    }

    @NodeCount(nodeClass = ControlFlowAnchorNode.class, expectedCount = 1)
    @NodeCount(nodeClass = ReturnNode.class, expectedCount = 1)
    public static int preventDuplicateSnippet(int arg) {
        int ret;
        if (arg > 5) {
            ret = 17;
        } else {
            ret = arg;
        }
        GraalDirectives.controlFlowAnchor();
        return 42 / ret;
    }

    @Test
    public void testDuplicate() {
        // test("verifyDuplicateSnippet", 42);
        test("preventDuplicateSnippet", 42);
    }

    @NodeCount(nodeClass = LoopBeginNode.class, expectedCount = 0)
    public static int verifyFullUnrollSnippet(int arg) {
        int ret = arg;
        for (int i = 0; i < 5; i++) {
            ret = ret * 3 + 1;
        }
        return ret;
    }

    @NodeCount(nodeClass = LoopBeginNode.class, expectedCount = 1)
    @NodeCount(nodeClass = ControlFlowAnchorNode.class, expectedCount = 1)
    public static int preventFullUnrollSnippet(int arg) {
        int ret = arg;
        for (int i = 0; i < 5; i++) {
            GraalDirectives.controlFlowAnchor();
            ret = ret * 3 + 1;
        }
        return ret;
    }

    @Test
    public void testFullUnroll() {
        test("verifyFullUnrollSnippet", 42);
        test("preventFullUnrollSnippet", 42);
    }

    @NodeCount(nodeClass = LoopBeginNode.class, expectedCount = 1)
    @NodeCount(nodeClass = IfNode.class, expectedCount = 4)
    public static void verifyPeelSnippet(int arg) {
        int ret = arg;
        while (ret > 1) {
            if (ret % 2 == 0) {
                ret /= 2;
            } else {
                ret = 3 * ret + 1;
            }
        }
    }

    @NodeCount(nodeClass = LoopBeginNode.class, expectedCount = 1)
    @NodeCount(nodeClass = IfNode.class, expectedCount = 2)
    public static void preventPeelSnippet(int arg) {
        int ret = arg;
        while (ret > 1) {
            GraalDirectives.controlFlowAnchor();
            if (ret % 2 == 0) {
                GraalDirectives.controlFlowAnchor();
                ret /= 2;
            } else {
                ret = 3 * ret + 1;
            }
        }
    }

    @Test
    public void testPeel() {
        test("preventPeelSnippet", 42);
    }

    @NodeCount(nodeClass = LoopBeginNode.class, expectedCount = 2)
    public static void verifyUnswitchSnippet(int arg, boolean flag) {
        int ret = arg;
        while (GraalDirectives.injectBranchProbability(0.9999, ret < 1000)) {
            if (flag) {
                ret = ret * 2 + 1;
            } else {
                ret = ret * 3 + 1;
            }
        }
    }

    @NodeCount(nodeClass = LoopBeginNode.class, expectedCount = 1)
    @NodeCount(nodeClass = IfNode.class, expectedCount = 2)
    public static void preventUnswitchSnippet(int arg, boolean flag) {
        int ret = arg;
        while (GraalDirectives.injectBranchProbability(0.9999, ret < 1000)) {
            if (flag) {
                GraalDirectives.controlFlowAnchor();
                ret++;
            } else {
                ret += 2;
            }
        }
    }

    @Test
    public void testUnswitch() {
        test("verifyUnswitchSnippet", 0, false);
        test("preventUnswitchSnippet", 0, false);
    }

    /**
     * Cloning a ControlFlowAnchorNode is not allowed but cloning a whole graph containing one is
     * ok.
     */
    @Test
    public void testClone() {
        StructuredGraph g = parseEager("preventPeelSnippet", AllowAssumptions.NO);
        g.copy(g.getDebug());
    }

    private static List<NodeCount> getNodeCountAnnotations(StructuredGraph graph) {
        ResolvedJavaMethod method = graph.method();
        AnchorSnippet snippet = method.getAnnotation(AnchorSnippet.class);
        if (snippet != null) {
            return Arrays.asList(snippet.value());
        }

        NodeCount single = method.getAnnotation(NodeCount.class);
        if (single != null) {
            return Collections.singletonList(single);
        }

        return Collections.emptyList();
    }

    @Override
    protected HighTierContext getDefaultHighTierContext() {
        return new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL.remove(Optimization.RemoveNeverExecutedCode));
    }

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        List<ControlFlowAnchorNode> anchors = graph.getNodes().filter(ControlFlowAnchorNode.class).snapshot();
        for (int i = 0; i < anchors.size(); i++) {
            ControlFlowAnchorNode a = anchors.get(i);
            for (int j = i + 1; j < anchors.size(); j++) {
                ControlFlowAnchorNode b = anchors.get(j);
                if (a.valueEquals(b)) {
                    Assert.fail("found duplicated control flow anchors (" + a + " and " + b + ")");
                }
            }
        }

        for (NodeCount nodeCount : getNodeCountAnnotations(graph)) {
            NodeIterable<? extends Node> nodes = graph.getNodes().filter(nodeCount.nodeClass());
            Assert.assertEquals(nodeCount.nodeClass().getSimpleName(), nodeCount.expectedCount(), nodes.count());
        }
        return true;
    }
}
