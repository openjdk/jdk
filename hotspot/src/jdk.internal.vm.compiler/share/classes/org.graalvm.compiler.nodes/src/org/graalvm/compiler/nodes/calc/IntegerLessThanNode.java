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

import static org.graalvm.compiler.core.common.calc.Condition.LT;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

@NodeInfo(shortName = "<")
public final class IntegerLessThanNode extends IntegerLowerThanNode {
    public static final NodeClass<IntegerLessThanNode> TYPE = NodeClass.create(IntegerLessThanNode.class);
    public static final LessThanOp OP = new LessThanOp();

    public IntegerLessThanNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, OP);
        assert !x.getStackKind().isNumericFloat() && x.getStackKind() != JavaKind.Object;
        assert !y.getStackKind().isNumericFloat() && y.getStackKind() != JavaKind.Object;
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        return OP.create(x, y, constantReflection);
    }

    @Override
    protected ValueNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        PrimitiveConstant primitive = (PrimitiveConstant) constant;
        assert condition() == LT;
        if (primitive.getJavaKind() == JavaKind.Int && primitive.asInt() == 0) {
            ValueNode a = mirrored ? normalizeNode.getY() : normalizeNode.getX();
            ValueNode b = mirrored ? normalizeNode.getX() : normalizeNode.getY();

            if (normalizeNode.getX().getStackKind() == JavaKind.Double || normalizeNode.getX().getStackKind() == JavaKind.Float) {
                return new FloatLessThanNode(a, b, mirrored ^ normalizeNode.isUnorderedLess);
            } else {
                return new IntegerLessThanNode(a, b);
            }
        }
        return this;
    }

    public static boolean subtractMayUnderflow(long x, long y, long minValue) {
        long r = x - y;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different than the sign of x
        return (((x ^ y) & (x ^ r)) < 0) || r <= minValue;
    }

    public static boolean subtractMayOverflow(long x, long y, long maxValue) {
        long r = x - y;
        // HD 2-12 Overflow iff the arguments have different signs and
        // the sign of the result is different than the sign of x
        return (((x ^ y) & (x ^ r)) < 0) || r > maxValue;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = super.canonical(tool, forX, forY);
        if (result != this) {
            return result;
        }
        if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            if (IntegerStamp.sameSign((IntegerStamp) forX.stamp(), (IntegerStamp) forY.stamp())) {
                return new IntegerBelowNode(forX, forY);
            }
        }
        if (forY.isConstant() && forY.asConstant().isDefaultForKind() && forX instanceof SubNode) {
            // (x - y) < 0 when x - y is known not to underflow <=> x < y
            SubNode sub = (SubNode) forX;
            IntegerStamp xStamp = (IntegerStamp) sub.getX().stamp();
            IntegerStamp yStamp = (IntegerStamp) sub.getY().stamp();
            long minValue = CodeUtil.minValue(xStamp.getBits());
            long maxValue = CodeUtil.maxValue(xStamp.getBits());

            if (!subtractMayUnderflow(xStamp.lowerBound(), yStamp.upperBound(), minValue) && !subtractMayOverflow(xStamp.upperBound(), yStamp.lowerBound(), maxValue)) {
                return new IntegerLessThanNode(sub.getX(), sub.getY());
            }
        }

        int bits = ((IntegerStamp) getX().stamp()).getBits();
        assert ((IntegerStamp) getY().stamp()).getBits() == bits;
        long min = OP.minValue(bits);
        long xResidue = 0;
        ValueNode left = null;
        JavaConstant leftCst = null;
        if (forX instanceof AddNode) {
            AddNode xAdd = (AddNode) forX;
            if (xAdd.getY().isJavaConstant()) {
                long xCst = xAdd.getY().asJavaConstant().asLong();
                xResidue = xCst - min;
                left = xAdd.getX();
            }
        } else if (forX.isJavaConstant()) {
            leftCst = forX.asJavaConstant();
        }
        if (left != null || leftCst != null) {
            long yResidue = 0;
            ValueNode right = null;
            JavaConstant rightCst = null;
            if (forY instanceof AddNode) {
                AddNode yAdd = (AddNode) forY;
                if (yAdd.getY().isJavaConstant()) {
                    long yCst = yAdd.getY().asJavaConstant().asLong();
                    yResidue = yCst - min;
                    right = yAdd.getX();
                }
            } else if (forY.isJavaConstant()) {
                rightCst = forY.asJavaConstant();
            }
            if (right != null || rightCst != null) {
                if ((xResidue == 0 && left != null) || (yResidue == 0 && right != null)) {
                    if (left == null) {
                        left = ConstantNode.forIntegerBits(bits, leftCst.asLong() - min);
                    } else if (xResidue != 0) {
                        left = AddNode.create(left, ConstantNode.forIntegerBits(bits, xResidue));
                    }
                    if (right == null) {
                        right = ConstantNode.forIntegerBits(bits, rightCst.asLong() - min);
                    } else if (yResidue != 0) {
                        right = AddNode.create(right, ConstantNode.forIntegerBits(bits, yResidue));
                    }
                    return new IntegerBelowNode(left, right);
                }
            }
        }
        return this;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        if (newX.stamp() instanceof FloatStamp && newY.stamp() instanceof FloatStamp) {
            return new FloatLessThanNode(newX, newY, true);
        } else if (newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp) {
            return new IntegerLessThanNode(newX, newY);
        }
        throw GraalError.shouldNotReachHere();
    }

    public static class LessThanOp extends LowerOp {

        @Override
        protected Condition getCondition() {
            return LT;
        }

        @Override
        protected IntegerLowerThanNode create(ValueNode x, ValueNode y) {
            return new IntegerLessThanNode(x, y);
        }

        @Override
        protected long upperBound(IntegerStamp stamp) {
            return stamp.upperBound();
        }

        @Override
        protected long lowerBound(IntegerStamp stamp) {
            return stamp.lowerBound();
        }

        @Override
        protected int compare(long a, long b) {
            return Long.compare(a, b);
        }

        @Override
        protected long min(long a, long b) {
            return Math.min(a, b);
        }

        @Override
        protected long max(long a, long b) {
            return Math.max(a, b);
        }

        @Override
        protected long cast(long a, int bits) {
            return CodeUtil.signExtend(a, bits);
        }

        @Override
        protected long minValue(int bits) {
            return NumUtil.minValue(bits);
        }

        @Override
        protected long maxValue(int bits) {
            return NumUtil.maxValue(bits);
        }

        @Override
        protected IntegerStamp forInteger(int bits, long min, long max) {
            return StampFactory.forInteger(bits, cast(min, bits), cast(max, bits));
        }
    }
}
