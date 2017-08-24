/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.core.common.LocationIdentity.any;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.core.common.CollectionsFactory;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.extended.UnsafeLoadNode;
import org.graalvm.compiler.nodes.extended.UnsafeStoreNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationBlockState.CacheEntry;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationBlockState.LoadCacheEntry;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationBlockState.UnsafeLoadCacheEntry;

import jdk.vm.ci.meta.JavaKind;

public class ReadEliminationClosure extends EffectsClosure<ReadEliminationBlockState> {

    public ReadEliminationClosure(ControlFlowGraph cfg) {
        super(null, cfg);
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
                    if (cachedValue != null && access.stamp().isCompatible(cachedValue.stamp())) {
                        effects.replaceAtUsages(access, cachedValue);
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
                if (cachedValue != null && read.stamp().isCompatible(cachedValue.stamp())) {
                    // Anchor guard if it is not fixed and different from cachedValue's guard such
                    // that it gets preserved.
                    if (read.getGuard() != null && !(read.getGuard() instanceof FixedNode)) {
                        if (!(cachedValue instanceof GuardedNode) || ((GuardedNode) cachedValue).getGuard() != read.getGuard()) {
                            effects.addFixedNodeBefore(new ValueAnchorNode((ValueNode) read.getGuard()), read);
                        }
                    }
                    effects.replaceAtUsages(read, cachedValue);
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
            if (node instanceof UnsafeLoadNode) {
                UnsafeLoadNode load = (UnsafeLoadNode) node;
                if (load.getLocationIdentity().isSingle()) {
                    ValueNode object = GraphUtil.unproxify(load.object());
                    UnsafeLoadCacheEntry identifier = new UnsafeLoadCacheEntry(object, load.offset(), load.getLocationIdentity());
                    ValueNode cachedValue = state.getCacheEntry(identifier);
                    if (cachedValue != null && load.stamp().isCompatible(cachedValue.stamp())) {
                        effects.replaceAtUsages(load, cachedValue);
                        addScalarAlias(load, cachedValue);
                        deleted = true;
                    } else {
                        state.addCacheEntry(identifier, load);
                    }
                }
            } else {
                assert node instanceof UnsafeStoreNode;
                UnsafeStoreNode write = (UnsafeStoreNode) node;
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
            for (Map.Entry<CacheEntry<?>, ValueNode> entry : exitState.getReadCache().entrySet()) {
                if (initialState.getReadCache().get(entry.getKey()) != entry.getValue()) {
                    ProxyNode proxy = new ValueProxyNode(exitState.getCacheEntry(entry.getKey()), exitNode);
                    effects.addFloatingNode(proxy, "readCacheProxy");
                    entry.setValue(proxy);
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

        private final HashMap<Object, ValuePhiNode> materializedPhis = CollectionsFactory.newMap();

        ReadEliminationMergeProcessor(Block mergeBlock) {
            super(mergeBlock);
        }

        protected <T> PhiNode getCachedPhi(T virtual, Stamp stamp) {
            ValuePhiNode result = materializedPhis.get(virtual);
            if (result == null) {
                result = createValuePhi(stamp);
                materializedPhis.put(virtual, result);
            }
            return result;
        }

        @Override
        protected void merge(List<ReadEliminationBlockState> states) {
            super.merge(states);

            mergeReadCache(states);
        }

        private void mergeReadCache(List<ReadEliminationBlockState> states) {
            for (Map.Entry<CacheEntry<?>, ValueNode> entry : states.get(0).readCache.entrySet()) {
                CacheEntry<?> key = entry.getKey();
                ValueNode value = entry.getValue();
                boolean phi = false;
                for (int i = 1; i < states.size(); i++) {
                    ValueNode otherValue = states.get(i).readCache.get(key);
                    // e.g. unsafe loads / stores with different access kinds have different stamps
                    // although location, object and offset are the same, in this case we cannot
                    // create a phi nor can we set a common value
                    if (otherValue == null || !value.stamp().isCompatible(otherValue.stamp())) {
                        value = null;
                        phi = false;
                        break;
                    }
                    if (!phi && otherValue != value) {
                        phi = true;
                    }
                }
                if (phi) {
                    PhiNode phiNode = getCachedPhi(entry, value.stamp().unrestricted());
                    mergeEffects.addFloatingNode(phiNode, "mergeReadCache");
                    for (int i = 0; i < states.size(); i++) {
                        ValueNode v = states.get(i).getCacheEntry(key);
                        assert phiNode.stamp().isCompatible(v.stamp()) : "Cannot create read elimination phi for inputs with incompatible stamps.";
                        setPhiInput(phiNode, i, v);
                    }
                    newState.addCacheEntry(key, phiNode);
                } else if (value != null) {
                    // case that there is the same value on all branches
                    newState.addCacheEntry(key, value);
                }
            }
            for (PhiNode phi : getPhis()) {
                if (phi.getStackKind() == JavaKind.Object) {
                    for (Map.Entry<CacheEntry<?>, ValueNode> entry : states.get(0).readCache.entrySet()) {
                        if (entry.getKey().object == getPhiValueAt(phi, 0)) {
                            mergeReadCachePhi(phi, entry.getKey(), states);
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
                    if (value == null || !values[i - 1].stamp().isCompatible(value.stamp())) {
                        return;
                    }
                    values[i] = value;
                }

                CacheEntry<?> newIdentifier = identifier.duplicateWithObject(phi);
                PhiNode phiNode = getCachedPhi(newIdentifier, values[0].stamp().unrestricted());
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
                if (loopKilledLocations.visits() > ReadEliminationMaxLoopVisits.getValue()) {
                    // we have processed the loop too many times, kill all locations so the inner
                    // loop will never be processed more than once again on visit
                    loopKilledLocations.setKillsAll();
                } else {
                    // we have fully processed this loop >1 times, update the killed locations
                    Set<LocationIdentity> forwardEndLiveLocations = new HashSet<>();
                    for (CacheEntry<?> entry : initialState.readCache.keySet()) {
                        forwardEndLiveLocations.add(entry.getIdentity());
                    }
                    for (CacheEntry<?> entry : mergedStates.readCache.keySet()) {
                        forwardEndLiveLocations.remove(entry.getIdentity());
                    }
                    // every location that is alive before the loop but not after is killed by the
                    // loop
                    for (LocationIdentity location : forwardEndLiveLocations) {
                        loopKilledLocations.rememberLoopKilledLocation(location);
                    }
                    if (Debug.isLogEnabled() && loopKilledLocations != null) {
                        Debug.log("[Early Read Elimination] Setting loop killed locations of loop at node %s with %s",
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
            Set<CacheEntry<?>> forwardEndLiveLocations = initialState.readCache.keySet();
            Iterator<CacheEntry<?>> it = forwardEndLiveLocations.iterator();
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
