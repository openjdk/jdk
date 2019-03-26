/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.calc.CanonicalCondition.LT;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
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
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "<")
public final class IntegerLessThanNode extends IntegerLowerThanNode {
    public static final NodeClass<IntegerLessThanNode> TYPE = NodeClass.create(IntegerLessThanNode.class);
    private static final LessThanOp OP = new LessThanOp();

    public IntegerLessThanNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, OP);
        assert !x.getStackKind().isNumericFloat() && x.getStackKind() != JavaKind.Object;
        assert !y.getStackKind().isNumericFloat() && y.getStackKind() != JavaKind.Object;
    }

    public static LogicNode create(ValueNode x, ValueNode y, NodeView view) {
        return OP.create(x, y, view);
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                    ValueNode x, ValueNode y, NodeView view) {
        LogicNode value = OP.canonical(constantReflection, metaAccess, options, smallestCompareWidth, OP.getCondition(), false, x, y, view);
        if (value != null) {
            return value;
        }
        return create(x, y, view);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), OP.getCondition(), false, forX, forY, view);
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
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue, NodeView view) {
            if (newX.stamp(view) instanceof FloatStamp && newY.stamp(view) instanceof FloatStamp) {
                return new FloatLessThanNode(newX, newY, unorderedIsTrue); // TODO: Is the last arg
                                                                           // supposed to be true?
            } else if (newX.stamp(view) instanceof IntegerStamp && newY.stamp(view) instanceof IntegerStamp) {
                return new IntegerLessThanNode(newX, newY);
            }
            throw GraalError.shouldNotReachHere();
        }

        @Override
        protected LogicNode optimizeNormalizeCompare(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored, NodeView view) {
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
                    return FloatLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, a, b, mirrored ^ normalizeNode.isUnorderedLess, view);
                } else {
                    return IntegerLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, a, b, view);
                }
            } else if (cst == 1) {
                // a <= b <=> !(a > b)
                LogicNode compare;
                if (normalizeNode.getX().getStackKind() == JavaKind.Double || normalizeNode.getX().getStackKind() == JavaKind.Float) {
                    // since we negate, we have to reverse the unordered result
                    compare = FloatLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, b, a, mirrored == normalizeNode.isUnorderedLess, view);
                } else {
                    compare = IntegerLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, b, a, view);
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
        protected LogicNode findSynonym(ValueNode forX, ValueNode forY, NodeView view) {
            LogicNode result = super.findSynonym(forX, forY, view);
            if (result != null) {
                return result;
            }
            if (forX.stamp(view) instanceof IntegerStamp && forY.stamp(view) instanceof IntegerStamp) {
                if (IntegerStamp.sameSign((IntegerStamp) forX.stamp(view), (IntegerStamp) forY.stamp(view))) {
                    return new IntegerBelowNode(forX, forY);
                }
            }

            // Attempt to optimize the case where we can fold a constant from the left side (either
            // from an add or sub) into the constant on the right side.
            if (forY.isConstant()) {
                if (forX instanceof SubNode) {
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
                        IntegerStamp xStamp = (IntegerStamp) sub.getX().stamp(view);
                        IntegerStamp yStamp = (IntegerStamp) sub.getY().stamp(view);
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
                } else if (forX instanceof AddNode) {

                    // (x + xConstant) < forY => x < (forY - xConstant)
                    AddNode addNode = (AddNode) forX;
                    if (addNode.getY().isJavaConstant()) {
                        IntegerStamp xStamp = (IntegerStamp) addNode.getX().stamp(view);
                        if (!IntegerStamp.addCanOverflow(xStamp, (IntegerStamp) addNode.getY().stamp(view))) {
                            long minValue = CodeUtil.minValue(xStamp.getBits());
                            long maxValue = CodeUtil.maxValue(xStamp.getBits());
                            long yConstant = forY.asJavaConstant().asLong();
                            long xConstant = addNode.getY().asJavaConstant().asLong();
                            if (!subtractMayUnderflow(yConstant, xConstant, minValue) && !subtractMayOverflow(yConstant, xConstant, maxValue)) {
                                long newConstant = yConstant - xConstant;
                                return IntegerLessThanNode.create(addNode.getX(), ConstantNode.forIntegerStamp(xStamp, newConstant), view);
                            }
                        }
                    }
                }
            }

            if (forX.stamp(view) instanceof IntegerStamp) {
                assert forY.stamp(view) instanceof IntegerStamp;
                int bits = ((IntegerStamp) forX.stamp(view)).getBits();
                assert ((IntegerStamp) forY.stamp(view)).getBits() == bits;
                LogicNode logic = canonicalizeRangeFlip(forX, forY, bits, true, view);
                if (logic != null) {
                    return logic;
                }
            }
            return null;
        }

        @Override
        protected CanonicalCondition getCondition() {
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

    @Override
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (!thisNegated) {
            if (other instanceof IntegerLessThanNode) {
                ValueNode otherX = ((IntegerLessThanNode) other).getX();
                ValueNode otherY = ((IntegerLessThanNode) other).getY();
                // x < y => !y < x
                if (getX() == otherY && getY() == otherX) {
                    return TriState.FALSE;
                }
            }

            // x < y => !x == y
            // x < y => !y == x
            if (other instanceof IntegerEqualsNode) {
                ValueNode otherX = ((IntegerEqualsNode) other).getX();
                ValueNode otherY = ((IntegerEqualsNode) other).getY();
                if ((getX() == otherX && getY() == otherY) || (getX() == otherY && getY() == otherX)) {
                    return TriState.FALSE;
                }
            }
        }
        return super.implies(thisNegated, other);
    }
}
