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
package org.graalvm.compiler.nodes.virtual;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public abstract class VirtualObjectNode extends ValueNode implements LIRLowerable, IterableNodeType {

    public static final NodeClass<VirtualObjectNode> TYPE = NodeClass.create(VirtualObjectNode.class);
    protected boolean hasIdentity;
    private int objectId = -1;

    protected VirtualObjectNode(NodeClass<? extends VirtualObjectNode> c, ResolvedJavaType type, boolean hasIdentity) {
        super(c, StampFactory.objectNonNull(TypeReference.createExactTrusted(type)));
        this.hasIdentity = hasIdentity;
    }

    public final int getObjectId() {
        return objectId;
    }

    public final void resetObjectId() {
        this.objectId = -1;
    }

    public final void setObjectId(int objectId) {
        assert objectId != -1;
        this.objectId = objectId;
    }

    @Override
    protected void afterClone(Node other) {
        super.afterClone(other);
        resetObjectId();
    }

    /**
     * The type of object described by this {@link VirtualObjectNode}. In case of arrays, this is
     * the array type (and not the component type).
     */
    public abstract ResolvedJavaType type();

    /**
     * The number of entries this virtual object has. Either the number of fields or the number of
     * array elements.
     */
    public abstract int entryCount();

    /**
     * Returns the name of the entry at the given index. Only used for debugging purposes.
     */
    public abstract String entryName(int i);

    /**
     * If the given index denotes an entry in this virtual object, the index of this entry is
     * returned. If no such entry can be found, this method returns -1.
     *
     * @param constantOffset offset, where the value is placed.
     * @param expectedEntryKind Specifies which type is expected at this offset (Is important when
     *            doing implicit casts, especially on big endian systems.
     */
    public abstract int entryIndexForOffset(long constantOffset, JavaKind expectedEntryKind);

    /**
     * Returns the {@link JavaKind} of the entry at the given index.
     */
    public abstract JavaKind entryKind(int index);

    /**
     * Returns an exact duplicate of this virtual object node, which has not been added to the graph
     * yet.
     */
    public abstract VirtualObjectNode duplicate();

    /**
     * Specifies whether this virtual object has an object identity. If not, then the result of a
     * comparison of two virtual objects is determined by comparing their contents.
     */
    public boolean hasIdentity() {
        return hasIdentity;
    }

    public void setIdentity(boolean identity) {
        this.hasIdentity = identity;
    }

    /**
     * Returns a node that can be used to materialize this virtual object. If this returns an
     * {@link AllocatedObjectNode} then this node will be attached to a {@link CommitAllocationNode}
     * , otherwise the node will just be added to the graph.
     */
    public abstract ValueNode getMaterializedRepresentation(FixedNode fixed, ValueNode[] entries, LockState locks);

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // nothing to do...
    }
}
