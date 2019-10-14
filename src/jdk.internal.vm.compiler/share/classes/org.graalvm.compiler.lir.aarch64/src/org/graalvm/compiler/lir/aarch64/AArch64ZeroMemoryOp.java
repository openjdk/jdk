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
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Zero a chunk of memory on AArch64.
 */
@Opcode("ZERO_MEMORY")
public final class AArch64ZeroMemoryOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ZeroMemoryOp> TYPE = LIRInstructionClass.create(AArch64ZeroMemoryOp.class);

    @Use({REG}) protected Value addressValue;
    @Use({REG}) protected Value lengthValue;

    @Temp({REG}) protected Value addressValueTemp;
    @Temp({REG}) protected Value lengthValueTemp;

    private final boolean isAligned;
    private final boolean useDcZva;
    private final int zvaLength;

    /**
     * Constructor of AArch64ZeroMemoryOp.
     *
     * @param address starting address of the memory chunk to be zeroed.
     * @param length size of the memory chunk to be zeroed, in bytes.
     * @param isAligned whether both address and size are aligned to 8 bytes.
     * @param useDcZva is DC ZVA instruction is able to use.
     * @param zvaLength the ZVA length info of current AArch64 CPU, negative value indicates length
     *            is unknown at compile time.
     */
    public AArch64ZeroMemoryOp(Value address, Value length, boolean isAligned, boolean useDcZva, int zvaLength) {
        super(TYPE);
        this.addressValue = address;
        this.lengthValue = length;
        this.addressValueTemp = address;
        this.lengthValueTemp = length;
        this.useDcZva = useDcZva;
        this.zvaLength = zvaLength;
        this.isAligned = isAligned;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register base = asRegister(addressValue);
        Register size = asRegister(lengthValue);

        try (AArch64MacroAssembler.ScratchRegister scratchRegister = masm.getScratchRegister()) {
            Register alignmentBits = scratchRegister.getRegister();

            Label tail = new Label();
            Label done = new Label();

            // Jump to DONE if size is zero.
            masm.cbz(64, size, done);

            if (!isAligned) {
                Label baseAlignedTo2Bytes = new Label();
                Label baseAlignedTo4Bytes = new Label();
                Label baseAlignedTo8Bytes = new Label();

                // Jump to per-byte zeroing loop if the zeroing size is less than 8
                masm.cmp(64, size, 8);
                masm.branchConditionally(ConditionFlag.LT, tail);

                // Make base 8-byte aligned
                masm.neg(64, alignmentBits, base);
                masm.and(64, alignmentBits, alignmentBits, 7);

                masm.tbz(alignmentBits, 0, baseAlignedTo2Bytes);
                masm.sub(64, size, size, 1);
                masm.str(8, zr, AArch64Address.createPostIndexedImmediateAddress(base, 1));
                masm.bind(baseAlignedTo2Bytes);

                masm.tbz(alignmentBits, 1, baseAlignedTo4Bytes);
                masm.sub(64, size, size, 2);
                masm.str(16, zr, AArch64Address.createPostIndexedImmediateAddress(base, 2));
                masm.bind(baseAlignedTo4Bytes);

                masm.tbz(alignmentBits, 2, baseAlignedTo8Bytes);
                masm.sub(64, size, size, 4);
                masm.str(32, zr, AArch64Address.createPostIndexedImmediateAddress(base, 4));
                masm.bind(baseAlignedTo8Bytes);
                // At this point base is 8-byte aligned.
            }

            if (useDcZva && zvaLength > 0) {
                // From ARMv8-A architecture reference manual D12.2.35 Data Cache Zero ID register:
                // A valid ZVA length should be a power-of-2 value in [4, 2048]
                assert (CodeUtil.isPowerOf2(zvaLength) && 4 <= zvaLength && zvaLength <= 2048);

                Label preCheck = new Label();
                Label preLoop = new Label();
                Label mainCheck = new Label();
                Label mainLoop = new Label();
                Label postCheck = new Label();
                Label postLoop = new Label();

                masm.neg(64, alignmentBits, base);
                masm.and(64, alignmentBits, alignmentBits, zvaLength - 1);

                // Is size less than number of bytes to be pre-zeroed? Jump to post check if so.
                masm.cmp(64, size, alignmentBits);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.LE, postCheck);
                masm.sub(64, size, size, alignmentBits);

                // Pre loop: align base according to the supported bulk zeroing stride.
                masm.jmp(preCheck);

                masm.align(crb.target.wordSize * 2);
                masm.bind(preLoop);
                masm.str(64, zr, AArch64Address.createPostIndexedImmediateAddress(base, 8));
                masm.bind(preCheck);
                masm.subs(64, alignmentBits, alignmentBits, 8);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.GE, preLoop);

                // Main loop: bulk zeroing
                masm.jmp(mainCheck);

                masm.align(crb.target.wordSize * 2);
                masm.bind(mainLoop);
                masm.dc(AArch64Assembler.DataCacheOperationType.ZVA, base);
                masm.add(64, base, base, zvaLength);
                masm.bind(mainCheck);
                masm.subs(64, size, size, zvaLength);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.GE, mainLoop);

                masm.add(64, size, size, zvaLength);

                // Post loop: handle bytes after the main loop
                masm.jmp(postCheck);

                masm.align(crb.target.wordSize * 2);
                masm.bind(postLoop);
                masm.str(64, zr, AArch64Address.createPostIndexedImmediateAddress(base, 8));
                masm.bind(postCheck);
                masm.subs(64, size, size, 8);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.GE, postLoop);

                if (!isAligned) {
                    // Restore size for tail zeroing
                    masm.add(64, size, size, 8);
                }
            } else {
                Label mainCheck = new Label();
                Label mainLoop = new Label();

                if (!isAligned) {
                    // After aligning base, we may have size less than 8. Need to check again.
                    masm.cmp(64, size, 8);
                    masm.branchConditionally(ConditionFlag.LT, tail);
                }

                masm.tbz(base, 3, mainCheck);
                masm.sub(64, size, size, 8);
                masm.str(64, zr, AArch64Address.createPostIndexedImmediateAddress(base, 8));
                masm.jmp(mainCheck);

                // The STP loop that zeros 16 bytes in each iteration.
                masm.align(crb.target.wordSize * 2);
                masm.bind(mainLoop);
                masm.stp(64, zr, zr, AArch64Address.createPostIndexedImmediateAddress(base, 2));
                masm.bind(mainCheck);
                masm.subs(64, size, size, 16);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.GE, mainLoop);

                // We may need to zero the tail 8 bytes of the memory chunk.
                masm.add(64, size, size, 16);
                masm.tbz(size, 3, tail);
                masm.str(64, zr, AArch64Address.createPostIndexedImmediateAddress(base, 8));

                if (!isAligned) {
                    // Adjust size for tail zeroing
                    masm.sub(64, size, size, 8);
                }
            }

            masm.bind(tail);
            if (!isAligned) {
                Label perByteZeroingLoop = new Label();

                masm.cbz(64, size, done);
                // We have to ensure size > 0 when entering the following loop
                masm.align(crb.target.wordSize * 2);
                masm.bind(perByteZeroingLoop);
                masm.str(8, zr, AArch64Address.createPostIndexedImmediateAddress(base, 1));
                masm.subs(64, size, size, 1);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, perByteZeroingLoop);
            }
            masm.bind(done);
        }
    }

}
