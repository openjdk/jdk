/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.sparc;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCLIRInstruction;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.sparc.SPARCKind;

/**
 * Jumps to the exception handler specified by {@link #address} and leaves the current window. It
 * does not modify the i7 register, as the exception handler stub expects the throwing pc in it.
 * <p>
 * See also:
 * <li>Runtime1::generate_handle_exception c1_Runtime1_sparc.cpp
 * <li>SharedRuntime::generate_deopt_blob at exception_in_tls_offset (sharedRuntime_sparc.cpp)
 */
@Opcode("JUMP_TO_EXCEPTION_HANDLER")
final class SPARCHotSpotJumpToExceptionHandlerOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotJumpToExceptionHandlerOp> TYPE = LIRInstructionClass.create(SPARCHotSpotJumpToExceptionHandlerOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(2);

    @Use(REG) AllocatableValue address;

    SPARCHotSpotJumpToExceptionHandlerOp(AllocatableValue address) {
        super(TYPE, SIZE);
        this.address = address;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Register addrRegister = asRegister(address, SPARCKind.XWORD);
        masm.jmp(addrRegister);
        masm.restoreWindow();
    }
}
