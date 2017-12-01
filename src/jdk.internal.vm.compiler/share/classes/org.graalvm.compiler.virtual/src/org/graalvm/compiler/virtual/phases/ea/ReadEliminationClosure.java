/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.ReadEliminationMaxLoopVisits;
import static org.graalvm.word.LocationIdentity.any;

import java.util.Iterator;
import java.util.List;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationBlockState.CacheEntry;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationBlockState.LoadCacheEntry;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationBlockState.UnsafeLoadCacheEntry;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.EconomicSet;
import org.graalvm.util.Equivalence;
import org.graalvm.util.MapCursor;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * This closure initially handled a set of nodes that is disjunct from
 * {@link PEReadEliminationClosure}, but over time both have evolved so that there's a significant
 * overlap.
 */
public final class ReadEliminationClosure extends EffectsClosure<ReadEliminationBlockState> {
    private final boolean considerGuards;

    public ReadEliminationClosure(ControlFlowGraph cfg, boolean considerGuards) {
        super(null, cfg);
        this.considerGuards = considerGuards;
    }

    @Override
    protected ReadEliminationBlockState getInitialState() {
        return new ReadEliminationBlockState();
    }

    @Override
    protected boolean processNode(Node node, ReadEliminationBlockState state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        boolean deleted = false;
        if (node instanceof AccessFieldNode) {
            AccessFieldNode access = (AccessFieldNode) node;
            if (access.isVolatile()) {
                processIdentity(state, any());
            } else {
                ValueNode object = GraphUtil.unproxify(access.object());
                LoadCacheEntry identifier = new LoadCacheEntry(object, new FieldLocationIdentity(access.field()));
                ValueNode cachedValue = state.getCacheEntry(identifier);
                if (node instanceof LoadFieldNode) {
                    if (cachedValue != null && access.stamp(NodeView.DEFAULT).isCompatible(cachedValue.stamp(NodeView.DEFAULT))) {
                        effects.replaceAtUsages(access, cachedValue, access);
                        addScalarAlias(access, cachedValue);
                        deleted = true;
                    } else {
                        state.addCacheEntry(identifier, access);
                    }
                } else {
                    assert node instanceof StoreFieldNode;
                    StoreFieldNode store = (StoreFieldNode) node;
                    ValueNode value = getScalarAlias(store.value());
                    if (GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                        effects.deleteNode(store);
                        deleted = true;
                    }
                    state.killReadCache(identifier.identity);
                    state.addCacheEntry(identifier, value);
                }
            }
        } else if (node instanceof ReadNode) {
            ReadNode read = (ReadNode) node;
            if (read.getLocationIdentity().isSingle()) {
                ValueNode object = GraphUtil.unproxify(read.getAddress());
                LoadCacheEntry identifier = new LoadCacheEntry(object, read.getLocationIdentity());
                ValueNode cachedValue = state.getCacheEntry(identifier);
                if (cachedValue != null && areValuesReplaceable(read, cachedValue, considerGuards)) {
                    effects.replaceAtUsages(read, cachedValue, read);
                    addScalarAlias(read, cachedValue);
                    deleted = true;
                } else {
                    state.addCacheEntry(identifier, read);
                }
            }
        } else if (node instanceof WriteNode) {
            WriteNode write = (WriteNode) node;
            if (write.getLocationIdentity().isSingle()) {
                ValueNode object = GraphUtil.unproxify(write.getAddress());
                LoadCacheEntry identifier = new LoadCacheEntry(object, write.getLocationIdentity());
                ValueNode cachedValue = state.getCacheEntry(identifier);

                ValueNode value = getScalarAlias(write.value());
                if (GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                    effects.deleteNode(write);
                    deleted = true;
                }
                processIdentity(state, write.getLocationIdentity());
                state.addCacheEntry(identifier, value);
            } else {
                processIdentity(state, write.getLocationIdentity());
            }
        } else if (node instanceof UnsafeAccessNode) {
            ResolvedJavaType type = StampTool.typeOrNull(((UnsafeAccessNode) node).object());
            if (type != null && !type.isArray()) {
                if (node instanceof RawLoadNode) {
                    RawLoadNode load = (RawLoadNode) node;
                    if (load.getLocationIdentity().isSingle()) {
                        ValueNode object = GraphUtil.unproxify(load.object());
                        UnsafeLoadCacheEntry identifier = new UnsafeLoadCacheEntry(object, load.offset(), load.getLocationIdentity());
                        ValueNode cachedValue = state.getCacheEntry(identifier);
                        if (cachedValue != null && areValuesReplaceable(load, cachedValue, considerGuards)) {
                            effects.replaceAtUsages(load, cachedValue, load);
                            addScalarAlias(load, cachedValue);
                            deleted = true;
                        } else {
                            state.addCacheEntry(identifier, load);
                        }
                    }
                } else {
                    assert node instanceof RawStoreNode;
                    RawStoreNode write = (RawStoreNode) node;
                    if (write.getLocationIdentity().isSingle()) {
                        ValueNode object = GraphUtil.unproxify(write.object());
                        UnsafeLoadCacheEntry identifier = new UnsafeLoadCacheEntry(object, write.offset(), write.getLocationIdentity());
                        ValueNode cachedValue = state.getCacheEntry(identifier);

                        ValueNode value = getScalarAlias(write.value());
                        if (GraphUtil.unproxify(value) == GraphUtil.unproxify(cachedValue)) {
                            effects.deleteNode(write);
                            deleted = true;
                        }
                        processIdentity(state, write.getLocationIdentity());
                        state.addCacheEntry(identifier, value);
                    } else {
                        processIdentity(state, write.getLocationIdentity());
                    }
                }
            }
        } else if (node instanceof MemoryCheckpoint.Single) {
            LocationIdentity identity = ((MemoryCheckpoint.Single) node).getLocationIdentity();
            processIdentity(state, identity);
        } else if (node instanceof MemoryCheckpoint.Multi) {
            for (LocationIdentity identity : ((MemoryCheckpoint.Multi) node).getLocationIdentities()) {
                processIdentity(state, identity);
            }
        }
        return deleted;
    }

