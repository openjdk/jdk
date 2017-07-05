/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.core.common.util.FrequencyEncoder;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.TypeReader;
import org.graalvm.compiler.core.common.util.TypeWriter;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;

import jdk.vm.ci.code.Architecture;

/**
 * Encodes a {@link StructuredGraph} to a compact byte[] array. All nodes of the graph and edges
 * between the nodes are encoded. Primitive data fields of nodes are stored in the byte[] array.
 * Object data fields of nodes are stored in a separate Object[] array.
 *
 * One encoder instance can be used to encode multiple graphs. This requires that {@link #prepare}
 * is called for all graphs first, followed by one call to {@link #finishPrepare}. Then
 * {@link #encode} can be called for all graphs. The {@link #getObjects() objects} and
 * {@link #getNodeClasses() node classes} arrays do not change anymore after preparation.
 *
 * Multiple encoded graphs share the Object[] array, and elements of the Object[] array are
 * de-duplicated using {@link Object#equals Object equality}. This uses the assumption and good
 * coding practice that data objects are immutable if {@link Object#equals} is implemented.
 * Unfortunately, this cannot be enforced.
 *
 * The Graal {@link NodeClass} does not have a unique id that allows class lookup from an id.
 * Therefore, the encoded graph contains a {@link NodeClass}[] array for lookup, and type ids are
 * encoding-local.
 *
 * The encoded graph has the following structure: First, all nodes and their edges are serialized.
 * The start offset of every node is then known. The raw node data is followed by a "table of
 * contents" that lists the start offset for every node.
 *
 * The beginning of that table of contents is the return value of {@link #encode} and stored in
 * {@link EncodedGraph#getStartOffset()}. The order of nodes in the table of contents is the
 * {@link NodeOrder#orderIds orderId} of a node. Note that the orderId is not the regular node id
 * that every Graal graph node gets assigned. The orderId is computed and used just for encoding and
 * decoding. The orderId of fixed nodes is assigned in reverse postorder. The decoder processes
 * nodes using that order, which ensures that all predecessors of a node (including all
 * {@link EndNode predecessors} of a {@link AbstractBeginNode block}) are decoded before the node.
 * The order id of floating node does not matter during decoding, so floating nodes get order ids
 * after all fixed nodes. The order id is used to encode edges between nodes
 *
 * Structure of an encoded node:
 *
 * <pre>
 * struct Node {
 *   unsigned typeId
 *   signed[] properties
 *   unsigned[] successorOrderIds
 *   unsigned[] inputOrderIds
 * }
 * </pre>
 *
 * All numbers (unsigned and signed) are stored using a variable-length encoding as defined in
 * {@link TypeReader} and {@link TypeWriter}. Especially orderIds are small, so the variable-length
 * encoding is important to keep the encoding compact.
 *
 * The properties, successors, and inputs are written in the order as defined in
 * {@link NodeClass#getData}, {@link NodeClass#getSuccessorEdges()}, and
 * {@link NodeClass#getInputEdges()}. For variable-length successors and input lists, first the
 * length is written and then the orderIds. There is a distinction between null lists (encoded as
 * length -1) and empty lists (encoded as length 0). No reverse edges are written (predecessors,
 * usages) since that information can be easily restored during decoding.
 *
 * Some nodes have additional information written after the properties, successors, and inputs:
 * <li><item>{@link AbstractEndNode}: the orderId of the merge node and then all {@link PhiNode phi
 * mappings} from this end to the merge node are written. <item>{@link LoopExitNode}: the orderId of
 * all {@link ProxyNode proxy nodes} of the loop exit is written.</li>
 */
public class GraphEncoder {

    /** The orderId that always represents {@code null}. */
    public static final int NULL_ORDER_ID = 0;
    /** The orderId of the {@link StructuredGraph#start() start node} of the encoded graph. */
    public static final int START_NODE_ORDER_ID = 1;
    /**
     * The orderId of the first actual node after the {@link StructuredGraph#start() start node}.
     */
    public static final int FIRST_NODE_ORDER_ID = 2;

    /**
     * The known offset between the orderId of a {@link AbstractBeginNode} and its
     * {@link AbstractBeginNode#next() successor}.
     */
    protected static final int BEGIN_NEXT_ORDER_ID_OFFSET = 1;

    protected final Architecture architecture;

