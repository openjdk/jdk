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


package org.graalvm.compiler.graph.test;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;

import org.junit.Test;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.options.OptionValues;

public class TypedNodeIteratorTest extends GraphTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class TestNode extends Node implements IterableNodeType, TestNodeInterface {

        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);
        protected final String name;

        protected TestNode(String name) {
            super(TYPE);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    @Test
    public void singleNodeTest() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        graph.add(new TestNode("a"));
        assertTrue(graph.hasNode(TestNode.TYPE));
        assertEquals("a", toString(graph.getNodes(TestNode.TYPE)));
    }

    static OptionValues getOptions() {
        return Graal.getRequiredCapability(OptionValues.class);
    }

    @Test
    public void deletingNodeTest() {
        TestNode testNode = new TestNode("a");
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        graph.add(testNode);
        testNode.safeDelete();
        assertEquals("", toString(graph.getNodes(TestNode.TYPE)));
    }

    @Test
    public void deleteAndAddTest() {
        TestNode testNode = new TestNode("b");
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        graph.add(new TestNode("a"));
        graph.add(testNode);
        testNode.safeDelete();
        assertEquals("a", toString(graph.getNodes(TestNode.TYPE)));
        graph.add(new TestNode("c"));
        assertEquals("ac", toString(graph.getNodes(TestNode.TYPE)));
    }

    @Test
    public void iteratorBehaviorTest() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        graph.add(new TestNode("a"));
        Iterator<TestNode> iterator = graph.getNodes(TestNode.TYPE).iterator();
        assertTrue(iterator.hasNext());
        assertEquals("a", iterator.next().getName());
        assertFalse(iterator.hasNext());
        graph.add(new TestNode("b"));
        assertTrue(iterator.hasNext());
        assertEquals("b", iterator.next().getName());
        assertFalse(iterator.hasNext());
        TestNode c = new TestNode("c");
        graph.add(c);
        assertTrue(iterator.hasNext());
        c.safeDelete();
        assertFalse(iterator.hasNext());
    }

    @Test
    public void complicatedIterationTest() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        graph.add(new TestNode("a"));
        for (TestNode tn : graph.getNodes(TestNode.TYPE)) {
            String name = tn.getName();
            for (int i = 0; i < name.length(); ++i) {
                char c = name.charAt(i);
                if (c == 'a') {
                    tn.safeDelete();
                    graph.add(new TestNode("b"));
                    graph.add(new TestNode("c"));
                } else if (c == 'b') {
                    tn.safeDelete();
                } else if (c == 'c') {
                    graph.add(new TestNode("d"));
                    graph.add(new TestNode("e"));
                    graph.add(new TestNode("d"));
                    graph.add(new TestNode("e"));
                    graph.add(new TestNode("e"));
                    graph.add(new TestNode("d"));
                    graph.add(new TestNode("e"));
                    graph.add(new TestNode("d"));
                } else if (c == 'd') {
                    for (TestNode tn2 : graph.getNodes(TestNode.TYPE)) {
                        if (tn2.getName().equals("e")) {
                            tn2.safeDelete();
                        } else if (tn2.getName().equals("c")) {
                            tn2.safeDelete();
                        }
                    }
                } else if (c == 'e') {
                    fail("All e nodes must have been deleted by visiting the d node");
                }
            }
        }
        assertEquals("dddd", toString(graph.getNodes(TestNode.TYPE)));
    }

    @Test
    public void addingNodeDuringIterationTest() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));
        graph.add(new TestNode("a"));
        StringBuilder sb = new StringBuilder();
        int z = 0;
        for (TestNode tn : graph.getNodes(TestNode.TYPE)) {
            if (z == 0) {
                graph.add(new TestNode("b"));
            }
            sb.append(tn.getName());
            z++;
        }
        assertEquals(2, z);
        assertEquals("ab", sb.toString());
        z = 0;
        for (TestNode tn : graph.getNodes(TestNode.TYPE)) {
            if (z == 0) {
                graph.add(new TestNode("c"));
            }
            assertNotNull(tn);
            z++;
        }
        assertEquals(3, z);
    }

    public static String toString(Iterable<? extends TestNodeInterface> nodes) {
        StringBuilder sb = new StringBuilder();
        for (TestNodeInterface tn : nodes) {
            sb.append(tn.getName());
        }
        return sb.toString();
    }
}
