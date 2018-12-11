/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.Arrays;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Restores registers from stack slots.
 */
@Opcode("RESTORE_REGISTER")
public class AArch64RestoreRegistersOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64RestoreRegistersOp> TYPE = LIRInstructionClass.create(AArch64RestoreRegistersOp.class);

    /**
     * The slots from which the registers are restored.
     */
    @Use(STACK) protected final AllocatableValue[] slots;

    /**
     * The operation that saved the registers restored by this operation.
     */
    private final AArch64SaveRegistersOp save;

    public AArch64RestoreRegistersOp(AllocatableValue[] values, AArch64SaveRegistersOp save) {
        this(TYPE, values, save);
    }

    protected AArch64RestoreRegistersOp(LIRInstructionClass<? extends AArch64RestoreRegistersOp> c, AllocatableValue[] values, AArch64SaveRegistersOp save) {
        super(c);
        assert Arrays.asList(values).stream().allMatch(LIRValueUtil::isVirtualStackSlot);
        this.slots = values;
        this.save = save;
    }

    protected Register[] getSavedRegisters() {
        return save.savedRegisters;
    }

    protected void restoreRegister(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register result, StackSlot input) {
        AArch64Move.stack2reg(crb, masm, result.asValue(), input);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register[] savedRegisters = getSavedRegisters();
        for (int i = 0; i < savedRegisters.length; i++) {
            if (savedRegisters[i] != null) {
                assert isStackSlot(slots[i]) : "not a StackSlot: " + slots[i];
                restoreRegister(crb, masm, savedRegisters[i], asStackSlot(slots[i]));
            }
        }
    }
}
