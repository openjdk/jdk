/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualBoxingNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(shortName = "==")
public final class ObjectEqualsNode extends PointerEqualsNode implements Virtualizable {

    public static final NodeClass<ObjectEqualsNode> TYPE = NodeClass.create(ObjectEqualsNode.class);
    private static final ObjectEqualsOp OP = new ObjectEqualsOp();

    public ObjectEqualsNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y);
        assert x.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp;
        assert y.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp;
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection, NodeView view) {
        LogicNode result = CompareNode.tryConstantFold(CanonicalCondition.EQ, x, y, constantReflection, false);
        if (result != null) {
            return result;
        } else {
            result = findSynonym(x, y, view);
            if (result != null) {
                return result;
            }
            return new ObjectEqualsNode(x, y);
        }
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, ValueNode x, ValueNode y, NodeView view) {
        LogicNode result = OP.canonical(constantReflection, metaAccess, options, null, CanonicalCondition.EQ, false, x, y, view);
        if (result != null) {
            return result;
        }
        return create(x, y, constantReflection, view);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), CanonicalCondition.EQ, false, forX, forY, view);
        if (value != null) {
            return value;
        }
        return this;
    }

    public static class ObjectEqualsOp extends PointerEqualsOp {

        @Override
        protected LogicNode canonicalizeSymmetricConstant(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        CanonicalCondition condition, Constant constant, ValueNode nonConstant, boolean mirrored, boolean unorderedIsTrue, NodeView view) {
            ResolvedJavaType type = constantReflection.asJavaType(constant);
            if (type != null && nonConstant instanceof GetClassNode) {
                GetClassNode getClassNode = (GetClassNode) nonConstant;
                ValueNode object = getClassNode.getObject();
                assert ((ObjectStamp) object.stamp(view)).nonNull();
                if (!type.isPrimitive() && (type.isConcrete() || type.isArray())) {
                    return InstanceOfNode.create(TypeReference.createExactTrusted(type), object);
                }
                return LogicConstantNode.forBoolean(false);
            }
            return super.canonicalizeSymmetricConstant(constantReflection, metaAccess, options, smallestCompareWidth, condition, constant, nonConstant, mirrored, unorderedIsTrue, view);
        }

        @Override
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue, NodeView view) {
            if (newX.stamp(view) instanceof ObjectStamp && newY.stamp(view) instanceof ObjectStamp) {
                return new ObjectEqualsNode(newX, newY);
            } else if (newX.stamp(view) instanceof AbstractPointerStamp && newY.stamp(view) instanceof AbstractPointerStamp) {
                return new PointerEqualsNode(newX, newY);
            }
            throw GraalError.shouldNotReachHere();
        }
    }

    private static LogicNode virtualizeNonVirtualComparison(VirtualObjectNode virtual, ValueNode other, StructuredGraph graph, VirtualizerTool tool) {
        if (virtual instanceof VirtualBoxingNode && other.isConstant()) {
            VirtualBoxingNode virtualBoxingNode = (VirtualBoxingNode) virtual;
            if (virtualBoxingNode.getBoxingKind() == JavaKind.Boolean) {
                JavaConstant otherUnboxed = tool.getConstantReflection().unboxPrimitive(other.asJavaConstant());
                if (otherUnboxed != null && otherUnboxed.getJavaKind() == JavaKind.Boolean) {
                    int expectedValue = otherUnboxed.asBoolean() ? 1 : 0;
                    return new IntegerEqualsNode(virtualBoxingNode.getBoxedValue(tool), ConstantNode.forInt(expectedValue, graph));
                } else {
                    return LogicConstantNode.contradiction(graph);
                }
            }
        }
        if (virtual.hasIdentity()) {
            // one of them is virtual: they can never be the same objects
            return LogicConstantNode.contradiction(graph);
        }
        return null;
    }

    public static LogicNode virtualizeComparison(ValueNode x, ValueNode y, StructuredGraph graph, VirtualizerTool tool) {
        ValueNode xAlias = tool.getAlias(x);
        ValueNode yAlias = tool.getAlias(y);

        VirtualObjectNode xVirtual = xAlias instanceof VirtualObjectNode ? (VirtualObjectNode) xAlias : null;
        VirtualObjectNode yVirtual = yAlias instanceof VirtualObjectNode ? (VirtualObjectNode) yAlias : null;

        if (xVirtual != null && yVirtual == null) {
            return virtualizeNonVirtualComparison(xVirtual, yAlias, graph, tool);
        } else if (xVirtual == null && yVirtual != null) {
            return virtualizeNonVirtualComparison(yVirtual, xAlias, graph, tool);
        } else if (xVirtual != null && yVirtual != null) {
            if (xVirtual.hasIdentity() ^ yVirtual.hasIdentity()) {
                /*
                 * One of the two objects has identity, the other doesn't. In code, this looks like
                 * "Integer.valueOf(a) == new Integer(b)", which is always false.
                 *
                 * In other words: an object created via valueOf can never be equal to one created
                 * by new in the same compilation unit.
                 */
                return LogicConstantNode.contradiction(graph);
            } else if (!xVirtual.hasIdentity() && !yVirtual.hasIdentity()) {
                ResolvedJavaType type = xVirtual.type();
                if (type.equals(yVirtual.type())) {
                    MetaAccessProvider metaAccess = tool.getMetaAccess();
                    if (type.equals(metaAccess.lookupJavaType(Integer.class)) || type.equals(metaAccess.lookupJavaType(Long.class))) {
                        // both are virtual without identity: check contents
                        assert xVirtual.entryCount() == 1 && yVirtual.entryCount() == 1;
                        assert xVirtual.entryKind(0).getStackKind() == JavaKind.Int || xVirtual.entryKind(0) == JavaKind.Long;
                        return new IntegerEqualsNode(tool.getEntry(xVirtual, 0), tool.getEntry(yVirtual, 0));
                    }
                }
            } else {
                // both are virtual with identity: check if they refer to the same object
                return LogicConstantNode.forBoolean(xVirtual == yVirtual, graph);
            }
        }
        return null;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode node = virtualizeComparison(getX(), getY(), graph(), tool);
        if (node == null) {
            return;
        }
        if (!node.isAlive()) {
            tool.addNode(node);
        }
        tool.replaceWithValue(node);
    }
}
