/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.graph.test;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NodeBitMapTest extends GraphTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        protected TestNode() {
            super(TYPE);
        }
    }

    private Graph graph;
    private TestNode[] nodes = new TestNode[100];
    private NodeBitMap map;

    @Before
    public void before() {
        // Need to initialize HotSpotGraalRuntime before any Node class is initialized.
        Graal.getRuntime();

        OptionValues options = getOptions();
        graph = new Graph(options, getDebug(options));
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = graph.add(new TestNode());
        }
        map = graph.createNodeBitMap();
    }

    @Test
    public void iterateEmpty() {
        for (Node n : map) {
            Assert.fail("no elements expected: " + n);
        }
    }

    @Test
    public void iterateMarkedNodes() {
        map.mark(nodes[99]);
        map.mark(nodes[0]);
        map.mark(nodes[7]);
        map.mark(nodes[1]);
        map.mark(nodes[53]);

        Iterator<Node> iter = map.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[0], iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[1], iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[7], iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[53], iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[99], iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void deleteNodeWhileIterating() {
        map.mark(nodes[99]);
        map.mark(nodes[0]);
        map.mark(nodes[7]);
        map.mark(nodes[1]);
        map.mark(nodes[53]);

        Iterator<Node> iter = map.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[0], iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[1], iter.next());
        nodes[7].markDeleted();
        nodes[53].markDeleted();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[99], iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void deleteAllNodesBeforeIterating() {
        for (int i = 0; i < nodes.length; i++) {
            map.mark(nodes[i]);
            nodes[i].markDeleted();
        }

        Iterator<Node> iter = map.iterator();
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void multipleHasNextInvocations() {
        map.mark(nodes[7]);

        Iterator<Node> iter = map.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[7], iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test(expected = NoSuchElementException.class)
    public void noSuchElement() {
        map.iterator().next();
    }

    @Test(expected = ConcurrentModificationException.class)
    public void concurrentModification() {
        map.mark(nodes[7]);

        map.mark(nodes[99]);
        map.mark(nodes[0]);
        map.mark(nodes[7]);
        map.mark(nodes[1]);
        map.mark(nodes[53]);

        Iterator<Node> iter = map.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[0], iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[1], iter.next());
        Assert.assertTrue(iter.hasNext());
        nodes[7].markDeleted();
        iter.next();
    }

    @Test
    public void nextWithoutHasNext() {
        map.mark(nodes[99]);
        map.mark(nodes[0]);
        map.mark(nodes[7]);
        map.mark(nodes[1]);
        map.mark(nodes[53]);

        Iterator<Node> iter = map.iterator();
        Assert.assertEquals(nodes[0], iter.next());
        Assert.assertEquals(nodes[1], iter.next());
        Assert.assertEquals(nodes[7], iter.next());
        Assert.assertEquals(nodes[53], iter.next());
        Assert.assertEquals(nodes[99], iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void markWhileIterating() {
        map.mark(nodes[0]);

        Iterator<Node> iter = map.iterator();
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[0], iter.next());
        map.mark(nodes[7]);
        Assert.assertTrue(iter.hasNext());
        map.mark(nodes[1]);
        Assert.assertEquals(nodes[7], iter.next());
        map.mark(nodes[99]);
        map.mark(nodes[53]);
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[53], iter.next());
        Assert.assertTrue(iter.hasNext());
        Assert.assertEquals(nodes[99], iter.next());
        Assert.assertFalse(iter.hasNext());
    }
}
