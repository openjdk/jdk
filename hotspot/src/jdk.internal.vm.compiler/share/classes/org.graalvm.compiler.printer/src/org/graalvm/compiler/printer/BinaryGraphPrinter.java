/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.printer;

import static org.graalvm.compiler.graph.Edges.Type.Inputs;
import static org.graalvm.compiler.graph.Edges.Type.Successors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalDebugConfig.Options;
import org.graalvm.compiler.graph.CachedGraph;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.InputEdges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.schedule.SchedulePhase;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

public class BinaryGraphPrinter implements GraphPrinter {

    private static final int CONSTANT_POOL_MAX_SIZE = 8000;

    private static final int BEGIN_GROUP = 0x00;
    private static final int BEGIN_GRAPH = 0x01;
    private static final int CLOSE_GROUP = 0x02;

    private static final int POOL_NEW = 0x00;
    private static final int POOL_STRING = 0x01;
    private static final int POOL_ENUM = 0x02;
    private static final int POOL_CLASS = 0x03;
    private static final int POOL_METHOD = 0x04;
    private static final int POOL_NULL = 0x05;
    private static final int POOL_NODE_CLASS = 0x06;
    private static final int POOL_FIELD = 0x07;
    private static final int POOL_SIGNATURE = 0x08;

    private static final int PROPERTY_POOL = 0x00;
    private static final int PROPERTY_INT = 0x01;
    private static final int PROPERTY_LONG = 0x02;
    private static final int PROPERTY_DOUBLE = 0x03;
    private static final int PROPERTY_FLOAT = 0x04;
    private static final int PROPERTY_TRUE = 0x05;
    private static final int PROPERTY_FALSE = 0x06;
    private static final int PROPERTY_ARRAY = 0x07;
    private static final int PROPERTY_SUBGRAPH = 0x08;

    private static final int KLASS = 0x00;
    private static final int ENUM_KLASS = 0x01;

    static final int CURRENT_MAJOR_VERSION = 1;
    static final int CURRENT_MINOR_VERSION = 0;

    static final byte[] MAGIC_BYTES = {'B', 'I', 'G', 'V'};

    private void writeVersion() throws IOException {
        writeBytesRaw(MAGIC_BYTES);
        writeByte(CURRENT_MAJOR_VERSION);
        writeByte(CURRENT_MINOR_VERSION);
    }

    private static final class ConstantPool extends LinkedHashMap<Object, Character> {

        private final LinkedList<Character> availableIds;
        private char nextId;
        private static final long serialVersionUID = -2676889957907285681L;

        ConstantPool() {
            super(50, 0.65f);
            availableIds = new LinkedList<>();
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Object, Character> eldest) {
            if (size() > CONSTANT_POOL_MAX_SIZE) {
                availableIds.addFirst(eldest.getValue());
                return true;
            }
            return false;
        }

        private Character nextAvailableId() {
            if (!availableIds.isEmpty()) {
                return availableIds.removeFirst();
            }
            return nextId++;
        }

        public char add(Object obj) {
            Character id = nextAvailableId();
            put(obj, id);
            return id;
        }
    }

    private final ConstantPool constantPool;
    private final ByteBuffer buffer;
    private final WritableByteChannel channel;
    private final SnippetReflectionProvider snippetReflection;

    private static final Charset utf8 = Charset.forName("UTF-8");

    public BinaryGraphPrinter(WritableByteChannel channel, SnippetReflectionProvider snippetReflection) throws IOException {
        constantPool = new ConstantPool();
        buffer = ByteBuffer.allocateDirect(256 * 1024);
        this.snippetReflection = snippetReflection;
        this.channel = channel;
        writeVersion();
    }

    @Override
    public SnippetReflectionProvider getSnippetReflectionProvider() {
        return snippetReflection;
    }

    @Override
    public void print(Graph graph, String title, Map<Object, Object> properties) throws IOException {
        writeByte(BEGIN_GRAPH);
        writePoolObject(title);
        writeGraph(graph, properties);
        flush();
    }

