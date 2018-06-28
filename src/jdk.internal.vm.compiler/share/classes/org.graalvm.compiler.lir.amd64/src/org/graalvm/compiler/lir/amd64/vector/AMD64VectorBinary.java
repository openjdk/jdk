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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

public class AMD64VectorBinary {

    public static final class AVXBinaryOp extends AMD64VectorLIRInstruction {
        public static final LIRInstructionClass<AVXBinaryOp> TYPE = LIRInstructionClass.create(AVXBinaryOp.class);

        @Opcode private final AMD64VectorAssembler.VexRVMOp opcode;
        private final AVXKind.AVXSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;

        public AVXBinaryOp(AMD64VectorAssembler.VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (isRegister(y)) {
                opcode.emit(vasm, size, asRegister(result), asRegister(x), asRegister(y));
            } else {
                opcode.emit(vasm, size, asRegister(result), asRegister(x), (AMD64Address) crb.asAddress(y));
            }
        }
    }

    public static final class AVXBinaryConstOp extends AMD64VectorLIRInstruction {

        public static final LIRInstructionClass<AVXBinaryConstOp> TYPE = LIRInstructionClass.create(AVXBinaryConstOp.class);

        @Opcode private final AMD64VectorAssembler.VexRRIOp opcode;
        private final AVXKind.AVXSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        protected int y;

        public AVXBinaryConstOp(AMD64VectorAssembler.VexRRIOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, int y) {
            super(TYPE);
            assert (y & 0xFF) == y;
            this.opcode = opcode;
            this.size = size;
            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            opcode.emit(vasm, size, asRegister(result), asRegister(x), y);
        }
    }

    public static final class AVXBinaryMemoryOp extends AMD64VectorLIRInstruction {
        public static final LIRInstructionClass<AVXBinaryMemoryOp> TYPE = LIRInstructionClass.create(AVXBinaryMemoryOp.class);

        @Opcode private final AMD64VectorAssembler.VexRVMOp opcode;
        private final AVXKind.AVXSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({COMPOSITE}) protected AMD64AddressValue y;
        @State protected LIRFrameState state;

        public AVXBinaryMemoryOp(AMD64VectorAssembler.VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, AMD64AddressValue y, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;
            this.result = result;
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64VectorAssembler vasm) {
            if (state != null) {
                crb.recordImplicitException(vasm.position(), state);
            }
            opcode.emit(vasm, size, asRegister(result), asRegister(x), y.toAddress());
        }
    }
}
