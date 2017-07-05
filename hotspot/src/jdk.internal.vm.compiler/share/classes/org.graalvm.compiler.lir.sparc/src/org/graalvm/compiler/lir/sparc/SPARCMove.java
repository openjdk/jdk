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

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.MEMBAR_STORE_LOAD;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isCPURegister;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isDoubleFloatRegister;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSimm13;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSingleFloatRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.UNINITIALIZED;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static java.lang.Math.max;
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARCKind.DOUBLE;
import static jdk.vm.ci.sparc.SPARCKind.SINGLE;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;

import java.util.Set;

import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.DataPointerConstant;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.ImplicitNullCheck;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.StandardOp.NullCheck;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARC.CPUFeature;
import jdk.vm.ci.sparc.SPARCKind;

public class SPARCMove {

    public static class LoadInlineConstant extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction, LoadConstantOp {
        public static final LIRInstructionClass<LoadInlineConstant> TYPE = LIRInstructionClass.create(LoadInlineConstant.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);
        private JavaConstant constant;
        @Def({REG, STACK}) AllocatableValue result;

        public LoadInlineConstant(JavaConstant constant, AllocatableValue result) {
            super(TYPE, SIZE);
            this.constant = constant;
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (isRegister(result)) {
                const2reg(crb, masm, result, g0, constant, getDelayedControlTransfer());
            } else if (isStackSlot(result)) {
                StackSlot slot = asStackSlot(result);
                const2stack(crb, masm, slot, g0, getDelayedControlTransfer(), constant);
            }
        }

        @Override
        public Constant getConstant() {
            return constant;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    public static class LoadConstantFromTable extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<LoadConstantFromTable> TYPE = LIRInstructionClass.create(LoadConstantFromTable.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1, 8);

        private Constant constant;
        @Def({REG, STACK}) AllocatableValue result;
        @Use({REG}) private AllocatableValue constantTableBase;

        public LoadConstantFromTable(Constant constant, AllocatableValue constantTableBase, AllocatableValue result) {
            super(TYPE, SIZE);
            this.constant = constant;
            this.result = result;
            this.constantTableBase = constantTableBase;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            final int byteCount = result.getPlatformKind().getSizeInBytes();
            assert byteCount > 1 : "Byte values must not be loaded via constant table";
            Register baseRegister = asRegister(constantTableBase);
            if (isRegister(result)) {
                Register resultRegister = asRegister(result);
                loadFromConstantTable(crb, masm, byteCount, baseRegister, constant, resultRegister, getDelayedControlTransfer());
            } else if (isStackSlot(result)) {
                try (ScratchRegister scratch = masm.getScratchRegister()) {
                    Register scratchRegister = scratch.getRegister();
                    loadFromConstantTable(crb, masm, byteCount, baseRegister, constant, scratchRegister, getDelayedControlTransfer());
                    StackSlot slot = asStackSlot(result);
                    reg2stack(crb, masm, slot, scratchRegister.asValue(), getDelayedControlTransfer());
                }
            }
        }
    }

