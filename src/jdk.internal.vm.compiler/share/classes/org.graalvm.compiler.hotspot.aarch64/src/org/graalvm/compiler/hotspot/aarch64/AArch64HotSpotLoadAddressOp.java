/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

public final class AArch64HotSpotLoadAddressOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64HotSpotLoadAddressOp> TYPE = LIRInstructionClass.create(AArch64HotSpotLoadAddressOp.class);

    @Def({OperandFlag.REG}) protected AllocatableValue result;
    private final Constant constant;
    private final Object note;

    public AArch64HotSpotLoadAddressOp(AllocatableValue result, Constant constant, Object note) {
        super(TYPE);
        this.result = result;
        this.constant = constant;
        this.note = note;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        crb.recordInlineDataInCodeWithNote(constant, note);
        AArch64Kind kind = (AArch64Kind) result.getPlatformKind();
        int size = 0;
        switch (kind) {
            case DWORD:
                size = 32;
                break;
            case QWORD:
                size = 64;
                break;
            default:
                throw GraalError.shouldNotReachHere("unexpected kind: " + kind);
        }
        if (crb.compilationResult.isImmutablePIC()) {
            Register dst = asRegister(result);
            masm.addressOf(dst);
            masm.ldr(size, dst, AArch64Address.createBaseRegisterOnlyAddress(dst));
        } else {
            masm.ldr(size, asRegister(result), masm.getPlaceholder(-1));
        }
    }
}
