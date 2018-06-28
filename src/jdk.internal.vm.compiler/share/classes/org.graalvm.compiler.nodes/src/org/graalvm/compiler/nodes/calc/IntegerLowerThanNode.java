/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.CodeUtil.mask;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.TriState;

/**
 * Common super-class for "a < b" comparisons both {@linkplain IntegerLowerThanNode signed} and
 * {@linkplain IntegerBelowNode unsigned}.
 */
@NodeInfo()
public abstract class IntegerLowerThanNode extends CompareNode {
    public static final NodeClass<IntegerLowerThanNode> TYPE = NodeClass.create(IntegerLowerThanNode.class);
    private final LowerOp op;

    protected IntegerLowerThanNode(NodeClass<? extends CompareNode> c, ValueNode x, ValueNode y, LowerOp op) {
        super(c, op.getCondition(), false, x, y);
        this.op = op;
    }

    protected LowerOp getOp() {
        return op;
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStampGeneric, Stamp yStampGeneric) {
        return getSucceedingStampForX(negated, !negated, xStampGeneric, yStampGeneric, getX(), getY());
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStampGeneric, Stamp yStampGeneric) {
        return getSucceedingStampForX(!negated, !negated, yStampGeneric, xStampGeneric, getY(), getX());
    }

    private Stamp getSucceedingStampForX(boolean mirror, boolean strict, Stamp xStampGeneric, Stamp yStampGeneric, ValueNode forX, ValueNode forY) {
        Stamp s = getSucceedingStampForX(mirror, strict, xStampGeneric, yStampGeneric);
        if (s != null && s.isUnrestricted()) {
            s = null;
        }
        if (forY instanceof AddNode && xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            AddNode addNode = (AddNode) forY;
            IntegerStamp aStamp = null;
            if (addNode.getX() == forX && addNode.getY().stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                // x < x + a
                aStamp = (IntegerStamp) addNode.getY().stamp(NodeView.DEFAULT);
            } else if (addNode.getY() == forX && addNode.getX().stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                // x < a + x
                aStamp = (IntegerStamp) addNode.getX().stamp(NodeView.DEFAULT);
            }
            if (aStamp != null) {
                IntegerStamp result = getOp().getSucceedingStampForXLowerXPlusA(mirror, strict, aStamp);
                result = (IntegerStamp) xStamp.tryImproveWith(result);
                if (result != null) {
                    if (s != null) {
                        s = s.improveWith(result);
                    } else {
                        s = result;
                    }
                }
            }
        }
        return s;
    }

