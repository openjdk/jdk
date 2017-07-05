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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.CompressEncoding;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
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
            if (GeneratePIC.getValue()) {
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
            if (GeneratePIC.getValue()) {
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

    public static final class CompressPointer extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CompressPointer> TYPE = LIRInstructionClass.create(CompressPointer.class);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public CompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(AMD64Kind.QWORD, crb, masm, result, input);

            Register resReg = asRegister(result);
            if (encoding.base != 0 || GeneratePIC.getValue()) {
                Register baseReg = asRegister(baseRegister);
                if (!nonNull) {
                    masm.testq(resReg, resReg);
                    masm.cmovq(ConditionFlag.Equal, resReg, baseReg);
                }
                masm.subq(resReg, baseReg);
            }

            if (encoding.shift != 0) {
                masm.shrq(resReg, encoding.shift);
            }
        }
    }

    public static final class UncompressPointer extends AMD64LIRInstruction {
        public static final LIRInstructionClass<UncompressPointer> TYPE = LIRInstructionClass.create(UncompressPointer.class);

        private final CompressEncoding encoding;
        private final boolean nonNull;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Alive({REG, ILLEGAL}) protected AllocatableValue baseRegister;

        public UncompressPointer(AllocatableValue result, AllocatableValue input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(AMD64Kind.DWORD, crb, masm, result, input);

            Register resReg = asRegister(result);
            if (encoding.shift != 0) {
                masm.shlq(resReg, encoding.shift);
            }

            if (encoding.base != 0 || GeneratePIC.getValue()) {
                if (nonNull) {
                    masm.addq(resReg, asRegister(baseRegister));
                } else {
                    if (encoding.shift == 0) {
                        // if encoding.shift != 0, the flags are already set by the shlq
                        masm.testq(resReg, resReg);
                    }

                    Label done = new Label();
                    masm.jccb(ConditionFlag.Equal, done);
                    masm.addq(resReg, asRegister(baseRegister));
                    masm.bind(done);
                }
            }
        }
    }

    public static void decodeKlassPointer(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register register, Register scratch, AMD64Address address, GraalHotSpotVMConfig config) {
        CompressEncoding encoding = config.getKlassEncoding();
        masm.movl(register, address);
        if (encoding.shift != 0) {
            assert encoding.alignment == encoding.shift : "Decode algorithm is wrong";
            masm.shlq(register, encoding.alignment);
        }
        if (GeneratePIC.getValue() || encoding.base != 0) {
            if (GeneratePIC.getValue()) {
                masm.movq(scratch, masm.getPlaceholder(-1));
                crb.recordMark(config.MARKID_NARROW_KLASS_BASE_ADDRESS);
            } else {
                assert encoding.base != 0;
                masm.movq(scratch, encoding.base);
            }
            masm.addq(register, scratch);
        }
    }
}
