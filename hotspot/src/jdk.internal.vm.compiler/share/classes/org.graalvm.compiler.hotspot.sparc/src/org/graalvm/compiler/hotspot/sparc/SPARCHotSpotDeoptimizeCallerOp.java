/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.HotSpotHostBackend.UNCOMMON_TRAP_HANDLER;

import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCCall;

import jdk.vm.ci.code.Register;

/**
 * Removes the current frame and tail calls the uncommon trap routine.
 */
@Opcode("DEOPT_CALLER")
final class SPARCHotSpotDeoptimizeCallerOp extends SPARCHotSpotEpilogueOp {
    public static final LIRInstructionClass<SPARCHotSpotDeoptimizeCallerOp> TYPE = LIRInstructionClass.create(SPARCHotSpotDeoptimizeCallerOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(32);

    protected SPARCHotSpotDeoptimizeCallerOp() {
        super(TYPE, SIZE);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        leaveFrame(crb);

        // SPARCHotSpotBackend backend = (SPARCHotSpotBackend)
        // HotSpotGraalRuntime.runtime().getBackend();
        // final boolean isStub = true;
        // HotSpotFrameContext frameContext = backend.new HotSpotFrameContext(isStub);
        // frameContext.enter(crb);

        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            SPARCCall.indirectJmp(crb, masm, scratch, crb.foreignCalls.lookupForeignCall(UNCOMMON_TRAP_HANDLER));
        }

        // frameContext.leave(crb);
    }
}
