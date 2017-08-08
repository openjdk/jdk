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
package org.graalvm.compiler.hotspot.amd64;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.framemap.FrameMap;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterSaveLayout;
import jdk.vm.ci.meta.JavaKind;

/**
 * Emits code that leaves a stack frame which is tailored to call the C++ method
 * {@link HotSpotBackend#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@Opcode("LEAVE_UNPACK_FRAMES_STACK_FRAME")
final class AMD64HotSpotLeaveUnpackFramesStackFrameOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotLeaveUnpackFramesStackFrameOp> TYPE = LIRInstructionClass.create(AMD64HotSpotLeaveUnpackFramesStackFrameOp.class);

    private final Register threadRegister;
    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    private final int threadLastJavaFpOffset;

    private final SaveRegistersOp saveRegisterOp;

    AMD64HotSpotLeaveUnpackFramesStackFrameOp(Register threadRegister, int threadLastJavaSpOffset, int threadLastJavaPcOffset, int threadLastJavaFpOffset, SaveRegistersOp saveRegisterOp) {
        super(TYPE);
        this.threadRegister = threadRegister;
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.threadLastJavaFpOffset = threadLastJavaFpOffset;
        this.saveRegisterOp = saveRegisterOp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        FrameMap frameMap = crb.frameMap;
        RegisterConfig registerConfig = frameMap.getRegisterConfig();
        RegisterSaveLayout registerSaveLayout = saveRegisterOp.getMap(frameMap);
        Register stackPointerRegister = registerConfig.getFrameRegister();

        // Restore stack pointer.
        masm.movq(stackPointerRegister, new AMD64Address(threadRegister, threadLastJavaSpOffset));

        // Clear last Java frame values.
        masm.movslq(new AMD64Address(threadRegister, threadLastJavaSpOffset), 0);
        masm.movslq(new AMD64Address(threadRegister, threadLastJavaPcOffset), 0);
        masm.movslq(new AMD64Address(threadRegister, threadLastJavaFpOffset), 0);

        // Restore return values.
        final int stackSlotSize = frameMap.getTarget().wordSize;
        Register integerResultRegister = registerConfig.getReturnRegister(JavaKind.Long);
        masm.movptr(integerResultRegister, new AMD64Address(stackPointerRegister, registerSaveLayout.registerToSlot(integerResultRegister) * stackSlotSize));

        Register floatResultRegister = registerConfig.getReturnRegister(JavaKind.Double);
        masm.movdbl(floatResultRegister, new AMD64Address(stackPointerRegister, registerSaveLayout.registerToSlot(floatResultRegister) * stackSlotSize));
    }
}
