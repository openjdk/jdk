/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.UNINITIALIZED;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.DataPointerConstant;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.StandardOp.NullCheck;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.MemoryBarriers;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AArch64Move {

    public static class LoadInlineConstant extends AArch64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<LoadInlineConstant> TYPE = LIRInstructionClass.create(LoadInlineConstant.class);

        private JavaConstant constant;
        @Def({REG, STACK}) AllocatableValue result;

        public LoadInlineConstant(JavaConstant constant, AllocatableValue result) {
            super(TYPE);
            this.constant = constant;
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            if (isRegister(result)) {
                const2reg(crb, masm, result, constant);
            } else if (isStackSlot(result)) {
                StackSlot slot = asStackSlot(result);
                const2stack(crb, masm, slot, constant);
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

    @Opcode("MOVE")
    public static class Move extends AArch64LIRInstruction implements ValueMoveOp {
        public static final LIRInstructionClass<Move> TYPE = LIRInstructionClass.create(Move.class);

        @Def({REG, STACK, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        public Move(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
            assert !(isStackSlot(result) && isStackSlot(input));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            move(crb, masm, getResult(), getInput());
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

    public static class LoadAddressOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<LoadAddressOp> TYPE = LIRInstructionClass.create(LoadAddressOp.class);

        @Def protected AllocatableValue result;
        @Use(COMPOSITE) protected AArch64AddressValue address;

        public LoadAddressOp(AllocatableValue result, AArch64AddressValue address) {
            super(TYPE);
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            AArch64Address adr = address.toAddress();
            masm.loadAddress(dst, adr, address.getScaleFactor());
        }
    }

    public static class LoadDataOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<LoadDataOp> TYPE = LIRInstructionClass.create(LoadDataOp.class);

        @Def protected AllocatableValue result;
        private final DataPointerConstant data;

        public LoadDataOp(AllocatableValue result, DataPointerConstant data) {
            super(TYPE);
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            if (crb.compilationResult.isImmutablePIC()) {
                crb.recordDataReferenceInCode(data);
                masm.addressOf(dst);
            } else {
                masm.loadAddress(dst, (AArch64Address) crb.recordDataReferenceInCode(data), data.getAlignment());
            }
        }
    }

    public static class StackLoadAddressOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<StackLoadAddressOp> TYPE = LIRInstructionClass.create(StackLoadAddressOp.class);

        @Def protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected AllocatableValue slot;

        public StackLoadAddressOp(AllocatableValue result, AllocatableValue slot) {
            super(TYPE);
            assert slot instanceof VirtualStackSlot || slot instanceof StackSlot;
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            try (ScratchRegister addrReg = masm.getScratchRegister()) {
                AArch64Address address = loadStackSlotAddress(crb, masm, (StackSlot) slot, addrReg.getRegister());
                PlatformKind kind = AArch64Kind.QWORD;
                masm.loadAddress(asRegister(result, kind), address, kind.getSizeInBytes());
            }
        }
    }

    public static class MembarOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<MembarOp> TYPE = LIRInstructionClass.create(MembarOp.class);

        // For future use.
        @SuppressWarnings("unused") private final int barriers;

        public MembarOp(int barriers) {
            super(TYPE);
            this.barriers = barriers;
        }

        @Override
        // The odd-looking @SuppressWarnings("all") is here because of
        // a compiler bug which warns that crb is unused, and also
        // warns that @SuppressWarnings("unused") is unnecessary.
        public void emitCode(@SuppressWarnings("all") CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert barriers >= MemoryBarriers.LOAD_LOAD && barriers <= (MemoryBarriers.STORE_STORE | MemoryBarriers.STORE_LOAD | MemoryBarriers.LOAD_STORE | MemoryBarriers.LOAD_LOAD);
            switch (barriers) {
                case MemoryBarriers.STORE_STORE:
                    masm.dmb(AArch64MacroAssembler.BarrierKind.STORE_STORE);
                    break;
                case MemoryBarriers.LOAD_LOAD:
                case MemoryBarriers.LOAD_STORE:
                case MemoryBarriers.LOAD_LOAD | MemoryBarriers.LOAD_STORE:
                    masm.dmb(AArch64MacroAssembler.BarrierKind.LOAD_LOAD);
                    break;
                default:
                    masm.dmb(AArch64MacroAssembler.BarrierKind.ANY_ANY);
                    break;
            }
        }
    }

    abstract static class MemOp extends AArch64LIRInstruction implements StandardOp.ImplicitNullCheck {

        protected final AArch64Kind kind;
        @Use({COMPOSITE}) protected AArch64AddressValue addressValue;
        @State protected LIRFrameState state;

        MemOp(LIRInstructionClass<? extends MemOp> c, AArch64Kind kind, AArch64AddressValue address, LIRFrameState state) {
            super(c);
            this.kind = kind;
            this.addressValue = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int prePosition = masm.position();
            emitMemAccess(crb, masm);
            if (state != null) {
                int implicitExceptionPosition = prePosition;
                // Adjust implicit exception position if this ldr/str has been merged to ldp/stp.
                if (kind.isInteger() && prePosition == masm.position() && masm.isImmLoadStoreMerged()) {
                    implicitExceptionPosition = prePosition - 4;
                    if (crb.isImplicitExceptionExist(implicitExceptionPosition)) {
                        return;
                    }
                }
                crb.recordImplicitException(implicitExceptionPosition, state);
            }
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            int displacement = addressValue.getDisplacement();
            if (state == null && value.equals(addressValue.getBase()) && addressValue.getOffset().equals(Value.ILLEGAL) && displacement >= 0 && displacement < implicitNullCheckLimit) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static final class LoadOp extends MemOp {
        public static final LIRInstructionClass<LoadOp> TYPE = LIRInstructionClass.create(LoadOp.class);

        @Def protected AllocatableValue result;

        public LoadOp(AArch64Kind kind, AllocatableValue result, AArch64AddressValue address, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.result = result;
        }

        @Override
        protected void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Address address = addressValue.toAddress();
            Register dst = asRegister(result);

            int destSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            int srcSize = kind.getSizeInBytes() * Byte.SIZE;
            if (kind.isInteger()) {
                masm.ldr(srcSize, dst, address);
            } else {
                assert srcSize == destSize;
                masm.fldr(srcSize, dst, address);
            }
        }
    }

    public static final class VolatileLoadOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VolatileLoadOp> TYPE = LIRInstructionClass.create(VolatileLoadOp.class);
        protected final AArch64Kind kind;
        @State protected LIRFrameState state;
        @Def protected AllocatableValue result;
        @Use protected AllocatableValue address;

        public VolatileLoadOp(AArch64Kind kind, AllocatableValue result, AllocatableValue address, LIRFrameState state) {
            super(TYPE);
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
            if (state != null) {
                throw GraalError.shouldNotReachHere("Can't handle implicit null check");
            }
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int srcSize = kind.getSizeInBytes() * Byte.SIZE;
            int destSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            if (kind.isInteger()) {
                masm.ldar(srcSize, asRegister(result), asRegister(address));
            } else {
                assert srcSize == destSize;
                try (ScratchRegister r1 = masm.getScratchRegister()) {
                    Register rscratch1 = r1.getRegister();
                    masm.ldar(srcSize, rscratch1, asRegister(address));
                    masm.fmov(destSize, asRegister(result), rscratch1);
                }
            }
        }

        public AArch64Kind getKind() {
            return kind;
        }
    }

    public static class StoreOp extends MemOp {
        public static final LIRInstructionClass<StoreOp> TYPE = LIRInstructionClass.create(StoreOp.class);
        @Use protected AllocatableValue input;

        public StoreOp(AArch64Kind kind, AArch64AddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.input = input;
        }

        @Override
        protected void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            emitStore(crb, masm, kind, addressValue.toAddress(), input);
        }
    }

    public static final class StoreConstantOp extends MemOp {
        public static final LIRInstructionClass<StoreConstantOp> TYPE = LIRInstructionClass.create(StoreConstantOp.class);

        protected final JavaConstant input;

        public StoreConstantOp(AArch64Kind kind, AArch64AddressValue address, JavaConstant input, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.input = input;
            if (!input.isDefaultForKind()) {
                throw GraalError.shouldNotReachHere("Can only store null constants to memory");
            }
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            emitStore(crb, masm, kind, addressValue.toAddress(), zr.asValue(LIRKind.combine(addressValue)));
        }
    }

    public static class VolatileStoreOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VolatileStoreOp> TYPE = LIRInstructionClass.create(VolatileStoreOp.class);
        protected final AArch64Kind kind;
        @State protected LIRFrameState state;
        @Use protected AllocatableValue input;
        @Use protected AllocatableValue address;

        public VolatileStoreOp(AArch64Kind kind, AllocatableValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE);
            this.kind = kind;
            this.address = address;
            this.input = input;
            this.state = state;
            if (state != null) {
                throw GraalError.shouldNotReachHere("Can't handle implicit null check");
            }
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int destSize = kind.getSizeInBytes() * Byte.SIZE;
            if (kind.isInteger()) {
                masm.stlr(destSize, asRegister(input), asRegister(address));
            } else {
                try (ScratchRegister r1 = masm.getScratchRegister()) {
                    Register rscratch1 = r1.getRegister();
                    masm.fmov(destSize, rscratch1, asRegister(input));
                    masm.stlr(destSize, rscratch1, asRegister(address));
                }
            }
        }

        public AArch64Kind getKind() {
            return kind;
        }
    }

    public static final class NullCheckOp extends AArch64LIRInstruction implements NullCheck {
        public static final LIRInstructionClass<NullCheckOp> TYPE = LIRInstructionClass.create(NullCheckOp.class);

        @Use(COMPOSITE) protected AArch64AddressValue address;
        @State protected LIRFrameState state;

        public NullCheckOp(AArch64AddressValue address, LIRFrameState state) {
            super(TYPE);
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int prePosition = masm.position();
            masm.ldr(64, zr, address.toAddress());
            int implicitExceptionPosition = prePosition;
            // Adjust implicit exception position if this ldr has been merged to ldp.
            if (prePosition == masm.position() && masm.isImmLoadStoreMerged()) {
                implicitExceptionPosition = prePosition - 4;
                if (crb.isImplicitExceptionExist(implicitExceptionPosition)) {
                    return;
                }
            }
            crb.recordImplicitException(implicitExceptionPosition, state);
        }

        @Override
        public Value getCheckedValue() {
            return address.base;
        }

        @Override
        public LIRFrameState getState() {
            return state;
        }
    }

    private static void emitStore(@SuppressWarnings("unused") CompilationResultBuilder crb, AArch64MacroAssembler masm, AArch64Kind kind, AArch64Address dst, Value src) {
        int destSize = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            masm.str(destSize, asRegister(src), dst);
        } else {
            masm.fstr(destSize, asRegister(src), dst);
        }
    }

    public static void move(CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(crb, masm, result, asAllocatableValue(input));
            } else if (isStackSlot(result)) {
                reg2stack(crb, masm, result, asAllocatableValue(input));
            } else {
                throw GraalError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                stack2reg(crb, masm, result, asAllocatableValue(input));
            } else if (isStackSlot(result)) {
                emitStackMove(crb, masm, result, input);
            } else {
                throw GraalError.shouldNotReachHere();
            }
        } else if (isJavaConstant(input)) {
            if (isRegister(result)) {
                const2reg(crb, masm, result, asJavaConstant(input));
            } else {
                throw GraalError.shouldNotReachHere();
            }
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    private static void emitStackMove(CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, Value input) {
        try (ScratchRegister r1 = masm.getScratchRegister()) {
            try (ScratchRegister r2 = masm.getScratchRegister()) {
                Register rscratch1 = r1.getRegister();
                Register rscratch2 = r2.getRegister();
                // use the slot kind to define the operand size
                PlatformKind kind = input.getPlatformKind();
                final int size = kind.getSizeInBytes() * Byte.SIZE;

                // Always perform stack -> stack copies through integer registers
                crb.blockComment("[stack -> stack copy]");
                AArch64Address src = loadStackSlotAddress(crb, masm, asStackSlot(input), rscratch2);
                masm.ldr(size, rscratch1, src);
                AArch64Address dst = loadStackSlotAddress(crb, masm, asStackSlot(result), rscratch2);
                masm.str(size, rscratch1, dst);
            }
        }
    }

    private static void reg2reg(@SuppressWarnings("unused") CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, AllocatableValue input) {
        Register dst = asRegister(result);
        Register src = asRegister(input);
        if (src.equals(dst)) {
            return;
        }
        AArch64Kind kind = (AArch64Kind) input.getPlatformKind();
        int size = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            masm.mov(size, dst, src);
        } else {
            masm.fmov(size, dst, src);
        }
    }

    static void reg2stack(CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, AllocatableValue input) {
        AArch64Address dest;
        try (ScratchRegister scratch = masm.getScratchRegister()) {
            dest = loadStackSlotAddress(crb, masm, asStackSlot(result), scratch.getRegister());
        }
        Register src = asRegister(input);
        // use the slot kind to define the operand size
        AArch64Kind kind = (AArch64Kind) result.getPlatformKind();
        final int size = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            masm.str(size, src, dest);
        } else {
            masm.fstr(size, src, dest);
        }
    }

    static void stack2reg(CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, AllocatableValue input) {
        AArch64Kind kind = (AArch64Kind) input.getPlatformKind();
        // use the slot kind to define the operand size
        final int size = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            AArch64Address src = loadStackSlotAddress(crb, masm, asStackSlot(input), result);
            masm.ldr(size, asRegister(result), src);
        } else {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                AllocatableValue scratchRegisterValue = sc.getRegister().asValue(LIRKind.combine(input));
                AArch64Address src = loadStackSlotAddress(crb, masm, asStackSlot(input), scratchRegisterValue);
                masm.fldr(size, asRegister(result), src);
            }
        }
    }

    private static void const2reg(CompilationResultBuilder crb, AArch64MacroAssembler masm, Value result, JavaConstant input) {
        Register dst = asRegister(result);
        switch (input.getJavaKind().getStackKind()) {
            case Int:
                final int value = input.asInt();
                int maskedValue;
                switch (input.getJavaKind()) {
                    case Boolean:
                    case Byte:
                        maskedValue = value & 0xFF;
                        break;
                    case Char:
                    case Short:
                        maskedValue = value & 0xFFFF;
                        break;
                    case Int:
                        maskedValue = value;
                        break;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
                masm.mov(dst, maskedValue);
                break;
            case Long:
                masm.mov(dst, input.asLong());
                break;
            case Float:
                if (AArch64MacroAssembler.isFloatImmediate(input.asFloat())) {
                    masm.fmov(32, dst, input.asFloat());
                } else if (crb.compilationResult.isImmutablePIC()) {
                    try (ScratchRegister scr = masm.getScratchRegister()) {
                        Register scratch = scr.getRegister();
                        masm.mov(scratch, Float.floatToRawIntBits(input.asFloat()));
                        masm.fmov(32, dst, scratch);
                    }
                } else {
                    try (ScratchRegister scr = masm.getScratchRegister()) {
                        Register scratch = scr.getRegister();
                        crb.asFloatConstRef(input);
                        masm.addressOf(scratch);
                        masm.fldr(32, dst, AArch64Address.createBaseRegisterOnlyAddress(scratch));
                    }
                }
                break;
            case Double:
                if (AArch64MacroAssembler.isDoubleImmediate(input.asDouble())) {
                    masm.fmov(64, dst, input.asDouble());
                } else if (crb.compilationResult.isImmutablePIC()) {
                    try (ScratchRegister scr = masm.getScratchRegister()) {
                        Register scratch = scr.getRegister();
                        masm.mov(scratch, Double.doubleToRawLongBits(input.asDouble()));
                        masm.fmov(64, dst, scratch);
                    }
                } else {
                    try (ScratchRegister scr = masm.getScratchRegister()) {
                        Register scratch = scr.getRegister();
                        crb.asDoubleConstRef(input);
                        masm.addressOf(scratch);
                        masm.fldr(64, dst, AArch64Address.createBaseRegisterOnlyAddress(scratch));
                    }
                }
                break;
            case Object:
                if (input.isNull()) {
                    if (crb.mustReplaceWithUncompressedNullRegister(input)) {
                        masm.mov(64, dst, crb.uncompressedNullRegister);
                    } else {
                        masm.mov(dst, 0);
                    }
                } else if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(input);
                    masm.mov(dst, 0xDEADDEADDEADDEADL, true);
                } else {
                    crb.recordDataReferenceInCode(input, 8);
                    AArch64Address address = AArch64Address.createScaledImmediateAddress(dst, 0x0);
                    masm.adrpLdr(64, dst, address);
                }
                break;
            default:
                throw GraalError.shouldNotReachHere("kind=" + input.getJavaKind().getStackKind());
        }
    }

    private static void const2stack(CompilationResultBuilder crb, AArch64MacroAssembler masm, Value result, JavaConstant constant) {
        try (ScratchRegister addrReg = masm.getScratchRegister()) {
            StackSlot slot = (StackSlot) result;
            AArch64Address resultAddress = loadStackSlotAddress(crb, masm, slot, addrReg.getRegister());
            if (constant.isNull() && !crb.mustReplaceWithUncompressedNullRegister(constant)) {
                emitStore(crb, masm, (AArch64Kind) result.getPlatformKind(), resultAddress, zr.asValue(LIRKind.combine(result)));
            } else {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Value scratchRegisterValue = sc.getRegister().asValue(LIRKind.combine(result));
                    const2reg(crb, masm, scratchRegisterValue, constant);
                    emitStore(crb, masm, (AArch64Kind) result.getPlatformKind(), resultAddress, scratchRegisterValue);
                }
            }
        }
    }

    /**
     * Returns AArch64Address of given StackSlot. We cannot use CompilationResultBuilder.asAddress
     * since this calls AArch64MacroAssembler.makeAddress with displacements that may be larger than
     * 9-bit signed, which cannot be handled by that method.
     *
     * Instead we create an address ourselves. We use scaled unsigned addressing since we know the
     * transfersize, which gives us a 15-bit address range (for longs/doubles) respectively a 14-bit
     * range (for everything else).
     *
     * @param scratch Scratch register that can be used to load address. If Value.ILLEGAL this
     *            instruction fails if we try to access a StackSlot that is too large to be loaded
     *            directly.
     * @return AArch64Address of given StackSlot. Uses scratch register if necessary to do so.
     */
    private static AArch64Address loadStackSlotAddress(CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot slot, AllocatableValue scratch) {
        Register scratchReg = Value.ILLEGAL.equals(scratch) ? zr : asRegister(scratch);
        return loadStackSlotAddress(crb, masm, slot, scratchReg);
    }

    private static AArch64Address loadStackSlotAddress(CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot slot, Register scratchReg) {
        int displacement = crb.frameMap.offsetForStackSlot(slot);
        int transferSize = slot.getPlatformKind().getSizeInBytes();
        return masm.makeAddress(sp, displacement, scratchReg, transferSize, /* allowOverwrite */false);
    }

    public abstract static class PointerCompressionOp extends AArch64LIRInstruction {

        @Def({REG, HINT}) private AllocatableValue result;
        @Use({REG, CONST}) private Value input;
        @Alive({REG, ILLEGAL, UNINITIALIZED}) private AllocatableValue baseRegister;

        protected final CompressEncoding encoding;
        protected final boolean nonNull;
        protected final LIRKindTool lirKindTool;

        protected PointerCompressionOp(LIRInstructionClass<? extends PointerCompressionOp> type, AllocatableValue result, Value input,
                        AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {

            super(type);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
            this.lirKindTool = lirKindTool;
        }

        public static boolean hasBase(OptionValues options, CompressEncoding encoding) {
            return GeneratePIC.getValue(options) || encoding.hasBase();
        }

        public final Value getInput() {
            return input;
        }

        public final AllocatableValue getResult() {
            return result;
        }

        protected final Register getResultRegister() {
            return asRegister(result);
        }

        protected final Register getBaseRegister(CompilationResultBuilder crb) {
            return hasBase(crb.getOptions(), encoding) ? asRegister(baseRegister) : Register.None;
        }

        protected final int getShift() {
            return encoding.getShift();
        }

        protected final void move(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Move.move(crb, masm, result, input);
        }
    }

    public static class CompressPointerOp extends PointerCompressionOp {
        public static final LIRInstructionClass<CompressPointerOp> TYPE = LIRInstructionClass.create(CompressPointerOp.class);

        public CompressPointerOp(AllocatableValue result, Value input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            this(TYPE, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        private CompressPointerOp(LIRInstructionClass<? extends PointerCompressionOp> type, AllocatableValue result, Value input,
                        AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {

            super(type, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register resultRegister = getResultRegister();
            Register ptr = asRegister(getInput());
            Register base = getBaseRegister(crb);
            // result = (ptr - base) >> shift
            if (!encoding.hasBase()) {
                if (encoding.hasShift()) {
                    masm.lshr(64, resultRegister, ptr, encoding.getShift());
                } else {
                    masm.movx(resultRegister, ptr);
                }
            } else if (nonNull) {
                masm.sub(64, resultRegister, ptr, base);
                if (encoding.hasShift()) {
                    masm.lshr(64, resultRegister, resultRegister, encoding.getShift());
                }
            } else {
                // if ptr is null it still has to be null after compression
                masm.cmp(64, ptr, 0);
                masm.cmov(64, resultRegister, ptr, base, AArch64Assembler.ConditionFlag.NE);
                masm.sub(64, resultRegister, resultRegister, base);
                if (encoding.hasShift()) {
                    masm.lshr(64, resultRegister, resultRegister, encoding.getShift());
                }
            }
        }
    }

    public static class UncompressPointerOp extends PointerCompressionOp {
        public static final LIRInstructionClass<UncompressPointerOp> TYPE = LIRInstructionClass.create(UncompressPointerOp.class);

        public UncompressPointerOp(AllocatableValue result, Value input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            this(TYPE, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        private UncompressPointerOp(LIRInstructionClass<? extends PointerCompressionOp> type, AllocatableValue result, Value input,
                        AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            super(type, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register inputRegister = asRegister(getInput());
            Register resultRegister = getResultRegister();
            Register base = encoding.hasBase() ? getBaseRegister(crb) : null;

            // result = base + (ptr << shift)
            if (nonNull || base == null) {
                masm.add(64, resultRegister, base == null ? zr : base, inputRegister, AArch64Assembler.ShiftType.LSL, encoding.getShift());
            } else {
                // if ptr is null it has to be null after decompression
                Label done = new Label();
                if (!resultRegister.equals(inputRegister)) {
                    masm.mov(32, resultRegister, inputRegister);
                }
                masm.cbz(32, resultRegister, done);
                masm.add(64, resultRegister, base, resultRegister, AArch64Assembler.ShiftType.LSL, encoding.getShift());
                masm.bind(done);
            }
        }
    }

    private abstract static class ZeroNullConversionOp extends AArch64LIRInstruction {
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        protected ZeroNullConversionOp(LIRInstructionClass<? extends ZeroNullConversionOp> type, AllocatableValue result, AllocatableValue input) {
            super(type);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register nullRegister = crb.uncompressedNullRegister;
            if (!nullRegister.equals(Register.None)) {
                emitConversion(asRegister(result), asRegister(input), nullRegister, masm);
            }
        }

        protected abstract void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AArch64MacroAssembler masm);
    }

    public static class ConvertNullToZeroOp extends ZeroNullConversionOp {
        public static final LIRInstructionClass<ConvertNullToZeroOp> TYPE = LIRInstructionClass.create(ConvertNullToZeroOp.class);

        public ConvertNullToZeroOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE, result, input);
        }

        @Override
        protected final void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AArch64MacroAssembler masm) {
            if (inputRegister.equals(resultRegister)) {
                masm.subs(64, inputRegister, inputRegister, nullRegister);
                Label done = new Label();
                masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, done);
                masm.add(64, inputRegister, inputRegister, nullRegister);
                masm.bind(done);
            } else {
                masm.subs(64, resultRegister, resultRegister, resultRegister);
                masm.cmp(64, inputRegister, nullRegister);
                Label done = new Label();
                masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, done);
                masm.movx(resultRegister, inputRegister);
                masm.bind(done);
            }
        }
    }

    public static class ConvertZeroToNullOp extends ZeroNullConversionOp {
        public static final LIRInstructionClass<ConvertZeroToNullOp> TYPE = LIRInstructionClass.create(ConvertZeroToNullOp.class);

        public ConvertZeroToNullOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE, result, input);
        }

        @Override
        protected final void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AArch64MacroAssembler masm) {
            if (!inputRegister.equals(resultRegister)) {
                masm.movx(resultRegister, inputRegister);
            }
            Label done = new Label();
            masm.ands(64, zr, inputRegister, inputRegister);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, done);
            masm.movx(resultRegister, nullRegister);
            masm.bind(done);
        }
    }

}
