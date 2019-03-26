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


package org.graalvm.compiler.graph.test.graphio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphStructure;
import org.junit.Test;

public final class GraphOutputTest {

    @Test
    @SuppressWarnings("static-method")
    public void testWritableByteChannel() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(out);
        ByteBuffer data = generateData(1 << 17);
        GraphOutput<?, ?> graphOutput = GraphOutput.newBuilder(new MockGraphStructure()).protocolVersion(6, 0).embedded(true).build(channel);
        try (GraphOutput<?, ?> closable = graphOutput) {
            assertTrue(closable.isOpen());
            closable.write(data);
        }
        assertFalse(graphOutput.isOpen());
        assertArrayEquals(data.array(), out.toByteArray());
    }

    @Test
    @SuppressWarnings("static-method")
    public void testWriteDuringPrint() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(out);
        class Action implements Runnable {
            GraphOutput<MockGraph, ?> out;

            @Override
            public void run() {
                try {
                    ByteBuffer data = ByteBuffer.allocate(16);
                    data.limit(16);
                    out.write(data);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        }
        Action action = new Action();
        try (GraphOutput<MockGraph, ?> graphOutput = GraphOutput.newBuilder(new MockGraphStructure(action)).protocolVersion(6, 0).build(channel)) {
            action.out = graphOutput;
            try {
                graphOutput.print(new MockGraph(), Collections.emptyMap(), 0, "Mock Graph");
                fail("Expected IllegalStateException");
            } catch (IllegalStateException ise) {
                // expected exception
            }
        }
    }

    @Test
    @SuppressWarnings("static-method")
    public void testEmbeddedWritableByteChannel() throws IOException {
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        WritableByteChannel expectedChannel = Channels.newChannel(expected);
        Map<Object, Object> properties = Collections.singletonMap("version.id", 1);
        try (GraphOutput<MockGraph, ?> graphOutput = GraphOutput.newBuilder(new MockGraphStructure()).protocolVersion(6, 0).build(expectedChannel)) {
            graphOutput.print(new MockGraph(), properties, 1, "Graph 1");
            graphOutput.write(ByteBuffer.allocate(0));
            graphOutput.print(new MockGraph(), properties, 2, "Graph 1");
        }
        ByteArrayOutputStream embedded = new ByteArrayOutputStream();
        SharedWritableByteChannel embeddChannel = new SharedWritableByteChannel(Channels.newChannel(embedded));
        try {
            try (GraphOutput<MockGraph, ?> baseOutput = GraphOutput.newBuilder(new MockGraphStructure()).protocolVersion(6, 0).build(embeddChannel)) {
                try (GraphOutput<MockGraph, ?> embeddedOutput = GraphOutput.newBuilder(new MockGraphStructure()).protocolVersion(6, 0).embedded(true).build((WritableByteChannel) baseOutput)) {
                    embeddedOutput.print(new MockGraph(), properties, 1, "Graph 1");
                    baseOutput.print(new MockGraph(), properties, 2, "Graph 1");
                }
            }
        } finally {
            embeddChannel.realClose();
        }
        assertArrayEquals(expected.toByteArray(), embedded.toByteArray());
    }

    private static ByteBuffer generateData(int size) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++) {
            buffer.put(i, (byte) i);
        }
        buffer.limit(size);
        return buffer;
    }

    private static final class SharedWritableByteChannel implements WritableByteChannel {

        private final WritableByteChannel delegate;

        SharedWritableByteChannel(WritableByteChannel delegate) {
            Objects.requireNonNull(delegate, "Delegate must be non null.");
            this.delegate = delegate;
        }

        @Override
        public int write(ByteBuffer bb) throws IOException {
            return delegate.write(bb);
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
        }

        void realClose() throws IOException {
            delegate.close();
        }
    }

    private static final class MockGraphStructure implements GraphStructure<MockGraph, Void, Void, Void> {

        private final Runnable enterAction;

        MockGraphStructure() {
            this.enterAction = null;
        }

        MockGraphStructure(Runnable enterAction) {
            this.enterAction = enterAction;
        }

        @Override
        public MockGraph graph(MockGraph currentGraph, Object obj) {
            onEnter();
            return null;
        }

        @Override
        public Iterable<? extends Void> nodes(MockGraph graph) {
            onEnter();
            return Collections.emptySet();
        }

        @Override
        public int nodesCount(MockGraph graph) {
            onEnter();
            return 0;
        }

        @Override
        public int nodeId(Void node) {
            onEnter();
            return 0;
        }

        @Override
        public boolean nodeHasPredecessor(Void node) {
            onEnter();
            return false;
        }

        @Override
        public void nodeProperties(MockGraph graph, Void node, Map<String, ? super Object> properties) {
            onEnter();
        }

        @Override
        public Void node(Object obj) {
            onEnter();
            return null;
        }

        @Override
        public Void nodeClass(Object obj) {
            onEnter();
            return null;
        }

        @Override
        public Void classForNode(Void node) {
            onEnter();
            return null;
        }

        @Override
        public String nameTemplate(Void nodeClass) {
            onEnter();
            return null;
        }

        @Override
        public Object nodeClassType(Void nodeClass) {
            onEnter();
            return null;
        }

        @Override
        public Void portInputs(Void nodeClass) {
            onEnter();
            return null;
        }

        @Override
        public Void portOutputs(Void nodeClass) {
            onEnter();
            return null;
        }

        @Override
        public int portSize(Void port) {
            onEnter();
            return 0;
        }

        @Override
        public boolean edgeDirect(Void port, int index) {
            onEnter();
            return false;
        }

        @Override
        public String edgeName(Void port, int index) {
            onEnter();
            return null;
        }

        @Override
        public Object edgeType(Void port, int index) {
            onEnter();
            return null;
        }

        @Override
        public Collection<? extends Void> edgeNodes(MockGraph graph, Void node, Void port, int index) {
            onEnter();
            return null;
        }

        private void onEnter() {
            if (enterAction != null) {
                enterAction.run();
            }
        }
    }

    private static final class MockGraph {
    }
}
