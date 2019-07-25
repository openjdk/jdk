/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.nodes.spi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeInterface;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

/**
 * Nodes that implement this interface can be collapsed to a single IntegerSwitch when they are seen
 * in a cascade.
 */
@SuppressFBWarnings(value = {"UCF"}, justification = "javac spawns useless control flow in static initializer when using assert(asNode().isAlive())")
public interface SwitchFoldable extends ValueNodeInterface {
    Comparator<KeyData> SORTER = Comparator.comparingInt((KeyData k) -> k.key);

    /**
     * Returns the direct successor in the branch to check for SwitchFoldability.
     */
    Node getNextSwitchFoldableBranch();

    /**
     * Returns the value that will be used as the switch input. This value should be an int.
     */
    ValueNode switchValue();

    /**
     * Returns the branch that will close this switch folding, assuming this is called on the lowest
     * node of the cascade.
     */
    AbstractBeginNode getDefault();

    /**
     * Determines whether the node should be folded in the current folding attempt.
     *
     * @param switchValue the value of the switch that will spawn through this folding attempt.
     * @return true if this node should be folded in the current folding attempt, false otherwise.
     * @see SwitchFoldable#maybeIsInSwitch(LogicNode)
     * @see SwitchFoldable#sameSwitchValue(LogicNode, ValueNode)
     */
    boolean isInSwitch(ValueNode switchValue);

    /**
     * Removes the successors of this node, while keeping it linked to the rest of the cascade.
     */
    void cutOffCascadeNode();

    /**
     * Completely removes all successors from this node.
     */
    void cutOffLowestCascadeNode();

    /**
     * Returns the value of the i-th key of this node.
     */
    int intKeyAt(int i);

    /**
     * Returns the probability of seeing the i-th key of this node.
     */
    double keyProbability(int i);

    /**
     * Returns the branch to follow when seeing the i-th key of this node.
     */
    AbstractBeginNode keySuccessor(int i);

    /**
     * Returns the probability of going to the default branch.
     */
    double defaultProbability();

    /**
     * @return The number of keys the SwitchFoldable node will try to add.
     */
    default int keyCount() {
        return 1;
    }

    /**
     * Should be overridden if getDefault() has side effects.
     */
    default boolean isDefaultSuccessor(AbstractBeginNode successor) {
        return successor == getDefault();
    }

    /**
     * Heuristics that tries to determine whether or not a foldable node was profiled.
     */
    default boolean isNonInitializedProfile() {
        return false;
    }

    static boolean maybeIsInSwitch(LogicNode condition) {
        return condition instanceof IntegerEqualsNode && ((IntegerEqualsNode) condition).getY().isJavaConstant();
    }

    static boolean sameSwitchValue(LogicNode condition, ValueNode switchValue) {
        return ((IntegerEqualsNode) condition).getX() == switchValue;
    }

    // Helper data structures

    class Helper {
        private Helper() {
        }

        private static boolean isDuplicateKey(int key, QuickQueryKeyData keyData) {
            return keyData.contains(key);
        }

        private static int duplicateIndex(AbstractBeginNode begin, QuickQueryList<AbstractBeginNode> successors) {
            return successors.indexOf(begin);
        }

        private static Node skipUpBegins(Node node) {
            Node result = node;
            while (result instanceof BeginNode && result.hasNoUsages()) {
                result = result.predecessor();
            }
            return result;
        }

        private static Node skipDownBegins(Node node) {
            Node result = node;
            while (result instanceof BeginNode && result.hasNoUsages()) {
                result = ((BeginNode) result).next();
            }
            return result;
        }

        private static SwitchFoldable getParentSwitchNode(SwitchFoldable node, ValueNode switchValue) {
            Node result = skipUpBegins(node.asNode().predecessor());
            if (result instanceof SwitchFoldable && ((SwitchFoldable) result).isInSwitch(switchValue)) {
                return (SwitchFoldable) result;
            }
            return null;
        }

