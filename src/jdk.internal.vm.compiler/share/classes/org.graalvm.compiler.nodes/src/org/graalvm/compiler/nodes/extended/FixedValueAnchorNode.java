/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.ValueProxy;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public class FixedValueAnchorNode extends FixedWithNextNode implements LIRLowerable, ValueProxy, GuardingNode {
    public static final NodeClass<FixedValueAnchorNode> TYPE = NodeClass.create(FixedValueAnchorNode.class);

    @OptionalInput ValueNode object;
    private Stamp predefinedStamp;

    public ValueNode object() {
        return object;
    }

    protected FixedValueAnchorNode(NodeClass<? extends FixedValueAnchorNode> c, ValueNode object) {
        super(c, object == null ? StampFactory.forVoid() : object.stamp(NodeView.DEFAULT));
        this.object = object;
    }

    public FixedValueAnchorNode(ValueNode object) {
        this(TYPE, object);
    }

    public FixedValueAnchorNode(ValueNode object, Stamp predefinedStamp) {
        super(TYPE, predefinedStamp);
        this.object = object;
        this.predefinedStamp = predefinedStamp;
    }

    @Override
    public boolean inferStamp() {
        if (predefinedStamp == null && object != null) {
            return updateStamp(stamp.join(object.stamp(NodeView.DEFAULT)));
        } else {
            return false;
        }
    }

    @NodeIntrinsic
    public static native Object getObject(Object object);

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, generator.operand(object));
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }

    @Override
    public GuardingNode getGuard() {
        return this;
    }

}