    private void writeGraph(Graph graph, Map<Object, Object> properties) throws IOException {
        ScheduleResult scheduleResult = null;
        if (graph instanceof StructuredGraph) {

            StructuredGraph structuredGraph = (StructuredGraph) graph;
            scheduleResult = structuredGraph.getLastSchedule();
            if (scheduleResult == null) {

                // Also provide a schedule when an error occurs
                if (Options.PrintIdealGraphSchedule.getValue() || Debug.contextLookup(Throwable.class) != null) {
                    try {
                        SchedulePhase schedule = new SchedulePhase();
                        schedule.apply(structuredGraph);
                        scheduleResult = structuredGraph.getLastSchedule();
                    } catch (Throwable t) {
                    }
                }

            }
        }
        ControlFlowGraph cfg = scheduleResult == null ? Debug.contextLookup(ControlFlowGraph.class) : scheduleResult.getCFG();
        BlockMap<List<Node>> blockToNodes = scheduleResult == null ? null : scheduleResult.getBlockToNodesMap();
        NodeMap<Block> nodeToBlocks = scheduleResult == null ? null : scheduleResult.getNodeToBlockMap();
        List<Block> blocks = cfg == null ? null : Arrays.asList(cfg.getBlocks());
        writeProperties(properties);
        writeNodes(graph, nodeToBlocks, cfg);
        writeBlocks(blocks, blockToNodes);
    }

    private void flush() throws IOException {
        buffer.flip();
        channel.write(buffer);
        buffer.compact();
    }

    private void ensureAvailable(int i) throws IOException {
        assert buffer.capacity() >= i : "Can not make " + i + " bytes available, buffer is too small";
        while (buffer.remaining() < i) {
            flush();
        }
    }

    private void writeByte(int b) throws IOException {
        ensureAvailable(1);
        buffer.put((byte) b);
    }

    private void writeInt(int b) throws IOException {
        ensureAvailable(4);
        buffer.putInt(b);
    }

    private void writeLong(long b) throws IOException {
        ensureAvailable(8);
        buffer.putLong(b);
    }

    private void writeDouble(double b) throws IOException {
        ensureAvailable(8);
        buffer.putDouble(b);
    }

    private void writeFloat(float b) throws IOException {
        ensureAvailable(4);
        buffer.putFloat(b);
    }

    private void writeShort(char b) throws IOException {
        ensureAvailable(2);
        buffer.putChar(b);
    }

    private void writeString(String str) throws IOException {
        byte[] bytes = str.getBytes(utf8);
        writeBytes(bytes);
    }

    private void writeBytes(byte[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            writeBytesRaw(b);
        }
    }

    private void writeBytesRaw(byte[] b) throws IOException {
        int bytesWritten = 0;
        while (bytesWritten < b.length) {
            int toWrite = Math.min(b.length - bytesWritten, buffer.capacity());
            ensureAvailable(toWrite);
            buffer.put(b, bytesWritten, toWrite);
            bytesWritten += toWrite;
        }
    }

    private void writeInts(int[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            int sizeInBytes = b.length * 4;
            ensureAvailable(sizeInBytes);
            buffer.asIntBuffer().put(b);
            buffer.position(buffer.position() + sizeInBytes);
        }
    }

    private void writeDoubles(double[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            int sizeInBytes = b.length * 8;
            ensureAvailable(sizeInBytes);
            buffer.asDoubleBuffer().put(b);
            buffer.position(buffer.position() + sizeInBytes);
        }
    }

