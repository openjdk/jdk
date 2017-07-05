/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.salver.dumper;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.GraalDebugConfig.Options;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Edges.Type;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.InputEdges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
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
import org.graalvm.compiler.salver.data.DataDict;
import org.graalvm.compiler.salver.data.DataList;

public class GraphDumper extends AbstractMethodScopeDumper {

    public static final String EVENT_NAMESPACE = "graal/graph";

    private static final Map<Class<?>, String> nodeClassCategoryMap;

    static {
        nodeClassCategoryMap = new LinkedHashMap<>();
        nodeClassCategoryMap.put(ControlSinkNode.class, "ControlSink");
        nodeClassCategoryMap.put(ControlSplitNode.class, "ControlSplit");
        nodeClassCategoryMap.put(AbstractMergeNode.class, "Merge");
        nodeClassCategoryMap.put(AbstractBeginNode.class, "Begin");
        nodeClassCategoryMap.put(AbstractEndNode.class, "End");
        nodeClassCategoryMap.put(FixedNode.class, "Fixed");
        nodeClassCategoryMap.put(VirtualState.class, "State");
        nodeClassCategoryMap.put(PhiNode.class, "Phi");
        nodeClassCategoryMap.put(ProxyNode.class, "Proxy");
        // nodeClassCategoryMap.put(Node.class, "Floating");
    }

    @Override
    public void beginDump() throws IOException {
        beginDump(EVENT_NAMESPACE);
    }

