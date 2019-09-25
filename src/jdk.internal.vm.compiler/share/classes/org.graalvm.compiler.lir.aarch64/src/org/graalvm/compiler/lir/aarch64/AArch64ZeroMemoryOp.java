/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited and affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Zero a chunk of memory on AArch64.
 */
@Opcode("ZERO_MEMORY")
public final class AArch64ZeroMemoryOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ZeroMemoryOp> TYPE = LIRInstructionClass.create(AArch64ZeroMemoryOp.class);

    @Use({REG}) protected AllocatableValue addressValue;
    @Use({REG}) protected AllocatableValue lengthValue;

    private final boolean useDcZva;
    private final int zvaLength;

    /**
     * Constructor of AArch64ZeroMemoryOp.
     *
     * @param address allocatable 8-byte aligned base address of the memory chunk.
     * @param length allocatable length of the memory chunk, the value must be multiple of 8.
     * @param useDcZva is DC ZVA instruction is able to use.
     * @param zvaLength the ZVA length info of current AArch64 CPU, negative value indicates length
     *            is unknown at compile time.
     */
    public AArch64ZeroMemoryOp(AllocatableValue address, AllocatableValue length, boolean useDcZva, int zvaLength) {
        super(TYPE);
        this.addressValue = address;
        this.lengthValue = length;
        this.useDcZva = useDcZva;
        this.zvaLength = zvaLength;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register base = asRegister(addressValue);
        Register size = asRegister(lengthValue);
        if (useDcZva && zvaLength > 0) {
            // From ARMv8-A architecture reference manual D12.2.35 Data Cache Zero ID register:
            // A valid ZVA length should be a power-of-2 value in [4, 2048]
            assert (CodeUtil.isPowerOf2(zvaLength) && 4 <= zvaLength && zvaLength <= 2048);
            emitZeroMemoryWithDc(masm, base, size, zvaLength);
        } else {
            // Use store pair instructions (STP) to zero memory as a fallback.
            emitZeroMemoryWithStp(masm, base, size);
        }
    }

    /**
     * Zero a chunk of memory with DC ZVA instructions.
     *
     * @param masm the AArch64 macro assembler.
     * @param base base an 8-byte aligned address of the memory chunk to be zeroed.
     * @param size size of the memory chunk to be zeroed, in bytes, must be multiple of 8.
     * @param zvaLength the ZVA length info of current AArch64 CPU.
     */
    private static void emitZeroMemoryWithDc(AArch64MacroAssembler masm, Register base, Register size, int zvaLength) {
        Label preLoop = new Label();
        Label zvaLoop = new Label();
        Label postLoop = new Label();
        Label tail = new Label();
        Label done = new Label();

        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();

            // Count number of bytes to be pre-zeroed to align base address with ZVA length.
            masm.neg(64, rscratch1, base);
            masm.and(64, rscratch1, rscratch1, zvaLength - 1);

            // Is size less than number of bytes to be pre-zeroed? Jump to POST_LOOP if so.
            masm.cmp(64, size, rscratch1);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.LE, postLoop);
            masm.sub(64, size, size, rscratch1);

            // Pre-ZVA loop.
            masm.bind(preLoop);
            masm.subs(64, rscratch1, rscratch1, 8);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.LT, zvaLoop);
            masm.str(64, zr, AArch64Address.createPostIndexedImmediateAddress(base, 8));
            masm.jmp(preLoop);

            // ZVA loop.
            masm.bind(zvaLoop);
            masm.subs(64, size, size, zvaLength);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.LT, tail);
            masm.dc(AArch64Assembler.DataCacheOperationType.ZVA, base);
            masm.add(64, base, base, zvaLength);
            masm.jmp(zvaLoop);

            // Handle bytes after ZVA loop.
            masm.bind(tail);
            masm.add(64, size, size, zvaLength);

            // Post-ZVA loop.
            masm.bind(postLoop);
            masm.subs(64, size, size, 8);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.LT, done);
            masm.str(64, zr, AArch64Address.createPostIndexedImmediateAddress(base, 8));
            masm.jmp(postLoop);

            // Done.
            masm.bind(done);
        }
    }

    /**
     * Zero a chunk of memory with STP instructions.
     *
     * @param masm the AArch64 macro assembler.
     * @param base base an 8-byte aligned address of the memory chunk to be zeroed.
     * @param size size of the memory chunk to be zeroed, in bytes, must be multiple of 8.
     */
    private static void emitZeroMemoryWithStp(AArch64MacroAssembler masm, Register base, Register size) {
        Label loop = new Label();
        Label tail = new Label();
        Label done = new Label();

        // Jump to DONE if size is zero.
        masm.cbz(64, size, done);

        // Is base address already 16-byte aligned? Jump to LDP loop if so.
        masm.tbz(base, 3, loop);
        masm.sub(64, size, size, 8);
        masm.str(64, zr, AArch64Address.createPostIndexedImmediateAddress(base, 8));

        // The STP loop that zeros 16 bytes in each iteration.
        masm.bind(loop);
        masm.subs(64, size, size, 16);
        masm.branchConditionally(AArch64Assembler.ConditionFlag.LT, tail);
        masm.stp(64, zr, zr, AArch64Address.createPostIndexedImmediateAddress(base, 2));
        masm.jmp(loop);

        // We may need to zero the tail 8 bytes of the memory chunk.
        masm.bind(tail);
        masm.adds(64, size, size, 16);
        masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, done);
        masm.str(64, zr, AArch64Address.createPostIndexedImmediateAddress(base, 8));

        // Done.
        masm.bind(done);
    }
}
