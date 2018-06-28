/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.graph.Graph.SourcePositionTracking.Default;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.core.common.CancellationBailoutException;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.JavaMethodContext;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * A graph that contains at least one distinguished node : the {@link #start() start} node. This
 * node is the start of the control flow of the graph.
 */
public final class StructuredGraph extends Graph implements JavaMethodContext {

    /**
     * The different stages of the compilation of a {@link Graph} regarding the status of
     * {@link GuardNode guards}, {@link DeoptimizingNode deoptimizations} and {@link FrameState
     * framestates}. The stage of a graph progresses monotonously.
     *
     */
    public enum GuardsStage {
        /**
         * During this stage, there can be {@link FloatingNode floating} {@link DeoptimizingNode}
         * such as {@link GuardNode GuardNodes}. New {@link DeoptimizingNode DeoptimizingNodes} can
         * be introduced without constraints. {@link FrameState} nodes are associated with
         * {@link StateSplit} nodes.
         */
        FLOATING_GUARDS,
        /**
         * During this stage, all {@link DeoptimizingNode DeoptimizingNodes} must be
         * {@link FixedNode fixed} but new {@link DeoptimizingNode DeoptimizingNodes} can still be
         * introduced. {@link FrameState} nodes are still associated with {@link StateSplit} nodes.
         */
        FIXED_DEOPTS,
        /**
         * During this stage, all {@link DeoptimizingNode DeoptimizingNodes} must be
         * {@link FixedNode fixed}. New {@link DeoptimizingNode DeoptimizingNodes} can not be
         * introduced any more. {@link FrameState} nodes are now associated with
         * {@link DeoptimizingNode} nodes.
         */
        AFTER_FSA;

        public boolean allowsFloatingGuards() {
            return this == FLOATING_GUARDS;
        }

        public boolean areFrameStatesAtDeopts() {
            return this == AFTER_FSA;
        }

        public boolean areFrameStatesAtSideEffects() {
            return !this.areFrameStatesAtDeopts();
        }

        public boolean areDeoptsFixed() {
            return this.ordinal() >= FIXED_DEOPTS.ordinal();
        }
    }

    /**
     * Constants denoting whether or not {@link Assumption}s can be made while processing a graph.
     */
    public enum AllowAssumptions {
        YES,
        NO;
        public static AllowAssumptions ifTrue(boolean flag) {
            return flag ? YES : NO;
        }

        public static AllowAssumptions ifNonNull(Assumptions assumptions) {
            return assumptions != null ? YES : NO;
        }
    }

    public static class ScheduleResult {
        private final ControlFlowGraph cfg;
        private final NodeMap<Block> nodeToBlockMap;
        private final BlockMap<List<Node>> blockToNodesMap;

        public ScheduleResult(ControlFlowGraph cfg, NodeMap<Block> nodeToBlockMap, BlockMap<List<Node>> blockToNodesMap) {
            this.cfg = cfg;
            this.nodeToBlockMap = nodeToBlockMap;
            this.blockToNodesMap = blockToNodesMap;
        }

        public ControlFlowGraph getCFG() {
            return cfg;
        }

        public NodeMap<Block> getNodeToBlockMap() {
            return nodeToBlockMap;
        }

        public BlockMap<List<Node>> getBlockToNodesMap() {
            return blockToNodesMap;
        }

        public List<Node> nodesFor(Block block) {
            return blockToNodesMap.get(block);
        }
    }

    /**
     * Object used to create a {@link StructuredGraph}.
     */
    public static class Builder {
        private String name;
        private final Assumptions assumptions;
        private SpeculationLog speculationLog;
        private ResolvedJavaMethod rootMethod;
        private CompilationIdentifier compilationId = CompilationIdentifier.INVALID_COMPILATION_ID;
        private int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
        private boolean useProfilingInfo = true;
        private boolean recordInlinedMethods = true;
        private SourcePositionTracking trackNodeSourcePosition = Default;
        private final OptionValues options;
        private Cancellable cancellable = null;
        private final DebugContext debug;
        private NodeSourcePosition callerContext;

        /**
         * Creates a builder for a graph.
         */
        public Builder(OptionValues options, DebugContext debug, AllowAssumptions allowAssumptions) {
            this.options = options;
            this.debug = debug;
            this.assumptions = allowAssumptions == AllowAssumptions.YES ? new Assumptions() : null;
            this.trackNodeSourcePosition = Graph.trackNodeSourcePositionDefault(options, debug);
        }

        /**
         * Creates a builder for a graph that does not support {@link Assumptions}.
         */
        public Builder(OptionValues options, DebugContext debug) {
            this.options = options;
            this.debug = debug;
            this.assumptions = null;
            this.trackNodeSourcePosition = Graph.trackNodeSourcePositionDefault(options, debug);
        }

        public String getName() {
            return name;
        }

        public Builder name(String s) {
            this.name = s;
            return this;
        }

        public ResolvedJavaMethod getMethod() {
            return rootMethod;
        }

        public Builder method(ResolvedJavaMethod method) {
            this.rootMethod = method;
            return this;
        }

        public DebugContext getDebug() {
            return debug;
        }

        public SpeculationLog getSpeculationLog() {
            return speculationLog;
        }

        public Builder speculationLog(SpeculationLog log) {
            this.speculationLog = log;
            return this;
        }

        public CompilationIdentifier getCompilationId() {
            return compilationId;
        }

        public Builder compilationId(CompilationIdentifier id) {
            this.compilationId = id;
            return this;
        }

        public Cancellable getCancellable() {
            return cancellable;
        }

        public Builder cancellable(Cancellable cancel) {
            this.cancellable = cancel;
            return this;
        }

        public int getEntryBCI() {
            return entryBCI;
        }

        public Builder entryBCI(int bci) {
            this.entryBCI = bci;
            return this;
        }

        public boolean getUseProfilingInfo() {
            return useProfilingInfo;
        }

        public Builder useProfilingInfo(boolean flag) {
            this.useProfilingInfo = flag;
            return this;
        }

        public boolean getRecordInlinedMethods() {
            return recordInlinedMethods;
        }

        public Builder recordInlinedMethods(boolean flag) {
            this.recordInlinedMethods = flag;
            return this;
        }

        public Builder trackNodeSourcePosition(SourcePositionTracking tracking) {
            this.trackNodeSourcePosition = tracking;
            return this;
        }

        public Builder trackNodeSourcePosition(boolean flag) {
            if (flag) {
                this.trackNodeSourcePosition = SourcePositionTracking.Track;
            }
            return this;
        }

        public Builder callerContext(NodeSourcePosition context) {
            this.callerContext = context;
            return this;
        }

        public StructuredGraph build() {
            List<ResolvedJavaMethod> inlinedMethods = recordInlinedMethods ? new ArrayList<>() : null;
            return new StructuredGraph(name, rootMethod, entryBCI, assumptions, speculationLog, useProfilingInfo, inlinedMethods,
                            trackNodeSourcePosition, compilationId, options, debug, cancellable, callerContext);
        }
    }

    public static final long INVALID_GRAPH_ID = -1;
    private static final AtomicLong uniqueGraphIds = new AtomicLong();

    private StartNode start;
    private ResolvedJavaMethod rootMethod;
    private final long graphId;
    private final CompilationIdentifier compilationId;
    private final int entryBCI;
    private GuardsStage guardsStage = GuardsStage.FLOATING_GUARDS;
    private boolean isAfterFloatingReadPhase = false;
    private boolean isAfterFixedReadPhase = false;
    private boolean hasValueProxies = true;
    private boolean isAfterExpandLogic = false;
    private final boolean useProfilingInfo;
    private final Cancellable cancellable;
    /**
     * The assumptions made while constructing and transforming this graph.
     */
    private final Assumptions assumptions;

    private SpeculationLog speculationLog;

    private ScheduleResult lastSchedule;

    private final InliningLog inliningLog;

    /**
     * Call stack (context) leading to construction of this graph.
     */
    private final NodeSourcePosition callerContext;

    /**
     * Records the methods that were used while constructing this graph, one entry for each time a
     * specific method is used. This will be {@code null} if recording of inlined methods is
     * disabled for the graph.
     */
    private final List<ResolvedJavaMethod> methods;

    /**
     * Records the fields that were accessed while constructing this graph.
     */
    private EconomicSet<ResolvedJavaField> fields = null;

    private enum UnsafeAccessState {
        NO_ACCESS,
        HAS_ACCESS,
        DISABLED
    }

    private UnsafeAccessState hasUnsafeAccess = UnsafeAccessState.NO_ACCESS;

    public static final boolean USE_PROFILING_INFO = true;

    public static final boolean NO_PROFILING_INFO = false;

    private StructuredGraph(String name,
                    ResolvedJavaMethod method,
                    int entryBCI,
                    Assumptions assumptions,
                    SpeculationLog speculationLog,
                    boolean useProfilingInfo,
                    List<ResolvedJavaMethod> methods,
                    SourcePositionTracking trackNodeSourcePosition,
                    CompilationIdentifier compilationId,
                    OptionValues options,
                    DebugContext debug,
                    Cancellable cancellable,
                    NodeSourcePosition context) {
        super(name, options, debug);
        this.setStart(add(new StartNode()));
        this.rootMethod = method;
        this.graphId = uniqueGraphIds.incrementAndGet();
        this.compilationId = compilationId;
        this.entryBCI = entryBCI;
        this.assumptions = assumptions;
        this.methods = methods;
        this.speculationLog = speculationLog;
        this.useProfilingInfo = useProfilingInfo;
        this.trackNodeSourcePosition = trackNodeSourcePosition;
        assert trackNodeSourcePosition != null;
        this.cancellable = cancellable;
        this.inliningLog = new InliningLog(rootMethod, GraalOptions.TraceInlining.getValue(options));
        this.callerContext = context;
    }

    public void setLastSchedule(ScheduleResult result) {
        lastSchedule = result;
    }

    public ScheduleResult getLastSchedule() {
        return lastSchedule;
    }

    public void clearLastSchedule() {
        setLastSchedule(null);
    }

    @Override
    public boolean maybeCompress() {
        if (super.maybeCompress()) {
            /*
             * The schedule contains a NodeMap which is unusable after compression.
             */
            clearLastSchedule();
            return true;
        }
        return false;
    }

    public Stamp getReturnStamp() {
        Stamp returnStamp = null;
        for (ReturnNode returnNode : getNodes(ReturnNode.TYPE)) {
            ValueNode result = returnNode.result();
            if (result != null) {
                if (returnStamp == null) {
                    returnStamp = result.stamp(NodeView.DEFAULT);
                } else {
                    returnStamp = returnStamp.meet(result.stamp(NodeView.DEFAULT));
                }
            }
        }
        return returnStamp;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(getClass().getSimpleName() + ":" + graphId);
        String sep = "{";
        if (name != null) {
            buf.append(sep);
            buf.append(name);
            sep = ", ";
        }
        if (method() != null) {
            buf.append(sep);
            buf.append(method());
            sep = ", ";
        }

        if (!sep.equals("{")) {
            buf.append("}");
        }
        return buf.toString();
    }

    public StartNode start() {
        return start;
    }

    /**
     * Gets the root method from which this graph was built.
     *
     * @return null if this method was not built from a method or the method is not available
     */
    public ResolvedJavaMethod method() {
        return rootMethod;
    }

    public int getEntryBCI() {
        return entryBCI;
    }

    public Cancellable getCancellable() {
        return cancellable;
    }

    public void checkCancellation() {
        if (cancellable != null && cancellable.isCancelled()) {
            CancellationBailoutException.cancelCompilation();
        }
    }

    public boolean isOSR() {
        return entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
    }

    public long graphId() {
        return graphId;
    }

    /**
     * @see CompilationIdentifier
     */
    public CompilationIdentifier compilationId() {
        return compilationId;
    }

    public void setStart(StartNode start) {
        this.start = start;
    }

    public InliningLog getInliningLog() {
        return inliningLog;
    }

    public void logInliningTree() {
        if (GraalOptions.TraceInlining.getValue(getOptions())) {
            String formattedTree = getInliningLog().formatAsTree(true);
            if (formattedTree != null) {
                TTY.println(formattedTree);
            }
        }
    }

    /**
     * Creates a copy of this graph.
     *
     * @param newName the name of the copy, used for debugging purposes (can be null)
     * @param duplicationMapCallback consumer of the duplication map created during the copying
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     */
    @Override
    protected Graph copy(String newName, Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback, DebugContext debugForCopy) {
        return copy(newName, duplicationMapCallback, compilationId, debugForCopy);
    }

    @SuppressWarnings("try")
    private StructuredGraph copy(String newName, Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback, CompilationIdentifier newCompilationId, DebugContext debugForCopy) {
        AllowAssumptions allowAssumptions = AllowAssumptions.ifNonNull(assumptions);
        StructuredGraph copy = new StructuredGraph(newName,
                        method(),
                        entryBCI,
                        assumptions == null ? null : new Assumptions(),
                        speculationLog,
                        useProfilingInfo,
                        methods != null ? new ArrayList<>(methods) : null,
                        trackNodeSourcePosition,
                        newCompilationId,
                        getOptions(), debugForCopy, null, callerContext);
        if (allowAssumptions == AllowAssumptions.YES && assumptions != null) {
            copy.assumptions.record(assumptions);
        }
        copy.hasUnsafeAccess = hasUnsafeAccess;
        copy.setGuardsStage(getGuardsStage());
        copy.isAfterFloatingReadPhase = isAfterFloatingReadPhase;
        copy.hasValueProxies = hasValueProxies;
        copy.isAfterExpandLogic = isAfterExpandLogic;
        copy.trackNodeSourcePosition = trackNodeSourcePosition;
        if (fields != null) {
            copy.fields = createFieldSet(fields);
        }
        EconomicMap<Node, Node> replacements = EconomicMap.create(Equivalence.IDENTITY);
        replacements.put(start, copy.start);
        UnmodifiableEconomicMap<Node, Node> duplicates;
        try (InliningLog.UpdateScope scope = copy.getInliningLog().openDefaultUpdateScope()) {
            duplicates = copy.addDuplicates(getNodes(), this, this.getNodeCount(), replacements);
            if (scope != null) {
                copy.getInliningLog().replaceLog(duplicates, this.getInliningLog());
            }
        }
        if (duplicationMapCallback != null) {
            duplicationMapCallback.accept(duplicates);
        }
        return copy;
    }

    /**
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     */
    public StructuredGraph copyWithIdentifier(CompilationIdentifier newCompilationId, DebugContext debugForCopy) {
        return copy(name, null, newCompilationId, debugForCopy);
    }

    public ParameterNode getParameter(int index) {
        for (ParameterNode param : getNodes(ParameterNode.TYPE)) {
            if (param.index() == index) {
                return param;
            }
        }
        return null;
    }

    public Iterable<Invoke> getInvokes() {
        final Iterator<MethodCallTargetNode> callTargets = getNodes(MethodCallTargetNode.TYPE).iterator();
        return new Iterable<Invoke>() {

            private Invoke next;

            @Override
            public Iterator<Invoke> iterator() {
                return new Iterator<Invoke>() {

                    @Override
                    public boolean hasNext() {
                        if (next == null) {
                            while (callTargets.hasNext()) {
                                Invoke i = callTargets.next().invoke();
                                if (i != null) {
                                    next = i;
                                    return true;
                                }
                            }
                            return false;
                        } else {
                            return true;
                        }
                    }

                    @Override
                    public Invoke next() {
                        try {
                            return next;
                        } finally {
                            next = null;
                        }
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public boolean hasLoops() {
        return hasNode(LoopBeginNode.TYPE);
    }

    /**
     * Unlinks a node from all its control flow neighbors and then removes it from its graph. The
     * node must have no {@linkplain Node#usages() usages}.
     *
     * @param node the node to be unlinked and removed
     */
    @SuppressWarnings("static-method")
    public void removeFixed(FixedWithNextNode node) {
        assert node != null;
        if (node instanceof AbstractBeginNode) {
            ((AbstractBeginNode) node).prepareDelete();
        }
        assert node.hasNoUsages() : node + " " + node.usages().count() + ", " + node.usages().first();
        GraphUtil.unlinkFixedNode(node);
        node.safeDelete();
    }

    public void replaceFixed(FixedWithNextNode node, Node replacement) {
        if (replacement instanceof FixedWithNextNode) {
            replaceFixedWithFixed(node, (FixedWithNextNode) replacement);
        } else {
            assert replacement != null : "cannot replace " + node + " with null";
            assert replacement instanceof FloatingNode : "cannot replace " + node + " with " + replacement;
            replaceFixedWithFloating(node, (FloatingNode) replacement);
        }
    }

    public void replaceFixedWithFixed(FixedWithNextNode node, FixedWithNextNode replacement) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        FixedNode next = node.next();
        node.setNext(null);
        replacement.setNext(next);
        node.replaceAndDelete(replacement);
        if (node == start) {
            setStart((StartNode) replacement);
        }
    }

    @SuppressWarnings("static-method")
    public void replaceFixedWithFloating(FixedWithNextNode node, ValueNode replacement) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        GraphUtil.unlinkFixedNode(node);
        node.replaceAtUsagesAndDelete(replacement);
    }

    @SuppressWarnings("static-method")
    public void removeSplit(ControlSplitNode node, AbstractBeginNode survivingSuccessor) {
        assert node != null;
        assert node.hasNoUsages();
        assert survivingSuccessor != null;
        node.clearSuccessors();
        node.replaceAtPredecessor(survivingSuccessor);
        node.safeDelete();
    }

    @SuppressWarnings("static-method")
    public void removeSplitPropagate(ControlSplitNode node, AbstractBeginNode survivingSuccessor) {
        assert node != null;
        assert node.hasNoUsages();
        assert survivingSuccessor != null;
        List<Node> snapshot = node.successors().snapshot();
        node.clearSuccessors();
        node.replaceAtPredecessor(survivingSuccessor);
        node.safeDelete();
        for (Node successor : snapshot) {
            if (successor != null && successor.isAlive()) {
                if (successor != survivingSuccessor) {
                    GraphUtil.killCFG((FixedNode) successor);
                }
            }
        }
    }

    public void replaceSplit(ControlSplitNode node, Node replacement, AbstractBeginNode survivingSuccessor) {
        if (replacement instanceof FixedWithNextNode) {
            replaceSplitWithFixed(node, (FixedWithNextNode) replacement, survivingSuccessor);
        } else {
            assert replacement != null : "cannot replace " + node + " with null";
            assert replacement instanceof FloatingNode : "cannot replace " + node + " with " + replacement;
            replaceSplitWithFloating(node, (FloatingNode) replacement, survivingSuccessor);
        }
    }

    @SuppressWarnings("static-method")
    public void replaceSplitWithFixed(ControlSplitNode node, FixedWithNextNode replacement, AbstractBeginNode survivingSuccessor) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        assert survivingSuccessor != null;
        node.clearSuccessors();
        replacement.setNext(survivingSuccessor);
        node.replaceAndDelete(replacement);
    }

    @SuppressWarnings("static-method")
    public void replaceSplitWithFloating(ControlSplitNode node, FloatingNode replacement, AbstractBeginNode survivingSuccessor) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        assert survivingSuccessor != null;
        node.clearSuccessors();
        node.replaceAtPredecessor(survivingSuccessor);
        node.replaceAtUsagesAndDelete(replacement);
    }

    @SuppressWarnings("static-method")
    public void addAfterFixed(FixedWithNextNode node, FixedNode newNode) {
        assert node != null && newNode != null && node.isAlive() && newNode.isAlive() : "cannot add " + newNode + " after " + node;
        FixedNode next = node.next();
        node.setNext(newNode);
        if (next != null) {
            assert newNode instanceof FixedWithNextNode;
            FixedWithNextNode newFixedWithNext = (FixedWithNextNode) newNode;
            assert newFixedWithNext.next() == null;
            newFixedWithNext.setNext(next);
        }
    }

    @SuppressWarnings("static-method")
    public void addBeforeFixed(FixedNode node, FixedWithNextNode newNode) {
        assert node != null && newNode != null && node.isAlive() && newNode.isAlive() : "cannot add " + newNode + " before " + node;
        assert node.predecessor() != null && node.predecessor() instanceof FixedWithNextNode : "cannot add " + newNode + " before " + node;
        assert newNode.next() == null : newNode;
        assert !(node instanceof AbstractMergeNode);
        FixedWithNextNode pred = (FixedWithNextNode) node.predecessor();
        pred.setNext(newNode);
        newNode.setNext(node);
    }

    public void reduceDegenerateLoopBegin(LoopBeginNode begin) {
        assert begin.loopEnds().isEmpty() : "Loop begin still has backedges";
        if (begin.forwardEndCount() == 1) { // bypass merge and remove
            reduceTrivialMerge(begin);
        } else { // convert to merge
            AbstractMergeNode merge = this.add(new MergeNode());
            for (EndNode end : begin.forwardEnds()) {
                merge.addForwardEnd(end);
            }
            this.replaceFixedWithFixed(begin, merge);
        }
    }

    @SuppressWarnings("static-method")
    public void reduceTrivialMerge(AbstractMergeNode merge) {
        assert merge.forwardEndCount() == 1;
        assert !(merge instanceof LoopBeginNode) || ((LoopBeginNode) merge).loopEnds().isEmpty();
        for (PhiNode phi : merge.phis().snapshot()) {
            assert phi.valueCount() == 1;
            ValueNode singleValue = phi.valueAt(0);
            if (phi.hasUsages()) {
                phi.replaceAtUsagesAndDelete(singleValue);
            } else {
                phi.safeDelete();
                if (singleValue != null) {
                    GraphUtil.tryKillUnused(singleValue);
                }
            }
        }
        // remove loop exits
        if (merge instanceof LoopBeginNode) {
            ((LoopBeginNode) merge).removeExits();
        }
        AbstractEndNode singleEnd = merge.forwardEndAt(0);
        FixedNode sux = merge.next();
        FrameState stateAfter = merge.stateAfter();
        // evacuateGuards
        merge.prepareDelete((FixedNode) singleEnd.predecessor());
        merge.safeDelete();
        if (stateAfter != null) {
            GraphUtil.tryKillUnused(stateAfter);
        }
        if (sux == null) {
            singleEnd.replaceAtPredecessor(null);
            singleEnd.safeDelete();
        } else {
            singleEnd.replaceAndDelete(sux);
        }
    }

    public GuardsStage getGuardsStage() {
        return guardsStage;
    }

    public void setGuardsStage(GuardsStage guardsStage) {
        assert guardsStage.ordinal() >= this.guardsStage.ordinal();
        this.guardsStage = guardsStage;
    }

    public boolean isAfterFloatingReadPhase() {
        return isAfterFloatingReadPhase;
    }

    public boolean isAfterFixedReadPhase() {
        return isAfterFixedReadPhase;
    }

    public void setAfterFloatingReadPhase(boolean state) {
        assert state : "cannot 'unapply' floating read phase on graph";
        isAfterFloatingReadPhase = state;
    }

    public void setAfterFixReadPhase(boolean state) {
        assert state : "cannot 'unapply' fix reads phase on graph";
        isAfterFixedReadPhase = state;
    }

    public boolean hasValueProxies() {
        return hasValueProxies;
    }

    public void setHasValueProxies(boolean state) {
        assert !state : "cannot 'unapply' value proxy removal on graph";
        hasValueProxies = state;
    }

    public boolean isAfterExpandLogic() {
        return isAfterExpandLogic;
    }

    public void setAfterExpandLogic() {
        isAfterExpandLogic = true;
    }

    /**
     * Determines if {@link ProfilingInfo} is used during construction of this graph.
     */
    public boolean useProfilingInfo() {
        return useProfilingInfo;
    }

    /**
     * Gets the profiling info for the {@linkplain #method() root method} of this graph.
     */
    public ProfilingInfo getProfilingInfo() {
        return getProfilingInfo(method());
    }

    /**
     * Gets the profiling info for a given method that is or will be part of this graph, taking into
     * account {@link #useProfilingInfo()}.
     */
    public ProfilingInfo getProfilingInfo(ResolvedJavaMethod m) {
        if (useProfilingInfo && m != null) {
            return m.getProfilingInfo();
        } else {
            return DefaultProfilingInfo.get(TriState.UNKNOWN);
        }
    }

    /**
     * Gets the object for recording assumptions while constructing of this graph.
     *
     * @return {@code null} if assumptions cannot be made for this graph
     */
    public Assumptions getAssumptions() {
        return assumptions;
    }

    /**
     * Checks that any method referenced from a {@link FrameState} is also in the set of methods
     * parsed while building this graph.
     */
    private boolean checkFrameStatesAgainstInlinedMethods() {
        for (FrameState fs : getNodes(FrameState.TYPE)) {
            if (!BytecodeFrame.isPlaceholderBci(fs.bci)) {
                ResolvedJavaMethod m = fs.code.getMethod();
                if (!m.equals(rootMethod) && !methods.contains(m)) {
                    SortedSet<String> haystack = new TreeSet<>();
                    if (!methods.contains(rootMethod)) {
                        haystack.add(rootMethod.format("%H.%n(%p)"));
                    }
                    for (ResolvedJavaMethod e : methods) {
                        haystack.add(e.format("%H.%n(%p)"));
                    }
                    throw new AssertionError(String.format("Could not find %s from %s in set(%s)", m.format("%H.%n(%p)"), fs, haystack.stream().collect(Collectors.joining(System.lineSeparator()))));
                }
            }
        }
        return true;
    }

    private static EconomicSet<ResolvedJavaField> createFieldSet(EconomicSet<ResolvedJavaField> init) {
        // Multiple ResolvedJavaField objects can represent the same field so they
        // need to be compared with equals().
        if (init != null) {
            return EconomicSet.create(Equivalence.DEFAULT, init);
        }
        return EconomicSet.create(Equivalence.DEFAULT);
    }

    /**
     * Gets an unmodifiable view of the methods that were inlined while constructing this graph.
     */
    public List<ResolvedJavaMethod> getMethods() {
        if (methods != null) {
            assert checkFrameStatesAgainstInlinedMethods();
            return Collections.unmodifiableList(methods);
        }
        return Collections.emptyList();
    }

    /**
     * Records that {@code method} was used to build this graph.
     */
    public void recordMethod(ResolvedJavaMethod method) {
        if (methods == null) {
            throw new GraalError("inlined method recording not enabled for %s", this);
        }
        methods.add(method);
    }

    /**
     * Updates the {@linkplain #getMethods() methods} used to build this graph with the methods used
     * to build another graph.
     */
    public void updateMethods(StructuredGraph other) {
        if (methods == null) {
            throw new GraalError("inlined method recording not enabled for %s", this);
        }
        if (other.rootMethod != null) {
            methods.add(other.rootMethod);
        }
        for (ResolvedJavaMethod m : other.methods) {
            methods.add(m);
        }
    }

    /**
     * Gets an unmodifiable view of the fields that were accessed while constructing this graph.
     *
     * @return {@code null} if no field accesses were recorded
     */
    public EconomicSet<ResolvedJavaField> getFields() {
        return fields;
    }

    /**
     * Records that {@code field} was accessed in this graph.
     */
    public void recordField(ResolvedJavaField field) {
        assert GraalOptions.GeneratePIC.getValue(getOptions());
        if (this.fields == null) {
            this.fields = createFieldSet(null);
        }
        fields.add(field);
    }

    /**
     * Updates the {@linkplain #getFields() fields} of this graph with the accessed fields of
     * another graph.
     */
    public void updateFields(StructuredGraph other) {
        assert this != other;
        assert GraalOptions.GeneratePIC.getValue(getOptions());
        if (other.fields != null) {
            if (this.fields == null) {
                this.fields = createFieldSet(null);
            }
            this.fields.addAll(other.fields);
        }
    }

    /**
     * Gets the input bytecode {@linkplain ResolvedJavaMethod#getCodeSize() size} from which this
     * graph is constructed. This ignores how many bytecodes in each constituent method are actually
     * parsed (which may be none for methods whose IR is retrieved from a cache or less than the
     * full amount for any given method due to profile guided branch pruning).
     */
    public int getBytecodeSize() {
        int res = 0;
        if (rootMethod != null) {
            res += rootMethod.getCodeSize();
        }
        if (methods != null) {
            for (ResolvedJavaMethod e : methods) {
                res += e.getCodeSize();
            }
        }
        return res;
    }

    /**
     *
     * @return true if the graph contains only a {@link StartNode} and {@link ReturnNode}
     */
    public boolean isTrivial() {
        return !(start.next() instanceof ReturnNode);
    }

    @Override
    public JavaMethod asJavaMethod() {
        return method();
    }

    public boolean hasUnsafeAccess() {
        return hasUnsafeAccess == UnsafeAccessState.HAS_ACCESS;
    }

    public void markUnsafeAccess() {
        if (hasUnsafeAccess == UnsafeAccessState.DISABLED) {
            return;
        }
        hasUnsafeAccess = UnsafeAccessState.HAS_ACCESS;
    }

    public void disableUnsafeAccessTracking() {
        hasUnsafeAccess = UnsafeAccessState.DISABLED;
    }

    public boolean isUnsafeAccessTrackingEnabled() {
        return hasUnsafeAccess != UnsafeAccessState.DISABLED;
    }

    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    public void clearAllStateAfter() {
        for (Node node : getNodes()) {
            if (node instanceof StateSplit) {
                FrameState stateAfter = ((StateSplit) node).stateAfter();
                if (stateAfter != null) {
                    ((StateSplit) node).setStateAfter(null);
                    // 2 nodes referencing the same framestate
                    if (stateAfter.isAlive()) {
                        GraphUtil.killWithUnusedFloatingInputs(stateAfter);
                    }
                }
            }
        }
    }

    public boolean hasVirtualizableAllocation() {
        for (Node n : getNodes()) {
            if (n instanceof VirtualizableAllocation) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void afterRegister(Node node) {
        assert hasValueProxies() || !(node instanceof ValueProxyNode);
        if (GraalOptions.TraceInlining.getValue(getOptions())) {
            if (node instanceof Invokable) {
                ((Invokable) node).updateInliningLogAfterRegister(this);
            }
        }
    }

    public NodeSourcePosition getCallerContext() {
        return callerContext;
    }
}
