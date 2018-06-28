/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.graphio;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

abstract class GraphProtocol<Graph, Node, NodeClass, Edges, Block, ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition, Location> implements Closeable {
    private static final Charset UTF8 = Charset.forName("UTF-8");

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
    private static final int POOL_NODE_SOURCE_POSITION = 0x09;
    private static final int POOL_NODE = 0x0a;

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

    private static final byte[] MAGIC_BYTES = {'B', 'I', 'G', 'V'};

    private final ConstantPool constantPool;
    private final ByteBuffer buffer;
    private final WritableByteChannel channel;
    final int versionMajor;
    final int versionMinor;

    GraphProtocol(WritableByteChannel channel, int major, int minor) throws IOException {
        if (major > 6 || (major == 6 && minor > 0)) {
            throw new IllegalArgumentException("Unrecognized version " + major + "." + minor);
        }
        this.versionMajor = major;
        this.versionMinor = minor;
        this.constantPool = new ConstantPool();
        this.buffer = ByteBuffer.allocateDirect(256 * 1024);
        this.channel = channel;
        writeVersion();
    }

    GraphProtocol(GraphProtocol<?, ?, ?, ?, ?, ?, ?, ?, ?, ?> parent) {
        this.versionMajor = parent.versionMajor;
        this.versionMinor = parent.versionMinor;
        this.constantPool = parent.constantPool;
        this.buffer = parent.buffer;
        this.channel = parent.channel;
    }

    @SuppressWarnings("all")
    public final void print(Graph graph, Map<? extends Object, ? extends Object> properties, int id, String format, Object... args) throws IOException {
        writeByte(BEGIN_GRAPH);
        if (versionMajor >= 3) {
            writeInt(id);
            writeString(format);
            writeInt(args.length);
            for (Object a : args) {
                writePropertyObject(graph, a);
            }
        } else {
            writePoolObject(formatTitle(graph, id, format, args));
        }
        writeGraph(graph, properties);
        flush();
    }

    public final void beginGroup(Graph noGraph, String name, String shortName, ResolvedJavaMethod method, int bci, Map<? extends Object, ? extends Object> properties) throws IOException {
        writeByte(BEGIN_GROUP);
        writePoolObject(name);
        writePoolObject(shortName);
        writePoolObject(method);
        writeInt(bci);
        writeProperties(noGraph, properties);
    }

    public final void endGroup() throws IOException {
        writeByte(CLOSE_GROUP);
    }

    @Override
    public final void close() {
        try {
            flush();
            channel.close();
        } catch (IOException ex) {
            throw new Error(ex);
        }
    }

    protected abstract Graph findGraph(Graph current, Object obj);

    protected abstract ResolvedJavaMethod findMethod(Object obj);

    /**
     * Attempts to recognize the provided object as a node. Used to encode it with
     * {@link #POOL_NODE} pool type.
     *
     * @param obj any object
     * @return <code>null</code> if it is not a node object, non-null otherwise
     */
    protected abstract Node findNode(Object obj);

    /**
     * Determines whether the provided object is node class or not.
     *
     * @param obj object to check
     * @return {@code null} if {@code obj} does not represent a NodeClass otherwise the NodeClass
     *         represented by {@code obj}
     */
    protected abstract NodeClass findNodeClass(Object obj);

    /**
     * Returns the NodeClass for a given Node {@code obj}.
     *
     * @param obj instance of node
     * @return non-{@code null} instance of the node's class object
     */
    protected abstract NodeClass findClassForNode(Node obj);

    /**
     * Find a Java class. The returned object must be acceptable by
     * {@link #findJavaTypeName(java.lang.Object)} and return valid name for the class.
     *
     * @param clazz node class object
     * @return object representing the class, for example {@link Class}
     */
    protected abstract Object findJavaClass(NodeClass clazz);

    protected abstract Object findEnumClass(Object enumValue);

    protected abstract String findNameTemplate(NodeClass clazz);

    protected abstract Edges findClassEdges(NodeClass nodeClass, boolean dumpInputs);

    protected abstract int findNodeId(Node n);

    protected abstract void findExtraNodes(Node node, Collection<? super Node> extraNodes);

    protected abstract boolean hasPredecessor(Node node);

    protected abstract int findNodesCount(Graph info);

    protected abstract Iterable<? extends Node> findNodes(Graph info);

    protected abstract void findNodeProperties(Node node, Map<String, Object> props, Graph info);

    protected abstract Collection<? extends Node> findBlockNodes(Graph info, Block block);

    protected abstract int findBlockId(Block sux);

    protected abstract Collection<? extends Block> findBlocks(Graph graph);

    protected abstract Collection<? extends Block> findBlockSuccessors(Block block);