    private void writePoolObject(Object object) throws IOException {
        if (object == null) {
            writeByte(POOL_NULL);
            return;
        }
        Character id = constantPool.get(object);
        if (id == null) {
            addPoolEntry(object);
        } else {
            if (object instanceof Enum<?>) {
                writeByte(POOL_ENUM);
            } else if (object instanceof Class<?> || object instanceof JavaType) {
                writeByte(POOL_CLASS);
            } else if (object instanceof NodeClass) {
                writeByte(POOL_NODE_CLASS);
            } else if (object instanceof ResolvedJavaMethod) {
                writeByte(POOL_METHOD);
            } else if (object instanceof ResolvedJavaField) {
                writeByte(POOL_FIELD);
            } else if (object instanceof Signature) {
                writeByte(POOL_SIGNATURE);
            } else {
                writeByte(POOL_STRING);
            }
            writeShort(id.charValue());
        }
    }

    private static String getClassName(Class<?> klass) {
        if (!klass.isArray()) {
            return klass.getName();
        }
        return getClassName(klass.getComponentType()) + "[]";
    }

    private void addPoolEntry(Object object) throws IOException {
        char index = constantPool.add(object);
        writeByte(POOL_NEW);
        writeShort(index);
        if (object instanceof Class<?>) {
            Class<?> klass = (Class<?>) object;
            writeByte(POOL_CLASS);
            writeString(getClassName(klass));
            if (klass.isEnum()) {
                writeByte(ENUM_KLASS);
                Object[] enumConstants = klass.getEnumConstants();
                writeInt(enumConstants.length);
                for (Object o : enumConstants) {
                    writePoolObject(((Enum<?>) o).name());
                }
            } else {
                writeByte(KLASS);
            }
        } else if (object instanceof Enum<?>) {
            writeByte(POOL_ENUM);
            writePoolObject(object.getClass());
            writeInt(((Enum<?>) object).ordinal());
        } else if (object instanceof JavaType) {
            JavaType type = (JavaType) object;
            writeByte(POOL_CLASS);
            writeString(type.toJavaName());
            writeByte(KLASS);
        } else if (object instanceof NodeClass) {
            NodeClass<?> nodeClass = (NodeClass<?>) object;
            writeByte(POOL_NODE_CLASS);
            writeString(nodeClass.getJavaClass().getSimpleName());
            writeString(nodeClass.getNameTemplate());
            writeEdgesInfo(nodeClass, Inputs);
            writeEdgesInfo(nodeClass, Successors);
        } else if (object instanceof ResolvedJavaMethod) {
            writeByte(POOL_METHOD);
            ResolvedJavaMethod method = ((ResolvedJavaMethod) object);
            writePoolObject(method.getDeclaringClass());
            writePoolObject(method.getName());
            writePoolObject(method.getSignature());
            writeInt(method.getModifiers());
            writeBytes(method.getCode());
        } else if (object instanceof ResolvedJavaField) {
            writeByte(POOL_FIELD);
            ResolvedJavaField field = ((ResolvedJavaField) object);
            writePoolObject(field.getDeclaringClass());
            writePoolObject(field.getName());
            writePoolObject(field.getType().getName());
            writeInt(field.getModifiers());
        } else if (object instanceof Signature) {
            writeByte(POOL_SIGNATURE);
            Signature signature = ((Signature) object);
            int args = signature.getParameterCount(false);
            writeShort((char) args);
            for (int i = 0; i < args; i++) {
                writePoolObject(signature.getParameterType(i, null).getName());
            }
            writePoolObject(signature.getReturnType(null).getName());
        } else {
            writeByte(POOL_STRING);
            writeString(object.toString());
        }
    }

    private void writeEdgesInfo(NodeClass<?> nodeClass, Edges.Type type) throws IOException {
        Edges edges = nodeClass.getEdges(type);
        writeShort((char) edges.getCount());
        for (int i = 0; i < edges.getCount(); i++) {
            writeByte(i < edges.getDirectCount() ? 0 : 1);
            writePoolObject(edges.getName(i));
            if (type == Inputs) {
                writePoolObject(((InputEdges) edges).getInputType(i));
            }
        }
    }

