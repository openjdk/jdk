/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.StampProvider;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Loads the component hub for of the provided array hub.
 *
 * This is a fixed node because on certain VMs, for example the HotSpot VM, the read is only valid
 * when the provided hub is actually an array hub (and not, e.g., a primitive hub), so this node
 * must not float above a possible type check. Properly guarding this node would be possible too,
 * but it is unlikely that the guard would be more flexible than just fixing the node in the control
 * flow.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class LoadArrayComponentHubNode extends FixedWithNextNode implements Lowerable, Canonicalizable.Unary<ValueNode> {

    public static final NodeClass<LoadArrayComponentHubNode> TYPE = NodeClass.create(LoadArrayComponentHubNode.class);

    private @Input ValueNode value;

    public static ValueNode create(ValueNode value, StampProvider stampProvider, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        Stamp stamp = stampProvider.createHubStamp(null);
        return findSynonym(null, value, stamp, metaAccess, constantReflection);
    }

    protected LoadArrayComponentHubNode(Stamp stamp, ValueNode value) {
        super(TYPE, stamp);
        this.value = value;
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        return findSynonym(this, forValue, stamp, tool.getMetaAccess(), tool.getConstantReflection());
    }

    private static ValueNode findSynonym(LoadArrayComponentHubNode self, ValueNode forValue, Stamp stamp, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        if (forValue.isConstant()) {
            ResolvedJavaType type = constantReflection.asJavaType(forValue.asConstant());
            if (type != null) {
                return ConstantNode.forConstant(stamp, constantReflection.asObjectHub(type.getComponentType()), metaAccess);
            }
        }
        return self != null ? self : new LoadArrayComponentHubNode(stamp, forValue);
    }
}
