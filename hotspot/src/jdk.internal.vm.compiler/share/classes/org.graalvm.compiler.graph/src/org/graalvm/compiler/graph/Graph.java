/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.compiler.core.common.CollectionsFactory;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.DebugTimer;
import org.graalvm.compiler.debug.Fingerprint;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ValueNumberable;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;

/**
 * This class is a graph container, it contains the set of nodes that belong to this graph.
 */
public class Graph {

    public static class Options {
        @Option(help = "Verify graphs often during compilation when assertions are turned on", type = OptionType.Debug)//
        public static final OptionValue<Boolean> VerifyGraalGraphs = new OptionValue<>(true);
        @Option(help = "Perform expensive verification of graph inputs, usages, successors and predecessors", type = OptionType.Debug)//
        public static final OptionValue<Boolean> VerifyGraalGraphEdges = new OptionValue<>(false);
        @Option(help = "Graal graph compression is performed when percent of live nodes falls below this value", type = OptionType.Debug)//
        public static final OptionValue<Integer> GraphCompressionThreshold = new OptionValue<>(70);
        @Option(help = "Use Unsafe to clone graph nodes thus avoiding copying fields that will be re-initialized anyway", type = OptionType.Debug)//
        public static final OptionValue<Boolean> CloneNodesWithUnsafe = new OptionValue<>(true);
    }

    public final String name;

    /**
     * The set of nodes in the graph, ordered by {@linkplain #register(Node) registration} time.
     */
    Node[] nodes;

    /**
     * Source information to associate with newly created nodes.
     */
    NodeSourcePosition currentNodeSourcePosition;

    /**
     * Records if updating of node source information is required when performing inlining.
     */
    boolean seenNodeSourcePosition;

    /**
     * The number of valid entries in {@link #nodes}.
     */
    int nodesSize;

    /**
     * Records the modification count for nodes. This is only used in assertions.
     */
    private int[] nodeModCounts;

    /**
     * Records the modification count for nodes' usage lists. This is only used in assertions.
     */
    private int[] nodeUsageModCounts;

    // these two arrays contain one entry for each NodeClass, indexed by NodeClass.iterableId.
    // they contain the first and last pointer to a linked list of all nodes with this type.
    private final ArrayList<Node> iterableNodesFirst;
    private final ArrayList<Node> iterableNodesLast;

    private int nodesDeletedSinceLastCompression;
    private int nodesDeletedBeforeLastCompression;

    /**
     * The number of times this graph has been compressed.
     */
    int compressions;

    NodeEventListener nodeEventListener;

    /**
     * Used to global value number {@link ValueNumberable} {@linkplain NodeClass#isLeafNode() leaf}
     * nodes.
     */
    private final HashMap<CacheEntry, Node> cachedLeafNodes = CollectionsFactory.newMap();

    /*
     * Indicates that the graph should no longer be modified. Frozen graphs can be used my multiple
     * threads so it's only safe to read them.
     */
    private boolean isFrozen = false;

    /**
     * Entry in {@link Graph#cachedLeafNodes}.
     */
    private static final class CacheEntry {

        private final Node node;

        CacheEntry(Node node) {
            assert node.getNodeClass().valueNumberable();
            assert node.getNodeClass().isLeafNode();
            this.node = node;
        }

        @Override
        public int hashCode() {
            return node.getNodeClass().valueNumber(node);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof CacheEntry) {
                CacheEntry other = (CacheEntry) obj;
                if (other.node.getClass() == node.getClass()) {
                    return node.valueEquals(other.node);
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return node.toString();
        }
    }

    private class NodeSourcePositionScope implements DebugCloseable {
        private final NodeSourcePosition previous;

        NodeSourcePositionScope(NodeSourcePosition sourcePosition) {
            previous = currentNodeSourcePosition;
            currentNodeSourcePosition = sourcePosition;
        }

        @Override
        public void close() {
            currentNodeSourcePosition = previous;
        }
    }

    public NodeSourcePosition currentNodeSourcePosition() {
        return currentNodeSourcePosition;
    }

    /**
     * Opens a scope in which the source information from {@code node} is copied into nodes created
     * within the scope. If {@code node} has no source information information, no scope is opened
     * and {@code null} is returned.
     *
     * @return a {@link DebugCloseable} for managing the opened scope or {@code null} if no scope
     *         was opened
     */
    public DebugCloseable withNodeSourcePosition(Node node) {
        return withNodeSourcePosition(node.sourcePosition);
    }