    @Opcode("MOVE")
    public static class Move extends SPARCLIRInstruction implements ValueMoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<Move> TYPE = LIRInstructionClass.create(Move.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(8);

        @Def({REG, STACK, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        public Move(AllocatableValue result, AllocatableValue input) {
            super(TYPE, SIZE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            move(crb, masm, getResult(), getInput(), getDelayedControlTransfer());
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

    /**
     * Move between floating-point and general purpose register domain.
     */
    @Opcode("MOVE_FPGP")
    public static final class MoveFpGp extends SPARCLIRInstruction implements ValueMoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MoveFpGp> TYPE = LIRInstructionClass.create(MoveFpGp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Temp({STACK, ILLEGAL}) protected AllocatableValue temp;

        public MoveFpGp(AllocatableValue result, AllocatableValue input, AllocatableValue temp) {
            super(TYPE, SIZE);
            this.result = result;
            this.input = input;
            this.temp = temp;
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
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCKind inputKind = (SPARCKind) input.getPlatformKind();
            SPARCKind resultKind = (SPARCKind) result.getPlatformKind();
            if (AllocatableValue.ILLEGAL.equals(temp)) {
                moveDirect(crb, masm, inputKind, resultKind);
            } else {
                moveViaStack(crb, masm, inputKind, resultKind);
            }
        }

        private void moveDirect(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCKind inputKind, SPARCKind resultKind) {
            getDelayedControlTransfer().emitControlTransfer(crb, masm);
            if (resultKind == SINGLE) {
                if (inputKind == WORD) {
                    masm.movwtos(asRegister(input, WORD), asRegister(result, SINGLE));
                } else {
                    throw GraalError.shouldNotReachHere("inputKind: " + inputKind);
                }
            } else if (resultKind == DOUBLE) {
                if (inputKind == WORD) {
                    masm.movxtod(asRegister(input, WORD), asRegister(result, DOUBLE));
                } else {
                    masm.movxtod(asRegister(input, XWORD), asRegister(result, DOUBLE));
                }
            } else if (inputKind == SINGLE) {
                if (resultKind == WORD) {
                    masm.movstosw(asRegister(input, SINGLE), asRegister(result, WORD));
                } else {
                    masm.movstouw(asRegister(input, SINGLE), asRegister(result, WORD));
                }
            } else if (inputKind == DOUBLE) {
                if (resultKind == XWORD) {
                    masm.movdtox(asRegister(input, DOUBLE), asRegister(result, XWORD));
                } else {
                    throw GraalError.shouldNotReachHere();
                }
            }
        }

        private void moveViaStack(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCKind inputKind, SPARCKind resultKind) {
            int resultKindSize = resultKind.getSizeInBytes();
            assert inputKind.getSizeInBytes() == resultKindSize;
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                SPARCAddress tempAddress = generateSimm13OffsetLoad((SPARCAddress) crb.asAddress(temp), masm, scratch);
                masm.st(asRegister(input), tempAddress, resultKindSize);
                getDelayedControlTransfer().emitControlTransfer(crb, masm);
                masm.ld(tempAddress, asRegister(result), resultKindSize, false);
            }
        }
    }

    public abstract static class MemOp extends SPARCLIRInstruction implements ImplicitNullCheck {
        public static final LIRInstructionClass<MemOp> TYPE = LIRInstructionClass.create(MemOp.class);

        protected final PlatformKind kind;
        @Use({COMPOSITE}) protected SPARCAddressValue address;
        @State protected LIRFrameState state;

        public MemOp(LIRInstructionClass<? extends MemOp> c, SizeEstimate size, PlatformKind kind, SPARCAddressValue address, LIRFrameState state) {
            super(c, size);
            this.kind = kind;
            this.address = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitMemAccess(crb, masm);
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && address.isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static final class LoadOp extends MemOp implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<LoadOp> TYPE = LIRInstructionClass.create(LoadOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        @Def({REG}) protected AllocatableValue result;
        protected boolean signExtend;

        public LoadOp(PlatformKind kind, AllocatableValue result, SPARCAddressValue address, LIRFrameState state) {
            this(kind, result, address, state, false);
        }

        public LoadOp(PlatformKind kind, AllocatableValue result, SPARCAddressValue address, LIRFrameState state, boolean signExtend) {
            super(TYPE, SIZE, kind, address, state);
            this.result = result;
            this.signExtend = signExtend;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitLoad(crb, masm, address.toAddress(), result, signExtend, kind, getDelayedControlTransfer(), state);
        }
    }

    public static final class LoadAddressOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<LoadAddressOp> TYPE = LIRInstructionClass.create(LoadAddressOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(8);

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected SPARCAddressValue addressValue;

        public LoadAddressOp(AllocatableValue result, SPARCAddressValue address) {
            super(TYPE, SIZE);
            this.result = result;
            this.addressValue = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCAddress address = addressValue.toAddress();
            loadEffectiveAddress(crb, masm, address, asRegister(result, XWORD), getDelayedControlTransfer());
        }
    }

    public static final class LoadDataAddressOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<LoadDataAddressOp> TYPE = LIRInstructionClass.create(LoadDataAddressOp.class);

        @Def({REG}) protected AllocatableValue result;
        private final DataPointerConstant data;

        public LoadDataAddressOp(AllocatableValue result, DataPointerConstant data) {
            super(TYPE);
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            // HotSpot for SPARC requires at least word alignment
            SPARCAddress addr = (SPARCAddress) crb.recordDataReferenceInCode(data, max(SPARCKind.WORD.getSizeInBytes(), data.getAlignment()));
            assert addr == masm.getPlaceholder(-1);
            final boolean forceRelocatable = true;
            Register dstReg = asRegister(result);
            masm.setx(0, dstReg, forceRelocatable);
        }

        @Override
        public SizeEstimate estimateSize() {
            return SizeEstimate.create(8, data.getSerializedSize());
        }
    }

    public static final class MembarOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MembarOp> TYPE = LIRInstructionClass.create(MembarOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        private final int barriers;

        public MembarOp(final int barriers) {
            super(TYPE, SIZE);
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            getDelayedControlTransfer().emitControlTransfer(crb, masm);
            masm.membar(MEMBAR_STORE_LOAD);
        }

        @Override
        public void verify() {
            assert barriers == STORE_LOAD : String.format("Got barriers 0x%x; On SPARC only STORE_LOAD barriers are accepted; all other barriers are not neccessary due to TSO", barriers);
        }
    }

    public static final class NullCheckOp extends SPARCLIRInstruction implements NullCheck, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<NullCheckOp> TYPE = LIRInstructionClass.create(NullCheckOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        @Use({COMPOSITE}) protected SPARCAddressValue input;
        @State protected LIRFrameState state;

        public NullCheckOp(SPARCAddressValue input, LIRFrameState state) {
            super(TYPE, SIZE);
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            getDelayedControlTransfer().emitControlTransfer(crb, masm);
            SPARCAddress addr = input.toAddress();
            crb.recordImplicitException(masm.position(), state);
            // Just need to check whether this is a valid address or not; alignment is not
            // checked
            masm.ldub(addr, g0);
        }

        @Override
        public Value getCheckedValue() {
            return input;
        }

        @Override
        public LIRFrameState getState() {
            return state;
        }
    }

    @Opcode("CAS")
    public static final class CompareAndSwapOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<CompareAndSwapOp> TYPE = LIRInstructionClass.create(CompareAndSwapOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Def({REG, HINT}) protected AllocatableValue result;
        @Alive({REG}) protected AllocatableValue address;
        @Alive({REG}) protected AllocatableValue cmpValue;
        @Use({REG}) protected AllocatableValue newValue;

        public CompareAndSwapOp(AllocatableValue result, AllocatableValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            super(TYPE, SIZE);
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            move(crb, masm, result, newValue, SPARCDelayedControlTransfer.DUMMY);
            compareAndSwap(crb, masm, address, cmpValue, result, getDelayedControlTransfer());
        }
    }

    public static final class StackLoadAddressOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<StackLoadAddressOp> TYPE = LIRInstructionClass.create(StackLoadAddressOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected AllocatableValue slot;

        public StackLoadAddressOp(AllocatableValue result, AllocatableValue slot) {
            super(TYPE, SIZE);
            this.result = result;
            this.slot = slot;
            assert slot instanceof VirtualStackSlot || slot instanceof StackSlot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCAddress address = (SPARCAddress) crb.asAddress(slot);
            loadEffectiveAddress(crb, masm, address, asRegister(result, XWORD), getDelayedControlTransfer());
        }
    }

    private static void loadEffectiveAddress(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCAddress address, Register result, SPARCDelayedControlTransfer delaySlotHolder) {
        if (address.getIndex().equals(Register.None)) {
            if (isSimm13(address.getDisplacement())) {
                delaySlotHolder.emitControlTransfer(crb, masm);
                masm.add(address.getBase(), address.getDisplacement(), result);
            } else {
                assert result.encoding() != address.getBase().encoding();
                masm.setx(address.getDisplacement(), result, false);
                // No relocation, therefore, the add can be delayed as well
                delaySlotHolder.emitControlTransfer(crb, masm);
                masm.add(address.getBase(), result, result);
            }
        } else {
            delaySlotHolder.emitControlTransfer(crb, masm);
            masm.add(address.getBase(), address.getIndex(), result);
        }
    }

    public static class StoreOp extends MemOp implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<StoreOp> TYPE = LIRInstructionClass.create(StoreOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        @Use({REG}) protected AllocatableValue input;

        public StoreOp(PlatformKind kind, SPARCAddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, SIZE, kind, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitStore(input, address.toAddress(), kind, getDelayedControlTransfer(), state, crb, masm);
        }
    }

    public static final class StoreConstantOp extends MemOp implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<StoreConstantOp> TYPE = LIRInstructionClass.create(StoreConstantOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        protected final JavaConstant input;

        public StoreConstantOp(PlatformKind kind, SPARCAddressValue address, JavaConstant input, LIRFrameState state) {
            super(TYPE, SIZE, kind, address, state);
            this.input = input;
            if (!input.isDefaultForKind()) {
                throw GraalError.shouldNotReachHere("Can only store null constants to memory");
            }
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                SPARCAddress addr = generateSimm13OffsetLoad(address.toAddress(), masm, scratch);
                getDelayedControlTransfer().emitControlTransfer(crb, masm);
                if (state != null) {
                    crb.recordImplicitException(masm.position(), state);
                }
                int byteCount = kind.getSizeInBytes();
                masm.st(g0, addr, byteCount);
            }
        }
    }

    public static void move(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        move(crb, masm, result, g0, input, delaySlotLir);
    }

    public static void move(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Register constantTableBase, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(crb, masm, result, input, delaySlotLir);
            } else if (isStackSlot(result)) {
                reg2stack(crb, masm, result, input, delaySlotLir);
            } else {
                throw GraalError.shouldNotReachHere("Result is a: " + result);
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                SPARCAddress inputAddress = (SPARCAddress) crb.asAddress(input);
                emitLoad(crb, masm, inputAddress, result, false, input.getPlatformKind(), delaySlotLir, null);
            } else if (isStackSlot(result)) {
                stack2stack(crb, masm, result, input, delaySlotLir);
            } else {
                throw GraalError.shouldNotReachHere("Result is a: " + result);
            }
        } else if (isJavaConstant(input)) {
            JavaConstant constant = asJavaConstant(input);
            if (isRegister(result)) {
                const2reg(crb, masm, result, constantTableBase, constant, delaySlotLir);
            } else if (isStackSlot(result)) {
                const2stack(crb, masm, result, constantTableBase, delaySlotLir, constant);
            } else {
                throw GraalError.shouldNotReachHere("Result is a: " + result);
            }
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    public static void const2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Register constantTableBase, SPARCDelayedControlTransfer delaySlotLir, JavaConstant constant) {
        if (constant.isDefaultForKind() || constant.isNull()) {
            SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
            emitStore(g0.asValue(LIRKind.combine(result)), resultAddress, result.getPlatformKind(), delaySlotLir, null, crb, masm);
        } else {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Value scratchRegisterValue = sc.getRegister().asValue(LIRKind.combine(result));
                const2reg(crb, masm, scratchRegisterValue, constantTableBase, constant, SPARCDelayedControlTransfer.DUMMY);
                SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
                emitStore(scratchRegisterValue, resultAddress, result.getPlatformKind(), delaySlotLir, null, crb, masm);
            }
        }
    }

    public static void stack2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, PlatformKind resultKind, PlatformKind inputKind, Value result, Value input,
                    SPARCDelayedControlTransfer delaySlotLir) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            SPARCAddress inputAddress = (SPARCAddress) crb.asAddress(input);
            Value scratchRegisterValue = sc.getRegister().asValue(LIRKind.combine(input));
            emitLoad(crb, masm, inputAddress, scratchRegisterValue, false, inputKind, SPARCDelayedControlTransfer.DUMMY, null);
            SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
            emitStore(scratchRegisterValue, resultAddress, resultKind, delaySlotLir, null, crb, masm);
        }
    }

