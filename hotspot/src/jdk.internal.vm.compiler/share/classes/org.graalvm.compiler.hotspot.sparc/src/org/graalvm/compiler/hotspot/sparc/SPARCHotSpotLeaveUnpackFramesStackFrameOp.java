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

import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARC.i0;
import static jdk.vm.ci.sparc.SPARC.l7;

import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCLIRInstruction;
import org.graalvm.compiler.lir.sparc.SPARCSaveRegistersOp;

import jdk.vm.ci.code.Register;

/**
 * Emits code that leaves a stack frame which is tailored to call the C++ method
 * {@link HotSpotBackend#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@Opcode("LEAVE_UNPACK_FRAMES_STACK_FRAME")
final class SPARCHotSpotLeaveUnpackFramesStackFrameOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotLeaveUnpackFramesStackFrameOp> TYPE = LIRInstructionClass.create(SPARCHotSpotLeaveUnpackFramesStackFrameOp.class);

    private final Register thread;
    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    private final int threadJavaFrameAnchorFlagsOffset;

    SPARCHotSpotLeaveUnpackFramesStackFrameOp(Register thread, int threadLastJavaSpOffset, int threadLastJavaPcOffset, int threadJavaFrameAnchorFlagsOffset) {
        super(TYPE);
        this.thread = thread;
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.threadJavaFrameAnchorFlagsOffset = threadJavaFrameAnchorFlagsOffset;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        /*
         * Safe thread register manually since we are not using LEAF_SP for {@link
         * DeoptimizationStub#UNPACK_FRAMES}.
         */
        masm.mov(l7, thread);

        SPARCAddress lastJavaPc = new SPARCAddress(thread, threadLastJavaPcOffset);

        // We borrow the threads lastJavaPC to transfer the value from float to i0
        masm.stdf(SPARCSaveRegistersOp.RETURN_REGISTER_STORAGE, lastJavaPc);
        masm.ldx(lastJavaPc, i0);

        // Clear last Java frame values.
        masm.stx(g0, lastJavaPc);
        masm.stx(g0, new SPARCAddress(thread, threadLastJavaSpOffset));
        masm.stw(g0, new SPARCAddress(thread, threadJavaFrameAnchorFlagsOffset));
    }
}
