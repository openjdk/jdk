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
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "|<|")
public final class IntegerBelowNode extends CompareNode {
    public static final NodeClass<IntegerBelowNode> TYPE = NodeClass.create(IntegerBelowNode.class);

    public IntegerBelowNode(ValueNode x, ValueNode y) {
        super(TYPE, Condition.BT, false, x, y);
        assert x.stamp() instanceof IntegerStamp;
        assert y.stamp() instanceof IntegerStamp;
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.BT, x, y, constantReflection, false);
        if (result != null) {
            return result;
        } else {
            result = findSynonym(x, y);
            if (result != null) {
                return result;
            }
            return new IntegerBelowNode(x, y);
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = super.canonical(tool, forX, forY);
        if (result != this) {
            return result;
        }
        LogicNode synonym = findSynonym(forX, forY);
        if (synonym != null) {
            return synonym;
        }
        if (forX.isConstant() && forX.asJavaConstant().asLong() == 0) {
            // 0 |<| y is the same as 0 != y
            return LogicNegationNode.create(CompareNode.createCompareNode(Condition.EQ, forX, forY, tool.getConstantReflection()));
        }
        return this;
    }

    private static LogicNode findSynonym(ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.contradiction();
        } else if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) forX.stamp();
            IntegerStamp yStamp = (IntegerStamp) forY.stamp();
            if (yStamp.isPositive()) {
                if (xStamp.isPositive() && xStamp.upperBound() < yStamp.lowerBound()) {
                    return LogicConstantNode.tautology();
                } else if (xStamp.isStrictlyNegative() || xStamp.lowerBound() >= yStamp.upperBound()) {
                    return LogicConstantNode.contradiction();
                }
            }
        }
        return null;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        return new IntegerBelowNode(newX, newY);
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
                    if (xStamp.isPositive() && yStamp.isPositive()) {
                        long xLowerBound = xStamp.lowerBound();
                        long yLowerBound = yStamp.lowerBound();
                        if (yLowerBound > xLowerBound) {
                            return StampFactory.forIntegerWithMask(bits, yLowerBound, xStamp.upperBound(), xStamp);
                        }
                    }
                } else {
                    // x < y
                    if (yStamp.isStrictlyPositive()) {
                        // x >= 0 && x < y
                        long xUpperBound = xStamp.upperBound();
                        long yUpperBound = yStamp.upperBound();
                        if (yUpperBound <= xUpperBound || !xStamp.isPositive()) {
                            return StampFactory.forIntegerWithMask(bits, Math.max(0, xStamp.lowerBound()), Math.min(xUpperBound, yUpperBound - 1), xStamp);
                        }
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
                    if (xStamp.isPositive()) {
                        long xUpperBound = xStamp.upperBound();
                        long yUpperBound = yStamp.upperBound();
                        if (xUpperBound < yUpperBound || !yStamp.isPositive()) {
                            return StampFactory.forIntegerWithMask(bits, Math.max(0, yStamp.lowerBound()), Math.min(xUpperBound, yUpperBound), yStamp);
                        }
                    }
                } else {
                    // y > x
                    if (xStamp.isPositive() && yStamp.isPositive()) {
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
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            if (yStampGeneric instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                if (yStamp.isPositive()) {
                    if (xStamp.isPositive() && xStamp.upperBound() < yStamp.lowerBound()) {
                        return TriState.TRUE;
                    } else if (xStamp.isStrictlyNegative() || xStamp.lowerBound() >= yStamp.upperBound()) {
                        return TriState.FALSE;
                    }
                }
            }
        }
        return TriState.UNKNOWN;
    }
}
