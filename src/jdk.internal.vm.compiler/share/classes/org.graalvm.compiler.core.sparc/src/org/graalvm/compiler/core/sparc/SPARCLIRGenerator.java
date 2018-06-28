/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.core.sparc;

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.FMOVDCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.FMOVSCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.MOVicc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Fcc0;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Subcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fcmpd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fcmps;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static jdk.vm.ci.sparc.SPARCKind.SINGLE;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;

import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.CC;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.CMOV;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.NoOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.sparc.SPARCAddressValue;
import org.graalvm.compiler.lir.sparc.SPARCArrayEqualsOp;
import org.graalvm.compiler.lir.sparc.SPARCByteSwapOp;
import org.graalvm.compiler.lir.sparc.SPARCCall;
import org.graalvm.compiler.lir.sparc.SPARCControlFlow;
import org.graalvm.compiler.lir.sparc.SPARCControlFlow.BranchOp;
import org.graalvm.compiler.lir.sparc.SPARCControlFlow.CondMoveOp;
import org.graalvm.compiler.lir.sparc.SPARCControlFlow.ReturnOp;
import org.graalvm.compiler.lir.sparc.SPARCControlFlow.StrategySwitchOp;
import org.graalvm.compiler.lir.sparc.SPARCControlFlow.TableSwitchOp;
import org.graalvm.compiler.lir.sparc.SPARCFloatCompareOp;
import org.graalvm.compiler.lir.sparc.SPARCImmediateAddressValue;
import org.graalvm.compiler.lir.sparc.SPARCJumpOp;
import org.graalvm.compiler.lir.sparc.SPARCLoadConstantTableBaseOp;
import org.graalvm.compiler.lir.sparc.SPARCMove.LoadOp;
import org.graalvm.compiler.lir.sparc.SPARCMove.MembarOp;
import org.graalvm.compiler.lir.sparc.SPARCMove.NullCheckOp;
import org.graalvm.compiler.lir.sparc.SPARCMove.StackLoadAddressOp;
import org.graalvm.compiler.lir.sparc.SPARCOP3Op;
import org.graalvm.compiler.lir.sparc.SPARCPauseOp;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARCKind;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public abstract class SPARCLIRGenerator extends LIRGenerator {

    private SPARCLoadConstantTableBaseOp loadConstantTableBaseOp;
    private final ConstantTableBaseProvider constantTableBaseProvider;

    public static final class ConstantTableBaseProvider {
        private Variable constantTableBase;
        private boolean useConstantTableBase = false;

        public Variable getConstantTableBase() {
            useConstantTableBase = true;
            return constantTableBase;
        }
    }

    public SPARCLIRGenerator(LIRKindTool lirKindTool, SPARCArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes,
                    ConstantTableBaseProvider constantTableBaseProvider) {
        super(lirKindTool, arithmeticLIRGen, moveFactory, providers, lirGenRes);
        this.constantTableBaseProvider = constantTableBaseProvider;
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((SPARCKind) kind) {
            case BYTE:
                return JavaConstant.forByte((byte) dead);
            case HWORD:
                return JavaConstant.forShort((short) dead);
            case WORD:
                return JavaConstant.forInt((int) dead);
            case XWORD:
                return JavaConstant.forLong(dead);
            case SINGLE:
            case V32_BYTE:
            case V32_HWORD:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) dead));
            case DOUBLE:
            case V64_BYTE:
            case V64_HWORD:
            case V64_WORD:
                return JavaConstant.forDouble(Double.longBitsToDouble(dead));
            default:
                throw new IllegalArgumentException(kind.toString());
        }
    }

    /**
     * The SPARC backend only uses WORD and DWORD values in registers because except to the ld/st
     * instructions no instruction deals either with 32 or 64 bits. This function converts small
     * integer kinds to WORD.
     */
    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        switch ((SPARCKind) kind.getPlatformKind()) {
            case BYTE:
            case HWORD:
                return kind.changeType(SPARCKind.WORD);
            default:
                return kind;
        }
    }

    public SPARCAddressValue asAddressValue(Value address) {
        if (address instanceof SPARCAddressValue) {
            return (SPARCAddressValue) address;
        } else {
            ValueKind<?> kind = address.getValueKind();
            if (address instanceof JavaConstant) {
                long displacement = ((JavaConstant) address).asLong();
                if (SPARCAssembler.isSimm13(displacement)) {
                    return new SPARCImmediateAddressValue(kind, SPARC.g0.asValue(kind), (int) displacement);
                }
            }
            return new SPARCImmediateAddressValue(kind, asAllocatable(address), 0);
        }
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        Variable result = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new StackLoadAddressOp(result, stackslot));
        return result;
    }

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(javaKind, input.getValueKind());
            emitMove(operand, input);
        }
        append(new ReturnOp(operand));
    }

    @Override
    public void emitJump(LabelRef label) {
        append(new SPARCJumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value x, Value y, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        AllocatableValue left;
        Value right;
        Condition actualCondition;
        if (isJavaConstant(x)) {
            left = load(y);
            right = loadSimm13(x);
            actualCondition = cond.mirror();
        } else {
            left = load(x);
            right = loadSimm13(y);
            actualCondition = cond;
        }
        SPARCKind actualCmpKind = (SPARCKind) cmpKind;
        if (actualCmpKind.isInteger()) {
            assert actualCmpKind.equals(XWORD) || actualCmpKind.equals(WORD) : "SPARC does not support compare of: " + actualCmpKind;
            append(new SPARCControlFlow.CompareBranchOp(left, right, actualCondition, trueDestination, falseDestination, actualCmpKind, unorderedIsTrue, trueDestinationProbability));
        } else if (actualCmpKind.isFloat()) {
            emitFloatCompare(actualCmpKind, x, y, Fcc0);
            ConditionFlag cf = SPARCControlFlow.fromCondition(false, cond, unorderedIsTrue);
            append(new SPARCControlFlow.BranchOp(cf, trueDestination, falseDestination, actualCmpKind, trueDestinationProbability));
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpLIRKind, double overflowProbability) {
        SPARCKind cmpKind = (SPARCKind) cmpLIRKind.getPlatformKind();
        append(new BranchOp(ConditionFlag.OverflowSet, overflow, noOverflow, cmpKind, overflowProbability));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        emitIntegerTest(left, right);
        append(new BranchOp(ConditionFlag.Equal, trueDestination, falseDestination, (SPARCKind) left.getPlatformKind(), trueDestinationProbability));
    }

    private void emitIntegerTest(Value a, Value b) {
        assert ((SPARCKind) a.getPlatformKind()).isInteger();
        if (LIRValueUtil.isVariable(b)) {
            append(SPARCOP3Op.newBinaryVoid(Op3s.Andcc, load(b), loadSimm13(a)));
        } else {
            append(SPARCOP3Op.newBinaryVoid(Op3s.Andcc, load(a), loadSimm13(b)));
        }
    }

    private Value loadSimm11(Value value) {
        if (isJavaConstant(value)) {
            JavaConstant c = asJavaConstant(value);
            if (c.isNull() || SPARCAssembler.isSimm11(c)) {
                return value;
            }
        }
        return load(value);
    }

    public Value loadSimm13(Value value) {
        if (isJavaConstant(value)) {
            JavaConstant c = asJavaConstant(value);
            if (c.isNull() || SPARCAssembler.isSimm13(c)) {
                return value;
            }
        }
        return load(value);
    }

    @Override
    public Value loadNonConst(Value value) {
        // SPARC does not support a proper way of loadNonConst. Please use the appropriate
        // loadSimm11 or loadSimm13 variants.
        throw GraalError.shouldNotReachHere("This operation is not available for SPARC.");
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        // Emit compare
        SPARCKind cmpSPARCKind = (SPARCKind) cmpKind;
        boolean mirrored = emitCompare(cmpSPARCKind, left, right);

        // Emit move
        Value actualTrueValue = trueValue;
        Value actualFalseValue = falseValue;
        SPARCKind valueKind = (SPARCKind) trueValue.getPlatformKind();
        CMOV cmove;
        if (valueKind.isFloat()) {
            actualTrueValue = load(trueValue); // Floats cannot be immediate at all
            actualFalseValue = load(falseValue);
            cmove = valueKind.equals(SINGLE) ? FMOVSCC : FMOVDCC;
        } else if (valueKind.isInteger()) {
            actualTrueValue = loadSimm11(trueValue);
            actualFalseValue = loadSimm11(falseValue);
            cmove = MOVicc;
        } else {
            throw GraalError.shouldNotReachHere();
        }
        Variable result = newVariable(trueValue.getValueKind());
        ConditionFlag finalCondition = SPARCControlFlow.fromCondition(cmpSPARCKind.isInteger(), mirrored ? cond.mirror() : cond, unorderedIsTrue);
        CC cc = CC.forKind(cmpSPARCKind);
        append(new CondMoveOp(cmove, cc, finalCondition, actualTrueValue, actualFalseValue, result));
        return result;
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param cmpKind Kind how a and b have to be compared
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    protected boolean emitCompare(SPARCKind cmpKind, Value a, Value b) {
        boolean mirrored;
        if (cmpKind.isInteger()) { // Integer case
            mirrored = emitIntegerCompare(cmpKind, a, b);
        } else if (cmpKind.isFloat()) { // Float case
            mirrored = false; // No mirroring done on floats
            emitFloatCompare(cmpKind, a, b, Fcc0);
        } else {
            throw GraalError.shouldNotReachHere();
        }
        return mirrored;
    }

    private boolean emitIntegerCompare(SPARCKind cmpKind, Value a, Value b) {
        boolean mirrored;
        assert cmpKind.isInteger();
        AllocatableValue left;
        Value right;
        if (LIRValueUtil.isVariable(b)) {
            left = load(b);
            right = loadSimm13(a);
            mirrored = true;
        } else {
            left = load(a);
            right = loadSimm13(b);
            mirrored = false;
        }
        int compareBytes = cmpKind.getSizeInBytes();
        // SPARC compares 32 or 64 bits
        if (compareBytes < left.getPlatformKind().getSizeInBytes()) {
            left = asAllocatable(arithmeticLIRGen.emitSignExtend(left, cmpKind.getSizeInBits(), XWORD.getSizeInBits()));
        }
        if (compareBytes < right.getPlatformKind().getSizeInBytes()) {
            right = arithmeticLIRGen.emitSignExtend(right, cmpKind.getSizeInBits(), XWORD.getSizeInBits());
        }
        append(SPARCOP3Op.newBinaryVoid(Subcc, left, right));
        return mirrored;
    }

    private void emitFloatCompare(SPARCKind cmpJavaKind, Value a, Value b, CC cc) {
        Opfs floatCompareOpcode;
        assert cmpJavaKind.isFloat();
        switch (cmpJavaKind) {
            case DOUBLE:
                floatCompareOpcode = Fcmpd;
                break;
            case SINGLE:
                floatCompareOpcode = Fcmps;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        append(new SPARCFloatCompareOp(floatCompareOpcode, cc, load(a), load(b)));
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        Variable result = newVariable(trueValue.getValueKind());
        ConditionFlag flag = SPARCControlFlow.fromCondition(true, Condition.EQ, false);
        CC cc = CC.forKind(left.getPlatformKind());
        append(new CondMoveOp(MOVicc, cc, flag, loadSimm11(trueValue), loadSimm11(falseValue), result));
        return result;
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        if (SPARCAssembler.isWordDisp30(maxOffset)) {
            append(new SPARCCall.DirectNearForeignCallOp(linkage, result, arguments, temps, info));
        } else {
            append(new SPARCCall.DirectFarForeignCallOp(linkage, result, arguments, temps, info));
        }
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        Variable scratchValue = newVariable(key.getValueKind());
        AllocatableValue base = AllocatableValue.ILLEGAL;
        for (Constant c : strategy.getKeyConstants()) {
            if (!getMoveFactory().canInlineConstant(c)) {
                base = constantTableBaseProvider.getConstantTableBase();
                break;
            }
        }
        append(createStrategySwitchOp(base, strategy, keyTargets, defaultTarget, key, scratchValue));
    }

    protected StrategySwitchOp createStrategySwitchOp(AllocatableValue base, SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, Variable scratchValue) {
        return new StrategySwitchOp(base, strategy, keyTargets, defaultTarget, key, scratchValue);
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        // Making a copy of the switch value is necessary because jump table destroys the input
        // value
        Variable tmp = newVariable(key.getValueKind());
        emitMove(tmp, key);
        append(new TableSwitchOp(lowKey, defaultTarget, targets, tmp, newVariable(LIRKind.value(target().arch.getWordKind()))));
    }

    protected SPARC getArchitecture() {
        return (SPARC) target().arch;
    }

    @Override
    public Variable emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new SPARCByteSwapOp(this, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length) {
        Variable result = newVariable(LIRKind.value(SPARCKind.WORD));
        append(new SPARCArrayEqualsOp(this, kind, result, load(array1), load(array2), asAllocatable(length)));
        return result;
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
        append(new ReturnOp(Value.ILLEGAL));
    }

    public Value emitSignExtendLoad(LIRKind kind, LIRKind resultKind, Value address, LIRFrameState state) {
        SPARCAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(resultKind);
        append(new LoadOp(kind.getPlatformKind(), result, loadAddress, state, true));
        return result;
    }

    public Value emitZeroExtendLoad(LIRKind kind, LIRKind resultKind, Value address, LIRFrameState state) {
        SPARCAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(resultKind);
        append(new LoadOp(kind.getPlatformKind(), result, loadAddress, state));
        return result;
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        PlatformKind kind = address.getPlatformKind();
        assert kind == XWORD : address + " - " + kind + " not an object!";
        append(new NullCheckOp(asAddressValue(address), state));
    }

    public void emitLoadConstantTableBase() {
        constantTableBaseProvider.constantTableBase = newVariable(LIRKind.value(XWORD));
        int nextPosition = getResult().getLIR().getLIRforBlock(getCurrentBlock()).size();
        NoOp placeHolder = append(new NoOp(getCurrentBlock(), nextPosition));
        loadConstantTableBaseOp = new SPARCLoadConstantTableBaseOp(constantTableBaseProvider.constantTableBase, placeHolder);
    }

    @Override
    public void beforeRegisterAllocation() {
        LIR lir = getResult().getLIR();
        loadConstantTableBaseOp.setAlive(lir, constantTableBaseProvider.useConstantTableBase);
    }

    @Override
    public void emitPause() {
        append(new SPARCPauseOp());
    }
}
