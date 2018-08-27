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


package org.graalvm.compiler.lir.amd64.vector;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTF128;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTI128;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRB;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRQ;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VPEXTRW;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VINSERTF128;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VINSERTI128;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VSHUFPD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.VSHUFPS;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSHUFB;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMRIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;

public class AMD64VectorShuffle {

    public static final class IntToVectorOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<IntToVectorOp> TYPE = LIRInstructionClass.create(IntToVectorOp.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue value;

        public IntToVectorOp(AllocatableValue result, AllocatableValue value) {
            super(TYPE);
            assert ((AMD64Kind) result.getPlatformKind()).getScalar().isInteger() : result.getPlatformKind();
            this.result = result;
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(value)) {
                VMOVD.emit(masm, XMM, asRegister(result), asRegister(value));
            } else {
                assert isStackSlot(value);
                VMOVD.emit(masm, XMM, asRegister(result), (AMD64Address) crb.asAddress(value));
            }
        }
    }

    public static final class LongToVectorOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<LongToVectorOp> TYPE = LIRInstructionClass.create(LongToVectorOp.class);
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue value;

        public LongToVectorOp(AllocatableValue result, AllocatableValue value) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.V128_QWORD || result.getPlatformKind() == AMD64Kind.V256_QWORD;
            this.result = result;
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(value)) {
                VMOVQ.emit(masm, XMM, asRegister(result), asRegister(value));
            } else {
                assert isStackSlot(value);
                VMOVQ.emit(masm, XMM, asRegister(result), (AMD64Address) crb.asAddress(value));
            }
        }
    }

    public static final class ShuffleBytesOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ShuffleBytesOp> TYPE = LIRInstructionClass.create(ShuffleBytesOp.class);
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue source;
        @Use({REG, STACK}) protected AllocatableValue selector;

        public ShuffleBytesOp(AllocatableValue result, AllocatableValue source, AllocatableValue selector) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            if (isRegister(selector)) {
                VPSHUFB.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), asRegister(selector));
            } else {
                assert isStackSlot(selector);
                VPSHUFB.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), (AMD64Address) crb.asAddress(selector));
            }
        }
    }

    public static final class ConstShuffleBytesOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ConstShuffleBytesOp> TYPE = LIRInstructionClass.create(ConstShuffleBytesOp.class);
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue source;
        private final byte[] selector;

        public ConstShuffleBytesOp(AllocatableValue result, AllocatableValue source, byte... selector) {
            super(TYPE);
            assert AVXKind.getRegisterSize(((AMD64Kind) result.getPlatformKind())).getBytes() == selector.length;
            this.result = result;
            this.source = source;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(selector, selector.length);
            VPSHUFB.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), address);
        }
    }

    public static class ShuffleWordOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ShuffleWordOp> TYPE = LIRInstructionClass.create(ShuffleWordOp.class);
        private final VexRMIOp op;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue source;
        private final int selector;

        public ShuffleWordOp(VexRMIOp op, AllocatableValue result, AllocatableValue source, int selector) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.source = source;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();
            if (isRegister(source)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), selector);
            } else {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), (AMD64Address) crb.asAddress(source), selector);
            }
        }
    }

    public static class ShuffleFloatOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ShuffleFloatOp> TYPE = LIRInstructionClass.create(ShuffleFloatOp.class);
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue source1;
        @Use({REG, STACK}) protected AllocatableValue source2;
        private final int selector;

        public ShuffleFloatOp(AllocatableValue result, AllocatableValue source1, AllocatableValue source2, int selector) {
            super(TYPE);
            this.result = result;
            this.source1 = source1;
            this.source2 = source2;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();

            VexRVMIOp op;
            switch (kind.getScalar()) {
                case SINGLE:
                    op = VSHUFPS;
                    break;
                case DOUBLE:
                    op = VSHUFPD;
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }

            if (isRegister(source2)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), asRegister(source2), selector);
            } else {
                assert isStackSlot(source2);
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), (AMD64Address) crb.asAddress(source2), selector);
            }
        }
    }

    public static final class Extract128Op extends AMD64LIRInstruction {
        public static final LIRInstructionClass<Extract128Op> TYPE = LIRInstructionClass.create(Extract128Op.class);
        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue source;
        private final int selector;

        public Extract128Op(AllocatableValue result, AllocatableValue source, int selector) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) source.getPlatformKind();

            VexMRIOp op;
            switch (kind.getScalar()) {
                case SINGLE:
                case DOUBLE:
                    op = VEXTRACTF128;
                    break;
                default:
                    AMD64 arch = (AMD64) crb.target.arch;
                    // if supported we want VEXTRACTI128
                    // on AVX1, we have to use VEXTRACTF128
                    op = arch.getFeatures().contains(CPUFeature.AVX2) ? VEXTRACTI128 : VEXTRACTF128;
                    break;
            }

            if (isRegister(result)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source), selector);
            } else {
                assert isStackSlot(result);
                op.emit(masm, AVXKind.getRegisterSize(kind), (AMD64Address) crb.asAddress(result), asRegister(source), selector);
            }
        }
    }

    public static final class Insert128Op extends AMD64LIRInstruction {
        public static final LIRInstructionClass<Insert128Op> TYPE = LIRInstructionClass.create(Insert128Op.class);
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue source1;
        @Use({REG, STACK}) protected AllocatableValue source2;
        private final int selector;

        public Insert128Op(AllocatableValue result, AllocatableValue source1, AllocatableValue source2, int selector) {
            super(TYPE);
            this.result = result;
            this.source1 = source1;
            this.source2 = source2;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();

            VexRVMIOp op;
            switch (kind.getScalar()) {
                case SINGLE:
                case DOUBLE:
                    op = VINSERTF128;
                    break;
                default:
                    AMD64 arch = (AMD64) crb.target.arch;
                    // if supported we want VINSERTI128 - on AVX1, we have to use VINSERTF128.
                    // using instructions with an incorrect data type is possible but typically
                    // results in an additional overhead whenever the value is being accessed.
                    op = arch.getFeatures().contains(CPUFeature.AVX2) ? VINSERTI128 : VINSERTF128;
                    break;
            }

            if (isRegister(source2)) {
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), asRegister(source2), selector);
            } else {
                assert isStackSlot(source2);
                op.emit(masm, AVXKind.getRegisterSize(kind), asRegister(result), asRegister(source1), (AMD64Address) crb.asAddress(source2), selector);
            }
        }
    }

    public static final class ExtractByteOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExtractByteOp> TYPE = LIRInstructionClass.create(ExtractByteOp.class);
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue vector;
        private final int selector;

        public ExtractByteOp(AllocatableValue result, AllocatableValue vector, int selector) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.DWORD;
            assert ((AMD64Kind) vector.getPlatformKind()).getScalar() == AMD64Kind.BYTE;
            this.result = result;
            this.vector = vector;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            VPEXTRB.emit(masm, XMM, asRegister(result), asRegister(vector), selector);
        }
    }

    public static final class ExtractShortOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExtractShortOp> TYPE = LIRInstructionClass.create(ExtractShortOp.class);
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue vector;
        private final int selector;

        public ExtractShortOp(AllocatableValue result, AllocatableValue vector, int selector) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.DWORD;
            assert ((AMD64Kind) vector.getPlatformKind()).getScalar() == AMD64Kind.WORD;
            this.result = result;
            this.vector = vector;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            VPEXTRW.emit(masm, XMM, asRegister(result), asRegister(vector), selector);
        }
    }

    public static final class ExtractIntOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExtractIntOp> TYPE = LIRInstructionClass.create(ExtractIntOp.class);
        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue vector;
        private final int selector;

        public ExtractIntOp(AllocatableValue result, AllocatableValue vector, int selector) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.DWORD;
            assert ((AMD64Kind) vector.getPlatformKind()).getScalar() == AMD64Kind.DWORD;
            this.result = result;
            this.vector = vector;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(result)) {
                if (selector == 0) {
                    VMOVD.emitReverse(masm, XMM, asRegister(result), asRegister(vector));
                } else {
                    VPEXTRD.emit(masm, XMM, asRegister(result), asRegister(vector), selector);
                }
            } else {
                assert isStackSlot(result);
                if (selector == 0) {
                    VMOVD.emit(masm, XMM, (AMD64Address) crb.asAddress(result), asRegister(vector));
                } else {
                    VPEXTRD.emit(masm, XMM, (AMD64Address) crb.asAddress(result), asRegister(vector), selector);
                }
            }
        }
    }

    public static final class ExtractLongOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExtractLongOp> TYPE = LIRInstructionClass.create(ExtractLongOp.class);
        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue vector;
        private final int selector;

        public ExtractLongOp(AllocatableValue result, AllocatableValue vector, int selector) {
            super(TYPE);
            assert result.getPlatformKind() == AMD64Kind.QWORD;
            assert ((AMD64Kind) vector.getPlatformKind()).getScalar() == AMD64Kind.QWORD;
            this.result = result;
            this.vector = vector;
            this.selector = selector;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(result)) {
                if (selector == 0) {
                    VMOVQ.emitReverse(masm, XMM, asRegister(result), asRegister(vector));
                } else {
                    VPEXTRQ.emit(masm, XMM, asRegister(result), asRegister(vector), selector);
                }
            } else {
                assert isStackSlot(result);
                if (selector == 0) {
                    VMOVQ.emit(masm, XMM, (AMD64Address) crb.asAddress(result), asRegister(vector));
                } else {
                    VPEXTRQ.emit(masm, XMM, (AMD64Address) crb.asAddress(result), asRegister(vector), selector);
                }
            }
        }
    }
}
