/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.junit.Assert;
import org.junit.Test;

public class ProbabilityDirectiveTest extends GraalCompilerTest {

    public static int branchProbabilitySnippet(int arg) {
        if (GraalDirectives.injectBranchProbability(0.125, arg > 0)) {
            GraalDirectives.controlFlowAnchor(); // prevent removal of the if
            return 1;
        } else {
            GraalDirectives.controlFlowAnchor(); // prevent removal of the if
            return 2;
        }
    }

    @Test
    public void testBranchProbability() {
        test("branchProbabilitySnippet", 5);
    }

    public static int branchProbabilitySnippet2(int arg) {
        if (!GraalDirectives.injectBranchProbability(0.125, arg <= 0)) {
            GraalDirectives.controlFlowAnchor(); // prevent removal of the if
            return 2;
        } else {
            GraalDirectives.controlFlowAnchor(); // prevent removal of the if
            return 1;
        }
    }

    @Test
    public void testBranchProbability2() {
        test("branchProbabilitySnippet2", 5);
    }

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        NodeIterable<IfNode> ifNodes = graph.getNodes(IfNode.TYPE);
        Assert.assertEquals("IfNode count", 1, ifNodes.count());

        IfNode ifNode = ifNodes.first();
        AbstractBeginNode oneSuccessor;
        if (returnValue(ifNode.trueSuccessor()) == 1) {
            oneSuccessor = ifNode.trueSuccessor();
        } else {
            assert returnValue(ifNode.falseSuccessor()) == 1;
            oneSuccessor = ifNode.falseSuccessor();
        }
        Assert.assertEquals("branch probability of " + ifNode, 0.125, ifNode.probability(oneSuccessor), 0);

        return true;
    }

    private static int returnValue(AbstractBeginNode b) {
        ControlFlowAnchorNode anchor = (ControlFlowAnchorNode) b.next();
        ReturnNode returnNode = (ReturnNode) anchor.next();
        return returnNode.result().asJavaConstant().asInt();
    }
}
