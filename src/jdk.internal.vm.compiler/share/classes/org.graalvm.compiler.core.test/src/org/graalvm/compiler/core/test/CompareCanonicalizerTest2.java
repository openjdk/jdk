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

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Test;

public class CompareCanonicalizerTest2 extends GraalCompilerTest {

    @SuppressWarnings("unused") private static boolean sink;

    private StructuredGraph getCanonicalizedGraph(String name) {
        StructuredGraph graph = getRegularGraph(name);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        return graph;
    }

    private StructuredGraph getRegularGraph(String name) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.YES);
        return graph;
    }

    @Test
    public void test0() {
        assertEquals(getCanonicalizedGraph("integerTestCanonicalization0"), getRegularGraph("integerTestCanonicalization0"));
    }

    public static void integerTestCanonicalization0(int a) {
        sink = 1 < a + 1;
    }

    @Test
    public void test1() {
        assertEquals(getCanonicalizedGraph("integerTestCanonicalization1"), getRegularGraph("integerTestCanonicalization1"));
    }

    public static void integerTestCanonicalization1(int a) {
        sink = a - 1 < -1;
    }

    @Test
    public void test2() {
        assertEquals(getCanonicalizedGraph("integerTestCanonicalization2a"), getCanonicalizedGraph("integerTestCanonicalization2Reference"));
        assertEquals(getCanonicalizedGraph("integerTestCanonicalization2b"), getCanonicalizedGraph("integerTestCanonicalization2Reference"));
    }

    public static boolean integerTestCanonicalization2a(Object[] arr) {
        return arr.length - 1 < 0;
    }

    public static boolean integerTestCanonicalization2b(Object[] arr) {
        return arr.length < 1;
    }

    public static boolean integerTestCanonicalization2Reference(Object[] arr) {
        return arr.length == 0;
    }

    @Test
    public void test3() {
        assertEquals(getCanonicalizedGraph("integerTestCanonicalization3"), getCanonicalizedGraph("integerTestCanonicalization3Reference"));
    }

    public static boolean integerTestCanonicalization3(Object[] arr) {
        return ((long) (arr.length - 1)) - 1 < 0;
    }

    public static boolean integerTestCanonicalization3Reference(Object[] arr) {
        return arr.length < 2;
    }

}
