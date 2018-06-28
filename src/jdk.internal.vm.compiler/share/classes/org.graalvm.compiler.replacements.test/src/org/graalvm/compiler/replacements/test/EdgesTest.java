/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.common.inlining.policy.InlineMethodSubstitutionsPolicy;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class EdgesTest extends GraalCompilerTest {

    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);
        @Input NodeInputList<ValueNode> itail;
        @Input ConstantNode i1;
        @Input FloatingNode i2;

        protected TestNode() {
            super(TYPE);
        }

    }

    protected StructuredGraph createGraph() {
        OptionValues options = getInitialOptions();
        return new StructuredGraph.Builder(options, getDebugContext(options)).build();
    }

    StructuredGraph graph = createGraph();
    TestNode node;
    ConstantNode i1;
    ConstantNode i2;
    ConstantNode i3;
    ConstantNode i4;
    Edges inputs;

    public EdgesTest() {
        node = new TestNode();
        i1 = ConstantNode.forInt(1, graph);
        i2 = ConstantNode.forDouble(1.0d, graph);
        i3 = ConstantNode.forInt(4, graph);
        i4 = ConstantNode.forInt(14, graph);
        node.itail = new NodeInputList<>(node, new ValueNode[]{i3, i4});
        node.i1 = i1;
        node.i2 = i2;
        graph.add(node);
        inputs = node.getNodeClass().getInputEdges();
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#getNode(Node, long[], int)}.
     */
    @Test
    public void test0() {
        testMethod(getMethod("getNode", Node.class, long[].class, int.class), null, node, inputs.getOffsets(), 0);
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#getNodeList(Node, long[], int)}.
     */
    @Test
    public void test1() {
        testMethod(getMethod("getNodeList", Node.class, long[].class, int.class), null, node, inputs.getOffsets(), 2);
    }

    /**
     * Checks that there are no checkcasts in the compiled version of
     * {@link Edges#setNode(Node, int, Node)}.
     */
    @Test
    public void test2() {
        testMethod(getMethod("setNode", Node.class, int.class, Node.class), inputs, node, 1, i2);
    }

    private void testMethod(Method method, Object receiver, Object... args) {
        try {
            // Invoke the method to ensure it has a type profile
            for (int i = 0; i < 5000; i++) {
                method.invoke(receiver, args);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(method);
        StructuredGraph g = parseProfiled(javaMethod, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        new InliningPhase(new InlineMethodSubstitutionsPolicy(), new CanonicalizerPhase()).apply(g, context);
        new CanonicalizerPhase().apply(g, context);
        Assert.assertTrue(g.getNodes().filter(InstanceOfNode.class).isEmpty());
    }

    private static Method getMethod(final String name, Class<?>... parameters) {
        try {
            return Edges.class.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
