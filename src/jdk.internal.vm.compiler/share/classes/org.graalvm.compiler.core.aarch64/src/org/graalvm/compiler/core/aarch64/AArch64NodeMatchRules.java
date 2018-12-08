/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.core.aarch64;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.ComplexMatchResult;
import org.graalvm.compiler.core.match.MatchRule;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.NotNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.memory.Access;

import jdk.vm.ci.aarch64.AArch64Kind;

public class AArch64NodeMatchRules extends NodeMatchRules {
    private static final EconomicMap<Class<? extends Node>, AArch64ArithmeticOp> nodeOpMap;

    private static final EconomicMap<Class<? extends BinaryNode>, AArch64MacroAssembler.ShiftType> shiftTypeMap;

    static {
        nodeOpMap = EconomicMap.create(Equivalence.IDENTITY, 5);
        nodeOpMap.put(AddNode.class, AArch64ArithmeticOp.ADD);
        nodeOpMap.put(SubNode.class, AArch64ArithmeticOp.SUB);
        nodeOpMap.put(AndNode.class, AArch64ArithmeticOp.AND);
        nodeOpMap.put(OrNode.class, AArch64ArithmeticOp.OR);
        nodeOpMap.put(XorNode.class, AArch64ArithmeticOp.XOR);

        shiftTypeMap = EconomicMap.create(Equivalence.IDENTITY, 3);
        shiftTypeMap.put(LeftShiftNode.class, AArch64MacroAssembler.ShiftType.LSL);
        shiftTypeMap.put(RightShiftNode.class, AArch64MacroAssembler.ShiftType.ASR);
        shiftTypeMap.put(UnsignedRightShiftNode.class, AArch64MacroAssembler.ShiftType.LSR);
    }

    public AArch64NodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
    }

    protected LIRFrameState getState(Access access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    protected AArch64Kind getMemoryKind(Access access) {
        return (AArch64Kind) gen.getLIRKind(access.asNode().stamp(NodeView.DEFAULT)).getPlatformKind();
    }

    private AllocatableValue moveSp(AllocatableValue value) {
        return getLIRGeneratorTool().moveSp(value);
    }

    private ComplexMatchResult emitBinaryShift(AArch64ArithmeticOp op, ValueNode value, BinaryNode shift, boolean isShiftNot) {
        AArch64MacroAssembler.ShiftType shiftType = shiftTypeMap.get(shift.getClass());
        assert shiftType != null;
        assert value.getStackKind().isNumericInteger();
        assert shift.getX().getStackKind().isNumericInteger();
        assert shift.getY() instanceof ConstantNode;

        return builder -> {
            Value a = operand(value);
            Value b = operand(shift.getX());
            Variable result = gen.newVariable(LIRKind.combine(a, b));
            AllocatableValue x = moveSp(gen.asAllocatable(a));
            AllocatableValue y = moveSp(gen.asAllocatable(b));
            int shiftAmount = shift.getY().asJavaConstant().asInt();
            gen.append(new AArch64ArithmeticOp.BinaryShiftOp(op, result, x, y, shiftType, shiftAmount, isShiftNot));
            return result;
        };
    }

    @MatchRule("(Add=binary a (LeftShift=shift b Constant))")
    @MatchRule("(Add=binary a (RightShift=shift b Constant))")
    @MatchRule("(Add=binary a (UnsignedRightShift=shift b Constant))")
    @MatchRule("(Sub=binary a (LeftShift=shift b Constant))")
    @MatchRule("(Sub=binary a (RightShift=shift b Constant))")
    @MatchRule("(Sub=binary a (UnsignedRightShift=shift b Constant))")
    public ComplexMatchResult addSubShift(BinaryNode binary, ValueNode a, BinaryNode shift) {
        AArch64ArithmeticOp op = nodeOpMap.get(binary.getClass());
        assert op != null;
        return emitBinaryShift(op, a, shift, false);
    }

    @MatchRule("(And=binary a (LeftShift=shift b Constant))")
    @MatchRule("(And=binary a (RightShift=shift b Constant))")
    @MatchRule("(And=binary a (UnsignedRightShift=shift b Constant))")
    @MatchRule("(Or=binary a (LeftShift=shift b Constant))")
    @MatchRule("(Or=binary a (RightShift=shift b Constant))")
    @MatchRule("(Or=binary a (UnsignedRightShift=shift b Constant))")
    @MatchRule("(Xor=binary a (LeftShift=shift b Constant))")
    @MatchRule("(Xor=binary a (RightShift=shift b Constant))")
    @MatchRule("(Xor=binary a (UnsignedRightShift=shift b Constant))")
    @MatchRule("(And=binary a (Not (LeftShift=shift b Constant)))")
    @MatchRule("(And=binary a (Not (RightShift=shift b Constant)))")
    @MatchRule("(And=binary a (Not (UnsignedRightShift=shift b Constant)))")
    @MatchRule("(Or=binary a (Not (LeftShift=shift b Constant)))")
    @MatchRule("(Or=binary a (Not (RightShift=shift b Constant)))")
    @MatchRule("(Or=binary a (Not (UnsignedRightShift=shift b Constant)))")
    @MatchRule("(Xor=binary a (Not (LeftShift=shift b Constant)))")
    @MatchRule("(Xor=binary a (Not (RightShift=shift b Constant)))")
    @MatchRule("(Xor=binary a (Not (UnsignedRightShift=shift b Constant)))")
    public ComplexMatchResult logicShift(BinaryNode binary, ValueNode a, BinaryNode shift) {
        AArch64ArithmeticOp op = nodeOpMap.get(binary.getClass());
        assert op != null;
        ValueNode operand = binary.getX() == a ? binary.getY() : binary.getX();
        boolean isShiftNot = operand instanceof NotNode;
        return emitBinaryShift(op, a, shift, isShiftNot);
    }

    @MatchRule("(Mul (Negate a) b)")
    @MatchRule("(Negate (Mul a b))")
    public ComplexMatchResult multiplyNegate(ValueNode a, ValueNode b) {
        if (a.getStackKind().isNumericInteger() && b.getStackKind().isNumericInteger()) {
            return builder -> getArithmeticLIRGenerator().emitMNeg(operand(a), operand(b));
        }
        return null;
    }

    @MatchRule("(Add=binary (Mul a b) c)")
    @MatchRule("(Sub=binary c (Mul a b))")
    public ComplexMatchResult multiplyAddSub(BinaryNode binary, ValueNode a, ValueNode b, ValueNode c) {
        JavaKind kindA = a.getStackKind();
        JavaKind kindB = b.getStackKind();
        JavaKind kindC = c.getStackKind();
        if (!kindA.isNumericInteger() || !kindB.isNumericInteger() || !kindC.isNumericInteger()) {
            return null;
        }

        if (binary instanceof AddNode) {
            return builder -> getArithmeticLIRGenerator().emitMAdd(operand(a), operand(b), operand(c));
        }
        return builder -> getArithmeticLIRGenerator().emitMSub(operand(a), operand(b), operand(c));
    }

    @Override
    public AArch64LIRGenerator getLIRGeneratorTool() {
        return (AArch64LIRGenerator) gen;
    }

    protected AArch64ArithmeticLIRGenerator getArithmeticLIRGenerator() {
        return (AArch64ArithmeticLIRGenerator) getLIRGeneratorTool().getArithmetic();
    }
}
