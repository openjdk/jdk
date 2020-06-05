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


package org.graalvm.compiler.phases.common;

import static org.graalvm.compiler.graph.Graph.NodeEvent.NODE_ADDED;
import static org.graalvm.compiler.graph.Graph.NodeEvent.ZERO_USAGES;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.cfg.HIRLoop;
import org.graalvm.compiler.nodes.memory.FloatableAccessNode;
import org.graalvm.compiler.nodes.memory.FloatingAccessNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryAnchorNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.MemoryMap;
import org.graalvm.compiler.nodes.memory.MemoryMapNode;
import org.graalvm.compiler.nodes.memory.MemoryPhiNode;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.LoopInfo;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;
import jdk.internal.vm.compiler.word.LocationIdentity;

public class FloatingReadPhase extends Phase {

    private boolean createFloatingReads;
    private boolean createMemoryMapNodes;

    public static class MemoryMapImpl implements MemoryMap {

        private final EconomicMap<LocationIdentity, MemoryKill> lastMemorySnapshot;

        public MemoryMapImpl(MemoryMapImpl memoryMap) {
            lastMemorySnapshot = EconomicMap.create(Equivalence.DEFAULT, memoryMap.lastMemorySnapshot);
        }

        public MemoryMapImpl(StartNode start) {
            this();
            lastMemorySnapshot.put(any(), start);
        }

        public MemoryMapImpl() {
            lastMemorySnapshot = EconomicMap.create(Equivalence.DEFAULT);
        }

        @Override
        public MemoryKill getLastLocationAccess(LocationIdentity locationIdentity) {
            MemoryKill lastLocationAccess;
            if (locationIdentity.isImmutable()) {
                return null;
            } else {
                lastLocationAccess = lastMemorySnapshot.get(locationIdentity);
                if (lastLocationAccess == null) {
                    lastLocationAccess = lastMemorySnapshot.get(any());
                    assert lastLocationAccess != null;
                }
                return lastLocationAccess;
            }
        }

        @Override
        public Iterable<LocationIdentity> getLocations() {
            return lastMemorySnapshot.getKeys();
        }

        public EconomicMap<LocationIdentity, MemoryKill> getMap() {
            return lastMemorySnapshot;
        }
    }

    public FloatingReadPhase() {
        this(true, false);
    }

    /**
     * @param createFloatingReads specifies whether {@link FloatableAccessNode}s like
     *            {@link ReadNode} should be converted into floating nodes (e.g.,
     *            {@link FloatingReadNode}s) where possible
     * @param createMemoryMapNodes a {@link MemoryMapNode} will be created for each return if this
     *            is true
     */
    public FloatingReadPhase(boolean createFloatingReads, boolean createMemoryMapNodes) {
        this.createFloatingReads = createFloatingReads;
        this.createMemoryMapNodes = createMemoryMapNodes;
    }

    @Override
    public float codeSizeIncrease() {
        return 1.50f;
    }

    /**
     * Removes nodes from a given set that (transitively) have a usage outside the set.
     */
    private static EconomicSet<Node> removeExternallyUsedNodes(EconomicSet<Node> set) {
        boolean change;
        do {
            change = false;
            for (Iterator<Node> iter = set.iterator(); iter.hasNext();) {
                Node node = iter.next();
                for (Node usage : node.usages()) {
                    if (!set.contains(usage)) {
                        change = true;
                        iter.remove();
                        break;
                    }
                }
            }
        } while (change);
        return set;
    }

    protected void processNode(FixedNode node, EconomicSet<LocationIdentity> currentState) {
        if (node instanceof SingleMemoryKill) {
            processIdentity(currentState, ((SingleMemoryKill) node).getKilledLocationIdentity());
        } else if (node instanceof MultiMemoryKill) {
            for (LocationIdentity identity : ((MultiMemoryKill) node).getKilledLocationIdentities()) {
                processIdentity(currentState, identity);
            }
        }
    }

    private static void processIdentity(EconomicSet<LocationIdentity> currentState, LocationIdentity identity) {
        if (identity.isMutable()) {
            currentState.add(identity);
        }
    }

