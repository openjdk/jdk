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

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;

public class IterationDirectiveTest extends GraalCompilerTest {

    public static int loopFrequencySnippet(int arg) {
        int x = arg;
        while (GraalDirectives.injectIterationCount(128, x > 1)) {
            GraalDirectives.controlFlowAnchor(); // prevent loop peeling or unrolling
            if (x % 2 == 0) {
                x /= 2;
            } else {
                x = 3 * x + 1;
            }
        }
        return x;
    }

    @Test
    public void testLoopFrequency() {
        test("loopFrequencySnippet", 7);
    }

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        NodeIterable<LoopBeginNode> loopBeginNodes = graph.getNodes(LoopBeginNode.TYPE);
        Assert.assertEquals("LoopBeginNode count", 1, loopBeginNodes.count());

        LoopBeginNode loopBeginNode = loopBeginNodes.first();
        Assert.assertEquals("loop frequency of " + loopBeginNode, 128, loopBeginNode.loopFrequency(), 0);

        return true;
    }
}
