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

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Add;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Addcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.And;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Mulx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sdivx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sllx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sra;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Srax;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Srl;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sub;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Subcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Udivx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Xnor;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Faddd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fadds;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fdtos;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fitod;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fitos;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fmuld;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fmuls;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fnegd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fnegs;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fstod;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fxtod;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.UMulxhi;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_DREM;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_FREM;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static org.graalvm.compiler.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.BSF;
import static org.graalvm.compiler.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.IBSR;
import static org.graalvm.compiler.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.LBSR;
import static jdk.vm.ci.code.CodeUtil.mask;
import static jdk.vm.ci.meta.JavaConstant.forLong;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARCKind.DOUBLE;
import static jdk.vm.ci.sparc.SPARCKind.SINGLE;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;

import org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;
import org.graalvm.compiler.lir.sparc.SPARCAddressValue;
import org.graalvm.compiler.lir.sparc.SPARCArithmetic;
import org.graalvm.compiler.lir.sparc.SPARCArithmetic.FloatConvertOp;
import org.graalvm.compiler.lir.sparc.SPARCArithmetic.MulHighOp;
import org.graalvm.compiler.lir.sparc.SPARCArithmetic.MulHighOp.MulHigh;
import org.graalvm.compiler.lir.sparc.SPARCArithmetic.RemOp;
import org.graalvm.compiler.lir.sparc.SPARCArithmetic.RemOp.Rem;
import org.graalvm.compiler.lir.sparc.SPARCArithmetic.SPARCIMulccOp;
import org.graalvm.compiler.lir.sparc.SPARCArithmetic.SPARCLMulccOp;
import org.graalvm.compiler.lir.sparc.SPARCBitManipulationOp;
import org.graalvm.compiler.lir.sparc.SPARCMove.LoadOp;
import org.graalvm.compiler.lir.sparc.SPARCMove.MoveFpGp;
import org.graalvm.compiler.lir.sparc.SPARCMove.StoreConstantOp;
import org.graalvm.compiler.lir.sparc.SPARCMove.StoreOp;
import org.graalvm.compiler.lir.sparc.SPARCOP3Op;
import org.graalvm.compiler.lir.sparc.SPARCOPFOp;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARC.CPUFeature;
import jdk.vm.ci.sparc.SPARCKind;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public class SPARCArithmeticLIRGenerator extends ArithmeticLIRGenerator {

    @Override
    public SPARCLIRGenerator getLIRGen() {
        return (SPARCLIRGenerator) super.getLIRGen();
    }

    @Override
    public Variable emitBitCount(Value operand) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        AllocatableValue usedOperand = asAllocatable(emitZeroExtend(operand));
        getLIRGen().append(new SPARCOP3Op(Op3s.Popc, g0.asValue(), usedOperand, result));
        return result;
    }

    @Override
    public Variable emitBitScanForward(Value operand) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        getLIRGen().append(new SPARCBitManipulationOp(BSF, result, asAllocatable(operand), getLIRGen()));
        return result;
    }

    @Override
    public Variable emitBitScanReverse(Value operand) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        if (operand.getPlatformKind() == SPARCKind.XWORD) {
            getLIRGen().append(new SPARCBitManipulationOp(LBSR, result, asAllocatable(operand), getLIRGen()));
        } else {
            getLIRGen().append(new SPARCBitManipulationOp(IBSR, result, asAllocatable(operand), getLIRGen()));
        }
        return result;
    }

    @Override
    public Value emitMathAbs(Value inputValue) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(inputValue));
        SPARCKind kind = (SPARCKind) inputValue.getPlatformKind();
        Opfs opf;
        switch (kind) {
            case SINGLE:
                opf = Opfs.Fabss;
                break;
            case DOUBLE:
                opf = Opfs.Fabsd;
                break;
            default:
                throw GraalError.shouldNotReachHere("Input kind: " + kind);
        }
        getLIRGen().append(new SPARCOPFOp(opf, g0.asValue(), asAllocatable(inputValue), result));
        return result;
    }

    @Override
    public Value emitMathSqrt(Value inputValue) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(inputValue));
        SPARCKind kind = (SPARCKind) inputValue.getPlatformKind();
        Opfs opf;
        switch (kind) {
            case SINGLE:
                opf = Opfs.Fsqrts;
                break;
            case DOUBLE:
                opf = Opfs.Fsqrtd;
                break;
            default:
                throw GraalError.shouldNotReachHere("Input kind: " + kind);
        }
        getLIRGen().append(new SPARCOPFOp(opf, g0.asValue(), asAllocatable(inputValue), result));
        return result;
    }

    @Override
    public Value emitNegate(Value input) {
        PlatformKind inputKind = input.getPlatformKind();
        if (isNumericInteger(inputKind)) {
            return emitUnary(Sub, input);
        } else {
            return emitUnary(inputKind.equals(DOUBLE) ? Fnegd : Fnegs, input);
        }
    }

    @Override
    public Value emitNot(Value input) {
        return emitUnary(Xnor, input);
    }

    private Variable emitUnary(Opfs opf, Value inputValue) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(inputValue));
        getLIRGen().append(new SPARCOPFOp(opf, g0.asValue(), asAllocatable(inputValue), result));
        return result;
    }

    private Variable emitUnary(Op3s op3, Value input) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(input));
        getLIRGen().append(SPARCOP3Op.newUnary(op3, getLIRGen().loadSimm13(input), result));
        return result;
    }

    private Variable emitBinary(ValueKind<?> resultKind, Opfs opf, Value a, Value b) {
        return emitBinary(resultKind, opf, a, b, null);
    }

    private Variable emitBinary(ValueKind<?> resultKind, Opfs opf, Value a, Value b, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new SPARCOPFOp(opf, asAllocatable(a), asAllocatable(b), result, state));
        return result;
    }

    private Variable emitBinary(ValueKind<?> resultKind, Op3s op3, Value a, int b) {
        return emitBinary(resultKind, op3, a, new ConstantValue(LIRKind.value(WORD), JavaConstant.forInt(b)));
    }

    private Variable emitBinary(ValueKind<?> resultKind, Op3s op3, Value a, Value b) {
        return emitBinary(resultKind, op3, a, b, null);
    }

    private Variable emitBinary(ValueKind<?> resultKind, Op3s op3, Value a, Value b, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(resultKind);
        if (op3.isCommutative() && isJavaConstant(a) && getLIRGen().getMoveFactory().canInlineConstant(asJavaConstant(a))) {
            getLIRGen().append(new SPARCOP3Op(op3, getLIRGen().load(b), getLIRGen().loadSimm13(a), result, state));
        } else {
            getLIRGen().append(new SPARCOP3Op(op3, getLIRGen().load(a), getLIRGen().loadSimm13(b), result, state));
        }
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return ((SPARCKind) kind).isInteger();
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            return emitBinary(resultKind, setFlags ? Addcc : Add, a, b);
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Faddd : Fadds, a, b);
        }
    }

    @Override
    public Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            return emitBinary(resultKind, setFlags ? Subcc : Sub, a, b);
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Opfs.Fsubd : Opfs.Fsubs, a, b);
        }
    }

    @Override
    public Variable emitMul(Value a, Value b, boolean setFlags) {
        LIRKind resultKind = LIRKind.combine(a, b);
        PlatformKind aKind = a.getPlatformKind();
        if (isNumericInteger(aKind)) {
            if (setFlags) {
                Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
                if (aKind == XWORD) {
                    getLIRGen().append(new SPARCLMulccOp(result, getLIRGen().load(a), getLIRGen().load(b), getLIRGen()));
                } else if (aKind == WORD) {
                    getLIRGen().append(new SPARCIMulccOp(result, getLIRGen().load(a), getLIRGen().load(b)));
                } else {
                    throw GraalError.shouldNotReachHere();
                }
                return result;
            } else {
                return emitBinary(resultKind, Op3s.Mulx, a, b);
            }
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Fmuld : Fmuls, a, b);
        }
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        MulHigh opcode;
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                opcode = MulHigh.IMUL;
                break;
            case XWORD:
                opcode = MulHigh.LMUL;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        return emitMulHigh(opcode, a, b);
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                Value result = emitBinary(LIRKind.combine(a, b), Mulx, emitZeroExtend(a), emitZeroExtend(b));
                return emitBinary(LIRKind.combine(a, b), Srax, result, WORD.getSizeInBits());
            case XWORD:
                return emitBinary(LIRKind.combine(a, b), UMulxhi, a, b);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private Value emitMulHigh(MulHigh opcode, Value a, Value b) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
        MulHighOp mulHigh = new MulHighOp(opcode, getLIRGen().load(a), getLIRGen().load(b), result, getLIRGen().newVariable(LIRKind.combine(a, b)));
        getLIRGen().append(mulHigh);
        return result;
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        LIRKind resultKind = LIRKind.combine(a, b);
        if (isJavaConstant(b) && asJavaConstant(b).isDefaultForKind()) { // Div by zero
            Value zero = SPARC.g0.asValue(LIRKind.value(SPARCKind.WORD));
            return emitBinary(resultKind, Op3s.Sdivx, zero, zero, state);
        } else if (isNumericInteger(a.getPlatformKind())) {
            return emitBinary(resultKind, Op3s.Sdivx, emitSignExtend(a), emitSignExtend(b), state);
        } else {
            boolean isDouble = a.getPlatformKind() == DOUBLE;
            return emitBinary(resultKind, isDouble ? Opfs.Fdivd : Opfs.Fdivs, a, b, state);
        }
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
        Variable q1; // Intermediate values
        Variable q2;
        switch ((SPARCKind) a.getPlatformKind()) {
            case WORD:
                // Sign extend a and b
                Value as = emitSignExtend(a);
                Value bs = emitSignExtend(b);
                q1 = emitBinary(as.getValueKind(), Sdivx, as, bs, state);
                q2 = emitBinary(as.getValueKind(), Mulx, q1, bs);
                result = emitSub(as, q2, false);
                break;
            case XWORD:
                q1 = emitBinary(result.getValueKind(), Sdivx, a, b, state);
                q2 = emitBinary(result.getValueKind(), Mulx, q1, b);
                result = emitSub(a, q2, false);
                break;
            case SINGLE:
                ForeignCallLinkage fremCall = getLIRGen().getForeignCalls().lookupForeignCall(ARITHMETIC_FREM);
                result = getLIRGen().emitForeignCall(fremCall, state, a, b);
                break;
            case DOUBLE:
                ForeignCallLinkage dremCall = getLIRGen().getForeignCalls().lookupForeignCall(ARITHMETIC_DREM);
                result = getLIRGen().emitForeignCall(dremCall, state, a, b);
                break;
            default:
                throw GraalError.shouldNotReachHere("missing: " + a.getPlatformKind());
        }
        return result;
    }

    @Override
    public Value emitURem(Value a, Value b, LIRFrameState state) {
        Variable result = getLIRGen().newVariable(LIRKind.combine(a, b));
        Variable scratch1 = getLIRGen().newVariable(LIRKind.combine(a, b));
        Variable scratch2 = getLIRGen().newVariable(LIRKind.combine(a, b));
        Rem opcode;
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                opcode = Rem.IUREM;
                break;
            case XWORD:
                opcode = Rem.LUREM;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        getLIRGen().append(new RemOp(opcode, result, asAllocatable(a), asAllocatable(b), scratch1, scratch2, state));
        return result;

    }

    @Override
    public Value emitUDiv(Value a, Value b, LIRFrameState state) {
        return emitBinary(LIRKind.combine(a, b), Udivx, emitZeroExtend(a), emitZeroExtend(b), state);
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.And, a, b);
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.Or, a, b);
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.Xor, a, b);
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Sll;
                break;
            case XWORD:
                op = Op3s.Sllx;
                break;
            default:
                throw GraalError.shouldNotReachHere(String.format("Unsupported kind %s", aKind));
        }
        return emitBinary(resultKind, op, a, b);
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Sra;
                break;
            case XWORD:
                op = Op3s.Srax;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        return emitBinary(resultKind, op, a, b);
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Srl;
                break;
            case XWORD:
                op = Op3s.Srlx;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        return emitBinary(resultKind, op, a, b);
    }

    private AllocatableValue emitConvertMove(LIRKind kind, AllocatableValue input) {
        Variable result = getLIRGen().newVariable(kind);
        getLIRGen().emitMove(result, input);
        return result;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputValue) {
        AllocatableValue inputAllocatable = asAllocatable(inputValue);
        AllocatableValue result;
        switch (op) {
            case D2F:
                result = getLIRGen().newVariable(LIRKind.combine(inputValue).changeType(SINGLE));
                getLIRGen().append(new SPARCOPFOp(Fdtos, inputAllocatable, result));
                break;
            case F2D:
                result = getLIRGen().newVariable(LIRKind.combine(inputValue).changeType(DOUBLE));
                getLIRGen().append(new SPARCOPFOp(Fstod, inputAllocatable, result));
                break;
            case I2F: {
                AllocatableValue intEncodedFloatReg = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(SINGLE));
                result = getLIRGen().newVariable(intEncodedFloatReg.getValueKind());
                moveBetweenFpGp(intEncodedFloatReg, inputAllocatable);
                getLIRGen().append(new SPARCOPFOp(Fitos, intEncodedFloatReg, result));
                break;
            }
            case I2D: {
                // Unfortunately we must do int -> float -> double because fitod has float
                // and double encoding in one instruction
                AllocatableValue convertedFloatReg = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(SINGLE));
                result = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(DOUBLE));
                moveBetweenFpGp(convertedFloatReg, inputAllocatable);
                getLIRGen().append(new SPARCOPFOp(Fitod, convertedFloatReg, result));
                break;
            }
            case L2D: {
                AllocatableValue longEncodedDoubleReg = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(DOUBLE));
                moveBetweenFpGp(longEncodedDoubleReg, inputAllocatable);
                AllocatableValue convertedDoubleReg = getLIRGen().newVariable(longEncodedDoubleReg.getValueKind());
                getLIRGen().append(new SPARCOPFOp(Fxtod, longEncodedDoubleReg, convertedDoubleReg));
                result = convertedDoubleReg;
                break;
            }
            case D2I: {
                AllocatableValue convertedFloatReg = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(SINGLE));
                getLIRGen().append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.D2I, inputAllocatable, convertedFloatReg));
                AllocatableValue convertedIntReg = getLIRGen().newVariable(LIRKind.combine(convertedFloatReg).changeType(WORD));
                moveBetweenFpGp(convertedIntReg, convertedFloatReg);
                result = convertedIntReg;
                break;
            }
            case F2L: {
                AllocatableValue convertedDoubleReg = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(DOUBLE));
                getLIRGen().append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.F2L, inputAllocatable, convertedDoubleReg));
                AllocatableValue convertedLongReg = getLIRGen().newVariable(LIRKind.combine(convertedDoubleReg).changeType(XWORD));
                moveBetweenFpGp(convertedLongReg, convertedDoubleReg);
                result = convertedLongReg;
                break;
            }
            case F2I: {
                AllocatableValue convertedFloatReg = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(SINGLE));
                getLIRGen().append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.F2I, inputAllocatable, convertedFloatReg));
                AllocatableValue convertedIntReg = getLIRGen().newVariable(LIRKind.combine(convertedFloatReg).changeType(WORD));
                moveBetweenFpGp(convertedIntReg, convertedFloatReg);
                result = convertedIntReg;
                break;
            }
            case D2L: {
                AllocatableValue convertedDoubleReg = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(DOUBLE));
                getLIRGen().append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.D2L, inputAllocatable, convertedDoubleReg));
                AllocatableValue convertedLongReg = getLIRGen().newVariable(LIRKind.combine(convertedDoubleReg).changeType(XWORD));
                moveBetweenFpGp(convertedLongReg, convertedDoubleReg);
                result = convertedLongReg;
                break;
            }
            case L2F: {
                AllocatableValue convertedDoubleReg = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(DOUBLE));
                result = getLIRGen().newVariable(LIRKind.combine(inputAllocatable).changeType(SINGLE));
                moveBetweenFpGp(convertedDoubleReg, inputAllocatable);
                getLIRGen().append(new SPARCOPFOp(Opfs.Fxtos, convertedDoubleReg, result));
                break;
            }
            default:
                throw GraalError.shouldNotReachHere();
        }
        return result;
    }

    protected VirtualStackSlot getTempSlot(LIRKind kind) {
        return getLIRGen().getResult().getFrameMapBuilder().allocateSpillSlot(kind);
    }

    private void moveBetweenFpGp(AllocatableValue dst, AllocatableValue src) {
        AllocatableValue tempSlot;
        PlatformKind dstKind = dst.getPlatformKind();
        PlatformKind srcKind = src.getPlatformKind();
        if (getLIRGen().getArchitecture().getFeatures().contains(CPUFeature.VIS3) && !(srcKind == WORD && dstKind == SINGLE) && !(srcKind == SINGLE && dstKind == WORD)) {
            tempSlot = AllocatableValue.ILLEGAL;
        } else {
            tempSlot = getTempSlot(LIRKind.value(XWORD));
        }
        getLIRGen().append(new MoveFpGp(dst, src, tempSlot));
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getPlatformKind() == XWORD && bits <= 32) {
            LIRKind resultKind = LIRKind.combine(inputVal).changeType(WORD);
            Variable result = getLIRGen().newVariable(resultKind);
            getLIRGen().emitMove(result, inputVal);
            return result;
        } else {
            return inputVal;
        }
    }

    private Value emitSignExtend(Value inputValue) {
        int inputBits = inputValue.getPlatformKind().getSizeInBytes() * 8;
        return emitNarrow(emitSignExtend(inputValue, inputBits, XWORD.getSizeInBits()), inputBits);
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= XWORD.getSizeInBits();
        LIRKind shiftKind = LIRKind.value(WORD);
        LIRKind resultKind = LIRKind.combine(inputVal).changeType(toBits > 32 ? XWORD : WORD);
        int shiftCount = XWORD.getSizeInBits() - fromBits;
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
            return new ConstantValue(resultKind, JavaConstant.forLong((constant << shiftCount) >> shiftCount));
        } else {
            AllocatableValue inputAllocatable = asAllocatable(inputVal);
            Variable result = getLIRGen().newVariable(resultKind);
            if (fromBits == WORD.getSizeInBits() && toBits == XWORD.getSizeInBits()) {
                getLIRGen().append(new SPARCOP3Op(Sra, inputAllocatable, g0.asValue(LIRKind.value(WORD)), result));
            } else {
                Variable tmp = getLIRGen().newVariable(resultKind.changeType(XWORD));
                getLIRGen().append(new SPARCOP3Op(Sllx, inputAllocatable, new ConstantValue(shiftKind, JavaConstant.forInt(shiftCount)), tmp));
                getLIRGen().append(new SPARCOP3Op(Srax, tmp, new ConstantValue(shiftKind, JavaConstant.forInt(shiftCount)), result));
            }
            return result;
        }
    }

    private Value emitZeroExtend(Value inputValue) {
        int inputBits = inputValue.getPlatformKind().getSizeInBytes() * 8;
        return emitNarrow(emitZeroExtend(inputValue, inputBits, XWORD.getSizeInBits()), inputBits);
    }

    @Override
    public Value emitZeroExtend(Value inputValue, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputValue;
        }
        Variable result = getLIRGen().newVariable(LIRKind.combine(inputValue).changeType(toBits > WORD.getSizeInBits() ? XWORD : WORD));
        AllocatableValue inputAllocatable = asAllocatable(inputValue);
        if (fromBits == 32) {
            getLIRGen().append(new SPARCOP3Op(Srl, inputAllocatable, g0.asValue(), result));
        } else {
            Value mask = getLIRGen().emitConstant(LIRKind.value(XWORD), forLong(mask(fromBits)));
            getLIRGen().append(new SPARCOP3Op(And, inputAllocatable, mask, result));
        }
        return result;
    }

    @Override
    public AllocatableValue emitReinterpret(LIRKind to, Value inputVal) {
        SPARCKind fromKind = (SPARCKind) inputVal.getPlatformKind();
        SPARCKind toKind = (SPARCKind) to.getPlatformKind();
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = getLIRGen().newVariable(to);
        // These cases require a move between CPU and FPU registers:
        if (fromKind.isFloat() != toKind.isFloat()) {
            moveBetweenFpGp(result, input);
            return result;
        } else {
            // Otherwise, just emit an ordinary move instruction.
            // Instructions that move or generate 32-bit register values also set the upper 32
            // bits of the register to zero.
            // Consequently, there is no need for a special zero-extension move.
            return emitConvertMove(to, input);
        }
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        SPARCAddressValue loadAddress = getLIRGen().asAddressValue(address);
        Variable result = getLIRGen().newVariable(getLIRGen().toRegisterKind(kind));
        getLIRGen().append(new LoadOp(kind.getPlatformKind(), result, loadAddress, state));
        return result;
    }

    @Override
    public void emitStore(ValueKind<?> kind, Value address, Value inputVal, LIRFrameState state) {
        SPARCAddressValue storeAddress = getLIRGen().asAddressValue(address);
        if (isJavaConstant(inputVal)) {
            JavaConstant c = asJavaConstant(inputVal);
            if (c.isDefaultForKind()) {
                getLIRGen().append(new StoreConstantOp(kind.getPlatformKind(), storeAddress, c, state));
                return;
            }
        }
        Variable input = getLIRGen().load(inputVal);
        getLIRGen().append(new StoreOp(kind.getPlatformKind(), storeAddress, input, state));
    }
}
