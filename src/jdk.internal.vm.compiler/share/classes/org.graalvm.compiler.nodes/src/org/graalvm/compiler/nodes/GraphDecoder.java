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

import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.util.TypeReader;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeReader;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.NodeSuccessorList;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.GraphDecoder.MethodScope;
import org.graalvm.compiler.nodes.GraphDecoder.ProxyPlaceholder;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Decoder for {@link EncodedGraph encoded graphs} produced by {@link GraphEncoder}. Support for
 * loop explosion during decoding is built into this class, because it requires many interactions
 * with the decoding process. Subclasses can provide canonicalization and simplification of nodes
 * during decoding, as well as method inlining during decoding.
 */
public class GraphDecoder {

    /** Decoding state maintained for each encoded graph. */
    protected class MethodScope {
        /** The loop that contains the call. Only non-null during method inlining. */
        public final LoopScope callerLoopScope;
        /**
         * Mark for nodes that were present before the decoding of this method started. Note that
         * nodes that were decoded after the mark can still be part of an outer method, since
         * floating nodes of outer methods are decoded lazily.
         */
        public final Graph.Mark methodStartMark;
        /** The encode graph that is decoded. */
        public final EncodedGraph encodedGraph;
        /** The highest node order id that a fixed node has in the EncodedGraph. */
        public final int maxFixedNodeOrderId;
        /** Access to the encoded graph. */
        public final TypeReader reader;
        /** The kind of loop explosion to be performed during decoding. */
        public final LoopExplosionKind loopExplosion;

        /** All return nodes encountered during decoding. */
        public final List<ControlSinkNode> returnAndUnwindNodes;

        /** All merges created during loop explosion. */
        public final EconomicSet<Node> loopExplosionMerges;

        /**
         * The start of explosion, and the merge point for when irreducible loops are detected. Only
         * used when {@link MethodScope#loopExplosion} is {@link LoopExplosionKind#MERGE_EXPLODE}.
         */
        public MergeNode loopExplosionHead;

        protected MethodScope(LoopScope callerLoopScope, StructuredGraph graph, EncodedGraph encodedGraph, LoopExplosionKind loopExplosion) {
            this.callerLoopScope = callerLoopScope;
            this.methodStartMark = graph.getMark();
            this.encodedGraph = encodedGraph;
            this.loopExplosion = loopExplosion;
            this.returnAndUnwindNodes = new ArrayList<>(2);

            if (encodedGraph != null) {
                reader = UnsafeArrayTypeReader.create(encodedGraph.getEncoding(), encodedGraph.getStartOffset(), architecture.supportsUnalignedMemoryAccess());
                maxFixedNodeOrderId = reader.getUVInt();
                if (encodedGraph.nodeStartOffsets == null) {
                    int nodeCount = reader.getUVInt();
                    int[] nodeStartOffsets = new int[nodeCount];
                    for (int i = 0; i < nodeCount; i++) {
                        nodeStartOffsets[i] = encodedGraph.getStartOffset() - reader.getUVInt();
                    }
                    encodedGraph.nodeStartOffsets = nodeStartOffsets;
                }
            } else {
                reader = null;
                maxFixedNodeOrderId = 0;
            }

            if (loopExplosion != LoopExplosionKind.NONE) {
                loopExplosionMerges = EconomicSet.create(Equivalence.IDENTITY);
            } else {
                loopExplosionMerges = null;
            }
        }

        public boolean isInlinedMethod() {
            return false;
        }

        public NodeSourcePosition getCallerBytecodePosition() {
            return getCallerBytecodePosition(null);
        }

        public NodeSourcePosition getCallerBytecodePosition(NodeSourcePosition position) {
            return position;
        }

    }

    /** Decoding state maintained for each loop in the encoded graph. */
    protected static class LoopScope {
        public final MethodScope methodScope;
        public final LoopScope outer;
        public final int loopDepth;
        public final int loopIteration;
        /**
         * Upcoming loop iterations during loop explosions that have not been processed yet. Only
         * used when {@link MethodScope#loopExplosion} is not {@link LoopExplosionKind#NONE}.
         */
        public Deque<LoopScope> nextIterations;
        /**
         * Information about already processed loop iterations for state merging during loop
         * explosion. Only used when {@link MethodScope#loopExplosion} is
         * {@link LoopExplosionKind#MERGE_EXPLODE}.
         */
        public final EconomicMap<LoopExplosionState, LoopExplosionState> iterationStates;
        public final int loopBeginOrderId;
        /**
         * The worklist of fixed nodes to process. Since we already the correct processing order
         * from the orderId, we just set the orderId bit in the bitset when a node is ready for
         * processing. The lowest set bit is the next node to process.
         */
        public final BitSet nodesToProcess;
        /** Nodes that have been created, indexed by the orderId. */
        public final Node[] createdNodes;
        /**
         * Nodes that have been created in outer loop scopes and existed before starting to process
         * this loop, indexed by the orderId. Only used when {@link MethodScope#loopExplosion} is
         * not {@link LoopExplosionKind#NONE}.
         */
        public final Node[] initialCreatedNodes;

        protected LoopScope(MethodScope methodScope) {
            this.methodScope = methodScope;
            this.outer = null;
            this.nextIterations = methodScope.loopExplosion == LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN ? new ArrayDeque<>(2) : null;
            this.loopDepth = 0;
            this.loopIteration = 0;
            this.iterationStates = null;
            this.loopBeginOrderId = -1;

            int nodeCount = methodScope.encodedGraph.nodeStartOffsets.length;
            this.nodesToProcess = new BitSet(methodScope.maxFixedNodeOrderId);
            this.createdNodes = new Node[nodeCount];
            this.initialCreatedNodes = null;
        }

        protected LoopScope(MethodScope methodScope, LoopScope outer, int loopDepth, int loopIteration, int loopBeginOrderId, Node[] initialCreatedNodes, Node[] createdNodes,
                        Deque<LoopScope> nextIterations, EconomicMap<LoopExplosionState, LoopExplosionState> iterationStates) {
            this.methodScope = methodScope;
            this.outer = outer;
            this.loopDepth = loopDepth;
            this.loopIteration = loopIteration;
            this.nextIterations = nextIterations;
            this.iterationStates = iterationStates;
            this.loopBeginOrderId = loopBeginOrderId;
            this.nodesToProcess = new BitSet(methodScope.maxFixedNodeOrderId);
            this.initialCreatedNodes = initialCreatedNodes;
            this.createdNodes = createdNodes;
        }

        @Override
        public String toString() {
            return loopDepth + "," + loopIteration + (loopBeginOrderId == -1 ? "" : "#" + loopBeginOrderId);
        }
    }

    protected static class LoopExplosionState {
        public final FrameState state;
        public final MergeNode merge;
        public final int hashCode;

        protected LoopExplosionState(FrameState state, MergeNode merge) {
            this.state = state;
            this.merge = merge;

            int h = 0;
            for (ValueNode value : state.values()) {
                if (value == null) {
                    h = h * 31 + 1234;
                } else {
                    h = h * 31 + ProxyPlaceholder.unwrap(value).hashCode();
                }
            }
            this.hashCode = h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof LoopExplosionState)) {
                return false;
            }

            FrameState otherState = ((LoopExplosionState) obj).state;
            FrameState thisState = state;
            assert thisState.outerFrameState() == otherState.outerFrameState();

            Iterator<ValueNode> thisIter = thisState.values().iterator();
            Iterator<ValueNode> otherIter = otherState.values().iterator();
            while (thisIter.hasNext() && otherIter.hasNext()) {
                ValueNode thisValue = ProxyPlaceholder.unwrap(thisIter.next());
                ValueNode otherValue = ProxyPlaceholder.unwrap(otherIter.next());
                if (thisValue != otherValue) {
                    return false;
                }
            }
            return thisIter.hasNext() == otherIter.hasNext();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * Additional information encoded for {@link Invoke} nodes to allow method inlining without
     * decoding the frame state and successors beforehand.
     */
    protected static class InvokeData {
        public final Invoke invoke;
        public final ResolvedJavaType contextType;
        public final int invokeOrderId;
        public final int callTargetOrderId;
        public final int stateAfterOrderId;
        public final int nextOrderId;

        public final int nextNextOrderId;
        public final int exceptionOrderId;
        public final int exceptionStateOrderId;
        public final int exceptionNextOrderId;
        public JavaConstant constantReceiver;

        protected InvokeData(Invoke invoke, ResolvedJavaType contextType, int invokeOrderId, int callTargetOrderId, int stateAfterOrderId, int nextOrderId, int nextNextOrderId, int exceptionOrderId,
                        int exceptionStateOrderId, int exceptionNextOrderId) {
            this.invoke = invoke;
            this.contextType = contextType;
            this.invokeOrderId = invokeOrderId;
            this.callTargetOrderId = callTargetOrderId;
            this.stateAfterOrderId = stateAfterOrderId;
            this.nextOrderId = nextOrderId;
            this.nextNextOrderId = nextNextOrderId;
            this.exceptionOrderId = exceptionOrderId;
            this.exceptionStateOrderId = exceptionStateOrderId;
            this.exceptionNextOrderId = exceptionNextOrderId;
        }
    }

    /**
     * A node that is created during {@link LoopExplosionKind#MERGE_EXPLODE loop explosion} that can
     * later be replaced by a ProxyNode if {@link LoopDetector loop detection} finds out that the
     * value is defined in the loop, but used outside the loop.
     */
    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    protected static final class ProxyPlaceholder extends FloatingNode implements Canonicalizable {
        public static final NodeClass<ProxyPlaceholder> TYPE = NodeClass.create(ProxyPlaceholder.class);

        @Input ValueNode value;
        @Input(InputType.Unchecked) Node proxyPoint;

        public ProxyPlaceholder(ValueNode value, MergeNode proxyPoint) {
            super(TYPE, value.stamp(NodeView.DEFAULT));
            this.value = value;
            this.proxyPoint = proxyPoint;
        }

        void setValue(ValueNode value) {
            updateUsages(this.value, value);
            this.value = value;
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            if (tool.allUsagesAvailable()) {
                /* The node is always unnecessary after graph decoding. */
                return value;
            } else {
                return this;
            }
        }

        public static ValueNode unwrap(ValueNode value) {
            ValueNode result = value;
            while (result instanceof ProxyPlaceholder) {
                result = ((ProxyPlaceholder) result).value;
            }
            return result;
        }
    }

