/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.NumUtil;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public class AArch64HotSpotSafepointOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotSafepointOp> TYPE = LIRInstructionClass.create(AArch64HotSpotSafepointOp.class);

    @State protected LIRFrameState state;
    @Temp protected AllocatableValue scratchValue;

    private final GraalHotSpotVMConfig config;

    public AArch64HotSpotSafepointOp(LIRFrameState state, GraalHotSpotVMConfig config, AllocatableValue scratch) {
        super(TYPE);
        this.state = state;
        this.config = config;
        this.scratchValue = scratch;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register scratch = asRegister(scratchValue);
        emitCode(crb, masm, config, false, scratch, state);
    }

    /**
     * Conservatively checks whether we can load the safepoint polling address with a single ldr
     * instruction or not.
     *
     * @return true if it is guaranteed that polling page offset will always fit into a 21-bit
     *         signed integer, false otherwise.
     */
    private static boolean isPollingPageFar(GraalHotSpotVMConfig config) {
        final long pollingPageAddress = config.safepointPollingAddress;
        return !NumUtil.isSignedNbit(21, pollingPageAddress - config.codeCacheLowBound) || !NumUtil.isSignedNbit(21, pollingPageAddress - config.codeCacheHighBound);
    }

    public static void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm, GraalHotSpotVMConfig config, boolean onReturn, Register scratch, LIRFrameState state) {
        int pos = masm.position();
        if (isPollingPageFar(config)) {
            crb.recordMark(onReturn ? config.MARKID_POLL_RETURN_FAR : config.MARKID_POLL_FAR);
            masm.movNativeAddress(scratch, config.safepointPollingAddress);
            if (state != null) {
                crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            }
            masm.ldr(32, zr, AArch64Address.createBaseRegisterOnlyAddress(scratch));
        } else {
            crb.recordMark(onReturn ? config.MARKID_POLL_RETURN_NEAR : config.MARKID_POLL_NEAR);
            if (state != null) {
                crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            }
            masm.ldr(32, zr, AArch64Address.createPcLiteralAddress(0));
        }
    }

}
