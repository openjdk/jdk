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

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

public class AMD64HotSpotMove {

    public static final class HotSpotLoadObjectConstantOp extends AMD64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<HotSpotLoadObjectConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadObjectConstantOp.class);

        @Def({REG, STACK}) private AllocatableValue result;
        private final HotSpotObjectConstant input;

        public HotSpotLoadObjectConstantOp(AllocatableValue result, HotSpotObjectConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (GeneratePIC.getValue(crb.getOptions())) {
                throw GraalError.shouldNotReachHere("Object constant load should not be happening directly");
            }
            boolean compressed = input.isCompressed();
            if (crb.target.inlineObjects) {
                crb.recordInlineDataInCode(input);
                if (isRegister(result)) {
                    if (compressed) {
                        masm.movl(asRegister(result), 0xDEADDEAD);
                    } else {
                        masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                    }
                } else {
                    assert isStackSlot(result);
                    if (compressed) {
                        masm.movl((AMD64Address) crb.asAddress(result), 0xDEADDEAD);
                    } else {
                        throw GraalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                }
            } else {
                if (isRegister(result)) {
                    AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(input, compressed ? 4 : 8);
                    if (compressed) {
                        masm.movl(asRegister(result), address);
                    } else {
                        masm.movq(asRegister(result), address);
                    }
                } else {
                    throw GraalError.shouldNotReachHere("Cannot directly store data patch to memory");
                }
            }
        }

        @Override
        public Constant getConstant() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    public static final class BaseMove extends AMD64LIRInstruction {
        public static final LIRInstructionClass<BaseMove> TYPE = LIRInstructionClass.create(BaseMove.class);

        @Def({REG, HINT}) protected AllocatableValue result;
        private final GraalHotSpotVMConfig config;

        public BaseMove(AllocatableValue result, GraalHotSpotVMConfig config) {
            super(TYPE);
            this.result = result;
            this.config = config;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.movq(asRegister(result), masm.getPlaceholder(-1));
            crb.recordMark(config.MARKID_NARROW_KLASS_BASE_ADDRESS);
        }

    }

    public static final class HotSpotLoadMetaspaceConstantOp extends AMD64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<HotSpotLoadMetaspaceConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadMetaspaceConstantOp.class);

        @Def({REG, STACK}) private AllocatableValue result;
        private final HotSpotMetaspaceConstant input;

        public HotSpotLoadMetaspaceConstantOp(AllocatableValue result, HotSpotMetaspaceConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (GeneratePIC.getValue(crb.getOptions())) {
                throw GraalError.shouldNotReachHere("Metaspace constant load should not be happening directly");
            }
            boolean compressed = input.isCompressed();
            if (isRegister(result)) {
                if (compressed) {
                    crb.recordInlineDataInCode(input);
                    masm.movl(asRegister(result), 0xDEADDEAD);
                } else {
                    crb.recordInlineDataInCode(input);
                    masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                }
            } else {
                assert isStackSlot(result);
                if (compressed) {
                    crb.recordInlineDataInCode(input);
                    masm.movl((AMD64Address) crb.asAddress(result), 0xDEADDEAD);
                } else {
                    throw GraalError.shouldNotReachHere("Cannot store 64-bit constants to memory");
                }
            }
        }

        @Override
        public Constant getConstant() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    public static void decodeKlassPointer(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register register, Register scratch, AMD64Address address, GraalHotSpotVMConfig config) {
        CompressEncoding encoding = config.getKlassEncoding();
        masm.movl(register, address);
        if (encoding.getShift() != 0) {
            masm.shlq(register, encoding.getShift());
        }
        boolean pic = GeneratePIC.getValue(crb.getOptions());
        if (pic || encoding.hasBase()) {
            if (pic) {
                masm.movq(scratch, masm.getPlaceholder(-1));
                crb.recordMark(config.MARKID_NARROW_KLASS_BASE_ADDRESS);
            } else {
                assert encoding.getBase() != 0;
                masm.movq(scratch, encoding.getBase());
            }
            masm.addq(register, scratch);
        }
    }
}
