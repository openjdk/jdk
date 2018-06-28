/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.amd64.vector;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64VectorAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;

public class AMD64VectorUnary {

    public static final class AVXUnaryOp extends AMD64VectorLIRInstruction {
        public static final LIRInstructionClass<AVXUnaryOp> TYPE = LIRInstructionClass.create(AVXUnaryOp.class);

        @Opcode private final AMD64VectorAssembler.VexRMOp opcode;
        private final AVXKind.AVXSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        public AVXUnaryOp(AMD64VectorAssembler.VexRMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (isRegister(input)) {
                opcode.emit(vasm, size, asRegister(result), asRegister(input));
            } else {
                opcode.emit(vasm, size, asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }

    public static final class AVXUnaryMemoryOp extends AMD64VectorLIRInstruction {
        public static final LIRInstructionClass<AVXUnaryMemoryOp> TYPE = LIRInstructionClass.create(AVXUnaryMemoryOp.class);

        @Opcode private final AMD64VectorAssembler.VexRMOp opcode;
        private final AVXKind.AVXSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected AMD64AddressValue input;
        @State protected LIRFrameState state;

        public AVXUnaryMemoryOp(AMD64VectorAssembler.VexRMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AMD64AddressValue input, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;
            this.result = result;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (state != null) {
                crb.recordImplicitException(vasm.position(), state);
            }
            opcode.emit(vasm, size, asRegister(result), input.toAddress());
        }
    }

    public static final class AVXBroadcastOp extends AMD64VectorLIRInstruction {
        public static final LIRInstructionClass<AVXBroadcastOp> TYPE = LIRInstructionClass.create(AVXBroadcastOp.class);

        @Opcode private final AMD64VectorAssembler.VexRMOp opcode;
        private final AVXKind.AVXSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public AVXBroadcastOp(AMD64VectorAssembler.VexRMOp opcode, AVXKind.AVXSize size, AllocatableValue result, Value input) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (isRegister(input)) {
                opcode.emit(vasm, size, asRegister(result), asRegister(input));
            } else if (isConstantValue(input)) {
                int align = input.getPlatformKind().getSizeInBytes();
                AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(asConstant(input), align);
                opcode.emit(vasm, size, asRegister(result), address);
            } else {
                opcode.emit(vasm, size, asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }

    public static final class AVXConvertMemoryOp extends AMD64VectorLIRInstruction {
        public static final LIRInstructionClass<AVXConvertMemoryOp> TYPE = LIRInstructionClass.create(AVXConvertMemoryOp.class);

        @Opcode private final AMD64VectorAssembler.VexRVMOp opcode;
        private final AVXKind.AVXSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected AMD64AddressValue input;
        @State protected LIRFrameState state;

        public AVXConvertMemoryOp(AMD64VectorAssembler.VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AMD64AddressValue input, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;
            this.result = result;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (state != null) {
                crb.recordImplicitException(vasm.position(), state);
            }
            opcode.emit(vasm, size, asRegister(result), asRegister(result), input.toAddress());
        }
    }

    public static final class AVXConvertOp extends AMD64VectorLIRInstruction {
        public static final LIRInstructionClass<AVXConvertOp> TYPE = LIRInstructionClass.create(AVXConvertOp.class);

        @Opcode private final AMD64VectorAssembler.VexRVMOp opcode;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        public AVXConvertOp(AMD64VectorAssembler.VexRVMOp opcode, AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.opcode = opcode;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (isRegister(input)) {
                if (!asRegister(input).equals(asRegister(result))) {
                    // clear result register to avoid unnecessary dependency
                    AMD64VectorAssembler.VexRVMOp.VXORPD.emit(vasm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(result));
                }
                opcode.emit(vasm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(input));
            } else {
                AMD64VectorAssembler.VexRVMOp.VXORPD.emit(vasm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), asRegister(result));
                opcode.emit(vasm, AVXKind.AVXSize.XMM, asRegister(result), asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }
}
