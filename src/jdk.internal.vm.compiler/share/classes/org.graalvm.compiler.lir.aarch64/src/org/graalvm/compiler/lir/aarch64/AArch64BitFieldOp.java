/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020, Arm Limited and affiliates. All rights reserved.
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

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

/**
 * Bit field ops for AArch64.
 */
public class AArch64BitFieldOp extends AArch64LIRInstruction {
    public enum BitFieldOpCode {
        SBFX,
        SBFIZ,
        UBFX,
        UBFIZ,
    }

    private static final LIRInstructionClass<AArch64BitFieldOp> TYPE = LIRInstructionClass.create(AArch64BitFieldOp.class);

    @Opcode private final AArch64BitFieldOp.BitFieldOpCode opcode;
    @Def protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue input;
    private final int lsb;
    private final int width;

    public AArch64BitFieldOp(AArch64BitFieldOp.BitFieldOpCode opcode, AllocatableValue result,
                    AllocatableValue input, int lsb, int width) {
        super(TYPE);
        this.opcode = opcode;
        this.result = result;
        this.input = input;
        this.lsb = lsb;
        this.width = width;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register dst = asRegister(result);
        Register src = asRegister(input);
        final int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        switch (opcode) {
            case SBFX:
                masm.sbfm(size, dst, src, lsb, lsb + width - 1);
                break;
            case SBFIZ:
                masm.sbfm(size, dst, src, size - lsb, width - 1);
                break;
            case UBFX:
                masm.ubfm(size, dst, src, lsb, lsb + width - 1);
                break;
            case UBFIZ:
                masm.ubfm(size, dst, src, size - lsb, width - 1);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }
}
