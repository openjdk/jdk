/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.sparc;

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BPCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CCR_V_SHIFT;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CCR_XCC_SHIFT;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.FBPCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSimm13;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.NOT_ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Fcc0;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Xcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.F_Ordered;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fcmpd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fcmps;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARCKind.DOUBLE;
import static jdk.vm.ci.sparc.SPARCKind.SINGLE;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARC;

public class SPARCArithmetic {
    public static final class FloatConvertOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<FloatConvertOp> TYPE = LIRInstructionClass.create(FloatConvertOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(5);

        @Opcode private final FloatConvert opcode;
        @Def({REG, HINT}) protected Value result;
        @Use({REG}) protected Value x;

        public enum FloatConvert {
            F2I,
            D2I,
            F2L,
            D2L
        }

        public FloatConvertOp(FloatConvert opcode, Value x, Value result) {
            super(TYPE, SIZE);
            this.opcode = opcode;
            this.x = x;
            this.result = result;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Label notOrdered = new Label();
            switch (opcode) {
                case F2L:
                    masm.fcmp(Fcc0, Fcmps, asRegister(x, SINGLE), asRegister(x, SINGLE));
                    FBPCC.emit(masm, Fcc0, F_Ordered, ANNUL, PREDICT_TAKEN, notOrdered);
                    masm.fstox(asRegister(x, SINGLE), asRegister(result, DOUBLE));
                    masm.fxtod(asRegister(result), asRegister(result));
                    masm.fsubd(asRegister(result, DOUBLE), asRegister(result, DOUBLE), asRegister(result, DOUBLE));
                    masm.bind(notOrdered);
                    break;
                case F2I:
                    masm.fcmp(Fcc0, Fcmps, asRegister(x, SINGLE), asRegister(x, SINGLE));
                    FBPCC.emit(masm, Fcc0, F_Ordered, ANNUL, PREDICT_TAKEN, notOrdered);
                    masm.fstoi(asRegister(x, SINGLE), asRegister(result, SINGLE));
                    masm.fitos(asRegister(result, SINGLE), asRegister(result, SINGLE));
                    masm.fsubs(asRegister(result, SINGLE), asRegister(result, SINGLE), asRegister(result, SINGLE));
                    masm.bind(notOrdered);
                    break;
                case D2L:
                    masm.fcmp(Fcc0, Fcmpd, asRegister(x, DOUBLE), asRegister(x, DOUBLE));
                    FBPCC.emit(masm, Fcc0, F_Ordered, ANNUL, PREDICT_TAKEN, notOrdered);
                    masm.fdtox(asRegister(x, DOUBLE), asRegister(result, DOUBLE));
                    masm.fxtod(asRegister(result, DOUBLE), asRegister(result, DOUBLE));
                    masm.fsubd(asRegister(result, DOUBLE), asRegister(result, DOUBLE), asRegister(result, DOUBLE));
                    masm.bind(notOrdered);
                    break;
                case D2I:
                    masm.fcmp(Fcc0, Fcmpd, asRegister(x, DOUBLE), asRegister(x, DOUBLE));
                    FBPCC.emit(masm, Fcc0, F_Ordered, ANNUL, PREDICT_TAKEN, notOrdered);
                    masm.fdtoi(asRegister(x, DOUBLE), asRegister(result, SINGLE));
                    masm.fitos(asRegister(result, SINGLE), asRegister(result, SINGLE));
                    masm.fsubs(asRegister(result, SINGLE), asRegister(result, SINGLE), asRegister(result, SINGLE));
                    masm.bind(notOrdered);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("missing: " + opcode);
            }
        }
    }

