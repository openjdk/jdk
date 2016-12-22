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

import java.nio.ByteOrder;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

@NodeInfo(nameTemplate = "VirtualArray {p#componentType/s}[{p#length}]")
public class VirtualArrayNode extends VirtualObjectNode implements ArrayLengthProvider {

    public static final NodeClass<VirtualArrayNode> TYPE = NodeClass.create(VirtualArrayNode.class);
    protected final ResolvedJavaType componentType;
    protected final int length;

    public VirtualArrayNode(ResolvedJavaType componentType, int length) {
        this(TYPE, componentType, length);
    }

    protected VirtualArrayNode(NodeClass<? extends VirtualObjectNode> c, ResolvedJavaType componentType, int length) {
        super(c, componentType.getArrayClass(), true);
        this.componentType = componentType;
        this.length = length;
    }

    @Override
    public ResolvedJavaType type() {
        return componentType.getArrayClass();
    }

    public ResolvedJavaType componentType() {
        return componentType;
    }

    @Override
    public int entryCount() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // nothing to do...
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + " " + componentType.getName() + "[" + length + "]";
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public String entryName(int index) {
        return "[" + index + "]";
    }

    @Override
    public int entryIndexForOffset(long constantOffset, JavaKind expectedEntryKind) {
        return entryIndexForOffset(constantOffset, expectedEntryKind, componentType, length);
    }

    public static int entryIndexForOffset(long constantOffset, JavaKind expectedEntryKind, ResolvedJavaType componentType, int length) {
        int baseOffset;
        int indexScale;
        switch (componentType.getJavaKind()) {
            case Boolean:
                baseOffset = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_BOOLEAN_INDEX_SCALE;
                break;
            case Byte:
                baseOffset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_BYTE_INDEX_SCALE;
                break;
            case Short:
                baseOffset = Unsafe.ARRAY_SHORT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_SHORT_INDEX_SCALE;
                break;
            case Char:
                baseOffset = Unsafe.ARRAY_CHAR_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_CHAR_INDEX_SCALE;
                break;
            case Int:
                baseOffset = Unsafe.ARRAY_INT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_INT_INDEX_SCALE;
                break;
            case Long:
                baseOffset = Unsafe.ARRAY_LONG_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_LONG_INDEX_SCALE;
                break;
            case Float:
                baseOffset = Unsafe.ARRAY_FLOAT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_FLOAT_INDEX_SCALE;
                break;
            case Double:
                baseOffset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
                break;
            case Object:
                baseOffset = Unsafe.ARRAY_OBJECT_BASE_OFFSET;
                indexScale = Unsafe.ARRAY_OBJECT_INDEX_SCALE;
                break;
            default:
                return -1;
        }
        long offset;
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN && componentType.isPrimitive()) {
            // On big endian, we do just get expect the type be right aligned in this memory slot
            offset = constantOffset - (componentType.getJavaKind().getByteCount() - Math.min(componentType.getJavaKind().getByteCount(), 4 + expectedEntryKind.getByteCount()));
        } else {
            offset = constantOffset;
        }
        long index = offset - baseOffset;
        if (index % indexScale != 0) {
            return -1;
        }
        long elementIndex = index / indexScale;
        if (elementIndex < 0 || elementIndex >= length) {
            return -1;
        }
        return (int) elementIndex;
    }

    @Override
    public JavaKind entryKind(int index) {
        assert index >= 0 && index < length;
        return componentType.getJavaKind();
    }

    @Override
    public VirtualArrayNode duplicate() {
        return new VirtualArrayNode(componentType, length);
    }

    @Override
    public ValueNode getMaterializedRepresentation(FixedNode fixed, ValueNode[] entries, LockState locks) {
        return new AllocatedObjectNode(this);
    }

    @Override
    public ValueNode length() {
        return ConstantNode.forInt(length);
    }
}