    private static boolean areValuesReplaceable(ValueNode originalValue, ValueNode replacementValue, boolean considerGuards) {
        return originalValue.stamp(NodeView.DEFAULT).isCompatible(replacementValue.stamp(NodeView.DEFAULT)) &&
                        (!considerGuards || (getGuard(originalValue) == null || getGuard(originalValue) == getGuard(replacementValue)));
    }

    private static GuardingNode getGuard(ValueNode node) {
        if (node instanceof GuardedNode) {
            GuardedNode guardedNode = (GuardedNode) node;
            return guardedNode.getGuard();
        }
        return null;
    }

    private static void processIdentity(ReadEliminationBlockState state, LocationIdentity identity) {
        if (identity.isAny()) {
            state.killReadCache();
            return;
        }
        state.killReadCache(identity);
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, ReadEliminationBlockState initialState, ReadEliminationBlockState exitState, GraphEffectList effects) {
        if (exitNode.graph().hasValueProxies()) {
            MapCursor<CacheEntry<?>, ValueNode> entry = exitState.getReadCache().getEntries();
            while (entry.advance()) {
                if (initialState.getReadCache().get(entry.getKey()) != entry.getValue()) {
                    ProxyNode proxy = new ValueProxyNode(exitState.getCacheEntry(entry.getKey()), exitNode);
                    effects.addFloatingNode(proxy, "readCacheProxy");
                    exitState.getReadCache().put(entry.getKey(), proxy);
                }
            }
        }
    }

    @Override
    protected ReadEliminationBlockState cloneState(ReadEliminationBlockState other) {
        return new ReadEliminationBlockState(other);
    }

    @Override
    protected MergeProcessor createMergeProcessor(Block merge) {
        return new ReadEliminationMergeProcessor(merge);
    }

    private class ReadEliminationMergeProcessor extends EffectsClosure<ReadEliminationBlockState>.MergeProcessor {

        private final EconomicMap<Object, ValuePhiNode> materializedPhis = EconomicMap.create(Equivalence.DEFAULT);

        ReadEliminationMergeProcessor(Block mergeBlock) {
            super(mergeBlock);
        }

        protected ValuePhiNode getCachedPhi(CacheEntry<?> virtual, Stamp stamp) {
            ValuePhiNode result = materializedPhis.get(virtual);
            if (result == null) {
                result = createValuePhi(stamp);
                materializedPhis.put(virtual, result);
            }
            return result;
        }

        @Override
        protected void merge(List<ReadEliminationBlockState> states) {
            MapCursor<CacheEntry<?>, ValueNode> cursor = states.get(0).readCache.getEntries();
            while (cursor.advance()) {
                CacheEntry<?> key = cursor.getKey();
                ValueNode value = cursor.getValue();
                boolean phi = false;
                for (int i = 1; i < states.size(); i++) {
                    ValueNode otherValue = states.get(i).readCache.get(key);
                    // E.g. unsafe loads / stores with different access kinds have different stamps
                    // although location, object and offset are the same. In this case we cannot
                    // create a phi nor can we set a common value.
                    if (otherValue == null || !value.stamp(NodeView.DEFAULT).isCompatible(otherValue.stamp(NodeView.DEFAULT))) {
                        value = null;
                        phi = false;
                        break;
                    }
                    if (!phi && otherValue != value) {
                        phi = true;
                    }
                }
                if (phi) {
                    PhiNode phiNode = getCachedPhi(key, value.stamp(NodeView.DEFAULT).unrestricted());
                    mergeEffects.addFloatingNode(phiNode, "mergeReadCache");
                    for (int i = 0; i < states.size(); i++) {
                        ValueNode v = states.get(i).getCacheEntry(key);
                        assert phiNode.stamp(NodeView.DEFAULT).isCompatible(v.stamp(NodeView.DEFAULT)) : "Cannot create read elimination phi for inputs with incompatible stamps.";
                        setPhiInput(phiNode, i, v);
                    }
                    newState.addCacheEntry(key, phiNode);
                } else if (value != null) {
                    // case that there is the same value on all branches
                    newState.addCacheEntry(key, value);
                }
            }
            /*
             * For object phis, see if there are known reads on all predecessors, for which we could
             * create new phis.
             */
            for (PhiNode phi : getPhis()) {
                if (phi.getStackKind() == JavaKind.Object) {
                    for (CacheEntry<?> entry : states.get(0).readCache.getKeys()) {
                        if (entry.object == getPhiValueAt(phi, 0)) {
                            mergeReadCachePhi(phi, entry, states);
                        }
                    }
                }
            }
        }

