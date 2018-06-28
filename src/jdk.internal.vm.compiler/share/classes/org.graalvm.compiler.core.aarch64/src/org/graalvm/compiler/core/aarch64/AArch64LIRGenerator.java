/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import java.util.function.Function;

import org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.graalvm.compiler.lir.aarch64.AArch64ArrayCompareToOp;
import org.graalvm.compiler.lir.aarch64.AArch64ArrayEqualsOp;
import org.graalvm.compiler.lir.aarch64.AArch64ByteSwapOp;
import org.graalvm.compiler.lir.aarch64.AArch64Compare;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow.BranchOp;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow.CondMoveOp;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow.StrategySwitchOp;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow.TableSwitchOp;
import org.graalvm.compiler.lir.aarch64.AArch64LIRFlagsVersioned;
import org.graalvm.compiler.lir.aarch64.AArch64Move;
import org.graalvm.compiler.lir.aarch64.AArch64AtomicMove.AtomicReadAndAddOp;
import org.graalvm.compiler.lir.aarch64.AArch64AtomicMove.AtomicReadAndAddLSEOp;
import org.graalvm.compiler.lir.aarch64.AArch64AtomicMove.CompareAndSwapOp;
import org.graalvm.compiler.lir.aarch64.AArch64AtomicMove.AtomicReadAndWriteOp;
import org.graalvm.compiler.lir.aarch64.AArch64Move.MembarOp;
import org.graalvm.compiler.lir.aarch64.AArch64PauseOp;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public abstract class AArch64LIRGenerator extends LIRGenerator {

    public AArch64LIRGenerator(LIRKindTool lirKindTool, AArch64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes) {
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
        // Our own code never calls this since we can't make a definite statement about whether or
        // not we can inline a constant without knowing what kind of operation we execute. Let's be
        // optimistic here and fix up mistakes later.
        return true;
    }

    /**
     * AArch64 cannot use anything smaller than a word in any instruction other than load and store.
     */
    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        switch ((AArch64Kind) kind.getPlatformKind()) {
            case BYTE:
            case WORD:
                return kind.changeType(AArch64Kind.DWORD);
            default:
                return kind;
        }
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        append(new AArch64Move.NullCheckOp(asAddressValue(address), state));
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        Variable result = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new AArch64Move.StackLoadAddressOp(result, stackslot));
        return result;
    }

    public AArch64AddressValue asAddressValue(Value address) {
        if (address instanceof AArch64AddressValue) {
            return (AArch64AddressValue) address;
        } else {
            return new AArch64AddressValue(address.getValueKind(), asAllocatable(address), Value.ILLEGAL, 0, 1, AddressingMode.BASE_REGISTER_ONLY);
        }
    }

    @Override
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        Variable prevValue = newVariable(expectedValue.getValueKind());
        Variable scratch = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new CompareAndSwapOp(prevValue, loadReg(expectedValue), loadReg(newValue), asAllocatable(address), scratch));
        assert trueValue.getValueKind().equals(falseValue.getValueKind());
        Variable result = newVariable(trueValue.getValueKind());
        append(new CondMoveOp(result, ConditionFlag.EQ, asAllocatable(trueValue), asAllocatable(falseValue)));
        return result;
    }

    @Override
    public Variable emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue) {
        Variable result = newVariable(newValue.getValueKind());
        Variable scratch = newVariable(LIRKind.value(AArch64Kind.WORD));
        append(new CompareAndSwapOp(result, loadNonCompareConst(expectedValue), loadReg(newValue), asAllocatable(address), scratch));
        return result;
    }

    @Override
    public Value emitAtomicReadAndWrite(Value address, ValueKind<?> kind, Value newValue) {
        Variable result = newVariable(kind);
        Variable scratch = newVariable(kind);
        append(new AtomicReadAndWriteOp((AArch64Kind) kind.getPlatformKind(), asAllocatable(result), asAllocatable(address), asAllocatable(newValue), asAllocatable(scratch)));
        return result;
    }

    @Override
    public Value emitAtomicReadAndAdd(Value address, ValueKind<?> kind, Value delta) {
        Variable result = newVariable(kind);
        if (AArch64LIRFlagsVersioned.useLSE(target().arch)) {
            append(new AtomicReadAndAddLSEOp((AArch64Kind) kind.getPlatformKind(), asAllocatable(result), asAllocatable(address), asAllocatable(delta)));
        } else {
            append(new AtomicReadAndAddOp((AArch64Kind) kind.getPlatformKind(), asAllocatable(result), asAllocatable(address), delta));
        }
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
    public void emitJump(LabelRef label) {
        assert label != null;
        append(new StandardOp.JumpOp(label));
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability) {
        append(new AArch64ControlFlow.BranchOp(ConditionFlag.VS, overflow, noOverflow, overflowProbability));
    }

    /**
     * Branches to label if (left & right) == 0. If negated is true branchse on non-zero instead.
     *
     * @param left Integer kind. Non null.
     * @param right Integer kind. Non null.
     * @param trueDestination destination if left & right == 0. Non null.
     * @param falseDestination destination if left & right != 0. Non null
     * @param trueSuccessorProbability hoistoric probability that comparison is true
     */
    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {
        assert ((AArch64Kind) left.getPlatformKind()).isInteger() && left.getPlatformKind() == right.getPlatformKind();
        ((AArch64ArithmeticLIRGenerator) getArithmetic()).emitBinary(LIRKind.combine(left, right), AArch64ArithmeticOp.ANDS, true, left, right);
        append(new AArch64ControlFlow.BranchOp(ConditionFlag.EQ, trueDestination, falseDestination, trueSuccessorProbability));
    }

    /**
     * Conditionally move trueValue into new variable if cond + unorderedIsTrue is true, else
     * falseValue.
     *
     * @param left Arbitrary value. Has to have same type as right. Non null.
     * @param right Arbitrary value. Has to have same type as left. Non null.
     * @param cond condition that decides whether to move trueValue or falseValue into result. Non
     *            null.
     * @param unorderedIsTrue defines whether floating-point comparisons consider unordered true or
     *            not. Ignored for integer comparisons.
     * @param trueValue arbitrary value same type as falseValue. Non null.
     * @param falseValue arbitrary value same type as trueValue. Non null.
     * @return value containing trueValue if cond + unorderedIsTrue is true, else falseValue. Non
     *         null.
     */
    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        boolean mirrored = emitCompare(cmpKind, left, right, cond, unorderedIsTrue);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        boolean finalUnorderedIsTrue = mirrored ? !unorderedIsTrue : unorderedIsTrue;
        ConditionFlag cmpCondition = toConditionFlag(((AArch64Kind) cmpKind).isInteger(), finalCondition, finalUnorderedIsTrue);
        Variable result = newVariable(trueValue.getValueKind());
        append(new CondMoveOp(result, cmpCondition, loadReg(trueValue), loadReg(falseValue)));
        return result;
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        boolean mirrored = emitCompare(cmpKind, left, right, cond, unorderedIsTrue);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        boolean finalUnorderedIsTrue = mirrored ? !unorderedIsTrue : unorderedIsTrue;
        ConditionFlag cmpCondition = toConditionFlag(((AArch64Kind) cmpKind).isInteger(), finalCondition, finalUnorderedIsTrue);
        append(new BranchOp(cmpCondition, trueDestination, falseDestination, trueDestinationProbability));
    }

    private static ConditionFlag toConditionFlag(boolean isInt, Condition cond, boolean unorderedIsTrue) {
        return isInt ? toIntConditionFlag(cond) : toFloatConditionFlag(cond, unorderedIsTrue);
    }

    /**
     * Takes a Condition and unorderedIsTrue flag and returns the correct Aarch64 specific
     * ConditionFlag. Note: This is only correct if the emitCompare code for floats has correctly
     * handled the case of 'EQ && unorderedIsTrue', respectively 'NE && !unorderedIsTrue'!
     */
    private static ConditionFlag toFloatConditionFlag(Condition cond, boolean unorderedIsTrue) {
        switch (cond) {
            case LT:
                return unorderedIsTrue ? ConditionFlag.LT : ConditionFlag.LO;
            case LE:
                return unorderedIsTrue ? ConditionFlag.LE : ConditionFlag.LS;
            case GE:
                return unorderedIsTrue ? ConditionFlag.PL : ConditionFlag.GE;
            case GT:
                return unorderedIsTrue ? ConditionFlag.HI : ConditionFlag.GT;
            case EQ:
                return ConditionFlag.EQ;
            case NE:
                return ConditionFlag.NE;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Takes a Condition and returns the correct Aarch64 specific ConditionFlag.
     */
    private static ConditionFlag toIntConditionFlag(Condition cond) {
        switch (cond) {
            case EQ:
                return ConditionFlag.EQ;
            case NE:
                return ConditionFlag.NE;
            case LT:
                return ConditionFlag.LT;
            case LE:
                return ConditionFlag.LE;
            case GT:
                return ConditionFlag.GT;
            case GE:
                return ConditionFlag.GE;
            case AE:
                return ConditionFlag.HS;
            case BE:
                return ConditionFlag.LS;
            case AT:
                return ConditionFlag.HI;
            case BT:
                return ConditionFlag.LO;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param a the left operand of the comparison. Has to have same type as b. Non null.
     * @param b the right operand of the comparison. Has to have same type as a. Non null.
     * @return true if mirrored (i.e. "b cmp a" instead of "a cmp b" was done).
     */
    protected boolean emitCompare(PlatformKind cmpKind, Value a, Value b, Condition condition, boolean unorderedIsTrue) {
        Value left;
        Value right;
        boolean mirrored;
        AArch64Kind kind = (AArch64Kind) cmpKind;
        if (kind.isInteger()) {
            Value aExt = a;
            Value bExt = b;

            int compareBytes = cmpKind.getSizeInBytes();
            // AArch64 compares 32 or 64 bits: sign extend a and b as required.
            if (compareBytes < a.getPlatformKind().getSizeInBytes()) {
                aExt = arithmeticLIRGen.emitSignExtend(a, compareBytes * 8, 64);
            }
            if (compareBytes < b.getPlatformKind().getSizeInBytes()) {
                bExt = arithmeticLIRGen.emitSignExtend(b, compareBytes * 8, 64);
            }

            if (LIRValueUtil.isVariable(bExt)) {
                left = load(bExt);
                right = loadNonConst(aExt);
                mirrored = true;
            } else {
                left = load(aExt);
                right = loadNonConst(bExt);
                mirrored = false;
            }
            append(new AArch64Compare.CompareOp(left, loadNonCompareConst(right)));
        } else if (kind.isSIMD()) {
            if (AArch64Compare.FloatCompareOp.isFloatCmpConstant(a, condition, unorderedIsTrue)) {
                left = load(b);
                right = a;
                mirrored = true;
            } else if (AArch64Compare.FloatCompareOp.isFloatCmpConstant(b, condition, unorderedIsTrue)) {
                left = load(a);
                right = b;
                mirrored = false;
            } else {
                left = load(a);
                right = loadReg(b);
                mirrored = false;
            }
            append(new AArch64Compare.FloatCompareOp(left, asAllocatable(right), condition, unorderedIsTrue));
        } else {
            throw GraalError.shouldNotReachHere();
        }
        return mirrored;
    }

    /**
     * If value is a constant that cannot be used directly with a gpCompare instruction load it into
     * a register and return the register, otherwise return constant value unchanged.
     */
    protected Value loadNonCompareConst(Value value) {
        if (!isCompareConstant(value)) {
            return loadReg(value);
        }
        return value;
    }

    /**
     * Checks whether value can be used directly with a gpCompare instruction. This is <b>not</b>
     * the same as {@link AArch64ArithmeticLIRGenerator#isArithmeticConstant(JavaConstant)}, because
     * 0.0 is a valid compare constant for floats, while there are no arithmetic constants for
     * floats.
     *
     * @param value any type. Non null.
     * @return true if value can be used directly in comparison instruction, false otherwise.
     */
    public boolean isCompareConstant(Value value) {
        if (isJavaConstant(value)) {
            JavaConstant constant = asJavaConstant(value);
            if (constant instanceof PrimitiveConstant) {
                final long longValue = constant.asLong();
                long maskedValue;
                switch (constant.getJavaKind()) {
                    case Boolean:
                    case Byte:
                        maskedValue = longValue & 0xFF;
                        break;
                    case Char:
                    case Short:
                        maskedValue = longValue & 0xFFFF;
                        break;
                    case Int:
                        maskedValue = longValue & 0xFFFF_FFFF;
                        break;
                    case Long:
                        maskedValue = longValue;
                        break;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
                return AArch64MacroAssembler.isArithmeticImmediate(maskedValue);
            } else {
                return constant.isDefaultForKind();
            }
        }
        return false;
    }

    /**
     * Moves trueValue into result if (left & right) == 0, else falseValue.
     *
     * @param left Integer kind. Non null.
     * @param right Integer kind. Non null.
     * @param trueValue Integer kind. Non null.
     * @param falseValue Integer kind. Non null.
     * @return virtual register containing trueValue if (left & right) == 0, else falseValue.
     */
    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        assert ((AArch64Kind) left.getPlatformKind()).isInteger() && ((AArch64Kind) right.getPlatformKind()).isInteger();
        assert ((AArch64Kind) trueValue.getPlatformKind()).isInteger() && ((AArch64Kind) falseValue.getPlatformKind()).isInteger();
        ((AArch64ArithmeticLIRGenerator) getArithmetic()).emitBinary(left.getValueKind(), AArch64ArithmeticOp.ANDS, true, left, right);
        Variable result = newVariable(trueValue.getValueKind());
        append(new CondMoveOp(result, ConditionFlag.EQ, load(trueValue), load(falseValue)));
        return result;
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        append(createStrategySwitchOp(strategy, keyTargets, defaultTarget, key, newVariable(key.getValueKind()), AArch64LIRGenerator::toIntConditionFlag));
    }

    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, AllocatableValue scratchValue,
                    Function<Condition, ConditionFlag> converter) {
        return new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, scratchValue, converter);
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        append(new TableSwitchOp(lowKey, defaultTarget, targets, key, newVariable(LIRKind.value(target().arch.getWordKind())), newVariable(key.getValueKind())));
    }

    @Override
    public Variable emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AArch64ByteSwapOp(result, input));
        return result;
    }

    @Override
    public Variable emitArrayCompareTo(JavaKind kind1, JavaKind kind2, Value array1, Value array2, Value length1, Value length2) {
        LIRKind resultKind = LIRKind.value(AArch64Kind.DWORD);
        // DMS TODO: check calling conversion and registers used
        RegisterValue res = AArch64.r0.asValue(resultKind);
        RegisterValue cnt1 = AArch64.r1.asValue(length1.getValueKind());
        RegisterValue cnt2 = AArch64.r2.asValue(length2.getValueKind());
        emitMove(cnt1, length1);
        emitMove(cnt2, length2);
        append(new AArch64ArrayCompareToOp(this, kind1, kind2, res, array1, array2, cnt1, cnt2));
        Variable result = newVariable(resultKind);
        emitMove(result, res);
        return result;
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64ArrayEqualsOp(this, kind, result, array1, array2, asAllocatable(length)));
        return result;
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((AArch64Kind) kind) {
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
            case DOUBLE:
                return JavaConstant.forDouble(Double.longBitsToDouble(dead));
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Loads value into virtual register. Contrary to {@link #load(Value)} this handles
     * RegisterValues (i.e. values corresponding to fixed physical registers) correctly, by not
     * creating an unnecessary move into a virtual register.
     *
     * This avoids generating the following code: mov x0, x19 # x19 is fixed thread register ldr x0,
     * [x0] instead of: ldr x0, [x19].
     */
    protected AllocatableValue loadReg(Value val) {
        if (!(val instanceof Variable || val instanceof RegisterValue)) {
            return emitMove(val);
        }
        return (AllocatableValue) val;
    }

    @Override
    public void emitPause() {
        append(new AArch64PauseOp());
    }

    public abstract void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args);
}
