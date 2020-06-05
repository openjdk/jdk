/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.hotspot.HotSpotMarkId;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.aarch64.AArch64Call;
import org.graalvm.compiler.lir.aarch64.AArch64Call.IndirectCallOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

/**
 * A register indirect call that complies with the extra conventions for such calls in HotSpot. In
 * particular, the metaspace Method of the callee must be in r12 for the case where a vtable entry's
 * _from_compiled_entry is the address of an C2I adapter. Such adapters expect the target method to
 * be in r12.
 */
@Opcode("CALL_INDIRECT")
final class AArch64IndirectCallOp extends IndirectCallOp {

    public static final LIRInstructionClass<AArch64IndirectCallOp> TYPE = LIRInstructionClass.create(AArch64IndirectCallOp.class);

    /**
     * Vtable stubs expect the metaspace Method in r12.
     */
    public static final Register METHOD = r12;

    @Use({REG}) private Value metaspaceMethod;

    AArch64IndirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value metaspaceMethod, Value targetAddress, LIRFrameState state) {
        super(TYPE, callTarget, result, parameters, temps, targetAddress, state);
        this.metaspaceMethod = metaspaceMethod;
    }

    @Override
    @SuppressWarnings("try")
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (CompilationResultBuilder.CallContext callContext = crb.openCallContext(false)) {
            crb.recordMark(HotSpotMarkId.INLINE_INVOKE);
            Register callReg = asRegister(targetAddress);
            assert !callReg.equals(METHOD);
            AArch64Call.indirectCall(crb, masm, callReg, callTarget, state);
        }
    }

    @Override
    public void verify() {
        super.verify();
        assert asRegister(metaspaceMethod).equals(METHOD);
    }
}
