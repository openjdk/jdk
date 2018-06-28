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
import static org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexMoveOp.VMOVD;
import static org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexMoveOp.VMOVDQU;
import static org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexMoveOp.VMOVQ;
import static org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexMoveOp.VMOVSD;
import static org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexMoveOp.VMOVSS;
import static org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexMoveOp.VMOVUPD;
import static org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexMoveOp.VMOVUPS;
import static org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexRVMOp.VXORPD;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.UNINITIALIZED;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AMD64VectorAssembler;
import org.graalvm.compiler.asm.amd64.AMD64VectorAssembler.VexMoveOp;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.amd64.AMD64RestoreRegistersOp;
import org.graalvm.compiler.lir.amd64.AMD64SaveRegistersOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class AMD64VectorMove {

    @Opcode("VMOVE")
    public static final class MoveToRegOp extends AMD64VectorLIRInstruction implements ValueMoveOp {
        public static final LIRInstructionClass<MoveToRegOp> TYPE = LIRInstructionClass.create(MoveToRegOp.class);

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        public MoveToRegOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            move(crb, vasm, result, input);
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("VMOVE")
    public static final class MoveFromRegOp extends AMD64VectorLIRInstruction implements ValueMoveOp {
        public static final LIRInstructionClass<MoveFromRegOp> TYPE = LIRInstructionClass.create(MoveFromRegOp.class);

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, HINT}) protected AllocatableValue input;

        public MoveFromRegOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            move(crb, vasm, result, input);
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("VMOVE")
    public static class MoveFromConstOp extends AMD64VectorLIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<MoveFromConstOp> TYPE = LIRInstructionClass.create(MoveFromConstOp.class);

        @Def({REG, STACK}) protected AllocatableValue result;
        private final JavaConstant input;

        public MoveFromConstOp(AllocatableValue result, JavaConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (isRegister(result)) {
                const2reg(crb, vasm, (RegisterValue) result, input);
            } else {
                assert isStackSlot(result);
                AMD64Move.const2stack(crb, vasm, result, input);
            }
        }

        @Override
        public Constant getConstant() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("VSTACKMOVE")
    public static final class StackMoveOp extends AMD64VectorLIRInstruction implements ValueMoveOp {
        public static final LIRInstructionClass<StackMoveOp> TYPE = LIRInstructionClass.create(StackMoveOp.class);

        @Def({STACK}) protected AllocatableValue result;
        @Use({STACK, HINT}) protected AllocatableValue input;
        @Alive({STACK, UNINITIALIZED}) private AllocatableValue backupSlot;

        private Register scratch;

        public StackMoveOp(AllocatableValue result, AllocatableValue input, Register scratch, AllocatableValue backupSlot) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.backupSlot = backupSlot;
            this.scratch = scratch;
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler masm) {
            // backup scratch register
            move(crb, masm, backupSlot, scratch.asValue(backupSlot.getValueKind()));
            // move stack slot
            move(crb, masm, scratch.asValue(getInput().getValueKind()), getInput());
            move(crb, masm, getResult(), scratch.asValue(getResult().getValueKind()));
            // restore scratch register
            move(crb, masm, scratch.asValue(backupSlot.getValueKind()), backupSlot);

        }
    }

    public abstract static class VectorMemOp extends AMD64VectorLIRInstruction {

        protected final AVXSize size;
        protected final VexMoveOp op;

        @Use({COMPOSITE}) protected AMD64AddressValue address;
        @State protected LIRFrameState state;

        protected VectorMemOp(LIRInstructionClass<? extends VectorMemOp> c, AVXSize size, VexMoveOp op, AMD64AddressValue address, LIRFrameState state) {
            super(c);
            this.size = size;
            this.op = op;
            this.address = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(AMD64VectorAssembler vasm);

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (state != null) {
                crb.recordImplicitException(vasm.position(), state);
            }
            emitMemAccess(vasm);
        }
    }

    public static final class VectorLoadOp extends VectorMemOp {
        public static final LIRInstructionClass<VectorLoadOp> TYPE = LIRInstructionClass.create(VectorLoadOp.class);

        @Def({REG}) protected AllocatableValue result;

        public VectorLoadOp(AVXSize size, VexMoveOp op, AllocatableValue result, AMD64AddressValue address, LIRFrameState state) {
            super(TYPE, size, op, address, state);
            this.result = result;
        }

        @Override
        public void emitMemAccess(AMD64VectorAssembler vasm) {
            op.emit(vasm, size, asRegister(result), address.toAddress());
        }
    }

    public static class VectorStoreOp extends VectorMemOp {
        public static final LIRInstructionClass<VectorStoreOp> TYPE = LIRInstructionClass.create(VectorStoreOp.class);

        @Use({REG}) protected AllocatableValue input;

        public VectorStoreOp(AVXSize size, VexMoveOp op, AMD64AddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, size, op, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(AMD64VectorAssembler vasm) {
            op.emit(vasm, size, address.toAddress(), asRegister(input));
        }
    }

    @Opcode("SAVE_REGISTER")
    public static class SaveRegistersOp extends AMD64SaveRegistersOp {
        public static final LIRInstructionClass<SaveRegistersOp> TYPE = LIRInstructionClass.create(SaveRegistersOp.class);

        public SaveRegistersOp(Register[] savedRegisters, AllocatableValue[] slots, boolean supportsRemove) {
            super(TYPE, savedRegisters, slots, supportsRemove);
        }

        @Override
        protected void saveRegister(CompilationResultBuilder crb, AMD64MacroAssembler masm, StackSlot result, Register register) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            if (kind.isXMM()) {
                VexMoveOp op;
                if (kind.getVectorLength() > 1) {
                    op = getVectorMoveOp(kind.getScalar());
                } else {
                    op = getScalarMoveOp(kind);
                }

                AMD64Address addr = (AMD64Address) crb.asAddress(result);
                op.emit((AMD64VectorAssembler) masm, AVXKind.getRegisterSize(kind), addr, register);
            } else {
                super.saveRegister(crb, masm, result, register);
            }
        }
    }

    @Opcode("RESTORE_REGISTER")
    public static final class RestoreRegistersOp extends AMD64RestoreRegistersOp {
        public static final LIRInstructionClass<RestoreRegistersOp> TYPE = LIRInstructionClass.create(RestoreRegistersOp.class);

        public RestoreRegistersOp(AllocatableValue[] source, AMD64SaveRegistersOp save) {
            super(TYPE, source, save);
        }

        @Override
        protected void restoreRegister(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register register, StackSlot input) {
            AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
            if (kind.isXMM()) {
                VexMoveOp op;
                if (kind.getVectorLength() > 1) {
                    op = getVectorMoveOp(kind.getScalar());
                } else {
                    op = getScalarMoveOp(kind);
                }

                AMD64Address addr = (AMD64Address) crb.asAddress(input);
                op.emit((AMD64VectorAssembler) masm, AVXKind.getRegisterSize(kind), register, addr);
            } else {
                super.restoreRegister(crb, masm, register, input);
            }
        }
    }

    private static VexMoveOp getScalarMoveOp(AMD64Kind kind) {
        switch (kind) {
            case SINGLE:
                return VMOVSS;
            case DOUBLE:
                return VMOVSD;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private static VexMoveOp getVectorMoveOp(AMD64Kind kind) {
        switch (kind) {
            case SINGLE:
                return VMOVUPS;
            case DOUBLE:
                return VMOVUPD;
            default:
                return VMOVDQU;
        }
    }

    private static VexMoveOp getVectorMemMoveOp(AMD64Kind kind) {
        switch (AVXKind.getDataSize(kind)) {
            case DWORD:
                return VMOVD;
            case QWORD:
                return VMOVQ;
            default:
                return getVectorMoveOp(kind.getScalar());
        }
    }

    private static void move(CompilationResultBuilder crb, AMD64VectorAssembler vasm, AllocatableValue result, Value input) {
        VexMoveOp op;
        AVXSize size;
        AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            size = AVXKind.getRegisterSize(kind);
            if (isRegister(input) && isRegister(result)) {
                op = getVectorMoveOp(kind.getScalar());
            } else {
                op = getVectorMemMoveOp(kind);
            }
        } else {
            size = AVXSize.XMM;
            if (isRegister(input) && isRegister(result)) {
                op = getVectorMoveOp(kind);
            } else {
                op = getScalarMoveOp(kind);
            }
        }

        if (isRegister(input)) {
            if (isRegister(result)) {
                if (!asRegister(input).equals(asRegister(result))) {
                    op.emit(vasm, size, asRegister(result), asRegister(input));
                }
            } else {
                assert isStackSlot(result);
                op.emit(vasm, size, (AMD64Address) crb.asAddress(result), asRegister(input));
            }
        } else {
            assert isStackSlot(input) && isRegister(result);
            op.emit(vasm, size, asRegister(result), (AMD64Address) crb.asAddress(input));
        }
    }

    private static void const2reg(CompilationResultBuilder crb, AMD64VectorAssembler vasm, RegisterValue result, JavaConstant input) {
        if (input.isDefaultForKind()) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            Register register = result.getRegister();
            VXORPD.emit(vasm, AVXKind.getRegisterSize(kind), register, register, register);
            return;
        }

        AMD64Address address;
        switch (input.getJavaKind()) {
            case Float:
                address = (AMD64Address) crb.asFloatConstRef(input);
                break;

            case Double:
                address = (AMD64Address) crb.asDoubleConstRef(input);
                break;

            default:
                throw GraalError.shouldNotReachHere();
        }
        VexMoveOp op = getScalarMoveOp((AMD64Kind) result.getPlatformKind());
        op.emit(vasm, AVXSize.XMM, asRegister(result), address);
    }

    public static final class AVXMoveToIntOp extends AMD64VectorLIRInstruction {
        public static final LIRInstructionClass<AVXMoveToIntOp> TYPE = LIRInstructionClass.create(AVXMoveToIntOp.class);

        @Opcode private final VexMoveOp opcode;

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        public AVXMoveToIntOp(VexMoveOp opcode, AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (isRegister(result)) {
                opcode.emitReverse(vasm, AVXSize.XMM, asRegister(result), asRegister(input));
            } else {
                opcode.emit(vasm, AVXSize.XMM, (AMD64Address) crb.asAddress(result), asRegister(input));
            }
        }
    }
}