    /**
     * Opens a scope in which {@code sourcePosition} is copied into nodes created within the scope.
     * If {@code sourcePosition == null}, no scope is opened and {@code null} is returned.
     *
     * @return a {@link DebugCloseable} for managing the opened scope or {@code null} if no scope
     *         was opened
     */
    public DebugCloseable withNodeSourcePosition(NodeSourcePosition sourcePosition) {
        return sourcePosition != null ? new NodeSourcePositionScope(sourcePosition) : null;
    }

    /**
     * Opens a scope in which newly created nodes do not get any source information added.
     *
     * @return a {@link DebugCloseable} for managing the opened scope
     */
    public DebugCloseable withoutNodeSourcePosition() {
        return new NodeSourcePositionScope(null);
    }

    /**
     * Determines if this graph might contain nodes with source information. This is mainly useful
     * to short circuit logic for updating those positions after inlining since that requires
     * visiting every node in the graph.
     */
    public boolean mayHaveNodeSourcePosition() {
        assert seenNodeSourcePosition || verifyHasNoSourcePosition();
        return seenNodeSourcePosition;
    }

    private boolean verifyHasNoSourcePosition() {
        for (Node node : getNodes()) {
            assert node.getNodeSourcePosition() == null;
        }
        return true;
    }

    /**
     * Creates an empty Graph with no name.
     */
    public Graph() {
        this(null);
    }

    /**
     * We only want the expensive modification count tracking when assertions are enabled for the
     * {@link Graph} class.
     */
    @SuppressWarnings("all")
    public static boolean isModificationCountsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    private static final int INITIAL_NODES_SIZE = 32;

    /**
     * Creates an empty Graph with a given name.
     *
     * @param name the name of the graph, used for debugging purposes
     */
    public Graph(String name) {
        nodes = new Node[INITIAL_NODES_SIZE];
        iterableNodesFirst = new ArrayList<>(NodeClass.allocatedNodeIterabledIds());
        iterableNodesLast = new ArrayList<>(NodeClass.allocatedNodeIterabledIds());
        this.name = name;
        if (isModificationCountsEnabled()) {
            nodeModCounts = new int[INITIAL_NODES_SIZE];
            nodeUsageModCounts = new int[INITIAL_NODES_SIZE];
        }
    }

    int extractOriginalNodeId(Node node) {
        int id = node.id;
        if (id <= Node.DELETED_ID_START) {
            id = Node.DELETED_ID_START - id;
        }
        return id;
    }