    public static void stack2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        stack2stack(crb, masm, result.getPlatformKind(), input.getPlatformKind(), result, input, delaySlotLir);
    }

    public static void reg2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
        emitStore(input, resultAddress, result.getPlatformKind(), delaySlotLir, null, crb, masm);
    }

    public static void reg2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        final Register src = asRegister(input);
        final Register dst = asRegister(result);
        if (src.equals(dst)) {
            return;
        }
        delaySlotLir.emitControlTransfer(crb, masm);
        if (isCPURegister(src) && isCPURegister(dst)) {
            masm.mov(src, dst);
        } else if (isSingleFloatRegister(src) && isSingleFloatRegister(dst)) {
            masm.fsrc2s(src, dst);
        } else if (isDoubleFloatRegister(src) && isDoubleFloatRegister(dst)) {
            masm.fsrc2d(src, dst);
        } else {
            throw GraalError.shouldNotReachHere(String.format("Trying to move between register domains src: %s dst: %s", src, dst));
        }
    }

    /**
     * Guarantees that the given SPARCAddress given before is loadable by subsequent load/store
     * instruction. If the displacement exceeds the simm13 value range, the value is put into a
     * scratch register.
     *
     * @param addr Address to modify
     * @param masm assembler to output the potential code to store the value in the scratch register
     * @param scratch The register as scratch to use
     * @return a loadable SPARCAddress
     */
    public static SPARCAddress generateSimm13OffsetLoad(SPARCAddress addr, SPARCMacroAssembler masm, Register scratch) {
        boolean displacementOutOfBound = addr.getIndex().equals(Register.None) && !SPARCAssembler.isSimm13(addr.getDisplacement());
        if (displacementOutOfBound) {
            masm.setx(addr.getDisplacement(), scratch, false);
            return new SPARCAddress(addr.getBase(), scratch);
        } else {
            return addr;
        }
    }

    public static void const2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Register constantTableBase, JavaConstant input, SPARCDelayedControlTransfer delaySlotLir) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            Set<CPUFeature> cpuFeatures = ((SPARC) masm.target.arch).getFeatures();
            boolean hasVIS1 = cpuFeatures.contains(CPUFeature.VIS1);
            boolean hasVIS3 = cpuFeatures.contains(CPUFeature.VIS3);
            Register resultRegister = asRegister(result);
            int byteCount = result.getPlatformKind().getSizeInBytes();
            switch (input.getJavaKind().getStackKind()) {
                case Int:
                    if (input.isDefaultForKind()) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.clr(resultRegister);
                    } else if (isSimm13(input.asInt())) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.or(g0, input.asInt(), resultRegister);
                    } else {
                        if (constantTableBase.equals(g0)) {
                            throw GraalError.shouldNotReachHere();
                        } else {
                            loadFromConstantTable(crb, masm, byteCount, constantTableBase, input, resultRegister, delaySlotLir);
                        }
                    }
                    break;
                case Long:
                    if (input.isDefaultForKind()) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.clr(resultRegister);
                    } else if (isSimm13(input.asLong())) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.or(g0, (int) input.asLong(), resultRegister);
                    } else {
                        loadFromConstantTable(crb, masm, byteCount, constantTableBase, input, resultRegister, delaySlotLir);
                    }
                    break;
                case Float: {
                    float constant = input.asFloat();
                    int constantBits = java.lang.Float.floatToIntBits(constant);
                    if (hasVIS1 && constantBits == 0) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.fzeros(resultRegister);
                    } else {
                        if (hasVIS3 && isSimm13(constantBits)) {
                            masm.or(g0, constantBits, scratch);
                            delaySlotLir.emitControlTransfer(crb, masm);
                            masm.movwtos(scratch, resultRegister);
                        } else {
                            // First load the address into the scratch register
                            loadFromConstantTable(crb, masm, byteCount, constantTableBase, input, resultRegister, delaySlotLir);
                        }
                    }
                    break;
                }
                case Double: {
                    double constant = input.asDouble();
                    long constantBits = java.lang.Double.doubleToRawLongBits(constant);
                    if (hasVIS1 && constantBits == 0) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.fzerod(resultRegister);
                    } else {
                        if (hasVIS3 && isSimm13(constantBits)) {
                            masm.or(g0, (int) constantBits, scratch);
                            delaySlotLir.emitControlTransfer(crb, masm);
                            masm.movxtod(scratch, resultRegister);
                        } else {
                            loadFromConstantTable(crb, masm, byteCount, constantTableBase, input, resultRegister, delaySlotLir);
                        }
                    }
                    break;
                }
                case Object:
                    if (input.isNull()) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.clr(resultRegister);
                    } else {
                        loadFromConstantTable(crb, masm, byteCount, constantTableBase, input, resultRegister, delaySlotLir);
                    }
                    break;
                default:
                    throw GraalError.shouldNotReachHere("missing: " + input.getJavaKind());
            }
        }
    }

    protected static void compareAndSwap(CompilationResultBuilder crb, SPARCMacroAssembler masm, AllocatableValue address, AllocatableValue cmpValue, AllocatableValue newValue,
                    SPARCDelayedControlTransfer delay) {
        delay.emitControlTransfer(crb, masm);
        switch ((SPARCKind) cmpValue.getPlatformKind()) {
            case WORD:
                masm.cas(asRegister(address), asRegister(cmpValue), asRegister(newValue));
                break;
            case XWORD:
                masm.casx(asRegister(address), asRegister(cmpValue), asRegister(newValue));
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public static void emitLoad(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCAddress address, Value result, boolean signExtend, PlatformKind kind,
                    SPARCDelayedControlTransfer delayedControlTransfer, LIRFrameState state) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            final SPARCAddress addr = generateSimm13OffsetLoad(address, masm, scratch);
            final Register dst = asRegister(result);
            delayedControlTransfer.emitControlTransfer(crb, masm);
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            int byteCount = kind.getSizeInBytes();
            masm.ld(addr, dst, byteCount, signExtend);
        }
    }

    public static void emitStore(Value input, SPARCAddress address, PlatformKind kind, SPARCDelayedControlTransfer delayedControlTransfer, LIRFrameState state, CompilationResultBuilder crb,
                    SPARCMacroAssembler masm) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            SPARCAddress addr = generateSimm13OffsetLoad(address, masm, scratch);
            delayedControlTransfer.emitControlTransfer(crb, masm);
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            int byteCount = kind.getSizeInBytes();
            masm.st(asRegister(input), addr, byteCount);
        }
    }

    /**
     * This method creates a load from the constant section. It automatically respects the different
     * patterns used for small constant sections (<8k) and large constant sections (>=8k). The
     * generated patterns by this method must be understood by
     * CodeInstaller::pd_patch_DataSectionReference (jvmciCodeInstaller_sparc.cpp).
     */
    public static void loadFromConstantTable(CompilationResultBuilder crb, SPARCMacroAssembler masm, int byteCount, Register constantTableBase, Constant input, Register dest,
                    SPARCDelayedControlTransfer delaySlotInstruction) {
        SPARCAddress address;
        ScratchRegister scratch = null;
        try {
            if (masm.isImmediateConstantLoad()) {
                address = new SPARCAddress(constantTableBase, 0);
                // Make delayed only, when using immediate constant load.
                delaySlotInstruction.emitControlTransfer(crb, masm);
                crb.recordDataReferenceInCode(input, byteCount);
            } else {
                scratch = masm.getScratchRegister();
                Register sr = scratch.getRegister();
                crb.recordDataReferenceInCode(input, byteCount);
                masm.sethix(0, sr, true);
                address = new SPARCAddress(sr, 0);
            }
            masm.ld(address, dest, byteCount, false);
        } finally {
            if (scratch != null) {
                scratch.close();
            }
        }
    }
}
