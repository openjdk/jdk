/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Opcode("CRUNTIME_CALL_PROLOGUE")
public class AArch64HotSpotCRuntimeCallPrologueOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotCRuntimeCallPrologueOp> TYPE = LIRInstructionClass.create(AArch64HotSpotCRuntimeCallPrologueOp.class);

    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    private final Register thread;
    @Temp({REG}) protected AllocatableValue scratch;
    private final Label label;

    public AArch64HotSpotCRuntimeCallPrologueOp(int threadLastJavaSpOffset, int threadLastJavaPcOffset, Register thread, AllocatableValue scratch, Label label) {
        super(TYPE);
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.thread = thread;
        this.scratch = scratch;
        this.label = label;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        // Save last Java frame.
        // We cannot save the SP directly so use a temporary register.
        Register scratchRegister = asRegister(scratch);
        masm.movx(scratchRegister, sp);
        masm.str(64, scratchRegister, masm.makeAddress(thread, threadLastJavaSpOffset, 8));

        // Get the current PC. Use a label to patch the return address.
        masm.adr(scratchRegister, label);
        masm.str(64, scratchRegister, masm.makeAddress(thread, threadLastJavaPcOffset, 8));
    }
}
