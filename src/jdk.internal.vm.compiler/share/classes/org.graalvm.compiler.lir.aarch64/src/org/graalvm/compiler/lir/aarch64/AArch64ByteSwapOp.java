/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;

@Opcode("BSWAP")
public final class AArch64ByteSwapOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ByteSwapOp> TYPE = LIRInstructionClass.create(AArch64ByteSwapOp.class);

    @Def({OperandFlag.REG, OperandFlag.HINT}) protected Value result;
    @Use protected Value input;

    public AArch64ByteSwapOp(Value result, Value input) {
        super(TYPE);
        this.result = result;
        this.input = input;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        switch ((AArch64Kind) input.getPlatformKind()) {
            case DWORD:
                masm.rev(32, ValueUtil.asRegister(result), ValueUtil.asRegister(input));
                break;
            case QWORD:
                masm.rev(64, ValueUtil.asRegister(result), ValueUtil.asRegister(input));
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }
}
