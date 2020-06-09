/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.virtual.phases.ea;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntUnaryOperator;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.NodeWithState;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.virtual.nodes.VirtualObjectState;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public abstract class PartialEscapeClosure<BlockT extends PartialEscapeBlockState<BlockT>> extends EffectsClosure<BlockT> {

    public static final CounterKey COUNTER_MATERIALIZATIONS = DebugContext.counter("Materializations");
    public static final CounterKey COUNTER_MATERIALIZATIONS_PHI = DebugContext.counter("MaterializationsPhi");
    public static final CounterKey COUNTER_MATERIALIZATIONS_MERGE = DebugContext.counter("MaterializationsMerge");
    public static final CounterKey COUNTER_MATERIALIZATIONS_UNHANDLED = DebugContext.counter("MaterializationsUnhandled");
    public static final CounterKey COUNTER_MATERIALIZATIONS_LOOP_REITERATION = DebugContext.counter("MaterializationsLoopReiteration");
    public static final CounterKey COUNTER_MATERIALIZATIONS_LOOP_END = DebugContext.counter("MaterializationsLoopEnd");
    public static final CounterKey COUNTER_ALLOCATION_REMOVED = DebugContext.counter("AllocationsRemoved");
    public static final CounterKey COUNTER_MEMORYCHECKPOINT = DebugContext.counter("MemoryCheckpoint");

    /**
     * Nodes with inputs that were modified during analysis are marked in this bitset - this way
     * nodes that are not influenced at all by analysis can be rejected quickly.
     */
    private final NodeBitMap hasVirtualInputs;

    /**
     * This is handed out to implementers of {@link Virtualizable}.
     */
    protected final VirtualizerToolImpl tool;

    /**
     * The indexes into this array correspond to {@link VirtualObjectNode#getObjectId()}.
     */
    public final ArrayList<VirtualObjectNode> virtualObjects = new ArrayList<>();

    @Override
    public boolean needsApplyEffects() {
        if (hasChanged()) {
            return true;
        }
        /*
         * If there is a mismatch between the number of materializations and the number of
         * virtualizations, we need to apply effects, even if there were no other significant
         * changes to the graph. This applies to each block, since moving from one block to the
         * other can also be important (if the probabilities of the block differ).
         */
        for (Block block : cfg.getBlocks()) {
            GraphEffectList effects = blockEffects.get(block);
            if (effects != null) {
                if (effects.getVirtualizationDelta() != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private final class CollectVirtualObjectsClosure2 extends VirtualState.NodePositionClosure<Node> {
        private final EconomicSet<VirtualObjectNode> virtual;
        private final GraphEffectList effects;
        private final BlockT state;

        private CollectVirtualObjectsClosure2(EconomicSet<VirtualObjectNode> virtual, GraphEffectList effects, BlockT state) {
            this.virtual = virtual;
            this.effects = effects;
            this.state = state;
        }

        @Override
        public void apply(Node from, Position p) {
            ValueNode value = (ValueNode) p.get(from);
            Node usage = from;
            if (value instanceof VirtualObjectNode) {
                VirtualObjectNode object = (VirtualObjectNode) value;
                if (object.getObjectId() != -1 && state.getObjectStateOptional(object) != null) {
                    virtual.add(object);
                }
            } else {
                ValueNode alias = getAlias(value);
                if (alias instanceof VirtualObjectNode) {
                    VirtualObjectNode object = (VirtualObjectNode) alias;
                    virtual.add(object);
                    effects.replaceFirstInput(usage, value, object);
                }
            }
        }

    }

    /**
     * Final subclass of PartialEscapeClosure, for performance and to make everything behave nicely
     * with generics.
     */
    public static final class Final extends PartialEscapeClosure<PartialEscapeBlockState.Final> {

        public Final(ScheduleResult schedule, CoreProviders providers) {
            super(schedule, providers);
        }

        @Override
        protected PartialEscapeBlockState.Final getInitialState() {
            return new PartialEscapeBlockState.Final(tool.getOptions(), tool.getDebug());
        }

        @Override
        protected PartialEscapeBlockState.Final cloneState(PartialEscapeBlockState.Final oldState) {
            return new PartialEscapeBlockState.Final(oldState);
        }
    }

    public PartialEscapeClosure(ScheduleResult schedule, CoreProviders providers) {
        super(schedule, schedule.getCFG());
        StructuredGraph graph = schedule.getCFG().graph;
        this.hasVirtualInputs = graph.createNodeBitMap();
        this.tool = new VirtualizerToolImpl(providers, this, graph.getAssumptions(), graph.getOptions(), debug);
    }

    /**
     * @return true if the node was deleted, false otherwise
     */
    @Override
    protected boolean processNode(Node node, BlockT state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        /*
         * These checks make up for the fact that an earliest schedule moves CallTargetNodes upwards
         * and thus materializes virtual objects needlessly. Also, FrameStates and ConstantNodes are
         * scheduled, but can safely be ignored.
         */
        if (node instanceof CallTargetNode || node instanceof FrameState || node instanceof ConstantNode) {
            return false;
        } else if (node instanceof Invoke) {
            processNodeInternal(((Invoke) node).callTarget(), state, effects, lastFixedNode);
        }
        return processNodeInternal(node, state, effects, lastFixedNode);
    }

    private boolean processNodeInternal(Node node, BlockT state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        FixedNode nextFixedNode = lastFixedNode == null ? null : lastFixedNode.next();
        VirtualUtil.trace(node.getOptions(), debug, "%s", node);

        if (requiresProcessing(node)) {
            if (processVirtualizable((ValueNode) node, nextFixedNode, state, effects) == false) {
                return false;
            }
            if (tool.isDeleted()) {
                VirtualUtil.trace(node.getOptions(), debug, "deleted virtualizable allocation %s", node);
                return true;
            }
        }
        if (hasVirtualInputs.isMarked(node) && node instanceof ValueNode) {
            if (node instanceof Virtualizable) {
                if (processVirtualizable((ValueNode) node, nextFixedNode, state, effects) == false) {
                    return false;
                }
                if (tool.isDeleted()) {
                    VirtualUtil.trace(node.getOptions(), debug, "deleted virtualizable node %s", node);
                    return true;
                }
            }
            processNodeInputs((ValueNode) node, nextFixedNode, state, effects);
        }

        if (hasScalarReplacedInputs(node) && node instanceof ValueNode) {
            if (processNodeWithScalarReplacedInputs((ValueNode) node, nextFixedNode, state, effects)) {
                return true;
            }
        }
        return false;
    }

    protected boolean requiresProcessing(Node node) {
        return node instanceof VirtualizableAllocation;
    }

    private boolean processVirtualizable(ValueNode node, FixedNode insertBefore, BlockT state, GraphEffectList effects) {
        tool.reset(state, node, insertBefore, effects);
        return virtualize(node, tool);
    }

    protected boolean virtualize(ValueNode node, VirtualizerTool vt) {
        ((Virtualizable) node).virtualize(vt);
        return true; // request further processing
    }

    /**
     * This tries to canonicalize the node based on improved (replaced) inputs.
     */
    @SuppressWarnings("unchecked")
    private boolean processNodeWithScalarReplacedInputs(ValueNode node, FixedNode insertBefore, BlockT state, GraphEffectList effects) {
        ValueNode canonicalizedValue = node;
        if (node instanceof Canonicalizable.Unary<?>) {
            Canonicalizable.Unary<ValueNode> canonicalizable = (Canonicalizable.Unary<ValueNode>) node;
            ObjectState valueObj = getObjectState(state, canonicalizable.getValue());
            ValueNode valueAlias = valueObj != null ? valueObj.getMaterializedValue() : getScalarAlias(canonicalizable.getValue());
            if (valueAlias != canonicalizable.getValue()) {
                canonicalizedValue = (ValueNode) canonicalizable.canonical(tool, valueAlias);
            }
        } else if (node instanceof Canonicalizable.Binary<?>) {
            Canonicalizable.Binary<ValueNode> canonicalizable = (Canonicalizable.Binary<ValueNode>) node;
            ObjectState xObj = getObjectState(state, canonicalizable.getX());
            ValueNode xAlias = xObj != null ? xObj.getMaterializedValue() : getScalarAlias(canonicalizable.getX());
            ObjectState yObj = getObjectState(state, canonicalizable.getY());
            ValueNode yAlias = yObj != null ? yObj.getMaterializedValue() : getScalarAlias(canonicalizable.getY());
            if (xAlias != canonicalizable.getX() || yAlias != canonicalizable.getY()) {
                canonicalizedValue = (ValueNode) canonicalizable.canonical(tool, xAlias, yAlias);
            }
        } else {
            return false;
        }
        if (canonicalizedValue != node && canonicalizedValue != null) {
            if (canonicalizedValue.isAlive()) {
                ValueNode alias = getAliasAndResolve(state, canonicalizedValue);
                if (alias instanceof VirtualObjectNode) {
                    addVirtualAlias((VirtualObjectNode) alias, node);
                    effects.deleteNode(node);
                } else {
                    effects.replaceAtUsages(node, alias, insertBefore);
                    addScalarAlias(node, alias);
                }
            } else {
                if (!prepareCanonicalNode(canonicalizedValue, state, effects)) {
                    VirtualUtil.trace(node.getOptions(), debug, "replacement via canonicalization too complex: %s -> %s", node, canonicalizedValue);
                    return false;
                }
                if (canonicalizedValue instanceof ControlSinkNode) {
                    effects.replaceWithSink((FixedWithNextNode) node, (ControlSinkNode) canonicalizedValue);
                    state.markAsDead();
                } else {
                    effects.replaceAtUsages(node, canonicalizedValue, insertBefore);
                    addScalarAlias(node, canonicalizedValue);
                }
            }
            VirtualUtil.trace(node.getOptions(), debug, "replaced via canonicalization: %s -> %s", node, canonicalizedValue);
            return true;
        }
        return false;
    }

    /**
     * Nodes created during canonicalizations need to be scanned for values that were replaced.
     */
    private boolean prepareCanonicalNode(ValueNode node, BlockT state, GraphEffectList effects) {
        assert !node.isAlive();
        for (Position pos : node.inputPositions()) {
            Node input = pos.get(node);
            if (input instanceof ValueNode) {
                if (input.isAlive()) {
                    if (!(input instanceof VirtualObjectNode)) {
                        ObjectState obj = getObjectState(state, (ValueNode) input);
                        if (obj != null) {
                            if (obj.isVirtual()) {
                                return false;
                            } else {
                                pos.initialize(node, obj.getMaterializedValue());
                            }
                        } else {
                            pos.initialize(node, getScalarAlias((ValueNode) input));
                        }
                    }
                } else {
                    if (!prepareCanonicalNode((ValueNode) input, state, effects)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * This replaces all inputs that point to virtual or materialized values with the actual value,
     * materializing if necessary. Also takes care of frame states, adding the necessary
     * {@link VirtualObjectState}.
     */
    private void processNodeInputs(ValueNode node, FixedNode insertBefore, BlockT state, GraphEffectList effects) {
        VirtualUtil.trace(node.getOptions(), debug, "processing nodewithstate: %s", node);
        for (Node input : node.inputs()) {
            if (input instanceof ValueNode) {
                ValueNode alias = getAlias((ValueNode) input);
                if (alias instanceof VirtualObjectNode) {
                    int id = ((VirtualObjectNode) alias).getObjectId();
                    ensureMaterialized(state, id, insertBefore, effects, COUNTER_MATERIALIZATIONS_UNHANDLED);
                    effects.replaceFirstInput(node, input, state.getObjectState(id).getMaterializedValue());
                    VirtualUtil.trace(node.getOptions(), debug, "replacing input %s at %s", input, node);
                }
            }
        }
        if (node instanceof NodeWithState) {
            processNodeWithState((NodeWithState) node, state, effects);
        }
    }

    private void processNodeWithState(NodeWithState nodeWithState, BlockT state, GraphEffectList effects) {
        for (FrameState fs : nodeWithState.states()) {
            FrameState frameState = getUniqueFramestate(nodeWithState, fs);
            EconomicSet<VirtualObjectNode> virtual = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            frameState.applyToNonVirtual(new CollectVirtualObjectsClosure2(virtual, effects, state));
            collectLockedVirtualObjects(state, virtual);
            collectReferencedVirtualObjects(state, virtual);
            addVirtualMappings(frameState, virtual, state, effects);
        }
    }

    private static FrameState getUniqueFramestate(NodeWithState nodeWithState, FrameState frameState) {
        if (frameState.hasMoreThanOneUsage()) {
            // Can happen for example from inlined snippets with multiple state split nodes.
            FrameState copy = (FrameState) frameState.copyWithInputs();
            nodeWithState.asNode().replaceFirstInput(frameState, copy);
            return copy;
        }
        return frameState;
    }

    private void addVirtualMappings(FrameState frameState, EconomicSet<VirtualObjectNode> virtual, BlockT state, GraphEffectList effects) {
        for (VirtualObjectNode obj : virtual) {
            effects.addVirtualMapping(frameState, state.getObjectState(obj).createEscapeObjectState(debug, tool.getMetaAccessExtensionProvider(), obj));
        }
    }

    private void collectReferencedVirtualObjects(BlockT state, EconomicSet<VirtualObjectNode> virtual) {
        Iterator<VirtualObjectNode> iterator = virtual.iterator();
        while (iterator.hasNext()) {
            VirtualObjectNode object = iterator.next();
            int id = object.getObjectId();
            if (id != -1) {
                ObjectState objState = state.getObjectStateOptional(id);
                if (objState != null && objState.isVirtual()) {
                    for (ValueNode entry : objState.getEntries()) {
                        if (entry instanceof VirtualObjectNode) {
                            VirtualObjectNode entryVirtual = (VirtualObjectNode) entry;
                            if (!virtual.contains(entryVirtual)) {
                                virtual.add(entryVirtual);
                            }
                        }
                    }
                }
            }
        }
    }

    private void collectLockedVirtualObjects(BlockT state, EconomicSet<VirtualObjectNode> virtual) {
        for (int i = 0; i < state.getStateCount(); i++) {
            ObjectState objState = state.getObjectStateOptional(i);
            if (objState != null && objState.isVirtual() && objState.hasLocks()) {
                virtual.add(virtualObjects.get(i));
            }
        }
    }

    /**
     * @return true if materialization happened, false if not.
     */
    protected boolean ensureMaterialized(PartialEscapeBlockState<?> state, int object, FixedNode materializeBefore, GraphEffectList effects, CounterKey counter) {
        if (state.getObjectState(object).isVirtual()) {
            counter.increment(debug);
            VirtualObjectNode virtual = virtualObjects.get(object);
            state.materializeBefore(materializeBefore, virtual, effects);
            assert !updateStatesForMaterialized(state, virtual, state.getObjectState(object).getMaterializedValue()) : "method must already have been called before";
            return true;
        } else {
            return false;
        }
    }

    public static boolean updateStatesForMaterialized(PartialEscapeBlockState<?> state, VirtualObjectNode virtual, ValueNode materializedValue) {
        // update all existing states with the newly materialized object
        boolean change = false;
        for (int i = 0; i < state.getStateCount(); i++) {
            ObjectState objState = state.getObjectStateOptional(i);
            if (objState != null && objState.isVirtual()) {
                ValueNode[] entries = objState.getEntries();
                for (int i2 = 0; i2 < entries.length; i2++) {
                    if (entries[i2] == virtual) {
                        state.setEntry(i, i2, materializedValue);
                        change = true;
                    }
                }
            }
        }
        return change;
    }

    @Override
    protected BlockT stripKilledLoopLocations(Loop<Block> loop, BlockT originalInitialState) {
        BlockT initialState = super.stripKilledLoopLocations(loop, originalInitialState);
        if (loop.getDepth() > GraalOptions.EscapeAnalysisLoopCutoff.getValue(cfg.graph.getOptions())) {
            /*
             * After we've reached the maximum loop nesting, we'll simply materialize everything we
             * can to make sure that the loops only need to be iterated one time. Care is taken here
             * to not materialize virtual objects that have the "ensureVirtualized" flag set.
             */
            LoopBeginNode loopBegin = (LoopBeginNode) loop.getHeader().getBeginNode();
            AbstractEndNode end = loopBegin.forwardEnd();
            Block loopPredecessor = loop.getHeader().getFirstPredecessor();
            assert loopPredecessor.getEndNode() == end;
            int length = initialState.getStateCount();

            boolean change;
            BitSet ensureVirtualized = new BitSet(length);
            for (int i = 0; i < length; i++) {
                ObjectState state = initialState.getObjectStateOptional(i);
                if (state != null && state.isVirtual() && state.getEnsureVirtualized()) {
                    ensureVirtualized.set(i);
                }
            }
            do {
                // propagate "ensureVirtualized" flag
                change = false;
                for (int i = 0; i < length; i++) {
                    if (!ensureVirtualized.get(i)) {
                        ObjectState state = initialState.getObjectStateOptional(i);
                        if (state != null && state.isVirtual()) {
                            for (ValueNode entry : state.getEntries()) {
                                if (entry instanceof VirtualObjectNode) {
                                    if (ensureVirtualized.get(((VirtualObjectNode) entry).getObjectId())) {
                                        change = true;
                                        ensureVirtualized.set(i);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            } while (change);

            for (int i = 0; i < length; i++) {
                ObjectState state = initialState.getObjectStateOptional(i);
                if (state != null && state.isVirtual() && !ensureVirtualized.get(i)) {
                    initialState.materializeBefore(end, virtualObjects.get(i), blockEffects.get(loopPredecessor));
                }
            }
        }
        return initialState;
    }

    @Override
    protected void processInitialLoopState(Loop<Block> loop, BlockT initialState) {
        for (PhiNode phi : ((LoopBeginNode) loop.getHeader().getBeginNode()).phis()) {
            if (phi.valueAt(0) != null) {
                ValueNode alias = getAliasAndResolve(initialState, phi.valueAt(0));
                if (alias instanceof VirtualObjectNode) {
                    VirtualObjectNode virtual = (VirtualObjectNode) alias;
                    addVirtualAlias(virtual, phi);
                } else {
                    aliases.set(phi, null);
                }
            }
        }
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, BlockT initialState, BlockT exitState, GraphEffectList effects) {
        if (exitNode.graph().hasValueProxies()) {
            EconomicMap<Integer, ProxyNode> proxies = EconomicMap.create(Equivalence.DEFAULT);
            for (ProxyNode proxy : exitNode.proxies()) {
                ValueNode alias = getAlias(proxy.value());
                if (alias instanceof VirtualObjectNode) {
                    VirtualObjectNode virtual = (VirtualObjectNode) alias;
                    proxies.put(virtual.getObjectId(), proxy);
                }
            }
            for (int i = 0; i < exitState.getStateCount(); i++) {
                ObjectState exitObjState = exitState.getObjectStateOptional(i);
                if (exitObjState != null) {
                    ObjectState initialObjState = initialState.getObjectStateOptional(i);

                    if (exitObjState.isVirtual()) {
                        processVirtualAtLoopExit(exitNode, effects, i, exitObjState, initialObjState, exitState);
                    } else {
                        processMaterializedAtLoopExit(exitNode, effects, proxies, i, exitObjState, initialObjState, exitState);
                    }
                }
            }
        }
    }

    private static void processMaterializedAtLoopExit(LoopExitNode exitNode, GraphEffectList effects, EconomicMap<Integer, ProxyNode> proxies, int object, ObjectState exitObjState,
                    ObjectState initialObjState, PartialEscapeBlockState<?> exitState) {
        if (initialObjState == null || initialObjState.isVirtual()) {
            ProxyNode proxy = proxies.get(object);
            if (proxy == null) {
                proxy = new ValueProxyNode(exitObjState.getMaterializedValue(), exitNode);
                effects.addFloatingNode(proxy, "proxy");
            } else {
                effects.replaceFirstInput(proxy, proxy.value(), exitObjState.getMaterializedValue());
                // nothing to do - will be handled in processNode
            }
            exitState.updateMaterializedValue(object, proxy);
        } else {
            if (initialObjState.getMaterializedValue() != exitObjState.getMaterializedValue()) {
                exitNode.getDebug().log("materialized value changes within loop: %s vs. %s at %s", initialObjState.getMaterializedValue(), exitObjState.getMaterializedValue(), exitNode);
            }
        }
    }

    private static void processVirtualAtLoopExit(LoopExitNode exitNode, GraphEffectList effects, int object, ObjectState exitObjState, ObjectState initialObjState,
                    PartialEscapeBlockState<?> exitState) {
        for (int i = 0; i < exitObjState.getEntries().length; i++) {
            ValueNode value = exitState.getObjectState(object).getEntry(i);
            if (!(value instanceof VirtualObjectNode || value.isConstant())) {
                if (exitNode.loopBegin().isPhiAtMerge(value) || initialObjState == null || !initialObjState.isVirtual() || initialObjState.getEntry(i) != value) {
                    ProxyNode proxy = new ValueProxyNode(value, exitNode);
                    exitState.setEntry(object, i, proxy);
                    effects.addFloatingNode(proxy, "virtualProxy");
                }
            }
        }
    }

    @Override
    protected MergeProcessor createMergeProcessor(Block merge) {
        return new MergeProcessor(merge);
    }

    protected class MergeProcessor extends EffectsClosure<BlockT>.MergeProcessor {

        private EconomicMap<Object, ValuePhiNode> materializedPhis;
        private EconomicMap<ValueNode, ValuePhiNode[]> valuePhis;
        private EconomicMap<ValuePhiNode, VirtualObjectNode> valueObjectVirtuals;
        private final boolean needsCaching;

        public MergeProcessor(Block mergeBlock) {
            super(mergeBlock);
            // merge will only be called multiple times for loop headers
            needsCaching = mergeBlock.isLoopHeader();
        }

        protected <T> PhiNode getPhi(T virtual, Stamp stamp) {
            if (needsCaching) {
                return getPhiCached(virtual, stamp);
            } else {
                return createValuePhi(stamp);
            }
        }

        private <T> PhiNode getPhiCached(T virtual, Stamp stamp) {
            if (materializedPhis == null) {
                materializedPhis = EconomicMap.create(Equivalence.DEFAULT);
            }
            ValuePhiNode result = materializedPhis.get(virtual);
            if (result == null) {
                result = createValuePhi(stamp);
                materializedPhis.put(virtual, result);
            }
            return result;
        }

        private PhiNode[] getValuePhis(ValueNode key, int entryCount) {
            if (needsCaching) {
                return getValuePhisCached(key, entryCount);
            } else {
                return new ValuePhiNode[entryCount];
            }
        }

        private PhiNode[] getValuePhisCached(ValueNode key, int entryCount) {
            if (valuePhis == null) {
                valuePhis = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            }
            ValuePhiNode[] result = valuePhis.get(key);
            if (result == null) {
                result = new ValuePhiNode[entryCount];
                valuePhis.put(key, result);
            }
            assert result.length == entryCount;
            return result;
        }

        private VirtualObjectNode getValueObjectVirtual(ValuePhiNode phi, VirtualObjectNode virtual) {
            if (needsCaching) {
                return getValueObjectVirtualCached(phi, virtual);
            } else {
                VirtualObjectNode duplicate = virtual.duplicate();
                duplicate.setNodeSourcePosition(virtual.getNodeSourcePosition());
                return duplicate;
            }
        }

        private VirtualObjectNode getValueObjectVirtualCached(ValuePhiNode phi, VirtualObjectNode virtual) {
            if (valueObjectVirtuals == null) {
                valueObjectVirtuals = EconomicMap.create(Equivalence.IDENTITY);
            }
            VirtualObjectNode result = valueObjectVirtuals.get(phi);
            if (result == null) {
                result = virtual.duplicate();
                result.setNodeSourcePosition(virtual.getNodeSourcePosition());
                valueObjectVirtuals.put(phi, result);
            }
            return result;
        }

        /**
         * Merge all predecessor block states into one block state. This is an iterative process,
         * because merging states can lead to materializations which make previous parts of the
         * merging operation invalid. The merging process is executed until a stable state has been
         * reached. This method needs to be careful to place the effects of the merging operation
         * into the correct blocks.
         *
         * @param statesList the predecessor block states of the merge
         */
        @Override
        protected void merge(List<BlockT> statesList) {

            PartialEscapeBlockState<?>[] states = new PartialEscapeBlockState<?>[statesList.size()];
            for (int i = 0; i < statesList.size(); i++) {
                states[i] = statesList.get(i);
            }

            // calculate the set of virtual objects that exist in all predecessors
            int[] virtualObjTemp = intersectVirtualObjects(states);

            boolean forceMaterialization = false;
            ValueNode forcedMaterializationValue = null;
            FrameState frameState = merge.stateAfter();
            if (frameState != null && frameState.isExceptionHandlingBCI()) {
                // We can not go below merges with an exception handling bci
                // it could create allocations whose slow-path has an invalid framestate
                forceMaterialization = true;
                // check if we can reduce the scope of forced materialization to one phi node
                if (frameState.stackSize() == 1 && merge.next() instanceof UnwindNode) {
                    assert frameState.outerFrameState() == null;
                    UnwindNode unwind = (UnwindNode) merge.next();
                    if (unwind.exception() == frameState.stackAt(0)) {
                        boolean nullLocals = true;
                        for (int i = 0; i < frameState.localsSize(); i++) {
                            if (frameState.localAt(i) != null) {
                                nullLocals = false;
                                break;
                            }
                        }
                        if (nullLocals) {
                            // We found that the merge is directly followed by an unwind
                            // the Framestate only has the thrown value on the stack and no locals
                            forcedMaterializationValue = unwind.exception();
                        }
                    }
                }
            }

            boolean materialized;
            do {
                materialized = false;

                if (!forceMaterialization && PartialEscapeBlockState.identicalObjectStates(states)) {
                    newState.adoptAddObjectStates(states[0]);
                } else {

                    for (int object : virtualObjTemp) {
                        if (!forceMaterialization && PartialEscapeBlockState.identicalObjectStates(states, object)) {
                            newState.addObject(object, states[0].getObjectState(object).share());
                            continue;
                        }

                        // determine if all inputs are virtual or the same materialized value
                        int virtualCount = 0;
                        ObjectState startObj = states[0].getObjectState(object);
                        boolean locksMatch = true;
                        boolean ensureVirtual = true;
                        ValueNode uniqueMaterializedValue = startObj.isVirtual() ? null : startObj.getMaterializedValue();
                        for (int i = 0; i < states.length; i++) {
                            ObjectState obj = states[i].getObjectState(object);
                            ensureVirtual &= obj.getEnsureVirtualized();
                            if (forceMaterialization) {
                                if (forcedMaterializationValue == null) {
                                    uniqueMaterializedValue = null;
                                    continue;
                                } else {
                                    ValueNode value = forcedMaterializationValue;
                                    if (merge.isPhiAtMerge(value)) {
                                        value = ((ValuePhiNode) value).valueAt(i);
                                    }
                                    ValueNode alias = getAlias(value);
                                    if (alias instanceof VirtualObjectNode && ((VirtualObjectNode) alias).getObjectId() == object) {
                                        uniqueMaterializedValue = null;
                                        continue;
                                    }
                                }
                            }
                            if (obj.isVirtual()) {
                                virtualCount++;
                                uniqueMaterializedValue = null;
                                locksMatch &= obj.locksEqual(startObj);
                            } else if (obj.getMaterializedValue() != uniqueMaterializedValue) {
                                uniqueMaterializedValue = null;
                            }
                        }

                        if (virtualCount == states.length && locksMatch) {
                            materialized |= mergeObjectStates(object, null, states);
                        } else {
                            if (uniqueMaterializedValue != null) {
                                newState.addObject(object, new ObjectState(uniqueMaterializedValue, null, ensureVirtual));
                            } else {
                                PhiNode materializedValuePhi = getPhi(object, StampFactory.forKind(JavaKind.Object));
                                mergeEffects.addFloatingNode(materializedValuePhi, "materializedPhi");
                                for (int i = 0; i < states.length; i++) {
                                    ObjectState obj = states[i].getObjectState(object);
                                    if (obj.isVirtual()) {
                                        Block predecessor = getPredecessor(i);
                                        if (!ensureVirtual && obj.isVirtual()) {
                                            // we can materialize if not all inputs are
                                            // "ensureVirtualized"
                                            obj.setEnsureVirtualized(false);
                                        }
                                        materialized |= ensureMaterialized(states[i], object, predecessor.getEndNode(), blockEffects.get(predecessor), COUNTER_MATERIALIZATIONS_MERGE);
                                        obj = states[i].getObjectState(object);
                                    }
                                    setPhiInput(materializedValuePhi, i, obj.getMaterializedValue());
                                }
                                newState.addObject(object, new ObjectState(materializedValuePhi, null, false));
                            }
                        }
                    }
                }

                for (PhiNode phi : getPhis()) {
                    aliases.set(phi, null);
                    if (hasVirtualInputs.isMarked(phi) && phi instanceof ValuePhiNode) {
                        materialized |= processPhi((ValuePhiNode) phi, states);
                    }
                }
                if (materialized) {
                    newState.resetObjectStates(virtualObjects.size());
                    mergeEffects.clear();
                    afterMergeEffects.clear();
                }
            } while (materialized);
        }

        private int[] intersectVirtualObjects(PartialEscapeBlockState<?>[] states) {
            int length = states[0].getStateCount();
            for (int i = 1; i < states.length; i++) {
                length = Math.min(length, states[i].getStateCount());
            }

            int count = 0;
            for (int objectIndex = 0; objectIndex < length; objectIndex++) {
                if (intersectObjectState(states, objectIndex)) {
                    count++;
                }
            }

            int index = 0;
            int[] resultInts = new int[count];
            for (int objectIndex = 0; objectIndex < length; objectIndex++) {
                if (intersectObjectState(states, objectIndex)) {
                    resultInts[index++] = objectIndex;
                }
            }
            assert index == count;
            return resultInts;
        }

        private boolean intersectObjectState(PartialEscapeBlockState<?>[] states, int objectIndex) {
            for (int i = 0; i < states.length; i++) {
                PartialEscapeBlockState<?> state = states[i];
                if (state.getObjectStateOptional(objectIndex) == null) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Try to merge multiple virtual object states into a single object state. If the incoming
         * object states are compatible, then this method will create PhiNodes for the object's
         * entries where needed. If they are incompatible, then all incoming virtual objects will be
         * materialized, and a PhiNode for the materialized values will be created. Object states
         * can be incompatible if they contain {@code long} or {@code double} values occupying two
         * {@code int} slots in such a way that that their values cannot be merged using PhiNodes.
         * The states may also be incompatible if they contain escaped large writes to byte arrays
         * in such a way that they cannot be merged using PhiNodes.
         *
         * @param states the predecessor block states of the merge
         * @return true if materialization happened during the merge, false otherwise
         */
        private boolean mergeObjectStates(int resultObject, int[] sourceObjects, PartialEscapeBlockState<?>[] states) {
            boolean compatible = true;
            boolean ensureVirtual = true;
            IntUnaryOperator getObject = index -> sourceObjects == null ? resultObject : sourceObjects[index];

            VirtualObjectNode virtual = virtualObjects.get(resultObject);
            int entryCount = virtual.entryCount();

            // determine all entries that have a two-slot value
            JavaKind[] twoSlotKinds = null;

            // Determine all entries that span multiple slots.
            int[] virtualByteCount = null;
            JavaKind[] virtualKinds = null;

            outer: for (int i = 0; i < states.length; i++) {
                ObjectState objectState = states[i].getObjectState(getObject.applyAsInt(i));
                ValueNode[] entries = objectState.getEntries();
                int valueIndex = 0;
                ensureVirtual &= objectState.getEnsureVirtualized();
                while (valueIndex < entryCount) {
                    JavaKind otherKind = entries[valueIndex].getStackKind();
                    JavaKind entryKind = virtual.entryKind(tool.getMetaAccessExtensionProvider(), valueIndex);
                    if (entryKind == JavaKind.Int && otherKind.needsTwoSlots()) {
                        if (twoSlotKinds == null) {
                            twoSlotKinds = new JavaKind[entryCount];
                        }
                        if (twoSlotKinds[valueIndex] != null && twoSlotKinds[valueIndex] != otherKind) {
                            compatible = false;
                            break outer;
                        }
                        twoSlotKinds[valueIndex] = otherKind;
                        // skip the next entry
                        valueIndex++;
                    } else if (virtual.isVirtualByteArray(tool.getMetaAccessExtensionProvider())) {
                        int bytecount = tool.getVirtualByteCount(entries, valueIndex);
                        // @formatter:off
                        /*
                         * Having a bytecount of 1 here can mean two things:
                         * - This was a regular byte array access
                         * - This is an uninitialized value (ie: default)
                         *
                         * In the first case, we want to be able to merge regular accesses without
                         * issues. But in the second case, if one of the branch has escaped a write
                         * (while other branches did not touch the array), we want to be able to
                         * propagate the escape to the merge.
                         *
                         * However, the semantics of virtual object creation in PEA puts a default
                         * (0) byte value on all entries. As such, the merging is done in two steps:
                         * - For each virtual entry, know if there is an escaped write in one of the
                         * branch, and store its byte count, unless it is 1.
                         * - Now that we know the byte count, we can escape multiple writes for the
                         * default values from branches that did nothing on the entry in question to
                         * a default write of a bigger kind.
                         *
                         * for example, consider:
                         *
                         * b = new byte[8];
                         * if (...) {b[0] <- 1L}
                         * else     {}
                         *
                         * for escape analysis purposes, it can be seen as:
                         *
                         * b = new byte[8];
                         * if (...) {b[0] <- 1L}
                         * else     {b[0] <- 0L}
                         */
                        // @formatter:on
                        if (bytecount > 1) {
                            if (virtualByteCount == null) {
                                virtualByteCount = new int[entryCount];
                            }
                            if (virtualKinds == null) {
                                virtualKinds = new JavaKind[entryCount];
                            }
                            if (virtualByteCount[valueIndex] != 0 && virtualByteCount[valueIndex] != bytecount) {
                                compatible = false;
                                break outer;
                            }
                            // Disallow merging ints with floats. Allows merging shorts with chars
                            // (working with stack kinds).
                            if (virtualKinds[valueIndex] != null && virtualKinds[valueIndex] != otherKind) {
                                compatible = false;
                                break outer;
                            }
                            virtualByteCount[valueIndex] = bytecount;
                            virtualKinds[valueIndex] = otherKind;
                            // skip illegals.
                            valueIndex = valueIndex + bytecount - 1;
                        }
                    } else {
                        assert entryKind.getStackKind() == otherKind.getStackKind() || (entryKind == JavaKind.Int && otherKind == JavaKind.Illegal) ||
                                        entryKind.getBitCount() >= otherKind.getBitCount() : entryKind + " vs " + otherKind;
                    }
                    valueIndex++;
                }
            }
            if (compatible && twoSlotKinds != null) {
                // if there are two-slot values then make sure the incoming states can be merged
                outer: for (int valueIndex = 0; valueIndex < entryCount; valueIndex++) {
                    if (twoSlotKinds[valueIndex] != null) {
                        assert valueIndex < virtual.entryCount() - 1 && virtual.entryKind(tool.getMetaAccessExtensionProvider(), valueIndex) == JavaKind.Int &&
                                        virtual.entryKind(tool.getMetaAccessExtensionProvider(), valueIndex + 1) == JavaKind.Int;
                        for (int i = 0; i < states.length; i++) {
                            int object = getObject.applyAsInt(i);
                            ObjectState objectState = states[i].getObjectState(object);
                            ValueNode value = objectState.getEntry(valueIndex);
                            JavaKind valueKind = value.getStackKind();
                            if (valueKind != twoSlotKinds[valueIndex]) {
                                ValueNode nextValue = objectState.getEntry(valueIndex + 1);
                                if (value.isConstant() && value.asConstant().equals(JavaConstant.INT_0) && nextValue.isConstant() && nextValue.asConstant().equals(JavaConstant.INT_0)) {
                                    // rewrite to a zero constant of the larger kind
                                    debug.log("Rewriting entry %s to constant of larger size", valueIndex);
                                    states[i].setEntry(object, valueIndex, ConstantNode.defaultForKind(twoSlotKinds[valueIndex], graph()));
                                    states[i].setEntry(object, valueIndex + 1, tool.getIllegalConstant());
                                } else {
                                    compatible = false;
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }
            if (compatible && virtualByteCount != null) {
                assert twoSlotKinds == null;
                outer: //
                for (int valueIndex = 0; valueIndex < entryCount; valueIndex++) {
                    if (virtualByteCount[valueIndex] != 0) {
                        int byteCount = virtualByteCount[valueIndex];
                        for (int i = 0; i < states.length; i++) {
                            int object = getObject.applyAsInt(i);
                            ObjectState objectState = states[i].getObjectState(object);
                            if (tool.isEntryDefaults(objectState, byteCount, valueIndex)) {
                                // Interpret uninitialized as a corresponding large access.
                                states[i].setEntry(object, valueIndex, ConstantNode.defaultForKind(virtualKinds[valueIndex]));
                                for (int illegalIndex = valueIndex + 1; illegalIndex < valueIndex + byteCount; illegalIndex++) {
                                    states[i].setEntry(object, illegalIndex, tool.getIllegalConstant());
                                }
                            } else {
                                if (tool.getVirtualByteCount(objectState.getEntries(), valueIndex) != byteCount) {
                                    compatible = false;
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }

            if (compatible) {
                // virtual objects are compatible: create phis for all entries that need them
                ValueNode[] values = states[0].getObjectState(getObject.applyAsInt(0)).getEntries().clone();
                PhiNode[] phis = getValuePhis(virtual, virtual.entryCount());
                int valueIndex = 0;
                while (valueIndex < values.length) {
                    for (int i = 1; i < states.length; i++) {
                        if (phis[valueIndex] == null) {
                            ValueNode field = states[i].getObjectState(getObject.applyAsInt(i)).getEntry(valueIndex);
                            if (values[valueIndex] != field) {
                                phis[valueIndex] = createValuePhi(values[valueIndex].stamp(NodeView.DEFAULT).unrestricted());
                            }
                        }
                    }
                    if (phis[valueIndex] != null && !phis[valueIndex].stamp(NodeView.DEFAULT).isCompatible(values[valueIndex].stamp(NodeView.DEFAULT))) {
                        phis[valueIndex] = createValuePhi(values[valueIndex].stamp(NodeView.DEFAULT).unrestricted());
                    }
                    if (twoSlotKinds != null && twoSlotKinds[valueIndex] != null) {
                        // skip an entry after a long/double value that occupies two int slots
                        valueIndex++;
                        phis[valueIndex] = null;
                        values[valueIndex] = tool.getIllegalConstant();
                    }
                    valueIndex++;
                }

                boolean materialized = false;
                for (int i = 0; i < values.length; i++) {
                    PhiNode phi = phis[i];
                    if (phi != null) {
                        mergeEffects.addFloatingNode(phi, "virtualMergePhi");
                        if (virtual.entryKind(tool.getMetaAccessExtensionProvider(), i) == JavaKind.Object) {
                            materialized |= mergeObjectEntry(getObject, states, phi, i);
                        } else {
                            for (int i2 = 0; i2 < states.length; i2++) {
                                ObjectState state = states[i2].getObjectState(getObject.applyAsInt(i2));
                                if (!state.isVirtual()) {
                                    break;
                                }
                                setPhiInput(phi, i2, state.getEntry(i));
                            }
                        }
                        values[i] = phi;
                    }
                }
                newState.addObject(resultObject, new ObjectState(values, states[0].getObjectState(getObject.applyAsInt(0)).getLocks(), ensureVirtual));
                return materialized;
            } else {
                // not compatible: materialize in all predecessors
                PhiNode materializedValuePhi = getPhi(resultObject, StampFactory.forKind(JavaKind.Object));
                for (int i = 0; i < states.length; i++) {
                    Block predecessor = getPredecessor(i);
                    if (!ensureVirtual && states[i].getObjectState(getObject.applyAsInt(i)).isVirtual()) {
                        // we can materialize if not all inputs are "ensureVirtualized"
                        states[i].getObjectState(getObject.applyAsInt(i)).setEnsureVirtualized(false);
                    }
                    ensureMaterialized(states[i], getObject.applyAsInt(i), predecessor.getEndNode(), blockEffects.get(predecessor), COUNTER_MATERIALIZATIONS_MERGE);
                    setPhiInput(materializedValuePhi, i, states[i].getObjectState(getObject.applyAsInt(i)).getMaterializedValue());
                }
                newState.addObject(resultObject, new ObjectState(materializedValuePhi, null, ensureVirtual));
                return true;
            }
        }

        /**
         * Fill the inputs of the PhiNode corresponding to one {@link JavaKind#Object} entry in the
         * virtual object.
         *
         * @return true if materialization happened during the merge, false otherwise
         */
        private boolean mergeObjectEntry(IntUnaryOperator objectIdFunc, PartialEscapeBlockState<?>[] states, PhiNode phi, int entryIndex) {
            boolean materialized = false;
            for (int i = 0; i < states.length; i++) {
                int object = objectIdFunc.applyAsInt(i);
                ObjectState objectState = states[i].getObjectState(object);
                if (!objectState.isVirtual()) {
                    break;
                }
                ValueNode entry = objectState.getEntry(entryIndex);
                if (entry instanceof VirtualObjectNode) {
                    VirtualObjectNode entryVirtual = (VirtualObjectNode) entry;
                    Block predecessor = getPredecessor(i);
                    materialized |= ensureMaterialized(states[i], entryVirtual.getObjectId(), predecessor.getEndNode(), blockEffects.get(predecessor), COUNTER_MATERIALIZATIONS_MERGE);
                    objectState = states[i].getObjectState(object);
                    if (objectState.isVirtual()) {
                        states[i].setEntry(object, entryIndex, entry = states[i].getObjectState(entryVirtual.getObjectId()).getMaterializedValue());
                    }
                }
                setPhiInput(phi, i, entry);
            }
            return materialized;
        }

        /**
         * Examine a PhiNode and try to replace it with merging of virtual objects if all its inputs
         * refer to virtual object states. In order for the merging to happen, all incoming object
         * states need to be compatible and without object identity (meaning that their object
         * identity if not used later on).
         *
         * @param phi the PhiNode that should be processed
         * @param states the predecessor block states of the merge
         * @return true if materialization happened during the merge, false otherwise
         */
        private boolean processPhi(ValuePhiNode phi, PartialEscapeBlockState<?>[] states) {

            // determine how many inputs are virtual and if they're all the same virtual object
            int virtualInputs = 0;
            boolean uniqueVirtualObject = true;
            boolean ensureVirtual = true;
            VirtualObjectNode[] virtualObjs = new VirtualObjectNode[states.length];
            for (int i = 0; i < states.length; i++) {
                ValueNode alias = getAlias(getPhiValueAt(phi, i));
                if (alias instanceof VirtualObjectNode) {
                    VirtualObjectNode virtual = (VirtualObjectNode) alias;
                    virtualObjs[i] = virtual;
                    ObjectState objectState = states[i].getObjectStateOptional(virtual);
                    if (objectState == null) {
                        assert getPhiValueAt(phi, i) instanceof PhiNode : "this should only happen for phi nodes";
                        return false;
                    }
                    if (objectState.isVirtual()) {
                        if (virtualObjs[0] != alias) {
                            uniqueVirtualObject = false;
                        }
                        ensureVirtual &= objectState.getEnsureVirtualized();
                        virtualInputs++;
                    }
                }
            }
            if (virtualInputs == states.length) {
                if (uniqueVirtualObject) {
                    // all inputs refer to the same object: just make the phi node an alias
                    addVirtualAlias(virtualObjs[0], phi);
                    mergeEffects.deleteNode(phi);
                    return false;
                } else {
                    // all inputs are virtual: check if they're compatible and without identity
                    boolean compatible = true;
                    VirtualObjectNode firstVirtual = virtualObjs[0];
                    for (int i = 0; i < states.length; i++) {
                        VirtualObjectNode virtual = virtualObjs[i];

                        if (!firstVirtual.type().equals(virtual.type()) || firstVirtual.entryCount() != virtual.entryCount()) {
                            compatible = false;
                            break;
                        }
                        if (!states[0].getObjectState(firstVirtual).locksEqual(states[i].getObjectState(virtual))) {
                            compatible = false;
                            break;
                        }
                    }
                    if (compatible) {
                        for (int i = 0; i < states.length; i++) {
                            VirtualObjectNode virtual = virtualObjs[i];
                            /*
                             * check whether we trivially see that this is the only reference to
                             * this allocation
                             */
                            if (virtual.hasIdentity() && !isSingleUsageAllocation(getPhiValueAt(phi, i), virtualObjs, states[i])) {
                                compatible = false;
                                break;
                            }
                        }
                    }
                    if (compatible) {
                        VirtualObjectNode virtual = getValueObjectVirtual(phi, virtualObjs[0]);
                        mergeEffects.addFloatingNode(virtual, "valueObjectNode");
                        mergeEffects.deleteNode(phi);
                        if (virtual.getObjectId() == -1) {
                            int id = virtualObjects.size();
                            virtualObjects.add(virtual);
                            virtual.setObjectId(id);
                        }

                        int[] virtualObjectIds = new int[states.length];
                        for (int i = 0; i < states.length; i++) {
                            virtualObjectIds[i] = virtualObjs[i].getObjectId();
                        }
                        boolean materialized = mergeObjectStates(virtual.getObjectId(), virtualObjectIds, states);
                        addVirtualAlias(virtual, virtual);
                        addVirtualAlias(virtual, phi);
                        return materialized;
                    }
                }
            }

            // otherwise: materialize all phi inputs
            boolean materialized = false;
            if (virtualInputs > 0) {
                for (int i = 0; i < states.length; i++) {
                    VirtualObjectNode virtual = virtualObjs[i];
                    if (virtual != null) {
                        Block predecessor = getPredecessor(i);
                        if (!ensureVirtual && states[i].getObjectState(virtual).isVirtual()) {
                            // we can materialize if not all inputs are "ensureVirtualized"
                            states[i].getObjectState(virtual).setEnsureVirtualized(false);
                        }
                        materialized |= ensureMaterialized(states[i], virtual.getObjectId(), predecessor.getEndNode(), blockEffects.get(predecessor), COUNTER_MATERIALIZATIONS_PHI);
                    }
                }
            }
            for (int i = 0; i < states.length; i++) {
                VirtualObjectNode virtual = virtualObjs[i];
                if (virtual != null) {
                    setPhiInput(phi, i, getAliasAndResolve(states[i], virtual));
                }
            }
            return materialized;
        }

        private boolean isSingleUsageAllocation(ValueNode value, VirtualObjectNode[] virtualObjs, PartialEscapeBlockState<?> state) {
            /*
             * If the phi input is an allocation, we know that it is a "fresh" value, i.e., that
             * this is a value that will only appear through this source, and cannot appear anywhere
             * else. If the phi is also the only usage of this input, we know that no other place
             * can check object identity against it, so it is safe to lose the object identity here.
             */
            if (!(value instanceof AllocatedObjectNode && value.hasExactlyOneUsage())) {
                return false;
            }

            /*
             * Check that the state only references the one virtual object from the Phi.
             */
            VirtualObjectNode singleVirtual = null;
            for (int v = 0; v < virtualObjs.length; v++) {
                if (state.contains(virtualObjs[v])) {
                    if (singleVirtual == null) {
                        singleVirtual = virtualObjs[v];
                    } else if (singleVirtual != virtualObjs[v]) {
                        /*
                         * More than one virtual object is visible in the object state.
                         */
                        return false;
                    }
                }
            }
            return true;
        }
    }

    public ObjectState getObjectState(PartialEscapeBlockState<?> state, ValueNode value) {
        if (value == null) {
            return null;
        }
        if (value.isAlive() && !aliases.isNew(value)) {
            ValueNode object = aliases.get(value);
            return object instanceof VirtualObjectNode ? state.getObjectStateOptional((VirtualObjectNode) object) : null;
        } else {
            if (value instanceof VirtualObjectNode) {
                return state.getObjectStateOptional((VirtualObjectNode) value);
            }
            return null;
        }
    }

    public ValueNode getAlias(ValueNode value) {
        if (value != null && !(value instanceof VirtualObjectNode)) {
            if (value.isAlive() && !aliases.isNew(value)) {
                ValueNode result = aliases.get(value);
                if (result != null) {
                    return result;
                }
            }
        }
        return value;
    }

    public ValueNode getAliasAndResolve(PartialEscapeBlockState<?> state, ValueNode value) {
        ValueNode result = getAlias(value);
        if (result instanceof VirtualObjectNode) {
            int id = ((VirtualObjectNode) result).getObjectId();
            if (id != -1 && !state.getObjectState(id).isVirtual()) {
                result = state.getObjectState(id).getMaterializedValue();
            }
        }
        return result;
    }

    void addVirtualAlias(VirtualObjectNode virtual, ValueNode node) {
        if (node.isAlive()) {
            aliases.set(node, virtual);
            for (Node usage : node.usages()) {
                markVirtualUsages(usage);
            }
        }
    }

    private void markVirtualUsages(Node node) {
        if (!hasVirtualInputs.isNew(node) && !hasVirtualInputs.isMarked(node)) {
            hasVirtualInputs.mark(node);
            if (node instanceof VirtualState) {
                for (Node usage : node.usages()) {
                    markVirtualUsages(usage);
                }
            }
        }
    }
}
