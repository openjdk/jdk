/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code IntegerSwitchNode} represents a switch on integer keys, with a sorted array of key
 * values. The actual implementation of the switch will be decided by the backend.
 */
@NodeInfo
public final class IntegerSwitchNode extends SwitchNode implements LIRLowerable, Simplifiable {
    public static final NodeClass<IntegerSwitchNode> TYPE = NodeClass.create(IntegerSwitchNode.class);

    protected final int[] keys;

    public IntegerSwitchNode(ValueNode value, AbstractBeginNode[] successors, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
        super(TYPE, value, successors, keySuccessors, keyProbabilities);
        assert keySuccessors.length == keys.length + 1;
        assert keySuccessors.length == keyProbabilities.length;
        this.keys = keys;
        assert value.stamp() instanceof PrimitiveStamp && value.stamp().getStackKind().isNumericInteger();
        assert assertSorted();
    }

    private boolean assertSorted() {
        for (int i = 1; i < keys.length; i++) {
            assert keys[i - 1] < keys[i];
        }
        return true;
    }

    public IntegerSwitchNode(ValueNode value, int successorCount, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
        this(value, new AbstractBeginNode[successorCount], keys, keyProbabilities, keySuccessors);
    }

    @Override
    public boolean isSorted() {
        return true;
    }

    /**
     * Gets the key at the specified index.
     *
     * @param i the index
     * @return the key at that index
     */
    @Override
    public JavaConstant keyAt(int i) {
        return JavaConstant.forInt(keys[i]);
    }

    @Override
    public int keyCount() {
        return keys.length;
    }

    @Override
    public boolean equalKeys(SwitchNode switchNode) {
        if (!(switchNode instanceof IntegerSwitchNode)) {
            return false;
        }
        IntegerSwitchNode other = (IntegerSwitchNode) switchNode;
        return Arrays.equals(keys, other.keys);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.emitSwitch(this);
    }

    public AbstractBeginNode successorAtKey(int key) {
        return blockSuccessor(successorIndexAtKey(key));
    }

    public int successorIndexAtKey(int key) {
        for (int i = 0; i < keyCount(); i++) {
            if (keys[i] == key) {
                return keySuccessorIndex(i);
            }
        }
        return keySuccessorIndex(keyCount());
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (blockSuccessorCount() == 1) {
            tool.addToWorkList(defaultSuccessor());
            graph().removeSplitPropagate(this, defaultSuccessor());
        } else if (value() instanceof ConstantNode) {
            killOtherSuccessors(tool, successorIndexAtKey(value().asJavaConstant().asInt()));
        } else if (tryOptimizeEnumSwitch(tool)) {
            return;
        } else if (tryRemoveUnreachableKeys(tool)) {
            return;
        }
    }

    static final class KeyData {
        final int key;
        final double keyProbability;
        final int keySuccessor;

        KeyData(int key, double keyProbability, int keySuccessor) {
            this.key = key;
            this.keyProbability = keyProbability;
            this.keySuccessor = keySuccessor;
        }
    }

    /**
     * Remove unreachable keys from the switch based on the stamp of the value, i.e., based on the
     * known range of the switch value.
     */
    private boolean tryRemoveUnreachableKeys(SimplifierTool tool) {
        if (!(value().stamp() instanceof IntegerStamp)) {
            return false;
        }
        IntegerStamp integerStamp = (IntegerStamp) value().stamp();
        if (integerStamp.isUnrestricted()) {
            return false;
        }

        List<KeyData> newKeyDatas = new ArrayList<>(keys.length);
        ArrayList<AbstractBeginNode> newSuccessors = new ArrayList<>(blockSuccessorCount());
        for (int i = 0; i < keys.length; i++) {
            if (integerStamp.contains(keys[i])) {
                newKeyDatas.add(new KeyData(keys[i], keyProbabilities[i], addNewSuccessor(keySuccessor(i), newSuccessors)));
            }
        }

        if (newKeyDatas.size() == keys.length) {
            /* All keys are reachable. */
            return false;

        } else if (newKeyDatas.size() == 0) {
            tool.addToWorkList(defaultSuccessor());
            graph().removeSplitPropagate(this, defaultSuccessor());
            return true;

        } else {
            int newDefaultSuccessor = addNewSuccessor(defaultSuccessor(), newSuccessors);
            double newDefaultProbability = keyProbabilities[keyProbabilities.length - 1];
            doReplace(tool, value(), newKeyDatas, newSuccessors, newDefaultSuccessor, newDefaultProbability);
            return true;
        }
    }

