/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/* TODO (thomaswue/gdub) For high-level optimization purpose the compare node should be a boolean *value* (it is currently only a helper node)
 * But in the back-end the comparison should not always be materialized (for example in x86 the comparison result will not be in a register but in a flag)
 *
 * Compare should probably be made a value (so that it can be canonicalized for example) and in later stages some Compare usage should be transformed
 * into variants that do not materialize the value (CompareIf, CompareGuard...)
 */
@NodeInfo(cycles = CYCLES_1)
public abstract class CompareNode extends BinaryOpLogicNode implements Canonicalizable.Binary<ValueNode> {

    public static final NodeClass<CompareNode> TYPE = NodeClass.create(CompareNode.class);
    protected final Condition condition;
    protected final boolean unorderedIsTrue;

    /**
     * Constructs a new Compare instruction.
     *
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     */
    protected CompareNode(NodeClass<? extends CompareNode> c, Condition condition, boolean unorderedIsTrue, ValueNode x, ValueNode y) {
        super(c, x, y);
        this.condition = condition;
        this.unorderedIsTrue = unorderedIsTrue;
    }

    /**
     * Gets the condition (comparison operation) for this instruction.
     *
     * @return the condition
     */
    public final Condition condition() {
        return condition;
    }

    /**
     * Checks whether unordered inputs mean true or false (only applies to float operations).
     *
     * @return {@code true} if unordered inputs produce true
     */
    public final boolean unorderedIsTrue() {
        return this.unorderedIsTrue;
    }

    private ValueNode optimizeConditional(Constant constant, ConditionalNode conditionalNode, ConstantReflectionProvider constantReflection, Condition cond) {
        Constant trueConstant = conditionalNode.trueValue().asConstant();
        Constant falseConstant = conditionalNode.falseValue().asConstant();

        if (falseConstant != null && trueConstant != null && constantReflection != null) {
            boolean trueResult = cond.foldCondition(trueConstant, constant, constantReflection, unorderedIsTrue());
            boolean falseResult = cond.foldCondition(falseConstant, constant, constantReflection, unorderedIsTrue());

            if (trueResult == falseResult) {
                return LogicConstantNode.forBoolean(trueResult);
            } else {
                if (trueResult) {
                    assert falseResult == false;
                    return conditionalNode.condition();
                } else {
                    assert falseResult == true;
                    return LogicNegationNode.create(conditionalNode.condition());

                }
            }
        }
        return this;
    }

