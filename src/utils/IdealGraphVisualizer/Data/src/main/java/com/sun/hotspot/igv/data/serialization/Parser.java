/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.serialization.XMLParser.ElementHandler;
import com.sun.hotspot.igv.data.serialization.XMLParser.HandoverElementHandler;
import com.sun.hotspot.igv.data.serialization.XMLParser.TopElementHandler;
import com.sun.hotspot.igv.data.services.GroupCallback;
import com.sun.hotspot.igv.data.serialization.Printer.SerialData;
import com.sun.hotspot.igv.data.serialization.Printer.GraphContext;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Parser implements GraphParser {

    public static final String TOP_ELEMENT = "graphDocument";
    public static final String GROUP_ELEMENT = "group";
    public static final String GRAPH_ELEMENT = "graph";
    public static final String GRAPH_STATES_ELEMENT = "graphStates";
    public static final String STATE_ELEMENT = "state";
    public static final String STATE_POSITION_DIFFERENCE = "difference";
    public static final String POSITION_DIFFERENCE_PROPERTY = "value";
    public static final String ROOT_ELEMENT = "graphDocument";
    public static final String PROPERTIES_ELEMENT = "properties";
    public static final String EDGES_ELEMENT = "edges";
    public static final String PROPERTY_ELEMENT = "p";
    public static final String EDGE_ELEMENT = "edge";
    public static final String NODE_ELEMENT = "node";
    public static final String NODES_ELEMENT = "nodes";
    public static final String HIDDEN_NODES_ELEMENT = "hiddenNodes";

    public static final String REMOVE_EDGE_ELEMENT = "removeEdge";
    public static final String REMOVE_NODE_ELEMENT = "removeNode";
    public static final String METHOD_NAME_PROPERTY = "name";
    public static final String NODE_ID_PROPERTY = "id";
    public static final String FROM_PROPERTY = "from";
    public static final String TO_PROPERTY = "to";
    public static final String TYPE_PROPERTY = "type";
    public static final String PROPERTY_NAME_PROPERTY = "name";
    public static final String GRAPH_NAME_PROPERTY = "name";
    public static final String FROM_INDEX_PROPERTY = "fromIndex";
    public static final String TO_INDEX_PROPERTY = "toIndex";
    public static final String TO_INDEX_ALT_PROPERTY = "index";
    public static final String LABEL_PROPERTY = "label";
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
    public static final String DIFFERENCE_PROPERTY = "difference";
    private final TopElementHandler<SerialData<GraphDocument>> xmlData = new TopElementHandler<>();
    private final Map<Group, Boolean> differenceEncoding = new HashMap<>();
    private final Map<Group, InputGraph> lastParsedGraph = new HashMap<>();
    private final GroupCallback groupCallback;
    private final HashMap<String, Integer> idCache = new HashMap<>();
    private final ArrayList<Pair<String, String>> blockConnections = new ArrayList<>();
    private int maxId = 0;
    private SerialData<GraphDocument> serialData;
    private final ParseMonitor monitor;
    private final ReadableByteChannel channel;
    private boolean invokeLater = true;

    private int lookupID(String i) {
        try {
            return Integer.parseInt(i);
        } catch (NumberFormatException nfe) {
            // ignore
        }
        Integer id = idCache.get(i);
        if (id == null) {
            id = maxId++;
            idCache.put(i, id);
        }
        return id;
    }

    // <graphDocument>
    private final ElementHandler<SerialData<GraphDocument>, Object> topHandler = new ElementHandler<>(TOP_ELEMENT) {

        @Override
        protected SerialData<GraphDocument> start() {
            serialData = new SerialData<>(new GraphDocument(), new HashSet<>());
            return serialData;
        }
    };
    // <group>
    private final ElementHandler<SerialData<Group>, SerialData<? extends Folder>> groupHandler = new XMLParser.ElementHandler<>(GROUP_ELEMENT) {

        @Override
        protected SerialData<Group> start() {
            final Folder folder = getParentObject().data();
            final Group group = new Group(folder);

            String differenceProperty = this.readAttribute(DIFFERENCE_PROPERTY);
            Parser.this.differenceEncoding.put(group, (differenceProperty != null && (differenceProperty.equals("1") || differenceProperty.equals("true"))));

            ParseMonitor monitor = getMonitor();
            if (monitor != null) {
                monitor.setState(group.getName());
            }

            if (groupCallback == null || folder instanceof Group) {
                Runnable addToParent = () -> folder.addElement(group);
                if (invokeLater) {
                    SwingUtilities.invokeLater(addToParent);
                } else {
                    addToParent.run();
                }
            }

            return new SerialData<>(group, getParentObject().contexts());
        }

        @Override
        protected void end(String text) {
        }
    };
    // <method>
    private final ElementHandler<InputMethod, SerialData<Group>> methodHandler = new XMLParser.ElementHandler<>(METHOD_ELEMENT) {

        @Override
        protected InputMethod start() throws SAXException {
            Group group = getParentObject().data();
            InputMethod method = parseMethod(this, group);
            group.setMethod(method);
            return method;
        }
    };

    private InputMethod parseMethod(XMLParser.ElementHandler<?,?> handler, Group group) throws SAXException {
        String s = handler.readRequiredAttribute(METHOD_BCI_PROPERTY);
        int bci;
        try {
            bci = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new SAXException(e);
        }
        return new InputMethod(group, handler.readRequiredAttribute(METHOD_NAME_PROPERTY), handler.readRequiredAttribute(METHOD_SHORT_NAME_PROPERTY), bci);
    }
    // <bytecodes>
    private final HandoverElementHandler<InputMethod> bytecodesHandler = new XMLParser.HandoverElementHandler<>(BYTECODES_ELEMENT, true) {

        @Override
        protected void end(String text) {
            getParentObject().setBytecodes(text);
        }
    };
    // <inlined>
    private final HandoverElementHandler<InputMethod> inlinedHandler = new XMLParser.HandoverElementHandler<>(INLINE_ELEMENT);
    // <inlined><method>
    private final ElementHandler<InputMethod, InputMethod> inlinedMethodHandler = new XMLParser.ElementHandler<>(METHOD_ELEMENT) {

        @Override
        protected InputMethod start() throws SAXException {
            InputMethod method = parseMethod(this, getParentObject().getGroup());
            getParentObject().addInlined(method);
            return method;
        }
    };
    // <graph>
    private final ElementHandler<SerialData<InputGraph>, SerialData<Group>> graphHandler = new XMLParser.ElementHandler<>(GRAPH_ELEMENT) {

        @Override
        protected SerialData<InputGraph> start() {
            Group group = getParentObject().data();
            String name = readAttribute(GRAPH_NAME_PROPERTY);
            InputGraph curGraph = new InputGraph(name);
            if (differenceEncoding.get(group)) {
                InputGraph previous = lastParsedGraph.get(group);
                lastParsedGraph.put(group, curGraph);
                if (previous != null) {
                    for (InputNode n : previous.getNodes()) {
                        curGraph.addNode(n);
                    }
                    for (InputEdge e : previous.getEdges()) {
                        curGraph.addEdge(e);
                    }
                }
            }
            ParseMonitor monitor = getMonitor();
            if (monitor != null) {
                monitor.updateProgress();
            }
            return new SerialData<>(curGraph, new HashSet<>());
        }

        @Override
        protected void end(String text) {
            // NOTE: Some graphs intentionally don't provide blocks. Instead,
            //       they later generate the blocks from other information such
            //       as node properties (example: ServerCompilerScheduler).
            //       Thus, we shouldn't assign nodes that don't belong to any
            //       block to some artificial block below unless blocks are
            //       defined and nodes are assigned to them.

            final InputGraph graph = getObject().data();

            final Group parent = getParentObject().data();
            if (!graph.getBlocks().isEmpty()) {
                boolean blocksContainNodes = false;
                for (InputBlock b : graph.getBlocks()) {
                    if (!b.getNodes().isEmpty()) {
                        blocksContainNodes = true;
                        break;
                    }
                }

                if (!blocksContainNodes) {
                    graph.clearBlocks();
                    blockConnections.clear();
                } else {
                    // Blocks and their nodes defined: add other nodes to an
                    //  artificial "no block" block
                    InputBlock noBlock = null;
                    for (InputNode n : graph.getNodes()) {
                        if (graph.getBlock(n) == null) {
                            if (noBlock == null) {
                                noBlock = graph.addArtificialBlock();
                            }

                            noBlock.addNode(n.getId());
                        }

                        assert graph.getBlock(n) != null;
                    }
                }
            }

            // Resolve block successors
            for (Pair<String, String> p : blockConnections) {
                final InputBlock left = graph.getBlock(p.getLeft());
                assert left != null;
                final InputBlock right = graph.getBlock(p.getRight());
                assert right != null;
                graph.addBlockEdge(left, right);
            }
            blockConnections.clear();

            Runnable addToParent = () -> parent.addElement(graph);
            Runnable addContext = () -> getParentObject().contexts().addAll(getObject().contexts());
            if (invokeLater) {
                SwingUtilities.invokeLater(addToParent);
                SwingUtilities.invokeLater(addContext);

            } else {
                addToParent.run();
                addContext.run();
            }
        }
    };
    // <nodes>
    private final HandoverElementHandler<SerialData<InputGraph>> nodesHandler = new HandoverElementHandler<>(NODES_ELEMENT);
    // <controlFlow>
    private final HandoverElementHandler<SerialData<InputGraph>> controlFlowHandler = new HandoverElementHandler<>(CONTROL_FLOW_ELEMENT);
    private final HandoverElementHandler<SerialData<InputGraph>> graphStatesHandler = new HandoverElementHandler<>(GRAPH_STATES_ELEMENT);
    private final ElementHandler<GraphContext, SerialData<InputGraph>> stateHandler = new ElementHandler<>(STATE_ELEMENT) {

        @Override
        protected GraphContext start() {
            SerialData<InputGraph> data = getParentObject();
            InputGraph inputGraph = data.data();
            GraphContext graphContext = new GraphContext(inputGraph,  new AtomicInteger(0), new HashSet<>());
            data.contexts().add(graphContext);
            return graphContext;
        }
    };


    private final HandoverElementHandler<GraphContext> hiddenNodesHandler = new HandoverElementHandler<>(HIDDEN_NODES_ELEMENT);

    private final ElementHandler<GraphContext, GraphContext> hiddenNodeHandler = new ElementHandler<>(NODE_ELEMENT) {

        @Override
        protected GraphContext start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int nodeID;
            try {
                nodeID = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            getParentObject().hiddenNodes().add(nodeID);
            return getParentObject();
        }
    };

    private final ElementHandler<GraphContext, GraphContext> differenceHandler = new ElementHandler<>(STATE_POSITION_DIFFERENCE) {

        @Override
        protected GraphContext start() throws SAXException {
            String s = readRequiredAttribute(POSITION_DIFFERENCE_PROPERTY);
            int posDiff;
            try {
                posDiff = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            getParentObject().posDiff().set(posDiff);
            return getParentObject();
        }
    };


    // <block>
    private final ElementHandler<InputBlock, SerialData<InputGraph>> blockHandler = new ElementHandler<>(BLOCK_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            InputGraph graph = getParentObject().data();
            String name = readRequiredAttribute(BLOCK_NAME_PROPERTY);
            InputBlock b = graph.addBlock(name);
            for (InputNode n : b.getNodes()) {
                assert graph.getBlock(n).equals(b);
            }
            return b;
        }
    };

    // <nodes>
    private final HandoverElementHandler<InputBlock> blockNodesHandler = new HandoverElementHandler<>(NODES_ELEMENT);
    // <node>
    private final ElementHandler<InputBlock, InputBlock> blockNodeHandler = new ElementHandler<>(NODE_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);

            int id;
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
    private final HandoverElementHandler<InputBlock> successorsHandler = new HandoverElementHandler<>(SUCCESSORS_ELEMENT);
    // <successor>
    private final ElementHandler<InputBlock, InputBlock> successorHandler = new ElementHandler<>(SUCCESSOR_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            String name = readRequiredAttribute(BLOCK_NAME_PROPERTY);
            blockConnections.add(new Pair<>(getParentObject().getName(), name));
            return getParentObject();
        }
    };
    // <node>
    private final ElementHandler<InputNode, SerialData<InputGraph>> nodeHandler = new ElementHandler<>(NODE_ELEMENT) {

        @Override
        protected InputNode start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int id;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            InputNode node = new InputNode(id);
            getParentObject().data().addNode(node);
            return node;
        }
    };
    // <removeNode>
    private final ElementHandler<InputNode, SerialData<InputGraph>> removeNodeHandler = new ElementHandler<>(REMOVE_NODE_ELEMENT) {

        @Override
        protected InputNode start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int id;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            return getParentObject().data().removeNode(id);
        }
    };
    // <graph>
    private final HandoverElementHandler<SerialData<InputGraph>> edgesHandler = new HandoverElementHandler<>(EDGES_ELEMENT);

    // Local class for edge elements
    private class EdgeElementHandler extends ElementHandler<InputEdge, SerialData<InputGraph>> {

        public EdgeElementHandler(String name) {
            super(name);
        }

        @Override
        protected InputEdge start() throws SAXException {
            int fromIndex = 0;
            int toIndex = 0;
            int from;
            int to;
            String label;
            String type;

            try {
                String fromIndexString = readAttribute(FROM_INDEX_PROPERTY);
                if (fromIndexString != null) {
                    fromIndex = Integer.parseInt(fromIndexString);
                }

                String toIndexString = readAttribute(TO_INDEX_PROPERTY);
                if (toIndexString == null) {
                    toIndexString = readAttribute(TO_INDEX_ALT_PROPERTY);
                }
                if (toIndexString != null) {
                    toIndex = Integer.parseInt(toIndexString);
                }

                label = readAttribute(LABEL_PROPERTY);
                type = readAttribute(TYPE_PROPERTY);

                from = lookupID(readRequiredAttribute(FROM_PROPERTY));
                to = lookupID(readRequiredAttribute(TO_PROPERTY));
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }

            InputEdge conn = new InputEdge((char) fromIndex, (char) toIndex, from, to, label, type == null ? "" : type);
            return start(conn);
        }

        protected InputEdge start(InputEdge conn) {
            return conn;
        }
    }
    // <edge>
    private final EdgeElementHandler edgeHandler = new EdgeElementHandler(EDGE_ELEMENT) {

        @Override
        protected InputEdge start(InputEdge conn) {
            getParentObject().data().addEdge(conn);
            return conn;
        }
    };
    // <removeEdge>
    private final EdgeElementHandler removeEdgeHandler = new EdgeElementHandler(REMOVE_EDGE_ELEMENT) {

        @Override
        protected InputEdge start(InputEdge conn) {
            getParentObject().data().removeEdge(conn);
            return conn;
        }
    };
    // <properties>
    private final HandoverElementHandler<Properties.Provider> propertiesHandler = new HandoverElementHandler<>(PROPERTIES_ELEMENT);
    // <properties>
    private final HandoverElementHandler<SerialData<Group>> groupPropertiesHandler = new HandoverElementHandler<>(PROPERTIES_ELEMENT) {

        @Override
        public void end(String text) {
            final Group group = getParentObject().data();
            if (groupCallback != null && group.getParent() instanceof GraphDocument) {
                Runnable addStarted = () -> groupCallback.started(group);
                if (invokeLater) {
                    SwingUtilities.invokeLater(addStarted);
                } else {
                    addStarted.run();
                }
            }
        }
    };
    // <property>
    private final ElementHandler<String, Properties.Provider> propertyHandler = new XMLParser.ElementHandler<>(PROPERTY_ELEMENT, true) {

        @Override
        public String start() throws SAXException {
            return readRequiredAttribute(PROPERTY_NAME_PROPERTY);
        }

        @Override
        public void end(String text) {
            getParentObject().getProperties().setProperty(getObject(), text.trim());
        }
    };

    public Parser(ReadableByteChannel channel) {
        this(channel, null, null);
    }

    public Parser(ReadableByteChannel channel, ParseMonitor monitor, GroupCallback groupCallback) {

        this.groupCallback = groupCallback;
        this.monitor = monitor;
        this.channel = channel;

        // Initialize dependencies
        xmlData.addChild(topHandler);
        topHandler.addChild(groupHandler);

        groupHandler.addChild(methodHandler);
        groupHandler.addChild(graphHandler);
        groupHandler.addChild(groupHandler);

        methodHandler.addChild(inlinedHandler);
        methodHandler.addChild(bytecodesHandler);

        inlinedHandler.addChild(inlinedMethodHandler);
        inlinedMethodHandler.addChild(bytecodesHandler);
        inlinedMethodHandler.addChild(inlinedHandler);

        graphHandler.addChild(nodesHandler);
        graphHandler.addChild(edgesHandler);
        graphHandler.addChild(controlFlowHandler);
        graphHandler.addChild(graphStatesHandler);

        controlFlowHandler.addChild(blockHandler);

        graphStatesHandler.addChild(stateHandler);
        stateHandler.addChild(differenceHandler);
        stateHandler.addChild(hiddenNodesHandler);
        hiddenNodesHandler.addChild(hiddenNodeHandler);

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
    @Override
    public SerialData<GraphDocument> parse() throws IOException {
        if (monitor != null) {
            monitor.setState("Starting parsing");
        }
        try {
            XMLReader reader = createReader();
            // To enforce using English for non-English users, we must use Locale.ROOT rather than Locale.ENGLISH
            reader.setProperty("http://apache.org/xml/properties/locale", Locale.ROOT);
            reader.setContentHandler(new XMLParser(xmlData, monitor));
            reader.parse(new InputSource(Channels.newInputStream(channel)));
        } catch (SAXException ex) {
            if (!(ex instanceof SAXParseException) || !"XML document structures must start and end within the same entity.".equals(ex.getMessage())) {
                throw new IOException(ex);
            }
        }
        if (monitor != null) {
            monitor.setState("Finished parsing");
        }

        return serialData;
    }

    // Whether the parser is allowed to defer connecting the parsed elements.
    // Setting to false is useful for synchronization in unit tests.
    public void setInvokeLater(boolean invokeLater) {
        this.invokeLater = invokeLater;
    }

    private XMLReader createReader() throws SAXException {
        try {
            SAXParserFactory pFactory = SAXParserFactory.newInstance();
            pFactory.setValidating(false);
            pFactory.setNamespaceAware(true);
            return pFactory.newSAXParser().getXMLReader();
        } catch (ParserConfigurationException ex) {
            throw new SAXException(ex);
        }
    }
}