    protected abstract String formatTitle(Graph graph, int id, String format, Object... args);

    protected abstract int findSize(Edges edges);

    protected abstract boolean isDirect(Edges edges, int i);

    protected abstract String findName(Edges edges, int i);

    protected abstract Object findType(Edges edges, int i);

    protected abstract Collection<? extends Node> findNodes(Graph graph, Node node, Edges edges, int i);

    protected abstract int findEnumOrdinal(Object obj);

    protected abstract String[] findEnumTypeValues(Object clazz);

    protected abstract String findJavaTypeName(Object obj);

    protected abstract byte[] findMethodCode(ResolvedJavaMethod method);

    protected abstract int findMethodModifiers(ResolvedJavaMethod method);

    protected abstract Signature findMethodSignature(ResolvedJavaMethod method);

    protected abstract String findMethodName(ResolvedJavaMethod method);

    protected abstract Object findMethodDeclaringClass(ResolvedJavaMethod method);

    protected abstract int findFieldModifiers(ResolvedJavaField field);

    protected abstract String findFieldTypeName(ResolvedJavaField field);

    protected abstract String findFieldName(ResolvedJavaField field);

    protected abstract Object findFieldDeclaringClass(ResolvedJavaField field);

    protected abstract ResolvedJavaField findJavaField(Object object);

    protected abstract Signature findSignature(Object object);

    protected abstract int findSignatureParameterCount(Signature signature);

    protected abstract String findSignatureParameterTypeName(Signature signature, int index);

    protected abstract String findSignatureReturnTypeName(Signature signature);

    protected abstract NodeSourcePosition findNodeSourcePosition(Object object);

    protected abstract ResolvedJavaMethod findNodeSourcePositionMethod(NodeSourcePosition pos);

    protected abstract NodeSourcePosition findNodeSourcePositionCaller(NodeSourcePosition pos);

    protected abstract int findNodeSourcePositionBCI(NodeSourcePosition pos);

    protected abstract Iterable<Location> findLocation(ResolvedJavaMethod method, int bci, NodeSourcePosition pos);

    protected abstract String findLocationFile(Location loc) throws IOException;

    protected abstract int findLocationLine(Location loc);

    protected abstract URI findLocationURI(Location loc) throws URISyntaxException;

    protected abstract String findLocationLanguage(Location loc);

    protected abstract int findLocationStart(Location loc);

    protected abstract int findLocationEnd(Location loc);

    private void writeVersion() throws IOException {
        writeBytesRaw(MAGIC_BYTES);
        writeByte(versionMajor);
        writeByte(versionMinor);
    }

