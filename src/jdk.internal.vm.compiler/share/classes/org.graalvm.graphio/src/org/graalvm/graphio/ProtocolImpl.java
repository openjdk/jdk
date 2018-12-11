/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.Map;

final class ProtocolImpl<Graph, Node, NodeClass, Port, Block, ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition, Location>
                extends GraphProtocol<Graph, Node, NodeClass, Port, Block, ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition, Location> {
    private final GraphStructure<Graph, Node, NodeClass, Port> structure;
    private final GraphTypes types;
    private final GraphBlocks<Graph, Block, Node> blocks;
    private final GraphElements<ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition> elements;
    private final GraphLocations<ResolvedJavaMethod, NodeSourcePosition, Location> locations;

    ProtocolImpl(int major, int minor, GraphStructure<Graph, Node, NodeClass, Port> structure, GraphTypes enums, GraphBlocks<Graph, Block, Node> blocks,
                    GraphElements<ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition> elements,
                    GraphLocations<ResolvedJavaMethod, NodeSourcePosition, Location> locs,
                    WritableByteChannel channel) throws IOException {
        super(channel, major, minor);
        this.structure = structure;
        this.types = enums;
        this.blocks = blocks;
        this.elements = elements;
        this.locations = locs;
    }

    ProtocolImpl(GraphProtocol<?, ?, ?, ?, ?, ?, ?, ?, ?, ?> parent, GraphStructure<Graph, Node, NodeClass, Port> structure, GraphTypes enums, GraphBlocks<Graph, Block, Node> blocks,
                    GraphElements<ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition> elements,
                    GraphLocations<ResolvedJavaMethod, NodeSourcePosition, Location> locs) {
        super(parent);
        this.structure = structure;
        this.types = enums;
        this.blocks = blocks;
        this.elements = elements;
        this.locations = locs;
    }

    @Override
    protected Graph findGraph(Graph current, Object obj) {
        return structure.graph(current, obj);
    }

    @Override
    protected Node findNode(Object obj) {
        return structure.node(obj);
    }

    @Override
    protected NodeClass findNodeClass(Object obj) {
        return structure.nodeClass(obj);
    }

    @Override
    protected NodeClass findClassForNode(Node obj) {
        NodeClass clazz = structure.classForNode(obj);
        if (clazz != null && (findNodeClass(clazz) == null || findNode(clazz) != null)) {
            throw new IllegalStateException("classForNode method shall return node class representation rather than node: " + clazz);
        }
        return clazz;
    }

    @Override
    protected String findNameTemplate(NodeClass clazz) {
        return structure.nameTemplate(clazz);
    }

    @Override
    protected int findNodeId(Node n) {
        return structure.nodeId(n);
    }

    @Override
    protected boolean hasPredecessor(Node node) {
        return structure.nodeHasPredecessor(node);
    }

    @Override
    protected int findNodesCount(Graph info) {
        return structure.nodesCount(info);
    }

    @Override
    protected Iterable<? extends Node> findNodes(Graph info) {
        return structure.nodes(info);
    }

    @Override
    protected void findNodeProperties(Node node, Map<String, Object> props, Graph info) {
        structure.nodeProperties(info, node, props);
    }

    @Override
    protected Port findClassEdges(NodeClass nodeClass, boolean dumpInputs) {
        if (dumpInputs) {
            return structure.portInputs(nodeClass);
        } else {
            return structure.portOutputs(nodeClass);
        }
    }

    @Override
    protected int findSize(Port edges) {
        return structure.portSize(edges);
    }

    @Override
    protected boolean isDirect(Port edges, int i) {
        return structure.edgeDirect(edges, i);
    }

    @Override
    protected String findName(Port edges, int i) {
        return structure.edgeName(edges, i);
    }

    @Override
    protected Object findType(Port edges, int i) {
        Object type = structure.edgeType(edges, i);
        if (findEnumOrdinal(type) < 0) {
            throw new IllegalStateException("edgeType method shall return an enum! Was: " + type);
        }
        return type;
    }

    @Override
    protected Collection<? extends Node> findNodes(Graph graph, Node node, Port port, int i) {
        return structure.edgeNodes(graph, node, port, i);
    }

    @Override
    protected Object findJavaClass(NodeClass clazz) {
        final Object type = structure.nodeClassType(clazz);
        if (!(type instanceof Class<?>) && findJavaTypeName(type) == null) {
            throw new IllegalStateException("nodeClassType method shall return a Java class (instance of Class)! Was: " + type);
        }
        return type;
    }

    @Override
    protected Object findEnumClass(Object enumValue) {
        return types.enumClass(enumValue);
    }

    @Override
    protected int findEnumOrdinal(Object obj) {
        return types.enumOrdinal(obj);
    }

    @Override
    protected String[] findEnumTypeValues(Object clazz) {
        return types.enumTypeValues(clazz);
    }

    @Override
    protected String findJavaTypeName(Object obj) {
        return types.typeName(obj);
    }

    @Override
    protected Collection<? extends Node> findBlockNodes(Graph info, Block block) {
        return blocks.blockNodes(info, block);
    }

    @Override
    protected int findBlockId(Block block) {
        return blocks.blockId(block);
    }

    @Override
    protected Collection<? extends Block> findBlocks(Graph graph) {
        return blocks.blocks(graph);
    }

    @Override
    protected Collection<? extends Block> findBlockSuccessors(Block block) {
        return blocks.blockSuccessors(block);
    }

    @Override
    protected ResolvedJavaMethod findMethod(Object obj) {
        return elements == null ? null : elements.method(obj);
    }

    @Override
    protected byte[] findMethodCode(ResolvedJavaMethod method) {
        return elements.methodCode(method);
    }

    @Override
    protected int findMethodModifiers(ResolvedJavaMethod method) {
        return elements.methodModifiers(method);
    }

    @Override
    protected Signature findMethodSignature(ResolvedJavaMethod method) {
        return elements.methodSignature(method);
    }

    @Override
    protected String findMethodName(ResolvedJavaMethod method) {
        return elements.methodName(method);
    }

    @Override
    protected Object findMethodDeclaringClass(ResolvedJavaMethod method) {
        return elements.methodDeclaringClass(method);
    }

    @Override
    protected int findFieldModifiers(ResolvedJavaField field) {
        return elements.fieldModifiers(field);
    }

    @Override
    protected String findFieldTypeName(ResolvedJavaField field) {
        return elements.fieldTypeName(field);
    }

    @Override
    protected String findFieldName(ResolvedJavaField field) {
        return elements.fieldName(field);
    }

    @Override
    protected Object findFieldDeclaringClass(ResolvedJavaField field) {
        return elements.fieldDeclaringClass(field);
    }

    @Override
    protected ResolvedJavaField findJavaField(Object object) {
        return elements == null ? null : elements.field(object);
    }

    @Override
    protected Signature findSignature(Object object) {
        return elements == null ? null : elements.signature(object);
    }

    @Override
    protected int findSignatureParameterCount(Signature signature) {
        return elements.signatureParameterCount(signature);
    }

    @Override
    protected String findSignatureParameterTypeName(Signature signature, int index) {
        return elements.signatureParameterTypeName(signature, index);
    }

    @Override
    protected String findSignatureReturnTypeName(Signature signature) {
        return elements.signatureReturnTypeName(signature);
    }

    @Override
    protected NodeSourcePosition findNodeSourcePosition(Object object) {
        return elements == null ? null : elements.nodeSourcePosition(object);
    }

    @Override
    protected ResolvedJavaMethod findNodeSourcePositionMethod(NodeSourcePosition pos) {
        return elements.nodeSourcePositionMethod(pos);
    }

    @Override
    protected NodeSourcePosition findNodeSourcePositionCaller(NodeSourcePosition pos) {
        return elements.nodeSourcePositionCaller(pos);
    }

    @Override
    protected int findNodeSourcePositionBCI(NodeSourcePosition pos) {
        return elements.nodeSourcePositionBCI(pos);
    }

    @Override
    protected Iterable<Location> findLocation(ResolvedJavaMethod method, int bci, NodeSourcePosition pos) {
        return locations.methodLocation(method, bci, pos);
    }

    @Override
    protected String findLocationFile(Location loc) throws IOException {
        if (loc == null) {
            return null;
        }
        URI u;
        try {
            u = locations.locationURI(loc);
        } catch (URISyntaxException ex) {
            throw new IOException(ex);
        }
        if (u == null) {
            return null;
        }
        if (u.getScheme() == null) {
            return u.getPath();
        }
        if ("file".equals(u.getScheme())) {
            return new File(u).getPath();
        }
        return null;
    }

    @Override
    protected int findLocationLine(Location loc) {
        return locations.locationLineNumber(loc);
    }

    @Override
    protected URI findLocationURI(Location loc) throws URISyntaxException {
        return locations.locationURI(loc);
    }

    @Override
    protected String findLocationLanguage(Location loc) {
        return locations.locationLanguage(loc);
    }

    @Override
    protected int findLocationStart(Location loc) {
        return locations.locationOffsetStart(loc);
    }

    @Override
    protected int findLocationEnd(Location loc) {
        return locations.locationOffsetEnd(loc);
    }

    @Override
    protected void findExtraNodes(Node node, Collection<? super Node> extraNodes) {
    }

    @Override
    protected String formatTitle(Graph graph, int id, String format, Object... args) {
        return String.format(format, args) + " [" + id + "]";
    }
}
