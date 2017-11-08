/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * This tool can be used to query the current state (normal/virtualized/re-materialized) of values
 * and to describe the actions that would be taken for this state.
 *
 * See also {@link Virtualizable}.
 */
public interface VirtualizerTool {

    /**
     * @return the {@link MetaAccessProvider} associated with the current compilation.
     */
    MetaAccessProvider getMetaAccessProvider();

    /**
     * @return the {@link ConstantReflectionProvider} associated with the current compilation, which
     *         can be used to access {@link JavaConstant}s.
     */
    ConstantReflectionProvider getConstantReflectionProvider();

    /**
     * This method should be used to query the maximum size of virtualized objects before attempting
     * virtualization.
     *
     * @return the maximum number of entries for virtualized objects.
     */
    int getMaximumEntryCount();

    // methods working on virtualized/materialized objects

    /**
     * Introduces a new virtual object to the current state.
     *
     * @param virtualObject the new virtual object.
     * @param entryState the initial state of the virtual object's fields.
     * @param locks the initial locking depths.
     * @param ensureVirtualized true if this object needs to stay virtual
     */
    void createVirtualObject(VirtualObjectNode virtualObject, ValueNode[] entryState, List<MonitorIdNode> locks, boolean ensureVirtualized);

    /**
     * Returns a VirtualObjectNode if the given value is aliased with a virtual object that is still
     * virtual, the materialized value of the given value is aliased with a virtual object that was
     * materialized, the replacement if the give value was replaced, otherwise the given value.
     *
     * Replacements via {@link #replaceWithValue(ValueNode)} are not immediately committed. This
     * method can be used to determine if a value was replaced by another one (e.g., a load field by
     * the loaded value).
     */
    ValueNode getAlias(ValueNode value);

    /**
     * Sets the entry (field or array element) with the given index in the virtualized object.
     *
     * @param index the index to be set.
     * @param value the new value for the given index.
     * @param accessKind the kind of the store which might be different than
     *            {@link VirtualObjectNode#entryKind(int)}.
     * @return true if the operation was permitted
     */
    boolean setVirtualEntry(VirtualObjectNode virtualObject, int index, ValueNode value, JavaKind accessKind, long offset);

    default void setVirtualEntry(VirtualObjectNode virtualObject, int index, ValueNode value) {
        if (!setVirtualEntry(virtualObject, index, value, null, 0)) {
            throw new GraalError("unexpected failure when updating virtual entry");
        }
    }

    ValueNode getEntry(VirtualObjectNode virtualObject, int index);

    void addLock(VirtualObjectNode virtualObject, MonitorIdNode monitorId);

    MonitorIdNode removeLock(VirtualObjectNode virtualObject);

    void setEnsureVirtualized(VirtualObjectNode virtualObject, boolean ensureVirtualized);

    boolean getEnsureVirtualized(VirtualObjectNode virtualObject);

    // operations on the current node

    /**
     * Deletes the current node and replaces it with the given virtualized object.
     *
     * @param virtualObject the virtualized object that should replace the current node.
     */
    void replaceWithVirtual(VirtualObjectNode virtualObject);

    /**
     * Deletes the current node and replaces it with the given value.
     *
     * @param replacement the value that should replace the current node.
     */
    void replaceWithValue(ValueNode replacement);

    /**
     * Deletes the current node.
     */
    void delete();

    /**
     * Replaces an input of the current node.
     *
     * @param oldInput the old input value.
     * @param replacement the new input value.
     */
    void replaceFirstInput(Node oldInput, Node replacement);

    /**
     * Adds the given node to the graph.This action will only be performed when, and if, the changes
     * are committed.
     *
     * @param node the node to add.
     */
    void addNode(ValueNode node);

    /**
     * This method performs either {@link #replaceWithValue(ValueNode)} or
     * {@link #replaceWithVirtual(VirtualObjectNode)}, depending on the given value.
     *
     * @param value the replacement value
     */
    void replaceWith(ValueNode value);

    /**
     *
     * If state is virtual, materialization is performed for the given state.
     *
     * @return true if materialization happened, false if not.
     */
    boolean ensureMaterialized(VirtualObjectNode virtualObject);

    OptionValues getOptions();

    DebugContext getDebug();
}
