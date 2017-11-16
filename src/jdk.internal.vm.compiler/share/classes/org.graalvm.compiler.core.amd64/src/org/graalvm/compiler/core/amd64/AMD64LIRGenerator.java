/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.CMP;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.PD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.PS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize.QWORD;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isIntConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp;
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
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.amd64.AMD64ArrayEqualsOp;
import org.graalvm.compiler.lir.amd64.AMD64Binary;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer;
import org.graalvm.compiler.lir.amd64.AMD64ByteSwapOp;
import org.graalvm.compiler.lir.amd64.AMD64Call;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.BranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CondMoveOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CondSetOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatCondSetOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.ReturnOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.TableSwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.amd64.AMD64Move.CompareAndSwapOp;
import org.graalvm.compiler.lir.amd64.AMD64Move.MembarOp;
import org.graalvm.compiler.lir.amd64.AMD64Move.StackLeaOp;
import org.graalvm.compiler.lir.amd64.AMD64PauseOp;
import org.graalvm.compiler.lir.amd64.AMD64StringIndexOfOp;
import org.graalvm.compiler.lir.amd64.AMD64ZapRegistersOp;
import org.graalvm.compiler.lir.amd64.AMD64ZapStackOp;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
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

    @Override
    public Variable emitLogicCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        ValueKind<?> kind = newValue.getValueKind();
        assert kind.equals(expectedValue.getValueKind());
        AMD64Kind memKind = (AMD64Kind) kind.getPlatformKind();

        AMD64AddressValue addressValue = asAddressValue(address);
        RegisterValue raxRes = AMD64.rax.asValue(kind);
        emitMove(raxRes, expectedValue);
        append(new CompareAndSwapOp(memKind, raxRes, addressValue, raxRes, asAllocatable(newValue)));

        assert trueValue.getValueKind().equals(falseValue.getValueKind());
        Variable result = newVariable(trueValue.getValueKind());
        append(new CondMoveOp(result, Condition.EQ, asAllocatable(trueValue), falseValue));
        return result;
    }

    @Override
    public Value emitValueCompareAndSwap(Value address, Value expectedValue, Value newValue) {
        ValueKind<?> kind = newValue.getValueKind();
        assert kind.equals(expectedValue.getValueKind());
        AMD64Kind memKind = (AMD64Kind) kind.getPlatformKind();

        AMD64AddressValue addressValue = asAddressValue(address);
        RegisterValue raxRes = AMD64.rax.asValue(kind);
        emitMove(raxRes, expectedValue);
        append(new CompareAndSwapOp(memKind, raxRes, addressValue, raxRes, asAllocatable(newValue)));
        Variable result = newVariable(kind);
        emitMove(result, raxRes);
        return result;
    }

    public void emitCompareAndSwapBranch(ValueKind<?> kind, AMD64AddressValue address, Value expectedValue, Value newValue, Condition condition, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        assert kind.equals(expectedValue.getValueKind());
        assert kind.equals(newValue.getValueKind());
        assert condition == Condition.EQ || condition == Condition.NE;
        AMD64Kind memKind = (AMD64Kind) kind.getPlatformKind();
        RegisterValue raxValue = AMD64.rax.asValue(kind);
        emitMove(raxValue, expectedValue);
        append(new CompareAndSwapOp(memKind, raxValue, address, raxValue, asAllocatable(newValue)));
        append(new BranchOp(condition, trueLabel, falseLabel, trueLabelProbability));
    }

    @Override
    public Value emitAtomicReadAndAdd(Value address, Value delta) {
        ValueKind<?> kind = delta.getValueKind();
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndAddOp((AMD64Kind) kind.getPlatformKind(), result, addressValue, asAllocatable(delta)));
        return result;
    }

    @Override
    public Value emitAtomicReadAndWrite(Value address, Value newValue) {
        ValueKind<?> kind = newValue.getValueKind();
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
        Condition finalCondition = emitCompare(cmpKind, left, right, cond);
        if (cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE) {
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
        } else {
            append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    public void emitCompareBranchMemory(AMD64Kind cmpKind, Value left, AMD64AddressValue right, LIRFrameState state, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        boolean mirrored = emitCompareMemory(cmpKind, left, right, state);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        if (cmpKind.isXMM()) {
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
        } else {
            append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpLIRKind, double overflowProbability) {
        append(new BranchOp(ConditionFlag.Overflow, overflow, noOverflow, overflowProbability));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        emitIntegerTest(left, right);
        append(new BranchOp(Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
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

        boolean isParityCheckNecessary = isFloatComparison && unorderedIsTrue != AMD64ControlFlow.trueOnUnordered(finalCondition);
        Variable result = newVariable(finalTrueValue.getValueKind());
        if (!isParityCheckNecessary && isIntConstant(finalTrueValue, 1) && isIntConstant(finalFalseValue, 0)) {
            if (isFloatComparison) {
                append(new FloatCondSetOp(result, finalCondition));
            } else {
                append(new CondSetOp(result, finalCondition));
            }
        } else if (!isParityCheckNecessary && isIntConstant(finalTrueValue, 0) && isIntConstant(finalFalseValue, 1)) {
            if (isFloatComparison) {
                if (unorderedIsTrue == AMD64ControlFlow.trueOnUnordered(finalCondition.negate())) {
                    append(new FloatCondSetOp(result, finalCondition.negate()));
                } else {
                    append(new FloatCondSetOp(result, finalCondition));
                    Variable negatedResult = newVariable(result.getValueKind());
                    append(new AMD64Binary.ConstOp(AMD64BinaryArithmetic.XOR, OperandSize.get(result.getPlatformKind()), negatedResult, result, 1));
                    result = negatedResult;
                }
            } else {
                append(new CondSetOp(result, finalCondition.negate()));
            }
        } else if (isFloatComparison) {
            append(new FloatCondMoveOp(result, finalCondition, unorderedIsTrue, load(finalTrueValue), load(finalFalseValue)));
        } else {
            append(new CondMoveOp(result, finalCondition, load(finalTrueValue), loadNonConst(finalFalseValue)));
        }
        return result;
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        Variable result = newVariable(trueValue.getValueKind());
        append(new CondMoveOp(result, Condition.EQ, load(trueValue), loadNonConst(falseValue)));
        return result;
    }

    private void emitIntegerTest(Value a, Value b) {
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

    /**
     * This method emits the compare against memory instruction, and may reorder the operands. It
     * returns true if it did so.
     *
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompareMemory(AMD64Kind cmpKind, Value a, AMD64AddressValue b, LIRFrameState state) {
        OperandSize size;
        switch (cmpKind) {
            case BYTE:
                size = OperandSize.BYTE;
                break;
            case WORD:
                size = OperandSize.WORD;
                break;
            case DWORD:
                size = OperandSize.DWORD;
                break;
            case QWORD:
                size = OperandSize.QWORD;
                break;
            case SINGLE:
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PS, asAllocatable(a), b, state));
                return false;
            case DOUBLE:
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PD, asAllocatable(a), b, state));
                return false;
            default:
                throw GraalError.shouldNotReachHere("unexpected kind: " + cmpKind);
        }

        if (isConstantValue(a)) {
            return emitCompareMemoryConOp(size, asConstantValue(a), b, state);
        } else {
            return emitCompareRegMemoryOp(size, asAllocatable(a), b, state);
        }
    }

    protected boolean emitCompareMemoryConOp(OperandSize size, ConstantValue a, AMD64AddressValue b, LIRFrameState state) {
        if (JavaConstant.isNull(a.getConstant())) {
            append(new AMD64BinaryConsumer.MemoryConstOp(CMP, size, b, 0, state));
            return true;
        } else if (a.getConstant() instanceof VMConstant && size == DWORD) {
            VMConstant vc = (VMConstant) a.getConstant();
            append(new AMD64BinaryConsumer.MemoryVMConstOp(CMP.getMIOpcode(size, false), b, vc, state));
            return true;
        } else {
            long value = a.getJavaConstant().asLong();
            if (NumUtil.is32bit(value)) {
                append(new AMD64BinaryConsumer.MemoryConstOp(CMP, size, b, (int) value, state));
                return true;
            } else {
                return emitCompareRegMemoryOp(size, asAllocatable(a), b, state);
            }
        }
    }

    private boolean emitCompareRegMemoryOp(OperandSize size, AllocatableValue a, AMD64AddressValue b, LIRFrameState state) {
        AMD64RMOp op = CMP.getRMOpcode(size);
        append(new AMD64BinaryConsumer.MemoryRMOp(op, size, a, b, state));
        return false;
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
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64ArrayEqualsOp(this, kind, result, array1, array2, asAllocatable(length)));
        return result;
    }

    /**
     * Return a conservative estimate of the page size for use by the String.indexOf intrinsic.
     */
    protected int getVMPageSize() {
        return 4096;
    }

    @Override
    public Variable emitStringIndexOf(Value source, Value sourceCount, Value target, Value targetCount, int constantTargetCount) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        RegisterValue cnt1 = AMD64.rdx.asValue(sourceCount.getValueKind());
        emitMove(cnt1, sourceCount);
        RegisterValue cnt2 = AMD64.rax.asValue(targetCount.getValueKind());
        emitMove(cnt2, targetCount);
        append(new AMD64StringIndexOfOp(this, result, source, target, cnt1, cnt2, AMD64.rcx.asValue(), AMD64.xmm0.asValue(), constantTargetCount, getVMPageSize()));
        return result;
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
    public void emitPause() {
        append(new AMD64PauseOp());
    }

    @Override
    public SaveRegistersOp createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        return new AMD64ZapRegistersOp(zappedRegisters, zapValues);
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        return new AMD64ZapStackOp(zappedStack, zapValues);
    }
}
