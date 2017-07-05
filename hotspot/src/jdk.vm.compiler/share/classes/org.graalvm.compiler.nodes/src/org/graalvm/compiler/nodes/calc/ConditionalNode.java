/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;
import static org.graalvm.compiler.nodes.calc.CompareNode.createCompareNode;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;

/**
 * The {@code ConditionalNode} class represents a comparison that yields one of two values. Note
 * that these nodes are not built directly from the bytecode but are introduced by canonicalization.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_2)
public final class ConditionalNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    public static final NodeClass<ConditionalNode> TYPE = NodeClass.create(ConditionalNode.class);
    @Input(InputType.Condition) LogicNode condition;
    @Input ValueNode trueValue;
    @Input ValueNode falseValue;

    public LogicNode condition() {
        return condition;
    }

    public ConditionalNode(LogicNode condition) {
        this(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()));
    }

    public ConditionalNode(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        super(TYPE, trueValue.stamp().meet(falseValue.stamp()));
        assert trueValue.stamp().isCompatible(falseValue.stamp());
        this.condition = condition;
        this.trueValue = trueValue;
        this.falseValue = falseValue;
    }

    public static ValueNode create(LogicNode condition) {
        return create(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()));
    }

    public static ValueNode create(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        ValueNode synonym = findSynonym(condition, trueValue, falseValue);
        if (synonym != null) {
            return synonym;
        }
        return new ConditionalNode(condition, trueValue, falseValue);
    }

    @Override
    public boolean inferStamp() {
        Stamp valueStamp = trueValue.stamp().meet(falseValue.stamp());
        if (condition instanceof IntegerLessThanNode) {
            IntegerLessThanNode lessThan = (IntegerLessThanNode) condition;
            if (lessThan.getX() == trueValue && lessThan.getY() == falseValue) {
                // this encodes a min operation
                JavaConstant constant = lessThan.getX().asJavaConstant();
                if (constant == null) {
                    constant = lessThan.getY().asJavaConstant();
                }
                if (constant != null) {
                    IntegerStamp bounds = StampFactory.forInteger(constant.getJavaKind(), constant.getJavaKind().getMinValue(), constant.asLong());
                    valueStamp = valueStamp.join(bounds);
                }
            } else if (lessThan.getX() == falseValue && lessThan.getY() == trueValue) {
                // this encodes a max operation
                JavaConstant constant = lessThan.getX().asJavaConstant();
                if (constant == null) {
                    constant = lessThan.getY().asJavaConstant();
                }
                if (constant != null) {
                    IntegerStamp bounds = StampFactory.forInteger(constant.getJavaKind(), constant.asLong(), constant.getJavaKind().getMaxValue());
                    valueStamp = valueStamp.join(bounds);
                }
            }

        }
        return updateStamp(valueStamp);
    }

    public ValueNode trueValue() {
        return trueValue;
    }

    public ValueNode falseValue() {
        return falseValue;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        ValueNode synonym = findSynonym(condition, trueValue(), falseValue());
        if (synonym != null) {
            return synonym;
        }

        ValueNode result = canonicalizeConditional(condition, trueValue(), falseValue(), stamp);
        if (result != null) {
            return result;
        }

        return this;
    }

    public static ValueNode canonicalizeConditional(LogicNode condition, ValueNode trueValue, ValueNode falseValue, Stamp stamp) {
        // this optimizes the case where a value that can only be 0 or 1 is materialized to 0 or 1
        if (trueValue.isConstant() && falseValue.isConstant() && condition instanceof IntegerEqualsNode) {
            IntegerEqualsNode equals = (IntegerEqualsNode) condition;
            if (equals.getY().isConstant() && equals.getY().asConstant().equals(JavaConstant.INT_0) && equals.getX().stamp() instanceof IntegerStamp) {
                IntegerStamp equalsXStamp = (IntegerStamp) equals.getX().stamp();
                if (equalsXStamp.upMask() == 1) {
                    if (trueValue.asConstant().equals(JavaConstant.INT_0) && falseValue.asConstant().equals(JavaConstant.INT_1)) {
                        return IntegerConvertNode.convertUnsigned(equals.getX(), stamp);
                    }
                }
            }
        }
        if (condition instanceof CompareNode && ((CompareNode) condition).isIdentityComparison()) {
            // optimize the pattern (x == y) ? x : y
            CompareNode compare = (CompareNode) condition;
            if ((compare.getX() == trueValue && compare.getY() == falseValue) || (compare.getX() == falseValue && compare.getY() == trueValue)) {
                return falseValue;
            }
        }
        if (trueValue == falseValue) {
            return trueValue;
        }

        if (condition instanceof IntegerLessThanNode && trueValue.stamp() instanceof IntegerStamp) {
            /*
             * Convert a conditional add ((x < 0) ? (x + y) : x) into (x + (y & (x >> (bits - 1))))
             * to avoid the test.
             */
            IntegerLessThanNode lt = (IntegerLessThanNode) condition;
            if (lt.getY().isConstant() && lt.getY().asConstant().isDefaultForKind()) {
                if (falseValue == lt.getX()) {
                    if (trueValue instanceof AddNode) {
                        AddNode add = (AddNode) trueValue;
                        if (add.getX() == falseValue) {
                            int bits = ((IntegerStamp) trueValue.stamp()).getBits();
                            ValueNode shift = new RightShiftNode(lt.getX(), ConstantNode.forIntegerBits(32, bits - 1));
                            ValueNode and = new AndNode(shift, add.getY());
                            return new AddNode(add.getX(), and);
                        }
                    }
                }
            }
        }

        return null;
    }

    private static ValueNode findSynonym(ValueNode condition, ValueNode trueValue, ValueNode falseValue) {
        if (condition instanceof LogicNegationNode) {
            LogicNegationNode negated = (LogicNegationNode) condition;
            return ConditionalNode.create(negated.getValue(), falseValue, trueValue);
        }
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            if (c.getValue()) {
                return trueValue;
            } else {
                return falseValue;
            }
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.emitConditional(this);
    }

    public ConditionalNode(StructuredGraph graph, Condition condition, ValueNode x, ValueNode y) {
        this(createCompareNode(graph, condition, x, y, null));
    }
}
