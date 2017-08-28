/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.meta.HotSpotClassInitializationPlugin;
import org.graalvm.compiler.hotspot.nodes.aot.InitializeKlassNode;
import org.graalvm.compiler.hotspot.phases.aot.EliminateRedundantInitializationPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Test;

public class EliminateRedundantInitializationPhaseTest extends GraalCompilerTest {
    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        plugins.setClassInitializationPlugin(new HotSpotClassInitializationPlugin());
        return plugins;
    }

    public static class X {
        public static int x;
        public static int y;
        public static int z;
    }

    public static class Y extends X {
        public static int a;
        public static int b;
    }

    public static void assignFields() {
        X.x = 1;
        X.y = 2;
        X.z = 3;
    }

    public static void assignFieldsConditionally(boolean choice) {
        X.x = 1;
        if (choice) {
            X.y = 2;
        } else {
            X.z = 3;
        }
    }

    public static void assignFieldsSubclassDominates() {
        Y.a = 1;
        X.x = 2;
        X.y = 3;
        X.z = 4;
    }

    public static void assignFieldsConditionallySubclassDominates(boolean choice) {
        Y.a = 1;
        if (choice) {
            X.x = 2;
        } else {
            X.y = 3;
        }
        Y.z = 4;
    }

    public static void assignFieldsSubclassPostdominates() {
        X.x = 1;
        Y.a = 2;
    }

    public static void assignFieldsConditionallySubclassPostdominates(boolean choice) {
        X.x = 1;
        if (choice) {
            X.y = 2;
        } else {
            X.z = 3;
        }
        Y.a = 4;
    }

    public static void assignFieldsConditionallyMixed(boolean choice) {
        X.x = 1;
        if (choice) {
            Y.a = 2;
        } else {
            X.z = 3;
        }
        Y.b = 4;
    }

    public static void assignFieldsInLoop() {
        X.x = 1;
        for (int i = 0; i < 10; i++) {
            X.y += X.z;
        }
    }

    public static void assignFieldsInBranches(boolean choice) {
        if (choice) {
            X.x = 1;
        } else {
            X.y = 2;
        }
        X.z = 3;
    }

    public static void assignFieldsInBranchesMixed(boolean choice) {
        if (choice) {
            X.x = 1;
        } else {
            Y.a = 2;
        }
        X.z = 3;
    }

    private void test(String name, int initNodesAfterParse, int initNodesAfterOpt) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.NO);
        Assert.assertEquals(initNodesAfterParse, graph.getNodes().filter(InitializeKlassNode.class).count());
        HighTierContext highTierContext = getDefaultHighTierContext();
        new EliminateRedundantInitializationPhase().apply(graph, highTierContext);
        Assert.assertEquals(initNodesAfterOpt, graph.getNodes().filter(InitializeKlassNode.class).count());
    }

    @Test
    public void test1() {
        test("assignFields", 3, 1);
    }

    @Test
    public void test2() {
        test("assignFieldsConditionally", 3, 1);
    }

    @Test
    public void test3() {
        test("assignFieldsSubclassDominates", 4, 1);
    }

    @Test
    public void test4() {
        test("assignFieldsConditionallySubclassDominates", 4, 1);
    }

    @Test
    public void test5() {
        test("assignFieldsSubclassPostdominates", 2, 2);
    }

    @Test
    public void test6() {
        test("assignFieldsConditionallySubclassPostdominates", 4, 2);
    }

    @Test
    public void test7() {
        test("assignFieldsConditionallyMixed", 4, 3);
    }

    @Test
    public void test8() {
        test("assignFieldsInLoop", 4, 1);
    }

    @Test
    public void test9() {
        test("assignFieldsInBranches", 3, 2);
    }

    @Test
    public void test10() {
        test("assignFieldsInBranchesMixed", 3, 2);
    }
}
