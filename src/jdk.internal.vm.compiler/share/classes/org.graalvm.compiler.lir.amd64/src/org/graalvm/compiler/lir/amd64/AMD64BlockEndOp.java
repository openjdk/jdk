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


package org.graalvm.compiler.lir.amd64;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

public abstract class AMD64BlockEndOp extends LIRInstruction implements BlockEndOp {

    public static final LIRInstructionClass<AMD64BlockEndOp> TYPE = LIRInstructionClass.create(AMD64BlockEndOp.class);

    protected AMD64BlockEndOp(LIRInstructionClass<? extends AMD64BlockEndOp> c) {
        super(c);
    }

    @Override
    public final void emitCode(CompilationResultBuilder crb) {
        emitCode(crb, (AMD64MacroAssembler) crb.asm);
    }

    public abstract void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm);
}
