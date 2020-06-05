/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotAOTClassInitializationPlugin;
import org.graalvm.compiler.hotspot.nodes.aot.InitializeKlassNode;
import org.graalvm.compiler.hotspot.nodes.aot.LoadConstantIndirectlyNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveConstantNode;
import org.graalvm.compiler.hotspot.phases.LoadJavaMirrorWithKlassPhase;
import org.graalvm.compiler.hotspot.phases.aot.EliminateRedundantInitializationPhase;
import org.graalvm.compiler.hotspot.phases.aot.ReplaceConstantNodesPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Test;

public class ReplaceConstantNodesPhaseTest extends HotSpotGraalCompilerTest {
    private final GraalHotSpotVMConfig config = runtime().getVMConfig();

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        plugins.setClassInitializationPlugin(new HotSpotAOTClassInitializationPlugin());
        return plugins;
    }

    public static class X {
        public static int x;
        public static int y;
        public static int z;
        public static Object o;
    }

    public static class Y extends X {
        public static int a;
        public static int b;
    }

    public static int a;

    public static void assignFields() {
        X.x = 1;
        X.y = 2;
        X.z = 3;
    }

    public static void assignFieldsInBranches(boolean x) {
        if (x) {
            X.y = 1;
        } else {
            X.z = 2;
        }
    }

    public static void assignFieldsWithDominatingInit(boolean x) {
        X.x = 1;
        if (x) {
            X.y = 2;
        } else {
            X.z = 3;
        }
    }

    public static void assignString() {
        X.o = "foo";
    }

    public static void assignToParentAndChild() {
        Y.a = 1;
        X.x = 2;
    }

    public static void assignToThis() {
        a = 1;
    }

    public static void assignFieldsWithDominatingInitOfParent(boolean x) {
        Y.a = 1;
        if (x) {
            X.y = 2;
        } else {
            X.z = 3;
        }
        Y.b = 4;
    }

    private void test(String name, int expectedInits, int expectedResolves, int expectedLoads) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.NO, new OptionValues(getInitialOptions(), GraalOptions.GeneratePIC, true));
        HighTierContext highTierContext = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        new EliminateRedundantInitializationPhase().apply(graph, highTierContext);
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, highTierContext);
        new LoadJavaMirrorWithKlassPhase(config).apply(graph, highTierContext);
        new ReplaceConstantNodesPhase(true, false).apply(graph, highTierContext);
        Assert.assertEquals(expectedInits, graph.getNodes().filter(InitializeKlassNode.class).count());
        Assert.assertEquals(expectedResolves, graph.getNodes().filter(ResolveConstantNode.class).count());
        Assert.assertEquals(expectedLoads, graph.getNodes().filter(LoadConstantIndirectlyNode.class).count());
    }

    @Test
    public void test1() {
        test("assignFields", 1, 0, 0);
    }

    @Test
    public void test2() {
        test("assignFieldsWithDominatingInit", 1, 0, 0);
    }

    @Test
    public void test3() {
        test("assignString", 1, 1, 0);
    }

    @Test
    public void test4() {
        test("assignToParentAndChild", 1, 1, 0);
    }

    @Test
    public void test5() {
        test("assignToThis", 0, 0, 1);
    }

    @Test
    public void test6() {
        test("assignFieldsWithDominatingInitOfParent", 1, 1, 0);
    }

    @Test
    public void test7() {
        test("assignFieldsInBranches", 2, 1, 0);
    }
}
