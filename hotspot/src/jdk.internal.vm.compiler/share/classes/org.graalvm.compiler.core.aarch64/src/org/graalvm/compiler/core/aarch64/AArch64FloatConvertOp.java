/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public final class AArch64FloatConvertOp extends AArch64LIRInstruction {
    private static final LIRInstructionClass<AArch64FloatConvertOp> TYPE = LIRInstructionClass.create(AArch64FloatConvertOp.class);

    private final FloatConvert op;
    @Def protected AllocatableValue resultValue;
    @Use protected AllocatableValue inputValue;

    protected AArch64FloatConvertOp(FloatConvert op, AllocatableValue resultValue, AllocatableValue inputValue) {
        super(TYPE);
        this.op = op;
        this.resultValue = resultValue;
        this.inputValue = inputValue;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        int fromSize = inputValue.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        int toSize = resultValue.getPlatformKind().getSizeInBytes() * Byte.SIZE;

        Register result = asRegister(resultValue);
        Register input = asRegister(inputValue);
        switch (op) {
            case F2I:
            case D2I:
            case F2L:
            case D2L:
                masm.fcvtzs(toSize, fromSize, result, input);
                break;
            case I2F:
            case I2D:
            case L2F:
            case L2D:
                masm.scvtf(toSize, fromSize, result, input);
                break;
            case D2F:
            case F2D:
                masm.fcvt(fromSize, result, input);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

}