    private void writePropertyObject(Object obj) throws IOException {
        if (obj instanceof Integer) {
            writeByte(PROPERTY_INT);
            writeInt(((Integer) obj).intValue());
        } else if (obj instanceof Long) {
            writeByte(PROPERTY_LONG);
            writeLong(((Long) obj).longValue());
        } else if (obj instanceof Double) {
            writeByte(PROPERTY_DOUBLE);
            writeDouble(((Double) obj).doubleValue());
        } else if (obj instanceof Float) {
            writeByte(PROPERTY_FLOAT);
            writeFloat(((Float) obj).floatValue());
        } else if (obj instanceof Boolean) {
            if (((Boolean) obj).booleanValue()) {
                writeByte(PROPERTY_TRUE);
            } else {
                writeByte(PROPERTY_FALSE);
            }
        } else if (obj instanceof Graph) {
            writeByte(PROPERTY_SUBGRAPH);
            writeGraph((Graph) obj, null);
        } else if (obj instanceof CachedGraph) {
            writeByte(PROPERTY_SUBGRAPH);
            writeGraph(((CachedGraph<?>) obj).getReadonlyCopy(), null);
        } else if (obj != null && obj.getClass().isArray()) {
            Class<?> componentType = obj.getClass().getComponentType();
            if (componentType.isPrimitive()) {
                if (componentType == Double.TYPE) {
                    writeByte(PROPERTY_ARRAY);
                    writeByte(PROPERTY_DOUBLE);
                    writeDoubles((double[]) obj);
                } else if (componentType == Integer.TYPE) {
                    writeByte(PROPERTY_ARRAY);
                    writeByte(PROPERTY_INT);
                    writeInts((int[]) obj);
                } else {
                    writeByte(PROPERTY_POOL);
                    writePoolObject(obj);
                }
            } else {
                writeByte(PROPERTY_ARRAY);
                writeByte(PROPERTY_POOL);
                Object[] array = (Object[]) obj;
                writeInt(array.length);
                for (Object o : array) {
                    writePoolObject(o);
                }
            }
        } else {
            writeByte(PROPERTY_POOL);
            writePoolObject(obj);
        }
    }

    @SuppressWarnings("deprecation")
    private static int getNodeId(Node node) {
        return node.getId();
    }

    private Object getBlockForNode(Node node, NodeMap<Block> nodeToBlocks) {
        if (nodeToBlocks.isNew(node)) {
            return "NEW (not in schedule)";
        } else {
            Block block = nodeToBlocks.get(node);
            if (block != null) {
                return block.getId();
            } else if (node instanceof PhiNode) {
                return getBlockForNode(((PhiNode) node).merge(), nodeToBlocks);
            }
        }
        return null;
    }

    private void writeNodes(Graph graph, NodeMap<Block> nodeToBlocks, ControlFlowGraph cfg) throws IOException {
        Map<Object, Object> props = new HashMap<>();

        writeInt(graph.getNodeCount());

        for (Node node : graph.getNodes()) {
            NodeClass<?> nodeClass = node.getNodeClass();
            node.getDebugProperties(props);
            if (cfg != null && Options.PrintGraphProbabilities.getValue() && node instanceof FixedNode) {
                try {
                    props.put("probability", cfg.blockFor(node).probability());
                } catch (Throwable t) {
                    props.put("probability", 0.0);
                    props.put("probability-exception", t);
                }
            }
            if (nodeToBlocks != null) {
                Object block = getBlockForNode(node, nodeToBlocks);
                if (block != null) {
                    props.put("node-to-block", block);
                }
            }

            if (node instanceof ControlSinkNode) {
                props.put("category", "controlSink");
            } else if (node instanceof ControlSplitNode) {
                props.put("category", "controlSplit");
            } else if (node instanceof AbstractMergeNode) {
                props.put("category", "merge");
            } else if (node instanceof AbstractBeginNode) {
                props.put("category", "begin");
            } else if (node instanceof AbstractEndNode) {
                props.put("category", "end");
            } else if (node instanceof FixedNode) {
                props.put("category", "fixed");
            } else if (node instanceof VirtualState) {
                props.put("category", "state");
            } else if (node instanceof PhiNode) {
                props.put("category", "phi");
            } else if (node instanceof ProxyNode) {
                props.put("category", "proxy");
            } else {
                if (node instanceof ConstantNode) {
                    ConstantNode cn = (ConstantNode) node;
                    updateStringPropertiesForConstant(props, cn);
                }
                props.put("category", "floating");
            }

            writeInt(getNodeId(node));
            writePoolObject(nodeClass);
            writeByte(node.predecessor() == null ? 0 : 1);
            writeProperties(props);
            writeEdges(node, Inputs);
            writeEdges(node, Successors);

            props.clear();
        }
    }

