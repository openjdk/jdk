/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.core.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.BYTE;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PS;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isIntConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import java.util.Optional;

import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.StandardOp.ZapRegistersOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.amd64.AMD64ArrayCompareToOp;
import org.graalvm.compiler.lir.amd64.AMD64ArrayEqualsOp;
import org.graalvm.compiler.lir.amd64.AMD64ArrayIndexOfOp;
import org.graalvm.compiler.lir.amd64.AMD64Binary;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer;
import org.graalvm.compiler.lir.amd64.AMD64ByteSwapOp;
import org.graalvm.compiler.lir.amd64.AMD64Call;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.BranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CmpBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CmpConstBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CmpDataBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CondMoveOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CondSetOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatCondSetOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.HashTableSwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.ReturnOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.TableSwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.TestBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.TestByteBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.TestConstBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64LFenceOp;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.amd64.AMD64Move.CompareAndSwapOp;
import org.graalvm.compiler.lir.amd64.AMD64Move.MembarOp;
import org.graalvm.compiler.lir.amd64.AMD64Move.StackLeaOp;
import org.graalvm.compiler.lir.amd64.AMD64PauseOp;
import org.graalvm.compiler.lir.amd64.AMD64StringLatin1InflateOp;
import org.graalvm.compiler.lir.amd64.AMD64StringUTF16CompressOp;
import org.graalvm.compiler.lir.amd64.AMD64ZapRegistersOp;
import org.graalvm.compiler.lir.amd64.AMD64ZapStackOp;
import org.graalvm.compiler.lir.amd64.AMD64ZeroMemoryOp;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorCompareOp;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.hashing.IntHasher;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public abstract class AMD64LIRGenerator extends LIRGenerator {

    public AMD64LIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, moveFactory, providers, lirGenRes);
    }

    /**
     * Checks whether the supplied constant can be used without loading it into a register for store
     * operations, i.e., on the right hand side of a memory access.
     *
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    protected static final boolean canStoreConstant(JavaConstant c) {
        // there is no immediate move of 64-bit constants on Intel
        switch (c.getJavaKind()) {
            case Long:
                return NumUtil.isInt(c.asLong());
            case Double:
                return false;
            case Object:
                return c.isNull();
            default:
                return true;
        }
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((AMD64Kind) kind) {
            case BYTE:
                return JavaConstant.forByte((byte) dead);
            case WORD:
                return JavaConstant.forShort((short) dead);
            case DWORD:
                return JavaConstant.forInt((int) dead);
            case QWORD:
                return JavaConstant.forLong(dead);
            case SINGLE:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) dead));
            default:
                // we don't support vector types, so just zap with double for all of them
                return JavaConstant.forDouble(Double.longBitsToDouble(dead));
        }
    }

    public AMD64AddressValue asAddressValue(Value address) {
        if (address instanceof AMD64AddressValue) {
            return (AMD64AddressValue) address;
        } else {
            if (address instanceof JavaConstant) {
                long displacement = ((JavaConstant) address).asLong();
                if (NumUtil.isInt(displacement)) {
                    return new AMD64AddressValue(address.getValueKind(), Value.ILLEGAL, (int) displacement);
                }
            }
            return new AMD64AddressValue(address.getValueKind(), asAllocatable(address), 0);
        }
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        Variable result = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new StackLeaOp(result, stackslot));
        return result;
    }

    /**
     * The AMD64 backend only uses DWORD and QWORD values in registers because of a performance
     * penalty when accessing WORD or BYTE registers. This function converts small integer kinds to
     * DWORD.
     */
    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        switch ((AMD64Kind) kind.getPlatformKind()) {
            case BYTE:
            case WORD:
                return kind.changeType(AMD64Kind.DWORD);
            default:
                return kind;
        }
    }

    private AllocatableValue asAllocatable(Value value, ValueKind<?> kind) {
        if (value.getValueKind().equals(kind)) {
            return asAllocatable(value);
        } else if (isRegister(value)) {
            return asRegister(value).asValue(kind);
        } else if (isConstantValue(value)) {
            return emitLoadConstant(kind, asConstant(value));
        } else {
            Variable variable = newVariable(kind);
            emitMove(variable, value);
            return variable;
        }
    }

    private Value emitCompareAndSwap(boolean isLogic, LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        ValueKind<?> kind = newValue.getValueKind();
        assert kind.equals(expectedValue.getValueKind());

        AMD64AddressValue addressValue = asAddressValue(address);
        LIRKind integralAccessKind = accessKind;
        Value reinterpretedExpectedValue = expectedValue;
        Value reinterpretedNewValue = newValue;
        boolean isXmm = ((AMD64Kind) accessKind.getPlatformKind()).isXMM();
        if (isXmm) {
            if (accessKind.getPlatformKind().equals(AMD64Kind.SINGLE)) {
                integralAccessKind = LIRKind.fromJavaKind(target().arch, JavaKind.Int);
            } else {
                integralAccessKind = LIRKind.fromJavaKind(target().arch, JavaKind.Long);
            }
            reinterpretedExpectedValue = arithmeticLIRGen.emitReinterpret(integralAccessKind, expectedValue);
            reinterpretedNewValue = arithmeticLIRGen.emitReinterpret(integralAccessKind, newValue);
        }
        AMD64Kind memKind = (AMD64Kind) integralAccessKind.getPlatformKind();
        RegisterValue aRes = AMD64.rax.asValue(integralAccessKind);
        AllocatableValue allocatableNewValue = asAllocatable(reinterpretedNewValue, integralAccessKind);
        emitMove(aRes, reinterpretedExpectedValue);
        append(new CompareAndSwapOp(memKind, aRes, addressValue, aRes, allocatableNewValue));

        if (isLogic) {
            assert trueValue.getValueKind().equals(falseValue.getValueKind());
            return emitCondMoveOp(Condition.EQ, trueValue, falseValue, false, false);
        } else {
            if (isXmm) {
                return arithmeticLIRGen.emitReinterpret(accessKind, aRes);
            } else {
                Variable result = newVariable(kind);
                emitMove(result, aRes);
                return result;
            }
        }
    }

    @Override
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        return (Variable) emitCompareAndSwap(true, accessKind, address, expectedValue, newValue, trueValue, falseValue);
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue) {
        return emitCompareAndSwap(false, accessKind, address, expectedValue, newValue, null, null);
    }

    public void emitCompareAndSwapBranch(ValueKind<?> kind, AMD64AddressValue address, Value expectedValue, Value newValue, Condition condition, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        assert kind.getPlatformKind().getSizeInBytes() <= expectedValue.getValueKind().getPlatformKind().getSizeInBytes();
        assert kind.getPlatformKind().getSizeInBytes() <= newValue.getValueKind().getPlatformKind().getSizeInBytes();
        assert condition == Condition.EQ || condition == Condition.NE;
        AMD64Kind memKind = (AMD64Kind) kind.getPlatformKind();
        RegisterValue raxValue = AMD64.rax.asValue(kind);
        emitMove(raxValue, expectedValue);
        append(new CompareAndSwapOp(memKind, raxValue, address, raxValue, asAllocatable(newValue)));
        append(new BranchOp(condition, trueLabel, falseLabel, trueLabelProbability));
    }

    @Override
    public Value emitAtomicReadAndAdd(Value address, ValueKind<?> kind, Value delta) {
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndAddOp((AMD64Kind) kind.getPlatformKind(), result, addressValue, asAllocatable(delta)));
        return result;
    }

    @Override
    public Value emitAtomicReadAndWrite(Value address, ValueKind<?> kind, Value newValue) {
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndWriteOp((AMD64Kind) kind.getPlatformKind(), result, addressValue, asAllocatable(newValue)));
        return result;
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        append(new AMD64Move.NullCheckOp(asAddressValue(address), state));
    }

    @Override
    public void emitJump(LabelRef label) {
        assert label != null;
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
        if (cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE) {
            boolean isSelfEqualsCheck = cond == Condition.EQ && !unorderedIsTrue && left.equals(right);
            Condition finalCondition = emitCompare(cmpKind, left, right, cond);
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability, isSelfEqualsCheck));
            return;
        }

        if (LIRValueUtil.isVariable(right)) {
            emitRawCompareBranch(OperandSize.get(cmpKind), load(right), loadNonConst(left), cond.mirror(), trueLabel, falseLabel, trueLabelProbability);
        } else {
            emitRawCompareBranch(OperandSize.get(cmpKind), load(left), loadNonConst(right), cond, trueLabel, falseLabel, trueLabelProbability);
        }
    }

    private void emitRawCompareBranch(OperandSize size, Variable left, Value right, Condition cond, LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
        if (isConstantValue(right)) {
            Constant c = LIRValueUtil.asConstant(right);
            if (JavaConstant.isNull(c)) {
                AMD64ArithmeticLIRGenerator arithmeticLIRGenerator = (AMD64ArithmeticLIRGenerator) arithmeticLIRGen;
                if (arithmeticLIRGenerator.mustReplaceNullWithNullRegister(c)) {
                    append(new CmpBranchOp(size, left, arithmeticLIRGenerator.getNullRegisterValue(), null, cond, trueLabel, falseLabel, trueLabelProbability));
                } else {
                    append(new TestBranchOp(size, left, left, null, cond, trueLabel, falseLabel, trueLabelProbability));
                }
                return;
            } else if (c instanceof VMConstant) {
                VMConstant vc = (VMConstant) c;
                if (size == DWORD && !GeneratePIC.getValue(getResult().getLIR().getOptions()) && target().inlineObjects) {
                    append(new CmpConstBranchOp(DWORD, left, vc, null, cond, trueLabel, falseLabel, trueLabelProbability));
                } else {
                    append(new CmpDataBranchOp(size, left, vc, cond, trueLabel, falseLabel, trueLabelProbability));
                }
                return;
            } else if (c instanceof JavaConstant) {
                JavaConstant jc = (JavaConstant) c;
                if (jc.isDefaultForKind()) {
                    if (size == BYTE) {
                        append(new TestByteBranchOp(left, left, cond, trueLabel, falseLabel, trueLabelProbability));
                    } else {
                        append(new TestBranchOp(size, left, left, null, cond, trueLabel, falseLabel, trueLabelProbability));
                    }
                    return;
                } else if (NumUtil.is32bit(jc.asLong())) {
                    append(new CmpConstBranchOp(size, left, (int) jc.asLong(), null, cond, trueLabel, falseLabel, trueLabelProbability));
                    return;
                }
            }
        }

        // fallback: load, then compare
        append(new CmpBranchOp(size, left, asAllocatable(right), null, cond, trueLabel, falseLabel, trueLabelProbability));
    }

    public void emitCompareBranchMemory(AMD64Kind cmpKind, Value left, AMD64AddressValue right, LIRFrameState state, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        if (cmpKind.isXMM()) {
            if (cmpKind == AMD64Kind.SINGLE) {
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PS, asAllocatable(left), right, state));
                append(new FloatBranchOp(cond, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
            } else if (cmpKind == AMD64Kind.DOUBLE) {
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PD, asAllocatable(left), right, state));
                append(new FloatBranchOp(cond, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
            } else {
                throw GraalError.shouldNotReachHere("unexpected kind: " + cmpKind);
            }
        } else {
            OperandSize size = OperandSize.get(cmpKind);
            if (isConstantValue(left)) {
                ConstantValue a = asConstantValue(left);
                if (JavaConstant.isNull(a.getConstant())) {
                    append(new CmpConstBranchOp(size, right, 0, state, cond.mirror(), trueLabel, falseLabel, trueLabelProbability));
                    return;
                } else if (a.getConstant() instanceof VMConstant && size == DWORD && target().inlineObjects) {
                    VMConstant vc = (VMConstant) a.getConstant();
                    append(new CmpConstBranchOp(size, right, vc, state, cond.mirror(), trueLabel, falseLabel, trueLabelProbability));
                    return;
                } else if (a.getConstant() instanceof JavaConstant && a.getJavaConstant().getJavaKind() != JavaKind.Object) {
                    long value = a.getJavaConstant().asLong();
                    if (NumUtil.is32bit(value)) {
                        append(new CmpConstBranchOp(size, right, (int) value, state, cond.mirror(), trueLabel, falseLabel, trueLabelProbability));
                        return;
                    }
                }
            }
            append(new CmpBranchOp(size, asAllocatable(left), right, state, cond, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpLIRKind, double overflowProbability) {
        append(new BranchOp(ConditionFlag.Overflow, overflow, noOverflow, overflowProbability));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        if (left.getPlatformKind().getVectorLength() > 1) {
            append(new AMD64VectorCompareOp(VexRMOp.VPTEST, getRegisterSize(left), asAllocatable(left), asAllocatable(right)));
            append(new BranchOp(Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
        } else {
            assert ((AMD64Kind) left.getPlatformKind()).isInteger();
            OperandSize size = left.getPlatformKind() == AMD64Kind.QWORD ? QWORD : DWORD;
            if (isJavaConstant(right) && NumUtil.is32bit(asJavaConstant(right).asLong())) {
                append(new TestConstBranchOp(size, asAllocatable(left), (int) asJavaConstant(right).asLong(), null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
            } else if (isJavaConstant(left) && NumUtil.is32bit(asJavaConstant(left).asLong())) {
                append(new TestConstBranchOp(size, asAllocatable(right), (int) asJavaConstant(left).asLong(), null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
            } else if (isAllocatableValue(right)) {
                append(new TestBranchOp(size, asAllocatable(right), asAllocatable(left), null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
            } else {
                append(new TestBranchOp(size, asAllocatable(left), asAllocatable(right), null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
            }
        }
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        boolean isFloatComparison = cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE;

        Condition finalCondition = cond;
        Value finalTrueValue = trueValue;
        Value finalFalseValue = falseValue;
        if (isFloatComparison) {
            // eliminate the parity check in case of a float comparison
            Value finalLeft = left;
            Value finalRight = right;
            if (unorderedIsTrue != AMD64ControlFlow.trueOnUnordered(finalCondition)) {
                if (unorderedIsTrue == AMD64ControlFlow.trueOnUnordered(finalCondition.mirror())) {
                    finalCondition = finalCondition.mirror();
                    finalLeft = right;
                    finalRight = left;
                } else if (finalCondition != Condition.EQ && finalCondition != Condition.NE) {
                    // negating EQ and NE does not make any sense as we would need to negate
                    // unorderedIsTrue as well (otherwise, we would no longer fulfill the Java
                    // NaN semantics)
                    assert unorderedIsTrue == AMD64ControlFlow.trueOnUnordered(finalCondition.negate());
                    finalCondition = finalCondition.negate();
                    finalTrueValue = falseValue;
                    finalFalseValue = trueValue;
                }
            }
            emitRawCompare(cmpKind, finalLeft, finalRight);
        } else {
            finalCondition = emitCompare(cmpKind, left, right, cond);
        }

        boolean isSelfEqualsCheck = isFloatComparison && finalCondition == Condition.EQ && left.equals(right);
        return emitCondMoveOp(finalCondition, finalTrueValue, finalFalseValue, isFloatComparison, unorderedIsTrue, isSelfEqualsCheck);
    }

    private Variable emitCondMoveOp(Condition condition, Value trueValue, Value falseValue, boolean isFloatComparison, boolean unorderedIsTrue) {
        return emitCondMoveOp(condition, trueValue, falseValue, isFloatComparison, unorderedIsTrue, false);
    }

    private Variable emitCondMoveOp(Condition condition, Value trueValue, Value falseValue, boolean isFloatComparison, boolean unorderedIsTrue, boolean isSelfEqualsCheck) {
        boolean isParityCheckNecessary = isFloatComparison && unorderedIsTrue != AMD64ControlFlow.trueOnUnordered(condition);
        Variable result = newVariable(trueValue.getValueKind());
        if (!isParityCheckNecessary && isIntConstant(trueValue, 1) && isIntConstant(falseValue, 0)) {
            if (isFloatComparison) {
                append(new FloatCondSetOp(result, condition));
            } else {
                append(new CondSetOp(result, condition));
            }
        } else if (!isParityCheckNecessary && isIntConstant(trueValue, 0) && isIntConstant(falseValue, 1)) {
            if (isFloatComparison) {
                if (unorderedIsTrue == AMD64ControlFlow.trueOnUnordered(condition.negate())) {
                    append(new FloatCondSetOp(result, condition.negate()));
                } else {
                    append(new FloatCondSetOp(result, condition));
                    Variable negatedResult = newVariable(result.getValueKind());
                    append(new AMD64Binary.ConstOp(AMD64BinaryArithmetic.XOR, OperandSize.get(result.getPlatformKind()), negatedResult, result, 1));
                    result = negatedResult;
                }
            } else {
                append(new CondSetOp(result, condition.negate()));
            }
        } else if (isFloatComparison) {
            append(new FloatCondMoveOp(result, condition, unorderedIsTrue, load(trueValue), load(falseValue), isSelfEqualsCheck));
        } else {
            append(new CondMoveOp(result, condition, load(trueValue), loadNonConst(falseValue)));
        }
        return result;
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        return emitCondMoveOp(Condition.EQ, load(trueValue), loadNonConst(falseValue), false, false);
    }

    protected static AVXSize getRegisterSize(Value a) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.isXMM()) {
            return AVXKind.getRegisterSize(kind);
        } else {
            return AVXSize.XMM;
        }
    }

    private void emitIntegerTest(Value a, Value b) {
        if (a.getPlatformKind().getVectorLength() > 1) {
            append(new AMD64VectorCompareOp(VexRMOp.VPTEST, getRegisterSize(a), asAllocatable(a), asAllocatable(b)));
        } else {
            assert ((AMD64Kind) a.getPlatformKind()).isInteger();
            OperandSize size = a.getPlatformKind() == AMD64Kind.QWORD ? QWORD : DWORD;
            if (isJavaConstant(b) && NumUtil.is32bit(asJavaConstant(b).asLong())) {
                append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(a), (int) asJavaConstant(b).asLong()));
            } else if (isJavaConstant(a) && NumUtil.is32bit(asJavaConstant(a).asLong())) {
                append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(b), (int) asJavaConstant(a).asLong()));
            } else if (isAllocatableValue(b)) {
                append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(b), asAllocatable(a)));
            } else {
                append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(a), asAllocatable(b)));
            }
        }
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @param cond the condition of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private Condition emitCompare(PlatformKind cmpKind, Value a, Value b, Condition cond) {
        if (LIRValueUtil.isVariable(b)) {
            emitRawCompare(cmpKind, b, a);
            return cond.mirror();
        } else {
            emitRawCompare(cmpKind, a, b);
            return cond;
        }
    }

    private void emitRawCompare(PlatformKind cmpKind, Value left, Value right) {
        ((AMD64ArithmeticLIRGeneratorTool) arithmeticLIRGen).emitCompareOp((AMD64Kind) cmpKind, load(left), loadNonConst(right));
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    public abstract void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments);

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        if (maxOffset != (int) maxOffset && !GeneratePIC.getValue(getResult().getLIR().getOptions())) {
            append(new AMD64Call.DirectFarForeignCallOp(linkage, result, arguments, temps, info));
        } else {
            append(new AMD64Call.DirectNearForeignCallOp(linkage, result, arguments, temps, info));
        }
    }

    @Override
    public Variable emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64ByteSwapOp(result, input));
        return result;
    }

    @Override
    public Variable emitArrayCompareTo(JavaKind kind1, JavaKind kind2, Value array1, Value array2, Value length1, Value length2) {
        LIRKind resultKind = LIRKind.value(AMD64Kind.DWORD);
        RegisterValue raxRes = AMD64.rax.asValue(resultKind);
        RegisterValue cnt1 = AMD64.rcx.asValue(length1.getValueKind());
        RegisterValue cnt2 = AMD64.rdx.asValue(length2.getValueKind());
        emitMove(cnt1, length1);
        emitMove(cnt2, length2);
        append(new AMD64ArrayCompareToOp(this, getAVX3Threshold(), kind1, kind2, raxRes, array1, array2, cnt1, cnt2));
        Variable result = newVariable(resultKind);
        emitMove(result, raxRes);
        return result;
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length, boolean directPointers) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64ArrayEqualsOp(this, kind, kind, result, array1, array2, length, directPointers, getMaxVectorSize()));
        return result;
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind1, JavaKind kind2, Value array1, Value array2, Value length, boolean directPointers) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64ArrayEqualsOp(this, kind1, kind2, result, array1, array2, length, directPointers, getMaxVectorSize()));
        return result;
    }

    /**
     * Return the maximum size of vector registers used in SSE/AVX instructions.
     */
    protected int getMaxVectorSize() {
        // default for "unlimited"
        return -1;
    }

    /**
     * Return the minimal array size for using AVX3 instructions.
     */
    protected int getAVX3Threshold() {
        return 4096;
    }

    @Override
    public Variable emitArrayIndexOf(JavaKind arrayKind, JavaKind valueKind, boolean findTwoConsecutive, Value arrayPointer, Value arrayLength, Value fromIndex, Value... searchValues) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64ArrayIndexOfOp(arrayKind, valueKind, findTwoConsecutive, getMaxVectorSize(), this, result,
                        asAllocatable(arrayPointer), asAllocatable(arrayLength), asAllocatable(fromIndex), searchValues));
        return result;
    }

    @Override
    public void emitStringLatin1Inflate(Value src, Value dst, Value len) {
        RegisterValue rsrc = AMD64.rsi.asValue(src.getValueKind());
        RegisterValue rdst = AMD64.rdi.asValue(dst.getValueKind());
        RegisterValue rlen = AMD64.rdx.asValue(len.getValueKind());

        emitMove(rsrc, src);
        emitMove(rdst, dst);
        emitMove(rlen, len);

        append(new AMD64StringLatin1InflateOp(this, getAVX3Threshold(), rsrc, rdst, rlen));
    }

    @Override
    public Variable emitStringUTF16Compress(Value src, Value dst, Value len) {
        RegisterValue rsrc = AMD64.rsi.asValue(src.getValueKind());
        RegisterValue rdst = AMD64.rdi.asValue(dst.getValueKind());
        RegisterValue rlen = AMD64.rdx.asValue(len.getValueKind());

        emitMove(rsrc, src);
        emitMove(rdst, dst);
        emitMove(rlen, len);

        LIRKind reskind = LIRKind.value(AMD64Kind.DWORD);
        RegisterValue rres = AMD64.rax.asValue(reskind);

        append(new AMD64StringUTF16CompressOp(this, getAVX3Threshold(), rres, rsrc, rdst, rlen));

        Variable res = newVariable(reskind);
        emitMove(res, rres);
        return res;
    }

    @Override
    public void emitReturn(JavaKind kind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(kind, input.getValueKind());
            emitMove(operand, input);
        }
        append(new ReturnOp(operand));
    }

    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, AllocatableValue temp) {
        return new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, temp);
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        // a temp is needed for loading object constants
        boolean needsTemp = !LIRKind.isValue(key);
        append(createStrategySwitchOp(strategy, keyTargets, defaultTarget, key, needsTemp ? newVariable(key.getValueKind()) : Value.ILLEGAL));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        append(new TableSwitchOp(lowKey, defaultTarget, targets, key, newVariable(LIRKind.value(target().arch.getWordKind())), newVariable(key.getValueKind())));
    }

    @Override
    protected Optional<IntHasher> hasherFor(JavaConstant[] keyConstants, double minDensity) {
        int[] keys = new int[keyConstants.length];
        for (int i = 0; i < keyConstants.length; i++) {
            keys[i] = keyConstants[i].asInt();
        }
        return IntHasher.forKeys(keys);
    }

    @Override
    protected void emitHashTableSwitch(IntHasher hasher, JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, Value value) {
        Value hash = value;
        if (hasher.factor > 1) {
            Value factor = emitJavaConstant(JavaConstant.forShort(hasher.factor));
            hash = arithmeticLIRGen.emitMul(hash, factor, false);
        }
        if (hasher.shift > 0) {
            Value shift = emitJavaConstant(JavaConstant.forByte(hasher.shift));
            hash = arithmeticLIRGen.emitShr(hash, shift);
        }
        Value cardinalityAnd = emitJavaConstant(JavaConstant.forInt(hasher.cardinality - 1));
        hash = arithmeticLIRGen.emitAnd(hash, cardinalityAnd);

        Variable scratch = newVariable(LIRKind.value(target().arch.getWordKind()));
        Variable entryScratch = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new HashTableSwitchOp(keys, defaultTarget, targets, value, hash, scratch, entryScratch));
    }

    @Override
    public void emitPause() {
        append(new AMD64PauseOp());
    }

    @Override
    public ZapRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        return new AMD64ZapRegistersOp(zappedRegisters, zapValues);
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        return new AMD64ZapStackOp(zappedStack, zapValues);
    }

    @Override
    public void emitSpeculationFence() {
        append(new AMD64LFenceOp());
    }

    @Override
    public void emitZeroMemory(Value address, Value length, boolean isAligned) {
        RegisterValue lengthReg = AMD64.rcx.asValue(length.getValueKind());
        emitMove(lengthReg, length);
        append(new AMD64ZeroMemoryOp(asAddressValue(address), lengthReg));
    }
}
