/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Assert;
import org.junit.Test;

public class ConstantProbablityBranchFoldingTest extends GraalCompilerTest {

    public static int branchFoldingSnippet1() {
        if (GraalDirectives.injectBranchProbability(0.5, true)) {
            return 1;
        } else {
            return 2;
        }
    }

    public static int branchFoldingSnippet2() {
        if (GraalDirectives.injectBranchProbability(0.5, false)) {
            return 1;
        } else {
            return 2;
        }
    }

    @Test
    public void testEarlyFolding1() {
        test("branchFoldingSnippet1");
    }

    @Test
    public void testEarlyFolding2() {
        test("branchFoldingSnippet2");
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        NodeIterable<IfNode> ifNodes = graph.getNodes(IfNode.TYPE);
        Assert.assertEquals("IfNode count", 0, ifNodes.count());
    }
}
