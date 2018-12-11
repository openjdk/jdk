/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class CompareCanonicalizerTest extends GraalCompilerTest {

    private StructuredGraph getCanonicalizedGraph(String name) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.YES);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        return graph;
    }

    private static ValueNode getResult(StructuredGraph graph) {
        assertTrue(graph.start().next() instanceof ReturnNode);
        ReturnNode ret = (ReturnNode) graph.start().next();
        return ret.result();
    }

    @Test
    public void testCanonicalComparison() {
        StructuredGraph referenceGraph = parseEager("referenceCanonicalComparison", AllowAssumptions.NO);
        for (int i = 1; i < 4; i++) {
            StructuredGraph graph = parseEager("canonicalCompare" + i, AllowAssumptions.NO);
            assertEquals(referenceGraph, graph);
        }
        new CanonicalizerPhase().apply(referenceGraph, new PhaseContext(getProviders()));
        for (int i = 1; i < 4; i++) {
            StructuredGraph graph = getCanonicalizedGraph("canonicalCompare" + i);
            assertEquals(referenceGraph, graph);
        }
    }

    public static int referenceCanonicalComparison(int a, int b) {
        if (a < b) {
            return 1;
        } else {
            return 2;
        }
    }

    public static int canonicalCompare1(int a, int b) {
        if (a >= b) {
            return 2;
        } else {
            return 1;
        }
    }

    public static int canonicalCompare2(int a, int b) {
        if (b > a) {
            return 1;
        } else {
            return 2;
        }
    }

    public static int canonicalCompare3(int a, int b) {
        if (b <= a) {
            return 2;
        } else {
            return 1;
        }
    }

    @Test
    public void testIntegerTest() {
        for (int i = 1; i <= 4; i++) {
            StructuredGraph graph = getCanonicalizedGraph("integerTest" + i);

            ReturnNode returnNode = (ReturnNode) graph.start().next();
            ConditionalNode conditional = (ConditionalNode) returnNode.result();
            IntegerTestNode test = (IntegerTestNode) conditional.condition();
            ParameterNode param0 = graph.getParameter(0);
            ParameterNode param1 = graph.getParameter(1);
            assertTrue((test.getX() == param0 && test.getY() == param1) || (test.getX() == param1 && test.getY() == param0));
        }
    }

    public static boolean integerTest1(int x, int y) {
        return (x & y) == 0;
    }

    public static boolean integerTest2(long x, long y) {
        return 0 == (x & y);
    }

    public static boolean integerTest3(long x, long y) {
        int c = 5;
        return (c - 5) == (x & y);
    }

    public static boolean integerTest4(int x, int y) {
        int c = 10;
        return (x & y) == (10 - c);
    }

    @Test
    public void testIntegerTestCanonicalization() {
        ValueNode result = getResult(getCanonicalizedGraph("integerTestCanonicalization1"));
        assertTrue(result.isConstant() && result.asJavaConstant().asLong() == 1);
        result = getResult(getCanonicalizedGraph("integerTestCanonicalization2"));
        assertTrue(result.isConstant() && result.asJavaConstant().asLong() == 1);
        StructuredGraph graph = getCanonicalizedGraph("integerTestCanonicalization3");
        assertDeepEquals(1, graph.getNodes(ReturnNode.TYPE).count());
        assertTrue(graph.getNodes(ReturnNode.TYPE).first().result() instanceof ConditionalNode);
    }

    public static int integerTestCanonicalization1(boolean b) {
        int x = b ? 128 : 256;
        if ((x & 8) == 0) {
            return 1;
        } else {
            return 2;
        }
    }

    public static int integerTestCanonicalization2(boolean b) {
        int x = b ? 128 : 256;
        int y = b ? 32 : 64;
        if ((x & y) == 0) {
            return 1;
        } else {
            return 2;
        }
    }

    public static int integerTestCanonicalization3(boolean b) {
        int x = b ? 128 : 64;
        int y = b ? 32 : 64;
        if ((x & y) == 0) {
            return 1;
        } else {
            return 2;
        }
    }

}
