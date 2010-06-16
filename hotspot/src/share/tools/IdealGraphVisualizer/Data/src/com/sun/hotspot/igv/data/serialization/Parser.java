/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package com.sun.hotspot.igv.data.serialization;

import com.sun.hotspot.igv.data.GraphDocument;
import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputEdge;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputMethod;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.Property;
import com.sun.hotspot.igv.data.services.GroupCallback;
import com.sun.hotspot.igv.data.serialization.XMLParser.ElementHandler;
import com.sun.hotspot.igv.data.serialization.XMLParser.HandoverElementHandler;
import com.sun.hotspot.igv.data.serialization.XMLParser.ParseMonitor;
import com.sun.hotspot.igv.data.serialization.XMLParser.TopElementHandler;
import java.io.IOException;
import java.util.HashMap;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Parser {

    public static final String INDENT = "  ";
    public static final String TOP_ELEMENT = "graphDocument";
    public static final String GROUP_ELEMENT = "group";
    public static final String GRAPH_ELEMENT = "graph";
    public static final String ROOT_ELEMENT = "graphDocument";
    public static final String PROPERTIES_ELEMENT = "properties";
    public static final String EDGES_ELEMENT = "edges";
    public static final String PROPERTY_ELEMENT = "p";
    public static final String EDGE_ELEMENT = "edge";
    public static final String NODE_ELEMENT = "node";
    public static final String NODES_ELEMENT = "nodes";
    public static final String REMOVE_EDGE_ELEMENT = "removeEdge";
    public static final String REMOVE_NODE_ELEMENT = "removeNode";
    public static final String METHOD_NAME_PROPERTY = "name";
    public static final String METHOD_IS_PUBLIC_PROPERTY = "public";
    public static final String METHOD_IS_STATIC_PROPERTY = "static";
    public static final String TRUE_VALUE = "true";
    public static final String NODE_NAME_PROPERTY = "name";
    public static final String EDGE_NAME_PROPERTY = "name";
    public static final String NODE_ID_PROPERTY = "id";
    public static final String FROM_PROPERTY = "from";
    public static final String TO_PROPERTY = "to";
    public static final String PROPERTY_NAME_PROPERTY = "name";
    public static final String GRAPH_NAME_PROPERTY = "name";
    public static final String TO_INDEX_PROPERTY = "index";
    public static final String METHOD_ELEMENT = "method";
    public static final String INLINE_ELEMENT = "inline";
    public static final String BYTECODES_ELEMENT = "bytecodes";
    public static final String METHOD_BCI_PROPERTY = "bci";
    public static final String METHOD_SHORT_NAME_PROPERTY = "shortName";
    public static final String CONTROL_FLOW_ELEMENT = "controlFlow";
    public static final String BLOCK_NAME_PROPERTY = "name";
    public static final String BLOCK_ELEMENT = "block";
    public static final String SUCCESSORS_ELEMENT = "successors";
    public static final String SUCCESSOR_ELEMENT = "successor";
    public static final String ASSEMBLY_ELEMENT = "assembly";
    public static final String DIFFERENCE_PROPERTY = "difference";
    private TopElementHandler xmlDocument = new TopElementHandler();
    private boolean difference;
    private GroupCallback groupCallback;
    private HashMap<String, Integer> idCache = new HashMap<String, Integer>();
    private int maxId = 0;

    private int lookupID(String i) {
        Integer id = idCache.get(i);
        if (id == null) {
            id = maxId++;
            idCache.put(i, id);
        }
        return id.intValue();
    }

    // <graphDocument>
    private ElementHandler<GraphDocument, Object> topHandler = new ElementHandler<GraphDocument, Object>(TOP_ELEMENT) {

        @Override
        protected GraphDocument start() throws SAXException {
            return new GraphDocument();
        }
    };
    // <group>
    private ElementHandler<Group, GraphDocument> groupHandler = new XMLParser.ElementHandler<Group, GraphDocument>(GROUP_ELEMENT) {

        @Override
        protected Group start() throws SAXException {
            Group group = new Group();
            Parser.this.difference = false;
            String differenceProperty = this.readAttribute(DIFFERENCE_PROPERTY);
            if (differenceProperty != null && (differenceProperty.equals("1") || differenceProperty.equals("true"))) {
                Parser.this.difference = true;
            }

            ParseMonitor monitor = getMonitor();
            if (monitor != null) {
                monitor.setState(group.getName());
            }

            return group;
        }

        @Override
        protected void end(String text) throws SAXException {
            if (groupCallback == null) {
                getParentObject().addGroup(getObject());
            }
        }
    };
    private HandoverElementHandler<Group> assemblyHandler = new XMLParser.HandoverElementHandler<Group>(ASSEMBLY_ELEMENT, true) {

        @Override
        protected void end(String text) throws SAXException {
            getParentObject().setAssembly(text);
        }
    };
    // <method>
    private ElementHandler<InputMethod, Group> methodHandler = new XMLParser.ElementHandler<InputMethod, Group>(METHOD_ELEMENT) {

        @Override
        protected InputMethod start() throws SAXException {

            InputMethod method = parseMethod(this, getParentObject());
            getParentObject().setMethod(method);
            return method;
        }
    };

    private InputMethod parseMethod(XMLParser.ElementHandler handler, Group group) throws SAXException {
        String s = handler.readRequiredAttribute(METHOD_BCI_PROPERTY);
        int bci = 0;
        try {
            bci = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new SAXException(e);
        }
        InputMethod method = new InputMethod(group, handler.readRequiredAttribute(METHOD_NAME_PROPERTY), handler.readRequiredAttribute(METHOD_SHORT_NAME_PROPERTY), bci);
        return method;
    }
    // <bytecodes>
    private HandoverElementHandler<InputMethod> bytecodesHandler = new XMLParser.HandoverElementHandler<InputMethod>(BYTECODES_ELEMENT, true) {

        @Override
        protected void end(String text) throws SAXException {
            getParentObject().setBytecodes(text);
        }
    };
    // <inlined>
    private HandoverElementHandler<InputMethod> inlinedHandler = new XMLParser.HandoverElementHandler<InputMethod>(INLINE_ELEMENT);
    // <inlined><method>
    private ElementHandler<InputMethod, InputMethod> inlinedMethodHandler = new XMLParser.ElementHandler<InputMethod, InputMethod>(METHOD_ELEMENT) {

        @Override
        protected InputMethod start() throws SAXException {
            InputMethod method = parseMethod(this, getParentObject().getGroup());
            getParentObject().addInlined(method);
            return method;
        }
    };
    // <graph>
    private ElementHandler<InputGraph, Group> graphHandler = new XMLParser.ElementHandler<InputGraph, Group>(GRAPH_ELEMENT) {

        private InputGraph graph;

        @Override
        protected InputGraph start() throws SAXException {

            String name = readAttribute(GRAPH_NAME_PROPERTY);
            InputGraph previous = getParentObject().getLastAdded();
            if (!difference) {
                previous = null;
            }
            InputGraph curGraph = new InputGraph(getParentObject(), previous, name);
            this.graph = curGraph;
            return curGraph;
        }

        @Override
        protected void end(String text) throws SAXException {
            getParentObject().addGraph(graph);
            graph.resolveBlockLinks();
        }
    };
    // <nodes>
    private HandoverElementHandler<InputGraph> nodesHandler = new HandoverElementHandler<InputGraph>(NODES_ELEMENT);
    // <controlFlow>
    private HandoverElementHandler<InputGraph> controlFlowHandler = new HandoverElementHandler<InputGraph>(CONTROL_FLOW_ELEMENT);
    // <block>
    private ElementHandler<InputBlock, InputGraph> blockHandler = new ElementHandler<InputBlock, InputGraph>(BLOCK_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            InputGraph graph = getParentObject();
            String name = readRequiredAttribute(BLOCK_NAME_PROPERTY).intern();
            InputBlock b = new InputBlock(getParentObject(), name);
            graph.addBlock(b);
            return b;
        }
    };
    // <nodes>
    private HandoverElementHandler<InputBlock> blockNodesHandler = new HandoverElementHandler<InputBlock>(NODES_ELEMENT);
    // <node>
    private ElementHandler<InputBlock, InputBlock> blockNodeHandler = new ElementHandler<InputBlock, InputBlock>(NODE_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);

            int id = 0;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            getParentObject().addNode(id);
            return getParentObject();
        }
    };
    // <successors>
    private HandoverElementHandler<InputBlock> successorsHandler = new HandoverElementHandler<InputBlock>(SUCCESSORS_ELEMENT);
    // <successor>
    private ElementHandler<InputBlock, InputBlock> successorHandler = new ElementHandler<InputBlock, InputBlock>(SUCCESSOR_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            String name = readRequiredAttribute(BLOCK_NAME_PROPERTY);
            getParentObject().addSuccessor(name);
            return getParentObject();
        }
    };
    // <node>
    private ElementHandler<InputNode, InputGraph> nodeHandler = new ElementHandler<InputNode, InputGraph>(NODE_ELEMENT) {

        @Override
        protected InputNode start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int id = 0;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            InputNode node = new InputNode(id);
            getParentObject().addNode(node);
            return node;
        }
    };
    // <removeNode>
    private ElementHandler<InputNode, InputGraph> removeNodeHandler = new ElementHandler<InputNode, InputGraph>(REMOVE_NODE_ELEMENT) {

        @Override
        protected InputNode start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int id = 0;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            return getParentObject().removeNode(id);
        }
    };
    // <graph>
    private HandoverElementHandler<InputGraph> edgesHandler = new HandoverElementHandler<InputGraph>(EDGES_ELEMENT);

    // Local class for edge elements
    private class EdgeElementHandler extends ElementHandler<InputEdge, InputGraph> {

        public EdgeElementHandler(String name) {
            super(name);
        }

        @Override
        protected InputEdge start() throws SAXException {
            int toIndex = 0;
            int from = -1;
            int to = -1;

            try {
                String toIndexString = readAttribute(TO_INDEX_PROPERTY);
                if (toIndexString != null) {
                    toIndex = Integer.parseInt(toIndexString);
                }

                from = lookupID(readRequiredAttribute(FROM_PROPERTY));
                to = lookupID(readRequiredAttribute(TO_PROPERTY));
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }


            InputEdge conn = new InputEdge((char) toIndex, from, to);
            return start(conn);
        }

        protected InputEdge start(InputEdge conn) throws SAXException {
            return conn;
        }
    }
    // <edge>
    private EdgeElementHandler edgeHandler = new EdgeElementHandler(EDGE_ELEMENT) {

        @Override
        protected InputEdge start(InputEdge conn) throws SAXException {
            getParentObject().addEdge(conn);
            return conn;
        }
    };
    // <removeEdge>
    private EdgeElementHandler removeEdgeHandler = new EdgeElementHandler(REMOVE_EDGE_ELEMENT) {

        @Override
        protected InputEdge start(InputEdge conn) throws SAXException {
            getParentObject().removeEdge(conn);
            return conn;
        }
    };
    // <properties>
    private HandoverElementHandler<Properties.Provider> propertiesHandler = new HandoverElementHandler<Properties.Provider>(PROPERTIES_ELEMENT);
    // <properties>
    private HandoverElementHandler<Group> groupPropertiesHandler = new HandoverElementHandler<Group>(PROPERTIES_ELEMENT) {

        @Override
        public void end(String text) throws SAXException {
            if (groupCallback != null) {
                groupCallback.started(getParentObject());
            }
        }
    };
    // <property>
    private ElementHandler<String, Properties.Provider> propertyHandler = new XMLParser.ElementHandler<String, Properties.Provider>(PROPERTY_ELEMENT, true) {

        @Override
        public String start() throws SAXException {
            return readRequiredAttribute(PROPERTY_NAME_PROPERTY).intern();
        }

        @Override
        public void end(String text) {
            getParentObject().getProperties().setProperty(getObject(), text.trim().intern());
        }
    };

    public Parser() {
        this(null);
    }

    public Parser(GroupCallback groupCallback) {

        this.groupCallback = groupCallback;

        // Initialize dependencies
        xmlDocument.addChild(topHandler);
        topHandler.addChild(groupHandler);

        groupHandler.addChild(methodHandler);
        groupHandler.addChild(assemblyHandler);
        groupHandler.addChild(graphHandler);

        methodHandler.addChild(inlinedHandler);
        methodHandler.addChild(bytecodesHandler);

        inlinedHandler.addChild(inlinedMethodHandler);
        inlinedMethodHandler.addChild(bytecodesHandler);
        inlinedMethodHandler.addChild(inlinedHandler);

        graphHandler.addChild(nodesHandler);
        graphHandler.addChild(edgesHandler);
        graphHandler.addChild(controlFlowHandler);

        controlFlowHandler.addChild(blockHandler);

        blockHandler.addChild(successorsHandler);
        successorsHandler.addChild(successorHandler);
        blockHandler.addChild(blockNodesHandler);
        blockNodesHandler.addChild(blockNodeHandler);

        nodesHandler.addChild(nodeHandler);
        nodesHandler.addChild(removeNodeHandler);
        edgesHandler.addChild(edgeHandler);
        edgesHandler.addChild(removeEdgeHandler);

        methodHandler.addChild(propertiesHandler);
        inlinedMethodHandler.addChild(propertiesHandler);
        topHandler.addChild(propertiesHandler);
        groupHandler.addChild(groupPropertiesHandler);
        graphHandler.addChild(propertiesHandler);
        nodeHandler.addChild(propertiesHandler);
        propertiesHandler.addChild(propertyHandler);
        groupPropertiesHandler.addChild(propertyHandler);
    }

    // Returns a new GraphDocument object deserialized from an XML input source.
    public GraphDocument parse(XMLReader reader, InputSource source, XMLParser.ParseMonitor monitor) throws SAXException {
        reader.setContentHandler(new XMLParser(xmlDocument, monitor));
        try {
            reader.parse(source);
        } catch (IOException ex) {
            throw new SAXException(ex);
        }

        return topHandler.getObject();
    }
}
