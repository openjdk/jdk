/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;

@Opcode("CRUNTIME_CALL_EPILOGUE")
final class AMD64HotSpotCRuntimeCallEpilogueOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotCRuntimeCallEpilogueOp> TYPE = LIRInstructionClass.create(AMD64HotSpotCRuntimeCallEpilogueOp.class);

    private final int threadLastJavaSpOffset;
    private final int threadLastJavaFpOffset;
    private final int threadLastJavaPcOffset;
    private final Register thread;

    AMD64HotSpotCRuntimeCallEpilogueOp(int threadLastJavaSpOffset, int threadLastJavaFpOffset, int threadLastJavaPcOffset, Register thread) {
        super(TYPE);
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaFpOffset = threadLastJavaFpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.thread = thread;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        // reset last Java frame:
        masm.movslq(new AMD64Address(thread, threadLastJavaSpOffset), 0);
        masm.movslq(new AMD64Address(thread, threadLastJavaFpOffset), 0);
        masm.movslq(new AMD64Address(thread, threadLastJavaPcOffset), 0);
    }
}