        private static SwitchFoldable getChildSwitchNode(SwitchFoldable node, ValueNode switchValue) {
            Node result = skipDownBegins(node.getNextSwitchFoldableBranch());
            if (result instanceof SwitchFoldable && ((SwitchFoldable) result).isInSwitch(switchValue)) {
                return (SwitchFoldable) result;
            }
            return null;
        }

        private static int addDefault(SwitchFoldable node, QuickQueryList<AbstractBeginNode> successors) {
            AbstractBeginNode defaultBranch = node.getDefault();
            int index = successors.indexOf(defaultBranch);
            if (index == -1) {
                index = successors.size();
                successors.add(defaultBranch);
            }
            return index;
        }

        private static int countNonDeoptSuccessors(QuickQueryKeyData keyData) {
            int result = 0;
            for (KeyData key : keyData.list) {
                if (key.keyProbability > 0.0d) {
                    result++;
                }
            }
            return result;
        }

        /**
         * Updates the current state of the IntegerSwitch that will be spawned. That means:
         * <p>
         * - Checking for duplicate keys: add the duplicate key's branch to duplicates
         * <p>
         * - For branches of non-duplicate keys: add them to successors and update the keyData
         * accordingly
         * <p>
         * - Update the value of the cumulative probability, ie, multiply it by the probability of
         * taking the next branch (according to {@link SwitchFoldable#getNextSwitchFoldableBranch})
         * <p>
         * </p>
         *
         * @see QuickQueryList
         * @see QuickQueryKeyData
         */
        private static void updateSwitchData(SwitchFoldable node, QuickQueryKeyData keyData, QuickQueryList<AbstractBeginNode> newSuccessors, double[] cumulative, double[] totalProbabilities,
                        QuickQueryList<AbstractBeginNode> duplicates) {
            for (int i = 0; i < node.keyCount(); i++) {
                int key = node.intKeyAt(i);
                double keyProbability = cumulative[0] * node.keyProbability(i);
                KeyData data;
                AbstractBeginNode keySuccessor = node.keySuccessor(i);
                if (isDuplicateKey(key, keyData)) {
                    // Key was already seen
                    data = keyData.fromKey(key);
                    if (data.keySuccessor != KeyData.KEY_UNKNOWN) {
                        // Unreachable key: kill it manually at the end
                        if (!newSuccessors.contains(keySuccessor) && !duplicates.contains(keySuccessor) && keySuccessor.isAlive()) {
                            // This might be a false alert, if one of the next keys points to it.
                            duplicates.add(keySuccessor);
                        }
                        continue;
                    }
                    /*
                     * A key might not be able to immediately link to its target, if it is shared
                     * with the default target. In that case, we will need to resolve the target at
                     * a later time, either by seeing this key going to a known target in later
                     * cascade nodes, or by linking it to the overall default target at the very end
                     * of the folding.
                     */
                } else {
                    data = new KeyData(key, keyProbability, KeyData.KEY_UNKNOWN);
                    totalProbabilities[0] += keyProbability;
                    keyData.add(data);
                }
                if (keySuccessor.isUnregistered()) {
                    // Shortcut map check if uninitialized node.
                    data.keySuccessor = newSuccessors.size();
                    newSuccessors.addUnique(keySuccessor);
                } else {
                    int pos = duplicateIndex(keySuccessor, newSuccessors);
                    if (pos != -1) {
                        // Target is already known
                        data.keySuccessor = pos;
                    } else if (!node.isDefaultSuccessor(keySuccessor)) {
                        data.keySuccessor = newSuccessors.size();
                        newSuccessors.add(keySuccessor);
                    }
                }
            }
            cumulative[0] *= node.defaultProbability();
        }
    }

    final class KeyData {
        private static final int KEY_UNKNOWN = -2;

        private final int key;
        private final double keyProbability;
        private int keySuccessor;