    private void flush() throws IOException {
        buffer.flip();
        /*
         * Try not to let interrupted threads abort the write. There's still a race here but an
         * interrupt that's been pending for a long time shouldn't stop this writing.
         */
        boolean interrupted = Thread.interrupted();
        try {
            channel.write(buffer);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
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
        byte[] bytes = str.getBytes(UTF8);
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

    private void writePoolObject(Object obj) throws IOException {
        Object object = obj;
        if (object == null) {
            writeByte(POOL_NULL);
            return;
        }
        Character id = constantPool.get(object);
        if (id == null) {
            addPoolEntry(object);
        } else {
            int type = findPoolType(object, null);
            writeByte(type);
            writeShort(id.charValue());
        }
    }

    private int findPoolType(Object obj, Object[] found) throws IOException {
        Object object = obj;
        if (object == null) {
            return POOL_NULL;
        }
        if (isFound(findJavaField(object), found)) {
            return POOL_FIELD;
        } else if (isFound(findSignature(object), found)) {
            return POOL_SIGNATURE;
        } else if (versionMajor >= 4 && isFound(findNodeSourcePosition(object), found)) {
            return POOL_NODE_SOURCE_POSITION;
        } else {
            final Node node = findNode(object);
            if (versionMajor == 4 && node != null) {
                object = classForNode(node);
            }
            if (isFound(findNodeClass(object), found)) {
                return POOL_NODE_CLASS;
            } else if (versionMajor >= 5 && isFound(node, found)) {
                return POOL_NODE;
            } else if (isFound(findMethod(object), found)) {
                return POOL_METHOD;
            } else if (object instanceof Enum<?>) {
                if (found != null) {
                    found[0] = ((Enum<?>) object).ordinal();
                }
                return POOL_ENUM;
            } else {
                int val = findEnumOrdinal(object);
                if (val >= 0) {
                    if (found != null) {
                        found[0] = val;
                    }
                    return POOL_ENUM;
                } else if (object instanceof Class<?>) {
                    if (found != null) {
                        found[0] = ((Class<?>) object).getName();
                    }
                    return POOL_CLASS;
                } else if (isFound(findJavaTypeName(object), found)) {
                    return POOL_CLASS;
                } else {
                    return POOL_STRING;
                }
            }
        }
    }

    private void writeGraph(Graph graph, Map<? extends Object, ? extends Object> properties) throws IOException {
        writeProperties(graph, properties);
        writeNodes(graph);
        writeBlocks(findBlocks(graph), graph);
    }

    private void writeNodes(Graph info) throws IOException {
        Map<String, Object> props = new HashMap<>();

        final int size = findNodesCount(info);
        writeInt(size);
        int cnt = 0;
        for (Node node : findNodes(info)) {
            NodeClass nodeClass = classForNode(node);
            findNodeProperties(node, props, info);

            writeInt(findNodeId(node));
            writePoolObject(nodeClass);
            writeByte(hasPredecessor(node) ? 1 : 0);
            writeProperties(info, props);
            writeEdges(info, node, true);
            writeEdges(info, node, false);

            props.clear();
            cnt++;
        }
        if (size != cnt) {
            throw new IOException("Expecting " + size + " nodes, but found " + cnt);
        }
    }

    private void writeEdges(Graph graph, Node node, boolean dumpInputs) throws IOException {
        NodeClass clazz = classForNode(node);
        Edges edges = findClassEdges(clazz, dumpInputs);
        int size = findSize(edges);
        for (int i = 0; i < size; i++) {
            Collection<? extends Node> list = findNodes(graph, node, edges, i);
            if (isDirect(edges, i)) {
                if (list != null && list.size() != 1) {
                    throw new IOException("Edge " + i + " in " + edges + " is direct, but list isn't singleton: " + list);
                }
                Node n = null;
                if (list != null && !list.isEmpty()) {
                    n = list.iterator().next();
                }
                writeNodeRef(n);
            } else {
                if (list == null) {
                    writeShort((char) 0);
                } else {
                    int listSize = list.size();
                    assert listSize == ((char) listSize);
                    writeShort((char) listSize);
                    for (Node edge : list) {
                        writeNodeRef(edge);
                    }
                }
            }
        }
    }

    private NodeClass classForNode(Node node) throws IOException {
        NodeClass clazz = findClassForNode(node);
        if (clazz == null) {
            throw new IOException("No class for " + node);
        }
        return clazz;
    }

    private void writeNodeRef(Node node) throws IOException {
        writeInt(findNodeId(node));
    }

    private void writeBlocks(Collection<? extends Block> blocks, Graph info) throws IOException {
        if (blocks != null) {
            for (Block block : blocks) {
                Collection<? extends Node> nodes = findBlockNodes(info, block);
                if (nodes == null) {
                    writeInt(0);
                    return;
                }
            }
            writeInt(blocks.size());
            for (Block block : blocks) {
                Collection<? extends Node> nodes = findBlockNodes(info, block);
                writeInt(findBlockId(block));
                writeInt(nodes.size());
                for (Node node : nodes) {
                    writeInt(findNodeId(node));
                }
                final Collection<? extends Block> successors = findBlockSuccessors(block);
                writeInt(successors.size());
                for (Block sux : successors) {
                    writeInt(findBlockId(sux));
                }
            }
        } else {
            writeInt(0);
        }
    }

    private void writeEdgesInfo(NodeClass nodeClass, boolean dumpInputs) throws IOException {
        Edges edges = findClassEdges(nodeClass, dumpInputs);
        int size = findSize(edges);
        writeShort((char) size);
        for (int i = 0; i < size; i++) {
            writeByte(isDirect(edges, i) ? 0 : 1);
            writePoolObject(findName(edges, i));
            if (dumpInputs) {
                writePoolObject(findType(edges, i));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addPoolEntry(Object obj) throws IOException {
        Object object = obj;
        char index = constantPool.add(object);
        writeByte(POOL_NEW);
        writeShort(index);

        Object[] found = {null};
        int type = findPoolType(object, found);
        writeByte(type);
        switch (type) {
            case POOL_FIELD: {
                ResolvedJavaField field = (ResolvedJavaField) found[0];
                Objects.nonNull(field);
                writePoolObject(findFieldDeclaringClass(field));
                writePoolObject(findFieldName(field));
                writePoolObject(findFieldTypeName(field));
                writeInt(findFieldModifiers(field));
                break;
            }
            case POOL_SIGNATURE: {
                Signature signature = (Signature) found[0];
                int args = findSignatureParameterCount(signature);
                writeShort((char) args);
                for (int i = 0; i < args; i++) {
                    writePoolObject(findSignatureParameterTypeName(signature, i));
                }
                writePoolObject(findSignatureReturnTypeName(signature));
                break;
            }
            case POOL_NODE_SOURCE_POSITION: {
                NodeSourcePosition pos = (NodeSourcePosition) found[0];
                Objects.nonNull(pos);
                ResolvedJavaMethod method = findNodeSourcePositionMethod(pos);
                writePoolObject(method);
                final int bci = findNodeSourcePositionBCI(pos);
                writeInt(bci);
                Iterator<Location> ste = findLocation(method, bci, pos).iterator();
                if (versionMajor >= 6) {
                    while (ste.hasNext()) {
                        Location loc = ste.next();
                        URI uri;
                        try {
                            uri = findLocationURI(loc);
                        } catch (URISyntaxException ex) {
                            throw new IOException(ex);
                        }
                        if (uri == null) {
                            continue;
                        }
                        String l = findLocationLanguage(loc);
                        if (l == null) {
                            continue;
                        }
                        writePoolObject(uri.toString());
                        writeString(l);
                        writeInt(findLocationLine(loc));
                        writeInt(findLocationStart(loc));
                        writeInt(findLocationEnd(loc));
                    }
                    writePoolObject(null);
                } else {
                    Location first = ste.hasNext() ? ste.next() : null;
                    String fileName = first != null ? findLocationFile(first) : null;
                    if (fileName != null) {
                        writePoolObject(fileName);
                        writeInt(findLocationLine(first));
                    } else {
                        writePoolObject(null);
                    }
                }
                writePoolObject(findNodeSourcePositionCaller(pos));
                break;
            }
            case POOL_NODE: {
                Node node = (Node) found[0];
                Objects.nonNull(node);
                writeInt(findNodeId(node));
                writePoolObject(classForNode(node));
                break;
            }
            case POOL_NODE_CLASS: {
                NodeClass nodeClass = (NodeClass) found[0];
                final Object clazz = findJavaClass(nodeClass);
                if (versionMajor >= 3) {
                    writePoolObject(clazz);
                    writeString(findNameTemplate(nodeClass));
                } else {
                    writeString(((Class<?>) clazz).getSimpleName());
                    String nameTemplate = findNameTemplate(nodeClass);
                    writeString(nameTemplate);
                }
                writeEdgesInfo(nodeClass, true);
                writeEdgesInfo(nodeClass, false);
                break;
            }
            case POOL_CLASS: {
                String typeName = (String) found[0];
                Objects.nonNull(typeName);
                writeString(typeName);
                String[] enumValueNames = findEnumTypeValues(object);
                if (enumValueNames != null) {
                    writeByte(ENUM_KLASS);
                    writeInt(enumValueNames.length);
                    for (String o : enumValueNames) {
                        writePoolObject(o);
                    }
                } else {
                    writeByte(KLASS);
                }
                break;
            }
            case POOL_METHOD: {
                ResolvedJavaMethod method = (ResolvedJavaMethod) found[0];
                Objects.nonNull(method);
                writePoolObject(findMethodDeclaringClass(method));
                writePoolObject(findMethodName(method));
                final Signature methodSignature = findMethodSignature(method);
                if (findSignature(methodSignature) == null) {
                    throw new IOException("Should be recognized as signature: " + methodSignature + " for " + method);
                }
                writePoolObject(methodSignature);
                writeInt(findMethodModifiers(method));
                writeBytes(findMethodCode(method));
                break;
            }
            case POOL_ENUM: {
                int enumOrdinal = (int) found[0];
                writePoolObject(findEnumClass(object));
                writeInt(enumOrdinal);
                break;
            }
            case POOL_STRING: {
                writeString(object.toString());
                break;
            }
            default:
                throw new IllegalStateException();
        }
    }

    private void writePropertyObject(Graph graph, Object obj) throws IOException {
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
            Graph g = findGraph(graph, obj);
            if (g == null) {
                writeByte(PROPERTY_POOL);
                writePoolObject(obj);
            } else {
                writeByte(PROPERTY_SUBGRAPH);
                writeGraph(g, null);
            }
        }
    }

    private void writeProperties(Graph graph, Map<? extends Object, ? extends Object> props) throws IOException {
        if (props == null) {
            writeShort((char) 0);
            return;
        }
        final int size = props.size();
        // properties
        writeShort((char) size);
        int cnt = 0;
        for (Map.Entry<? extends Object, ? extends Object> entry : props.entrySet()) {
            String key = entry.getKey().toString();
            writePoolObject(key);
            writePropertyObject(graph, entry.getValue());
            cnt++;
        }
        if (size != cnt) {
            throw new IOException("Expecting " + size + " properties, but found only " + cnt);
        }
    }

    private static boolean isFound(Object obj, Object[] found) {
        if (obj == null) {
            return false;
        }
        if (found != null) {
            found[0] = obj;
        }
        return true;
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

}