    /**
     * Collects all non-primitive data referenced from nodes. The encoding uses an index into an
     * array for decoding. Because of the variable-length encoding, it is beneficial that frequently
     * used objects have the small indices.
     */
    protected final FrequencyEncoder<Object> objects;
    /**
     * Collects all node classes referenced in graphs. This is necessary because {@link NodeClass}
     * currently does not have a unique id.
     */
    protected final FrequencyEncoder<NodeClass<?>> nodeClasses;
    /** The writer for the encoded graphs. */
    protected final UnsafeArrayTypeWriter writer;

    /** The last snapshot of {@link #objects} that was retrieved. */
    protected Object[] objectsArray;
    /** The last snapshot of {@link #nodeClasses} that was retrieved. */
    protected NodeClass<?>[] nodeClassesArray;

    /**
     * Utility method that does everything necessary to encode a single graph.
     */
    public static EncodedGraph encodeSingleGraph(StructuredGraph graph, Architecture architecture) {
        GraphEncoder encoder = new GraphEncoder(architecture);
        encoder.prepare(graph);
        encoder.finishPrepare();
        long startOffset = encoder.encode(graph);
        return new EncodedGraph(encoder.getEncoding(), startOffset, encoder.getObjects(), encoder.getNodeClasses(), graph.getAssumptions(), graph.getMethods());
    }

    public GraphEncoder(Architecture architecture) {
        this.architecture = architecture;
        objects = FrequencyEncoder.createEqualityEncoder();
        nodeClasses = FrequencyEncoder.createIdentityEncoder();
        writer = UnsafeArrayTypeWriter.create(architecture.supportsUnalignedMemoryAccess());
    }

