/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "==")
public final class IntegerEqualsNode extends CompareNode implements BinaryCommutative<ValueNode>, IterableNodeType {
    public static final NodeClass<IntegerEqualsNode> TYPE = NodeClass.create(IntegerEqualsNode.class);

    public IntegerEqualsNode(ValueNode x, ValueNode y) {
        super(TYPE, Condition.EQ, false, x, y);
        assert !x.getStackKind().isNumericFloat() && x.getStackKind() != JavaKind.Object;
        assert !y.getStackKind().isNumericFloat() && y.getStackKind() != JavaKind.Object;
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.EQ, x, y, constantReflection, false);
        if (result != null) {
            return result;
        } else {
            if (x instanceof ConditionalNode) {
                ConditionalNode conditionalNode = (ConditionalNode) x;
                if (conditionalNode.trueValue() == y) {
                    return conditionalNode.condition();
                }
                if (conditionalNode.falseValue() == y) {
                    return LogicNegationNode.create(conditionalNode.condition());
                }
            } else if (y instanceof ConditionalNode) {
                ConditionalNode conditionalNode = (ConditionalNode) y;
                if (conditionalNode.trueValue() == x) {
                    return conditionalNode.condition();
                }
                if (conditionalNode.falseValue() == x) {
                    return LogicNegationNode.create(conditionalNode.condition());
                }
            }

            return new IntegerEqualsNode(x, y).maybeCommuteInputs();
        }
    }

    @Override
    protected ValueNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        PrimitiveConstant primitive = (PrimitiveConstant) constant;
        if (primitive.getJavaKind() == JavaKind.Int && primitive.asInt() == 0) {
            ValueNode a = mirrored ? normalizeNode.getY() : normalizeNode.getX();
            ValueNode b = mirrored ? normalizeNode.getX() : normalizeNode.getY();

            if (normalizeNode.getX().getStackKind() == JavaKind.Double || normalizeNode.getX().getStackKind() == JavaKind.Float) {
                return new FloatEqualsNode(a, b);
            } else {
                return new IntegerEqualsNode(a, b);
            }
        }
        return this;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        if (newX.stamp() instanceof FloatStamp && newY.stamp() instanceof FloatStamp) {
            return new FloatEqualsNode(newX, newY);
        } else if (newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp) {
            return new IntegerEqualsNode(newX, newY);
        } else if (newX.stamp() instanceof AbstractPointerStamp && newY.stamp() instanceof AbstractPointerStamp) {
            return new IntegerEqualsNode(newX, newY);
        }
        throw GraalError.shouldNotReachHere();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.tautology();
        } else if (forX.stamp().alwaysDistinct(forY.stamp())) {
            return LogicConstantNode.contradiction();
        }
        return super.canonical(tool, forX, forY);
    }

    @Override
    protected ValueNode canonicalizeSymmetricConstant(CanonicalizerTool tool, Constant constant, ValueNode nonConstant, boolean mirrored) {
        if (constant instanceof PrimitiveConstant && ((PrimitiveConstant) constant).asLong() == 0) {
            if (nonConstant instanceof AndNode) {
                AndNode andNode = (AndNode) nonConstant;
                return new IntegerTestNode(andNode.getX(), andNode.getY());
            } else if (nonConstant instanceof SubNode) {
                SubNode subNode = (SubNode) nonConstant;
                return IntegerEqualsNode.create(subNode.getX(), subNode.getY(), tool.getConstantReflection());
            } else if (nonConstant instanceof ShiftNode && nonConstant.stamp() instanceof IntegerStamp) {
                if (nonConstant instanceof LeftShiftNode) {
                    LeftShiftNode shift = (LeftShiftNode) nonConstant;
                    if (shift.getY().isConstant()) {
                        int mask = shift.getShiftAmountMask();
                        int amount = shift.getY().asJavaConstant().asInt() & mask;
                        if (shift.getX().getStackKind() == JavaKind.Int) {
                            return new IntegerTestNode(shift.getX(), ConstantNode.forInt(-1 >>> amount));
                        } else {
                            assert shift.getX().getStackKind() == JavaKind.Long;
                            return new IntegerTestNode(shift.getX(), ConstantNode.forLong(-1L >>> amount));
                        }
                    }
                } else if (nonConstant instanceof RightShiftNode) {
                    RightShiftNode shift = (RightShiftNode) nonConstant;
                    if (shift.getY().isConstant() && ((IntegerStamp) shift.getX().stamp()).isPositive()) {
                        int mask = shift.getShiftAmountMask();
                        int amount = shift.getY().asJavaConstant().asInt() & mask;
                        if (shift.getX().getStackKind() == JavaKind.Int) {
                            return new IntegerTestNode(shift.getX(), ConstantNode.forInt(-1 << amount));
                        } else {
                            assert shift.getX().getStackKind() == JavaKind.Long;
                            return new IntegerTestNode(shift.getX(), ConstantNode.forLong(-1L << amount));
                        }
                    }
                } else if (nonConstant instanceof UnsignedRightShiftNode) {
                    UnsignedRightShiftNode shift = (UnsignedRightShiftNode) nonConstant;
                    if (shift.getY().isConstant()) {
                        int mask = shift.getShiftAmountMask();
                        int amount = shift.getY().asJavaConstant().asInt() & mask;
                        if (shift.getX().getStackKind() == JavaKind.Int) {
                            return new IntegerTestNode(shift.getX(), ConstantNode.forInt(-1 << amount));
                        } else {
                            assert shift.getX().getStackKind() == JavaKind.Long;
                            return new IntegerTestNode(shift.getX(), ConstantNode.forLong(-1L << amount));
                        }
                    }
                }
            }
        }
        if (nonConstant instanceof AndNode) {
            /*
             * a & c == c is the same as a & c != 0, if c is a single bit.
             */
            AndNode andNode = (AndNode) nonConstant;
            if (constant instanceof PrimitiveConstant && Long.bitCount(((PrimitiveConstant) constant).asLong()) == 1 && andNode.getY().isConstant() &&
                            andNode.getY().asJavaConstant().equals(constant)) {
                return new LogicNegationNode(new IntegerTestNode(andNode.getX(), andNode.getY()));
            }
        }
        return super.canonicalizeSymmetricConstant(tool, constant, nonConstant, mirrored);
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated) {
        if (!negated) {
            return getX().stamp().join(getY().stamp());
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated) {
        if (!negated) {
            return getX().stamp().join(getY().stamp());
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp && yStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
            if (xStamp.alwaysDistinct(yStamp)) {
                return TriState.FALSE;
            } else if (xStamp.neverDistinct(yStamp)) {
                return TriState.TRUE;
            }
        }
        return TriState.UNKNOWN;
    }
}