    protected ValueNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        throw new GraalError("NormalizeCompareNode connected to %s (%s %s %s)", this, constant, normalizeNode, mirrored);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        LogicNode constantCondition = tryConstantFold(condition(), forX, forY, constantReflection, unorderedIsTrue());
        if (constantCondition != null) {
            return constantCondition;
        }
        ValueNode result;
        if (forX.isConstant()) {
            if ((result = canonicalizeSymmetricConstant(tool, forX.asConstant(), forY, true)) != this) {
                return result;
            }
        } else if (forY.isConstant()) {
            if ((result = canonicalizeSymmetricConstant(tool, forY.asConstant(), forX, false)) != this) {
                return result;
            }
        } else if (forX instanceof ConvertNode && forY instanceof ConvertNode) {
            ConvertNode convertX = (ConvertNode) forX;
            ConvertNode convertY = (ConvertNode) forY;
            if (convertX.preservesOrder(condition()) && convertY.preservesOrder(condition()) && convertX.getValue().stamp().isCompatible(convertY.getValue().stamp())) {
                boolean supported = true;
                if (convertX.getValue().stamp() instanceof IntegerStamp) {
                    IntegerStamp intStamp = (IntegerStamp) convertX.getValue().stamp();
                    supported = tool.supportSubwordCompare(intStamp.getBits());
                }

                if (supported) {
                    boolean multiUsage = (convertX.asNode().getUsageCount() > 1 || convertY.asNode().getUsageCount() > 1);
                    if ((forX instanceof ZeroExtendNode || forX instanceof SignExtendNode) && multiUsage) {
                        // Do not perform for zero or sign extend if there are multiple usages of
                        // the value.
                        return this;
                    }
                    return duplicateModified(convertX.getValue(), convertY.getValue());
                }
            }
        }
        return this;
    }

    public static LogicNode tryConstantFold(Condition condition, ValueNode forX, ValueNode forY, ConstantReflectionProvider constantReflection, boolean unorderedIsTrue) {
        if (forX.isConstant() && forY.isConstant() && constantReflection != null) {
            return LogicConstantNode.forBoolean(condition.foldCondition(forX.asConstant(), forY.asConstant(), constantReflection, unorderedIsTrue));
        }
        return null;
    }

    /**
     * Does this operation represent an identity check such that for x == y, x is exactly the same
     * thing as y. This is generally true except for some floating point comparisons.
     *
     * @return true for identity comparisons
     */
    public boolean isIdentityComparison() {
        return condition == Condition.EQ;
    }

    protected abstract LogicNode duplicateModified(ValueNode newX, ValueNode newY);

    protected ValueNode canonicalizeSymmetricConstant(CanonicalizerTool tool, Constant constant, ValueNode nonConstant, boolean mirrored) {
        if (nonConstant instanceof ConditionalNode) {
            return optimizeConditional(constant, (ConditionalNode) nonConstant, tool.getConstantReflection(), mirrored ? condition().mirror() : condition());
        } else if (nonConstant instanceof NormalizeCompareNode) {
            return optimizeNormalizeCmp(constant, (NormalizeCompareNode) nonConstant, mirrored);
        } else if (nonConstant instanceof ConvertNode) {
            ConvertNode convert = (ConvertNode) nonConstant;
            boolean multiUsage = (convert.asNode().getUsageCount() > 1 && convert.getValue().getUsageCount() == 1);
            if ((convert instanceof ZeroExtendNode || convert instanceof SignExtendNode) && multiUsage) {
                // Do not perform for zero or sign extend if it could introduce
                // new live values.
                return this;
            }

            boolean supported = true;
            if (convert.getValue().stamp() instanceof IntegerStamp) {
                IntegerStamp intStamp = (IntegerStamp) convert.getValue().stamp();
                supported = tool.supportSubwordCompare(intStamp.getBits());
            }

            if (supported) {
                ConstantNode newConstant = canonicalConvertConstant(tool, convert, constant);
                if (newConstant != null) {
                    if (mirrored) {
                        return duplicateModified(newConstant, convert.getValue());
                    } else {
                        return duplicateModified(convert.getValue(), newConstant);
                    }
                }
            }
        }
        return this;
    }

    private ConstantNode canonicalConvertConstant(CanonicalizerTool tool, ConvertNode convert, Constant constant) {
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        if (convert.preservesOrder(condition(), constant, constantReflection)) {
            Constant reverseConverted = convert.reverse(constant, constantReflection);
            if (reverseConverted != null && convert.convert(reverseConverted, constantReflection).equals(constant)) {
                if (GeneratePIC.getValue()) {
                    // We always want uncompressed constants
                    return null;
                }
                return ConstantNode.forConstant(convert.getValue().stamp(), reverseConverted, tool.getMetaAccess());
            }
        }
        return null;
    }

    public static LogicNode createCompareNode(StructuredGraph graph, Condition condition, ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = createCompareNode(condition, x, y, constantReflection);
        return (result.graph() == null ? graph.unique(result) : result);
    }

    public static LogicNode createCompareNode(Condition condition, ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        assert x.getStackKind() == y.getStackKind();
        assert condition.isCanonical() : "condition is not canonical: " + condition;
        assert !x.getStackKind().isNumericFloat();

        LogicNode comparison;
        if (condition == Condition.EQ) {
            if (x.stamp() instanceof AbstractObjectStamp) {
                comparison = ObjectEqualsNode.create(x, y, constantReflection);
            } else if (x.stamp() instanceof AbstractPointerStamp) {
                comparison = PointerEqualsNode.create(x, y);
            } else {
                assert x.getStackKind().isNumericInteger();
                comparison = IntegerEqualsNode.create(x, y, constantReflection);
            }
        } else if (condition == Condition.LT) {
            assert x.getStackKind().isNumericInteger();
            comparison = IntegerLessThanNode.create(x, y, constantReflection);
        } else {
            assert condition == Condition.BT;
            assert x.getStackKind().isNumericInteger();
            comparison = IntegerBelowNode.create(x, y, constantReflection);
        }

        return comparison;
    }
}
