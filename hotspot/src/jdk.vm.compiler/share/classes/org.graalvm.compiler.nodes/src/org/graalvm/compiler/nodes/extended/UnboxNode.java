/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_100;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_50;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_100, size = SIZE_50)
public final class UnboxNode extends FixedWithNextNode implements Virtualizable, Lowerable, Canonicalizable.Unary<ValueNode> {

    public static final NodeClass<UnboxNode> TYPE = NodeClass.create(UnboxNode.class);
    @Input protected ValueNode value;
    protected final JavaKind boxingKind;

    @Override
    public ValueNode getValue() {
        return value;
    }

    public UnboxNode(ValueNode value, JavaKind boxingKind) {
        super(TYPE, StampFactory.forKind(boxingKind.getStackKind()));
        this.value = value;
        this.boxingKind = boxingKind;
    }

    public static ValueNode create(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ValueNode value, JavaKind boxingKind) {
        ValueNode synonym = findSynonym(metaAccess, constantReflection, value, boxingKind);
        if (synonym != null) {
            return synonym;
        }
        return new UnboxNode(value, boxingKind);
    }

    public JavaKind getBoxingKind() {
        return boxingKind;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            ResolvedJavaType objectType = virtual.type();
            ResolvedJavaType expectedType = tool.getMetaAccessProvider().lookupJavaType(boxingKind.toBoxedJavaClass());
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
