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

import static org.graalvm.compiler.lir.amd64.AMD64SaveRegistersOp.prune;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.framemap.FrameMap;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterSaveLayout;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Writes well known garbage values to registers.
 */
@Opcode("ZAP_REGISTER")
public final class AMD64ZapRegistersOp extends AMD64LIRInstruction implements SaveRegistersOp {
    public static final LIRInstructionClass<AMD64ZapRegistersOp> TYPE = LIRInstructionClass.create(AMD64ZapRegistersOp.class);

    /**
     * The registers that are zapped.
     */
    protected final Register[] zappedRegisters;

    /**
     * The garbage values that are written to the registers.
     */
    protected final JavaConstant[] zapValues;

    public AMD64ZapRegistersOp(Register[] zappedRegisters, JavaConstant[] zapValues) {
        super(TYPE);
        this.zappedRegisters = zappedRegisters;
        this.zapValues = zapValues;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        for (int i = 0; i < zappedRegisters.length; i++) {
            Register reg = zappedRegisters[i];
            if (reg != null) {
                AMD64Move.const2reg(crb, masm, reg, zapValues[i]);
            }
        }
    }

    @Override
    public boolean supportsRemove() {
        return true;
    }

    @Override
    public int remove(EconomicSet<Register> doNotSave) {
        return prune(doNotSave, zappedRegisters);
    }

    @Override
    public RegisterSaveLayout getMap(FrameMap frameMap) {
        return new RegisterSaveLayout(new Register[0], new int[0]);
    }
}
