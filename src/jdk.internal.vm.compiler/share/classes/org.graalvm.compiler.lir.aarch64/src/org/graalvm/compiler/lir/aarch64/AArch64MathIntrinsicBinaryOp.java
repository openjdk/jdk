/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGenerator;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

public abstract class AArch64MathIntrinsicBinaryOp extends AArch64LIRInstruction {

    @Def protected Value output;
    @Use protected Value input0;
    @Use protected Value input1;
    @Temp protected Value[] temps;

    public AArch64MathIntrinsicBinaryOp(LIRInstructionClass<? extends AArch64LIRInstruction> type, Register... registers) {
        super(type);
        input0 = v0.asValue(LIRKind.value(AArch64Kind.V64_WORD));
        input1 = v0.asValue(LIRKind.value(AArch64Kind.V64_WORD));
        output = v0.asValue(LIRKind.value(AArch64Kind.V64_WORD));

        temps = registersToValues(registers);
    }

    protected static Value[] registersToValues(Register[] registers) {
        Value[] temps = new Value[registers.length];
        for (int i = 0; i < registers.length; i++) {
            Register register = registers[i];
            if (AArch64.CPU.equals(register.getRegisterCategory())) {
                temps[i] = register.asValue(LIRKind.value(AArch64Kind.V64_WORD));
            } else if (AArch64.SIMD.equals(register.getRegisterCategory())) {
                temps[i] = register.asValue(LIRKind.value(AArch64Kind.V64_WORD));
            } else {
                throw GraalError.shouldNotReachHere("Unsupported register type in math stubs.");
            }
        }
        return temps;
    }

    public final Variable emitLIRWrapper(LIRGenerator gen, Value x, Value y) {
        LIRKind kind = LIRKind.combine(x, y);
        RegisterValue v0value = v0.asValue(kind);
        gen.emitMove(v0value, x);
        RegisterValue v1value = v1.asValue(kind);
        gen.emitMove(v1value, y);
        gen.append(this);
        Variable result = gen.newVariable(kind);
        gen.emitMove(result, v0value);
        return result;
    }
}