    protected final Architecture architecture;
    /** The target graph where decoded nodes are added to. */
    protected final StructuredGraph graph;
    protected final OptionValues options;
    protected final DebugContext debug;

    private final EconomicMap<NodeClass<?>, ArrayDeque<Node>> reusableFloatingNodes;

    public GraphDecoder(Architecture architecture, StructuredGraph graph) {
        this.architecture = architecture;
        this.graph = graph;
        this.options = graph.getOptions();
        this.debug = graph.getDebug();
        reusableFloatingNodes = EconomicMap.create(Equivalence.IDENTITY);
    }

    @SuppressWarnings("try")
    public final void decode(EncodedGraph encodedGraph) {
        try (DebugContext.Scope scope = debug.scope("GraphDecoder", graph)) {
            MethodScope methodScope = new MethodScope(null, graph, encodedGraph, LoopExplosionKind.NONE);
            decode(createInitialLoopScope(methodScope, null));
            cleanupGraph(methodScope);
            assert graph.verify();
        } catch (Throwable ex) {
            debug.handle(ex);
        }
    }

    protected final LoopScope createInitialLoopScope(MethodScope methodScope, FixedWithNextNode startNode) {
        LoopScope loopScope = new LoopScope(methodScope);
        FixedNode firstNode;
        if (startNode != null) {
            /*
             * The start node of a graph can be referenced as the guard for a GuardedNode. We
             * register the previous block node, so that such guards are correctly anchored when
             * doing inlining during graph decoding.
             */
            registerNode(loopScope, GraphEncoder.START_NODE_ORDER_ID, AbstractBeginNode.prevBegin(startNode), false, false);

            firstNode = makeStubNode(methodScope, loopScope, GraphEncoder.FIRST_NODE_ORDER_ID);
            startNode.setNext(firstNode);
            loopScope.nodesToProcess.set(GraphEncoder.FIRST_NODE_ORDER_ID);
        } else {
            firstNode = graph.start();
            registerNode(loopScope, GraphEncoder.START_NODE_ORDER_ID, firstNode, false, false);
            loopScope.nodesToProcess.set(GraphEncoder.START_NODE_ORDER_ID);
        }
        return loopScope;
    }

    protected final void decode(LoopScope initialLoopScope) {
        LoopScope loopScope = initialLoopScope;
        /* Process (inlined) methods. */
        while (loopScope != null) {
            MethodScope methodScope = loopScope.methodScope;

            /* Process loops of method. */
            while (loopScope != null) {

                /* Process nodes of loop. */
                while (!loopScope.nodesToProcess.isEmpty()) {
                    loopScope = processNextNode(methodScope, loopScope);
                    methodScope = loopScope.methodScope;
                    /*
                     * We can have entered a new loop, and we can have entered a new inlined method.
                     */
                }

                /* Finished with a loop. */
                if (loopScope.nextIterations != null && !loopScope.nextIterations.isEmpty()) {
                    /* Loop explosion: process the loop iteration. */
                    assert loopScope.nextIterations.peekFirst().loopIteration == loopScope.loopIteration + 1;
                    loopScope = loopScope.nextIterations.removeFirst();
                } else {
                    propagateCreatedNodes(loopScope);
                    loopScope = loopScope.outer;
                }
            }

            /*
             * Finished with an inlined method. Perform end-of-method cleanup tasks.
             */
            if (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE) {
                LoopDetector loopDetector = new LoopDetector(graph, methodScope);
                loopDetector.run();
            }
            if (methodScope.isInlinedMethod()) {
                finishInlining(methodScope);
            }

            /* continue with the caller */
            loopScope = methodScope.callerLoopScope;
        }
    }

    protected void finishInlining(@SuppressWarnings("unused") MethodScope inlineScope) {
    }

    private static void propagateCreatedNodes(LoopScope loopScope) {
        if (loopScope.outer == null || loopScope.createdNodes != loopScope.outer.createdNodes) {
            return;
        }

        /* Register nodes that were created while decoding the loop to the outside scope. */
        for (int i = 0; i < loopScope.createdNodes.length; i++) {
            if (loopScope.outer.createdNodes[i] == null) {
                loopScope.outer.createdNodes[i] = loopScope.createdNodes[i];
            }
        }
    }

    protected LoopScope processNextNode(MethodScope methodScope, LoopScope loopScope) {
        int nodeOrderId = loopScope.nodesToProcess.nextSetBit(0);
        loopScope.nodesToProcess.clear(nodeOrderId);

        FixedNode node = (FixedNode) lookupNode(loopScope, nodeOrderId);
        if (node.isDeleted()) {
            return loopScope;
        }

        if ((node instanceof MergeNode ||
                        (node instanceof LoopBeginNode && (methodScope.loopExplosion == LoopExplosionKind.FULL_UNROLL || methodScope.loopExplosion == LoopExplosionKind.FULL_EXPLODE ||
                                        methodScope.loopExplosion == LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN))) &&
                        ((AbstractMergeNode) node).forwardEndCount() == 1) {
            AbstractMergeNode merge = (AbstractMergeNode) node;
            EndNode singleEnd = merge.forwardEndAt(0);

            /* Nodes that would use this merge as the guard need to use the previous block. */
            registerNode(loopScope, nodeOrderId, AbstractBeginNode.prevBegin(singleEnd), true, false);

            FixedNode next = makeStubNode(methodScope, loopScope, nodeOrderId + GraphEncoder.BEGIN_NEXT_ORDER_ID_OFFSET);
            singleEnd.replaceAtPredecessor(next);

            merge.safeDelete();
            singleEnd.safeDelete();
            return loopScope;
        }

        LoopScope successorAddScope = loopScope;
        boolean updatePredecessors = true;
        if (node instanceof LoopExitNode) {
            if (methodScope.loopExplosion == LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN || (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE && loopScope.loopDepth > 1)) {
                /*
                 * We do not want to merge loop exits of inner loops. Instead, we want to keep
                 * exploding the outer loop separately for every loop exit and then merge the outer
                 * loop. Therefore, we create a new LoopScope of the outer loop for every loop exit
                 * of the inner loop.
                 */
                LoopScope outerScope = loopScope.outer;
                int nextIterationNumber = outerScope.nextIterations.isEmpty() ? outerScope.loopIteration + 1 : outerScope.nextIterations.getLast().loopIteration + 1;
                successorAddScope = new LoopScope(methodScope, outerScope.outer, outerScope.loopDepth, nextIterationNumber, outerScope.loopBeginOrderId,
                                outerScope.initialCreatedNodes == null ? null : Arrays.copyOf(outerScope.initialCreatedNodes, outerScope.initialCreatedNodes.length),
                                Arrays.copyOf(loopScope.initialCreatedNodes, loopScope.initialCreatedNodes.length), outerScope.nextIterations, outerScope.iterationStates);
                checkLoopExplosionIteration(methodScope, successorAddScope);

                /*
                 * Nodes that are still unprocessed in the outer scope might be merge nodes that are
                 * also reachable from the new exploded scope. Clearing them ensures that we do not
                 * merge, but instead keep exploding.
                 */
                for (int id = outerScope.nodesToProcess.nextSetBit(0); id >= 0; id = outerScope.nodesToProcess.nextSetBit(id + 1)) {
                    successorAddScope.createdNodes[id] = null;
                }

                outerScope.nextIterations.addLast(successorAddScope);
            } else {
                successorAddScope = loopScope.outer;
            }
            updatePredecessors = methodScope.loopExplosion == LoopExplosionKind.NONE;
        }

        methodScope.reader.setByteIndex(methodScope.encodedGraph.nodeStartOffsets[nodeOrderId]);
        int typeId = methodScope.reader.getUVInt();
        assert node.getNodeClass() == methodScope.encodedGraph.getNodeClasses()[typeId];
        makeFixedNodeInputs(methodScope, loopScope, node);
        readProperties(methodScope, node);
        makeSuccessorStubs(methodScope, successorAddScope, node, updatePredecessors);

        LoopScope resultScope = loopScope;
        if (node instanceof LoopBeginNode) {
            if (methodScope.loopExplosion != LoopExplosionKind.NONE) {
                handleLoopExplosionBegin(methodScope, loopScope, (LoopBeginNode) node);
            }

        } else if (node instanceof LoopExitNode) {
            if (methodScope.loopExplosion != LoopExplosionKind.NONE) {
                handleLoopExplosionProxyNodes(methodScope, loopScope, successorAddScope, (LoopExitNode) node, nodeOrderId);
            } else {
                handleProxyNodes(methodScope, loopScope, (LoopExitNode) node);
            }

        } else if (node instanceof MergeNode) {
            handleMergeNode(((MergeNode) node));

        } else if (node instanceof AbstractEndNode) {
            LoopScope phiInputScope = loopScope;
            LoopScope phiNodeScope = loopScope;

            if (methodScope.loopExplosion != LoopExplosionKind.NONE && node instanceof LoopEndNode) {
                node = handleLoopExplosionEnd(methodScope, loopScope, (LoopEndNode) node);
                phiNodeScope = loopScope.nextIterations.getLast();
            }

            int mergeOrderId = readOrderId(methodScope);
            AbstractMergeNode merge = (AbstractMergeNode) lookupNode(phiNodeScope, mergeOrderId);
            if (merge == null) {
                merge = (AbstractMergeNode) makeStubNode(methodScope, phiNodeScope, mergeOrderId);

                if (merge instanceof LoopBeginNode) {
                    assert phiNodeScope == phiInputScope && phiNodeScope == loopScope;
                    resultScope = new LoopScope(methodScope, loopScope, loopScope.loopDepth + 1, 0, mergeOrderId,
                                    methodScope.loopExplosion != LoopExplosionKind.NONE ? Arrays.copyOf(loopScope.createdNodes, loopScope.createdNodes.length) : null,
                                    methodScope.loopExplosion != LoopExplosionKind.NONE ? Arrays.copyOf(loopScope.createdNodes, loopScope.createdNodes.length) : loopScope.createdNodes, //
                                    methodScope.loopExplosion != LoopExplosionKind.NONE ? new ArrayDeque<>(2) : null, //
                                    methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE ? EconomicMap.create(Equivalence.DEFAULT) : null);
                    phiInputScope = resultScope;
                    phiNodeScope = resultScope;

                    if (methodScope.loopExplosion != LoopExplosionKind.NONE) {
                        registerNode(loopScope, mergeOrderId, null, true, true);
                    }
                    loopScope.nodesToProcess.clear(mergeOrderId);
                    resultScope.nodesToProcess.set(mergeOrderId);
                }
            }

            handlePhiFunctions(methodScope, phiInputScope, phiNodeScope, (AbstractEndNode) node, merge);

        } else if (node instanceof Invoke) {
            InvokeData invokeData = readInvokeData(methodScope, nodeOrderId, (Invoke) node);
            resultScope = handleInvoke(methodScope, loopScope, invokeData);

        } else if (node instanceof ReturnNode || node instanceof UnwindNode) {
            methodScope.returnAndUnwindNodes.add((ControlSinkNode) node);
        } else {
            handleFixedNode(methodScope, loopScope, nodeOrderId, node);
        }

        return resultScope;
    }

