/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Printer {

    public static void exportGraphDocument(Writer writer, Folder folder, List<GraphContext> contexts) {
        XMLWriter xmlWriter = new XMLWriter(writer);
        try {
            xmlWriter.startTag(Parser.ROOT_ELEMENT);
            xmlWriter.writeProperties(folder.getProperties());
            for (FolderElement e : folder.getElements()) {
                if (e instanceof Group group) {
                    exportGroup(xmlWriter, group, contexts);
                } else if (e instanceof InputGraph graph) {
                    exportInputGraph(xmlWriter, graph, null, false, contexts);
                }
            }
            xmlWriter.endTag();
            xmlWriter.flush();
        } catch (IOException ignored) {}
    }

    private static void exportGroup(XMLWriter writer, Group g, List<GraphContext> contexts) throws IOException {
        Properties attributes = new Properties();
        attributes.setProperty("difference", Boolean.toString(true));
        writer.startTag(Parser.GROUP_ELEMENT, attributes);
        writer.writeProperties(g.getProperties());

        if (g.getMethod() != null) {
            exportInputMethod(writer, g.getMethod());
        }

        InputGraph previous = null;
        for (FolderElement e : g.getElements()) {
            if (e instanceof InputGraph graph) {
                exportInputGraph(writer, graph, previous, true, contexts);
                previous = graph;
            } else if (e instanceof Group group) {
                exportGroup(writer, group, contexts);
            }
        }

        writer.endTag();
    }

    private static void exportInputGraph(XMLWriter writer, InputGraph graph, InputGraph previous, boolean difference, List<GraphContext> contexts) throws IOException {
        writer.startTag(Parser.GRAPH_ELEMENT);
        writer.writeProperties(graph.getProperties());
        writer.startTag(Parser.NODES_ELEMENT);

        Set<InputNode> removed = new HashSet<>();
        Set<InputNode> equal = new HashSet<>();

        if (previous != null) {
            for (InputNode n : previous.getNodes()) {
                int id = n.getId();
                InputNode n2 = graph.getNode(id);
                if (n2 == null) {
                    removed.add(n);
                } else if (n.equals(n2)) {
                    equal.add(n);
                }
            }
        }

        if (difference) {
            for (InputNode n : removed) {
                writer.simpleTag(Parser.REMOVE_NODE_ELEMENT, new Properties(Parser.NODE_ID_PROPERTY, Integer.toString(n.getId())));
            }
            for (InputNode n : graph.getNodes()) {
                if (!equal.contains(n)) {
                    writer.startTag(Parser.NODE_ELEMENT, new Properties(Parser.NODE_ID_PROPERTY, Integer.toString(n.getId())));
                    writer.writeProperties(n.getProperties());
                    writer.endTag(); // Parser.NODE_ELEMENT
                }
            }
        } else {
            for (InputNode n : graph.getNodes()) {
                writer.startTag(Parser.NODE_ELEMENT, new Properties(Parser.NODE_ID_PROPERTY, Integer.toString(n.getId())));
                writer.writeProperties(n.getProperties());
                writer.endTag(); // Parser.NODE_ELEMENT
            }
        }

        writer.endTag(); // Parser.NODES_ELEMENT

        writer.startTag(Parser.EDGES_ELEMENT);
        Set<InputEdge> removedEdges = new HashSet<>();
        Set<InputEdge> equalEdges = new HashSet<>();

        if (previous != null) {
            for (InputEdge e : previous.getEdges()) {
                if (graph.getEdges().contains(e)) {
                    equalEdges.add(e);
                } else {
                    removedEdges.add(e);
                }
            }
        }

        if (difference) {
            for (InputEdge e : removedEdges) {
                writer.simpleTag(Parser.REMOVE_EDGE_ELEMENT, createProperties(e));
            }
        }

        for (InputEdge e : graph.getEdges()) {
            if (!difference || !equalEdges.contains(e)) {
                if (!equalEdges.contains(e)) {
                    writer.simpleTag(Parser.EDGE_ELEMENT, createProperties(e));
                }
            }
        }

        writer.endTag(); // Parser.EDGES_ELEMENT

        writer.startTag(Parser.CONTROL_FLOW_ELEMENT);
        for (InputBlock b : graph.getBlocks()) {
            writer.startTag(Parser.BLOCK_ELEMENT, new Properties(Parser.BLOCK_NAME_PROPERTY, b.getName()));

            if (!b.getSuccessors().isEmpty()) {
                writer.startTag(Parser.SUCCESSORS_ELEMENT);
                for (InputBlock s : b.getSuccessors()) {
                    writer.simpleTag(Parser.SUCCESSOR_ELEMENT, new Properties(Parser.BLOCK_NAME_PROPERTY, s.getName()));
                }
                writer.endTag(); // Parser.SUCCESSORS_ELEMENT
            }

            if (!b.getNodes().isEmpty()) {
                writer.startTag(Parser.NODES_ELEMENT);
                for (InputNode n : b.getNodes()) {
                    writer.simpleTag(Parser.NODE_ELEMENT, new Properties(Parser.NODE_ID_PROPERTY, n.getId() + ""));
                }
                writer.endTag(); // Parser.NODES_ELEMENT
            }

            if (!b.getLiveOut().isEmpty()) {
                writer.startTag(Parser.LIVEOUT_ELEMENT);
                for (Integer lrg : b.getLiveOut()) {
                    writer.simpleTag(Parser.LIVE_RANGE_ELEMENT, new Properties(Parser.LIVE_RANGE_ID_PROPERTY, String.valueOf(lrg)));
                }
                writer.endTag(); // Parser.LIVEOUT_ELEMENT
            }

            writer.endTag(); // Parser.BLOCK_ELEMENT
        }

        writer.endTag(); // Parser.CONTROL_FLOW_ELEMENT

        if (!graph.getLiveRanges().isEmpty()) {
            writer.startTag(Parser.LIVE_RANGES_ELEMENT);
            for (InputLiveRange liveRange : graph.getLiveRanges()) {
                writer.startTag(Parser.LIVE_RANGE_ELEMENT, new Properties(Parser.LIVE_RANGE_ID_PROPERTY, String.valueOf(liveRange.getId())));
                writer.writeProperties(liveRange.getProperties());
                writer.endTag(); // Parser.LIVE_RANGE_ELEMENT
            }
            writer.endTag(); // Parser.LIVE_RANGES_ELEMENT
        }

        exportStates(writer, graph, contexts);

        writer.endTag(); // Parser.GRAPH_ELEMENT
    }

    private static void exportStates(XMLWriter writer, InputGraph exportingGraph, List<GraphContext> contexts) throws IOException {
        List<GraphContext> contextsContainingGraph = contexts.stream()
                .filter(context -> context.inputGraph().equals(exportingGraph))
                .toList();

        if (contextsContainingGraph.isEmpty()) {
            return;
        }

        writer.startTag(Parser.GRAPH_STATES_ELEMENT);

        for (GraphContext context : contextsContainingGraph) {
            assert exportingGraph == context.inputGraph();

            writer.startTag(Parser.STATE_ELEMENT);

            writer.simpleTag(Parser.STATE_POSITION_DIFFERENCE,
                    new Properties(Parser.POSITION_DIFFERENCE_PROPERTY, Integer.toString(context.posDiff().get())));

            writer.startTag(Parser.VISIBLE_NODES_ELEMENT, new Properties(Parser.ALL_PROPERTY, Boolean.toString(context.showAll().get())));
            for (Integer hiddenNodeID : context.visibleNodes()) {
                writer.simpleTag(Parser.NODE_ELEMENT, new Properties(Parser.NODE_ID_PROPERTY, hiddenNodeID.toString()));
            }
            writer.endTag(); // Parser.VISIBLE_NODES_ELEMENT

            writer.endTag(); // Parser.STATES_ELEMENT
        }

        writer.endTag(); // Parser.GRAPH_STATE_ELEMENT
    }

    private static void exportInputMethod(XMLWriter w, InputMethod method) throws IOException {
        w.startTag(Parser.METHOD_ELEMENT, new Properties(Parser.METHOD_BCI_PROPERTY, method.getBci() + "", Parser.METHOD_NAME_PROPERTY, method.getName(), Parser.METHOD_SHORT_NAME_PROPERTY, method.getShortName()));

        w.writeProperties(method.getProperties());

        if (!method.getInlined().isEmpty()) {
            w.startTag(Parser.INLINE_ELEMENT);
            for (InputMethod m : method.getInlined()) {
                exportInputMethod(w, m);
            }
            w.endTag();
        }

        w.startTag(Parser.BYTECODES_ELEMENT);

        StringBuilder b = new StringBuilder();
        b.append("<![CDATA[\n");
        for (InputBytecode code : method.getBytecodes()) {
            b.append(code.getBci());
            b.append(" ");
            b.append(code.getName());
            b.append(" ");
            b.append(code.getOperands());
            b.append(" ");
            b.append(code.getComment());
            b.append("\n");
        }

        b.append("]]>");
        w.write(b.toString());
        w.endTag();
        w.endTag();
    }

    private static Properties createProperties(InputEdge edge) {
        Properties p = new Properties();
        if (edge.getToIndex() != 0) {
            p.setProperty(Parser.TO_INDEX_PROPERTY, Integer.toString(edge.getToIndex()));
        }
        if (edge.getFromIndex() != 0) {
            p.setProperty(Parser.FROM_INDEX_PROPERTY, Integer.toString(edge.getFromIndex()));
        }
        p.setProperty(Parser.TO_PROPERTY, Integer.toString(edge.getTo()));
        p.setProperty(Parser.FROM_PROPERTY, Integer.toString(edge.getFrom()));
        p.setProperty(Parser.TYPE_PROPERTY, edge.getType());
        return p;
    }

    public record GraphContext(InputGraph inputGraph, AtomicInteger posDiff, Set<Integer> visibleNodes, AtomicBoolean showAll) { }

    public interface GraphContextAction {
        void performAction(GraphContext context);
    }
}
