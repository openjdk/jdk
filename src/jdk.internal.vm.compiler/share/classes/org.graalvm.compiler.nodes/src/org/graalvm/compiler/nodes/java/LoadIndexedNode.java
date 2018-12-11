/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_8)
public class LoadIndexedNode extends AccessIndexedNode implements Virtualizable, Canonicalizable, Simplifiable {

    public static final NodeClass<LoadIndexedNode> TYPE = NodeClass.create(LoadIndexedNode.class);

    /**
     * Creates a new LoadIndexedNode.
     *
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param elementKind the element type
     */
    public LoadIndexedNode(Assumptions assumptions, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind) {
        this(TYPE, createStamp(assumptions, array, elementKind), array, index, boundsCheck, elementKind);
    }

    public static ValueNode create(Assumptions assumptions, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind, MetaAccessProvider metaAccess,
                    ConstantReflectionProvider constantReflection) {
        ValueNode constant = tryConstantFold(array, index, metaAccess, constantReflection);
        if (constant != null) {
            return constant;
        }
        return new LoadIndexedNode(assumptions, array, index, boundsCheck, elementKind);
    }

    protected LoadIndexedNode(NodeClass<? extends LoadIndexedNode> c, Stamp stamp, ValueNode array, ValueNode index, GuardingNode boundsCheck, JavaKind elementKind) {
        super(c, stamp, array, index, boundsCheck, elementKind);
    }

    private static Stamp createStamp(Assumptions assumptions, ValueNode array, JavaKind kind) {
        ResolvedJavaType type = StampTool.typeOrNull(array);
        if (kind == JavaKind.Object && type != null && type.isArray()) {
            return StampFactory.object(TypeReference.createTrusted(assumptions, type.getComponentType()));
        } else {
            JavaKind preciseKind = determinePreciseArrayElementType(array, kind);
            return StampFactory.forKind(preciseKind);
        }
    }

    private static JavaKind determinePreciseArrayElementType(ValueNode array, JavaKind kind) {
        if (kind == JavaKind.Byte) {
            ResolvedJavaType javaType = ((ObjectStamp) array.stamp(NodeView.DEFAULT)).type();
            if (javaType != null && javaType.isArray() && javaType.getComponentType() != null && javaType.getComponentType().getJavaKind() == JavaKind.Boolean) {
                return JavaKind.Boolean;
            }
        }
        return kind;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(stamp.improveWith(createStamp(graph().getAssumptions(), array(), elementKind())));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(array());
        if (alias instanceof VirtualObjectNode) {
            VirtualArrayNode virtual = (VirtualArrayNode) alias;
            ValueNode indexValue = tool.getAlias(index());
            int idx = indexValue.isConstant() ? indexValue.asJavaConstant().asInt() : -1;
            if (idx >= 0 && idx < virtual.entryCount()) {
                ValueNode entry = tool.getEntry(virtual, idx);
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
    public Node canonical(CanonicalizerTool tool) {
        ValueNode constant = tryConstantFold(array(), index(), tool.getMetaAccess(), tool.getConstantReflection());
        if (constant != null) {
            return constant;
        }
        return this;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            NodeView view = NodeView.from(tool);
            ValueNode arrayLength = ArrayLengthNode.create(array, tool.getConstantReflection());
            LogicNode boundsCheck = CompareNode.createCompareNode(CanonicalCondition.BT, index, arrayLength, tool.getConstantReflection(), view);
            if (boundsCheck.isTautology()) {
                return;
            }
            if (graph().getGuardsStage().allowsGuardInsertion()) {
                if (!arrayLength.isAlive()) {
                    arrayLength = graph().addOrUniqueWithInputs(arrayLength);
                    if (arrayLength instanceof FixedWithNextNode) {
                        FixedWithNextNode fixedArrayLength = (FixedWithNextNode) arrayLength;
                        graph().addBeforeFixed(this, fixedArrayLength);
                    }
                }
                boundsCheck = graph().addOrUniqueWithInputs(boundsCheck);
                FixedGuardNode fixedGuard = new FixedGuardNode(boundsCheck, DeoptimizationReason.BoundsCheckException, DeoptimizationAction.InvalidateReprofile, false, getNodeSourcePosition());
                graph().replaceFixedWithFixed(this, graph().add(fixedGuard));
            }
        }
    }

    private static ValueNode tryConstantFold(ValueNode array, ValueNode index, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        if (array.isConstant() && !array.isNullConstant() && index.isConstant()) {
            JavaConstant arrayConstant = array.asJavaConstant();
            if (arrayConstant != null) {
                int stableDimension = ((ConstantNode) array).getStableDimension();
                if (stableDimension > 0) {
                    JavaConstant constant = constantReflection.readArrayElement(arrayConstant, index.asJavaConstant().asInt());
                    boolean isDefaultStable = ((ConstantNode) array).isDefaultStable();
                    if (constant != null && (isDefaultStable || !constant.isDefaultForKind())) {
                        return ConstantNode.forConstant(constant, stableDimension - 1, isDefaultStable, metaAccess);
                    }
                }
            }
        }
        return null;
    }
}
