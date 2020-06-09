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

import static org.graalvm.compiler.core.common.GraalOptions.ReadEliminationMaxLoopVisits;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.MapCursor;
import jdk.internal.vm.compiler.collections.Pair;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.virtual.phases.ea.PEReadEliminationBlockState.ReadCacheEntry;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class PEReadEliminationClosure extends PartialEscapeClosure<PEReadEliminationBlockState> {

    private static final EnumMap<JavaKind, LocationIdentity> UNBOX_LOCATIONS;

    static {
        UNBOX_LOCATIONS = new EnumMap<>(JavaKind.class);
        for (JavaKind kind : JavaKind.values()) {
            UNBOX_LOCATIONS.put(kind, NamedLocationIdentity.immutable("PEA unbox " + kind.getJavaName()));
        }
    }

    public PEReadEliminationClosure(ScheduleResult schedule, CoreProviders providers) {
        super(schedule, providers);
    }

    @Override
    protected PEReadEliminationBlockState getInitialState() {
        return new PEReadEliminationBlockState(tool.getOptions(), tool.getDebug());
    }

    @Override
    protected boolean processNode(Node node, PEReadEliminationBlockState state, GraphEffectList effects, FixedWithNextNode lastFixedNode) {
        if (super.processNode(node, state, effects, lastFixedNode)) {
            return true;
        }

        if (node instanceof LoadFieldNode) {
            return processLoadField((LoadFieldNode) node, state, effects);
        } else if (node instanceof StoreFieldNode) {
            return processStoreField((StoreFieldNode) node, state, effects);
        } else if (node instanceof LoadIndexedNode) {
            return processLoadIndexed((LoadIndexedNode) node, state, effects);
        } else if (node instanceof StoreIndexedNode) {
            return processStoreIndexed((StoreIndexedNode) node, state, effects);
        } else if (node instanceof ArrayLengthNode) {
            return processArrayLength((ArrayLengthNode) node, state, effects);
        } else if (node instanceof UnboxNode) {
            return processUnbox((UnboxNode) node, state, effects);
        } else if (node instanceof RawLoadNode) {
            return processUnsafeLoad((RawLoadNode) node, state, effects);
        } else if (node instanceof RawStoreNode) {
            return processUnsafeStore((RawStoreNode) node, state, effects);
        } else if (node instanceof SingleMemoryKill) {
            COUNTER_MEMORYCHECKPOINT.increment(node.getDebug());
            LocationIdentity identity = ((SingleMemoryKill) node).getKilledLocationIdentity();
            processIdentity(state, identity);
        } else if (node instanceof MultiMemoryKill) {
            COUNTER_MEMORYCHECKPOINT.increment(node.getDebug());
            for (LocationIdentity identity : ((MultiMemoryKill) node).getKilledLocationIdentities()) {
                processIdentity(state, identity);
            }
        }

        return false;
    }

    private boolean processStore(FixedNode store, ValueNode object, LocationIdentity identity, int index, JavaKind accessKind, boolean overflowAccess, ValueNode value,
                    PEReadEliminationBlockState state, GraphEffectList effects) {
        ValueNode unproxiedObject = GraphUtil.unproxify(object);
        ValueNode cachedValue = state.getReadCache(object, identity, index, accessKind, this);

        ValueNode finalValue = getScalarAlias(value);
        boolean result = false;
        if (GraphUtil.unproxify(finalValue) == GraphUtil.unproxify(cachedValue)) {
            effects.deleteNode(store);
            result = true;
        }
        state.killReadCache(identity, index);
        state.addReadCache(unproxiedObject, identity, index, accessKind, overflowAccess, finalValue, this);
        return result;
    }

    private boolean processLoad(FixedNode load, ValueNode object, LocationIdentity identity, int index, JavaKind kind, PEReadEliminationBlockState state, GraphEffectList effects) {
        ValueNode unproxiedObject = GraphUtil.unproxify(object);
        ValueNode cachedValue = state.getReadCache(unproxiedObject, identity, index, kind, this);
        if (cachedValue != null) {
            // perform the read elimination
            effects.replaceAtUsages(load, cachedValue, load);
            addScalarAlias(load, cachedValue);
            return true;
        } else {
            state.addReadCache(unproxiedObject, identity, index, kind, false, load, this);
            return false;
        }
    }

    private static boolean isOverflowAccess(JavaKind accessKind, JavaKind declaredKind) {
        if (accessKind == declaredKind) {
            return false;
        }
        if (accessKind == JavaKind.Object) {
            switch (declaredKind) {
                case Object:
                case Double:
                case Long:
                    return false;
                default:
                    return true;
            }
        }
        assert accessKind.isPrimitive() : "Illegal access kind";
        return declaredKind.isPrimitive() ? accessKind.getBitCount() > declaredKind.getBitCount() : true;
    }

    private boolean processUnsafeLoad(RawLoadNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.offset().isConstant()) {
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && type.isArray()) {
                JavaKind accessKind = load.accessKind();
                JavaKind componentKind = type.getComponentType().getJavaKind();
                long offset = load.offset().asJavaConstant().asLong();
                int index = VirtualArrayNode.entryIndexForOffset(tool.getMetaAccess(), offset, accessKind, type.getComponentType(), Integer.MAX_VALUE);
                if (index >= 0) {
                    ValueNode object = GraphUtil.unproxify(load.object());
                    LocationIdentity location = NamedLocationIdentity.getArrayLocation(componentKind);
                    ValueNode cachedValue = state.getReadCache(object, location, index, accessKind, this);
                    assert cachedValue == null || load.stamp(NodeView.DEFAULT).isCompatible(cachedValue.stamp(NodeView.DEFAULT)) : "The RawLoadNode's stamp is not compatible with the cached value.";
                    if (cachedValue != null) {
                        effects.replaceAtUsages(load, cachedValue, load);
                        addScalarAlias(load, cachedValue);
                        return true;
                    } else {
                        state.addReadCache(object, location, index, accessKind, isOverflowAccess(accessKind, componentKind), load, this);
                    }
                }
            }
        }
        return false;
    }

    private boolean processUnsafeStore(RawStoreNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        ResolvedJavaType type = StampTool.typeOrNull(store.object());
        if (type != null && type.isArray()) {
            JavaKind accessKind = store.accessKind();
            JavaKind componentKind = type.getComponentType().getJavaKind();
            LocationIdentity location = NamedLocationIdentity.getArrayLocation(componentKind);
            if (store.offset().isConstant()) {
                long offset = store.offset().asJavaConstant().asLong();
                boolean overflowAccess = isOverflowAccess(accessKind, componentKind);
                int index = overflowAccess ? -1 : VirtualArrayNode.entryIndexForOffset(tool.getMetaAccess(), offset, accessKind, type.getComponentType(), Integer.MAX_VALUE);
                if (index != -1) {
                    return processStore(store, store.object(), location, index, accessKind, overflowAccess, store.value(), state, effects);
                } else {
                    state.killReadCache(location, index);
                }
            } else {
                processIdentity(state, location);
            }
        } else {
            state.killReadCache();
        }
        return false;
    }

    private boolean processArrayLength(ArrayLengthNode length, PEReadEliminationBlockState state, GraphEffectList effects) {
        return processLoad(length, length.array(), ARRAY_LENGTH_LOCATION, -1, JavaKind.Int, state, effects);
    }

    private boolean processStoreField(StoreFieldNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (store.isVolatile()) {
            state.killReadCache();
            return false;
        }
        JavaKind kind = store.field().getJavaKind();
        return processStore(store, store.object(), new FieldLocationIdentity(store.field()), -1, kind, false, store.value(), state, effects);
    }

    private boolean processLoadField(LoadFieldNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.isVolatile()) {
            state.killReadCache();
            return false;
        }
        return processLoad(load, load.object(), new FieldLocationIdentity(load.field()), -1, load.field().getJavaKind(), state, effects);
    }

    private boolean processStoreIndexed(StoreIndexedNode store, PEReadEliminationBlockState state, GraphEffectList effects) {
        int index = store.index().isConstant() ? ((JavaConstant) store.index().asConstant()).asInt() : -1;
        JavaKind elementKind = store.elementKind();
        LocationIdentity arrayLocation = NamedLocationIdentity.getArrayLocation(elementKind);
        if (index != -1) {
            return processStore(store, store.array(), arrayLocation, index, elementKind, false, store.value(), state, effects);
        } else {
            state.killReadCache(arrayLocation, -1);
        }
        return false;
    }

    private boolean processLoadIndexed(LoadIndexedNode load, PEReadEliminationBlockState state, GraphEffectList effects) {
        if (load.index().isConstant()) {
            int index = ((JavaConstant) load.index().asConstant()).asInt();
            JavaKind elementKind = load.elementKind();
            LocationIdentity arrayLocation = NamedLocationIdentity.getArrayLocation(elementKind);
            return processLoad(load, load.array(), arrayLocation, index, elementKind, state, effects);
        }
        return false;
    }

    private boolean processUnbox(UnboxNode unbox, PEReadEliminationBlockState state, GraphEffectList effects) {
        return processLoad(unbox, unbox.getValue(), UNBOX_LOCATIONS.get(unbox.getBoxingKind()), -1, unbox.getBoxingKind(), state, effects);
    }

    private static void processIdentity(PEReadEliminationBlockState state, LocationIdentity identity) {
        if (identity.isAny()) {
            state.killReadCache();
        } else {
            state.killReadCache(identity, -1);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void processInitialLoopState(Loop<Block> loop, PEReadEliminationBlockState initialState) {
        super.processInitialLoopState(loop, initialState);

        if (!initialState.getReadCache().isEmpty()) {
            EconomicMap<ValueNode, Pair<ValueNode, Object>> firstValueSet = null;
            for (PhiNode phi : ((LoopBeginNode) loop.getHeader().getBeginNode()).phis()) {
                ValueNode firstValue = phi.valueAt(0);
                if (firstValue != null && phi.getStackKind().isObject()) {
                    ValueNode unproxified = GraphUtil.unproxify(firstValue);
                    if (firstValueSet == null) {
                        firstValueSet = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
                    }
                    Pair<ValueNode, Object> pair = Pair.create(unproxified, firstValueSet.get(unproxified));
                    firstValueSet.put(unproxified, pair);
                }
            }

            if (firstValueSet != null) {
                ReadCacheEntry[] entries = new ReadCacheEntry[initialState.getReadCache().size()];
                int z = 0;
                for (ReadCacheEntry entry : initialState.getReadCache().getKeys()) {
                    entries[z++] = entry;
                }

                for (ReadCacheEntry entry : entries) {
                    ValueNode object = entry.object;
                    if (object != null) {
                        Pair<ValueNode, Object> pair = firstValueSet.get(object);
                        while (pair != null) {
                            initialState.addReadCache(pair.getLeft(), entry.identity, entry.index, entry.kind, entry.overflowAccess, initialState.getReadCache().get(entry), this);
                            pair = (Pair<ValueNode, Object>) pair.getRight();
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void processLoopExit(LoopExitNode exitNode, PEReadEliminationBlockState initialState, PEReadEliminationBlockState exitState, GraphEffectList effects) {
        super.processLoopExit(exitNode, initialState, exitState, effects);

        if (exitNode.graph().hasValueProxies()) {
            MapCursor<ReadCacheEntry, ValueNode> entry = exitState.getReadCache().getEntries();
            while (entry.advance()) {
                if (initialState.getReadCache().get(entry.getKey()) != entry.getValue()) {
                    ValueNode value = exitState.getReadCache(entry.getKey().object, entry.getKey().identity, entry.getKey().index, entry.getKey().kind, this);
                    assert value != null : "Got null from read cache, entry's value:" + entry.getValue();
                    if (!(value instanceof ProxyNode) || ((ProxyNode) value).proxyPoint() != exitNode) {
                        ProxyNode proxy = new ValueProxyNode(value, exitNode);
                        effects.addFloatingNode(proxy, "readCacheProxy");
                        exitState.getReadCache().put(entry.getKey(), proxy);
                    }
                }
            }
        }
    }

    @Override
    protected PEReadEliminationBlockState cloneState(PEReadEliminationBlockState other) {
        return new PEReadEliminationBlockState(other);
    }

    @Override
    protected MergeProcessor createMergeProcessor(Block merge) {
        return new ReadEliminationMergeProcessor(merge);
    }

    private class ReadEliminationMergeProcessor extends MergeProcessor {

        ReadEliminationMergeProcessor(Block mergeBlock) {
            super(mergeBlock);
        }

        @Override
        protected void merge(List<PEReadEliminationBlockState> states) {
            super.merge(states);

            mergeReadCache(states);
        }

        private void mergeReadCache(List<PEReadEliminationBlockState> states) {
            MapCursor<ReadCacheEntry, ValueNode> cursor = states.get(0).readCache.getEntries();
            while (cursor.advance()) {
                ReadCacheEntry key = cursor.getKey();
                ValueNode value = cursor.getValue();
                boolean phi = false;
                for (int i = 1; i < states.size(); i++) {
                    ValueNode otherValue = states.get(i).readCache.get(key);
                    // e.g. unsafe loads / stores with different access kinds have different stamps
                    // although location, object and offset are the same, in this case we cannot
                    // create a phi nor can we set a common value
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
                    PhiNode phiNode = getPhi(key, value.stamp(NodeView.DEFAULT).unrestricted());
                    mergeEffects.addFloatingNode(phiNode, "mergeReadCache");
                    for (int i = 0; i < states.size(); i++) {
                        ValueNode v = states.get(i).getReadCache(key.object, key.identity, key.index, key.kind, PEReadEliminationClosure.this);
                        assert phiNode.stamp(NodeView.DEFAULT).isCompatible(v.stamp(NodeView.DEFAULT)) : "Cannot create read elimination phi for inputs with incompatible stamps.";
                        setPhiInput(phiNode, i, v);
                    }
                    newState.readCache.put(key, phiNode);
                } else if (value != null) {
                    newState.readCache.put(key, value);
                }
            }
            /*
             * For object phis, see if there are known reads on all predecessors, for which we could
             * create new phis.
             */
            for (PhiNode phi : getPhis()) {
                if (phi.getStackKind() == JavaKind.Object) {
                    for (ReadCacheEntry entry : states.get(0).readCache.getKeys()) {
                        if (entry.object == getPhiValueAt(phi, 0)) {
                            mergeReadCachePhi(phi, entry.identity, entry.index, entry.kind, entry.overflowAccess, states);
                        }
                    }
                }
            }
        }

        private void mergeReadCachePhi(PhiNode phi, LocationIdentity identity, int index, JavaKind kind, boolean overflowAccess, List<PEReadEliminationBlockState> states) {
            ValueNode[] values = new ValueNode[states.size()];
            values[0] = states.get(0).getReadCache(getPhiValueAt(phi, 0), identity, index, kind, PEReadEliminationClosure.this);
            if (values[0] != null) {
                for (int i = 1; i < states.size(); i++) {
                    ValueNode value = states.get(i).getReadCache(getPhiValueAt(phi, i), identity, index, kind, PEReadEliminationClosure.this);
                    // e.g. unsafe loads / stores with same identity and different access kinds see
                    // mergeReadCache(states)
                    if (value == null || !values[i - 1].stamp(NodeView.DEFAULT).isCompatible(value.stamp(NodeView.DEFAULT))) {
                        return;
                    }
                    values[i] = value;
                }

                PhiNode phiNode = getPhi(new ReadCacheEntry(identity, phi, index, kind, overflowAccess), values[0].stamp(NodeView.DEFAULT).unrestricted());
                mergeEffects.addFloatingNode(phiNode, "mergeReadCachePhi");
                for (int i = 0; i < values.length; i++) {
                    setPhiInput(phiNode, i, values[i]);
                }
                newState.readCache.put(new ReadCacheEntry(identity, phi, index, kind, overflowAccess), phiNode);
            }
        }
    }

    @Override
    protected void processKilledLoopLocations(Loop<Block> loop, PEReadEliminationBlockState initialState, PEReadEliminationBlockState mergedStates) {
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
                AbstractBeginNode beginNode = loop.getHeader().getBeginNode();
                OptionValues options = beginNode.getOptions();
                if (loopKilledLocations.visits() > ReadEliminationMaxLoopVisits.getValue(options)) {
                    // we have processed the loop too many times, kill all locations so the inner
                    // loop will never be processed more than once again on visit
                    loopKilledLocations.setKillsAll();
                } else {
                    // we have fully processed this loop >1 times, update the killed locations
                    EconomicSet<LocationIdentity> forwardEndLiveLocations = EconomicSet.create(Equivalence.DEFAULT);
                    for (ReadCacheEntry entry : initialState.readCache.getKeys()) {
                        forwardEndLiveLocations.add(entry.identity);
                    }
                    for (ReadCacheEntry entry : mergedStates.readCache.getKeys()) {
                        forwardEndLiveLocations.remove(entry.identity);
                    }
                    // every location that is alive before the loop but not after is killed by the
                    // loop
                    for (LocationIdentity location : forwardEndLiveLocations) {
                        loopKilledLocations.rememberLoopKilledLocation(location);
                    }
                    if (debug.isLogEnabled() && loopKilledLocations != null) {
                        debug.log("[Early Read Elimination] Setting loop killed locations of loop at node %s with %s",
                                        beginNode, forwardEndLiveLocations);
                    }
                }
                // remember the loop visit
                loopKilledLocations.visited();
            }
        }
    }

    @Override
    protected PEReadEliminationBlockState stripKilledLoopLocations(Loop<Block> loop, PEReadEliminationBlockState originalInitialState) {
        PEReadEliminationBlockState initialState = super.stripKilledLoopLocations(loop, originalInitialState);
        LoopKillCache loopKilledLocations = loopLocationKillCache.get(loop);
        if (loopKilledLocations != null && loopKilledLocations.loopKillsLocations()) {
            Iterator<ReadCacheEntry> it = initialState.readCache.getKeys().iterator();
            while (it.hasNext()) {
                ReadCacheEntry entry = it.next();
                if (loopKilledLocations.containsLocation(entry.identity)) {
                    it.remove();
                }
            }
        }
        return initialState;
    }

}