    protected InvokeData readInvokeData(MethodScope methodScope, int invokeOrderId, Invoke invoke) {
        ResolvedJavaType contextType = (ResolvedJavaType) readObject(methodScope);
        int callTargetOrderId = readOrderId(methodScope);
        int stateAfterOrderId = readOrderId(methodScope);
        int nextOrderId = readOrderId(methodScope);

        if (invoke instanceof InvokeWithExceptionNode) {
            int nextNextOrderId = readOrderId(methodScope);
            int exceptionOrderId = readOrderId(methodScope);
            int exceptionStateOrderId = readOrderId(methodScope);
            int exceptionNextOrderId = readOrderId(methodScope);
            return new InvokeData(invoke, contextType, invokeOrderId, callTargetOrderId, stateAfterOrderId, nextOrderId, nextNextOrderId, exceptionOrderId, exceptionStateOrderId,
                            exceptionNextOrderId);
        } else {
            return new InvokeData(invoke, contextType, invokeOrderId, callTargetOrderId, stateAfterOrderId, nextOrderId, -1, -1, -1, -1);
        }
    }

    /**
     * {@link Invoke} nodes do not have the {@link CallTargetNode}, {@link FrameState}, and
     * successors encoded. Instead, this information is provided separately to allow method inlining
     * without decoding and adding them to the graph upfront. For non-inlined methods, this method
     * restores the normal state. Subclasses can override it to perform method inlining.
     *
     * The return value is the loop scope where decoding should continue. When method inlining
     * should be performed, the returned loop scope must be a new loop scope for the inlined method.
     * Without inlining, the original loop scope must be returned.
     */
    protected LoopScope handleInvoke(MethodScope methodScope, LoopScope loopScope, InvokeData invokeData) {
        assert invokeData.invoke.callTarget() == null : "callTarget edge is ignored during decoding of Invoke";
        CallTargetNode callTarget = (CallTargetNode) ensureNodeCreated(methodScope, loopScope, invokeData.callTargetOrderId);
        appendInvoke(methodScope, loopScope, invokeData, callTarget);
        return loopScope;
    }