    int modCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0 && id < nodeModCounts.length) {
            return nodeModCounts[id];
        }
        return 0;
    }

    void incModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0) {
            if (id >= nodeModCounts.length) {
                nodeModCounts = Arrays.copyOf(nodeModCounts, id * 2 + 30);
            }
            nodeModCounts[id]++;
        } else {
            assert false;
        }
    }

    int usageModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0 && id < nodeUsageModCounts.length) {
            return nodeUsageModCounts[id];
        }
        return 0;
    }

    void incUsageModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0) {
            if (id >= nodeUsageModCounts.length) {
                nodeUsageModCounts = Arrays.copyOf(nodeUsageModCounts, id * 2 + 30);
            }
            nodeUsageModCounts[id]++;
        } else {
            assert false;
        }
    }

    /**
     * Creates a copy of this graph.
     */
    public final Graph copy() {
        return copy(name, null);
    }

    /**
     * Creates a copy of this graph.
     *
     * @param duplicationMapCallback consumer of the duplication map created during the copying
     */
    public final Graph copy(Consumer<Map<Node, Node>> duplicationMapCallback) {
        return copy(name, duplicationMapCallback);
    }

    /**
     * Creates a copy of this graph.
     *
     * @param newName the name of the copy, used for debugging purposes (can be null)
     */
    public final Graph copy(String newName) {
        return copy(newName, null);
    }

    /**
     * Creates a copy of this graph.
     *
     * @param newName the name of the copy, used for debugging purposes (can be null)
     * @param duplicationMapCallback consumer of the duplication map created during the copying
     */
    protected Graph copy(String newName, Consumer<Map<Node, Node>> duplicationMapCallback) {
        Graph copy = new Graph(newName);
        Map<Node, Node> duplicates = copy.addDuplicates(getNodes(), this, this.getNodeCount(), (Map<Node, Node>) null);
        if (duplicationMapCallback != null) {
            duplicationMapCallback.accept(duplicates);
        }
        return copy;
    }

    @Override
    public String toString() {
        return name == null ? super.toString() : "Graph " + name;
    }

    /**
     * Gets the number of live nodes in this graph. That is the number of nodes which have been
     * added to the graph minus the number of deleted nodes.
     *
     * @return the number of live nodes in this graph
     */
    public int getNodeCount() {
        return nodesSize - getNodesDeletedSinceLastCompression();
    }

    /**
     * Gets the number of times this graph has been {@linkplain #maybeCompress() compressed}. Node
     * identifiers are only stable between compressions. To ensure this constraint is observed, any
     * entity relying upon stable node identifiers should use {@link NodeIdAccessor}.
     */
    public int getCompressions() {
        return compressions;
    }

    /**
     * Gets the number of nodes which have been deleted from this graph since it was last
     * {@linkplain #maybeCompress() compressed}.
     */
    public int getNodesDeletedSinceLastCompression() {
        return nodesDeletedSinceLastCompression;
    }

    /**
     * Gets the total number of nodes which have been deleted from this graph.
     */
    public int getTotalNodesDeleted() {
        return nodesDeletedSinceLastCompression + nodesDeletedBeforeLastCompression;
    }

    /**
     * Adds a new node to the graph.
     *
     * @param node the node to be added
     * @return the node which was added to the graph
     */
    public <T extends Node> T add(T node) {
        if (node.getNodeClass().valueNumberable()) {
            throw new IllegalStateException("Using add for value numberable node. Consider using either unique or addWithoutUnique.");
        }
        return addHelper(node);
    }

    public <T extends Node> T addWithoutUnique(T node) {
        return addHelper(node);
    }

    public <T extends Node> T addOrUnique(T node) {
        if (node.getNodeClass().valueNumberable()) {
            return uniqueHelper(node, true);
        }
        return add(node);
    }

    public <T extends Node> T addWithoutUniqueWithInputs(T node) {
        addInputs(node);
        return addHelper(node);
    }

    public <T extends Node> T addOrUniqueWithInputs(T node) {
        addInputs(node);
        if (node.getNodeClass().valueNumberable()) {
            return uniqueHelper(node, true);
        }
        return add(node);
    }

    private final class AddInputsFilter extends Node.EdgeVisitor {

        @Override
        public Node apply(Node self, Node input) {
            if (!input.isAlive()) {
                assert !input.isDeleted();
                return addOrUniqueWithInputs(input);
            } else {
                return input;
            }
        }

    }

    private AddInputsFilter addInputsFilter = new AddInputsFilter();

    private <T extends Node> void addInputs(T node) {
        node.applyInputs(addInputsFilter);
    }

    private <T extends Node> T addHelper(T node) {
        node.initialize(this);
        return node;
    }

    /**
     * The type of events sent to a {@link NodeEventListener}.
     */
    public enum NodeEvent {
        /**
         * A node's input is changed.
         */
        INPUT_CHANGED,

        /**
         * A node's {@linkplain Node#usages() usages} count dropped to zero.
         */
        ZERO_USAGES,

        /**
         * A node was added to a graph.
         */
        NODE_ADDED;
    }

    /**
     * Client interested in one or more node related events.
     */
    public interface NodeEventListener {

        /**
         * Default handler for events.
         *
         * @param e an event
         * @param node the node related to {@code e}
         */
        default void event(NodeEvent e, Node node) {
        }

        /**
         * Notifies this listener of a change in a node's inputs.
         *
         * @param node a node who has had one of its inputs changed
         */
        default void inputChanged(Node node) {
            event(NodeEvent.INPUT_CHANGED, node);
        }

        /**
         * Notifies this listener of a node becoming unused.
         *
         * @param node a node whose {@link Node#usages()} just became empty
         */
        default void usagesDroppedToZero(Node node) {
            event(NodeEvent.ZERO_USAGES, node);
        }

        /**
         * Notifies this listener of an added node.
         *
         * @param node a node that was just added to the graph
         */
        default void nodeAdded(Node node) {
            event(NodeEvent.NODE_ADDED, node);
        }
    }

    /**
     * Registers a given {@link NodeEventListener} with the enclosing graph until this object is
     * {@linkplain #close() closed}.
     */
    public final class NodeEventScope implements AutoCloseable {
        NodeEventScope(NodeEventListener listener) {
            if (nodeEventListener == null) {
                nodeEventListener = listener;
            } else {
                nodeEventListener = new ChainedNodeEventListener(listener, nodeEventListener);
            }
        }

        @Override
        public void close() {
            assert nodeEventListener != null;
            if (nodeEventListener instanceof ChainedNodeEventListener) {
                nodeEventListener = ((ChainedNodeEventListener) nodeEventListener).next;
            } else {
                nodeEventListener = null;
            }
        }
    }

    private static class ChainedNodeEventListener implements NodeEventListener {

        NodeEventListener head;
        NodeEventListener next;

        ChainedNodeEventListener(NodeEventListener head, NodeEventListener next) {
            this.head = head;
            this.next = next;
        }

        @Override
        public void nodeAdded(Node node) {
            head.nodeAdded(node);
            next.nodeAdded(node);
        }

        @Override
        public void inputChanged(Node node) {
            head.inputChanged(node);
            next.inputChanged(node);
        }

        @Override
        public void usagesDroppedToZero(Node node) {
            head.usagesDroppedToZero(node);
            next.usagesDroppedToZero(node);
        }
    }

    /**
     * Registers a given {@link NodeEventListener} with this graph. This should be used in
     * conjunction with try-with-resources statement as follows:
     *
     * <pre>
     * try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
     *     // make changes to the graph
     * }
     * </pre>
     */
    public NodeEventScope trackNodeEvents(NodeEventListener listener) {
        return new NodeEventScope(listener);
    }

    /**
     * Looks for a node <i>similar</i> to {@code node} and returns it if found. Otherwise
     * {@code node} is added to this graph and returned.
     *
     * @return a node similar to {@code node} if one exists, otherwise {@code node}
     */
    public <T extends Node & ValueNumberable> T unique(T node) {
        return uniqueHelper(node, true);
    }

    <T extends Node> T uniqueHelper(T node, boolean addIfMissing) {
        assert node.getNodeClass().valueNumberable();
        T other = this.findDuplicate(node);
        if (other != null) {
            return other;
        } else {
            T result = addIfMissing ? addHelper(node) : node;
            if (node.getNodeClass().isLeafNode()) {
                putNodeIntoCache(result);
            }
            return result;
        }
    }

    void putNodeIntoCache(Node node) {
        assert node.graph() == this || node.graph() == null;
        assert node.getNodeClass().valueNumberable();
        assert node.getNodeClass().isLeafNode() : node.getClass();
        CacheEntry entry = new CacheEntry(node);
        cachedLeafNodes.put(entry, node);
    }

    Node findNodeInCache(Node node) {
        CacheEntry key = new CacheEntry(node);
        Node result = cachedLeafNodes.get(key);
        if (result != null && result.isDeleted()) {
            cachedLeafNodes.remove(key);
            return null;
        }
        return result;
    }

    /**
     * Returns a possible duplicate for the given node in the graph or {@code null} if no such
     * duplicate exists.
     */
    @SuppressWarnings("unchecked")
    public <T extends Node> T findDuplicate(T node) {
        NodeClass<?> nodeClass = node.getNodeClass();
        assert nodeClass.valueNumberable();
        if (nodeClass.isLeafNode()) {
            // Leaf node: look up in cache
            Node cachedNode = findNodeInCache(node);
            if (cachedNode != null && cachedNode != node) {
                return (T) cachedNode;
            } else {
                return null;
            }
        } else {
            /*
             * Non-leaf node: look for another usage of the node's inputs that has the same data,
             * inputs and successors as the node. To reduce the cost of this computation, only the
             * input with lowest usage count is considered. If this node is the only user of any
             * input then the search can terminate early. The usage count is only incremented once
             * the Node is in the Graph, so account for that in the test.
             */
            final int earlyExitUsageCount = node.graph() != null ? 1 : 0;
            int minCount = Integer.MAX_VALUE;
            Node minCountNode = null;
            for (Node input : node.inputs()) {
                int usageCount = input.getUsageCount();
                if (usageCount == earlyExitUsageCount) {
                    return null;
                } else if (usageCount < minCount) {
                    minCount = usageCount;
                    minCountNode = input;
                }
            }
            if (minCountNode != null) {
                for (Node usage : minCountNode.usages()) {
                    if (usage != node && nodeClass == usage.getNodeClass() && node.valueEquals(usage) && nodeClass.equalInputs(node, usage) &&
                                    nodeClass.equalSuccessors(node, usage)) {
                        return (T) usage;
                    }
                }
                return null;
            }
            return null;
        }
    }

    public boolean isNew(Mark mark, Node node) {
        return node.id >= mark.getValue();
    }

    /**
     * A snapshot of the {@linkplain Graph#getNodeCount() live node count} in a graph.
     */
    public static class Mark extends NodeIdAccessor {
        private final int value;

        Mark(Graph graph) {
            super(graph);
            this.value = graph.nodeIdCount();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Mark) {
                Mark other = (Mark) obj;
                return other.getValue() == getValue() && other.getGraph() == getGraph();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return value ^ (epoch + 11);
        }

        /**
         * Determines if this mark is positioned at the first live node in the graph.
         */
        public boolean isStart() {
            return value == 0;
        }

        /**
         * Gets the {@linkplain Graph#getNodeCount() live node count} of the associated graph when
         * this object was created.
         */
        int getValue() {
            return value;
        }

        /**
         * Determines if this mark still represents the {@linkplain Graph#getNodeCount() live node
         * count} of the graph.
         */
        public boolean isCurrent() {
            return value == graph.nodeIdCount();
        }
    }

    /**
     * Gets a mark that can be used with {@link #getNewNodes}.
     */
    public Mark getMark() {
        return new Mark(this);
    }

    /**
     * Returns an {@link Iterable} providing all nodes added since the last {@link Graph#getMark()
     * mark}.
     */
    public NodeIterable<Node> getNewNodes(Mark mark) {
        final int index = mark == null ? 0 : mark.getValue();
        return new NodeIterable<Node>() {

            @Override
            public Iterator<Node> iterator() {
                return new GraphNodeIterator(Graph.this, index);
            }
        };
    }

    /**
     * Returns an {@link Iterable} providing all the live nodes.
     *
     * @return an {@link Iterable} providing all the live nodes.
     */
    public NodeIterable<Node> getNodes() {
        return new NodeIterable<Node>() {

            @Override
            public Iterator<Node> iterator() {
                return new GraphNodeIterator(Graph.this);
            }

            @Override
            public int count() {
                return getNodeCount();
            }
        };
    }

    // Fully qualified annotation name is required to satisfy javac
    @org.graalvm.compiler.nodeinfo.NodeInfo
    static final class PlaceHolderNode extends Node {

        public static final NodeClass<PlaceHolderNode> TYPE = NodeClass.create(PlaceHolderNode.class);

        protected PlaceHolderNode() {
            super(TYPE);
        }

    }

    static final Node PLACE_HOLDER = new PlaceHolderNode();

    public static final int COMPRESSION_THRESHOLD = Options.GraphCompressionThreshold.getValue();

    private static final DebugCounter GraphCompressions = Debug.counter("GraphCompressions");

    /**
     * If the {@linkplain #COMPRESSION_THRESHOLD compression threshold} is met, the list of nodes is
     * compressed such that all non-null entries precede all null entries while preserving the
     * ordering between the nodes within the list.
     */
    public boolean maybeCompress() {
        if (Debug.isDumpEnabledForMethod() || Debug.isLogEnabledForMethod()) {
            return false;
        }
        int liveNodeCount = getNodeCount();
        int liveNodePercent = liveNodeCount * 100 / nodesSize;
        if (COMPRESSION_THRESHOLD == 0 || liveNodePercent >= COMPRESSION_THRESHOLD) {
            return false;
        }
        GraphCompressions.increment();
        int nextId = 0;
        for (int i = 0; nextId < liveNodeCount; i++) {
            Node n = nodes[i];
            if (n != null) {
                assert n.id == i;
                if (i != nextId) {
                    assert n.id > nextId;
                    n.id = nextId;
                    nodes[nextId] = n;
                    nodes[i] = null;
                }
                nextId++;
            }
        }
        if (isModificationCountsEnabled()) {
            // This will cause any current iteration to fail with an assertion
            Arrays.fill(nodeModCounts, 0);
            Arrays.fill(nodeUsageModCounts, 0);
        }
        nodesSize = nextId;
        compressions++;
        nodesDeletedBeforeLastCompression += nodesDeletedSinceLastCompression;
        nodesDeletedSinceLastCompression = 0;
        return true;
    }

    /**
     * Returns an {@link Iterable} providing all the live nodes whose type is compatible with
     * {@code type}.
     *
     * @param nodeClass the type of node to return
     * @return an {@link Iterable} providing all the matching nodes
     */
    public <T extends Node & IterableNodeType> NodeIterable<T> getNodes(final NodeClass<T> nodeClass) {
        return new NodeIterable<T>() {

            @Override
            public Iterator<T> iterator() {
                return new TypedGraphNodeIterator<>(nodeClass, Graph.this);
            }
        };
    }

    /**
     * Returns whether the graph contains at least one node of the given type.
     *
     * @param type the type of node that is checked for occurrence
     * @return whether there is at least one such node
     */
    public <T extends Node & IterableNodeType> boolean hasNode(final NodeClass<T> type) {
        return getNodes(type).iterator().hasNext();
    }

    /**
     * @param iterableId
     * @return the first live Node with a matching iterableId
     */
    Node getIterableNodeStart(int iterableId) {
        if (iterableNodesFirst.size() <= iterableId) {
            return null;
        }
        Node start = iterableNodesFirst.get(iterableId);
        if (start == null || !start.isDeleted()) {
            return start;
        }
        return findFirstLiveIterable(iterableId, start);
    }

    private Node findFirstLiveIterable(int iterableId, Node node) {
        Node start = node;
        while (start != null && start.isDeleted()) {
            start = start.typeCacheNext;
        }
        /*
         * Multiple threads iterating nodes can update this cache simultaneously. This is a benign
         * race, since all threads update it to the same value.
         */
        iterableNodesFirst.set(iterableId, start);
        if (start == null) {
            iterableNodesLast.set(iterableId, start);
        }
        return start;
    }

    /**
     * @param node
     * @return return the first live Node with a matching iterableId starting from {@code node}
     */
    Node getIterableNodeNext(Node node) {
        if (node == null) {
            return null;
        }
        Node n = node;
        if (n == null || !n.isDeleted()) {
            return n;
        }

        return findNextLiveiterable(node);
    }

    private Node findNextLiveiterable(Node start) {
        Node n = start;
        while (n != null && n.isDeleted()) {
            n = n.typeCacheNext;
        }
        if (n == null) {
            // Only dead nodes after this one
            start.typeCacheNext = null;
            int nodeClassId = start.getNodeClass().iterableId();
            assert nodeClassId != Node.NOT_ITERABLE;
            iterableNodesLast.set(nodeClassId, start);
        } else {
            // Everything in between is dead
            start.typeCacheNext = n;
        }
        return n;
    }

    public NodeBitMap createNodeBitMap() {
        return new NodeBitMap(this);
    }

    public <T> NodeMap<T> createNodeMap() {
        return new NodeMap<>(this);
    }

    public NodeFlood createNodeFlood() {
        return new NodeFlood(this);
    }

    public NodeWorkList createNodeWorkList() {
        return new NodeWorkList.SingletonNodeWorkList(this);
    }

    public NodeWorkList createIterativeNodeWorkList(boolean fill, int iterationLimitPerNode) {
        return new NodeWorkList.IterativeNodeWorkList(this, fill, iterationLimitPerNode);
    }

    void register(Node node) {
        assert !isFrozen();
        assert node.id() == Node.INITIAL_ID;
        if (nodes.length == nodesSize) {
            Node[] newNodes = new Node[(nodesSize * 2) + 1];
            System.arraycopy(nodes, 0, newNodes, 0, nodesSize);
            nodes = newNodes;
        }
        int id = nodesSize;
        nodes[id] = node;
        if (currentNodeSourcePosition != null) {
            node.setNodeSourcePosition(currentNodeSourcePosition);
        } else if (!seenNodeSourcePosition && node.getNodeSourcePosition() != null) {
            seenNodeSourcePosition = true;
        }
        nodesSize++;

        updateNodeCaches(node);

        node.id = id;
        if (nodeEventListener != null) {
            nodeEventListener.nodeAdded(node);
        }
        if (!seenNodeSourcePosition && node.sourcePosition != null) {
            seenNodeSourcePosition = true;
        }
        if (Fingerprint.ENABLED) {
            Fingerprint.submit("%s: %s", NodeEvent.NODE_ADDED, node);
        }
    }

    @SuppressWarnings("unused")
    private void postDeserialization() {
        recomputeIterableNodeLists();
    }

    /**
     * Rebuilds the lists used to support {@link #getNodes(NodeClass)}. This is useful for
     * serialization where the underlying {@linkplain NodeClass#iterableId() iterable ids} may have
     * changed.
     */
    private void recomputeIterableNodeLists() {
        iterableNodesFirst.clear();
        iterableNodesLast.clear();
        for (Node node : nodes) {
            if (node != null && node.isAlive()) {
                updateNodeCaches(node);
            }
        }
    }

    private void updateNodeCaches(Node node) {
        int nodeClassId = node.getNodeClass().iterableId();
        if (nodeClassId != Node.NOT_ITERABLE) {
            while (iterableNodesFirst.size() <= nodeClassId) {
                iterableNodesFirst.add(null);
                iterableNodesLast.add(null);
            }
            Node prev = iterableNodesLast.get(nodeClassId);
            if (prev != null) {
                prev.typeCacheNext = node;
            } else {
                iterableNodesFirst.set(nodeClassId, node);
            }
            iterableNodesLast.set(nodeClassId, node);
        }
    }

    void unregister(Node node) {
        assert !isFrozen();
        assert !node.isDeleted() : "cannot delete a node twice! node=" + node;
        nodes[node.id] = null;
        nodesDeletedSinceLastCompression++;

        // nodes aren't removed from the type cache here - they will be removed during iteration
    }

    public boolean verify() {
        if (Options.VerifyGraalGraphs.getValue()) {
            for (Node node : getNodes()) {
                try {
                    try {
                        assert node.verify();
                    } catch (AssertionError t) {
                        throw new GraalError(t);
                    } catch (RuntimeException t) {
                        throw new GraalError(t);
                    }
                } catch (GraalError e) {
                    throw GraalGraphError.transformAndAddContext(e, node).addContext(this);
                }
            }
        }
        return true;
    }

    public Node getNode(int id) {
        return nodes[id];
    }

    /**
     * Returns the number of node ids generated so far.
     *
     * @return the number of node ids generated so far
     */
    int nodeIdCount() {
        return nodesSize;
    }

    /**
     * Adds duplicates of the nodes in {@code nodes} to this graph. This will recreate any edges
     * between the duplicate nodes. The {@code replacement} map can be used to replace a node from
     * the source graph by a given node (which must already be in this graph). Edges between
     * duplicate and replacement nodes will also be recreated so care should be taken regarding the
     * matching of node types in the replacement map.
     *
     * @param newNodes the nodes to be duplicated
     * @param replacementsMap the replacement map (can be null if no replacement is to be performed)
     * @return a map which associates the original nodes from {@code nodes} to their duplicates
     */
    public Map<Node, Node> addDuplicates(Iterable<? extends Node> newNodes, final Graph oldGraph, int estimatedNodeCount, Map<Node, Node> replacementsMap) {
        DuplicationReplacement replacements;
        if (replacementsMap == null) {
            replacements = null;
        } else {
            replacements = new MapReplacement(replacementsMap);
        }
        return addDuplicates(newNodes, oldGraph, estimatedNodeCount, replacements);
    }

    public interface DuplicationReplacement {

        Node replacement(Node original);
    }

    private static final class MapReplacement implements DuplicationReplacement {

        private final Map<Node, Node> map;

        MapReplacement(Map<Node, Node> map) {
            this.map = map;
        }

        @Override
        public Node replacement(Node original) {
            Node replacement = map.get(original);
            return replacement != null ? replacement : original;
        }

    }

    private static final DebugTimer DuplicateGraph = Debug.timer("DuplicateGraph");

    @SuppressWarnings({"all", "try"})
    public Map<Node, Node> addDuplicates(Iterable<? extends Node> newNodes, final Graph oldGraph, int estimatedNodeCount, DuplicationReplacement replacements) {
        try (DebugCloseable s = DuplicateGraph.start()) {
            return NodeClass.addGraphDuplicate(this, oldGraph, estimatedNodeCount, newNodes, replacements);
        }
    }

    /**
     * Reverses the usage orders of all nodes. This is used for debugging to make sure an unorthodox
     * usage order does not trigger bugs in the compiler.
     */
    public void reverseUsageOrder() {
        for (Node n : getNodes()) {
            n.reverseUsageOrder();
        }
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    public void freeze() {
        this.isFrozen = true;
    }
}