    protected void processBlock(Block b, EconomicSet<LocationIdentity> currentState) {
        for (FixedNode n : b.getNodes()) {
            processNode(n, currentState);
        }
    }

    private EconomicSet<LocationIdentity> processLoop(HIRLoop loop, EconomicMap<LoopBeginNode, EconomicSet<LocationIdentity>> modifiedInLoops) {
        LoopBeginNode loopBegin = (LoopBeginNode) loop.getHeader().getBeginNode();
        EconomicSet<LocationIdentity> result = modifiedInLoops.get(loopBegin);
        if (result != null) {
            return result;
        }

        result = EconomicSet.create(Equivalence.DEFAULT);
        for (Loop<Block> inner : loop.getChildren()) {
            result.addAll(processLoop((HIRLoop) inner, modifiedInLoops));
        }

        for (Block b : loop.getBlocks()) {
            if (b.getLoop() == loop) {
                processBlock(b, result);
            }
        }

        modifiedInLoops.put(loopBegin, result);
        return result;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph) {
        EconomicMap<LoopBeginNode, EconomicSet<LocationIdentity>> modifiedInLoops = null;
        if (graph.hasLoops()) {
            modifiedInLoops = EconomicMap.create(Equivalence.IDENTITY);
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);
            for (Loop<?> l : cfg.getLoops()) {
                HIRLoop loop = (HIRLoop) l;
                processLoop(loop, modifiedInLoops);
            }
        }

        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener(EnumSet.of(NODE_ADDED, ZERO_USAGES));
        try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
            ReentrantNodeIterator.apply(new FloatingReadClosure(modifiedInLoops, createFloatingReads, createMemoryMapNodes), graph.start(), new MemoryMapImpl(graph.start()));
        }

        for (Node n : removeExternallyUsedNodes(listener.getNodes())) {
            if (n.isAlive() && n instanceof FloatingNode) {
                n.replaceAtUsages(null);
                GraphUtil.killWithUnusedFloatingInputs(n);
            }
        }
        if (createFloatingReads) {
            assert !graph.isAfterFloatingReadPhase();
            graph.setAfterFloatingReadPhase(true);
        }
    }

    public static MemoryMapImpl mergeMemoryMaps(AbstractMergeNode merge, List<? extends MemoryMap> states) {
        MemoryMapImpl newState = new MemoryMapImpl();

        EconomicSet<LocationIdentity> keys = EconomicSet.create(Equivalence.DEFAULT);
        for (MemoryMap other : states) {
            keys.addAll(other.getLocations());
        }
        assert checkNoImmutableLocations(keys);

        for (LocationIdentity key : keys) {
            int mergedStatesCount = 0;
            boolean isPhi = false;
            MemoryKill merged = null;
            for (MemoryMap state : states) {
                MemoryKill last = state.getLastLocationAccess(key);
                if (isPhi) {
                    // Fortify: Suppress Null Deference false positive (`isPhi == true` implies
                    // `merged != null`)
                    ((MemoryPhiNode) merged).addInput(ValueNodeUtil.asNode(last));
                } else {
                    if (merged == last) {
                        // nothing to do
                    } else if (merged == null) {
                        merged = last;
                    } else {
                        MemoryPhiNode phi = merge.graph().addWithoutUnique(new MemoryPhiNode(merge, key));
                        for (int j = 0; j < mergedStatesCount; j++) {
                            phi.addInput(ValueNodeUtil.asNode(merged));
                        }
                        phi.addInput(ValueNodeUtil.asNode(last));
                        merged = phi;
                        isPhi = true;
                    }
                }
                mergedStatesCount++;
            }
            newState.getMap().put(key, merged);
        }
        return newState;

    }

    public static boolean nodeOfMemoryType(Node node) {
        return !(node instanceof MemoryKill) || (node instanceof SingleMemoryKill ^ node instanceof MultiMemoryKill);
    }

    private static boolean checkNoImmutableLocations(EconomicSet<LocationIdentity> keys) {
        keys.forEach(t -> {
            assert t.isMutable();
        });
        return true;
    }

    public static class FloatingReadClosure extends NodeIteratorClosure<MemoryMapImpl> {

        private final EconomicMap<LoopBeginNode, EconomicSet<LocationIdentity>> modifiedInLoops;
        private boolean createFloatingReads;
        private boolean createMemoryMapNodes;

        public FloatingReadClosure(EconomicMap<LoopBeginNode, EconomicSet<LocationIdentity>> modifiedInLoops, boolean createFloatingReads, boolean createMemoryMapNodes) {
            this.modifiedInLoops = modifiedInLoops;
            this.createFloatingReads = createFloatingReads;
            this.createMemoryMapNodes = createMemoryMapNodes;
        }

        @Override
        protected MemoryMapImpl processNode(FixedNode node, MemoryMapImpl state) {

            if (node instanceof LoopExitNode) {
                final LoopExitNode loopExitNode = (LoopExitNode) node;
                final EconomicSet<LocationIdentity> modifiedInLoop = modifiedInLoops.get(loopExitNode.loopBegin());
                final boolean anyModified = modifiedInLoop.contains(LocationIdentity.any());
                state.getMap().replaceAll((locationIdentity, memoryNode) -> (anyModified || modifiedInLoop.contains(locationIdentity))
                                ? ProxyNode.forMemory(memoryNode, loopExitNode, locationIdentity)
                                : memoryNode);
            }

            if (node instanceof MemoryAnchorNode) {
                processAnchor((MemoryAnchorNode) node, state);
                return state;
            }

            if (node instanceof MemoryAccess) {
                processAccess((MemoryAccess) node, state);
            }

            if (createFloatingReads && node instanceof FloatableAccessNode) {
                processFloatable((FloatableAccessNode) node, state);
            }
            if (node instanceof SingleMemoryKill) {
                processCheckpoint((SingleMemoryKill) node, state);
            } else if (node instanceof MultiMemoryKill) {
                processCheckpoint((MultiMemoryKill) node, state);
            }
            assert nodeOfMemoryType(node) : node;

            if (createMemoryMapNodes && node instanceof ReturnNode) {
                ((ReturnNode) node).setMemoryMap(node.graph().unique(new MemoryMapNode(state.getMap())));
            }
            return state;
        }

        /**
         * Improve the memory graph by re-wiring all usages of a {@link MemoryAnchorNode} to the
         * real last access location.
         */
        private static void processAnchor(MemoryAnchorNode anchor, MemoryMapImpl state) {
            for (Node node : anchor.usages().snapshot()) {
                if (node instanceof MemoryAccess) {
                    MemoryAccess access = (MemoryAccess) node;
                    if (access.getLastLocationAccess() == anchor) {
                        MemoryKill lastLocationAccess = state.getLastLocationAccess(access.getLocationIdentity());
                        assert lastLocationAccess != null;
                        access.setLastLocationAccess(lastLocationAccess);
                    }
                }
            }

            if (anchor.hasNoUsages()) {
                anchor.graph().removeFixed(anchor);
            }
        }

        private static void processAccess(MemoryAccess access, MemoryMapImpl state) {
            LocationIdentity locationIdentity = access.getLocationIdentity();
            if (!locationIdentity.equals(LocationIdentity.any())) {
                MemoryKill lastLocationAccess = state.getLastLocationAccess(locationIdentity);
                access.setLastLocationAccess(lastLocationAccess);
            }
        }

        private static void processCheckpoint(SingleMemoryKill checkpoint, MemoryMapImpl state) {
            processIdentity(checkpoint.getKilledLocationIdentity(), checkpoint, state);
        }

        private static void processCheckpoint(MultiMemoryKill checkpoint, MemoryMapImpl state) {
            for (LocationIdentity identity : checkpoint.getKilledLocationIdentities()) {
                processIdentity(identity, checkpoint, state);
            }
        }

        private static void processIdentity(LocationIdentity identity, MemoryKill checkpoint, MemoryMapImpl state) {
            if (identity.isAny()) {
                state.getMap().clear();
            }
            if (identity.isMutable()) {
                state.getMap().put(identity, checkpoint);
            }
        }

        @SuppressWarnings("try")
        private static void processFloatable(FloatableAccessNode accessNode, MemoryMapImpl state) {
            StructuredGraph graph = accessNode.graph();
            LocationIdentity locationIdentity = accessNode.getLocationIdentity();
            if (accessNode.canFloat()) {
                assert accessNode.getNullCheck() == false;
                MemoryKill lastLocationAccess = state.getLastLocationAccess(locationIdentity);
                try (DebugCloseable position = accessNode.withNodeSourcePosition()) {
                    FloatingAccessNode floatingNode = accessNode.asFloatingNode();
                    assert floatingNode.getLastLocationAccess() == lastLocationAccess;
                    graph.replaceFixedWithFloating(accessNode, floatingNode);
                }
            }
        }

        @Override
        protected MemoryMapImpl merge(AbstractMergeNode merge, List<MemoryMapImpl> states) {
            return mergeMemoryMaps(merge, states);
        }

        @Override
        protected MemoryMapImpl afterSplit(AbstractBeginNode node, MemoryMapImpl oldState) {
            MemoryMapImpl result = new MemoryMapImpl(oldState);
            if (node.predecessor() instanceof WithExceptionNode && node.predecessor() instanceof MemoryKill) {
                /*
                 * This WithExceptionNode cannot be the lastLocationAccess for a FloatingReadNode.
                 * Since it is both a memory kill and a control flow split, the scheduler cannot
                 * schedule anything immediately after the kill. It can only schedule in the normal
                 * or exceptional successor - and we have to tell the scheduler here which side it
                 * needs to choose by putting in the location identity on both successors.
                 */
                LocationIdentity killedLocationIdentity = node.predecessor() instanceof SingleMemoryKill ? ((SingleMemoryKill) node.predecessor()).getKilledLocationIdentity() : LocationIdentity.any();
                result.getMap().put(killedLocationIdentity, (MemoryKill) node);
            }
            return result;
        }

        @Override
        protected EconomicMap<LoopExitNode, MemoryMapImpl> processLoop(LoopBeginNode loop, MemoryMapImpl initialState) {
            EconomicSet<LocationIdentity> modifiedLocations = modifiedInLoops.get(loop);
            EconomicMap<LocationIdentity, MemoryPhiNode> phis = EconomicMap.create(Equivalence.DEFAULT);
            if (modifiedLocations.contains(LocationIdentity.any())) {
                // create phis for all locations if ANY is modified in the loop
                modifiedLocations = EconomicSet.create(Equivalence.DEFAULT, modifiedLocations);
                modifiedLocations.addAll(initialState.getMap().getKeys());
            }

            for (LocationIdentity location : modifiedLocations) {
                createMemoryPhi(loop, initialState, phis, location);
            }
            initialState.getMap().putAll(phis);

            LoopInfo<MemoryMapImpl> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);

            UnmodifiableMapCursor<LoopEndNode, MemoryMapImpl> endStateCursor = loopInfo.endStates.getEntries();
            while (endStateCursor.advance()) {
                int endIndex = loop.phiPredecessorIndex(endStateCursor.getKey());
                UnmodifiableMapCursor<LocationIdentity, MemoryPhiNode> phiCursor = phis.getEntries();
                while (phiCursor.advance()) {
                    LocationIdentity key = phiCursor.getKey();
                    PhiNode phi = phiCursor.getValue();
                    phi.initializeValueAt(endIndex, ValueNodeUtil.asNode(endStateCursor.getValue().getLastLocationAccess(key)));
                }
            }
            return loopInfo.exitStates;
        }

        private static void createMemoryPhi(LoopBeginNode loop, MemoryMapImpl initialState, EconomicMap<LocationIdentity, MemoryPhiNode> phis, LocationIdentity location) {
            MemoryPhiNode phi = loop.graph().addWithoutUnique(new MemoryPhiNode(loop, location));
            phi.addInput(ValueNodeUtil.asNode(initialState.getLastLocationAccess(location)));
            phis.put(location, phi);
        }
    }
}
