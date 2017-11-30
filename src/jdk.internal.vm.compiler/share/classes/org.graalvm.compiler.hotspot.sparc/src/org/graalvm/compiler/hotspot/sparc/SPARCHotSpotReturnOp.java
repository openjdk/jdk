/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCControlFlow.ReturnOp;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Returns from a function.
 */
@Opcode("RETURN")
final class SPARCHotSpotReturnOp extends SPARCHotSpotEpilogueOp {
    public static final LIRInstructionClass<SPARCHotSpotReturnOp> TYPE = LIRInstructionClass.create(SPARCHotSpotReturnOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(2);

    @Use({REG, ILLEGAL}) protected Value value;
    @Use({REG, ILLEGAL}) protected Value safepointPollAddress;
    private final boolean isStub;
    private final GraalHotSpotVMConfig config;
    private final Register thread;

    SPARCHotSpotReturnOp(Value value, boolean isStub, GraalHotSpotVMConfig config, Register thread, Value safepointPoll) {
        super(TYPE, SIZE);
        this.value = value;
        this.isStub = isStub;
        this.config = config;
        this.thread = thread;
        this.safepointPollAddress = safepointPoll;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        if (!isStub) {
            // Every non-stub compile method must have a poll before the return.
            SPARCHotSpotSafepointOp.emitCode(crb, masm, config, true, null, thread, safepointPollAddress);
        }
        ReturnOp.emitCodeHelper(crb, masm);
    }
}
