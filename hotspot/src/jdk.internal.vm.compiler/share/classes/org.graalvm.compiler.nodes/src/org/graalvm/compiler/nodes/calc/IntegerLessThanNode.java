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
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "<")
public final class IntegerLessThanNode extends CompareNode implements IterableNodeType {
    public static final NodeClass<IntegerLessThanNode> TYPE = NodeClass.create(IntegerLessThanNode.class);

    public IntegerLessThanNode(ValueNode x, ValueNode y) {
        super(TYPE, Condition.LT, false, x, y);
        assert !x.getStackKind().isNumericFloat() && x.getStackKind() != JavaKind.Object;
        assert !y.getStackKind().isNumericFloat() && y.getStackKind() != JavaKind.Object;
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.LT, x, y, constantReflection, false);
        if (result != null) {
            return result;
        } else {
            result = findSynonym(x, y);
            if (result != null) {
                return result;
            }
            return new IntegerLessThanNode(x, y);
        }
    }

    @Override
    protected ValueNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        PrimitiveConstant primitive = (PrimitiveConstant) constant;
        assert condition() == Condition.LT;
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
        ValueNode synonym = findSynonym(forX, forY);
        if (synonym != null) {
            return synonym;
        }
        if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            if (IntegerStamp.sameSign((IntegerStamp) forX.stamp(), (IntegerStamp) forY.stamp())) {
                return new IntegerBelowNode(forX, forY);
            }
        }
        if (forY.isConstant() && forY.asConstant().isDefaultForKind() && forX instanceof SubNode) {
            // (x - y) < 0 when x - y is known not to underflow == x < y
            SubNode sub = (SubNode) forX;
            IntegerStamp xStamp = (IntegerStamp) sub.getX().stamp();
            IntegerStamp yStamp = (IntegerStamp) sub.getY().stamp();
            long minValue = CodeUtil.minValue(xStamp.getBits());
            long maxValue = CodeUtil.maxValue(xStamp.getBits());

            if (!subtractMayUnderflow(xStamp.lowerBound(), yStamp.upperBound(), minValue) && !subtractMayOverflow(xStamp.upperBound(), yStamp.lowerBound(), maxValue)) {
                return new IntegerLessThanNode(sub.getX(), sub.getY());
            }
        }
        return this;
    }

    private static LogicNode findSynonym(ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.contradiction();
        } else if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) forX.stamp();
            IntegerStamp yStamp = (IntegerStamp) forY.stamp();
            if (xStamp.upperBound() < yStamp.lowerBound()) {
                return LogicConstantNode.tautology();
            } else if (xStamp.lowerBound() >= yStamp.upperBound()) {
                return LogicConstantNode.contradiction();
            }
        }
        return null;
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

    @Override
    public Stamp getSucceedingStampForX(boolean negated) {
        Stamp xStampGeneric = getX().stamp();
        Stamp yStampGeneric = getY().stamp();
        if (xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            int bits = xStamp.getBits();
            if (yStampGeneric instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                assert yStamp.getBits() == bits;
                if (negated) {
                    // x >= y
                    long xLowerBound = xStamp.lowerBound();
                    long yLowerBound = yStamp.lowerBound();
                    if (yLowerBound > xLowerBound) {
                        return StampFactory.forIntegerWithMask(bits, yLowerBound, xStamp.upperBound(), xStamp);
                    }
                } else {
                    // x < y
                    long xUpperBound = xStamp.upperBound();
                    long yUpperBound = yStamp.upperBound();
                    if (yUpperBound == CodeUtil.minValue(bits)) {
                        return null;
                    } else if (yUpperBound <= xUpperBound) {
                        assert yUpperBound != CodeUtil.minValue(bits);
                        return StampFactory.forIntegerWithMask(bits, xStamp.lowerBound(), yUpperBound - 1, xStamp);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated) {
        Stamp xStampGeneric = getX().stamp();
        Stamp yStampGeneric = getY().stamp();
        if (xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            int bits = xStamp.getBits();
            if (yStampGeneric instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                assert yStamp.getBits() == bits;
                if (negated) {
                    // y <= x
                    long xUpperBound = xStamp.upperBound();
                    long yUpperBound = yStamp.upperBound();
                    if (xUpperBound < yUpperBound) {
                        return StampFactory.forIntegerWithMask(bits, yStamp.lowerBound(), xUpperBound, yStamp);
                    }
                } else {
                    // y > x
                    long xLowerBound = xStamp.lowerBound();
                    long yLowerBound = yStamp.lowerBound();
                    if (xLowerBound == CodeUtil.maxValue(bits)) {
                        return null;
                    } else if (xLowerBound >= yLowerBound) {
                        assert xLowerBound != CodeUtil.maxValue(bits);
                        return StampFactory.forIntegerWithMask(bits, xLowerBound + 1, yStamp.upperBound(), yStamp);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp && yStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
            if (xStamp.upperBound() < yStamp.lowerBound()) {
                return TriState.TRUE;
            }
            if (xStamp.lowerBound() >= yStamp.upperBound()) {
                return TriState.FALSE;
            }
        }
        return TriState.UNKNOWN;
    }
}
