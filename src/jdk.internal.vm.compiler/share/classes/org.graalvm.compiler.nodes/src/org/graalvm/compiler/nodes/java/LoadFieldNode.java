/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.graph.iterators.NodePredicates.isNotA;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.spi.UncheckedInterfaceProvider;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.ConstantFoldUtil;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * The {@code LoadFieldNode} represents a read of a static or instance field.
 */
@NodeInfo(nameTemplate = "LoadField#{p#field/s}")
public final class LoadFieldNode extends AccessFieldNode implements Canonicalizable.Unary<ValueNode>, Virtualizable, UncheckedInterfaceProvider {

    public static final NodeClass<LoadFieldNode> TYPE = NodeClass.create(LoadFieldNode.class);

    private final Stamp uncheckedStamp;

    protected LoadFieldNode(StampPair stamp, ValueNode object, ResolvedJavaField field) {
        super(TYPE, stamp.getTrustedStamp(), object, field);
        this.uncheckedStamp = stamp.getUncheckedStamp();
    }

    public static LoadFieldNode create(Assumptions assumptions, ValueNode object, ResolvedJavaField field) {
        return new LoadFieldNode(StampFactory.forDeclaredType(assumptions, field.getType(), false), object, field);
    }

    public static ValueNode create(ConstantFieldProvider constantFields, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess,
                    OptionValues options, Assumptions assumptions, ValueNode object, ResolvedJavaField field, boolean canonicalizeReads, boolean allUsagesAvailable) {
        return canonical(null, StampFactory.forDeclaredType(assumptions, field.getType(), false), object,
                        field, constantFields, constantReflection, options, metaAccess, canonicalizeReads, allUsagesAvailable);
    }

    public static LoadFieldNode createOverrideStamp(StampPair stamp, ValueNode object, ResolvedJavaField field) {
        return new LoadFieldNode(stamp, object, field);
    }

    public static ValueNode createOverrideStamp(ConstantFieldProvider constantFields, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess,
                    OptionValues options, StampPair stamp, ValueNode object, ResolvedJavaField field, boolean canonicalizeReads, boolean allUsagesAvailable) {
        return canonical(null, stamp, object, field, constantFields, constantReflection, options, metaAccess, canonicalizeReads, allUsagesAvailable);
    }

    @Override
    public ValueNode getValue() {
        return object();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forObject) {
        NodeView view = NodeView.from(tool);
        if (tool.allUsagesAvailable() && hasNoUsages() && !isVolatile() && (isStatic() || StampTool.isPointerNonNull(forObject.stamp(view)))) {
            return null;
        }
        return canonical(this, StampPair.create(stamp, uncheckedStamp), forObject, field, tool.getConstantFieldProvider(),
                        tool.getConstantReflection(), tool.getOptions(), tool.getMetaAccess(), tool.canonicalizeReads(), tool.allUsagesAvailable());
    }

    private static ValueNode canonical(LoadFieldNode loadFieldNode, StampPair stamp, ValueNode forObject, ResolvedJavaField field,
                    ConstantFieldProvider constantFields, ConstantReflectionProvider constantReflection,
                    OptionValues options, MetaAccessProvider metaAccess, boolean canonicalizeReads, boolean allUsagesAvailable) {
        LoadFieldNode self = loadFieldNode;
        if (canonicalizeReads && metaAccess != null) {
            ConstantNode constant = asConstant(constantFields, constantReflection, metaAccess, options, forObject, field);
            if (constant != null) {
                return constant;
            }
            if (allUsagesAvailable) {
                PhiNode phi = asPhi(constantFields, constantReflection, metaAccess, options, forObject,
                                field, stamp.getTrustedStamp());
                if (phi != null) {
                    return phi;
                }
            }
        }
        if (self != null && !field.isStatic() && forObject.isNullConstant()) {
            return new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        if (self == null) {
            self = new LoadFieldNode(stamp, forObject, field);
        }
        return self;
    }

    /**
     * Gets a constant value for this load if possible.
     */
    public ConstantNode asConstant(CanonicalizerTool tool, ValueNode forObject) {
        return asConstant(tool.getConstantFieldProvider(), tool.getConstantReflection(),
                        tool.getMetaAccess(), tool.getOptions(), forObject, field);
    }

    private static ConstantNode asConstant(ConstantFieldProvider constantFields, ConstantReflectionProvider constantReflection,
                    MetaAccessProvider metaAccess, OptionValues options, ValueNode forObject, ResolvedJavaField field) {
        if (field.isStatic()) {
            return ConstantFoldUtil.tryConstantFold(constantFields, constantReflection, metaAccess, field, null, options);
        } else if (forObject.isConstant() && !forObject.isNullConstant()) {
            return ConstantFoldUtil.tryConstantFold(constantFields, constantReflection, metaAccess, field, forObject.asJavaConstant(), options);
        }
        return null;
    }

    public ConstantNode asConstant(CanonicalizerTool tool, JavaConstant constant) {
        return ConstantFoldUtil.tryConstantFold(tool.getConstantFieldProvider(), tool.getConstantReflection(), tool.getMetaAccess(), field(), constant, tool.getOptions());
    }

    private static PhiNode asPhi(ConstantFieldProvider constantFields, ConstantReflectionProvider constantReflection,
                    MetaAccessProvider metaAcccess, OptionValues options, ValueNode forObject, ResolvedJavaField field, Stamp stamp) {
        if (!field.isStatic() && field.isFinal() && forObject instanceof ValuePhiNode && ((ValuePhiNode) forObject).values().filter(isNotA(ConstantNode.class)).isEmpty()) {
            PhiNode phi = (PhiNode) forObject;
            ConstantNode[] constantNodes = new ConstantNode[phi.valueCount()];
            for (int i = 0; i < phi.valueCount(); i++) {
                ConstantNode constant = ConstantFoldUtil.tryConstantFold(constantFields, constantReflection, metaAcccess, field, phi.valueAt(i).asJavaConstant(),
                                options);
                if (constant == null) {
                    return null;
                }
                constantNodes[i] = constant;
            }
            return new ValuePhiNode(stamp, phi.merge(), constantNodes);
        }
        return null;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            int fieldIndex = ((VirtualInstanceNode) alias).fieldIndex(field());
            if (fieldIndex != -1) {
                ValueNode entry = tool.getEntry((VirtualObjectNode) alias, fieldIndex);
                if (stamp.isCompatible(entry.stamp(NodeView.DEFAULT))) {
                    tool.replaceWith(entry);
                } else {
                    assert stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Int && (entry.stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Long || entry.getStackKind() == JavaKind.Double ||
                                    entry.getStackKind() == JavaKind.Illegal) : "Can only allow different stack kind two slot marker writes on one stot fields.";
                }
            }
        }
    }

    @Override
    public Stamp uncheckedStamp() {
        return uncheckedStamp;
    }

    public void setObject(ValueNode newObject) {
        this.updateUsages(object, newObject);
        this.object = newObject;
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        if (field.isVolatile()) {
            return CYCLES_2;
        }
        return super.estimatedNodeCycles();
    }
}
