/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.graph.test.matchers.NodeIterableIsEmpty.isEmpty;
import static org.junit.Assert.assertThat;

import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Test;

public class UnusedArray extends GraalCompilerTest {
    @SuppressWarnings("unused")
    public void smallArray() {
        byte[] array = new byte[3];
    }

    @SuppressWarnings("unused")
    public void largeArray() {
        byte[] array = new byte[10 * 1024 * 1024];
    }

    @SuppressWarnings("unused")
    public void unknownArray(int l) {
        byte[] array = new byte[l];
    }

    @Test
    public void testSmall() {
        test("smallArray");
    }

    @Test
    public void testLarge() {
        test("largeArray");
    }

    @Test
    public void testUnknown() {
        test("unknownArray");
    }

    public void test(String method) {
        StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        NodeIterable<NewArrayNode> newArrayNodes = graph.getNodes().filter(NewArrayNode.class);
        assertThat(newArrayNodes, isEmpty());
    }
}
