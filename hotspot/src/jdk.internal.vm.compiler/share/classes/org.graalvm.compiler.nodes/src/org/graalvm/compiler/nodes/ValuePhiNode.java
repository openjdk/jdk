/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import java.util.stream.Collectors;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

/**
 * Value {@link PhiNode}s merge data flow values at control flow merges.
 */
@NodeInfo(nameTemplate = "Phi({i#values})")
public class ValuePhiNode extends PhiNode implements ArrayLengthProvider {

    public static final NodeClass<ValuePhiNode> TYPE = NodeClass.create(ValuePhiNode.class);
    @Input protected NodeInputList<ValueNode> values;

    public ValuePhiNode(Stamp stamp, AbstractMergeNode merge) {
        this(TYPE, stamp, merge);
    }

    protected ValuePhiNode(NodeClass<? extends ValuePhiNode> c, Stamp stamp, AbstractMergeNode merge) {
        super(c, stamp, merge);
        assert stamp != StampFactory.forVoid();
        values = new NodeInputList<>(this);
    }

    public ValuePhiNode(Stamp stamp, AbstractMergeNode merge, ValueNode[] values) {
        super(TYPE, stamp, merge);
        assert stamp != StampFactory.forVoid();
        this.values = new NodeInputList<>(this, values);
    }

    @Override
    public NodeInputList<ValueNode> values() {
        return values;
    }

    @Override
    public boolean inferStamp() {
        /*
         * Meet all the values feeding this Phi but don't use the stamp of this Phi since that's
         * what's being computed.
         */
        Stamp valuesStamp = StampTool.meetOrNull(values(), this);
        if (valuesStamp == null) {
            valuesStamp = stamp;
        } else if (stamp.isCompatible(valuesStamp)) {
            valuesStamp = stamp.join(valuesStamp);
        }
        return updateStamp(valuesStamp);
    }

    @Override
    public ValueNode length() {
        if (merge() instanceof LoopBeginNode) {
            return null;
        }
        ValueNode length = null;
        for (ValueNode input : values()) {
            ValueNode l = GraphUtil.arrayLength(input);
            if (l == null) {
                return null;
            }
            if (length == null) {
                length = l;
            } else if (length != l) {
                return null;
            }
        }
        return length;
    }

    @Override
    public boolean verify() {
        Stamp s = null;
        for (ValueNode input : values()) {
            if (s == null) {
                s = input.stamp();
            } else {
                if (!s.isCompatible(input.stamp())) {
                    fail("Phi Input Stamps are not compatible. Phi:%s inputs:%s", this, values().stream().map(x -> x.toString() + ":" + x.stamp()).collect(Collectors.joining(", ")));
                }
            }
        }
        return super.verify();
    }
}