    protected void appendInvoke(MethodScope methodScope, LoopScope loopScope, InvokeData invokeData, CallTargetNode callTarget) {
        if (invokeData.invoke instanceof InvokeWithExceptionNode) {
            ((InvokeWithExceptionNode) invokeData.invoke).setCallTarget(callTarget);
        } else {
            ((InvokeNode) invokeData.invoke).setCallTarget(callTarget);
        }

        assert invokeData.invoke.stateAfter() == null && invokeData.invoke.stateDuring() == null : "FrameState edges are ignored during decoding of Invoke";
        invokeData.invoke.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, invokeData.stateAfterOrderId));

        invokeData.invoke.setNext(makeStubNode(methodScope, loopScope, invokeData.nextOrderId));
        if (invokeData.invoke instanceof InvokeWithExceptionNode) {
            ((InvokeWithExceptionNode) invokeData.invoke).setExceptionEdge((AbstractBeginNode) makeStubNode(methodScope, loopScope, invokeData.exceptionOrderId));
        }
    }

    /**
     * Hook for subclasses to perform simplifications for a non-loop-header control flow merge.
     *
     * @param merge The control flow merge.
     */
    protected void handleMergeNode(MergeNode merge) {
    }

    protected void handleLoopExplosionBegin(MethodScope methodScope, LoopScope loopScope, LoopBeginNode loopBegin) {
        checkLoopExplosionIteration(methodScope, loopScope);

        List<EndNode> predecessors = loopBegin.forwardEnds().snapshot();
        FixedNode successor = loopBegin.next();
        FrameState frameState = loopBegin.stateAfter();

        if (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE) {
            LoopExplosionState queryState = new LoopExplosionState(frameState, null);
            LoopExplosionState existingState = loopScope.iterationStates.get(queryState);
            if (existingState != null) {
                loopBegin.replaceAtUsagesAndDelete(existingState.merge);
                successor.safeDelete();
                for (EndNode predecessor : predecessors) {
                    existingState.merge.addForwardEnd(predecessor);
                }
                return;
            }
        }

        MergeNode merge = graph.add(new MergeNode());
        methodScope.loopExplosionMerges.add(merge);

        if (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE) {
            if (loopScope.iterationStates.size() == 0 && loopScope.loopDepth == 1) {
                if (methodScope.loopExplosionHead != null) {
                    throw new PermanentBailoutException("Graal implementation restriction: Method with %s loop explosion must not have more than one top-level loop", LoopExplosionKind.MERGE_EXPLODE);
                }
                methodScope.loopExplosionHead = merge;
            }

            List<ValueNode> newFrameStateValues = new ArrayList<>();
            for (ValueNode frameStateValue : frameState.values) {
                if (frameStateValue == null || frameStateValue.isConstant() || !graph.isNew(methodScope.methodStartMark, frameStateValue)) {
                    newFrameStateValues.add(frameStateValue);

                } else {
                    ProxyPlaceholder newFrameStateValue = graph.unique(new ProxyPlaceholder(frameStateValue, merge));
                    newFrameStateValues.add(newFrameStateValue);

                    /*
                     * We do not have the orderID of the value anymore, so we need to search through
                     * the complete list of nodes to find a match.
                     */
                    for (int i = 0; i < loopScope.createdNodes.length; i++) {
                        if (loopScope.createdNodes[i] == frameStateValue) {
                            loopScope.createdNodes[i] = newFrameStateValue;
                        }
                    }

                    if (loopScope.initialCreatedNodes != null) {
                        for (int i = 0; i < loopScope.initialCreatedNodes.length; i++) {
                            if (loopScope.initialCreatedNodes[i] == frameStateValue) {
                                loopScope.initialCreatedNodes[i] = newFrameStateValue;
                            }
                        }
                    }
                }
            }

            FrameState newFrameState = graph.add(new FrameState(frameState.outerFrameState(), frameState.getCode(), frameState.bci, newFrameStateValues, frameState.localsSize(),
                            frameState.stackSize(), frameState.rethrowException(), frameState.duringCall(), frameState.monitorIds(), frameState.virtualObjectMappings()));

            frameState.replaceAtUsagesAndDelete(newFrameState);
            frameState = newFrameState;
        }

        loopBegin.replaceAtUsagesAndDelete(merge);
        merge.setStateAfter(frameState);
        merge.setNext(successor);
        for (EndNode predecessor : predecessors) {
            merge.addForwardEnd(predecessor);
        }

        if (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE) {
            LoopExplosionState explosionState = new LoopExplosionState(frameState, merge);
            loopScope.iterationStates.put(explosionState, explosionState);
        }
    }

    /**
     * Hook for subclasses.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     */
    protected void checkLoopExplosionIteration(MethodScope methodScope, LoopScope loopScope) {
        throw shouldNotReachHere("when subclass uses loop explosion, it needs to implement this method");
    }

    protected FixedNode handleLoopExplosionEnd(MethodScope methodScope, LoopScope loopScope, LoopEndNode loopEnd) {
        EndNode replacementNode = graph.add(new EndNode());
        loopEnd.replaceAtPredecessor(replacementNode);
        loopEnd.safeDelete();

        assert methodScope.loopExplosion != LoopExplosionKind.NONE;
        if (methodScope.loopExplosion != LoopExplosionKind.FULL_UNROLL || loopScope.nextIterations.isEmpty()) {
            int nextIterationNumber = loopScope.nextIterations.isEmpty() ? loopScope.loopIteration + 1 : loopScope.nextIterations.getLast().loopIteration + 1;
            LoopScope nextIterationScope = new LoopScope(methodScope, loopScope.outer, loopScope.loopDepth, nextIterationNumber, loopScope.loopBeginOrderId,
                            Arrays.copyOf(loopScope.initialCreatedNodes, loopScope.initialCreatedNodes.length),
                            Arrays.copyOf(loopScope.initialCreatedNodes, loopScope.initialCreatedNodes.length), loopScope.nextIterations, loopScope.iterationStates);
            checkLoopExplosionIteration(methodScope, nextIterationScope);
            loopScope.nextIterations.addLast(nextIterationScope);
            registerNode(nextIterationScope, loopScope.loopBeginOrderId, null, true, true);
            makeStubNode(methodScope, nextIterationScope, loopScope.loopBeginOrderId);
        }
        return replacementNode;
    }

    /**
     * Hook for subclasses.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param nodeOrderId The orderId of the node.
     * @param node The node to be simplified.
     */
    protected void handleFixedNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId, FixedNode node) {
    }

    protected void handleProxyNodes(MethodScope methodScope, LoopScope loopScope, LoopExitNode loopExit) {
        assert loopExit.stateAfter() == null;
        int stateAfterOrderId = readOrderId(methodScope);
        loopExit.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, stateAfterOrderId));

        int numProxies = methodScope.reader.getUVInt();
        for (int i = 0; i < numProxies; i++) {
            int proxyOrderId = readOrderId(methodScope);
            ProxyNode proxy = (ProxyNode) ensureNodeCreated(methodScope, loopScope, proxyOrderId);
            /*
             * The ProxyNode transports a value from the loop to the outer scope. We therefore
             * register it in the outer scope.
             */
            if (loopScope.outer.createdNodes != loopScope.createdNodes) {
                registerNode(loopScope.outer, proxyOrderId, proxy, false, false);
            }
        }
    }

    protected void handleLoopExplosionProxyNodes(MethodScope methodScope, LoopScope loopScope, LoopScope outerScope, LoopExitNode loopExit, int loopExitOrderId) {
        assert loopExit.stateAfter() == null;
        int stateAfterOrderId = readOrderId(methodScope);

        BeginNode begin = graph.add(new BeginNode());

        FixedNode loopExitSuccessor = loopExit.next();
        loopExit.replaceAtPredecessor(begin);

        MergeNode loopExitPlaceholder = null;
        if (methodScope.loopExplosion == LoopExplosionKind.MERGE_EXPLODE && loopScope.loopDepth == 1) {
            /*
             * This exit might end up as a loop exit of a loop detected after partial evaluation. We
             * need to be able to create a FrameState and the necessary proxy nodes in this case.
             */
            loopExitPlaceholder = graph.add(new MergeNode());
            methodScope.loopExplosionMerges.add(loopExitPlaceholder);

            EndNode end = graph.add(new EndNode());
            begin.setNext(end);
            loopExitPlaceholder.addForwardEnd(end);

            begin = graph.add(new BeginNode());
            loopExitPlaceholder.setNext(begin);
        }

        /*
         * In the original graph, the loop exit is not a merge node. Multiple exploded loop
         * iterations now take the same loop exit, so we have to introduce a new merge node to
         * handle the merge.
         */
        MergeNode merge = null;
        Node existingExit = lookupNode(outerScope, loopExitOrderId);
        if (existingExit == null) {
            /* First loop iteration that exits. No merge necessary yet. */
            registerNode(outerScope, loopExitOrderId, begin, false, false);
            begin.setNext(loopExitSuccessor);

        } else if (existingExit instanceof BeginNode) {
            /* Second loop iteration that exits. Create the merge. */
            merge = graph.add(new MergeNode());
            registerNode(outerScope, loopExitOrderId, merge, true, false);
            /* Add the first iteration. */
            EndNode firstEnd = graph.add(new EndNode());
            ((BeginNode) existingExit).setNext(firstEnd);
            merge.addForwardEnd(firstEnd);
            merge.setNext(loopExitSuccessor);

        } else {
            /* Subsequent loop iteration. Merge already created. */
            merge = (MergeNode) existingExit;
        }

        if (merge != null) {
            EndNode end = graph.add(new EndNode());
            begin.setNext(end);
            merge.addForwardEnd(end);
        }

        /*
         * Possibly create phi nodes for the original proxy nodes that flow out of the loop. Note
         * that we definitely do not need a proxy node itself anymore, since the loop was exploded
         * and is no longer present.
         */
        int numProxies = methodScope.reader.getUVInt();
        boolean phiCreated = false;
        for (int i = 0; i < numProxies; i++) {
            int proxyOrderId = readOrderId(methodScope);
            ProxyNode proxy = (ProxyNode) ensureNodeCreated(methodScope, loopScope, proxyOrderId);
            ValueNode phiInput = proxy.value();

            if (loopExitPlaceholder != null) {
                if (!phiInput.isConstant()) {
                    phiInput = graph.unique(new ProxyPlaceholder(phiInput, loopExitPlaceholder));
                }
                registerNode(loopScope, proxyOrderId, phiInput, true, false);
            }

            ValueNode replacement;
            ValueNode existing = (ValueNode) outerScope.createdNodes[proxyOrderId];
            if (existing == null || existing == phiInput) {
                /*
                 * We are at the first loop exit, or the proxy carries the same value for all exits.
                 * We do not need a phi node yet.
                 */
                registerNode(outerScope, proxyOrderId, phiInput, true, false);
                replacement = phiInput;

            } else if (!merge.isPhiAtMerge(existing)) {
                /* Now we have two different values, so we need to create a phi node. */
                PhiNode phi;
                if (proxy instanceof ValueProxyNode) {
                    phi = graph.addWithoutUnique(new ValuePhiNode(proxy.stamp(NodeView.DEFAULT), merge));
                } else if (proxy instanceof GuardProxyNode) {
                    phi = graph.addWithoutUnique(new GuardPhiNode(merge));
                } else {
                    throw GraalError.shouldNotReachHere();
                }
                /* Add the inputs from all previous exits. */
                for (int j = 0; j < merge.phiPredecessorCount() - 1; j++) {
                    phi.addInput(existing);
                }
                /* Add the input from this exit. */
                phi.addInput(phiInput);
                registerNode(outerScope, proxyOrderId, phi, true, false);
                replacement = phi;
                phiCreated = true;

            } else {
                /* Phi node has been created before, so just add the new input. */
                PhiNode phi = (PhiNode) existing;
                phi.addInput(phiInput);
                replacement = phi;
            }

            proxy.replaceAtUsagesAndDelete(replacement);
        }

        if (loopExitPlaceholder != null) {
            registerNode(loopScope, stateAfterOrderId, null, true, true);
            loopExitPlaceholder.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, stateAfterOrderId));
        }

        if (merge != null && (merge.stateAfter() == null || phiCreated)) {
            FrameState oldStateAfter = merge.stateAfter();
            registerNode(outerScope, stateAfterOrderId, null, true, true);
            merge.setStateAfter((FrameState) ensureNodeCreated(methodScope, outerScope, stateAfterOrderId));
            if (oldStateAfter != null) {
                oldStateAfter.safeDelete();
            }
        }
        loopExit.safeDelete();
        assert loopExitSuccessor.predecessor() == null;
        if (merge != null) {
            merge.getNodeClass().getSuccessorEdges().update(merge, null, loopExitSuccessor);
        } else {
            begin.getNodeClass().getSuccessorEdges().update(begin, null, loopExitSuccessor);
        }
    }

    protected void handlePhiFunctions(MethodScope methodScope, LoopScope phiInputScope, LoopScope phiNodeScope, AbstractEndNode end, AbstractMergeNode merge) {

        if (end instanceof LoopEndNode) {
            /*
             * Fix the loop end index and the number of loop ends. When we do canonicalization
             * during decoding, we can end up with fewer ends than the encoded graph had. And the
             * order of loop ends can be different.
             */
            int numEnds = ((LoopBeginNode) merge).loopEnds().count();
            ((LoopBeginNode) merge).nextEndIndex = numEnds;
            ((LoopEndNode) end).endIndex = numEnds - 1;

        } else {
            if (merge.ends == null) {
                merge.ends = new NodeInputList<>(merge);
            }
            merge.addForwardEnd((EndNode) end);
        }

        /*
         * We create most phi functions lazily. Canonicalization and simplification during decoding
         * can lead to dead branches that are not decoded, so we might not need all phi functions
         * that the original graph contained. Since we process all predecessors before actually
         * processing the merge node, we have the final phi function when processing the merge node.
         * The only exception are loop headers of non-exploded loops: since backward branches are
         * not processed yet when processing the loop body, we need to create all phi functions
         * upfront.
         */
        boolean lazyPhi = allowLazyPhis() && (!(merge instanceof LoopBeginNode) || methodScope.loopExplosion != LoopExplosionKind.NONE);
        int numPhis = methodScope.reader.getUVInt();
        for (int i = 0; i < numPhis; i++) {
            int phiInputOrderId = readOrderId(methodScope);
            int phiNodeOrderId = readOrderId(methodScope);

            ValueNode phiInput = (ValueNode) ensureNodeCreated(methodScope, phiInputScope, phiInputOrderId);
            ValueNode existing = (ValueNode) lookupNode(phiNodeScope, phiNodeOrderId);

            if (existing != null && merge.phiPredecessorCount() == 1) {
                /*
                 * When exploding loops and the code after the loop (FULL_EXPLODE_UNTIL_RETURN),
                 * then an existing value can already be registered: Parsing of the code before the
                 * loop registers it when preparing for the later merge. The code after the loop,
                 * which starts with a clone of the values that were created before the loop, sees
                 * the stale value when processing the merge the first time. We can safely ignore
                 * the stale value because it will never be needed to be merged (we are exploding
                 * until we hit a return).
                 */
                assert methodScope.loopExplosion == LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN && phiNodeScope.loopIteration > 0;
                existing = null;
            }

            if (lazyPhi && (existing == null || existing == phiInput)) {
                /* Phi function not yet necessary. */
                registerNode(phiNodeScope, phiNodeOrderId, phiInput, true, false);

            } else if (!merge.isPhiAtMerge(existing)) {
                /*
                 * Phi function is necessary. Create it and fill it with existing inputs as well as
                 * the new input.
                 */
                registerNode(phiNodeScope, phiNodeOrderId, null, true, true);
                PhiNode phi = (PhiNode) ensureNodeCreated(methodScope, phiNodeScope, phiNodeOrderId);

                phi.setMerge(merge);
                for (int j = 0; j < merge.phiPredecessorCount() - 1; j++) {
                    phi.addInput(existing);
                }
                phi.addInput(phiInput);

            } else {
                /* Phi node has been created before, so just add the new input. */
                PhiNode phi = (PhiNode) existing;
                phi.addInput(phiInput);
            }
        }
    }

    protected boolean allowLazyPhis() {
        /* We need to exactly reproduce the encoded graph, including unnecessary phi functions. */
        return false;
    }

    protected void readProperties(MethodScope methodScope, Node node) {
        NodeSourcePosition position = (NodeSourcePosition) readObject(methodScope);
        Fields fields = node.getNodeClass().getData();
        for (int pos = 0; pos < fields.getCount(); pos++) {
            if (fields.getType(pos).isPrimitive()) {
                long primitive = methodScope.reader.getSV();
                fields.setRawPrimitive(node, pos, primitive);
            } else {
                Object value = readObject(methodScope);
                fields.putObject(node, pos, value);
            }
        }
        if (graph.trackNodeSourcePosition() && position != null) {
            NodeSourcePosition callerBytecodePosition = methodScope.getCallerBytecodePosition(position);
            node.setNodeSourcePosition(callerBytecodePosition);
            if (node instanceof DeoptimizingGuard) {
                ((DeoptimizingGuard) node).addCallerToNoDeoptSuccessorPosition(callerBytecodePosition.getCaller());
            }
        }
    }

    /**
     * Process the input edges of a node. Input nodes that have not yet been created must be
     * non-fixed nodes (because fixed nodes are processed in reverse postorder. Such non-fixed nodes
     * are created on demand (recursively since they can themselves reference not yet created
     * nodes).
     */
    protected void makeFixedNodeInputs(MethodScope methodScope, LoopScope loopScope, Node node) {
        Edges edges = node.getNodeClass().getInputEdges();
        for (int index = 0; index < edges.getDirectCount(); index++) {
            if (skipDirectEdge(node, edges, index)) {
                continue;
            }
            int orderId = readOrderId(methodScope);
            Node value = ensureNodeCreated(methodScope, loopScope, orderId);
            edges.initializeNode(node, index, value);
            if (value != null && !value.isDeleted()) {
                edges.update(node, null, value);

            }
        }

        if (node instanceof AbstractMergeNode) {
            /* The ends of merge nodes are filled manually when the ends are processed. */
            assert edges.getCount() - edges.getDirectCount() == 1 : "MergeNode has one variable size input (the ends)";
            assert Edges.getNodeList(node, edges.getOffsets(), edges.getDirectCount()) != null : "Input list must have been already created";
        } else {
            for (int index = edges.getDirectCount(); index < edges.getCount(); index++) {
                int size = methodScope.reader.getSVInt();
                if (size != -1) {
                    NodeList<Node> nodeList = new NodeInputList<>(node, size);
                    edges.initializeList(node, index, nodeList);
                    for (int idx = 0; idx < size; idx++) {
                        int orderId = readOrderId(methodScope);
                        Node value = ensureNodeCreated(methodScope, loopScope, orderId);
                        nodeList.initialize(idx, value);
                        if (value != null && !value.isDeleted()) {
                            edges.update(node, null, value);
                        }
                    }
                }
            }
        }
    }

    protected void makeFloatingNodeInputs(MethodScope methodScope, LoopScope loopScope, Node node) {
        Edges edges = node.getNodeClass().getInputEdges();
        if (node instanceof PhiNode) {
            /*
             * The inputs of phi functions are filled manually when the end nodes are processed.
             * However, the values must not be null, so initialize them with an empty list.
             */
            assert edges.getDirectCount() == 1 : "PhiNode has one direct input (the MergeNode)";
            assert edges.getCount() - edges.getDirectCount() == 1 : "PhiNode has one variable size input (the values)";
            edges.initializeList(node, edges.getDirectCount(), new NodeInputList<>(node));
        } else {
            for (int index = 0; index < edges.getDirectCount(); index++) {
                int orderId = readOrderId(methodScope);
                Node value = ensureNodeCreated(methodScope, loopScope, orderId);
                edges.initializeNode(node, index, value);
            }
            for (int index = edges.getDirectCount(); index < edges.getCount(); index++) {
                int size = methodScope.reader.getSVInt();
                if (size != -1) {
                    NodeList<Node> nodeList = new NodeInputList<>(node, size);
                    edges.initializeList(node, index, nodeList);
                    for (int idx = 0; idx < size; idx++) {
                        int orderId = readOrderId(methodScope);
                        Node value = ensureNodeCreated(methodScope, loopScope, orderId);
                        nodeList.initialize(idx, value);
                    }
                }
            }
        }
    }

    protected Node ensureNodeCreated(MethodScope methodScope, LoopScope loopScope, int nodeOrderId) {
        if (nodeOrderId == GraphEncoder.NULL_ORDER_ID) {
            return null;
        }
        Node node = lookupNode(loopScope, nodeOrderId);
        if (node != null) {
            return node;
        }

        node = decodeFloatingNode(methodScope, loopScope, nodeOrderId);
        if (node instanceof ProxyNode || node instanceof PhiNode) {
            /*
             * We need these nodes as they were in the original graph, without any canonicalization
             * or value numbering.
             */
            node = graph.addWithoutUnique(node);
        } else {
            /* Allow subclasses to canonicalize and intercept nodes. */
            Node newNode = handleFloatingNodeBeforeAdd(methodScope, loopScope, node);
            if (newNode != node) {
                releaseFloatingNode(node);
            }

            if (!newNode.isAlive()) {
                newNode = addFloatingNode(methodScope, newNode);
            }
            node = handleFloatingNodeAfterAdd(methodScope, loopScope, newNode);
        }
        registerNode(loopScope, nodeOrderId, node, false, false);
        return node;
    }

    protected Node addFloatingNode(@SuppressWarnings("unused") MethodScope methodScope, Node node) {
        /*
         * We want to exactly reproduce the encoded graph. Even though nodes should be unique in the
         * encoded graph, this is not always guaranteed.
         */
        return graph.addWithoutUnique(node);
    }

    /**
     * Decodes a non-fixed node, but does not do any post-processing and does not register it.
     */
    protected Node decodeFloatingNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId) {
        long readerByteIndex = methodScope.reader.getByteIndex();

        methodScope.reader.setByteIndex(methodScope.encodedGraph.nodeStartOffsets[nodeOrderId]);
        NodeClass<?> nodeClass = methodScope.encodedGraph.getNodeClasses()[methodScope.reader.getUVInt()];
        Node node = allocateFloatingNode(nodeClass);
        if (node instanceof FixedNode) {
            /*
             * This is a severe error that will lead to a corrupted graph, so it is better not to
             * continue decoding at all.
             */
            throw shouldNotReachHere("Not a floating node: " + node.getClass().getName());
        }

        /* Read the inputs of the node, possibly creating them recursively. */
        makeFloatingNodeInputs(methodScope, loopScope, node);

        /* Read the properties of the node. */
        readProperties(methodScope, node);
        /* There must not be any successors to read, since it is a non-fixed node. */
        assert node.getNodeClass().getEdges(Edges.Type.Successors).getCount() == 0;

        methodScope.reader.setByteIndex(readerByteIndex);
        return node;
    }

    private Node allocateFloatingNode(NodeClass<?> nodeClass) {
        ArrayDeque<? extends Node> cachedNodes = reusableFloatingNodes.get(nodeClass);
        if (cachedNodes != null) {
            Node node = cachedNodes.poll();
            if (node != null) {
                return node;
            }
        }
        return nodeClass.allocateInstance();
    }

    private void releaseFloatingNode(Node node) {
        ArrayDeque<Node> cachedNodes = reusableFloatingNodes.get(node.getNodeClass());
        if (cachedNodes == null) {
            cachedNodes = new ArrayDeque<>(2);
            reusableFloatingNodes.put(node.getNodeClass(), cachedNodes);
        }
        cachedNodes.push(node);
    }

    /**
     * Hook for subclasses to process a non-fixed node before it is added to the graph.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param node The node to be canonicalized.
     * @return The replacement for the node, or the node itself.
     */
    protected Node handleFloatingNodeBeforeAdd(MethodScope methodScope, LoopScope loopScope, Node node) {
        return node;
    }

    /**
     * Hook for subclasses to process a non-fixed node after it is added to the graph.
     *
     * If this method replaces a node with another node, it must update its source position if the
     * original node has the source position set.
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param node The node to be canonicalized.
     * @return The replacement for the node, or the node itself.
     */
    protected Node handleFloatingNodeAfterAdd(MethodScope methodScope, LoopScope loopScope, Node node) {
        return node;
    }

    /**
     * Process successor edges of a node. We create the successor nodes so that we can fill the
     * successor list, but no properties or edges are loaded yet. That is done when the successor is
     * on top of the worklist in {@link #processNextNode}.
     */
    protected void makeSuccessorStubs(MethodScope methodScope, LoopScope loopScope, Node node, boolean updatePredecessors) {
        Edges edges = node.getNodeClass().getSuccessorEdges();
        for (int index = 0; index < edges.getDirectCount(); index++) {
            if (skipDirectEdge(node, edges, index)) {
                continue;
            }
            int orderId = readOrderId(methodScope);
            Node value = makeStubNode(methodScope, loopScope, orderId);
            edges.initializeNode(node, index, value);
            if (updatePredecessors && value != null) {
                edges.update(node, null, value);
            }
        }
        for (int index = edges.getDirectCount(); index < edges.getCount(); index++) {
            int size = methodScope.reader.getSVInt();
            if (size != -1) {
                NodeList<Node> nodeList = new NodeSuccessorList<>(node, size);
                edges.initializeList(node, index, nodeList);
                for (int idx = 0; idx < size; idx++) {
                    int orderId = readOrderId(methodScope);
                    Node value = makeStubNode(methodScope, loopScope, orderId);
                    nodeList.initialize(idx, value);
                    if (updatePredecessors && value != null) {
                        edges.update(node, null, value);
                    }
                }
            }
        }
    }

    protected FixedNode makeStubNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId) {
        if (nodeOrderId == GraphEncoder.NULL_ORDER_ID) {
            return null;
        }
        FixedNode node = (FixedNode) lookupNode(loopScope, nodeOrderId);
        if (node != null) {
            return node;
        }

        long readerByteIndex = methodScope.reader.getByteIndex();
        methodScope.reader.setByteIndex(methodScope.encodedGraph.nodeStartOffsets[nodeOrderId]);
        NodeClass<?> nodeClass = methodScope.encodedGraph.getNodeClasses()[methodScope.reader.getUVInt()];
        Node stubNode = nodeClass.allocateInstance();
        if (graph.trackNodeSourcePosition()) {
            stubNode.setNodeSourcePosition(NodeSourcePosition.placeholder(graph.method()));
        }
        node = (FixedNode) graph.add(stubNode);
        /* Properties and edges are not filled yet, the node remains uninitialized. */
        methodScope.reader.setByteIndex(readerByteIndex);

        registerNode(loopScope, nodeOrderId, node, false, false);
        loopScope.nodesToProcess.set(nodeOrderId);
        return node;
    }

    protected static boolean skipDirectEdge(Node node, Edges edges, int index) {
        if (node instanceof Invoke) {
            assert node instanceof InvokeNode || node instanceof InvokeWithExceptionNode : "The only two Invoke node classes. Got " + node.getClass();
            if (edges.type() == Edges.Type.Successors) {
                assert edges.getCount() == (node instanceof InvokeWithExceptionNode ? 2 : 1) : "InvokeNode has one successor (next); InvokeWithExceptionNode has two successors (next, exceptionEdge)";
                return true;
            } else {
                assert edges.type() == Edges.Type.Inputs;
                if (edges.getType(index) == CallTargetNode.class) {
                    return true;
                } else if (edges.getType(index) == FrameState.class) {
                    assert edges.get(node, index) == null || edges.get(node, index) == ((Invoke) node).stateAfter() : "Only stateAfter can be a FrameState during encoding";
                    return true;
                }
            }
        } else if (node instanceof LoopExitNode && edges.type() == Edges.Type.Inputs && edges.getType(index) == FrameState.class) {
            /* The stateAfter of the loop exit is filled manually. */
            return true;

        }
        return false;
    }

    protected Node lookupNode(LoopScope loopScope, int nodeOrderId) {
        return loopScope.createdNodes[nodeOrderId];
    }

    protected void registerNode(LoopScope loopScope, int nodeOrderId, Node node, boolean allowOverwrite, boolean allowNull) {
        assert node == null || node.isAlive();
        assert allowNull || node != null;
        assert allowOverwrite || lookupNode(loopScope, nodeOrderId) == null;
        loopScope.createdNodes[nodeOrderId] = node;
    }

    protected int readOrderId(MethodScope methodScope) {
        return methodScope.reader.getUVInt();
    }

    protected Object readObject(MethodScope methodScope) {
        return methodScope.encodedGraph.getObjects()[methodScope.reader.getUVInt()];
    }

    /**
     * Removes unnecessary nodes from the graph after decoding.
     *
     * @param methodScope The current method.
     */
    protected void cleanupGraph(MethodScope methodScope) {
        assert verifyEdges();
    }

    protected boolean verifyEdges() {
        for (Node node : graph.getNodes()) {
            assert node.isAlive();
            for (Node i : node.inputs()) {
                assert i.isAlive();
                assert i.usages().contains(node);
            }
            for (Node s : node.successors()) {
                assert s.isAlive();
                assert s.predecessor() == node;
            }

            for (Node usage : node.usages()) {
                assert usage.isAlive();
                assert usage.inputs().contains(node) : node + " / " + usage + " / " + usage.inputs().count();
            }
            if (node.predecessor() != null) {
                assert node.predecessor().isAlive();
                assert node.predecessor().successors().contains(node);
            }
        }
        return true;
    }
}

