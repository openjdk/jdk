/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;

import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCDelayedControlTransfer;
import org.graalvm.compiler.lir.sparc.SPARCLIRInstruction;
import org.graalvm.compiler.lir.sparc.SPARCMove;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

@Opcode("CRUNTIME_CALL_EPILOGUE")
final class SPARCHotSpotCRuntimeCallEpilogueOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotCRuntimeCallEpilogueOp> TYPE = LIRInstructionClass.create(SPARCHotSpotCRuntimeCallEpilogueOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(11);

    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    private final int threadJavaFrameAnchorFlagsOffset;
    private final Register thread;
    @Use({REG, STACK}) protected Value threadTemp;

    SPARCHotSpotCRuntimeCallEpilogueOp(int threadLastJavaSpOffset, int threadLastJavaPcOffset, int threadJavaFrameAnchorFlagsOffset, Register thread, Value threadTemp) {
        super(TYPE, SIZE);
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.threadJavaFrameAnchorFlagsOffset = threadJavaFrameAnchorFlagsOffset;
        this.thread = thread;
        this.threadTemp = threadTemp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {

        // Restore the thread register when coming back from the runtime.
        SPARCMove.move(crb, masm, thread.asValue(LIRKind.value(XWORD)), threadTemp, SPARCDelayedControlTransfer.DUMMY);

        // Reset last Java frame, last Java PC and flags.
        masm.stx(g0, new SPARCAddress(thread, threadLastJavaSpOffset));
        masm.stx(g0, new SPARCAddress(thread, threadLastJavaPcOffset));
        masm.stw(g0, new SPARCAddress(thread, threadJavaFrameAnchorFlagsOffset));
    }
}
