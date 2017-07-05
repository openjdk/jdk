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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import java.io.Serializable;
import java.util.function.Function;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.ShiftOp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ArithmeticOperation;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code ShiftOp} class represents shift operations.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public abstract class ShiftNode<OP> extends BinaryNode implements ArithmeticOperation, ArithmeticLIRLowerable, NarrowableArithmeticNode {

    @SuppressWarnings("rawtypes") public static final NodeClass<ShiftNode> TYPE = NodeClass.create(ShiftNode.class);

    protected interface SerializableShiftFunction<T> extends Function<ArithmeticOpTable, ShiftOp<T>>, Serializable {
    }

    protected final SerializableShiftFunction<OP> getOp;

    /**
     * Creates a new shift operation.
     *
     * @param x the first input value
     * @param s the second input value
     */
    protected ShiftNode(NodeClass<? extends ShiftNode<OP>> c, SerializableShiftFunction<OP> getOp, ValueNode x, ValueNode s) {
        super(c, getOp.apply(ArithmeticOpTable.forStamp(x.stamp())).foldStamp(x.stamp(), (IntegerStamp) s.stamp()), x, s);
        assert ((IntegerStamp) s.stamp()).getBits() == 32;
        this.getOp = getOp;
    }

    protected final ShiftOp<OP> getOp(ValueNode forValue) {
        return getOp.apply(ArithmeticOpTable.forStamp(forValue.stamp()));
    }

    @Override
    public final ShiftOp<OP> getArithmeticOp() {
        return getOp(getX());
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return getArithmeticOp().foldStamp(stampX, (IntegerStamp) stampY);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && forY.isConstant()) {
            JavaConstant amount = forY.asJavaConstant();
            assert amount.getJavaKind() == JavaKind.Int;
            return ConstantNode.forPrimitive(stamp(), getOp(forX).foldConstant(forX.asConstant(), amount.asInt()));
        }
        return this;
    }

    public int getShiftAmountMask() {
        return getArithmeticOp().getShiftAmountMask(stamp());
    }

    @Override
    public boolean isNarrowable(int resultBits) {
        assert CodeUtil.isPowerOf2(resultBits);
        int narrowMask = resultBits - 1;
        int wideMask = getShiftAmountMask();
        assert (wideMask & narrowMask) == narrowMask : String.format("wideMask %x should be wider than narrowMask %x", wideMask, narrowMask);

        /*
         * Shifts are special because narrowing them also changes the implicit mask of the shift
         * amount. We can narrow only if (y & wideMask) == (y & narrowMask) for all possible values
         * of y.
         */
        IntegerStamp yStamp = (IntegerStamp) getY().stamp();
        return (yStamp.upMask() & (wideMask & ~narrowMask)) == 0;
    }
}