    private Stamp getSucceedingStampForX(boolean mirror, boolean strict, Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            if (yStampGeneric instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                assert yStamp.getBits() == xStamp.getBits();
                Stamp s = getOp().getSucceedingStampForX(xStamp, yStamp, mirror, strict);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        return getOp().tryFold(xStampGeneric, yStampGeneric);
    }

    public abstract static class LowerOp extends CompareOp {
        @Override
        public LogicNode canonical(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, CanonicalCondition condition,
                        boolean unorderedIsTrue, ValueNode forX, ValueNode forY, NodeView view) {
            LogicNode result = super.canonical(constantReflection, metaAccess, options, smallestCompareWidth, condition, unorderedIsTrue, forX, forY, view);
            if (result != null) {
                return result;
            }
            LogicNode synonym = findSynonym(forX, forY, view);
            if (synonym != null) {
                return synonym;
            }
            return null;
        }

        protected abstract long upperBound(IntegerStamp stamp);

        protected abstract long lowerBound(IntegerStamp stamp);

        protected abstract int compare(long a, long b);

        protected abstract long min(long a, long b);

        protected abstract long max(long a, long b);

        protected long min(long a, long b, int bits) {
            return min(cast(a, bits), cast(b, bits));
        }

        protected long max(long a, long b, int bits) {
            return max(cast(a, bits), cast(b, bits));
        }

        protected abstract long cast(long a, int bits);

        protected abstract long minValue(int bits);

        protected abstract long maxValue(int bits);

        protected abstract IntegerStamp forInteger(int bits, long min, long max);

        protected abstract CanonicalCondition getCondition();

        protected abstract IntegerLowerThanNode createNode(ValueNode x, ValueNode y);

        public LogicNode create(ValueNode x, ValueNode y, NodeView view) {
            LogicNode result = CompareNode.tryConstantFoldPrimitive(getCondition(), x, y, false, view);
            if (result != null) {
                return result;
            } else {
                result = findSynonym(x, y, view);
                if (result != null) {
                    return result;
                }
                return createNode(x, y);
            }
        }

        protected LogicNode findSynonym(ValueNode forX, ValueNode forY, NodeView view) {
            if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
                return LogicConstantNode.contradiction();
            }
            TriState fold = tryFold(forX.stamp(view), forY.stamp(view));
            if (fold.isTrue()) {
                return LogicConstantNode.tautology();
            } else if (fold.isFalse()) {
                return LogicConstantNode.contradiction();
            }
            if (forY.stamp(view) instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) forY.stamp(view);
                int bits = yStamp.getBits();
                if (forX.isJavaConstant() && !forY.isConstant()) {
                    // bring the constant on the right
                    long xValue = forX.asJavaConstant().asLong();
                    if (xValue != maxValue(bits)) {
                        // c < x <=> !(c >= x) <=> !(x <= c) <=> !(x < c + 1)
                        return LogicNegationNode.create(create(forY, ConstantNode.forIntegerStamp(yStamp, xValue + 1), view));
                    }
                }
                if (forY.isJavaConstant()) {
                    long yValue = forY.asJavaConstant().asLong();
                    if (yValue == maxValue(bits)) {
                        // x < MAX <=> x != MAX
                        return LogicNegationNode.create(IntegerEqualsNode.create(forX, forY, view));
                    }
                    if (yValue == minValue(bits) + 1) {
                        // x < MIN + 1 <=> x <= MIN <=> x == MIN
                        return IntegerEqualsNode.create(forX, ConstantNode.forIntegerStamp(yStamp, minValue(bits)), view);
                    }
                } else if (forY instanceof AddNode) {
                    AddNode addNode = (AddNode) forY;
                    LogicNode canonical = canonicalizeXLowerXPlusA(forX, addNode, false, true, view);
                    if (canonical != null) {
                        return canonical;
                    }
                }
                if (forX instanceof AddNode) {
                    AddNode addNode = (AddNode) forX;
                    LogicNode canonical = canonicalizeXLowerXPlusA(forY, addNode, true, false, view);
                    if (canonical != null) {
                        return canonical;
                    }
                }
            }
            return null;
        }

        private LogicNode canonicalizeXLowerXPlusA(ValueNode forX, AddNode addNode, boolean mirrored, boolean strict, NodeView view) {
            // x < x + a
            IntegerStamp succeedingXStamp;
            boolean exact;
            if (addNode.getX() == forX && addNode.getY().stamp(view) instanceof IntegerStamp) {
                IntegerStamp aStamp = (IntegerStamp) addNode.getY().stamp(view);
                succeedingXStamp = getSucceedingStampForXLowerXPlusA(mirrored, strict, aStamp);
                exact = aStamp.lowerBound() == aStamp.upperBound();
            } else if (addNode.getY() == forX && addNode.getX().stamp(view) instanceof IntegerStamp) {
                IntegerStamp aStamp = (IntegerStamp) addNode.getX().stamp(view);
                succeedingXStamp = getSucceedingStampForXLowerXPlusA(mirrored, strict, aStamp);
                exact = aStamp.lowerBound() == aStamp.upperBound();
            } else {
                return null;
            }
            if (succeedingXStamp.join(forX.stamp(view)).isEmpty()) {
                return LogicConstantNode.contradiction();
            } else if (exact && !succeedingXStamp.isEmpty()) {
                int bits = succeedingXStamp.getBits();
                if (compare(lowerBound(succeedingXStamp), minValue(bits)) > 0) {
                    assert upperBound(succeedingXStamp) == maxValue(bits);
                    // x must be in [L..MAX] <=> x >= L <=> !(x < L)
                    return LogicNegationNode.create(create(forX, ConstantNode.forIntegerStamp(succeedingXStamp, lowerBound(succeedingXStamp)), view));
                } else if (compare(upperBound(succeedingXStamp), maxValue(bits)) < 0) {
                    // x must be in [MIN..H] <=> x <= H <=> !(H < x)
                    return LogicNegationNode.create(create(ConstantNode.forIntegerStamp(succeedingXStamp, upperBound(succeedingXStamp)), forX, view));
                }
            }
            return null;
        }

