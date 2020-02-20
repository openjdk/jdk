/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.DEOPT_BLOB_UNPACK_WITH_EXCEPTION_IN_TLS;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64Call;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Removes the current frame and tail calls the uncommon trap routine.
 */
@Opcode("DEOPT_WITH_EXCEPTION_IN_CALLER")
final class AMD64HotSpotDeoptimizeWithExceptionCallerOp extends AMD64HotSpotEpilogueBlockEndOp {

    public static final LIRInstructionClass<AMD64HotSpotDeoptimizeWithExceptionCallerOp> TYPE = LIRInstructionClass.create(AMD64HotSpotDeoptimizeWithExceptionCallerOp.class);
    private final GraalHotSpotVMConfig config;
    @Alive(OperandFlag.REG) private Value exception;
    @Temp(OperandFlag.REG) private Value temp;

    protected AMD64HotSpotDeoptimizeWithExceptionCallerOp(GraalHotSpotVMConfig config, Value exception) {
        super(TYPE);
        this.config = config;
        this.exception = exception;
        this.temp = AMD64.rax.asValue();
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register stackPointer = crb.frameMap.getRegisterConfig().getFrameRegister();
        Register exc = asRegister(exception);

        leaveFrameAndRestoreRbp(crb, masm);

        // Save exception oop in TLS
        masm.movq(new AMD64Address(AMD64.r15, config.threadExceptionOopOffset), exc);
        // Get return address and store it into TLS
        Register returnPC = asRegister(temp);
        masm.movq(returnPC, new AMD64Address(stackPointer, 0));
        masm.movq(new AMD64Address(AMD64.r15, config.threadExceptionPcOffset), returnPC);

        // Remove return address.
        masm.addq(stackPointer, crb.target.arch.getReturnAddressSize());
        AMD64Call.directJmp(crb, masm, crb.foreignCalls.lookupForeignCall(DEOPT_BLOB_UNPACK_WITH_EXCEPTION_IN_TLS), null);
    }
}
