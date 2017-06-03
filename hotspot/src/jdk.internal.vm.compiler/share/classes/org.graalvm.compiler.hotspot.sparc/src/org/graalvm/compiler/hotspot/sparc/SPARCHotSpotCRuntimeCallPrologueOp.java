/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.sparc.SPARC.STACK_BIAS;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;

import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCLIRInstruction;
import org.graalvm.compiler.lir.sparc.SPARCMove;
import org.graalvm.compiler.lir.sparc.SPARCTailDelayedLIRInstruction;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

@Opcode("CRUNTIME_CALL_PROLOGUE")
final class SPARCHotSpotCRuntimeCallPrologueOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotCRuntimeCallPrologueOp> TYPE = LIRInstructionClass.create(SPARCHotSpotCRuntimeCallPrologueOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(10);

    private final int threadLastJavaSpOffset;
    private final Register thread;
    private final Register stackPointer;
    @Def({REG, STACK}) protected Value threadTemp;
    @Temp({REG}) protected AllocatableValue spScratch;

    SPARCHotSpotCRuntimeCallPrologueOp(int threadLastJavaSpOffset, Register thread, Register stackPointer, Value threadTemp, AllocatableValue spScratch) {
        super(TYPE, SIZE);
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.thread = thread;
        this.stackPointer = stackPointer;
        this.threadTemp = threadTemp;
        this.spScratch = spScratch;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // Save last Java frame.
        Register scratchRegister = asRegister(spScratch);
        masm.add(stackPointer, STACK_BIAS, scratchRegister);
        masm.stx(scratchRegister, new SPARCAddress(thread, threadLastJavaSpOffset));

        // Save the thread register when calling out to the runtime.
        SPARCMove.move(crb, masm, threadTemp, thread.asValue(LIRKind.value(XWORD)), getDelayedControlTransfer());
    }
}
