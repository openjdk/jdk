/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConvertNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Read {@code Class::_klass} to get the hub for a {@link java.lang.Class}. This node mostly exists
 * to replace {@code _klass._java_mirror._klass} with {@code _klass}. The constant folding could be
 * handled by
 * {@link ReadNode#canonicalizeRead(ValueNode, AddressNode, LocationIdentity, CanonicalizerTool)}.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class ClassGetHubNode extends FloatingNode implements Lowerable, Canonicalizable, ConvertNode {
    public static final NodeClass<ClassGetHubNode> TYPE = NodeClass.create(ClassGetHubNode.class);
    @Input protected ValueNode clazz;

    public ClassGetHubNode(ValueNode clazz) {
        super(TYPE, KlassPointerStamp.klass());
        this.clazz = clazz;
    }

    public static ValueNode create(ValueNode clazz, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, boolean allUsagesAvailable) {
        return canonical(null, metaAccess, constantReflection, allUsagesAvailable, KlassPointerStamp.klass(), clazz);
    }

    @SuppressWarnings("unused")
    public static boolean intrinsify(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode clazz) {
        ValueNode clazzValue = create(clazz, b.getMetaAccess(), b.getConstantReflection(), false);
        b.push(JavaKind.Object, b.append(clazzValue));
        return true;
    }

    public static ValueNode canonical(ClassGetHubNode classGetHubNode, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, boolean allUsagesAvailable, Stamp stamp,
                    ValueNode clazz) {
        ClassGetHubNode self = classGetHubNode;
        if (allUsagesAvailable && self != null && self.hasNoUsages()) {
            return null;
        } else {
            if (clazz.isConstant()) {
                if (metaAccess != null) {
                    ResolvedJavaType exactType = constantReflection.asJavaType(clazz.asJavaConstant());
                    if (exactType.isPrimitive()) {
                        return ConstantNode.forConstant(stamp, JavaConstant.NULL_POINTER, metaAccess);
                    } else {
                        return ConstantNode.forConstant(stamp, constantReflection.asObjectHub(exactType), metaAccess);
                    }
                }
            }
            if (clazz instanceof GetClassNode) {
                GetClassNode getClass = (GetClassNode) clazz;
                return new LoadHubNode(KlassPointerStamp.klassNonNull(), getClass.getObject());
            }
            if (clazz instanceof HubGetClassNode) {
                // Replace: _klass._java_mirror._klass -> _klass
                return ((HubGetClassNode) clazz).getHub();
            }
            if (self == null) {
                self = new ClassGetHubNode(clazz);
            }
            return self;
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        return canonical(this, tool.getMetaAccess(), tool.getConstantReflection(), tool.allUsagesAvailable(), stamp(NodeView.DEFAULT), clazz);
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @NodeIntrinsic
    public static native KlassPointer readClass(Class<?> clazzNonNull);

    @NodeIntrinsic(PiNode.class)
    public static native KlassPointer piCastNonNull(Object object, GuardingNode anchor);

    @Override
    public ValueNode getValue() {
        return clazz;
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        ResolvedJavaType exactType = constantReflection.asJavaType(c);
        if (exactType.isPrimitive()) {
            return JavaConstant.NULL_POINTER;
        } else {
            return constantReflection.asObjectHub(exactType);
        }
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        assert !c.equals(JavaConstant.NULL_POINTER);
        ResolvedJavaType objectType = constantReflection.asJavaType(c);
        return constantReflection.asJavaClass(objectType);
    }

    @Override
    public boolean isLossless() {
        return false;
    }

    /**
     * There is more than one {@link java.lang.Class} value that has a NULL hub.
     */
    @Override
    public boolean mayNullCheckSkipConversion() {
        return false;
    }

    @Override
    public boolean preservesOrder(Condition op, Constant value, ConstantReflectionProvider constantReflection) {
        assert op == Condition.EQ || op == Condition.NE;
        ResolvedJavaType exactType = constantReflection.asJavaType(value);
        return !exactType.isPrimitive();
    }

}
