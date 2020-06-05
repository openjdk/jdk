/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.Iterator;

import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeSuccessorList;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.junit.Assert;
import org.junit.Test;

public class NodePosIteratorTest extends GraalCompilerTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);
        @Successor Node s1;
        @Successor Node s2;
        @Successor NodeSuccessorList<Node> stail;

        @Input NodeInputList<ValueNode> itail;
        @Input ConstantNode i1;
        @Input FloatingNode i2;

        protected TestNode() {
            super(TYPE);
        }

    }

    @Test
    public void testNodeInputIteratorLimit() {
        DebugContext debug = getDebugContext();
        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug,
                        StructuredGraph.AllowAssumptions.YES).build();

        AbstractMergeNode merge = graph.add(new MergeNode());
        for (int i = 0; i < 65536; i++) {
            EndNode end = graph.add(new EndNode());
            merge.addForwardEnd(end);
        }
        merge.inputs().count();
        EndNode end = graph.add(new EndNode());
        try {
            merge.addForwardEnd(end);
        } catch (PermanentBailoutException e) {
            return;
        }
        Assert.fail("Expected a permanent bailout exception due to too high number of inputs");
    }

    @Test
    public void testInputs() {
        TestNode n = new TestNode();

        ConstantNode i1 = ConstantNode.forInt(1);
        ConstantNode i2 = ConstantNode.forDouble(1.0d);
        ConstantNode i3 = ConstantNode.forInt(4);
        ConstantNode i4 = ConstantNode.forInt(14);
        n.itail = new NodeInputList<>(n, new ValueNode[]{i3, i4});
        n.i1 = i1;
        n.i2 = i2;

        NodeIterable<Node> inputs = n.inputs();

        Iterator<Node> iterator = inputs.iterator();
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i1);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i2);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i3);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i4);
        Assert.assertFalse(iterator.hasNext());
        Assert.assertFalse(iterator.hasNext());

        Iterator<Position> positionIterator = n.inputPositions().iterator();
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertEquals("ConstantNode:i1", positionIterator.next().toString());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertEquals("FloatingNode:i2", positionIterator.next().toString());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertEquals("NodeInputList:itail[0]", positionIterator.next().toString());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertEquals("NodeInputList:itail[1]", positionIterator.next().toString());
        Assert.assertFalse(positionIterator.hasNext());
        Assert.assertFalse(positionIterator.hasNext());

        iterator = inputs.iterator();
        n.i1 = i4;
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i4);
        n.i2 = i1;
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i1);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i3);
        n.itail.initialize(1, i4);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i4);
        Assert.assertFalse(iterator.hasNext());

        iterator = inputs.iterator();
        n.i1 = null;
        n.i2 = i2;
        n.itail.initialize(0, null);
        n.itail.initialize(1, i4);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i2);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), i4);
        Assert.assertFalse(iterator.hasNext());
    }

    @Test
    public void testSuccessors() {
        TestNode n = new TestNode();
        EndNode s1 = new EndNode();
        EndNode s2 = new EndNode();
        EndNode s3 = new EndNode();
        EndNode s4 = new EndNode();
        n.s1 = s1;
        n.s2 = s2;
        n.stail = new NodeSuccessorList<>(n, new Node[]{s3, s4});

        NodeIterable<Node> successors = n.successors();
        Iterator<Node> iterator = successors.iterator();
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s1);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s2);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s3);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s4);
        Assert.assertFalse(iterator.hasNext());
        Assert.assertFalse(iterator.hasNext());

        Iterator<Position> positionIterator = n.successorPositions().iterator();
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertEquals(Node.class.getSimpleName() + ":s1", positionIterator.next().toString());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertEquals(Node.class.getSimpleName() + ":s2", positionIterator.next().toString());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertEquals(NodeSuccessorList.class.getSimpleName() + ":stail[0]", positionIterator.next().toString());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertTrue(positionIterator.hasNext());
        Assert.assertEquals(NodeSuccessorList.class.getSimpleName() + ":stail[1]", positionIterator.next().toString());
        Assert.assertFalse(positionIterator.hasNext());
        Assert.assertFalse(positionIterator.hasNext());

        iterator = successors.iterator();
        n.s1 = s4;
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s4);
        n.s2 = s1;
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s1);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s3);
        n.stail.initialize(1, s4);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s4);
        Assert.assertFalse(iterator.hasNext());

        iterator = successors.iterator();
        n.s1 = null;
        n.s2 = s2;
        n.stail.initialize(0, null);
        n.stail.initialize(1, s4);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s2);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(iterator.next(), s4);
        Assert.assertFalse(iterator.hasNext());
    }
}