        private void mergeReadCachePhi(PhiNode phi, CacheEntry<?> identifier, List<ReadEliminationBlockState> states) {
            ValueNode[] values = new ValueNode[states.size()];
            values[0] = states.get(0).getCacheEntry(identifier.duplicateWithObject(getPhiValueAt(phi, 0)));
            if (values[0] != null) {
                for (int i = 1; i < states.size(); i++) {
                    ValueNode value = states.get(i).getCacheEntry(identifier.duplicateWithObject(getPhiValueAt(phi, i)));
                    // e.g. unsafe loads / stores with same identity and different access kinds see
                    // mergeReadCache(states)
                    if (value == null || !values[i - 1].stamp(NodeView.DEFAULT).isCompatible(value.stamp(NodeView.DEFAULT))) {
                        return;
                    }
                    values[i] = value;
                }

                CacheEntry<?> newIdentifier = identifier.duplicateWithObject(phi);
                PhiNode phiNode = getCachedPhi(newIdentifier, values[0].stamp(NodeView.DEFAULT).unrestricted());
                mergeEffects.addFloatingNode(phiNode, "mergeReadCachePhi");
                for (int i = 0; i < values.length; i++) {
                    setPhiInput(phiNode, i, values[i]);
                }
                newState.addCacheEntry(newIdentifier, phiNode);
            }
        }
    }

    @Override
    protected void processKilledLoopLocations(Loop<Block> loop, ReadEliminationBlockState initialState, ReadEliminationBlockState mergedStates) {
        assert initialState != null;
        assert mergedStates != null;
        if (initialState.readCache.size() > 0) {
            LoopKillCache loopKilledLocations = loopLocationKillCache.get(loop);
            // we have fully processed this loop the first time, remember to cache it the next time
            // it is visited
            if (loopKilledLocations == null) {
                loopKilledLocations = new LoopKillCache(1/* 1.visit */);
                loopLocationKillCache.put(loop, loopKilledLocations);
            } else {
                OptionValues options = loop.getHeader().getBeginNode().getOptions();
                if (loopKilledLocations.visits() > ReadEliminationMaxLoopVisits.getValue(options)) {
                    // we have processed the loop too many times, kill all locations so the inner
                    // loop will never be processed more than once again on visit
                    loopKilledLocations.setKillsAll();
                } else {
                    // we have fully processed this loop >1 times, update the killed locations
                    EconomicSet<LocationIdentity> forwardEndLiveLocations = EconomicSet.create(Equivalence.DEFAULT);
                    for (CacheEntry<?> entry : initialState.readCache.getKeys()) {
                        forwardEndLiveLocations.add(entry.getIdentity());
                    }
                    for (CacheEntry<?> entry : mergedStates.readCache.getKeys()) {
                        forwardEndLiveLocations.remove(entry.getIdentity());
                    }
                    // every location that is alive before the loop but not after is killed by the
                    // loop
                    for (LocationIdentity location : forwardEndLiveLocations) {
                        loopKilledLocations.rememberLoopKilledLocation(location);
                    }
                    if (debug.isLogEnabled() && loopKilledLocations != null) {
                        debug.log("[Early Read Elimination] Setting loop killed locations of loop at node %s with %s",
                                        loop.getHeader().getBeginNode(), forwardEndLiveLocations);
                    }
                }
                // remember the loop visit
                loopKilledLocations.visited();
            }
        }
    }

    @Override
    protected ReadEliminationBlockState stripKilledLoopLocations(Loop<Block> loop, ReadEliminationBlockState originalInitialState) {
        ReadEliminationBlockState initialState = super.stripKilledLoopLocations(loop, originalInitialState);
        LoopKillCache loopKilledLocations = loopLocationKillCache.get(loop);
        if (loopKilledLocations != null && loopKilledLocations.loopKillsLocations()) {
            Iterator<CacheEntry<?>> it = initialState.readCache.getKeys().iterator();
            while (it.hasNext()) {
                CacheEntry<?> entry = it.next();
                if (loopKilledLocations.containsLocation(entry.getIdentity())) {
                    it.remove();
                }
            }
        }
        return initialState;
    }
}