    /**
     * For switch statements on enum values, the Java compiler has to generate complicated code:
     * because {@link Enum#ordinal()} can change when recompiling an enum, it cannot be used
     * directly as the value that is switched on. An intermediate int[] array, which is initialized
     * once at run time based on the actual {@link Enum#ordinal()} values, is used.
     *
     * The {@link ConstantFieldProvider} of Graal already detects the int[] arrays and marks them as
     * {@link ConstantNode#isDefaultStable() stable}, i.e., the array elements are constant. The
     * code in this method detects array loads from such a stable array and re-wires the switch to
     * use the keys from the array elements, so that the array load is unnecessary.
     */
    private boolean tryOptimizeEnumSwitch(SimplifierTool tool) {
        if (!(value() instanceof LoadIndexedNode)) {
            /* Not the switch pattern we are looking for. */
            return false;
        }
        LoadIndexedNode loadIndexed = (LoadIndexedNode) value();
        if (loadIndexed.usages().count() > 1) {
            /*
             * The array load is necessary for other reasons too, so there is no benefit optimizing
             * the switch.
             */
            return false;
        }
        assert loadIndexed.usages().first() == this;

        ValueNode newValue = loadIndexed.index();
        JavaConstant arrayConstant = loadIndexed.array().asJavaConstant();
        if (arrayConstant == null || ((ConstantNode) loadIndexed.array()).getStableDimension() != 1 || !((ConstantNode) loadIndexed.array()).isDefaultStable()) {
            /*
             * The array is a constant that we can optimize. We require the array elements to be
             * constant too, since we put them as literal constants into the switch keys.
             */
            return false;
        }

        Integer optionalArrayLength = tool.getConstantReflection().readArrayLength(arrayConstant);
        if (optionalArrayLength == null) {
            /* Loading a constant value can be denied by the VM. */
            return false;
        }
        int arrayLength = optionalArrayLength;

        Map<Integer, List<Integer>> reverseArrayMapping = new HashMap<>();
        for (int i = 0; i < arrayLength; i++) {
            JavaConstant elementConstant = tool.getConstantReflection().readArrayElement(arrayConstant, i);
            if (elementConstant == null || elementConstant.getJavaKind() != JavaKind.Int) {
                /* Loading a constant value can be denied by the VM. */
                return false;
            }
            int element = elementConstant.asInt();

            /*
             * The value loaded from the array is the old switch key, the index into the array is
             * the new switch key. We build a mapping from the old switch key to new keys.
             */
            reverseArrayMapping.computeIfAbsent(element, e -> new ArrayList<>()).add(i);
        }

        /* Build high-level representation of new switch keys. */
        List<KeyData> newKeyDatas = new ArrayList<>(arrayLength);
        ArrayList<AbstractBeginNode> newSuccessors = new ArrayList<>(blockSuccessorCount());
        for (int i = 0; i < keys.length; i++) {
            List<Integer> newKeys = reverseArrayMapping.get(keys[i]);
            if (newKeys == null || newKeys.size() == 0) {
                /* The switch case is unreachable, we can ignore it. */
                continue;
            }

            /*
             * We do not have detailed profiling information about the individual new keys, so we
             * have to assume they split the probability of the old key.
             */
            double newKeyProbability = keyProbabilities[i] / newKeys.size();
            int newKeySuccessor = addNewSuccessor(keySuccessor(i), newSuccessors);

            for (int newKey : newKeys) {
                newKeyDatas.add(new KeyData(newKey, newKeyProbability, newKeySuccessor));
            }
        }

        int newDefaultSuccessor = addNewSuccessor(defaultSuccessor(), newSuccessors);
        double newDefaultProbability = keyProbabilities[keyProbabilities.length - 1];

        /*
         * We remove the array load, but we still need to preserve exception semantics by keeping
         * the bounds check. Fortunately the array length is a constant.
         */
        LogicNode boundsCheck = graph().unique(new IntegerBelowNode(newValue, ConstantNode.forInt(arrayLength, graph())));
        graph().addBeforeFixed(this, graph().add(new FixedGuardNode(boundsCheck, DeoptimizationReason.BoundsCheckException, DeoptimizationAction.InvalidateReprofile)));

        /*
         * Build the low-level representation of the new switch keys and replace ourself with a new
         * node.
         */
        doReplace(tool, newValue, newKeyDatas, newSuccessors, newDefaultSuccessor, newDefaultProbability);

        /* The array load is now unnecessary. */
        assert loadIndexed.hasNoUsages();
        GraphUtil.removeFixedWithUnusedInputs(loadIndexed);

        return true;
    }

