/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.aarch64;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.fp;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Sets up the arguments for an exception handler in the callers frame, removes the current frame
 * and jumps to the handler.
 */
@Opcode("JUMP_TO_EXCEPTION_HANDLER_IN_CALLER")
public class AArch64HotSpotJumpToExceptionHandlerInCallerOp extends AArch64HotSpotEpilogueOp {

    public static final LIRInstructionClass<AArch64HotSpotJumpToExceptionHandlerInCallerOp> TYPE = LIRInstructionClass.create(AArch64HotSpotJumpToExceptionHandlerInCallerOp.class);

    @Use(REG) private AllocatableValue handlerInCallerPc;
    @Use(REG) private AllocatableValue exception;
    @Use(REG) private AllocatableValue exceptionPc;
    private final Register thread;
    private final int isMethodHandleReturnOffset;

    public AArch64HotSpotJumpToExceptionHandlerInCallerOp(AllocatableValue handlerInCallerPc, AllocatableValue exception, AllocatableValue exceptionPc, int isMethodHandleReturnOffset,
                    Register thread, GraalHotSpotVMConfig config) {
        super(TYPE, config);
        this.handlerInCallerPc = handlerInCallerPc;
        this.exception = exception;
        this.exceptionPc = exceptionPc;
        this.isMethodHandleReturnOffset = isMethodHandleReturnOffset;
        this.thread = thread;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        leaveFrame(crb, masm, /* emitSafepoint */false);

        // Restore sp from fp if the exception PC is a method handle call site.
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            AArch64Address address = masm.makeAddress(thread, isMethodHandleReturnOffset, scratch, 4, /* allowOverwrite */false);
            masm.ldr(32, scratch, address);
            Label noRestore = new Label();
            masm.cbz(32, scratch, noRestore);
            masm.mov(64, sp, fp);
            masm.bind(noRestore);
        }
        masm.jmp(asRegister(handlerInCallerPc));
    }
}