    /**
     * Must be invoked before {@link #finishPrepare()} and {@link #encode}.
     */
    public void prepare(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            nodeClasses.addObject(node.getNodeClass());

            NodeClass<?> nodeClass = node.getNodeClass();
            objects.addObject(node.getNodeSourcePosition());
            for (int i = 0; i < nodeClass.getData().getCount(); i++) {
                if (!nodeClass.getData().getType(i).isPrimitive()) {
                    objects.addObject(nodeClass.getData().get(node, i));
                }
            }
            if (node instanceof Invoke) {
                objects.addObject(((Invoke) node).getContextType());
            }
        }
    }

    public void finishPrepare() {
        objectsArray = objects.encodeAll(new Object[objects.getLength()]);
        nodeClassesArray = nodeClasses.encodeAll(new NodeClass<?>[nodeClasses.getLength()]);
    }

    public Object[] getObjects() {
        return objectsArray;
    }

    public NodeClass<?>[] getNodeClasses() {
        return nodeClassesArray;
    }

    /**
     * Compresses a graph to a byte array. Multiple graphs can be compressed with the same
     * {@link GraphEncoder}.
     *
     * @param graph The graph to encode
     */
    public long encode(StructuredGraph graph) {
        assert objectsArray != null && nodeClassesArray != null : "finishPrepare() must be called before encode()";

        NodeOrder nodeOrder = new NodeOrder(graph);
        int nodeCount = nodeOrder.nextOrderId;
        assert nodeOrder.orderIds.get(graph.start()) == START_NODE_ORDER_ID;
        assert nodeOrder.orderIds.get(graph.start().next()) == FIRST_NODE_ORDER_ID;
        assert nodeCount == graph.getNodeCount() + 1;

        long[] nodeStartOffsets = new long[nodeCount];
        for (Map.Entry<Node, Integer> entry : nodeOrder.orderIds.entries()) {
            Node node = entry.getKey();
            Integer orderId = entry.getValue();

            assert !(node instanceof AbstractBeginNode) || nodeOrder.orderIds.get(((AbstractBeginNode) node).next()) == orderId + BEGIN_NEXT_ORDER_ID_OFFSET;
            nodeStartOffsets[orderId] = writer.getBytesWritten();

            /* Write out the type, properties, and edges. */
            NodeClass<?> nodeClass = node.getNodeClass();
            writer.putUV(nodeClasses.getIndex(nodeClass));
            writeProperties(node, nodeClass.getData());
            writeEdges(node, nodeClass.getEdges(Edges.Type.Successors), nodeOrder);
            writeEdges(node, nodeClass.getEdges(Edges.Type.Inputs), nodeOrder);

            /* Special handling for some nodes that require additional information for decoding. */
            if (node instanceof AbstractEndNode) {
                AbstractEndNode end = (AbstractEndNode) node;
                AbstractMergeNode merge = end.merge();
                /*
                 * Write the orderId of the merge. The merge is not a successor in the Graal graph
                 * (only the merge has an input edge to the EndNode).
                 */
                writeOrderId(merge, nodeOrder);

                /*
                 * Write all phi mappings (the oderId of the phi input for this EndNode, and the
                 * orderId of the phi node.
                 */
                writer.putUV(merge.phis().count());
                for (PhiNode phi : merge.phis()) {
                    writeOrderId(phi.valueAt(end), nodeOrder);
                    writeOrderId(phi, nodeOrder);
                }

            } else if (node instanceof LoopExitNode) {
                LoopExitNode exit = (LoopExitNode) node;
                writeOrderId(exit.stateAfter(), nodeOrder);
                /* Write all proxy nodes of the LoopExitNode. */
                writer.putUV(exit.proxies().count());
                for (ProxyNode proxy : exit.proxies()) {
                    writeOrderId(proxy, nodeOrder);
                }

            } else if (node instanceof Invoke) {
                Invoke invoke = (Invoke) node;
                assert invoke.stateDuring() == null : "stateDuring is not used in high-level graphs";

                writeObjectId(invoke.getContextType());
                writeOrderId(invoke.callTarget(), nodeOrder);
                writeOrderId(invoke.stateAfter(), nodeOrder);
                writeOrderId(invoke.next(), nodeOrder);
                if (invoke instanceof InvokeWithExceptionNode) {
                    InvokeWithExceptionNode invokeWithExcpetion = (InvokeWithExceptionNode) invoke;
                    ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithExcpetion.exceptionEdge();

                    writeOrderId(invokeWithExcpetion.next().next(), nodeOrder);
                    writeOrderId(invokeWithExcpetion.exceptionEdge(), nodeOrder);
                    writeOrderId(exceptionEdge.stateAfter(), nodeOrder);
                    writeOrderId(exceptionEdge.next(), nodeOrder);
                }
            }
        }

        /* Write out the table of contents with the start offset for all nodes. */
        long nodeTableStart = writer.getBytesWritten();
        writer.putUV(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            assert i == NULL_ORDER_ID || i == START_NODE_ORDER_ID || nodeStartOffsets[i] > 0;
            writer.putUV(nodeTableStart - nodeStartOffsets[i]);
        }

        /* Check that the decoding of the encode graph is the same as the input. */
        assert verifyEncoding(graph, new EncodedGraph(getEncoding(), nodeTableStart, getObjects(), getNodeClasses(), graph.getAssumptions(), graph.getMethods()), architecture);

        return nodeTableStart;
    }

    public byte[] getEncoding() {
        return writer.toArray(new byte[TypeConversion.asS4(writer.getBytesWritten())]);
    }

    static class NodeOrder {
        protected final NodeMap<Integer> orderIds;
        protected int nextOrderId;

        NodeOrder(StructuredGraph graph) {
            this.orderIds = new NodeMap<>(graph);
            this.nextOrderId = START_NODE_ORDER_ID;

            /* Order the fixed nodes of the graph in reverse postorder. */
            Deque<AbstractBeginNode> nodeQueue = new ArrayDeque<>();
            FixedNode current = graph.start();
            do {
                add(current);
                if (current instanceof AbstractBeginNode) {
                    add(((AbstractBeginNode) current).next());
                }

                if (current instanceof FixedWithNextNode) {
                    current = ((FixedWithNextNode) current).next;
                } else {
                    if (current instanceof ControlSplitNode) {
                        for (Node successor : current.successors()) {
                            if (successor != null) {
                                nodeQueue.addFirst((AbstractBeginNode) successor);
                            }
                        }
                    } else if (current instanceof EndNode) {
                        AbstractMergeNode merge = ((AbstractEndNode) current).merge();
                        boolean allForwardEndsVisited = true;
                        for (int i = 0; i < merge.forwardEndCount(); i++) {
                            if (orderIds.get(merge.forwardEndAt(i)) == null) {
                                allForwardEndsVisited = false;
                                break;
                            }
                        }
                        if (allForwardEndsVisited) {
                            nodeQueue.add(merge);
                        }
                    }
                    current = nodeQueue.pollFirst();
                }
            } while (current != null);

            for (Node node : graph.getNodes()) {
                assert (node instanceof FixedNode) == (orderIds.get(node) != null) : "all fixed nodes must be ordered";
                add(node);
            }
        }

        private void add(Node node) {
            if (orderIds.get(node) == null) {
                orderIds.set(node, nextOrderId);
                nextOrderId++;
            }
        }
    }

    protected void writeProperties(Node node, Fields fields) {
        writeObjectId(node.getNodeSourcePosition());
        for (int idx = 0; idx < fields.getCount(); idx++) {
            if (fields.getType(idx).isPrimitive()) {
                long primitive = fields.getRawPrimitive(node, idx);
                writer.putSV(primitive);
            } else {
                Object property = fields.get(node, idx);
                writeObjectId(property);
            }
        }
    }

    protected void writeEdges(Node node, Edges edges, NodeOrder nodeOrder) {
        for (int idx = 0; idx < edges.getDirectCount(); idx++) {
            if (GraphDecoder.skipEdge(node, edges, idx, true, false)) {
                /* Edge is not needed for decoding, so we must not write it. */
                continue;
            }
            Node edge = Edges.getNode(node, edges.getOffsets(), idx);
            writeOrderId(edge, nodeOrder);
        }
        for (int idx = edges.getDirectCount(); idx < edges.getCount(); idx++) {
            if (GraphDecoder.skipEdge(node, edges, idx, false, false)) {
                /* Edge is not needed for decoding, so we must not write it. */
                continue;
            }
            NodeList<Node> edgeList = Edges.getNodeList(node, edges.getOffsets(), idx);
            if (edgeList == null) {
                writer.putSV(-1);
            } else {
                writer.putSV(edgeList.size());
                for (Node edge : edgeList) {
                    writeOrderId(edge, nodeOrder);
                }
            }
        }
    }

    protected void writeOrderId(Node node, NodeOrder nodeOrder) {
        writer.putUV(node == null ? NULL_ORDER_ID : nodeOrder.orderIds.get(node));
    }

    protected void writeObjectId(Object object) {
        writer.putUV(objects.getIndex(object));
    }

    /**
     * Verification code that checks that the decoding of an encode graph is the same as the
     * original graph.
     */
    @SuppressWarnings("try")
    public static boolean verifyEncoding(StructuredGraph originalGraph, EncodedGraph encodedGraph, Architecture architecture) {
        StructuredGraph decodedGraph = new StructuredGraph(originalGraph.method(), AllowAssumptions.YES, INVALID_COMPILATION_ID);
        GraphDecoder decoder = new GraphDecoder(architecture);
        decoder.decode(decodedGraph, encodedGraph);

        decodedGraph.verify();
        try {
            GraphComparison.verifyGraphsEqual(originalGraph, decodedGraph);
        } catch (Throwable ex) {
            try (Debug.Scope scope = Debug.scope("GraphEncoder")) {
                Debug.dump(Debug.INFO_LOG_LEVEL, originalGraph, "Original Graph");
                Debug.dump(Debug.INFO_LOG_LEVEL, decodedGraph, "Decoded Graph");
            }
            throw ex;
        }
        return true;
    }
}

