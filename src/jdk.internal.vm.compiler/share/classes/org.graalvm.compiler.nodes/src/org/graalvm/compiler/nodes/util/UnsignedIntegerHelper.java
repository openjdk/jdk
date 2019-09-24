/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.util;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;

import jdk.vm.ci.code.CodeUtil;

public class UnsignedIntegerHelper extends IntegerHelper {
    public UnsignedIntegerHelper(int bits) {
        super(bits);
    }

    @Override
    public long upperBound(IntegerStamp stamp) {
        assert stamp.getBits() == bits;
        return stamp.unsignedUpperBound();
    }

    @Override
    public long lowerBound(IntegerStamp stamp) {
        assert stamp.getBits() == bits;
        return stamp.unsignedLowerBound();
    }

    @Override
    protected int rawCompare(long a, long b) {
        return Long.compareUnsigned(a, b);
    }

    @Override
    protected long rawMin(long a, long b) {
        return NumUtil.minUnsigned(a, b);
    }

    @Override
    protected long rawMax(long a, long b) {
        return NumUtil.maxUnsigned(a, b);
    }

    @Override
    public long cast(long a) {
        return CodeUtil.zeroExtend(a, bits);
    }

    @Override
    public long minValue() {
        return 0;
    }

    @Override
    public long maxValue() {
        return NumUtil.maxValueUnsigned(bits);
    }

    @Override
    public IntegerStamp stamp(long min, long max) {
        return StampFactory.forUnsignedInteger(bits, min, max);
    }

    @Override
    public LogicNode createCompareNode(ValueNode x, ValueNode y, NodeView view) {
        return IntegerBelowNode.create(x, y, view);
    }
}