        KeyData(int key, double keyProbability, int keySuccessor) {
            this.key = key;
            this.keyProbability = keyProbability;
            this.keySuccessor = keySuccessor;
        }
    }

    /**
     * Supports O(1) addition to the list, fast {@code contains} and {@code indexOf} queries
     * (usually O(1), worst case O(n)), and O(1) random access.
     */
    final class QuickQueryList<T> {
        private final List<T> list = new ArrayList<>();
        private final EconomicMap<T, Integer> map = EconomicMap.create(Equivalence.IDENTITY);

        private int indexOf(T begin) {
            return map.get(begin, -1);
        }

        private boolean contains(T o) {
            return map.containsKey(o);
        }

        @SuppressWarnings("unused")
        private T get(int index) {
            return list.get(index);
        }

        private boolean add(T item) {
            map.put(item, list.size());
            return list.add(item);
        }

        /**
         * Adds an object, known to be unique beforehand.
         */
        private void addUnique(T item) {
            list.add(item);
        }

        private int size() {
            return list.size();
        }
    }

    final class QuickQueryKeyData {
        private final List<KeyData> list = new ArrayList<>();
        private final EconomicMap<Integer, KeyData> map = EconomicMap.create();

        private void add(KeyData key) {
            assert !map.containsKey(key.key);
            list.add(key);
            map.put(key.key, key);
        }

        private boolean contains(int key) {
            return map.containsKey(key);
        }

        private KeyData get(int index) {
            return list.get(index);
        }

        private int size() {
            return list.size();
        }

        private KeyData fromKey(int key) {
            assert contains(key);
            return map.get(key);
        }

        private void sort() {
            list.sort(SORTER);
        }

    }

