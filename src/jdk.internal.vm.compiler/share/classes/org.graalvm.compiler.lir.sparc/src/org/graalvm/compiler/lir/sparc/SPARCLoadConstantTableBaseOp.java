/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.sparc;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StandardOp.NoOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Loads the constant section base into a register.
 *
 * <p>
 * Layout:
 *
 * <pre>
 * +----constant section----+--pad--+---code section--+
 * |<-------------------_------------------->|
 *                      ^- Constant section base pointer
 * </pre>
 *
 * The constant section base pointer is placed as such that the lowest offset -4096 points to the
 * start of the constant section.
 * <p>
 * If the constant section grows beyond 8k size, the immediate addressing cannot be used anymore; in
 * this case absolute addressing (without using the base pointer is used). See also:
 * CodeInstaller::pd_patch_DataSectionReference
 *
 * @see SPARCMove#loadFromConstantTable(CompilationResultBuilder, SPARCMacroAssembler, Register,
 *      jdk.vm.ci.meta.Constant, Register, SPARCDelayedControlTransfer)
 */
public class SPARCLoadConstantTableBaseOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCLoadConstantTableBaseOp> TYPE = LIRInstructionClass.create(SPARCLoadConstantTableBaseOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(9);

    private final NoOp placeHolder;
    @Def({REG}) private AllocatableValue base;

    public SPARCLoadConstantTableBaseOp(Variable base, NoOp placeHolder) {
        super(TYPE, SIZE);
        this.base = base;
        this.placeHolder = placeHolder;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Register baseRegister = asRegister(base);
        int beforePosition = masm.position();
        masm.rdpc(baseRegister);
        // Must match with CodeInstaller::pd_patch_DataSectionReference
        masm.add(baseRegister, (int) SPARCAssembler.minSimm(13), baseRegister);
        masm.sub(baseRegister, beforePosition, baseRegister);
    }

    public AllocatableValue getResult() {
        return base;
    }

    public void setAlive(LIR lir, boolean alive) {
        if (alive) {
            placeHolder.replace(lir, this);
        } else {
            placeHolder.remove(lir);
        }
    }
}
