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

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import org.graalvm.compiler.options.OptionValues;

@NodeInfo(shortName = "<")
public final class IntegerLessThanNode extends IntegerLowerThanNode {
    public static final NodeClass<IntegerLessThanNode> TYPE = NodeClass.create(IntegerLessThanNode.class);
    public static final LessThanOp OP = new LessThanOp();

    public IntegerLessThanNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, OP);
        assert !x.getStackKind().isNumericFloat() && x.getStackKind() != JavaKind.Object;
        assert !y.getStackKind().isNumericFloat() && y.getStackKind() != JavaKind.Object;
    }

    public static LogicNode create(ValueNode x, ValueNode y) {
        return OP.create(x, y);
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                    ValueNode x, ValueNode y) {
        LogicNode value = OP.canonical(constantReflection, metaAccess, options, smallestCompareWidth, OP.getCondition(), false, x, y);
        if (value != null) {
            return value;
        }
        return create(x, y);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), OP.getCondition(), false, forX, forY);
        if (value != null) {
            return value;
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

    public static class LessThanOp extends LowerOp {
        @Override
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue) {
            if (newX.stamp() instanceof FloatStamp && newY.stamp() instanceof FloatStamp) {
                return new FloatLessThanNode(newX, newY, unorderedIsTrue); // TODO: Is the last arg
                                                                           // supposed to be true?
            } else if (newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp) {
                return new IntegerLessThanNode(newX, newY);
            }
            throw GraalError.shouldNotReachHere();
        }

        @Override
        protected LogicNode optimizeNormalizeCompare(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
            PrimitiveConstant primitive = (PrimitiveConstant) constant;
            /* @formatter:off
             * a NC b < c  (not mirrored)
             * cases for c:
             *  0         -> a < b
             *  [MIN, -1] -> false
             *  1         -> a <= b
             *  [2, MAX]  -> true
             * unordered-is-less means unordered-is-true.
             *
             * c < a NC b  (mirrored)
             * cases for c:
             *  0         -> a > b
             *  [1, MAX]  -> false
             *  -1        -> a >= b
             *  [MIN, -2] -> true
             * unordered-is-less means unordered-is-false.
             *
             *  We can handle mirroring by swapping a & b and negating the constant.
             *  @formatter:on
             */
            ValueNode a = mirrored ? normalizeNode.getY() : normalizeNode.getX();
            ValueNode b = mirrored ? normalizeNode.getX() : normalizeNode.getY();
            long cst = mirrored ? -primitive.asLong() : primitive.asLong();

            if (cst == 0) {
                if (normalizeNode.getX().getStackKind() == JavaKind.Double || normalizeNode.getX().getStackKind() == JavaKind.Float) {
                    return FloatLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, a, b, mirrored ^ normalizeNode.isUnorderedLess);
                } else {
                    return IntegerLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, a, b);
                }
            } else if (cst == 1) {
                // a <= b <=> !(a > b)
                LogicNode compare;
                if (normalizeNode.getX().getStackKind() == JavaKind.Double || normalizeNode.getX().getStackKind() == JavaKind.Float) {
                    // since we negate, we have to reverse the unordered result
                    compare = FloatLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, b, a, mirrored == normalizeNode.isUnorderedLess);
                } else {
                    compare = IntegerLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, b, a);
                }
                return LogicNegationNode.create(compare);
            } else if (cst <= -1) {
                return LogicConstantNode.contradiction();
            } else {
                assert cst >= 2;
                return LogicConstantNode.tautology();
            }
        }

        @Override
        protected LogicNode findSynonym(ValueNode forX, ValueNode forY) {
            LogicNode result = super.findSynonym(forX, forY);
            if (result != null) {
                return result;
            }
            if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
                if (IntegerStamp.sameSign((IntegerStamp) forX.stamp(), (IntegerStamp) forY.stamp())) {
                    return new IntegerBelowNode(forX, forY);
                }
            }
            if (forY.isConstant() && forX instanceof SubNode) {
                SubNode sub = (SubNode) forX;
                ValueNode xx = null;
                ValueNode yy = null;
                boolean negate = false;
                if (forY.asConstant().isDefaultForKind()) {
                    // (x - y) < 0 when x - y is known not to underflow <=> x < y
                    xx = sub.getX();
                    yy = sub.getY();
                } else if (forY.isJavaConstant() && forY.asJavaConstant().asLong() == 1) {
                    // (x - y) < 1 when x - y is known not to underflow <=> !(y < x)
                    xx = sub.getY();
                    yy = sub.getX();
                    negate = true;
                }
                if (xx != null) {
                    assert yy != null;
                    IntegerStamp xStamp = (IntegerStamp) sub.getX().stamp();
                    IntegerStamp yStamp = (IntegerStamp) sub.getY().stamp();
                    long minValue = CodeUtil.minValue(xStamp.getBits());
                    long maxValue = CodeUtil.maxValue(xStamp.getBits());

                    if (!subtractMayUnderflow(xStamp.lowerBound(), yStamp.upperBound(), minValue) && !subtractMayOverflow(xStamp.upperBound(), yStamp.lowerBound(), maxValue)) {
                        LogicNode logic = new IntegerLessThanNode(xx, yy);
                        if (negate) {
                            logic = LogicNegationNode.create(logic);
                        }
                        return logic;
                    }
                }
            }

            int bits = ((IntegerStamp) forX.stamp()).getBits();
            assert ((IntegerStamp) forY.stamp()).getBits() == bits;
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
            return null;
        }

        @Override
        protected Condition getCondition() {
            return LT;
        }

        @Override
        protected IntegerLowerThanNode createNode(ValueNode x, ValueNode y) {
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
