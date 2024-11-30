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

import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.data.serialization.Printer.GraphContext;
import com.sun.hotspot.igv.data.serialization.Printer.GraphContextAction;
import com.sun.hotspot.igv.data.serialization.XMLParser.ElementHandler;
import com.sun.hotspot.igv.data.serialization.XMLParser.HandoverElementHandler;
import com.sun.hotspot.igv.data.serialization.XMLParser.TopElementHandler;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
    public static final String VISIBLE_NODES_ELEMENT = "visibleNodes";
    public static final String ALL_PROPERTY = "all";
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
    private final TopElementHandler<GraphDocument> xmlData = new TopElementHandler<>();
    private final Map<Group, Boolean> differenceEncoding = new HashMap<>();
    private final Map<Group, InputGraph> lastParsedGraph = new HashMap<>();
    private final GraphDocument callbackDocument;
    private final GraphContextAction contextAction;
    private final ArrayList<GraphContext> contexts = new ArrayList<>();
    private final HashMap<String, Integer> idCache = new HashMap<>();
    private final ArrayList<Pair<String, String>> blockConnections = new ArrayList<>();
    private final ParseMonitor monitor;
    private final ReadableByteChannel channel;
    private boolean invokeLater = true;

    // <method>
    private final ElementHandler<InputMethod, Group> methodHandler = new XMLParser.ElementHandler<>(METHOD_ELEMENT) {

        @Override
        protected InputMethod start() throws SAXException {
            Group group = getParentObject();
            InputMethod method = parseMethod(this, group);
            group.setMethod(method);
            return method;
        }
    };
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
    // <nodes>
    private final HandoverElementHandler<InputGraph> nodesHandler = new HandoverElementHandler<>(NODES_ELEMENT);
    // <controlFlow>
    private final HandoverElementHandler<InputGraph> controlFlowHandler = new HandoverElementHandler<>(CONTROL_FLOW_ELEMENT);
    private final HandoverElementHandler<InputGraph> graphStatesHandler = new HandoverElementHandler<>(GRAPH_STATES_ELEMENT);
    private final ElementHandler<GraphContext, InputGraph> stateHandler = new ElementHandler<>(STATE_ELEMENT) {

        @Override
        protected GraphContext start() {
            InputGraph inputGraph = getParentObject();
            GraphContext graphContext = new GraphContext(inputGraph, new AtomicInteger(0), new HashSet<>(), new AtomicBoolean(false));
            if (contextAction != null) {
                contexts.add(graphContext);
            }
            return graphContext;
        }
    };
    private final ElementHandler<GraphContext, GraphContext> visibleNodesHandler = new ElementHandler<>(VISIBLE_NODES_ELEMENT) {

        @Override
        protected GraphContext start() throws SAXException {
            String s = readRequiredAttribute(ALL_PROPERTY);
            try {
                boolean all = Boolean.parseBoolean(s);
                getParentObject().showAll().set(all);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            return getParentObject();
        }
    };
    private final ElementHandler<GraphContext, GraphContext> visibleNodeHandler = new ElementHandler<>(NODE_ELEMENT) {

        @Override
        protected GraphContext start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int nodeID;
            try {
                nodeID = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            getParentObject().visibleNodes().add(nodeID);
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
    private final ElementHandler<InputBlock, InputGraph> blockHandler = new ElementHandler<>(BLOCK_ELEMENT) {

        @Override
        protected InputBlock start() throws SAXException {
            InputGraph graph = getParentObject();
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
    // <graph>
    private final HandoverElementHandler<InputGraph> edgesHandler = new HandoverElementHandler<>(EDGES_ELEMENT);
    // <edge>
    private final EdgeElementHandler edgeHandler = new EdgeElementHandler(EDGE_ELEMENT) {

        @Override
        protected InputEdge start(InputEdge conn) {
            InputGraph inputGraph = getParentObject();
            inputGraph.addEdge(conn);
            return conn;
        }
    };
    // <removeEdge>
    private final EdgeElementHandler removeEdgeHandler = new EdgeElementHandler(REMOVE_EDGE_ELEMENT) {

        @Override
        protected InputEdge start(InputEdge conn) {
            getParentObject().removeEdge(conn);
            return conn;
        }
    };
    // <properties>
    private final HandoverElementHandler<Properties.Provider> propertiesHandler = new HandoverElementHandler<>(PROPERTIES_ELEMENT);
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
    private int maxId = 0;
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
    // <node>
    private final ElementHandler<InputNode, InputGraph> nodeHandler = new ElementHandler<>(NODE_ELEMENT) {

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
            getParentObject().addNode(node);
            return node;
        }
    };
    // <removeNode>
    private final ElementHandler<InputNode, InputGraph> removeNodeHandler = new ElementHandler<>(REMOVE_NODE_ELEMENT) {

        @Override
        protected InputNode start() throws SAXException {
            String s = readRequiredAttribute(NODE_ID_PROPERTY);
            int id;
            try {
                id = lookupID(s);
            } catch (NumberFormatException e) {
                throw new SAXException(e);
            }
            return getParentObject().removeNode(id);
        }
    };
    private GraphDocument graphDocument;
    // <graphDocument>
    private final ElementHandler<GraphDocument, Object> topHandler = new ElementHandler<>(TOP_ELEMENT) {

        @Override
        protected GraphDocument start() {
            graphDocument = new GraphDocument();
            return graphDocument;
        }
    };
    // <group>
    private final ElementHandler<Group, Folder> groupHandler = new XMLParser.ElementHandler<>(GROUP_ELEMENT) {

        @Override
        protected Group start() {
            final Folder folder = getParentObject();
            final Group group = new Group(folder);

            String differenceProperty = this.readAttribute(DIFFERENCE_PROPERTY);
            Parser.this.differenceEncoding.put(group, (differenceProperty != null && (differenceProperty.equals("1") || differenceProperty.equals("true"))));

            ParseMonitor monitor = getMonitor();
            if (monitor != null) {
                monitor.setState(group.getName());
            }

            if (callbackDocument == null || folder instanceof Group) {
                if (invokeLater) {
                    SwingUtilities.invokeLater(() -> folder.addElement(group));
                } else {
                    folder.addElement(group);
                }
            }

            return group;
        }

        @Override
        protected void end(String text) {
        }
    };
    // <graph>
    private final ElementHandler<InputGraph, Group> graphHandler = new XMLParser.ElementHandler<>(GRAPH_ELEMENT) {

        @Override
        protected InputGraph start() {
            Group group = getParentObject();
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
            return curGraph;
        }

        @Override
        protected void end(String text) {
            // NOTE: Some graphs intentionally don't provide blocks. Instead,
            //       they later generate the blocks from other information such
            //       as node properties (example: ServerCompilerScheduler).
            //       Thus, we shouldn't assign nodes that don't belong to any
            //       block to some artificial block below unless blocks are
            //       defined and nodes are assigned to them.

            final InputGraph graph = getObject();

            final Group parent = getParentObject();
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
                    // artificial "no block" block
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

            if (invokeLater) {
                SwingUtilities.invokeLater(() -> parent.addElement(graph));
            } else {
                parent.addElement(graph);
            }
            if (contextAction != null) {
                for (GraphContext ctx : contexts) {
                    contextAction.performAction(ctx);
                }
            }
            contexts.clear();
        }
    };
    // <properties>
    private final HandoverElementHandler<Group> groupPropertiesHandler = new HandoverElementHandler<>(PROPERTIES_ELEMENT) {

        @Override
        public void end(String text) {
            final Group group = getParentObject();
            if (callbackDocument != null && group.getParent() instanceof GraphDocument) {
                group.setParent(callbackDocument);
                if (invokeLater) {
                    SwingUtilities.invokeLater(() -> callbackDocument.addElement(group));
                } else {
                    callbackDocument.addElement(group);
                }
            }
        }
    };

    public Parser(ReadableByteChannel channel, ParseMonitor monitor, GraphDocument callbackDocument, GraphContextAction contextAction) {
        this.callbackDocument = callbackDocument;
        this.contextAction = contextAction;
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
        stateHandler.addChild(visibleNodesHandler);
        visibleNodesHandler.addChild(visibleNodeHandler);

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

    private InputMethod parseMethod(XMLParser.ElementHandler<?, ?> handler, Group group) throws SAXException {
        String s = handler.readRequiredAttribute(METHOD_BCI_PROPERTY);
        int bci;
        try {
            bci = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new SAXException(e);
        }
        return new InputMethod(group, handler.readRequiredAttribute(METHOD_NAME_PROPERTY), handler.readRequiredAttribute(METHOD_SHORT_NAME_PROPERTY), bci);
    }

    // Returns a new GraphDocument object deserialized from an XML input source.
    @Override
    public GraphDocument parse() throws IOException {
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

        return graphDocument;
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

    // Local class for edge elements
    private class EdgeElementHandler extends ElementHandler<InputEdge, InputGraph> {

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
}