class GraphComparison {
    public static boolean verifyGraphsEqual(StructuredGraph expectedGraph, StructuredGraph actualGraph) {
        NodeMap<Node> nodeMapping = new NodeMap<>(expectedGraph);
        Deque<Pair<Node, Node>> workList = new ArrayDeque<>();

        pushToWorklist(expectedGraph.start(), actualGraph.start(), nodeMapping, workList);
        while (!workList.isEmpty()) {
            Pair<Node, Node> pair = workList.removeFirst();
            Node expectedNode = pair.first;
            Node actualNode = pair.second;
            assert expectedNode.getClass() == actualNode.getClass();

            NodeClass<?> nodeClass = expectedNode.getNodeClass();
            assert nodeClass == actualNode.getNodeClass();

            if (expectedNode instanceof MergeNode) {
                /* The order of the ends can be different, so ignore them. */
                verifyNodesEqual(expectedNode.inputs(), actualNode.inputs(), nodeMapping, workList, true);
            } else if (expectedNode instanceof PhiNode) {
                verifyPhi((PhiNode) expectedNode, (PhiNode) actualNode, nodeMapping, workList);
            } else {
                verifyNodesEqual(expectedNode.inputs(), actualNode.inputs(), nodeMapping, workList, false);
            }
            verifyNodesEqual(expectedNode.successors(), actualNode.successors(), nodeMapping, workList, false);

            if (expectedNode instanceof LoopEndNode) {
                LoopEndNode actualLoopEnd = (LoopEndNode) actualNode;
                assert actualLoopEnd.loopBegin().loopEnds().snapshot().indexOf(actualLoopEnd) == actualLoopEnd.endIndex();
            } else {
                for (int i = 0; i < nodeClass.getData().getCount(); i++) {
                    Object expectedProperty = nodeClass.getData().get(expectedNode, i);
                    Object actualProperty = nodeClass.getData().get(actualNode, i);
                    assert Objects.equals(expectedProperty, actualProperty);
                }
            }

            if (expectedNode instanceof EndNode) {
                /* Visit the merge node, which is the one and only usage of the EndNode. */
                assert expectedNode.usages().count() == 1;
                assert actualNode.usages().count() == 1;
                verifyNodesEqual(expectedNode.usages(), actualNode.usages(), nodeMapping, workList, false);
            }

            if (expectedNode instanceof AbstractEndNode) {
                /* Visit the input values of the merge phi functions for this EndNode. */
                verifyPhis((AbstractEndNode) expectedNode, (AbstractEndNode) actualNode, nodeMapping, workList);
            }
        }

        return true;
    }

