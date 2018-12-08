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

import static jdk.vm.ci.sparc.SPARC.sp;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.ENABLE_STACK_RESERVED_ZONE;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.THROW_DELAYED_STACKOVERFLOW_ERROR;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCCall;
import org.graalvm.compiler.lir.sparc.SPARCControlFlow.ReturnOp;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
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
    private final boolean requiresReservedStackAccessCheck;
    private final boolean isStub;
    private final GraalHotSpotVMConfig config;
    private final Register thread;

    SPARCHotSpotReturnOp(Value value, boolean isStub, GraalHotSpotVMConfig config, Register thread, Value safepointPoll, boolean requiresReservedStackAccessCheck) {
        super(TYPE, SIZE);
        this.value = value;
        this.isStub = isStub;
        this.config = config;
        this.thread = thread;
        this.safepointPollAddress = safepointPoll;
        this.requiresReservedStackAccessCheck = requiresReservedStackAccessCheck;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        if (!isStub) {
            if (requiresReservedStackAccessCheck) {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    HotSpotForeignCallsProvider foreignCalls = (HotSpotForeignCallsProvider) crb.foreignCalls;
                    Label noReserved = new Label();
                    Register scratch = sc.getRegister();
                    masm.ldx(new SPARCAddress(thread, config.javaThreadReservedStackActivationOffset), scratch);
                    masm.compareBranch(sp, scratch, SPARCAssembler.ConditionFlag.LessUnsigned, SPARCAssembler.CC.Xcc, noReserved, SPARCAssembler.BranchPredict.PREDICT_TAKEN, null);
                    ForeignCallLinkage enableStackReservedZone = foreignCalls.lookupForeignCall(ENABLE_STACK_RESERVED_ZONE);
                    CallingConvention cc = enableStackReservedZone.getOutgoingCallingConvention();
                    assert cc.getArgumentCount() == 1;
                    Register arg0 = ((RegisterValue) cc.getArgument(0)).getRegister();
                    masm.mov(thread, arg0);
                    SPARCCall.directCall(crb, masm, enableStackReservedZone, scratch, null);
                    masm.restoreWindow();
                    SPARCCall.indirectJmp(crb, masm, scratch, foreignCalls.lookupForeignCall(THROW_DELAYED_STACKOVERFLOW_ERROR));
                    masm.bind(noReserved);
                }
            }
            // Every non-stub compile method must have a poll before the return.
            SPARCHotSpotSafepointOp.emitCode(crb, masm, config, true, null, thread, safepointPollAddress);
        }
        ReturnOp.emitCodeHelper(crb, masm);
    }
}