    @SuppressWarnings("try")
    public void dump(Graph graph, String msg) throws IOException {
        resolveMethodContext();

        try (Scope s = Debug.sandbox(getClass().getSimpleName(), null)) {
            processGraph(graph, msg);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private void processGraph(Graph graph, String name) throws IOException {

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
                    } catch (Throwable t) {
                    }
                }

            }
        }

        DataDict dataDict = new DataDict();
        dataDict.put("id", nextItemId());
        dataDict.put("name", name);

        DataDict graphDict = new DataDict();
        dataDict.put("graph", graphDict);

        processNodes(graphDict, graph.getNodes(), scheduleResult);

        if (scheduleResult != null) {
            ControlFlowGraph cfg = scheduleResult.getCFG();
            if (cfg != null) {
                List<Block> blocks = Arrays.asList(cfg.getBlocks());
                processBlocks(graphDict, blocks, scheduleResult);
            }
        }
        serializeAndFlush(createEventDictWithId("graph", dataDict));
    }

    private static void processNodes(DataDict graphDict, NodeIterable<Node> nodes, ScheduleResult schedule) {
        Map<NodeClass<?>, Integer> classMap = new HashMap<>();

        DataList classList = new DataList();
        graphDict.put("classes", classList);

        DataList nodeList = new DataList();
        graphDict.put("nodes", nodeList);

        DataList edgeList = new DataList();
        graphDict.put("edges", edgeList);

        for (Node node : nodes) {
            NodeClass<?> nodeClass = node.getNodeClass();

            DataDict nodeDict = new DataDict();
            nodeList.add(nodeDict);

            nodeDict.put("id", getNodeId(node));
            nodeDict.put("class", getNodeClassId(classMap, classList, nodeClass));

            if (schedule != null) {
                processNodeSchedule(nodeDict, node, schedule);
            }

            DataDict propertyDict = new DataDict();
            node.getDebugProperties(propertyDict);

            if (!propertyDict.isEmpty()) {
                nodeDict.put("properties", propertyDict);
            }

            appendEdges(edgeList, node, Type.Inputs);
            appendEdges(edgeList, node, Type.Successors);
        }
    }

    private static void processNodeSchedule(DataDict nodeDict, Node node, ScheduleResult schedule) {
        NodeMap<Block> nodeToBlock = schedule.getNodeToBlockMap();
        if (nodeToBlock != null) {
            if (nodeToBlock.isNew(node)) {
                nodeDict.put("block", -1);
            } else {
                Block block = nodeToBlock.get(node);
                if (block != null) {
                    nodeDict.put("block", block.getId());
                }
            }
        }

        ControlFlowGraph cfg = schedule.getCFG();
        if (cfg != null && Options.PrintGraphProbabilities.getValue() && node instanceof FixedNode) {
            try {
                nodeDict.put("probability", cfg.blockFor(node).probability());
            } catch (Throwable t) {
                nodeDict.put("probability", t);
            }
        }
    }

    private static void processBlocks(DataDict graphDict, List<Block> blocks, ScheduleResult schedule) {
        BlockMap<List<Node>> blockToNodes = schedule.getBlockToNodesMap();
        DataList blockList = new DataList();
        graphDict.put("blocks", blockList);

        for (Block block : blocks) {
            List<Node> nodes = blockToNodes.get(block);
            if (nodes != null) {
                DataDict blockDict = new DataDict();
                blockList.add(blockDict);

                blockDict.put("id", block.getId());

                DataList nodeList = new DataList();
                blockDict.put("nodes", nodeList);

                for (Node node : nodes) {
                    nodeList.add(getNodeId(node));
                }

                Block[] successors = block.getSuccessors();
                if (successors != null && successors.length > 0) {
                    DataList successorList = new DataList();
                    blockDict.put("successors", successorList);
                    for (Block successor : successors) {
                        successorList.add(successor.getId());
                    }
                }
            }
        }
    }

    private static void appendEdges(DataList edgeList, Node node, Edges.Type type) {
        NodeClass<?> nodeClass = node.getNodeClass();

        Edges edges = nodeClass.getEdges(type);
        final long[] curOffsets = edges.getOffsets();

        for (int i = 0; i < edges.getDirectCount(); i++) {
            Node other = Edges.getNode(node, curOffsets, i);
            if (other != null) {
                DataDict edgeDict = new DataDict();

                DataDict nodeDict = new DataDict();
                nodeDict.put("node", getNodeId(node));
                nodeDict.put("field", edges.getName(i));

                edgeDict.put("from", type == Type.Inputs ? getNodeId(other) : nodeDict);
                edgeDict.put("to", type == Type.Inputs ? nodeDict : getNodeId(other));
                edgeList.add(edgeDict);
            }
        }
        for (int i = edges.getDirectCount(); i < edges.getCount(); i++) {
            NodeList<Node> list = Edges.getNodeList(node, curOffsets, i);
            if (list != null) {
                for (int index = 0; index < list.size(); index++) {
                    Node other = list.get(index);
                    if (other != null) {
                        DataDict edgeDict = new DataDict();

                        DataDict nodeDict = new DataDict();
                        nodeDict.put("node", getNodeId(node));
                        nodeDict.put("field", edges.getName(i));
                        nodeDict.put("index", index);

                        edgeDict.put("from", type == Type.Inputs ? getNodeId(other) : nodeDict);
                        edgeDict.put("to", type == Type.Inputs ? nodeDict : getNodeId(other));
                        edgeList.add(edgeDict);
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static int getNodeId(Node node) {
        return node != null ? node.getId() : -1;
    }

    private static int getNodeClassId(Map<NodeClass<?>, Integer> classMap, DataList classList, NodeClass<?> nodeClass) {
        if (classMap.containsKey(nodeClass)) {
            return classMap.get(nodeClass);
        }
        int classId = classMap.size();
        classMap.put(nodeClass, classId);

        Class<?> javaClass = nodeClass.getJavaClass();

        DataDict classDict = new DataDict();
        classList.add(classDict);

        classDict.put("id", classId);
        classDict.put("name", nodeClass.getNameTemplate());
        classDict.put("jtype", javaClass.getName());

        String category = getNodeClassCategory(javaClass);
        if (category != null) {
            classDict.put("category", category);
        }

        Object propertyInfo = getPropertyInfo(nodeClass);
        if (propertyInfo != null) {
            classDict.put("properties", propertyInfo);
        }

        Object inputInfo = getEdgeInfo(nodeClass, Type.Inputs);
        if (inputInfo != null) {
            classDict.put("inputs", inputInfo);
        }
        Object successorInfo = getEdgeInfo(nodeClass, Type.Successors);
        if (successorInfo != null) {
            classDict.put("successors", successorInfo);
        }
        return classId;
    }

    private static DataDict getPropertyInfo(NodeClass<?> nodeClass) {
        Fields properties = nodeClass.getData();
        if (properties.getCount() > 0) {
            DataDict propertyInfoDict = new DataDict();
            for (int i = 0; i < properties.getCount(); i++) {
                DataDict propertyDict = new DataDict();
                String name = properties.getName(i);
                propertyDict.put("name", name);
                propertyDict.put("jtype", properties.getType(i).getName());
                propertyInfoDict.put(name, propertyDict);
            }
            return propertyInfoDict;
        }
        return null;
    }

    private static DataDict getEdgeInfo(NodeClass<?> nodeClass, Edges.Type type) {
        DataDict edgeInfoDict = new DataDict();
        Edges edges = nodeClass.getEdges(type);
        for (int i = 0; i < edges.getCount(); i++) {
            DataDict edgeDict = new DataDict();
            String name = edges.getName(i);
            Class<?> fieldClass = edges.getType(i);
            edgeDict.put("name", name);
            edgeDict.put("jtype", fieldClass.getName());
            if (NodeList.class.isAssignableFrom(fieldClass)) {
                edgeDict.put("isList", true);
            }
            if (type == Type.Inputs) {
                InputEdges inputEdges = ((InputEdges) edges);
                edgeDict.put("type", inputEdges.getInputType(i));
                if (inputEdges.isOptional(i)) {
                    edgeDict.put("isOptional", true);
                }
            }
            edgeInfoDict.put(name, edgeDict);
        }
        return edgeInfoDict.isEmpty() ? null : edgeInfoDict;
    }

    private static String getNodeClassCategory(Class<?> clazz) {
        for (Map.Entry<Class<?>, String> entry : nodeClassCategoryMap.entrySet()) {
            if (entry.getKey().isAssignableFrom(clazz)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
