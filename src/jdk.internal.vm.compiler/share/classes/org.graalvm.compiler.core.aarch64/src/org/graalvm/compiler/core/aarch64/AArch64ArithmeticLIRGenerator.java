/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64Kind.DWORD;
import static jdk.vm.ci.aarch64.AArch64Kind.QWORD;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp.BitManipulationOpCode.BSR;
import static org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp.BitManipulationOpCode.CLZ;
import static org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp.BitManipulationOpCode.CTZ;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.graalvm.compiler.lir.aarch64.AArch64BitManipulationOp;
import org.graalvm.compiler.lir.aarch64.AArch64Move.LoadOp;
import org.graalvm.compiler.lir.aarch64.AArch64Move.StoreConstantOp;
import org.graalvm.compiler.lir.aarch64.AArch64Move.StoreOp;
import org.graalvm.compiler.lir.aarch64.AArch64ReinterpretOp;
import org.graalvm.compiler.lir.aarch64.AArch64SignExtendOp;
import org.graalvm.compiler.lir.aarch64.AArch64Unary;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class AArch64ArithmeticLIRGenerator extends ArithmeticLIRGenerator implements AArch64ArithmeticLIRGeneratorTool {

    @Override
    public AArch64LIRGenerator getLIRGen() {
        return (AArch64LIRGenerator) super.getLIRGen();
    }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return ((AArch64Kind) kind).isInteger();
    }

    @Override
    protected Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            AArch64ArithmeticOp op = setFlags ? AArch64ArithmeticOp.ADDS : AArch64ArithmeticOp.ADD;
            return emitBinary(resultKind, op, true, a, b);
        } else {
            assert !setFlags : "Cannot set flags on floating point arithmetic";
            return emitBinary(resultKind, AArch64ArithmeticOp.FADD, true, a, b);
        }
    }

    @Override
    protected Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            AArch64ArithmeticOp op = setFlags ? AArch64ArithmeticOp.SUBS : AArch64ArithmeticOp.SUB;
            return emitBinary(resultKind, op, false, a, b);
        } else {
            assert !setFlags : "Cannot set flags on floating point arithmetic";
            return emitBinary(resultKind, AArch64ArithmeticOp.FSUB, false, a, b);
        }
    }

    public Value emitExtendMemory(boolean isSigned, AArch64Kind memoryKind, int resultBits, AArch64AddressValue address, LIRFrameState state) {
        // Issue a zero extending load of the proper bit size and set the result to
        // the proper kind.
        Variable result = getLIRGen().newVariable(LIRKind.value(resultBits == 32 ? AArch64Kind.DWORD : AArch64Kind.QWORD));

        int targetSize = resultBits <= 32 ? 32 : 64;
        switch (memoryKind) {
            case BYTE:
            case WORD:
            case DWORD:
            case QWORD:
                getLIRGen().append(new AArch64Unary.MemoryOp(isSigned, targetSize,
                                memoryKind.getSizeInBytes() * 8, result, address, state));
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMul(Value a, Value b, boolean setFlags) {
        AArch64ArithmeticOp intOp = setFlags ? AArch64ArithmeticOp.MULVS : AArch64ArithmeticOp.MUL;
        return emitBinary(LIRKind.combine(a, b), getOpCode(a, intOp, AArch64ArithmeticOp.FMUL), true, a, b);
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.SMULH, true, a, b);
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.UMULH, true, a, b);
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        return emitBinary(LIRKind.combine(a, b), getOpCode(a, AArch64ArithmeticOp.DIV, AArch64ArithmeticOp.FDIV), false, asAllocatable(a), asAllocatable(b));
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        return emitBinary(LIRKind.combine(a, b), getOpCode(a, AArch64ArithmeticOp.REM, AArch64ArithmeticOp.FREM), false, asAllocatable(a), asAllocatable(b));
    }

    @Override
    public Value emitUDiv(Value a, Value b, LIRFrameState state) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.UDIV, false, asAllocatable(a), asAllocatable(b));
    }

    @Override
    public Value emitURem(Value a, Value b, LIRFrameState state) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.UREM, false, asAllocatable(a), asAllocatable(b));
    }

    @Override
    public Value emitAnd(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.AND, true, a, b);
    }

    @Override
    public Value emitOr(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.OR, true, a, b);
    }

    @Override
    public Value emitXor(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.XOR, true, a, b);
    }

    @Override
    public Value emitShl(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.SHL, false, a, b);
    }

    @Override
    public Value emitShr(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.ASHR, false, a, b);
    }

    @Override
    public Value emitUShr(Value a, Value b) {
        assert isNumericInteger(a.getPlatformKind());
        return emitBinary(LIRKind.combine(a, b), AArch64ArithmeticOp.LSHR, false, a, b);
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        PlatformKind resultPlatformKind = getFloatConvertResultKind(op);
        LIRKind resultLirKind = LIRKind.combine(inputVal).changeType(resultPlatformKind);
        Variable result = getLIRGen().newVariable(resultLirKind);
        getLIRGen().append(new AArch64FloatConvertOp(op, result, asAllocatable(inputVal)));
        return result;
    }

    private static PlatformKind getFloatConvertResultKind(FloatConvert op) {
        switch (op) {
            case F2I:
            case D2I:
                return AArch64Kind.DWORD;
            case F2L:
            case D2L:
                return AArch64Kind.QWORD;
            case I2F:
            case L2F:
            case D2F:
                return AArch64Kind.SINGLE;
            case I2D:
            case L2D:
            case F2D:
                return AArch64Kind.DOUBLE;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        ValueKind<?> from = inputVal.getValueKind();
        if (to.equals(from)) {
            return inputVal;
        }
        Variable result = getLIRGen().newVariable(to);
        getLIRGen().append(new AArch64ReinterpretOp(result, asAllocatable(inputVal)));
        return result;
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getPlatformKind() == AArch64Kind.QWORD && bits <= 32) {
            LIRKind resultKind = getResultLirKind(bits, inputVal);
            long mask = NumUtil.getNbitNumberLong(bits);
            Value maskValue = new ConstantValue(resultKind, JavaConstant.forLong(mask));
            return emitBinary(resultKind, AArch64ArithmeticOp.AND, true, inputVal, maskValue);
        } else {
            return inputVal;
        }
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        }
        LIRKind resultKind = getResultLirKind(toBits, inputVal);
        long mask = NumUtil.getNbitNumberLong(fromBits);
        Value maskValue = new ConstantValue(resultKind, JavaConstant.forLong(mask));
        return emitBinary(resultKind, AArch64ArithmeticOp.AND, true, inputVal, maskValue);
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        LIRKind resultKind = getResultLirKind(toBits, inputVal);
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (isJavaConstant(inputVal)) {
            JavaConstant javaConstant = asJavaConstant(inputVal);
            long constant;
            if (javaConstant.isNull()) {
                constant = 0;
            } else {
                constant = javaConstant.asLong();
            }
            int shiftCount = QWORD.getSizeInBytes() * 8 - fromBits;
            return new ConstantValue(resultKind, JavaConstant.forLong((constant << shiftCount) >> shiftCount));
        }
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AArch64SignExtendOp(result, asAllocatable(inputVal), fromBits, toBits));
        return result;
    }

    private static LIRKind getResultLirKind(int resultBitSize, Value... inputValues) {
        if (resultBitSize == 64) {
            return LIRKind.combine(inputValues).changeType(QWORD);
        } else {
            // FIXME: I have no idea what this assert was ever for
            // assert resultBitSize == 32;
            return LIRKind.combine(inputValues).changeType(DWORD);
        }
    }

    protected Variable emitBinary(ValueKind<?> resultKind, AArch64ArithmeticOp op, boolean commutative, Value a, Value b) {
        Variable result = getLIRGen().newVariable(resultKind);
        if (isValidBinaryConstant(op, a, b)) {
            emitBinaryConst(result, op, asAllocatable(a), asJavaConstant(b));
        } else if (commutative && isValidBinaryConstant(op, b, a)) {
            emitBinaryConst(result, op, asAllocatable(b), asJavaConstant(a));
        } else {
            emitBinaryVar(result, op, asAllocatable(a), asAllocatable(b));
        }
        return result;
    }

    private void emitBinaryVar(Variable result, AArch64ArithmeticOp op, AllocatableValue a, AllocatableValue b) {
        AllocatableValue x = moveSp(a);
        AllocatableValue y = moveSp(b);
        switch (op) {
            case FREM:
            case REM:
            case UREM:
                getLIRGen().append(new AArch64ArithmeticOp.BinaryCompositeOp(op, result, x, y));
                break;
            default:
                getLIRGen().append(new AArch64ArithmeticOp.BinaryOp(op, result, x, y));
                break;
        }
    }

    private void emitBinaryConst(Variable result, AArch64ArithmeticOp op, AllocatableValue a, JavaConstant b) {
        AllocatableValue x = moveSp(a);
        getLIRGen().append(new AArch64ArithmeticOp.BinaryConstOp(op, result, x, b));
    }

    private static boolean isValidBinaryConstant(AArch64ArithmeticOp op, Value a, Value b) {
        if (!isJavaConstant(b)) {
            return false;
        }
        JavaConstant constValue = asJavaConstant(b);
        switch (op.category) {
            case LOGICAL:
                return isLogicalConstant(constValue);
            case ARITHMETIC:
                return isArithmeticConstant(constValue);
            case SHIFT:
                assert constValue.asLong() >= 0 && constValue.asLong() < a.getPlatformKind().getSizeInBytes() * Byte.SIZE;
                return true;
            case NONE:
                return false;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private static boolean isLogicalConstant(JavaConstant constValue) {
        switch (constValue.getJavaKind()) {
            case Int:
                return AArch64MacroAssembler.isLogicalImmediate(constValue.asInt());
            case Long:
                return AArch64MacroAssembler.isLogicalImmediate(constValue.asLong());
            default:
                return false;
        }
    }

    protected static boolean isArithmeticConstant(JavaConstant constValue) {
        switch (constValue.getJavaKind()) {
            case Int:
            case Long:
                return AArch64MacroAssembler.isArithmeticImmediate(constValue.asLong());
            case Object:
                return constValue.isNull();
            default:
                return false;
        }
    }

    @Override
    public Value emitNegate(Value inputVal) {
        return emitUnary(getOpCode(inputVal, AArch64ArithmeticOp.NEG, AArch64ArithmeticOp.FNEG), inputVal);
    }

    @Override
    public Value emitNot(Value input) {
        assert isNumericInteger(input.getPlatformKind());
        return emitUnary(AArch64ArithmeticOp.NOT, input);
    }

    @Override
    public Value emitMathAbs(Value input) {
        return emitUnary(getOpCode(input, AArch64ArithmeticOp.ABS, AArch64ArithmeticOp.FABS), input);
    }

    @Override
    public Value emitMathSqrt(Value input) {
        assert input.getPlatformKind() == AArch64Kind.DOUBLE;
        return emitUnary(AArch64ArithmeticOp.SQRT, input);
    }

    @Override
    public Variable emitBitScanForward(Value value) {
        throw GraalError.unimplemented();
    }

    @Override
    public Value emitBitCount(Value operand) {
        throw GraalError.unimplemented("AArch64 ISA does not offer way to implement this more efficiently than a simple Java algorithm.");
    }

    @Override
    public Value emitBitScanReverse(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AArch64Kind.DWORD));
        getLIRGen().append(new AArch64BitManipulationOp(BSR, result, asAllocatable(value)));
        return result;
    }

    @Override
    public Value emitCountLeadingZeros(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AArch64Kind.DWORD));
        getLIRGen().append(new AArch64BitManipulationOp(CLZ, result, asAllocatable(value)));
        return result;
    }

    @Override
    public Value emitCountTrailingZeros(Value value) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(value).changeType(AArch64Kind.DWORD));
        getLIRGen().append(new AArch64BitManipulationOp(CTZ, result, asAllocatable(value)));
        return result;
    }

    private Variable emitUnary(AArch64ArithmeticOp op, Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(new AArch64ArithmeticOp.UnaryOp(op, result, input));
        return result;
    }

    /**
     * If val denotes the stackpointer, move it to another location. This is necessary since most
     * ops cannot handle the stackpointer as input or output.
     */
    private AllocatableValue moveSp(AllocatableValue val) {
        if (val instanceof RegisterValue && ((RegisterValue) val).getRegister().equals(sp)) {
            assert val.getPlatformKind() == AArch64Kind.QWORD : "Stackpointer must be long";
            return getLIRGen().emitMove(val);
        }
        return val;
    }

    /**
     * Returns the opcode depending on the platform kind of val.
     */
    private AArch64ArithmeticOp getOpCode(Value val, AArch64ArithmeticOp intOp, AArch64ArithmeticOp floatOp) {
        return isNumericInteger(val.getPlatformKind()) ? intOp : floatOp;
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        AArch64AddressValue loadAddress = getLIRGen().asAddressValue(address);
        Variable result = getLIRGen().newVariable(getLIRGen().toRegisterKind(kind));
        getLIRGen().append(new LoadOp((AArch64Kind) kind.getPlatformKind(), result, loadAddress, state));
        return result;
    }

    @Override
    public void emitStore(ValueKind<?> lirKind, Value address, Value inputVal, LIRFrameState state) {
        AArch64AddressValue storeAddress = getLIRGen().asAddressValue(address);
        AArch64Kind kind = (AArch64Kind) lirKind.getPlatformKind();

        if (isJavaConstant(inputVal) && kind.isInteger()) {
            JavaConstant c = asJavaConstant(inputVal);
            if (c.isDefaultForKind()) {
                // We can load 0 directly into integer registers
                getLIRGen().append(new StoreConstantOp(kind, storeAddress, c, state));
                return;
            }
        }
        AllocatableValue input = asAllocatable(inputVal);
        getLIRGen().append(new StoreOp(kind, storeAddress, input, state));
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        throw GraalError.unimplemented();
    }

    @Override
    public Value emitMathCos(Value input) {
        throw GraalError.unimplemented();
    }

    @Override
    public Value emitMathSin(Value input) {
        throw GraalError.unimplemented();
    }

    @Override
    public Value emitMathTan(Value input) {
        throw GraalError.unimplemented();
    }

    @Override
    public void emitCompareOp(AArch64Kind cmpKind, Variable left, Value right) {
        throw GraalError.unimplemented();
    }

    @Override
    public Value emitRound(Value value, RoundingMode mode) {
        AArch64ArithmeticOp op;
        switch (mode) {
            case NEAREST:
                op = AArch64ArithmeticOp.FRINTN;
                break;
            case UP:
                op = AArch64ArithmeticOp.FRINTP;
                break;
            case DOWN:
                op = AArch64ArithmeticOp.FRINTM;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }

        return emitUnary(op, value);
    }
}
