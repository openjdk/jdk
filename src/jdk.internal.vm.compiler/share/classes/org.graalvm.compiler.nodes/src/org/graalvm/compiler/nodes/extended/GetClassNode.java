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


package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Loads an object's class (i.e., this node can be created for {@code object.getClass()}).
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class GetClassNode extends FloatingNode implements Lowerable, Canonicalizable, Virtualizable {

    public static final NodeClass<GetClassNode> TYPE = NodeClass.create(GetClassNode.class);
    @Input ValueNode object;

    public ValueNode getObject() {
        return object;
    }

    public GetClassNode(Stamp stamp, ValueNode object) {
        super(TYPE, stamp);
        this.object = object;
        assert ((ObjectStamp) object.stamp(NodeView.DEFAULT)).nonNull();
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public static ValueNode tryFold(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, NodeView view, ValueNode object) {
        if (metaAccess != null && object != null && object.stamp(view) instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) object.stamp(view);
            if (objectStamp.isExactType()) {
                return ConstantNode.forConstant(constantReflection.asJavaClass(objectStamp.type()), metaAccess);
            }
        }
        return null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        NodeView view = NodeView.from(tool);
        ValueNode folded = tryFold(tool.getMetaAccess(), tool.getConstantReflection(), view, getObject());
        return folded == null ? this : folded;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getObject());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            Constant javaClass = tool.getConstantReflection().asJavaClass(virtual.type());
            tool.replaceWithValue(ConstantNode.forConstant(stamp(NodeView.DEFAULT), javaClass, tool.getMetaAccess(), graph()));
        }
    }
}
