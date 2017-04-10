/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.code.CodeUtil;

@NodeInfo(shortName = "|<|")
public final class IntegerBelowNode extends IntegerLowerThanNode {
    public static final NodeClass<IntegerBelowNode> TYPE = NodeClass.create(IntegerBelowNode.class);
    private static final BelowOp OP = new BelowOp();

    public IntegerBelowNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, OP);
        assert x.stamp() instanceof IntegerStamp;
        assert y.stamp() instanceof IntegerStamp;
    }

    public static LogicNode create(ValueNode x, ValueNode y) {
        return OP.create(x, y);
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        assert newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp;
        return new IntegerBelowNode(newX, newY);
    }

    public static class BelowOp extends LowerOp {

        @Override
        protected long upperBound(IntegerStamp stamp) {
            return stamp.unsignedUpperBound();
        }

        @Override
        protected long lowerBound(IntegerStamp stamp) {
            return stamp.unsignedLowerBound();
        }

        @Override
        protected int compare(long a, long b) {
            return Long.compareUnsigned(a, b);
        }

        @Override
        protected long min(long a, long b) {
            return NumUtil.minUnsigned(a, b);
        }

        @Override
        protected long max(long a, long b) {
            return NumUtil.maxUnsigned(a, b);
        }

        @Override
        protected long cast(long a, int bits) {
            return CodeUtil.zeroExtend(a, bits);
        }

        @Override
        protected long minValue(int bits) {
            return 0;
        }

        @Override
        protected long maxValue(int bits) {
            return NumUtil.maxValueUnsigned(bits);
        }

        @Override
        protected IntegerStamp forInteger(int bits, long min, long max) {
            return StampFactory.forUnsignedInteger(bits, min, max);
        }

        @Override
        protected Condition getCondition() {
            return Condition.BT;
        }

        @Override
        protected IntegerLowerThanNode createNode(ValueNode x, ValueNode y) {
            return new IntegerBelowNode(x, y);
        }
    }
}
