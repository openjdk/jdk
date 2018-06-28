/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;

import org.graalvm.compiler.asm.aarch64.AArch64Assembler.PrefetchMode;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

public final class AArch64PrefetchOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64PrefetchOp> TYPE = LIRInstructionClass.create(AArch64PrefetchOp.class);

    private final PrefetchMode mode;  // AllocatePrefetchInstr
    @Alive({COMPOSITE}) protected AArch64AddressValue address;

    public AArch64PrefetchOp(AArch64AddressValue address, PrefetchMode mode) {
        super(TYPE);
        this.address = address;
        this.mode = mode;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        // instr gets ignored!
        masm.prfm(address.toAddress(), mode);
    }
}
