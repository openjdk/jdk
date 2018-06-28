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


package org.graalvm.compiler.graph.test.graphio;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphStructure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

public final class NodeEncodingTest {

    private ByteArrayOutputStream out;

    @Before
    public void initOutput() {
        out = new ByteArrayOutputStream();
    }

    @Test
    public void version40TheNodeIsntDumpedWithItsID() throws Exception {
        runTheNodeIsntDumpedWithItsID(true);
    }

    @Test
    public void defaultVersionTheNodeIsntDumpedWithItsID() throws Exception {
        runTheNodeIsntDumpedWithItsID(false);
    }

    private void runTheNodeIsntDumpedWithItsID(boolean explicitVersion) throws Exception {
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("clazz");
        MockNode node = new MockNode(clazz, 33); // random value otherwise not found in the stream
        try (GraphOutput<MockGraph, ?> dump = explicitVersion ? GraphOutput.newBuilder(new MockStructure()).protocolVersion(4, 0).build(w) : GraphOutput.newBuilder(new MockStructure()).build(w)) {
            dump.beginGroup(graph, "test1", "t1", null, 0, Collections.singletonMap("node", node));
            dump.endGroup();
        }

        assertEquals("Node is always requested", 1, node.nodeRequested);
        assertEquals("Nobody asks for id of a node in version 4.0", 0, node.idTested);
        assertByte(false, out.toByteArray(), 33);
        assertEquals("Node class of the node has been requested", 1, node.nodeClassRequested);
        assertEquals("Node class template name stored", 1, clazz.nameTemplateQueried);
        assertFalse("No to string ops", node.toStringRequested);
    }

    @Test
    public void dumpingNodeInVersion10() throws Exception {
        runTheNodeIsTreatedAsString(true);
    }

    private void runTheNodeIsTreatedAsString(boolean explicitVersion) throws Exception {
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("clazz");
        MockNode node = new MockNode(clazz, 33); // random value otherwise not found in the stream
        try (GraphOutput<MockGraph, ?> dump = explicitVersion ? GraphOutput.newBuilder(new MockStructure()).protocolVersion(1, 0).build(w) : GraphOutput.newBuilder(new MockStructure()).build(w)) {
            dump.beginGroup(graph, "test1", "t1", null, 0, Collections.singletonMap("node", node));
            dump.endGroup();
        }

        assertEquals("Node is always requested", 1, node.nodeRequested);
        assertEquals("Nobody asks for id of a node in version 1.0", 0, node.idTested);
        assertByte(false, out.toByteArray(), 33);
        assertEquals("Node class was needed to find out it is not a NodeClass instance", 1, node.nodeClassRequested);
        assertEquals("Node class template name wasn't needed however", 0, clazz.nameTemplateQueried);
        assertTrue("Node sent as a string version 1.0", node.toStringRequested);
    }

    @Test
    public void dumpingNodeInVersion15() throws Exception {
        runTheNodeIsTreatedPoolEntry(true);
    }

    private void runTheNodeIsTreatedPoolEntry(boolean explicitVersion) throws Exception {
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("clazz");
        MockNode node = new MockNode(clazz, 33); // random value otherwise not found in the stream
        try (GraphOutput<MockGraph, ?> dump = explicitVersion ? GraphOutput.newBuilder(new MockStructure()).protocolVersion(5, 0).build(w) : GraphOutput.newBuilder(new MockStructure()).build(w)) {
            dump.beginGroup(graph, "test1", "t1", null, 0, Collections.singletonMap("node", node));
            dump.endGroup();
        }

        assertEquals("Node is always requested", 1, node.nodeRequested);
        assertEquals("Id of our node is requested in version 5.0", 1, node.idTested);
        assertByte(true, out.toByteArray(), 33);
        assertTrue("Node class was needed at least once", 1 <= node.nodeClassRequested);
        assertEquals("Node class template name sent to server", 1, clazz.nameTemplateQueried);
        assertFalse("Node.toString() isn't needed", node.toStringRequested);
    }

    @Test
    public void dumpingNodeTwiceInVersion4() throws Exception {
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("clazz");
        MockNode node = new MockNode(clazz, 33); // random value otherwise not found in the stream
        try (GraphOutput<MockGraph, ?> dump = GraphOutput.newBuilder(new MockStructure()).protocolVersion(4, 0).build(w)) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("node1", node);
            props.put("node2", node);
            props.put("node3", node);
            dump.beginGroup(graph, "test1", "t1", null, 0, props);
            dump.endGroup();
        }

