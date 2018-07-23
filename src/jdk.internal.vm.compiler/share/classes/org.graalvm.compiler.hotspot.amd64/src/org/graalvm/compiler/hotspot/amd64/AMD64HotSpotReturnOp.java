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


package org.graalvm.compiler.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.ENABLE_STACK_RESERVED_ZONE;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.THROW_DELAYED_STACKOVERFLOW_ERROR;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64Call;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool.ZapStackArgumentSpaceBeforeInstruction;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
 * Returns from a function.
 */
@Opcode("RETURN")
final class AMD64HotSpotReturnOp extends AMD64HotSpotEpilogueBlockEndOp implements ZapStackArgumentSpaceBeforeInstruction {

    public static final LIRInstructionClass<AMD64HotSpotReturnOp> TYPE = LIRInstructionClass.create(AMD64HotSpotReturnOp.class);
    @Use({REG, ILLEGAL}) protected Value value;
    private final boolean isStub;
    private final Register thread;
    private final Register scratchForSafepointOnReturn;
    private final GraalHotSpotVMConfig config;
    private final boolean requiresReservedStackAccessCheck;

    AMD64HotSpotReturnOp(Value value, boolean isStub, Register thread, Register scratchForSafepointOnReturn, GraalHotSpotVMConfig config, boolean requiresReservedStackAccessCheck) {
        super(TYPE);
        this.value = value;
        this.isStub = isStub;
        this.thread = thread;
        this.scratchForSafepointOnReturn = scratchForSafepointOnReturn;
        this.config = config;
        this.requiresReservedStackAccessCheck = requiresReservedStackAccessCheck;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        leaveFrameAndRestoreRbp(crb, masm);
        if (!isStub) {
            if (requiresReservedStackAccessCheck) {
                assert scratchForSafepointOnReturn != null;
                HotSpotForeignCallsProvider foreignCalls = (HotSpotForeignCallsProvider) crb.foreignCalls;

                Label noReserved = new Label();
                masm.cmpptr(rsp, new AMD64Address(r15, config.javaThreadReservedStackActivationOffset));
                masm.jccb(AMD64Assembler.ConditionFlag.Below, noReserved);
                // direct call to runtime without stub needs aligned stack
                int stackAdjust = crb.target.stackAlignment - crb.target.wordSize;
                if (stackAdjust > 0) {
                    masm.subq(rsp, stackAdjust);
                }
                ForeignCallLinkage enableStackReservedZone = foreignCalls.lookupForeignCall(ENABLE_STACK_RESERVED_ZONE);
                CallingConvention cc = enableStackReservedZone.getOutgoingCallingConvention();
                assert cc.getArgumentCount() == 1;
                Register arg0 = ((RegisterValue) cc.getArgument(0)).getRegister();
                masm.movq(arg0, thread);
                AMD64Call.directCall(crb, masm, enableStackReservedZone, scratchForSafepointOnReturn, false, null);
                if (stackAdjust > 0) {
                    masm.addq(rsp, stackAdjust);
                }
                AMD64Call.directJmp(crb, masm, foreignCalls.lookupForeignCall(THROW_DELAYED_STACKOVERFLOW_ERROR), scratchForSafepointOnReturn);
                masm.bind(noReserved);
            }

            // Every non-stub compile method must have a poll before the return.
            AMD64HotSpotSafepointOp.emitCode(crb, masm, config, true, null, thread, scratchForSafepointOnReturn);

            /*
             * We potentially return to the interpreter, and that's an AVX-SSE transition. The only
             * live value at this point should be the return value in either rax, or in xmm0 with
             * the upper half of the register unused, so we don't destroy any value here.
             */
            if (masm.supports(CPUFeature.AVX)) {
                masm.vzeroupper();
            }
        }
        masm.ret(0);
    }
}