    private void writeProperties(Map<Object, Object> props) throws IOException {
        if (props == null) {
            writeShort((char) 0);
            return;
        }
        // properties
        writeShort((char) props.size());
        for (Entry<Object, Object> entry : props.entrySet()) {
            String key = entry.getKey().toString();
            writePoolObject(key);
            writePropertyObject(entry.getValue());
        }
    }

    private void writeEdges(Node node, Edges.Type type) throws IOException {
        NodeClass<?> nodeClass = node.getNodeClass();
        Edges edges = nodeClass.getEdges(type);
        final long[] curOffsets = edges.getOffsets();
        for (int i = 0; i < edges.getDirectCount(); i++) {
            writeNodeRef(Edges.getNode(node, curOffsets, i));
        }
        for (int i = edges.getDirectCount(); i < edges.getCount(); i++) {
            NodeList<Node> list = Edges.getNodeList(node, curOffsets, i);
            if (list == null) {
                writeShort((char) 0);
            } else {
                int listSize = list.count();
                assert listSize == ((char) listSize);
                writeShort((char) listSize);
                for (Node edge : list) {
                    writeNodeRef(edge);
                }
            }
        }
    }

    private void writeNodeRef(Node edge) throws IOException {
        if (edge != null) {
            writeInt(getNodeId(edge));
        } else {
            writeInt(-1);
        }
    }

    private void writeBlocks(List<Block> blocks, BlockMap<List<Node>> blockToNodes) throws IOException {
        if (blocks != null && blockToNodes != null) {
            for (Block block : blocks) {
                List<Node> nodes = blockToNodes.get(block);
                if (nodes == null) {
                    writeInt(0);
                    return;
                }
            }
            writeInt(blocks.size());
            for (Block block : blocks) {
                List<Node> nodes = blockToNodes.get(block);
                List<Node> extraNodes = new LinkedList<>();
                writeInt(block.getId());
                for (Node node : nodes) {
                    if (node instanceof AbstractMergeNode) {
                        AbstractMergeNode merge = (AbstractMergeNode) node;
                        for (PhiNode phi : merge.phis()) {
                            if (!nodes.contains(phi)) {
                                extraNodes.add(phi);
                            }
                        }
                    }
                }
                writeInt(nodes.size() + extraNodes.size());
                for (Node node : nodes) {
                    writeInt(getNodeId(node));
                }
                for (Node node : extraNodes) {
                    writeInt(getNodeId(node));
                }
                writeInt(block.getSuccessors().length);
                for (Block sux : block.getSuccessors()) {
                    writeInt(sux.getId());
                }
            }
        } else {
            writeInt(0);
        }
    }

    @Override
    public void beginGroup(String name, String shortName, ResolvedJavaMethod method, int bci, Map<Object, Object> properties) throws IOException {
        writeByte(BEGIN_GROUP);
        writePoolObject(name);
        writePoolObject(shortName);
        writePoolObject(method);
        writeInt(bci);
        writeProperties(properties);
    }

    @Override
    public void endGroup() throws IOException {
        writeByte(CLOSE_GROUP);
    }

    @Override
    public void close() {
        try {
            flush();
            channel.close();
        } catch (IOException ex) {
            throw new Error(ex);
        }
    }
}