class LoopDetector implements Runnable {

    /**
     * Information about loops before the actual loop nodes are inserted.
     */
    static class Loop {
        /**
         * The header, i.e., the target of backward branches.
         */
        MergeNode header;
        /**
         * The ends, i.e., the source of backward branches. The {@link EndNode#successors successor}
         * is the {@link #header loop header}.
         */
        List<EndNode> ends = new ArrayList<>(2);
        /**
         * Exits of the loop. The successor is a {@link MergeNode} marked in
         * {@link MethodScope#loopExplosionMerges}.
         */
        List<AbstractEndNode> exits = new ArrayList<>();
        /**
         * Set to true when the loop is irreducible, i.e., has multiple entries. See
         * {@link #handleIrreducibleLoop} for details on the handling.
         */
        boolean irreducible;
    }

    private final StructuredGraph graph;
    private final MethodScope methodScope;

    private Loop irreducibleLoopHandler;
    private IntegerSwitchNode irreducibleLoopSwitch;

    protected LoopDetector(StructuredGraph graph, MethodScope methodScope) {
        this.graph = graph;
        this.methodScope = methodScope;
    }

    @Override
    public void run() {
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "Before loop detection");

        if (methodScope.loopExplosionHead == null) {
            /*
             * The to-be-exploded loop was not reached during partial evaluation (e.g., because
             * there was a deoptimization beforehand), or the method might not even contain a loop.
             * This is an uncommon case, but not an error.
             */
            return;
        }

