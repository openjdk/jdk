/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

public final class AMD64HotSpotLoadConfigValueOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64HotSpotLoadConfigValueOp> TYPE = LIRInstructionClass.create(AMD64HotSpotLoadConfigValueOp.class);

    @Def({OperandFlag.REG}) protected AllocatableValue result;
    private final int markId;

    public AMD64HotSpotLoadConfigValueOp(int markId, AllocatableValue result) {
        super(TYPE);
        this.result = result;
        this.markId = markId;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (GeneratePIC.getValue()) {
            AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
            Register reg = asRegister(result);
            AMD64Address placeholder = masm.getPlaceholder(-1);
            switch (kind) {
                case BYTE:
                    masm.movsbl(reg, placeholder);
                    break;
                case WORD:
                    masm.movswl(reg, placeholder);
                    break;
                case DWORD:
                    masm.movl(reg, placeholder);
                    break;
                case QWORD:
                    masm.movq(reg, placeholder);
                    break;
                default:
                    throw GraalError.unimplemented();
            }
        } else {
            throw GraalError.unimplemented();
        }
        crb.recordMark(markId);
    }

}
