/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.graph.test.matchers.NodeIterableIsEmpty.isNotEmpty;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Assert;
import org.junit.Test;

public class CompareCanonicalizerTest2 extends GraalCompilerTest {

    @SuppressWarnings("unused") private static int sink0;
    @SuppressWarnings("unused") private static int sink1;

    private StructuredGraph getCanonicalizedGraph(String name) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.YES);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        return graph;
    }

    public void testIntegerTestCanonicalization(String name) {
        StructuredGraph graph = getCanonicalizedGraph(name);
        Assert.assertThat(graph.getNodes().filter(IntegerLessThanNode.class), isNotEmpty());
    }

    @Test
    public void test0() {
        testIntegerTestCanonicalization("integerTestCanonicalization0");
    }

    @Test
    public void test1() {
        testIntegerTestCanonicalization("integerTestCanonicalization1");
    }

    public static void integerTestCanonicalization0(int a) {
        if (1 < a + 1) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

    public static void integerTestCanonicalization1(int a) {
        if (a - 1 < -1) {
            sink1 = 0;
        } else {
            sink0 = -1;
        }
    }

}
