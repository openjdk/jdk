/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.alloc.trace;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import java.util.EnumSet;

import org.graalvm.compiler.lir.CompositeValue;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Represents a {@link #register} which has a shadow copy on the {@link #stackslot stack}.
 */
public final class ShadowedRegisterValue extends CompositeValue {
    private static final EnumSet<OperandFlag> registerFlags = EnumSet.of(REG);
    private static final EnumSet<OperandFlag> stackslotFlags = EnumSet.of(STACK);

    @Component({REG}) protected RegisterValue register;
    @Component({STACK}) protected AllocatableValue stackslot;

    public ShadowedRegisterValue(RegisterValue register, AllocatableValue stackslot) {
        super(register.getValueKind());
        assert (register.getValueKind().equals(stackslot.getValueKind()));
        this.register = register;
        this.stackslot = stackslot;
    }

    public RegisterValue getRegister() {
        return register;
    }

    public AllocatableValue getStackSlot() {
        return stackslot;
    }

    @Override
    public CompositeValue forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedure proc) {
        RegisterValue newRegister = (RegisterValue) proc.doValue(inst, register, mode, registerFlags);
        AllocatableValue newStackSlot = (AllocatableValue) proc.doValue(inst, stackslot, mode, stackslotFlags);
        if (register.equals(newRegister) || stackslot.equals(newStackSlot)) {
            return this;
        }
        return new ShadowedRegisterValue(newRegister, newStackSlot);
    }

    @Override
    protected void visitEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueConsumer proc) {
        proc.visitValue(inst, register, mode, registerFlags);
        proc.visitValue(inst, stackslot, mode, stackslotFlags);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ShadowedRegisterValue other = (ShadowedRegisterValue) obj;
        assert register != null;
        assert stackslot != null;
        assert other.register != null;
        assert other.stackslot != null;
        if (!register.equals(other.register)) {
            return false;
        }
        if (!stackslot.equals(other.stackslot)) {
            return false;
        }
        return true;
    }

}
