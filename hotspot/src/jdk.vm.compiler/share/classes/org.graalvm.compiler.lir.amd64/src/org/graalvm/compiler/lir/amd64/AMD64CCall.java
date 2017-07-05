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
package org.graalvm.compiler.lir.amd64;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;

public final class AMD64CCall extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64CCall> TYPE = LIRInstructionClass.create(AMD64CCall.class);

    @Def({REG, ILLEGAL}) protected Value result;
    @Use({REG, STACK}) protected Value[] parameters;
    @Use({REG}) protected Value functionPtr;
    @Use({REG}) protected Value numberOfFloatingPointArguments;

    public AMD64CCall(Value result, Value functionPtr, Value numberOfFloatingPointArguments, Value[] parameters) {
        super(TYPE);
        this.result = result;
        this.functionPtr = functionPtr;
        this.parameters = parameters;
        this.numberOfFloatingPointArguments = numberOfFloatingPointArguments;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        directCall(masm);
    }

    private void directCall(AMD64MacroAssembler masm) {
        Register reg = ValueUtil.asRegister(functionPtr);
        masm.call(reg);
        masm.ensureUniquePC();
    }

    @Override
    public boolean destroysCallerSavedRegisters() {
        return true;
    }

}