        List<Loop> orderedLoops = findLoops();
        assert orderedLoops.get(orderedLoops.size() - 1) == irreducibleLoopHandler : "outermost loop must be the last element in the list";

        for (Loop loop : orderedLoops) {
            if (loop.ends.isEmpty()) {
                assert loop == irreducibleLoopHandler;
                continue;
            }

            /*
             * The algorithm to find loop exits requires that inner loops have already been
             * processed. Therefore, we need to iterate the loops in order (inner loops before outer
             * loops), and we cannot find the exits for all loops before we start inserting nodes.
             */
            findLoopExits(loop);

            if (loop.irreducible) {
                handleIrreducibleLoop(loop);
            } else {
                insertLoopNodes(loop);
            }
            debug.dump(DebugContext.DETAILED_LEVEL, graph, "After handling of loop %s", loop.header);
        }

        logIrreducibleLoops();
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "After loop detection");
    }

    private List<Loop> findLoops() {
        /* Mapping from the loop header node to additional loop information. */
        EconomicMap<MergeNode, Loop> unorderedLoops = EconomicMap.create(Equivalence.IDENTITY);
        /* Loops in reverse order of, i.e., inner loops before outer loops. */
        List<Loop> orderedLoops = new ArrayList<>();

        /*
         * Ensure we have an outermost loop that we can use to eliminate irreducible loops. This
         * loop can remain empty (no ends), in which case it is ignored.
         */
        irreducibleLoopHandler = findOrCreateLoop(unorderedLoops, methodScope.loopExplosionHead);

        NodeBitMap visited = graph.createNodeBitMap();
        NodeBitMap active = graph.createNodeBitMap();
        Deque<Node> stack = new ArrayDeque<>();
        visited.mark(methodScope.loopExplosionHead);
        stack.push(methodScope.loopExplosionHead);

        while (!stack.isEmpty()) {
            Node current = stack.peek();
            assert visited.isMarked(current);

            if (active.isMarked(current)) {
                /* We are back-tracking, i.e., all successor nodes have been processed. */
                stack.pop();
                active.clear(current);

                if (current instanceof MergeNode) {
                    Loop loop = unorderedLoops.get((MergeNode) current);
                    if (loop != null) {
                        /*
                         * Since nodes are popped in reverse order that they were pushed, we add
                         * inner loops before outer loops here.
                         */
                        assert !orderedLoops.contains(loop);
                        orderedLoops.add(loop);
                    }
                }

            } else {
                /*
                 * Process the node. Note that we do not remove the node from the stack, i.e., we
                 * will peek it again. But the next time the node is marked as active, so we do not
                 * execute this code again.
                 */
                active.mark(current);
                for (Node successor : current.cfgSuccessors()) {
                    if (active.isMarked(successor)) {
                        /* Detected a cycle, i.e., a backward branch of a loop. */
                        Loop loop = findOrCreateLoop(unorderedLoops, (MergeNode) successor);
                        assert !loop.ends.contains(current);
                        loop.ends.add((EndNode) current);

                    } else if (visited.isMarked(successor)) {
                        /* Forward merge into a branch we are already exploring. */

                    } else {
                        /* Forward branch to a node we have not seen yet. */
                        visited.mark(successor);
                        stack.push(successor);
                    }
                }
            }
        }
        return orderedLoops;
    }

    private Loop findOrCreateLoop(EconomicMap<MergeNode, Loop> unorderedLoops, MergeNode loopHeader) {
        assert methodScope.loopExplosionMerges.contains(loopHeader) : loopHeader;
        Loop loop = unorderedLoops.get(loopHeader);
        if (loop == null) {
            loop = new Loop();
            loop.header = loopHeader;
            unorderedLoops.put(loopHeader, loop);
        }
        return loop;
    }

    private void findLoopExits(Loop loop) {
        /*
         * Backward marking of loop nodes: Starting with the known loop ends, we mark all nodes that
         * are reachable until we hit the loop begin. All successors of loop nodes that are not
         * marked as loop nodes themselves are exits of the loop. We mark all successors, and then
         * subtract the loop nodes, to find the exits.
         */

        List<Node> possibleExits = new ArrayList<>();
        NodeBitMap visited = graph.createNodeBitMap();
        Deque<Node> stack = new ArrayDeque<>();
        for (EndNode loopEnd : loop.ends) {
            stack.push(loopEnd);
            visited.mark(loopEnd);
        }

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if (current == loop.header) {
                continue;
            }
            if (!graph.isNew(methodScope.methodStartMark, current)) {
                /*
                 * The current node is before the method that contains the exploded loop. The loop
                 * must have a second entry point, i.e., it is an irreducible loop.
                 */
                loop.irreducible = true;
                return;
            }

            for (Node predecessor : current.cfgPredecessors()) {
                if (predecessor instanceof LoopExitNode) {
                    /*
                     * Inner loop. We do not need to mark every node of it, instead we just continue
                     * marking at the loop header.
                     */
                    LoopBeginNode innerLoopBegin = ((LoopExitNode) predecessor).loopBegin();
                    if (!visited.isMarked(innerLoopBegin)) {
                        stack.push(innerLoopBegin);
                        visited.mark(innerLoopBegin);

                        /*
                         * All loop exits of the inner loop possibly need a LoopExit of our loop.
                         * Because we are processing inner loops first, we are guaranteed to already
                         * have all exits of the inner loop.
                         */
                        for (LoopExitNode exit : innerLoopBegin.loopExits()) {
                            possibleExits.add(exit);
                        }
                    }

                } else if (!visited.isMarked(predecessor)) {
                    stack.push(predecessor);
                    visited.mark(predecessor);

                    if (predecessor instanceof ControlSplitNode) {
                        for (Node succ : predecessor.cfgSuccessors()) {
                            /*
                             * We would not need to mark the current node, and would not need to
                             * mark visited nodes. But it is easier to just mark everything, since
                             * we subtract all visited nodes in the end anyway. Note that at this
                             * point we do not have the complete visited information, so we would
                             * always mark too many possible exits.
                             */
                            possibleExits.add(succ);
                        }
                    }
                }
            }
        }

        /*
         * Now we know all the actual loop exits. Ideally, we would insert LoopExit nodes for them.
         * However, a LoopExit needs a valid FrameState that captures the state at the point where
         * we exit the loop. During graph decoding, we create a FrameState for every exploded loop
         * iteration. We need to do a forward marking until we hit the next such point. This puts
         * some nodes into the loop that are actually not part of the loop.
         *
         * In some cases, we did not create a FrameState during graph decoding: when there was no
         * LoopExit in the original loop that we exploded. This happens for code paths that lead
         * immediately to a DeoptimizeNode.
         *
         * Both cases mimic the behavior of the BytecodeParser, which also puts more nodes than
         * necessary into a loop because it computes loop information based on bytecodes, before the
         * actual parsing.
         */
        for (Node succ : possibleExits) {
            if (!visited.contains(succ)) {
                stack.push(succ);
                visited.mark(succ);
                assert !methodScope.loopExplosionMerges.contains(succ);
            }
        }

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            assert visited.isMarked(current);
            assert current instanceof ControlSinkNode || current instanceof LoopEndNode || current.cfgSuccessors().iterator().hasNext() : "Must not reach a node that has not been decoded yet";

            for (Node successor : current.cfgSuccessors()) {
                if (visited.isMarked(successor)) {
                    /* Already processed this successor. */

                } else if (methodScope.loopExplosionMerges.contains(successor)) {
                    /*
                     * We have a FrameState for the successor. The LoopExit will be inserted between
                     * the current node and the successor node. Since the successor node is a
                     * MergeNode, the current node mus be a AbstractEndNode with only that MergeNode
                     * as the successor.
                     */
                    assert successor instanceof MergeNode;
                    assert !loop.exits.contains(current);
                    loop.exits.add((AbstractEndNode) current);

                } else {
                    /* Node we have not seen yet. */
                    visited.mark(successor);
                    stack.push(successor);
                }
            }
        }
    }

    private void insertLoopNodes(Loop loop) {
        MergeNode merge = loop.header;
        FrameState stateAfter = merge.stateAfter().duplicate();
        FixedNode afterMerge = merge.next();
        merge.setNext(null);
        EndNode preLoopEnd = graph.add(new EndNode());
        LoopBeginNode loopBegin = graph.add(new LoopBeginNode());

        merge.setNext(preLoopEnd);
        /* Add the single non-loop predecessor of the loop header. */
        loopBegin.addForwardEnd(preLoopEnd);
        loopBegin.setNext(afterMerge);
        loopBegin.setStateAfter(stateAfter);

        /*
         * Phi functions of the original merge need to be split: inputs that come from forward edges
         * remain with the original phi function; inputs that come from backward edges are added to
         * new phi functions.
         */
        List<PhiNode> mergePhis = merge.phis().snapshot();
        List<PhiNode> loopBeginPhis = new ArrayList<>(mergePhis.size());
        for (int i = 0; i < mergePhis.size(); i++) {
            PhiNode mergePhi = mergePhis.get(i);
            PhiNode loopBeginPhi = graph.addWithoutUnique(new ValuePhiNode(mergePhi.stamp(NodeView.DEFAULT), loopBegin));
            mergePhi.replaceAtUsages(loopBeginPhi);
            /*
             * The first input of the new phi function is the original phi function, for the one
             * forward edge of the LoopBeginNode.
             */
            loopBeginPhi.addInput(mergePhi);
            loopBeginPhis.add(loopBeginPhi);
        }

        for (EndNode endNode : loop.ends) {
            for (int i = 0; i < mergePhis.size(); i++) {
                PhiNode mergePhi = mergePhis.get(i);
                PhiNode loopBeginPhi = loopBeginPhis.get(i);
                loopBeginPhi.addInput(mergePhi.valueAt(endNode));
            }

            merge.removeEnd(endNode);
            LoopEndNode loopEnd = graph.add(new LoopEndNode(loopBegin));
            endNode.replaceAndDelete(loopEnd);
        }

        /*
         * Insert the LoopExit nodes (the easy part) and compute the FrameState for the new exits
         * (the difficult part).
         */
        for (AbstractEndNode exit : loop.exits) {
            AbstractMergeNode loopExplosionMerge = exit.merge();
            assert methodScope.loopExplosionMerges.contains(loopExplosionMerge);

            LoopExitNode loopExit = graph.add(new LoopExitNode(loopBegin));
            exit.replaceAtPredecessor(loopExit);
            loopExit.setNext(exit);
            assignLoopExitState(loopExit, loopExplosionMerge, exit);
        }
    }

    /**
     * During graph decoding, we create a FrameState for every exploded loop iteration. This is
     * mostly the state that we want, we only need to tweak it a little bit: we need to insert the
     * appropriate ProxyNodes for all values that are created inside the loop and that flow out of
     * the loop.
     */
    private void assignLoopExitState(LoopExitNode loopExit, AbstractMergeNode loopExplosionMerge, AbstractEndNode loopExplosionEnd) {
        FrameState oldState = loopExplosionMerge.stateAfter();

        /* Collect all nodes that are in the FrameState at the LoopBegin. */
        EconomicSet<Node> loopBeginValues = EconomicSet.create(Equivalence.IDENTITY);
        for (FrameState state = loopExit.loopBegin().stateAfter(); state != null; state = state.outerFrameState()) {
            for (ValueNode value : state.values()) {
                if (value != null && !value.isConstant() && !loopExit.loopBegin().isPhiAtMerge(value)) {
                    loopBeginValues.add(ProxyPlaceholder.unwrap(value));
                }
            }
        }

        List<ValueNode> newValues = new ArrayList<>(oldState.values().size());
        for (ValueNode v : oldState.values()) {
            ValueNode value = v;
            ValueNode realValue = ProxyPlaceholder.unwrap(value);

            /*
             * The LoopExit is inserted before the existing merge, i.e., separately for every branch
             * that leads to the merge. So for phi functions of the merge, we need to take the input
             * that corresponds to our branch.
             */
            if (realValue instanceof PhiNode && loopExplosionMerge.isPhiAtMerge(realValue)) {
                value = ((PhiNode) realValue).valueAt(loopExplosionEnd);
                realValue = ProxyPlaceholder.unwrap(value);
            }

            if (realValue == null || realValue.isConstant() || loopBeginValues.contains(realValue) || !graph.isNew(methodScope.methodStartMark, realValue)) {
                newValues.add(realValue);
            } else {
                /*
                 * The node is not in the FrameState of the LoopBegin, i.e., it is a value computed
                 * inside the loop.
                 */
                GraalError.guarantee(value instanceof ProxyPlaceholder && ((ProxyPlaceholder) value).proxyPoint == loopExplosionMerge,
                                "Value flowing out of loop, but we are not prepared to insert a ProxyNode");

                ProxyPlaceholder proxyPlaceholder = (ProxyPlaceholder) value;
                ValueProxyNode proxy = ProxyNode.forValue(proxyPlaceholder.value, loopExit, graph);
                proxyPlaceholder.setValue(proxy);
                newValues.add(proxy);
            }
        }

        FrameState newState = new FrameState(oldState.outerFrameState(), oldState.getCode(), oldState.bci, newValues, oldState.localsSize(), oldState.stackSize(), oldState.rethrowException(),
                        oldState.duringCall(), oldState.monitorIds(), oldState.virtualObjectMappings());

        assert loopExit.stateAfter() == null;
        loopExit.setStateAfter(graph.add(newState));
    }

    /**
     * Graal does not support irreducible loops (loops with more than one entry point). There are
     * two ways to make them reducible: 1) duplicate nodes (peel a loop iteration starting at the
     * second entry point until we reach the first entry point), or 2) insert a big outer loop
     * covering the whole method and build a state machine for the different loop entry points.
     * Since node duplication can lead to an exponential explosion of nodes in the worst case, we
     * use the second approach.
     *
     * We already did some preparations to insert a big outer loop:
     * {@link MethodScope#loopExplosionHead} is the loop header for the outer loop, and we ensured
     * that we have a {@link Loop} data object for it in {@link #irreducibleLoopHandler}.
     *
     * Now we need to insert the state machine. We have several implementation restrictions to make
     * that efficient:
     * <ul>
     * <li>There must be only one loop variable, i.e., one value that is different in the
     * {@link FrameState} of the different loop headers.</li>
     * <li>The loop variable must use the primitive {@code int} type, because Graal only has a
     * {@link IntegerSwitchNode switch node} for {@code int}.</li>
     * <li>The values of the loop variable that are merged are {@link PrimitiveConstant compile time
     * constants}.</li>
     * </ul>
     */
    private void handleIrreducibleLoop(Loop loop) {
        assert loop != irreducibleLoopHandler;

        FrameState loopState = loop.header.stateAfter();
        FrameState explosionHeadState = irreducibleLoopHandler.header.stateAfter();
        assert loopState.outerFrameState() == explosionHeadState.outerFrameState();
        NodeInputList<ValueNode> loopValues = loopState.values();
        NodeInputList<ValueNode> explosionHeadValues = explosionHeadState.values();
        assert loopValues.size() == explosionHeadValues.size();

        /*
         * Find the loop variable, and the value of the loop variable for our loop and the outermost
         * loop. There must be exactly one loop variable.
         */
        int loopVariableIndex = -1;
        ValueNode loopValue = null;
        ValueNode explosionHeadValue = null;
        for (int i = 0; i < loopValues.size(); i++) {
            ValueNode curLoopValue = loopValues.get(i);
            ValueNode curExplosionHeadValue = explosionHeadValues.get(i);

            if (curLoopValue != curExplosionHeadValue) {
                if (loopVariableIndex != -1) {
                    throw bailout("must have only one variable that is changed in loop. " + loopValue + " != " + explosionHeadValue + " and " + curLoopValue + " != " + curExplosionHeadValue);
                }

                loopVariableIndex = i;
                loopValue = curLoopValue;
                explosionHeadValue = curExplosionHeadValue;
            }
        }
        assert loopVariableIndex != -1;

        ValuePhiNode loopVariablePhi;
        SortedMap<Integer, AbstractBeginNode> dispatchTable = new TreeMap<>();
        AbstractBeginNode unreachableDefaultSuccessor;
        if (irreducibleLoopSwitch == null) {
            /*
             * This is the first irreducible loop. We need to build the initial state machine
             * (dispatch for the loop header of the outermost loop).
             */
            assert !irreducibleLoopHandler.header.isPhiAtMerge(explosionHeadValue);
            assert irreducibleLoopHandler.header.phis().isEmpty();

            /* The new phi function for the loop variable. */
            loopVariablePhi = graph.addWithoutUnique(new ValuePhiNode(explosionHeadValue.stamp(NodeView.DEFAULT).unrestricted(), irreducibleLoopHandler.header));
            for (int i = 0; i < irreducibleLoopHandler.header.phiPredecessorCount(); i++) {
                loopVariablePhi.addInput(explosionHeadValue);
            }

            /*
             * Build the new FrameState for the loop header. There is only once change in comparison
             * to the old FrameState: the loop variable is replaced with the phi function.
             */
            FrameState oldFrameState = explosionHeadState;
            List<ValueNode> newFrameStateValues = new ArrayList<>(explosionHeadValues.size());
            for (int i = 0; i < explosionHeadValues.size(); i++) {
                if (i == loopVariableIndex) {
                    newFrameStateValues.add(loopVariablePhi);
                } else {
                    newFrameStateValues.add(explosionHeadValues.get(i));
                }
            }

            FrameState newFrameState = graph.add(
                            new FrameState(oldFrameState.outerFrameState(), oldFrameState.getCode(), oldFrameState.bci, newFrameStateValues, oldFrameState.localsSize(),
                                            oldFrameState.stackSize(), oldFrameState.rethrowException(), oldFrameState.duringCall(), oldFrameState.monitorIds(),
                                            oldFrameState.virtualObjectMappings()));
            oldFrameState.replaceAtUsages(newFrameState);

            /*
             * Disconnect the outermost loop header from its loop body, so that we can later on
             * insert the switch node. Collect dispatch information for the outermost loop.
             */
            FixedNode handlerNext = irreducibleLoopHandler.header.next();
            irreducibleLoopHandler.header.setNext(null);
            BeginNode handlerBegin = graph.add(new BeginNode());
            handlerBegin.setNext(handlerNext);
            dispatchTable.put(asInt(explosionHeadValue), handlerBegin);

            /*
             * We know that there will always be a matching key in the switch. But Graal always
             * wants a default successor, so we build a dummy block that just deoptimizes.
             */
            unreachableDefaultSuccessor = graph.add(new BeginNode());
            DeoptimizeNode deopt = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.UnreachedCode));
            unreachableDefaultSuccessor.setNext(deopt);

        } else {
            /*
             * This is the second or a subsequent irreducible loop, i.e., we already inserted a
             * switch node before. We re-create the dispatch state machine of that switch, so that
             * we can extend it with one more branch.
             */
            assert irreducibleLoopHandler.header.isPhiAtMerge(explosionHeadValue);
            assert irreducibleLoopHandler.header.phis().count() == 1 && irreducibleLoopHandler.header.phis().first() == explosionHeadValue;
            assert irreducibleLoopSwitch.value() == explosionHeadValue;

            /* We can modify the phi function used by the old switch node. */
            loopVariablePhi = (ValuePhiNode) explosionHeadValue;

            /*
             * We cannot modify the old switch node. Insert all information from the old switch node
             * into our temporary data structures for the new, larger, switch node.
             */
            for (int i = 0; i < irreducibleLoopSwitch.keyCount(); i++) {
                int key = irreducibleLoopSwitch.keyAt(i).asInt();
                dispatchTable.put(key, irreducibleLoopSwitch.successorAtKey(key));
            }
            unreachableDefaultSuccessor = irreducibleLoopSwitch.defaultSuccessor();

            /* Unlink and delete the old switch node, we do not need it anymore. */
            assert irreducibleLoopHandler.header.next() == irreducibleLoopSwitch;
            irreducibleLoopHandler.header.setNext(null);
            irreducibleLoopSwitch.clearSuccessors();
            irreducibleLoopSwitch.safeDelete();
        }

        /* Insert our loop into the dispatch state machine. */
        assert loop.header.phis().isEmpty();
        BeginNode dispatchBegin = graph.add(new BeginNode());
        EndNode dispatchEnd = graph.add(new EndNode());
        dispatchBegin.setNext(dispatchEnd);
        loop.header.addForwardEnd(dispatchEnd);
        int intLoopValue = asInt(loopValue);
        assert !dispatchTable.containsKey(intLoopValue);
        dispatchTable.put(intLoopValue, dispatchBegin);

        /* Disconnect the ends of our loop and re-connect them to the outermost loop header. */
        for (EndNode end : loop.ends) {
            loop.header.removeEnd(end);
            irreducibleLoopHandler.ends.add(end);
            irreducibleLoopHandler.header.addForwardEnd(end);
            loopVariablePhi.addInput(loopValue);
        }

        /* Build and insert the switch node. */
        irreducibleLoopSwitch = graph.add(createSwitch(loopVariablePhi, dispatchTable, unreachableDefaultSuccessor));
        irreducibleLoopHandler.header.setNext(irreducibleLoopSwitch);
    }

    private static int asInt(ValueNode node) {
        if (!node.isConstant() || node.asJavaConstant().getJavaKind() != JavaKind.Int) {
            throw bailout("must have a loop variable of type int. " + node);
        }
        return node.asJavaConstant().asInt();
    }

    private static RuntimeException bailout(String msg) {
        throw new PermanentBailoutException("Graal implementation restriction: Method with %s loop explosion %s", LoopExplosionKind.MERGE_EXPLODE, msg);
    }

    private static IntegerSwitchNode createSwitch(ValuePhiNode switchedValue, SortedMap<Integer, AbstractBeginNode> dispatchTable, AbstractBeginNode defaultSuccessor) {
        int numKeys = dispatchTable.size();
        int numSuccessors = numKeys + 1;

        AbstractBeginNode[] switchSuccessors = new AbstractBeginNode[numSuccessors];
        int[] switchKeys = new int[numKeys];
        double[] switchKeyProbabilities = new double[numSuccessors];
        int[] switchKeySuccessors = new int[numSuccessors];

        int idx = 0;
        for (Map.Entry<Integer, AbstractBeginNode> entry : dispatchTable.entrySet()) {
            switchSuccessors[idx] = entry.getValue();
            switchKeys[idx] = entry.getKey();
            switchKeyProbabilities[idx] = 1d / numKeys;
            switchKeySuccessors[idx] = idx;
            idx++;
        }
        switchSuccessors[idx] = defaultSuccessor;
        /* We know the default branch is never going to be executed. */
        switchKeyProbabilities[idx] = 0;
        switchKeySuccessors[idx] = idx;

        return new IntegerSwitchNode(switchedValue, switchSuccessors, switchKeys, switchKeyProbabilities, switchKeySuccessors);
    }

    /**
     * Print information about irreducible loops, when enabled with -Dgraal.Log=IrreducibleLoops.
     */
    @SuppressWarnings("try")
    private void logIrreducibleLoops() {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("IrreducibleLoops")) {
            if (debug.isLogEnabled(DebugContext.BASIC_LEVEL) && irreducibleLoopSwitch != null) {
                StringBuilder msg = new StringBuilder("Inserted state machine to remove irreducible loops. Dispatching to the following states: ");
                String sep = "";
                for (int i = 0; i < irreducibleLoopSwitch.keyCount(); i++) {
                    msg.append(sep).append(irreducibleLoopSwitch.keyAt(i).asInt());
                    sep = ", ";
                }
                debug.log(DebugContext.BASIC_LEVEL, "%s", msg);
            }
        }
    }
}
