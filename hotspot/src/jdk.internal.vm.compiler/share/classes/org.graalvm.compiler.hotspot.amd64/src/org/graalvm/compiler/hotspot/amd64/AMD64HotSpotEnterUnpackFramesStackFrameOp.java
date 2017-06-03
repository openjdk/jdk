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

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rip;
import static jdk.vm.ci.code.ValueUtil.asRegister;

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
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;

/**
 * Emits code that enters a stack frame which is tailored to call the C++ method
 * {@link HotSpotBackend#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@Opcode("ENTER_UNPACK_FRAMES_STACK_FRAME")
final class AMD64HotSpotEnterUnpackFramesStackFrameOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotEnterUnpackFramesStackFrameOp> TYPE = LIRInstructionClass.create(AMD64HotSpotEnterUnpackFramesStackFrameOp.class);

    private final Register threadRegister;
    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    private final int threadLastJavaFpOffset;
    @Alive(REG) AllocatableValue framePc;
    @Alive(REG) AllocatableValue senderSp;
    @Alive(REG) AllocatableValue senderFp;

    private final SaveRegistersOp saveRegisterOp;

    AMD64HotSpotEnterUnpackFramesStackFrameOp(Register threadRegister, int threadLastJavaSpOffset, int threadLastJavaPcOffset, int threadLastJavaFpOffset, AllocatableValue framePc,
                    AllocatableValue senderSp, AllocatableValue senderFp, SaveRegistersOp saveRegisterOp) {
        super(TYPE);
        this.threadRegister = threadRegister;
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.threadLastJavaFpOffset = threadLastJavaFpOffset;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.senderFp = senderFp;
        this.saveRegisterOp = saveRegisterOp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        FrameMap frameMap = crb.frameMap;
        RegisterConfig registerConfig = frameMap.getRegisterConfig();
        RegisterSaveLayout registerSaveLayout = saveRegisterOp.getMap(frameMap);
        Register stackPointerRegister = registerConfig.getFrameRegister();
        final int totalFrameSize = frameMap.totalFrameSize();

        // Push return address.
        masm.push(asRegister(framePc));

        // Push base pointer.
        masm.push(asRegister(senderFp));
        masm.movq(rbp, stackPointerRegister);

        /*
         * Allocate a full sized frame. Since return address and base pointer are already in place
         * (see above) we allocate two words less.
         */
        masm.decrementq(stackPointerRegister, totalFrameSize - 2 * crb.target.wordSize);

        // Save return registers after moving the frame.
        final int stackSlotSize = frameMap.getTarget().wordSize;
        Register integerResultRegister = registerConfig.getReturnRegister(JavaKind.Long);
        masm.movptr(new AMD64Address(stackPointerRegister, registerSaveLayout.registerToSlot(integerResultRegister) * stackSlotSize), integerResultRegister);

        Register floatResultRegister = registerConfig.getReturnRegister(JavaKind.Double);
        masm.movdbl(new AMD64Address(stackPointerRegister, registerSaveLayout.registerToSlot(floatResultRegister) * stackSlotSize), floatResultRegister);

        // Set up last Java values.
        masm.movq(new AMD64Address(threadRegister, threadLastJavaSpOffset), stackPointerRegister);

        /*
         * Save the PC since it cannot easily be retrieved using the last Java SP after we aligned
         * SP. Don't need the precise return PC here, just precise enough to point into this code
         * blob.
         */
        masm.leaq(rax, new AMD64Address(rip, 0));
        masm.movq(new AMD64Address(threadRegister, threadLastJavaPcOffset), rax);

        // Use BP because the frames look interpreted now.
        masm.movq(new AMD64Address(threadRegister, threadLastJavaFpOffset), rbp);

        // Align the stack for the following unpackFrames call.
        masm.andq(stackPointerRegister, -(crb.target.stackAlignment));
    }
}