        protected TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
            if (xStampGeneric instanceof IntegerStamp && yStampGeneric instanceof IntegerStamp) {
                IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                if (compare(upperBound(xStamp), lowerBound(yStamp)) < 0) {
                    return TriState.TRUE;
                }
                if (compare(lowerBound(xStamp), upperBound(yStamp)) >= 0) {
                    return TriState.FALSE;
                }
            }
            return TriState.UNKNOWN;
        }

        protected IntegerStamp getSucceedingStampForX(IntegerStamp xStamp, IntegerStamp yStamp, boolean mirror, boolean strict) {
            int bits = xStamp.getBits();
            assert yStamp.getBits() == bits;
            if (mirror) {
                long low = lowerBound(yStamp);
                if (strict) {
                    if (low == maxValue(bits)) {
                        return null;
                    }
                    low += 1;
                }
                if (compare(low, lowerBound(xStamp)) > 0 || upperBound(xStamp) != (xStamp.upperBound() & mask(xStamp.getBits()))) {
                    return forInteger(bits, low, upperBound(xStamp));
                }
            } else {
                // x < y, i.e., x < y <= Y_UPPER_BOUND so x <= Y_UPPER_BOUND - 1
                long low = upperBound(yStamp);
                if (strict) {
                    if (low == minValue(bits)) {
                        return null;
                    }
                    low -= 1;
                }
                if (compare(low, upperBound(xStamp)) < 0 || lowerBound(xStamp) != (xStamp.lowerBound() & mask(xStamp.getBits()))) {
                    return forInteger(bits, lowerBound(xStamp), low);
                }
            }
            return null;
        }

        protected IntegerStamp getSucceedingStampForXLowerXPlusA(boolean mirrored, boolean strict, IntegerStamp a) {
            int bits = a.getBits();
            long min = minValue(bits);
            long max = maxValue(bits);
            /*
             * if x < x + a <=> x + a didn't overflow:
             *
             * x is outside ]MAX - a, MAX], i.e., inside [MIN, MAX - a]
             *
             * if a is negative those bounds wrap around correctly.
             *
             * If a is exactly zero this gives an unbounded stamp (any integer) in the positive case
             * and an empty stamp in the negative case: if x |<| x is true, then either x has no
             * value or any value...
             *
             * This does not use upper/lowerBound from LowerOp because it's about the (signed)
             * addition not the comparison.
             */
            if (mirrored) {
                if (a.contains(0)) {
                    // a may be zero
                    return a.unrestricted();
                }
                return forInteger(bits, min(max - a.lowerBound() + 1, max - a.upperBound() + 1, bits), max);
            } else {
                long aLower = a.lowerBound();
                long aUpper = a.upperBound();
                if (strict) {
                    if (aLower == 0) {
                        aLower = 1;
                    }
                    if (aUpper == 0) {
                        aUpper = -1;
                    }
                    if (aLower > aUpper) {
                        // impossible
                        return a.empty();
                    }
                }
                if (aLower < 0 && aUpper > 0) {
                    // a may be zero
                    return a.unrestricted();
                }
                return forInteger(bits, min, max(max - aLower, max - aUpper, bits));
            }
        }
    }
}
