/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.sparc.SPARC.STACK_BIAS;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARC.i0;
import static jdk.vm.ci.sparc.SPARC.i1;
import static jdk.vm.ci.sparc.SPARC.i2;
import static jdk.vm.ci.sparc.SPARC.i3;
import static jdk.vm.ci.sparc.SPARC.i4;
import static jdk.vm.ci.sparc.SPARC.l7;
import static jdk.vm.ci.sparc.SPARC.o0;
import static jdk.vm.ci.sparc.SPARC.o1;
import static jdk.vm.ci.sparc.SPARC.o2;
import static jdk.vm.ci.sparc.SPARC.o3;
import static jdk.vm.ci.sparc.SPARC.o4;
import static jdk.vm.ci.sparc.SPARC.o5;
import static jdk.vm.ci.sparc.SPARC.o7;
import static jdk.vm.ci.sparc.SPARC.sp;

import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCLIRInstruction;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Emits code that enters a stack frame which is tailored to call the C++ method
 * {@link HotSpotBackend#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@Opcode("ENTER_UNPACK_FRAMES_STACK_FRAME")
final class SPARCHotSpotEnterUnpackFramesStackFrameOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotEnterUnpackFramesStackFrameOp> TYPE = LIRInstructionClass.create(SPARCHotSpotEnterUnpackFramesStackFrameOp.class);

    private final Register thread;
    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    @Alive(REG) AllocatableValue framePc;
    @Alive(REG) AllocatableValue senderSp;
    @Temp(REG) AllocatableValue scratch;
    @Temp(REG) AllocatableValue callerReturnPc;

    SPARCHotSpotEnterUnpackFramesStackFrameOp(Register thread, int threadLastJavaSpOffset, int threadLastJavaPcOffset, AllocatableValue framePc, AllocatableValue senderSp, AllocatableValue scratch,
                    PlatformKind wordKind) {
        super(TYPE);
        this.thread = thread;
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.scratch = scratch;
        callerReturnPc = o7.asValue(LIRKind.value(wordKind));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        final int totalFrameSize = crb.frameMap.totalFrameSize();
        Register framePcRegister = asRegister(framePc);
        Register senderSpRegister = asRegister(senderSp);
        Register scratchRegister = asRegister(scratch);

        // Save final sender SP to O5_savedSP.
        masm.mov(senderSpRegister, o5);

        // Load final frame PC.
        masm.mov(framePcRegister, asRegister(callerReturnPc));

        // Allocate a full sized frame.
        masm.save(sp, -totalFrameSize, sp);

        masm.mov(i0, o0);
        masm.mov(i1, o1);
        masm.mov(i2, o2);
        masm.mov(i3, o3);
        masm.mov(i4, o4);

        // Set up last Java values.
        masm.add(sp, STACK_BIAS, scratchRegister);
        masm.stx(scratchRegister, new SPARCAddress(thread, threadLastJavaSpOffset));

        // Clear last Java PC.
        masm.stx(g0, new SPARCAddress(thread, threadLastJavaPcOffset));

        /*
         * Safe thread register manually since we are not using LEAF_SP for {@link
         * DeoptimizationStub#UNPACK_FRAMES}.
         */
        masm.mov(thread, l7);
    }

    @Override
    public boolean leavesRegisterWindow() {
        return true;
    }
}