        assertEquals("Node requested three times", 3, node.nodeRequested);
        assertEquals("Nobody asks for id of a node in version 4.0", 0, node.idTested);
        // check there is no encoded string for object #3
        assertByte(false, out.toByteArray(), 1, 0, 3);
        assertEquals("Node class of the node has been requested three times", 3, node.nodeClassRequested);
        assertEquals("Node class template name stored", 1, clazz.nameTemplateQueried);
        assertFalse("No to string ops", node.toStringRequested);
    }

    private static void assertByte(boolean shouldBeFound, byte[] arr, int... value) {
        boolean found = false;
        int at = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value[at]) {
                if (++at == value.length) {
                    found = true;
                    break;
                }
            } else {
                at = 0;
            }
        }
        if (shouldBeFound == found) {
            return;
        }
        if (shouldBeFound) {
            fail("Value " + value + " not found in\n" + Arrays.toString(arr));
        } else {
            fail("Value " + value + " surprisingly found in\n" + Arrays.toString(arr));
        }
    }

    private static final class MockStructure implements GraphStructure<MockGraph, MockNode, MockNodeClass, MockNodeClass> {

        @Override
        public MockGraph graph(MockGraph currentGraph, Object obj) {
            return obj instanceof MockGraph ? (MockGraph) obj : null;
        }

        @Override
        public Iterable<? extends MockNode> nodes(MockGraph graph) {
            return Collections.emptyList();
        }

        @Override
        public int nodesCount(MockGraph graph) {
            return 0;
        }

        @Override
        public int nodeId(MockNode node) {
            node.idTested++;
            return node.id;
        }

        @Override
        public boolean nodeHasPredecessor(MockNode node) {
            return false;
        }

        @Override
        public void nodeProperties(MockGraph graph, MockNode node, Map<String, ? super Object> properties) {
        }

        @Override
        public MockNode node(Object obj) {
            if (obj instanceof MockNode) {
                ((MockNode) obj).nodeRequested++;
                return (MockNode) obj;
            }
            return null;
        }

        @Override
        public MockNodeClass nodeClass(Object obj) {
            if (obj instanceof MockNode) {
                ((MockNode) obj).nodeClassRequested++;
            }
            return obj instanceof MockNodeClass ? (MockNodeClass) obj : null;
        }

        @Override
        public MockNodeClass classForNode(MockNode n) {
            n.nodeClassRequested++;
            return n.clazz;
        }

        @Override
        public String nameTemplate(MockNodeClass nodeClass) {
            nodeClass.nameTemplateQueried++;
            return "";
        }

        @Override
        public Object nodeClassType(MockNodeClass nodeClass) {
            return nodeClass.getClass();
        }

        @Override
        public MockNodeClass portInputs(MockNodeClass nodeClass) {
            return nodeClass;
        }

        @Override
        public MockNodeClass portOutputs(MockNodeClass nodeClass) {
            return nodeClass;
        }

        @Override
        public int portSize(MockNodeClass port) {
            return 0;
        }

        @Override
        public boolean edgeDirect(MockNodeClass port, int index) {
            return false;
        }

        @Override
        public String edgeName(MockNodeClass port, int index) {
            return null;
        }

        @Override
        public Object edgeType(MockNodeClass port, int index) {
            return null;
        }

        @Override
        public Collection<? extends MockNode> edgeNodes(MockGraph graph, MockNode node, MockNodeClass port, int index) {
            return null;
        }
    }

    private static final class MockGraph {

    }

    private static final class MockNode {
        final MockNodeClass clazz;
        final int id;
        int idTested;
        int nodeClassRequested;
        int nodeRequested;
        boolean toStringRequested;

        MockNode(MockNodeClass clazz, int id) {
            this.clazz = clazz;
            this.id = id;
        }

        @Override
        public String toString() {
            this.toStringRequested = true;
            return "MockNode{" + "id=" + id + ", class=" + clazz + '}';
        }
    }

    private static final class MockNodeClass {
        final String name;
        int nameTemplateQueried;

        MockNodeClass(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "MockNodeClass{" + "name=" + name + '}';
        }
    }
}
