/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.UNINITIALIZED;

import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.Asi;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.sparc.SPARCKind;

@Opcode("BSWAP")
public final class SPARCByteSwapOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
    public static final LIRInstructionClass<SPARCByteSwapOp> TYPE = LIRInstructionClass.create(SPARCByteSwapOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(3);
    @Def({REG, HINT}) protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue input;
    @Temp({REG}) protected AllocatableValue tempIndex;
    @Use({STACK, UNINITIALIZED}) protected AllocatableValue tmpSlot;

    public SPARCByteSwapOp(LIRGeneratorTool tool, AllocatableValue result, AllocatableValue input) {
        super(TYPE, SIZE);
        this.result = result;
        this.input = input;
        this.tmpSlot = tool.getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(XWORD));
        this.tempIndex = tool.newVariable(LIRKind.value(XWORD));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        SPARCAddress addr = (SPARCAddress) crb.asAddress(tmpSlot);
        SPARCMove.emitStore(input, addr, result.getPlatformKind(), SPARCDelayedControlTransfer.DUMMY, null, crb, masm);
        if (addr.getIndex().equals(Register.None)) {
            Register tempReg = ValueUtil.asRegister(tempIndex, XWORD);
            masm.setx(addr.getDisplacement(), tempReg, false);
            addr = new SPARCAddress(addr.getBase(), tempReg);
        }
        getDelayedControlTransfer().emitControlTransfer(crb, masm);
        switch ((SPARCKind) input.getPlatformKind()) {
            case WORD:
                masm.lduwa(addr.getBase(), addr.getIndex(), asRegister(result, WORD), Asi.ASI_PRIMARY_LITTLE);
                break;
            case XWORD:
                masm.ldxa(addr.getBase(), addr.getIndex(), asRegister(result, XWORD), Asi.ASI_PRIMARY_LITTLE);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }
}