    /**
     * Special LIR instruction as it requires a bunch of scratch registers.
     */
    public static final class RemOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<RemOp> TYPE = LIRInstructionClass.create(RemOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(4);

        @Opcode private final Rem opcode;
        @Def({REG}) protected Value result;
        @Alive({REG, CONST}) protected Value x;
        @Alive({REG, CONST}) protected Value y;
        @Temp({REG}) protected Value scratch1;
        @Temp({REG}) protected Value scratch2;
        @State protected LIRFrameState state;

        public enum Rem {
            IUREM,
            LUREM
        }

        public RemOp(Rem opcode, Value result, Value x, Value y, Value scratch1, Value scratch2, LIRFrameState state) {
            super(TYPE, SIZE);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.scratch1 = scratch1;
            this.scratch2 = scratch2;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (!isJavaConstant(x) && isJavaConstant(y)) {
                assert isSimm13(crb.asIntConst(y));
                assert !x.equals(scratch1);
                assert !x.equals(scratch2);
                assert !y.equals(scratch1);
                switch (opcode) {
                    case LUREM:
                        crb.recordImplicitException(masm.position(), state);
                        masm.udivx(asRegister(x, XWORD), crb.asIntConst(y), asRegister(scratch1, XWORD));
                        masm.mulx(asRegister(scratch1, XWORD), crb.asIntConst(y), asRegister(scratch2, XWORD));
                        getDelayedControlTransfer().emitControlTransfer(crb, masm);
                        masm.sub(asRegister(x, XWORD), asRegister(scratch2, XWORD), asRegister(result, XWORD));
                        break;
                    case IUREM:
                        GraalError.unimplemented();
                        break;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            } else if (isRegister(x) && isRegister(y)) {
                Value xLeft = x;
                switch (opcode) {
                    case LUREM:
                        if (isJavaConstant(x)) {
                            masm.setx(crb.asLongConst(x), asRegister(scratch2, XWORD), false);
                            xLeft = scratch2;
                        }
                        assert !asRegister(xLeft, XWORD).equals(asRegister(scratch1, XWORD));
                        assert !asRegister(y, XWORD).equals(asRegister(scratch1, XWORD));
                        crb.recordImplicitException(masm.position(), state);
                        masm.udivx(asRegister(xLeft, XWORD), asRegister(y, XWORD), asRegister(scratch1, XWORD));
                        masm.mulx(asRegister(scratch1, XWORD), asRegister(y, XWORD), asRegister(scratch1, XWORD));
                        getDelayedControlTransfer().emitControlTransfer(crb, masm);
                        masm.sub(asRegister(xLeft, XWORD), asRegister(scratch1, XWORD), asRegister(result, XWORD));
                        break;
                    case IUREM:
                        assert !asRegister(result, WORD).equals(asRegister(scratch1, WORD));
                        assert !asRegister(result, WORD).equals(asRegister(scratch2, WORD));
                        masm.srl(asRegister(x, WORD), 0, asRegister(scratch1, WORD));
                        masm.srl(asRegister(y, WORD), 0, asRegister(result, WORD));
                        crb.recordImplicitException(masm.position(), state);
                        masm.udivx(asRegister(scratch1, WORD), asRegister(result, WORD), asRegister(scratch2, WORD));
                        masm.mulx(asRegister(scratch2, WORD), asRegister(result, WORD), asRegister(result, WORD));
                        getDelayedControlTransfer().emitControlTransfer(crb, masm);
                        masm.sub(asRegister(scratch1, WORD), asRegister(result, WORD), asRegister(result, WORD));
                        break;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            } else {
                throw GraalError.shouldNotReachHere();
            }
        }
    }

    public static final class SPARCIMulccOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<SPARCIMulccOp> TYPE = LIRInstructionClass.create(SPARCIMulccOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(10);
        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value x;
        @Alive({REG}) protected Value y;

        public SPARCIMulccOp(Value result, Value x, Value y) {
            super(TYPE, SIZE);
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            try (ScratchRegister tmpScratch = masm.getScratchRegister()) {
                Register tmp = tmpScratch.getRegister();
                Register resultRegister = asRegister(result, WORD);
                Register xRegister = asRegister(x, WORD);
                Register yRegister = asRegister(y, WORD);
                masm.sra(xRegister, 0, xRegister);
                masm.sra(yRegister, 0, yRegister);
                masm.mulx(xRegister, yRegister, resultRegister);
                Label noOverflow = new Label();
                masm.sra(resultRegister, 0, tmp);
                masm.compareBranch(tmp, resultRegister, Equal, Xcc, noOverflow, PREDICT_TAKEN, null);
                masm.wrccr(SPARC.g0, 1 << (SPARCAssembler.CCR_ICC_SHIFT + SPARCAssembler.CCR_V_SHIFT));
                masm.bind(noOverflow);
            }
        }
    }

    /**
     * Calculates the product and condition code for long multiplication of long values.
     */
    public static final class SPARCLMulccOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<SPARCLMulccOp> TYPE = LIRInstructionClass.create(SPARCLMulccOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(13);

        @Def({REG}) protected Value result;
        @Alive({REG}) protected Value x;
        @Alive({REG}) protected Value y;
        @Temp({REG}) protected Value scratch1;
        @Temp({REG}) protected Value scratch2;

        public SPARCLMulccOp(Value result, Value x, Value y, LIRGeneratorTool gen) {
            super(TYPE, SIZE);
            this.result = result;
            this.x = x;
            this.y = y;
            this.scratch1 = gen.newVariable(LIRKind.combine(x, y));
            this.scratch2 = gen.newVariable(LIRKind.combine(x, y));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Label noOverflow = new Label();
            masm.mulx(asRegister(x, XWORD), asRegister(y, XWORD), asRegister(result, XWORD));

            // Calculate the upper 64 bit signed := (umulxhi product - (x{63}&y + y{63}&x))
            masm.umulxhi(asRegister(x, XWORD), asRegister(y, XWORD), asRegister(scratch1, XWORD));
            masm.srax(asRegister(x, XWORD), 63, asRegister(scratch2, XWORD));
            masm.and(asRegister(scratch2, XWORD), asRegister(y, XWORD), asRegister(scratch2, XWORD));
            masm.sub(asRegister(scratch1, XWORD), asRegister(scratch2, XWORD), asRegister(scratch1, XWORD));

            masm.srax(asRegister(y, XWORD), 63, asRegister(scratch2, XWORD));
            masm.and(asRegister(scratch2, XWORD), asRegister(x, XWORD), asRegister(scratch2, XWORD));
            masm.sub(asRegister(scratch1, XWORD), asRegister(scratch2, XWORD), asRegister(scratch1, XWORD));

            // Now construct the lower half and compare
            masm.srax(asRegister(result, XWORD), 63, asRegister(scratch2, XWORD));
            masm.cmp(asRegister(scratch1, XWORD), asRegister(scratch2, XWORD));
            BPCC.emit(masm, Xcc, Equal, NOT_ANNUL, PREDICT_TAKEN, noOverflow);
            masm.nop();
            masm.wrccr(g0, 1 << (CCR_XCC_SHIFT + CCR_V_SHIFT));
            masm.bind(noOverflow);
        }
    }

    public static final class MulHighOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<MulHighOp> TYPE = LIRInstructionClass.create(MulHighOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(4);

        @Opcode private final MulHigh opcode;
        @Def({REG}) public AllocatableValue result;
        @Alive({REG}) public AllocatableValue x;
        @Alive({REG}) public AllocatableValue y;
        @Temp({REG}) public AllocatableValue scratch;

        public enum MulHigh {
            IMUL,
            LMUL
        }

        public MulHighOp(MulHigh opcode, AllocatableValue x, AllocatableValue y, AllocatableValue result, AllocatableValue scratch) {
            super(TYPE, SIZE);
            this.opcode = opcode;
            this.x = x;
            this.y = y;
            this.scratch = scratch;
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert isRegister(x) && isRegister(y) && isRegister(result) && isRegister(scratch);
            switch (opcode) {
                case IMUL:
                    masm.sra(asRegister(x), 0, asRegister(x));
                    masm.sra(asRegister(y), 0, asRegister(y));
                    masm.mulx(asRegister(x, WORD), asRegister(y, WORD), asRegister(result, WORD));
                    masm.srax(asRegister(result, WORD), 32, asRegister(result, WORD));
                    break;
                case LMUL:
                    assert !asRegister(scratch, XWORD).equals(asRegister(result, XWORD));
                    masm.umulxhi(asRegister(x, XWORD), asRegister(y, XWORD), asRegister(result, XWORD));

                    masm.srlx(asRegister(x, XWORD), 63, asRegister(scratch, XWORD));
                    masm.mulx(asRegister(scratch, XWORD), asRegister(y, XWORD), asRegister(scratch, XWORD));
                    masm.sub(asRegister(result, XWORD), asRegister(scratch, XWORD), asRegister(result, XWORD));

                    masm.srlx(asRegister(y, XWORD), 63, asRegister(scratch, XWORD));
                    masm.mulx(asRegister(scratch, XWORD), asRegister(x, XWORD), asRegister(scratch, XWORD));
                    masm.sub(asRegister(result, XWORD), asRegister(scratch, XWORD), asRegister(result, XWORD));
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }
}
