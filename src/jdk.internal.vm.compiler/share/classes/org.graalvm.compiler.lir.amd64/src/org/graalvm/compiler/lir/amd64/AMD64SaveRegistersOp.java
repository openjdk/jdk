/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorMove;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Saves registers to stack slots.
 */
@Opcode("SAVE_REGISTER")
public class AMD64SaveRegistersOp extends SaveRegistersOp {
    public static final LIRInstructionClass<AMD64SaveRegistersOp> TYPE = LIRInstructionClass.create(AMD64SaveRegistersOp.class);

    /**
     *
     * @param savedRegisters the registers saved by this operation which may be subject to
     *            {@linkplain #remove(EconomicSet) pruning}
     * @param savedRegisterLocations the slots to which the registers are saved
     */
    public AMD64SaveRegistersOp(Register[] savedRegisters, AllocatableValue[] savedRegisterLocations) {
        super(TYPE, savedRegisters, savedRegisterLocations);
    }

    protected AMD64SaveRegistersOp(LIRInstructionClass<AMD64VectorMove.SaveRegistersOp> type, Register[] savedRegisters, AllocatableValue[] slots) {
        super(type, savedRegisters, slots);
    }

    protected void saveRegister(CompilationResultBuilder crb, AMD64MacroAssembler masm, StackSlot result, Register input) {
        AMD64Move.reg2stack((AMD64Kind) result.getPlatformKind(), crb, masm, result, input);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
        for (int i = 0; i < savedRegisters.length; i++) {
            if (savedRegisters[i] != null) {
                assert isStackSlot(slots[i]) : "not a StackSlot: " + slots[i];
                saveRegister(crb, masm, asStackSlot(slots[i]), savedRegisters[i]);
            }
        }
    }
}
