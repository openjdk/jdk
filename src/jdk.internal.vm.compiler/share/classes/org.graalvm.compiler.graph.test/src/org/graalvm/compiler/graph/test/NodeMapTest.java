/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.options.OptionValues;

public class NodeMapTest extends GraphTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        protected TestNode() {
            super(TYPE);
        }
    }

    private Graph graph;
    private TestNode[] nodes = new TestNode[100];
    private NodeMap<Integer> map;

    @Before
    public void before() {
        // Need to initialize HotSpotGraalRuntime before any Node class is initialized.
        Graal.getRuntime();

        OptionValues options = getOptions();
        graph = new Graph(options, getDebug(options));
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = graph.add(new TestNode());
        }
        map = new NodeMap<>(graph);
        for (int i = 0; i < nodes.length; i += 2) {
            map.set(nodes[i], i);
        }
    }

    @Test
    public void testEmpty() {
        NodeMap<Integer> emptyMap = new NodeMap<>(graph);
        for (TestNode node : nodes) {
            assertEquals(null, emptyMap.get(node));
        }
    }

    @Test
    public void testSimple() {
        for (int i = 0; i < nodes.length; i++) {
            if ((i & 1) == 0) {
                assertEquals((Integer) i, map.get(nodes[i]));
            } else {
                assertEquals(null, map.get(nodes[i]));
            }
        }
    }

    @Test
    public void testSimpleChanged() {
        for (TestNode node : nodes) {
            map.set(node, 1);
        }
        for (TestNode node : nodes) {
            map.set(node, null);
        }
        for (int i = 0; i < nodes.length; i += 2) {
            map.set(nodes[i], i);
        }

        for (int i = 0; i < nodes.length; i++) {
            if ((i & 1) == 0) {
                assertEquals((Integer) i, map.get(nodes[i]));
            } else {
                assertEquals(null, map.get(nodes[i]));
            }
        }
    }

    @Test
    public void testNewGet() {
        /*
         * Failing here is not required, but if this behavior changes, usages of get need to be
         * checked for compatibility.
         */
        TestNode newNode = graph.add(new TestNode());
        try {
            map.get(newNode);
            fail("expected " + (Assertions.assertionsEnabled() ? AssertionError.class.getSimpleName() : ArrayIndexOutOfBoundsException.class.getSimpleName()));
        } catch (AssertionError ae) {
            // thrown when assertions are enabled
        } catch (ArrayIndexOutOfBoundsException e) {
            // thrown when assertions are disabled
        }
    }

    @Test
    public void testNewSet() {
        /*
         * Failing here is not required, but if this behavior changes, usages of set need to be
         * checked for compatibility.
         */
        TestNode newNode = graph.add(new TestNode());
        try {
            map.set(newNode, 1);
            fail("expected " + (Assertions.assertionsEnabled() ? AssertionError.class.getSimpleName() : ArrayIndexOutOfBoundsException.class.getSimpleName()));
        } catch (AssertionError ae) {
            // thrown when assertions are enabled
        } catch (ArrayIndexOutOfBoundsException e) {
            // thrown when assertions are disabled
        }
    }

    @Test
    public void testNewGetAndGrow() {
        TestNode newNode = graph.add(new TestNode());
        assertEquals(null, map.getAndGrow(newNode));
    }

    @Test
    public void testNewSetAndGrow() {
        TestNode newNode = graph.add(new TestNode());
        map.setAndGrow(newNode, 1);
        assertEquals((Integer) 1, map.get(newNode));
    }
}