    protected static void verifyPhi(PhiNode expectedPhi, PhiNode actualPhi, NodeMap<Node> nodeMapping, Deque<Pair<Node, Node>> workList) {
        AbstractMergeNode expectedMergeNode = expectedPhi.merge();
        AbstractMergeNode actualMergeNode = actualPhi.merge();
        assert actualMergeNode == nodeMapping.get(expectedMergeNode);

        for (EndNode expectedEndNode : expectedMergeNode.ends) {
            EndNode actualEndNode = (EndNode) nodeMapping.get(expectedEndNode);
            if (actualEndNode != null) {
                ValueNode expectedPhiInput = expectedPhi.valueAt(expectedEndNode);
                ValueNode actualPhiInput = actualPhi.valueAt(actualEndNode);
                verifyNodeEqual(expectedPhiInput, actualPhiInput, nodeMapping, workList, false);
            }
        }
    }

    protected static void verifyPhis(AbstractEndNode expectedEndNode, AbstractEndNode actualEndNode, NodeMap<Node> nodeMapping, Deque<Pair<Node, Node>> workList) {
        AbstractMergeNode expectedMergeNode = expectedEndNode.merge();
        AbstractMergeNode actualMergeNode = (AbstractMergeNode) nodeMapping.get(expectedMergeNode);
        assert actualMergeNode != null;

        for (PhiNode expectedPhi : expectedMergeNode.phis()) {
            PhiNode actualPhi = (PhiNode) nodeMapping.get(expectedPhi);
            if (actualPhi != null) {
                ValueNode expectedPhiInput = expectedPhi.valueAt(expectedEndNode);
                ValueNode actualPhiInput = actualPhi.valueAt(actualEndNode);
                verifyNodeEqual(expectedPhiInput, actualPhiInput, nodeMapping, workList, false);
            }
        }
    }

    private static void verifyNodesEqual(NodeIterable<Node> expectedNodes, NodeIterable<Node> actualNodes, NodeMap<Node> nodeMapping, Deque<Pair<Node, Node>> workList, boolean ignoreEndNode) {
        Iterator<Node> actualIter = actualNodes.iterator();
        for (Node expectedNode : expectedNodes) {
            verifyNodeEqual(expectedNode, actualIter.next(), nodeMapping, workList, ignoreEndNode);
        }
        assert !actualIter.hasNext();
    }

    protected static void verifyNodeEqual(Node expectedNode, Node actualNode, NodeMap<Node> nodeMapping, Deque<Pair<Node, Node>> workList, boolean ignoreEndNode) {
        assert expectedNode.getClass() == actualNode.getClass();
        if (ignoreEndNode && expectedNode instanceof EndNode) {
            return;
        }

        Node existing = nodeMapping.get(expectedNode);
        if (existing != null) {
            assert existing == actualNode;
        } else {
            pushToWorklist(expectedNode, actualNode, nodeMapping, workList);
        }
    }

    protected static void pushToWorklist(Node expectedNode, Node actualNode, NodeMap<Node> nodeMapping, Deque<Pair<Node, Node>> workList) {
        nodeMapping.set(expectedNode, actualNode);
        if (expectedNode instanceof AbstractEndNode) {
            /* To ensure phi nodes have been added, we handle everything before block ends. */
            workList.addLast(new Pair<>(expectedNode, actualNode));
        } else {
            workList.addFirst(new Pair<>(expectedNode, actualNode));
        }
    }
}

class Pair<F, S> {
    public final F first;
    public final S second;

    Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }
}