    private static int addNewSuccessor(AbstractBeginNode newSuccessor, ArrayList<AbstractBeginNode> newSuccessors) {
        int index = newSuccessors.indexOf(newSuccessor);
        if (index == -1) {
            index = newSuccessors.size();
            newSuccessors.add(newSuccessor);
        }
        return index;
    }

    private void doReplace(SimplifierTool tool, ValueNode newValue, List<KeyData> newKeyDatas, ArrayList<AbstractBeginNode> newSuccessors, int newDefaultSuccessor, double newDefaultProbability) {
        /* Sort the new keys (invariant of the IntegerSwitchNode). */
        newKeyDatas.sort((k1, k2) -> k1.key - k2.key);

        /* Create the final data arrays. */
        int newKeyCount = newKeyDatas.size();
        int[] newKeys = new int[newKeyCount];
        double[] newKeyProbabilities = new double[newKeyCount + 1];
        int[] newKeySuccessors = new int[newKeyCount + 1];

        for (int i = 0; i < newKeyCount; i++) {
            KeyData keyData = newKeyDatas.get(i);
            newKeys[i] = keyData.key;
            newKeyProbabilities[i] = keyData.keyProbability;
            newKeySuccessors[i] = keyData.keySuccessor;
        }

        newKeySuccessors[newKeyCount] = newDefaultSuccessor;
        newKeyProbabilities[newKeyCount] = newDefaultProbability;

        /* Normalize new probabilities so that they sum up to 1. */
        double totalProbability = 0;
        for (double probability : newKeyProbabilities) {
            totalProbability += probability;
        }
        if (totalProbability > 0) {
            for (int i = 0; i < newKeyProbabilities.length; i++) {
                newKeyProbabilities[i] /= totalProbability;
            }
        } else {
            for (int i = 0; i < newKeyProbabilities.length; i++) {
                newKeyProbabilities[i] = 1.0 / newKeyProbabilities.length;
            }
        }

        /* Remove dead successors. */
        for (int i = 0; i < blockSuccessorCount(); i++) {
            AbstractBeginNode successor = blockSuccessor(i);
            if (!newSuccessors.contains(successor)) {
                tool.deleteBranch(successor);
            }
            setBlockSuccessor(i, null);
        }

        /* Create the new switch node and replace ourself with it. */
        AbstractBeginNode[] successorsArray = newSuccessors.toArray(new AbstractBeginNode[newSuccessors.size()]);
        SwitchNode newSwitch = graph().add(new IntegerSwitchNode(newValue, successorsArray, newKeys, newKeyProbabilities, newKeySuccessors));
        ((FixedWithNextNode) predecessor()).setNext(newSwitch);
        GraphUtil.killWithUnusedFloatingInputs(this);
    }
}
