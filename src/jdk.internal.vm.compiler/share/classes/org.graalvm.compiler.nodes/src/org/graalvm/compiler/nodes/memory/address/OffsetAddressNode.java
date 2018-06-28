/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.memory.address;

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents an address that is composed of a base and an offset. The base can be either a
 * {@link JavaKind#Object}, a word-sized integer or another pointer. The offset must be a word-sized
 * integer.
 */
@NodeInfo(allowedUsageTypes = InputType.Association)
public class OffsetAddressNode extends AddressNode implements Canonicalizable {
    public static final NodeClass<OffsetAddressNode> TYPE = NodeClass.create(OffsetAddressNode.class);

    @Input ValueNode base;
    @Input ValueNode offset;

    public OffsetAddressNode(ValueNode base, ValueNode offset) {
        super(TYPE);
        this.base = base;
        this.offset = offset;

        assert base != null && (base.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp || IntegerStamp.getBits(base.stamp(NodeView.DEFAULT)) == 64) &&
                        offset != null && IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)) == 64 : "both values must have 64 bits";
    }

    public static OffsetAddressNode create(ValueNode base) {
        return new OffsetAddressNode(base, ConstantNode.forIntegerBits(PrimitiveStamp.getBits(base.stamp(NodeView.DEFAULT)), 0));
    }

    @Override
    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        updateUsages(this.base, base);
        this.base = base;
        assert base != null && (base.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp || IntegerStamp.getBits(base.stamp(NodeView.DEFAULT)) == 64);
    }

    public ValueNode getOffset() {
        return offset;
    }

    public void setOffset(ValueNode offset) {
        updateUsages(this.offset, offset);
        this.offset = offset;
        assert offset != null && IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)) == 64;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (base instanceof OffsetAddressNode) {
            NodeView view = NodeView.from(tool);
            // Rewrite (&base[offset1])[offset2] to base[offset1 + offset2].
            OffsetAddressNode b = (OffsetAddressNode) base;
            return new OffsetAddressNode(b.getBase(), BinaryArithmeticNode.add(b.getOffset(), this.getOffset(), view));
        } else if (base instanceof AddNode) {
            AddNode add = (AddNode) base;
            if (add.getY().isConstant()) {
                return new OffsetAddressNode(add.getX(), new AddNode(add.getY(), getOffset()));
            }
        }
        return this;
    }

    @NodeIntrinsic
    public static native Address address(Object base, long offset);

    @Override
    public long getMaxConstantDisplacement() {
        Stamp curStamp = offset.stamp(NodeView.DEFAULT);
        if (curStamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) curStamp;
            if (integerStamp.lowerBound() >= 0) {
                return integerStamp.upperBound();
            }
        }
        return Long.MAX_VALUE;
    }

    @Override
    public ValueNode getIndex() {
        return null;
    }
}