    /**
     * Collapses a cascade of foldables (IfNode, FixedGuard and IntegerSwitch) into a single switch.
     */
    default boolean switchTransformationOptimization(SimplifierTool tool) {
        ValueNode switchValue = switchValue();
        assert asNode().isAlive();
        if (switchValue == null || !isInSwitch(switchValue) || (Helper.getParentSwitchNode(this, switchValue) == null && Helper.getChildSwitchNode(this, switchValue) == null)) {
            // Don't bother trying if there is nothing to do.
            return false;
        }
        Stamp switchStamp = switchValue.stamp(NodeView.DEFAULT);

        // Abort if we do not have an int
        if (!(switchStamp instanceof IntegerStamp)) {
            return false;
        }
        if (PrimitiveStamp.getBits(switchStamp) > 32) {
            return false;
        }

        // PlaceHolder for cascade traversal.
        SwitchFoldable iteratingNode = this;
        SwitchFoldable topMostSwitchNode = this;

        // Find top-most foldable.
        while (iteratingNode != null) {
            topMostSwitchNode = iteratingNode;
            iteratingNode = Helper.getParentSwitchNode(iteratingNode, switchValue);
        }
        QuickQueryKeyData keyData = new QuickQueryKeyData();
        QuickQueryList<AbstractBeginNode> successors = new QuickQueryList<>();
        QuickQueryList<AbstractBeginNode> potentiallyUnreachable = new QuickQueryList<>();
        double[] cumulative = {1.0d};
        double[] totalProbability = {0.0d};

        iteratingNode = topMostSwitchNode;
        SwitchFoldable lowestSwitchNode = topMostSwitchNode;

        // If this stays true, we will need to spawn an uniform distribution.
        boolean uninitializedProfiles = true;

        // Go down the if cascade, collecting necessary data
        while (iteratingNode != null) {
            lowestSwitchNode = iteratingNode;
            Helper.updateSwitchData(iteratingNode, keyData, successors, cumulative, totalProbability, potentiallyUnreachable);
            if (!iteratingNode.isNonInitializedProfile()) {
                uninitializedProfiles = false;
            }
            iteratingNode = Helper.getChildSwitchNode(iteratingNode, switchValue);
        }

        if (keyData.size() < 4 || lowestSwitchNode == topMostSwitchNode) {
            // Abort if it's not worth the hassle
            return false;
        }

        // At that point, we will commit the optimization.
        StructuredGraph graph = asNode().graph();

        // Sort the keys
        keyData.sort();

        /*
         * The total probability might be different than 1 if there was a duplicate key which was
         * erased by another branch whose probability was different (/ex: in the case where a method
         * constituted of only a switch is inlined after a guard for a particular value of that
         * switch). In that case, we need to re-normalize the probabilities. A more "correct" way
         * would be to only re-normalize the probabilities of the switch after the guard, but this
         * cannot be done without an additional overhead.
         */
        totalProbability[0] += cumulative[0];
        assert totalProbability[0] > 0.0d;
        double normalizationFactor = 1 / totalProbability[0];

        // Spawn the required data structures
        int newKeyCount = keyData.list.size();
        int[] keys = new int[newKeyCount];
        double[] keyProbabilities = new double[newKeyCount + 1];
        int[] keySuccessors = new int[newKeyCount + 1];
        int nonDeoptSuccessorCount = Helper.countNonDeoptSuccessors(keyData) + (cumulative[0] > 0.0d ? 1 : 0);
        double uniform = (uninitializedProfiles && nonDeoptSuccessorCount > 0 ? 1 / (double) nonDeoptSuccessorCount : 1.0d);

        // Add default
        keyProbabilities[newKeyCount] = uninitializedProfiles && cumulative[0] > 0.0d ? uniform : normalizationFactor * cumulative[0];
        keySuccessors[newKeyCount] = Helper.addDefault(lowestSwitchNode, successors);

        // Add branches.
        for (int i = 0; i < newKeyCount; i++) {
            SwitchFoldable.KeyData data = keyData.get(i);
            keys[i] = data.key;
            keyProbabilities[i] = uninitializedProfiles && data.keyProbability > 0.0d ? uniform : normalizationFactor * data.keyProbability;
            keySuccessors[i] = data.keySuccessor != KeyData.KEY_UNKNOWN ? data.keySuccessor : keySuccessors[newKeyCount];
        }

        // Spin an adapter if the value is narrower than an int
        ValueNode adapter = null;
        if (((IntegerStamp) switchStamp).getBits() < 32) {
            adapter = graph.addOrUnique(new SignExtendNode(switchValue, 32));
        } else {
            adapter = switchValue;
        }

        // Spawn the switch node
        IntegerSwitchNode toInsert = new IntegerSwitchNode(adapter, successors.size(), keys, keyProbabilities, keySuccessors);
        graph.add(toInsert);

        // Detach the cascade from the graph
        lowestSwitchNode.cutOffLowestCascadeNode();
        iteratingNode = lowestSwitchNode;
        while (iteratingNode != null) {
            if (iteratingNode != lowestSwitchNode) {
                iteratingNode.cutOffCascadeNode();
            }
            iteratingNode = Helper.getParentSwitchNode(iteratingNode, switchValue);
        }

        // Place the new Switch node
        topMostSwitchNode.asNode().replaceAtPredecessor(toInsert);
        topMostSwitchNode.asNode().replaceAtUsages(toInsert);

        // Attach the branches to the switch.
        int pos = 0;
        for (AbstractBeginNode begin : successors.list) {
            if (begin.isUnregistered()) {
                graph.add(begin.next());
                graph.add(begin);
                begin.setNext(begin.next());
            }
            toInsert.setBlockSuccessor(pos++, begin);
        }

        // Remove the cascade and unreachable code
        GraphUtil.killCFG((FixedNode) topMostSwitchNode);
        for (AbstractBeginNode duplicate : potentiallyUnreachable.list) {
            if (duplicate.predecessor() == null) {
                // Make sure the duplicate is not reachable.
                assert duplicate.isAlive();
                GraphUtil.killCFG(duplicate);
            }
        }

        tool.addToWorkList(toInsert);

        return true;
    }
}
