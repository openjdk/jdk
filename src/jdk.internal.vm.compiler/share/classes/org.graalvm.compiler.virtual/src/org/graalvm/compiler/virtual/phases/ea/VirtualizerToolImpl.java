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

import static org.graalvm.compiler.core.common.GraalOptions.MaximumEscapeAnalysisArrayLength;

import java.util.List;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.UnpackEndianHalfNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Forwards calls from {@link VirtualizerTool} to the actual {@link PartialEscapeBlockState}.
 */
class VirtualizerToolImpl implements VirtualizerTool, CanonicalizerTool {

    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final ConstantFieldProvider constantFieldProvider;
    private final PartialEscapeClosure<?> closure;
    private final Assumptions assumptions;
    private final OptionValues options;
    private final DebugContext debug;
    private final LoweringProvider loweringProvider;
    private ConstantNode illegalConstant;

    VirtualizerToolImpl(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, PartialEscapeClosure<?> closure,
                    Assumptions assumptions, OptionValues options, DebugContext debug, LoweringProvider loweringProvider) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.constantFieldProvider = constantFieldProvider;
        this.closure = closure;
        this.assumptions = assumptions;
        this.options = options;
        this.debug = debug;
        this.loweringProvider = loweringProvider;
    }

    private boolean deleted;
    private PartialEscapeBlockState<?> state;
    private ValueNode current;
    private FixedNode position;
    private GraphEffectList effects;

    @Override
    public OptionValues getOptions() {
        return options;
    }

    @Override
    public DebugContext getDebug() {
        return debug;
    }

    @Override
    public MetaAccessProvider getMetaAccessProvider() {
        return metaAccess;
    }

    @Override
    public ConstantReflectionProvider getConstantReflectionProvider() {
        return constantReflection;
    }

    @Override
    public ConstantFieldProvider getConstantFieldProvider() {
        return constantFieldProvider;
    }

    public void reset(PartialEscapeBlockState<?> newState, ValueNode newCurrent, FixedNode newPosition, GraphEffectList newEffects) {
        deleted = false;
        state = newState;
        current = newCurrent;
        position = newPosition;
        effects = newEffects;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public ValueNode getAlias(ValueNode value) {
        return closure.getAliasAndResolve(state, value);
    }

    @Override
    public ValueNode getEntry(VirtualObjectNode virtualObject, int index) {
        return state.getObjectState(virtualObject).getEntry(index);
    }

    @Override
    public boolean setVirtualEntry(VirtualObjectNode virtual, int index, ValueNode value, JavaKind theAccessKind, long offset) {
        ObjectState obj = state.getObjectState(virtual);
        assert obj.isVirtual() : "not virtual: " + obj;
        ValueNode newValue;
        JavaKind entryKind = virtual.entryKind(index);
        JavaKind accessKind = theAccessKind != null ? theAccessKind : entryKind;
        if (value == null) {
            newValue = null;
        } else {
            newValue = closure.getAliasAndResolve(state, value);
        }
        getDebug().log(DebugContext.DETAILED_LEVEL, "Setting entry %d in virtual object %s %s results in %s", index, virtual.getObjectId(), virtual, state.getObjectState(virtual.getObjectId()));
        ValueNode oldValue = getEntry(virtual, index);
        boolean canVirtualize = entryKind == accessKind || (entryKind == accessKind.getStackKind() && virtual instanceof VirtualInstanceNode);
        if (!canVirtualize) {
            if (entryKind == JavaKind.Long && oldValue.getStackKind() == newValue.getStackKind() && oldValue.getStackKind().isPrimitive()) {
                /*
                 * Special case: If the entryKind is long, allow arbitrary kinds as long as a value
                 * of the same kind is already there. This can only happen if some other node
                 * initialized the entry with a value of a different kind. One example where this
                 * happens is the Truffle NewFrameNode.
                 */
                getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s with primitive of kind %s in long entry ", current, oldValue.getStackKind());
                canVirtualize = true;
            } else if (entryKind == JavaKind.Int && (accessKind == JavaKind.Long || accessKind == JavaKind.Double) && offset % 8 == 0) {
                /*
                 * Special case: Allow storing a single long or double value into two consecutive
                 * int slots.
                 */
                int nextIndex = virtual.entryIndexForOffset(offset + 4, JavaKind.Int);
                if (nextIndex != -1) {
                    canVirtualize = true;
                    assert nextIndex == index + 1 : "expected to be sequential";
                    getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s for double word stored in two ints", current);
                }
            }
        }

        if (canVirtualize) {
            getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s for entryKind %s and access kind %s", current, entryKind, accessKind);
            state.setEntry(virtual.getObjectId(), index, newValue);
            if (entryKind == JavaKind.Int) {
                if (accessKind.needsTwoSlots()) {
                    // Storing double word value two int slots
                    assert virtual.entryKind(index + 1) == JavaKind.Int;
                    state.setEntry(virtual.getObjectId(), index + 1, getIllegalConstant());
                } else if (oldValue.getStackKind() == JavaKind.Double || oldValue.getStackKind() == JavaKind.Long) {
                    // Splitting double word constant by storing over it with an int
                    getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s producing second half of double word value %s", current, oldValue);
                    ValueNode secondHalf = UnpackEndianHalfNode.create(oldValue, false, NodeView.DEFAULT);
                    addNode(secondHalf);
                    state.setEntry(virtual.getObjectId(), index + 1, secondHalf);
                }
            }
            if (oldValue.isConstant() && oldValue.asConstant().equals(JavaConstant.forIllegal())) {
                // Storing into second half of double, so replace previous value
                ValueNode previous = getEntry(virtual, index - 1);
                getDebug().log(DebugContext.DETAILED_LEVEL, "virtualizing %s producing first half of double word value %s", current, previous);
                ValueNode firstHalf = UnpackEndianHalfNode.create(previous, true, NodeView.DEFAULT);
                addNode(firstHalf);
                state.setEntry(virtual.getObjectId(), index - 1, firstHalf);
            }
            return true;
        }
        // Should only occur if there are mismatches between the entry and access kind
        assert entryKind != accessKind;
        return false;
    }

    private ValueNode getIllegalConstant() {
        if (illegalConstant == null) {
            illegalConstant = ConstantNode.forConstant(JavaConstant.forIllegal(), getMetaAccessProvider());
            addNode(illegalConstant);
        }
        return illegalConstant;
    }

    @Override
    public void setEnsureVirtualized(VirtualObjectNode virtualObject, boolean ensureVirtualized) {
        int id = virtualObject.getObjectId();
        state.setEnsureVirtualized(id, ensureVirtualized);
    }

    @Override
    public boolean getEnsureVirtualized(VirtualObjectNode virtualObject) {
        return state.getObjectState(virtualObject).getEnsureVirtualized();
    }

    @Override
    public void replaceWithVirtual(VirtualObjectNode virtual) {
        closure.addVirtualAlias(virtual, current);
        effects.deleteNode(current);
        deleted = true;
    }

    @Override
    public void replaceWithValue(ValueNode replacement) {
        effects.replaceAtUsages(current, closure.getScalarAlias(replacement), position);
        closure.addScalarAlias(current, replacement);
        deleted = true;
    }

    @Override
    public void delete() {
        effects.deleteNode(current);
        deleted = true;
    }

    @Override
    public void replaceFirstInput(Node oldInput, Node replacement) {
        effects.replaceFirstInput(current, oldInput, replacement);
    }

    @Override
    public void addNode(ValueNode node) {
        if (node instanceof FloatingNode) {
            effects.addFloatingNode(node, "VirtualizerTool");
        } else {
            effects.addFixedNodeBefore((FixedWithNextNode) node, position);
        }
    }

    @Override
    public void createVirtualObject(VirtualObjectNode virtualObject, ValueNode[] entryState, List<MonitorIdNode> locks, boolean ensureVirtualized) {
        VirtualUtil.trace(options, debug, "{{%s}} ", current);
        if (!virtualObject.isAlive()) {
            effects.addFloatingNode(virtualObject, "newVirtualObject");
        }
        for (int i = 0; i < entryState.length; i++) {
            ValueNode entry = entryState[i];
            entryState[i] = entry instanceof VirtualObjectNode ? entry : closure.getAliasAndResolve(state, entry);
        }
        int id = virtualObject.getObjectId();
        if (id == -1) {
            id = closure.virtualObjects.size();
            closure.virtualObjects.add(virtualObject);
            virtualObject.setObjectId(id);
        }
        state.addObject(id, new ObjectState(entryState, locks, ensureVirtualized));
        closure.addVirtualAlias(virtualObject, virtualObject);
        PartialEscapeClosure.COUNTER_ALLOCATION_REMOVED.increment(debug);
        effects.addVirtualizationDelta(1);
    }

    @Override
    public int getMaximumEntryCount() {
        return MaximumEscapeAnalysisArrayLength.getValue(current.getOptions());
    }

    @Override
    public void replaceWith(ValueNode node) {
        if (node instanceof VirtualObjectNode) {
            replaceWithVirtual((VirtualObjectNode) node);
        } else {
            replaceWithValue(node);
        }
    }

    @Override
    public boolean ensureMaterialized(VirtualObjectNode virtualObject) {
        return closure.ensureMaterialized(state, virtualObject.getObjectId(), position, effects, PartialEscapeClosure.COUNTER_MATERIALIZATIONS_UNHANDLED);
    }

    @Override
    public void addLock(VirtualObjectNode virtualObject, MonitorIdNode monitorId) {
        int id = virtualObject.getObjectId();
        state.addLock(id, monitorId);
    }

    @Override
    public MonitorIdNode removeLock(VirtualObjectNode virtualObject) {
        int id = virtualObject.getObjectId();
        return state.removeLock(id);
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    @Override
    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    @Override
    public boolean canonicalizeReads() {
        return false;
    }

    @Override
    public boolean allUsagesAvailable() {
        return true;
    }

    @Override
    public Assumptions getAssumptions() {
        return assumptions;
    }

    @Override
    public Integer smallestCompareWidth() {
        if (loweringProvider != null) {
            return loweringProvider.smallestCompareWidth();
        } else {
            return null;
        }
    }
}
