/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.phases.aot;

import static org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph.strictlyDominates;
import static org.graalvm.compiler.hotspot.nodes.aot.LoadMethodCountersNode.getLoadMethodCountersNodes;
import static org.graalvm.compiler.nodes.ConstantNode.getConstantNodes;

import java.lang.ref.Reference;
import java.util.HashSet;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.hotspot.meta.HotSpotConstantLoadAction;
import org.graalvm.compiler.hotspot.nodes.aot.InitializeKlassNode;
import org.graalvm.compiler.hotspot.nodes.aot.LoadConstantIndirectlyFixedNode;
import org.graalvm.compiler.hotspot.nodes.aot.LoadConstantIndirectlyNode;
import org.graalvm.compiler.hotspot.nodes.aot.LoadMethodCountersNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveConstantNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveDynamicConstantNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveMethodAndLoadCountersNode;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ReplaceConstantNodesPhase extends BasePhase<CoreProviders> {

    private final boolean verifyFingerprints;
    private final boolean allowResolution;

    static Class<?> characterCacheClass = Character.class.getDeclaredClasses()[0];
    static Class<?> byteCacheClass = Byte.class.getDeclaredClasses()[0];
    static Class<?> shortCacheClass = Short.class.getDeclaredClasses()[0];
    static Class<?> integerCacheClass = Integer.class.getDeclaredClasses()[0];
    static Class<?> longCacheClass = Long.class.getDeclaredClasses()[0];

    static class ClassInfo {

        private ResolvedJavaType stringType;
        private ResolvedJavaType referenceType;
        private final HashSet<ResolvedJavaType> builtIns = new HashSet<>();

        ClassInfo(MetaAccessProvider metaAccessProvider) {
            builtIns.add(metaAccessProvider.lookupJavaType(Boolean.class));

            assert "java.lang.Character$CharacterCache".equals(characterCacheClass.getName());
            builtIns.add(metaAccessProvider.lookupJavaType(characterCacheClass));

            assert "java.lang.Byte$ByteCache".equals(byteCacheClass.getName());
            builtIns.add(metaAccessProvider.lookupJavaType(byteCacheClass));

            assert "java.lang.Short$ShortCache".equals(shortCacheClass.getName());
            builtIns.add(metaAccessProvider.lookupJavaType(shortCacheClass));

            assert "java.lang.Integer$IntegerCache".equals(integerCacheClass.getName());
            builtIns.add(metaAccessProvider.lookupJavaType(integerCacheClass));

            assert "java.lang.Long$LongCache".equals(longCacheClass.getName());
            builtIns.add(metaAccessProvider.lookupJavaType(longCacheClass));

            stringType = metaAccessProvider.lookupJavaType(String.class);
            referenceType = metaAccessProvider.lookupJavaType(Reference.class);
        }
    }

    private static boolean isReplacementNode(Node n) {
        // @formatter:off
        return n instanceof LoadConstantIndirectlyNode      ||
                n instanceof LoadConstantIndirectlyFixedNode ||
                n instanceof ResolveDynamicConstantNode      ||
                n instanceof ResolveConstantNode             ||
                n instanceof InitializeKlassNode;
        // @formatter:on
    }

    private static boolean anyUsagesNeedReplacement(ConstantNode node) {
        return node.usages().filter(n -> !isReplacementNode(n)).isNotEmpty();
    }

    private static boolean anyUsagesNeedReplacement(LoadMethodCountersNode node) {
        return node.usages().filter(n -> !(n instanceof ResolveMethodAndLoadCountersNode)).isNotEmpty();
    }

    private static boolean checkForBadFingerprint(HotSpotResolvedJavaType type) {
        if (type.isArray()) {
            if (type.getElementalType().isPrimitive()) {
                return false;
            }
            return ((HotSpotResolvedObjectType) (type.getElementalType())).getFingerprint() == 0;
        }
        return ((HotSpotResolvedObjectType) type).getFingerprint() == 0;
    }

    /**
     * Insert the replacement node into the graph. We may need to insert it into a place different
     * than the original {@link FloatingNode} since we need to make sure that replacement will have
     * a valid state assigned.
     *
     * @param graph
     * @param stateMapper
     * @param node
     * @param replacement
     */
    private static void insertReplacement(StructuredGraph graph, FrameStateMapperClosure stateMapper, FloatingNode node, FixedWithNextNode replacement) {
        FixedWithNextNode insertionPoint = findInsertionPoint(graph, stateMapper, node);
        graph.addAfterFixed(insertionPoint, replacement);
        stateMapper.addState(replacement, stateMapper.getState(insertionPoint));
    }

    /**
     * Find a good place to insert a stateful fixed node that is above the given node. A good
     * insertion point should have a valid FrameState reaching it.
     *
     * @param graph
     * @param stateMapper
     * @param node start search from this node up
     * @return an insertion point
     */
    private static FixedWithNextNode findInsertionPoint(StructuredGraph graph, FrameStateMapperClosure stateMapper, FloatingNode node) {
        FixedWithNextNode fixed = findFixedBeforeFloating(graph, node);
        FixedWithNextNode result = findFixedWithValidState(graph, stateMapper, fixed);
        return result;
    }

    /**
     * Find the first {@link FixedWithNextNode} that is currently scheduled before the given
     * floating node.
     *
     * @param graph
     * @param node start search from this node up
     * @return the first {@link FixedWithNextNode}
     */
    private static FixedWithNextNode findFixedBeforeFloating(StructuredGraph graph, FloatingNode node) {
        ScheduleResult schedule = graph.getLastSchedule();
        NodeMap<Block> nodeToBlock = schedule.getNodeToBlockMap();
        Block block = nodeToBlock.get(node);
        BlockMap<List<Node>> blockToNodes = schedule.getBlockToNodesMap();
        FixedWithNextNode result = null;
        for (Node n : blockToNodes.get(block)) {
            if (n.equals(node)) {
                break;
            }
            if (n instanceof FixedWithNextNode) {
                result = (FixedWithNextNode) n;
            }
        }
        assert result != null;
        return result;
    }

    /**
     * Find first dominating {@link FixedWithNextNode} that has a valid state reaching it starting
     * from the given node.
     *
     * @param graph
     * @param stateMapper
     * @param node
     * @return {@link FixedWithNextNode} that we can use as an insertion point
     */
    private static FixedWithNextNode findFixedWithValidState(StructuredGraph graph, FrameStateMapperClosure stateMapper, FixedWithNextNode node) {
        ScheduleResult schedule = graph.getLastSchedule();
        NodeMap<Block> nodeToBlock = schedule.getNodeToBlockMap();
        Block block = nodeToBlock.get(node);

        Node n = node;
        do {
            if (isFixedWithValidState(stateMapper, n)) {
                return (FixedWithNextNode) n;
            }
            while (n != block.getBeginNode()) {
                n = n.predecessor();
                if (isFixedWithValidState(stateMapper, n)) {
                    return (FixedWithNextNode) n;
                }
            }
            block = block.getDominator();
            if (block != null) {
                n = block.getEndNode();
            }
        } while (block != null);

        return graph.start();
    }

    private static boolean isFixedWithValidState(FrameStateMapperClosure stateMapper, Node n) {
        if (n instanceof FixedWithNextNode) {
            FixedWithNextNode fixed = (FixedWithNextNode) n;
            assert stateMapper.getState(fixed) != null;
            if (!BytecodeFrame.isPlaceholderBci(stateMapper.getState(fixed).bci)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compute frame states for all fixed nodes in the graph.
     */
    private static class FrameStateMapperClosure extends NodeIteratorClosure<FrameState> {
        private NodeMap<FrameState> reachingStates;

        @Override
        protected FrameState processNode(FixedNode node, FrameState previousState) {
            FrameState currentState = previousState;
            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState stateAfter = stateSplit.stateAfter();
                if (stateAfter != null) {
                    currentState = stateAfter;
                }
            }
            reachingStates.put(node, currentState);
            return currentState;
        }

        @Override
        protected FrameState merge(AbstractMergeNode merge, List<FrameState> states) {
            FrameState singleFrameState = singleFrameState(states);
            FrameState currentState = singleFrameState == null ? merge.stateAfter() : singleFrameState;
            reachingStates.put(merge, currentState);
            return currentState;
        }

        @Override
        protected FrameState afterSplit(AbstractBeginNode node, FrameState oldState) {
            return oldState;
        }

        @Override
        protected EconomicMap<LoopExitNode, FrameState> processLoop(LoopBeginNode loop, FrameState initialState) {
            return ReentrantNodeIterator.processLoop(this, loop, initialState).exitStates;
        }

        private static FrameState singleFrameState(List<FrameState> states) {
            FrameState singleState = states.get(0);
            for (int i = 1; i < states.size(); ++i) {
                if (states.get(i) != singleState) {
                    return null;
                }
            }
            return singleState;
        }

        FrameStateMapperClosure(StructuredGraph graph) {
            reachingStates = new NodeMap<>(graph);
        }

        public FrameState getState(Node n) {
            return reachingStates.get(n);
        }

        public void addState(Node n, FrameState s) {
            reachingStates.setAndGrow(n, s);
        }
    }

    /**
     * Try to find dominating node doing the resolution that can be reused.
     *
     * @param graph
     * @param node {@link ConstantNode} containing a {@link HotSpotResolvedJavaType} that needs
     *            resolution.
     * @return return true if all usages of the node have been replaced
     */
    private static boolean tryToReplaceWithExisting(StructuredGraph graph, ConstantNode node) {
        boolean allUsagesReplaced = true;
        ScheduleResult schedule = graph.getLastSchedule();
        NodeMap<Block> nodeToBlock = schedule.getNodeToBlockMap();
        BlockMap<List<Node>> blockToNodes = schedule.getBlockToNodesMap();

        EconomicMap<Block, Node> blockToExisting = EconomicMap.create(Equivalence.IDENTITY);
        for (Node n : node.usages().filter(n -> isReplacementNode(n))) {
            blockToExisting.put(nodeToBlock.get(n), n);
        }
        for (Node use : node.usages().filter(n -> !isReplacementNode(n)).snapshot()) {
            boolean replaced = false;
            Block b = nodeToBlock.get(use);
            Node e = blockToExisting.get(b);
            if (e != null) {
                // There is an initialization or resolution in the same block as the use, look if
                // the use is scheduled after it.
                for (Node n : blockToNodes.get(b)) {
                    if (n.equals(use)) {
                        // Usage is before initialization, can't use it
                        break;
                    }
                    if (n.equals(e)) {
                        use.replaceFirstInput(node, e);
                        replaced = true;
                        break;
                    }
                }
            }
            if (!replaced) {
                // Look for dominating blocks that have existing nodes
                for (Block d : blockToExisting.getKeys()) {
                    if (strictlyDominates(d, b)) {
                        use.replaceFirstInput(node, blockToExisting.get(d));
                        replaced = true;
                        break;
                    }
                }
            }
            if (!replaced && allUsagesReplaced) {
                allUsagesReplaced = false;
            }
        }
        return allUsagesReplaced;
    }

    /**
     * Replace the uses of a constant with {@link ResolveConstantNode}.
     *
     * @param graph
     * @param stateMapper
     * @param node {@link ConstantNode} containing a {@link HotSpotResolvedJavaType} that needs
     *            resolution.
     */
    private static void replaceWithResolution(StructuredGraph graph, FrameStateMapperClosure stateMapper, ConstantNode node, ClassInfo classInfo) {
        HotSpotMetaspaceConstant metaspaceConstant = (HotSpotMetaspaceConstant) node.asConstant();
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) metaspaceConstant.asResolvedJavaType();

        FixedWithNextNode fixedReplacement;
        if (classInfo.builtIns.contains(type)) {
            // Special case of klass constants that come from {@link BoxingSnippets}.
            fixedReplacement = graph.add(new ResolveConstantNode(node, HotSpotConstantLoadAction.INITIALIZE));
        } else {
            fixedReplacement = graph.add(new ResolveConstantNode(node));
        }
        insertReplacement(graph, stateMapper, node, fixedReplacement);
        node.replaceAtUsages(fixedReplacement, n -> !isReplacementNode(n));
    }

    /**
     * Replace the uses of a constant with either {@link LoadConstantIndirectlyNode} if possible.
     *
     * @param graph
     * @param node {@link ConstantNode} containing a {@link HotSpotResolvedJavaType} that needs
     *            resolution.
     * @return return true if all usages of the node have been replaced
     */
    private static boolean replaceWithLoad(StructuredGraph graph, ConstantNode node, ClassInfo classInfo) {
        HotSpotMetaspaceConstant metaspaceConstant = (HotSpotMetaspaceConstant) node.asConstant();
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) metaspaceConstant.asResolvedJavaType();
        ResolvedJavaType topMethodHolder = graph.method().getDeclaringClass();
        ValueNode replacement = null;
        if ((type.isArray() && type.getComponentType().isPrimitive()) || type.equals(classInfo.referenceType)) {
            // Special case for primitive arrays and j.l.ref.Reference.
            // The AOT runtime pre-resolves them, so we may omit the resolution call.
            replacement = graph.addOrUnique(new LoadConstantIndirectlyNode(node));
        } else if (type.equals(topMethodHolder) || (type.isAssignableFrom(topMethodHolder) && !type.isInterface())) {
            // If it's a supertype of or the same class that declares the top method, we are
            // guaranteed to have it resolved already. If it's an interface, we just test for
            // equality.
            replacement = graph.addOrUnique(new LoadConstantIndirectlyNode(node));
        }
        if (replacement != null) {
            node.replaceAtUsages(replacement, n -> !isReplacementNode(n));
            return true;
        }
        return false;
    }

    /**
     * Verify that {@link ConstantNode} containing a {@link HotSpotResolvedJavaType} has a valid
     * fingerprint.
     *
     * @param node {@link ConstantNode} containing a {@link HotSpotResolvedJavaType}.
     */
    private void verifyFingerprint(ConstantNode node) {
        HotSpotMetaspaceConstant metaspaceConstant = (HotSpotMetaspaceConstant) node.asConstant();
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) metaspaceConstant.asResolvedJavaType();
        if (type != null) {
            assert !metaspaceConstant.isCompressed() : "No support for replacing compressed metaspace constants";
            if (verifyFingerprints && checkForBadFingerprint(type)) {
                throw new GraalError("Type with bad fingerprint: " + type);
            }
        }
    }

    /**
     * Replace {@link ConstantNode} containing a {@link HotSpotResolvedJavaType} with indirection.
     *
     * @param graph
     * @param stateMapper
     * @param node {@link ConstantNode} containing a {@link HotSpotResolvedJavaType} that needs
     *            resolution.
     */
    private static void handleHotSpotMetaspaceConstant(StructuredGraph graph, FrameStateMapperClosure stateMapper, ConstantNode node, ClassInfo classInfo) {
        HotSpotMetaspaceConstant metaspaceConstant = (HotSpotMetaspaceConstant) node.asConstant();
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) metaspaceConstant.asResolvedJavaType();
        if (type != null) {
            if (!tryToReplaceWithExisting(graph, node) && !replaceWithLoad(graph, node, classInfo)) {
                replaceWithResolution(graph, stateMapper, node, classInfo);
            }
        } else {
            throw new GraalError("Unsupported metaspace constant type: " + type);
        }
    }

    /**
     * Replace {@link ConstantNode} containing a {@link HotSpotResolvedJavaType} with a load. This
     * variant handles only constants that don't require resolution.
     *
     * @param graph
     * @param node {@link ConstantNode} containing a {@link HotSpotResolvedJavaType} that needs
     *            resolution.
     */
    private static void handleHotSpotMetaspaceConstantWithoutResolution(StructuredGraph graph, ConstantNode node, ClassInfo classInfo) {
        HotSpotMetaspaceConstant metaspaceConstant = (HotSpotMetaspaceConstant) node.asConstant();
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) metaspaceConstant.asResolvedJavaType();
        if (type != null) {
            replaceWithLoad(graph, node, classInfo);
        } else {
            throw new GraalError("Unsupported metaspace constant type: " + type);
        }
    }

    /**
     * Replace an object constant with an indirect load {@link ResolveConstantNode}. Currently we
     * support only strings.
     *
     * @param graph
     * @param stateMapper
     * @param node {@link ConstantNode} containing a {@link HotSpotObjectConstant} that needs
     *            resolution.
     */
    private static void handleHotSpotObjectConstant(StructuredGraph graph, FrameStateMapperClosure stateMapper, ConstantNode node, ClassInfo classInfo) {
        HotSpotObjectConstant constant = (HotSpotObjectConstant) node.asJavaConstant();
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) constant.getType();
        if (type.equals(classInfo.stringType)) {
            assert !constant.isCompressed() : "No support for replacing compressed oop constants";
            FixedWithNextNode replacement = graph.add(new ResolveConstantNode(node));
            insertReplacement(graph, stateMapper, node, replacement);
            node.replaceAtUsages(replacement, n -> !(n instanceof ResolveConstantNode));
        } else {
            throw new GraalError("Unsupported object constant type: " + type);
        }
    }

    /**
     * Replace {@link LoadMethodCountersNode} with indirect load
     * {@link ResolveMethodAndLoadCountersNode}, expose a klass constant of the holder.
     *
     * @param graph
     * @param stateMapper
     * @param node
     * @param context
     */
    private static void handleLoadMethodCounters(StructuredGraph graph, FrameStateMapperClosure stateMapper, LoadMethodCountersNode node, CoreProviders context) {
        ResolvedJavaType type = node.getMethod().getDeclaringClass();
        Stamp hubStamp = context.getStampProvider().createHubStamp((ObjectStamp) StampFactory.objectNonNull());
        ConstantReflectionProvider constantReflection = context.getConstantReflection();
        ConstantNode klassHint = ConstantNode.forConstant(hubStamp, constantReflection.asObjectHub(type), context.getMetaAccess(), graph);
        FixedWithNextNode replacement = graph.add(new ResolveMethodAndLoadCountersNode(node.getMethod(), klassHint));
        insertReplacement(graph, stateMapper, node, replacement);
        node.replaceAtUsages(replacement, n -> !(n instanceof ResolveMethodAndLoadCountersNode));
    }

    /**
     * Replace {@link LoadMethodCountersNode} with {@link ResolveMethodAndLoadCountersNode}, expose
     * klass constants.
     *
     * @param graph
     * @param stateMapper
     * @param context
     */
    private static void replaceLoadMethodCounters(StructuredGraph graph, FrameStateMapperClosure stateMapper, CoreProviders context) {
        new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS, true).apply(graph, false);

        for (LoadMethodCountersNode node : getLoadMethodCountersNodes(graph)) {
            if (anyUsagesNeedReplacement(node)) {
                handleLoadMethodCounters(graph, stateMapper, node, context);
            }
        }
    }

    /**
     * Replace object and klass constants with resolution nodes or reuse preceding initializations.
     *
     * @param graph
     * @param stateMapper
     * @param classInfo
     */
    private void replaceKlassesAndObjects(StructuredGraph graph, FrameStateMapperClosure stateMapper, ClassInfo classInfo) {
        new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS, true).apply(graph, false);

        for (ConstantNode node : getConstantNodes(graph)) {
            Constant constant = node.asConstant();
            if (constant instanceof HotSpotMetaspaceConstant && anyUsagesNeedReplacement(node)) {
                verifyFingerprint(node);
                handleHotSpotMetaspaceConstant(graph, stateMapper, node, classInfo);
            } else if (constant instanceof HotSpotObjectConstant && anyUsagesNeedReplacement(node)) {
                handleHotSpotObjectConstant(graph, stateMapper, node, classInfo);
            }
        }
    }

    /**
     * Replace well-known klass constants with indirect loads.
     *
     * @param graph
     * @param classInfo
     */
    private static void replaceKlassesWithoutResolution(StructuredGraph graph, ClassInfo classInfo) {
        for (ConstantNode node : getConstantNodes(graph)) {
            Constant constant = node.asConstant();
            if (constant instanceof HotSpotMetaspaceConstant && anyUsagesNeedReplacement(node)) {
                handleHotSpotMetaspaceConstantWithoutResolution(graph, node, classInfo);
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (allowResolution) {
            FrameStateMapperClosure stateMapper = new FrameStateMapperClosure(graph);
            ReentrantNodeIterator.apply(stateMapper, graph.start(), null);

            // Replace LoadMethodCountersNode with ResolveMethodAndLoadCountersNode, expose klass
            // constants.
            replaceLoadMethodCounters(graph, stateMapper, context);

            // Replace object and klass constants (including the ones added in the previous pass)
            // with resolution nodes.
            replaceKlassesAndObjects(graph, stateMapper, new ClassInfo(context.getMetaAccess()));
        } else {
            replaceKlassesWithoutResolution(graph, new ClassInfo(context.getMetaAccess()));
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    public ReplaceConstantNodesPhase(boolean allowResolution) {
        this(allowResolution, true);
    }

    public ReplaceConstantNodesPhase(boolean allowResolution, boolean verifyFingerprints) {
        this.allowResolution = allowResolution;
        this.verifyFingerprints = verifyFingerprints;
    }
}
