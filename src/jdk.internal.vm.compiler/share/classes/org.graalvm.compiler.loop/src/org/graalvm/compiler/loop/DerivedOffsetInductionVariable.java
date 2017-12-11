/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop;

import static org.graalvm.compiler.loop.MathUtil.add;
import static org.graalvm.compiler.loop.MathUtil.sub;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.SubNode;

public class DerivedOffsetInductionVariable extends DerivedInductionVariable {

    private final ValueNode offset;
    private final BinaryArithmeticNode<?> value;

    public DerivedOffsetInductionVariable(LoopEx loop, InductionVariable base, ValueNode offset, BinaryArithmeticNode<?> value) {
        super(loop, base);
        this.offset = offset;
        this.value = value;
    }

    public ValueNode getOffset() {
        return offset;
    }

    @Override
    public Direction direction() {
        return base.direction();
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public boolean isConstantInit() {
        return offset.isConstant() && base.isConstantInit();
    }

    @Override
    public boolean isConstantStride() {
        return base.isConstantStride();
    }

    @Override
    public long constantInit() {
        return op(base.constantInit(), offset.asJavaConstant().asLong());
    }

    @Override
    public long constantStride() {
        if (value instanceof SubNode && base.valueNode() == value.getY()) {
            return -base.constantStride();
        }
        return base.constantStride();
    }

    @Override
    public ValueNode initNode() {
        return op(base.initNode(), offset);
    }

    @Override
    public ValueNode strideNode() {
        if (value instanceof SubNode && base.valueNode() == value.getY()) {
            return graph().addOrUniqueWithInputs(NegateNode.create(base.strideNode(), NodeView.DEFAULT));
        }
        return base.strideNode();
    }

    @Override
    public ValueNode extremumNode(boolean assumePositiveTripCount, Stamp stamp) {
        return op(base.extremumNode(assumePositiveTripCount, stamp), IntegerConvertNode.convert(offset, stamp, graph(), NodeView.DEFAULT));
    }

    @Override
    public ValueNode exitValueNode() {
        return op(base.exitValueNode(), offset);
    }

    @Override
    public boolean isConstantExtremum() {
        return offset.isConstant() && base.isConstantExtremum();
    }

    @Override
    public long constantExtremum() {
        return op(base.constantExtremum(), offset.asJavaConstant().asLong());
    }

    private long op(long b, long o) {
        if (value instanceof AddNode) {
            return b + o;
        }
        if (value instanceof SubNode) {
            if (base.valueNode() == value.getX()) {
                return b - o;
            } else {
                assert base.valueNode() == value.getY();
                return o - b;
            }
        }
        throw GraalError.shouldNotReachHere();
    }

    private ValueNode op(ValueNode b, ValueNode o) {
        if (value instanceof AddNode) {
            return add(graph(), b, o);
        }
        if (value instanceof SubNode) {
            if (base.valueNode() == value.getX()) {
                return sub(graph(), b, o);
            } else {
                assert base.valueNode() == value.getY();
                return sub(graph(), o, b);
            }
        }
        throw GraalError.shouldNotReachHere();
    }

    @Override
    public void deleteUnusedNodes() {
    }

    @Override
    public String toString() {
        return String.format("DerivedOffsetInductionVariable base (%s) %s %s", base, value.getNodeClass().shortName(), offset);
    }
}
