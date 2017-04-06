/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class MergeCanonicalizerTest extends GraalCompilerTest {

    public static int staticField;

    private int field;

    @Test
    public void testSplitReturn() {
        test("testSplitReturnSnippet", 2);
        testReturnCount("testSplitReturnSnippet", 2);
    }

    public int testSplitReturnSnippet(int b) {
        int v;
        if (b < 0) {
            staticField = 1;
            v = 10;
        } else {
            staticField = 2;
            v = 20;
        }
        int i = field;
        i = field + i;
        return v;
    }

    private void testReturnCount(String snippet, int returnCount) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "Graph");
        assertDeepEquals(returnCount, graph.getNodes(ReturnNode.TYPE).count());
    }
}
