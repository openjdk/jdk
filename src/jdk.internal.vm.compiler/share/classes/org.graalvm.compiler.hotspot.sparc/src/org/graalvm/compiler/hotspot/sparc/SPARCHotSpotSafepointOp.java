/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.sparc.SPARC.g0;

import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotDebugInfoBuilder;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.sparc.SPARCLIRInstruction;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public class SPARCHotSpotSafepointOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotSafepointOp> TYPE = LIRInstructionClass.create(SPARCHotSpotSafepointOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(9);

    @State protected LIRFrameState state;
    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) AllocatableValue safepointPollAddress;
    private final GraalHotSpotVMConfig config;
    private final Register thread;

    public SPARCHotSpotSafepointOp(LIRFrameState state, GraalHotSpotVMConfig config, Register thread, LIRGeneratorTool tool) {
        super(TYPE, SIZE);
        this.state = state;
        this.config = config;
        this.thread = thread;
        SPARCHotSpotLIRGenerator lirGen = (SPARCHotSpotLIRGenerator) tool;
        safepointPollAddress = lirGen.getSafepointAddressValue();
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        emitCode(crb, masm, config, false, state, thread, safepointPollAddress);
    }

    public static void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm, GraalHotSpotVMConfig config, boolean atReturn, LIRFrameState state, Register thread,
                    Value safepointPollAddress) {
        if (config.threadLocalHandshakes) {
            emitThreadLocalPoll(crb, masm, config, atReturn, state, thread);
        } else {
            emitGlobalPoll(crb, masm, config, atReturn, state, asRegister(safepointPollAddress));
        }
    }

    /**
     * Emit a global safepoint poll.
     */
    private static void emitGlobalPoll(CompilationResultBuilder crb, SPARCMacroAssembler masm, GraalHotSpotVMConfig config, boolean atReturn, LIRFrameState state, Register safepointPollAddress) {
        crb.recordMark(atReturn ? config.MARKID_POLL_RETURN_FAR : config.MARKID_POLL_FAR);
        if (state != null) {
            final int pos = masm.position();
            crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
        }
        masm.ldx(new SPARCAddress(safepointPollAddress, 0), g0);
    }

    /**
     * Emit a thread-local safepoint poll.
     */
    private static void emitThreadLocalPoll(CompilationResultBuilder crb, SPARCMacroAssembler masm, GraalHotSpotVMConfig config, boolean atReturn, LIRFrameState state, Register thread) {
        assert !atReturn || state == null : "state is unneeded at return";

        assert config.threadPollingPageOffset >= 0;

        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register scratch = scratchReg.getRegister();

            masm.ldx(new SPARCAddress(thread, config.threadPollingPageOffset), scratch);

            crb.recordMark(atReturn ? config.MARKID_POLL_RETURN_FAR : config.MARKID_POLL_FAR);
            if (state != null) {
                final int pos = masm.position();
                crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            }
            masm.ldx(new SPARCAddress(scratch, 0), g0);
        }
    }

    static AllocatableValue getSafepointAddressValue(SPARCHotSpotLIRGenerator gen) {
        if (gen.config.threadLocalHandshakes) {
            return Value.ILLEGAL;
        } else {
            return gen.newVariable(LIRKind.value(gen.target().arch.getWordKind()));
        }
    }

    static void emitPrologue(SPARCHotSpotNodeLIRBuilder lir, SPARCHotSpotLIRGenerator gen) {
        if (!gen.config.threadLocalHandshakes) {
            AllocatableValue var = gen.getSafepointAddressValue();
            lir.append(new SPARCHotSpotSafepointOp.SPARCLoadSafepointPollAddress(var, gen.config));
            gen.append(((HotSpotDebugInfoBuilder) lir.getDebugInfoBuilder()).lockStack());
        }
    }

    public static class SPARCLoadSafepointPollAddress extends SPARCLIRInstruction {
        public static final LIRInstructionClass<SPARCLoadSafepointPollAddress> TYPE = LIRInstructionClass.create(SPARCLoadSafepointPollAddress.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Def({OperandFlag.REG}) protected AllocatableValue result;
        private final GraalHotSpotVMConfig config;

        public SPARCLoadSafepointPollAddress(AllocatableValue result, GraalHotSpotVMConfig config) {
            super(TYPE, SIZE);
            this.result = result;
            this.config = config;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            masm.setx(config.safepointPollingAddress, ValueUtil.asRegister(result), false);
        }
    }
}
