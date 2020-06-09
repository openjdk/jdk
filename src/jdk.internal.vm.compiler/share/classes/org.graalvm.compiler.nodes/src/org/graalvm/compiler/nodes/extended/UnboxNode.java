/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_2, size = SIZE_2, allowedUsageTypes = {InputType.Memory, InputType.Value})
public final class UnboxNode extends AbstractBoxingNode implements Virtualizable, Lowerable, Canonicalizable.Unary<ValueNode> {

    public static final NodeClass<UnboxNode> TYPE = NodeClass.create(UnboxNode.class);

    public UnboxNode(ValueNode value, JavaKind boxingKind, FieldLocationIdentity location) {
        super(TYPE, value, boxingKind, StampFactory.forKind(boxingKind.getStackKind()), location);
    }

    public UnboxNode(ValueNode value, JavaKind boxingKind, MetaAccessProvider metaAccess) {
        super(TYPE, value, boxingKind, StampFactory.forKind(boxingKind.getStackKind()), new FieldLocationIdentity(getValueField(getResultType(metaAccess, boxingKind))));
    }

    private static ResolvedJavaType getResultType(MetaAccessProvider metaAccess, JavaKind boxingKind) {
        return metaAccess.lookupJavaType(boxingKind.toBoxedJavaClass());
    }

    public static ValueNode create(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ValueNode value, JavaKind boxingKind) {
        ValueNode synonym = findSynonym(metaAccess, constantReflection, value, boxingKind);
        if (synonym != null) {
            return synonym;
        }
        return new UnboxNode(value, boxingKind, metaAccess);
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            ResolvedJavaType objectType = virtual.type();
            ResolvedJavaType expectedType = tool.getMetaAccess().lookupJavaType(boxingKind.toBoxedJavaClass());
            if (objectType.equals(expectedType)) {
                tool.replaceWithValue(tool.getEntry(virtual, 0));
            }
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (tool.allUsagesAvailable() && hasNoUsages() && StampTool.isPointerNonNull(forValue)) {
            return null;
        }
        ValueNode synonym = findSynonym(tool.getMetaAccess(), tool.getConstantReflection(), forValue, boxingKind);
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    private static ValueNode findSynonym(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ValueNode forValue, JavaKind boxingKind) {
        if (forValue.isConstant()) {
            JavaConstant constant = forValue.asJavaConstant();
            JavaConstant unboxed = constantReflection.unboxPrimitive(constant);
            if (unboxed != null && unboxed.getJavaKind() == boxingKind) {
                return ConstantNode.forConstant(unboxed, metaAccess);
            }
        } else if (forValue instanceof BoxNode) {
            BoxNode box = (BoxNode) forValue;
            if (boxingKind == box.getBoxingKind()) {
                return box.getValue();
            }
        }
        return null;
    }

}
