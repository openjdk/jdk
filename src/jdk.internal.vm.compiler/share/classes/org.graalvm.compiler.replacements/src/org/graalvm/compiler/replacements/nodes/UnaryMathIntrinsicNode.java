/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(nameTemplate = "MathIntrinsic#{p#operation/s}", cycles = CYCLES_64, size = SIZE_1)
public final class UnaryMathIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, Lowerable {

    public static final NodeClass<UnaryMathIntrinsicNode> TYPE = NodeClass.create(UnaryMathIntrinsicNode.class);
    protected final UnaryOperation operation;

    public enum UnaryOperation {
        LOG(new ForeignCallSignature("arithmeticLog", double.class, double.class)),
        LOG10(new ForeignCallSignature("arithmeticLog10", double.class, double.class)),
        SIN(new ForeignCallSignature("arithmeticSin", double.class, double.class)),
        COS(new ForeignCallSignature("arithmeticCos", double.class, double.class)),
        TAN(new ForeignCallSignature("arithmeticTan", double.class, double.class)),
        EXP(new ForeignCallSignature("arithmeticExp", double.class, double.class));

        public final ForeignCallSignature foreignCallSignature;

        UnaryOperation(ForeignCallSignature foreignCallSignature) {
            this.foreignCallSignature = foreignCallSignature;
        }

        public double compute(double value) {
            switch (this) {
                case LOG:
                    return Math.log(value);
                case LOG10:
                    return Math.log10(value);
                case EXP:
                    return Math.exp(value);
                case SIN:
                    return Math.sin(value);
                case COS:
                    return Math.cos(value);
                case TAN:
                    return Math.tan(value);
                default:
                    throw new GraalError("unknown op %s", this);
            }
        }

        public Stamp computeStamp(Stamp valueStamp) {
            if (valueStamp instanceof FloatStamp) {
                FloatStamp floatStamp = (FloatStamp) valueStamp;
                switch (this) {
                    case COS:
                    case SIN: {
                        boolean nonNaN = floatStamp.lowerBound() != Double.NEGATIVE_INFINITY && floatStamp.upperBound() != Double.POSITIVE_INFINITY && floatStamp.isNonNaN();
                        return StampFactory.forFloat(JavaKind.Double, -1.0, 1.0, nonNaN);
                    }
                    case TAN: {
                        boolean nonNaN = floatStamp.lowerBound() != Double.NEGATIVE_INFINITY && floatStamp.upperBound() != Double.POSITIVE_INFINITY && floatStamp.isNonNaN();
                        return StampFactory.forFloat(JavaKind.Double, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, nonNaN);
                    }
                    case LOG:
                    case LOG10: {
                        double lowerBound = compute(floatStamp.lowerBound());
                        double upperBound = compute(floatStamp.upperBound());
                        if (floatStamp.contains(0.0)) {
                            // 0.0 and -0.0 infinity produces -Inf
                            lowerBound = Double.NEGATIVE_INFINITY;
                        }
                        boolean nonNaN = floatStamp.lowerBound() >= 0.0 && floatStamp.isNonNaN();
                        return StampFactory.forFloat(JavaKind.Double, lowerBound, upperBound, nonNaN);
                    }
                    case EXP: {
                        double lowerBound = Math.exp(floatStamp.lowerBound());
                        double upperBound = Math.exp(floatStamp.upperBound());
                        boolean nonNaN = floatStamp.isNonNaN();
                        return StampFactory.forFloat(JavaKind.Double, lowerBound, upperBound, nonNaN);
                    }

                }
            }
            return StampFactory.forKind(JavaKind.Double);
        }

    }

    public UnaryOperation getOperation() {
        return operation;
    }

    public static ValueNode create(ValueNode value, UnaryOperation op) {
        ValueNode c = tryConstantFold(value, op);
        if (c != null) {
            return c;
        }
        return new UnaryMathIntrinsicNode(value, op);
    }

    protected static ValueNode tryConstantFold(ValueNode value, UnaryOperation op) {
        if (value.isConstant()) {
            return ConstantNode.forDouble(op.compute(value.asJavaConstant().asDouble()));
        }
        return null;
    }

    protected UnaryMathIntrinsicNode(ValueNode value, UnaryOperation op) {
        super(TYPE, op.computeStamp(value.stamp(NodeView.DEFAULT)), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == 64;
        this.operation = op;
    }

    @Override
    public Stamp foldStamp(Stamp valueStamp) {
        return getOperation().computeStamp(valueStamp);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        // We can only reach here in the math stubs
        Value input = nodeValueMap.operand(getValue());
        Value result;
        switch (getOperation()) {
            case LOG:
                result = gen.emitMathLog(input, false);
                break;
            case LOG10:
                result = gen.emitMathLog(input, true);
                break;
            case EXP:
                result = gen.emitMathExp(input);
                break;
            case SIN:
                result = gen.emitMathSin(input);
                break;
            case COS:
                result = gen.emitMathCos(input);
                break;
            case TAN:
                result = gen.emitMathTan(input);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        nodeValueMap.setResult(this, result);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode c = tryConstantFold(forValue, getOperation());
        if (c != null) {
            return c;
        }
        return this;
    }

    @NodeIntrinsic
    public static native double compute(double value, @ConstantNodeParameter UnaryOperation op);

}
