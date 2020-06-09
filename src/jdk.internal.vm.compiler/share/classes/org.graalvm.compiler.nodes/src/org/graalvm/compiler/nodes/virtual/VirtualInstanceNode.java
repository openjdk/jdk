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


package org.graalvm.compiler.nodes.virtual;

import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(nameTemplate = "VirtualInstance({p#objectId}) {p#type/s}")
public class VirtualInstanceNode extends VirtualObjectNode {

    public static final NodeClass<VirtualInstanceNode> TYPE = NodeClass.create(VirtualInstanceNode.class);
    protected final ResolvedJavaType type;
    protected final ResolvedJavaField[] fields;

    public VirtualInstanceNode(ResolvedJavaType type, boolean hasIdentity) {
        this(type, type.getInstanceFields(true), hasIdentity);
    }

    public VirtualInstanceNode(ResolvedJavaType type, ResolvedJavaField[] fields, boolean hasIdentity) {
        this(TYPE, type, fields, hasIdentity);
    }

    protected VirtualInstanceNode(NodeClass<? extends VirtualInstanceNode> c, ResolvedJavaType type, boolean hasIdentity) {
        this(c, type, type.getInstanceFields(true), hasIdentity);
    }

    protected VirtualInstanceNode(NodeClass<? extends VirtualInstanceNode> c, ResolvedJavaType type, ResolvedJavaField[] fields, boolean hasIdentity) {
        super(c, type, hasIdentity);
        this.type = type;
        this.fields = fields;
    }

    @Override
    public ResolvedJavaType type() {
        return type;
    }

    @Override
    public int entryCount() {
        return fields.length;
    }

    public ResolvedJavaField field(int index) {
        return fields[index];
    }

    public ResolvedJavaField[] getFields() {
        return fields;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + "(" + getObjectId() + ") " + type.toJavaName(false);
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public String entryName(int index) {
        return fields[index].getName();
    }

    public int fieldIndex(ResolvedJavaField field) {
        // on average fields.length == ~6, so a linear search is fast enough
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].equals(field)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int entryIndexForOffset(MetaAccessProvider metaAccess, long constantOffset, JavaKind expectedEntryKind) {
        return fieldIndex(type.findInstanceFieldWithOffset(constantOffset, expectedEntryKind));
    }

    @Override
    public JavaKind entryKind(MetaAccessExtensionProvider metaAccessExtensionProvider, int index) {
        assert index >= 0 && index < fields.length;
        return metaAccessExtensionProvider.getStorageKind(fields[index].getType());
    }

    @Override
    public VirtualInstanceNode duplicate() {
        VirtualInstanceNode node = new VirtualInstanceNode(type, fields, super.hasIdentity());
        node.setNodeSourcePosition(this.getNodeSourcePosition());
        return node;
    }

    @Override
    public ValueNode getMaterializedRepresentation(FixedNode fixed, ValueNode[] entries, LockState locks) {
        AllocatedObjectNode node = new AllocatedObjectNode(this);
        node.setNodeSourcePosition(this.getNodeSourcePosition());
        return node;
    }
}
