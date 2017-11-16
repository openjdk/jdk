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

import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.virtual.EscapeObjectState;
import org.graalvm.compiler.nodes.virtual.LockState;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.virtual.nodes.MaterializedObjectState;
import org.graalvm.compiler.virtual.nodes.VirtualObjectState;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This class describes the state of a virtual object while iterating over the graph. It describes
 * the fields or array elements (called "entries") and the lock count if the object is still
 * virtual. If the object was materialized, it contains the current materialized value.
 */
public class ObjectState {

    public static final CounterKey CREATE_ESCAPED_OBJECT_STATE = DebugContext.counter("CreateEscapeObjectState");
    public static final CounterKey GET_ESCAPED_OBJECT_STATE = DebugContext.counter("GetEscapeObjectState");

    private ValueNode[] entries;
    private ValueNode materializedValue;
    private LockState locks;
    private boolean ensureVirtualized;

    private EscapeObjectState cachedState;

    /**
     * ObjectStates are duplicated lazily, if this field is true then the state needs to be copied
     * before it is modified.
     */
    boolean copyOnWrite;

    public ObjectState(ValueNode[] entries, List<MonitorIdNode> locks, boolean ensureVirtualized) {
        this(entries, (LockState) null, ensureVirtualized);
        for (int i = locks.size() - 1; i >= 0; i--) {
            this.locks = new LockState(locks.get(i), this.locks);
        }
    }

    public ObjectState(ValueNode[] entries, LockState locks, boolean ensureVirtualized) {
        assert checkIllegalValues(entries);
        this.entries = entries;
        this.locks = locks;
        this.ensureVirtualized = ensureVirtualized;
    }

    public ObjectState(ValueNode materializedValue, LockState locks, boolean ensureVirtualized) {
        assert materializedValue != null;
        this.materializedValue = materializedValue;
        this.locks = locks;
        this.ensureVirtualized = ensureVirtualized;
    }

    private ObjectState(ObjectState other) {
        entries = other.entries == null ? null : other.entries.clone();
        materializedValue = other.materializedValue;
        locks = other.locks;
        cachedState = other.cachedState;
        ensureVirtualized = other.ensureVirtualized;
    }

    public ObjectState cloneState() {
        return new ObjectState(this);
    }

    /**
     * Ensure that if an {@link JavaConstant#forIllegal() illegal value} is seen that the previous
     * value is a double word value.
     */
    public static boolean checkIllegalValues(ValueNode[] values) {
        if (values != null) {
            for (int v = 1; v < values.length; v++) {
                checkIllegalValue(values, v);
            }
        }
        return true;
    }

    /**
     * Ensure that if an {@link JavaConstant#forIllegal() illegal value} is seen that the previous
     * value is a double word value.
     */
    public static boolean checkIllegalValue(ValueNode[] values, int v) {
        if (v > 0 && values[v].isConstant() && values[v].asConstant().equals(JavaConstant.forIllegal())) {
            assert values[v - 1].getStackKind().needsTwoSlots();
        }
        return true;
    }

    public EscapeObjectState createEscapeObjectState(DebugContext debug, VirtualObjectNode virtual) {
        GET_ESCAPED_OBJECT_STATE.increment(debug);
        if (cachedState == null) {
            CREATE_ESCAPED_OBJECT_STATE.increment(debug);
            if (isVirtual()) {
                /*
                 * Clear out entries that are default values anyway.
                 *
                 * TODO: this should be propagated into ObjectState.entries, but that will take some
                 * more refactoring.
                 */
                ValueNode[] newEntries = entries.clone();
                for (int i = 0; i < newEntries.length; i++) {
                    if (newEntries[i].asJavaConstant() == JavaConstant.defaultForKind(virtual.entryKind(i).getStackKind())) {
                        newEntries[i] = null;
                    }
                }
                cachedState = new VirtualObjectState(virtual, newEntries);
            } else {
                cachedState = new MaterializedObjectState(virtual, materializedValue);
            }
        }
        return cachedState;

    }

    public boolean isVirtual() {
        assert materializedValue == null ^ entries == null;
        return materializedValue == null;
    }

    /**
     * Users of this method are not allowed to change the entries of the returned array.
     */
    public ValueNode[] getEntries() {
        assert isVirtual();
        return entries;
    }

    public ValueNode getEntry(int index) {
        assert isVirtual();
        return entries[index];
    }

    public ValueNode getMaterializedValue() {
        assert !isVirtual();
        return materializedValue;
    }

    public void setEntry(int index, ValueNode value) {
        assert isVirtual();
        cachedState = null;
        entries[index] = value;
    }

    public void escape(ValueNode materialized) {
        assert isVirtual();
        assert materialized != null;
        materializedValue = materialized;
        entries = null;
        cachedState = null;
        assert !isVirtual();
    }

    public void updateMaterializedValue(ValueNode value) {
        assert !isVirtual();
        assert value != null;
        cachedState = null;
        materializedValue = value;
    }

    public void addLock(MonitorIdNode monitorId) {
        locks = new LockState(monitorId, locks);
    }

    public MonitorIdNode removeLock() {
        try {
            return locks.monitorId;
        } finally {
            locks = locks.next;
        }
    }

    public LockState getLocks() {
        return locks;
    }

    public boolean hasLocks() {
        return locks != null;
    }

    public boolean locksEqual(ObjectState other) {
        LockState a = locks;
        LockState b = other.locks;
        while (a != null && b != null && a.monitorId == b.monitorId) {
            a = a.next;
            b = b.next;
        }
        return a == null && b == null;
    }

    public void setEnsureVirtualized(boolean ensureVirtualized) {
        this.ensureVirtualized = ensureVirtualized;
    }

    public boolean getEnsureVirtualized() {
        return ensureVirtualized;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder().append('{');
        if (locks != null) {
            str.append('l').append(locks).append(' ');
        }
        if (entries != null) {
            for (int i = 0; i < entries.length; i++) {
                str.append("entry").append(i).append('=').append(entries[i]).append(' ');
            }
        }
        if (materializedValue != null) {
            str.append("mat=").append(materializedValue);
        }

        return str.append('}').toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(entries);
        result = prime * result + (locks != null ? locks.monitorId.getLockDepth() : 0);
        result = prime * result + ((materializedValue == null) ? 0 : materializedValue.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ObjectState other = (ObjectState) obj;
        if (!Arrays.equals(entries, other.entries)) {
            return false;
        }
        if (!locksEqual(other)) {
            return false;
        }
        if (materializedValue == null) {
            if (other.materializedValue != null) {
                return false;
            }
        } else if (!materializedValue.equals(other.materializedValue)) {
            return false;
        }
        return true;
    }

    public ObjectState share() {
        copyOnWrite = true;
        return this;
    }
}
