/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSimm13;
import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.sparc.SPARC.g0;

import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public final class SPARCOP3Op extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
    public static final LIRInstructionClass<SPARCOP3Op> TYPE = LIRInstructionClass.create(SPARCOP3Op.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(1);

    @Opcode private final Op3s op3;
    @Use({REG}) protected AllocatableValue rs1;
    @Use({REG, CONST}) protected Value rs2;
    @Def({REG}) protected AllocatableValue rd;
    @State protected LIRFrameState state;

    public static SPARCOP3Op newUnary(Op3s op3, Value rs2, AllocatableValue rd) {
        return newUnary(op3, rs2, rd, null);
    }

    public static SPARCOP3Op newUnary(Op3s op3, Value rs2, AllocatableValue rd, LIRFrameState state) {
        return new SPARCOP3Op(op3, g0.asValue(LIRKind.value(rs2.getPlatformKind())), rs2, rd, state);
    }

    public static SPARCOP3Op newBinaryVoid(Op3s op3, AllocatableValue rs1, Value rs2) {
        return newBinaryVoid(op3, rs1, rs2, null);
    }

    public static SPARCOP3Op newBinaryVoid(Op3s op3, AllocatableValue rs1, Value rs2, LIRFrameState state) {
        return new SPARCOP3Op(op3, rs1, rs2, g0.asValue(LIRKind.value(rs2.getPlatformKind())), state);
    }

    public SPARCOP3Op(Op3s op3, AllocatableValue rs1, Value rs2, AllocatableValue rd) {
        this(op3, rs1, rs2, rd, null);
    }

    public SPARCOP3Op(Op3s op3, AllocatableValue rs1, Value rs2, AllocatableValue rd, LIRFrameState state) {
        super(TYPE, SIZE);
        this.op3 = op3;
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.rd = rd;
        this.state = state;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        getDelayedControlTransfer().emitControlTransfer(crb, masm);
        if (state != null) {
            crb.recordImplicitException(masm.position(), state);
        }
        emitOp3(masm, op3, rs1, rs2, rd);
    }

    public static void emitOp3(SPARCMacroAssembler masm, Op3s op3, Value rs1, Value rs2) {
        emitOp3(masm, op3, rs1, rs2, g0.asValue(LIRKind.value(rs2.getPlatformKind())));
    }

    public static void emitOp3(SPARCMacroAssembler masm, Op3s op3, Value rs1, Value rs2, Value rd) {
        assert isRegister(rs1) : rs1;
        if (isJavaConstant(rs2)) {
            JavaConstant constant = asJavaConstant(rs2);
            long simm13;
            if (constant.isNull()) {
                simm13 = 0;
            } else {
                // Cast is safe, as isSimm13 assertion is done
                simm13 = constant.asLong();
            }
            assert isSimm13(constant);
            SPARCAssembler.Op3Op.emit(masm, op3, asRegister(rs1), (int) simm13, asRegister(rd));
        } else if (isRegister(rs2)) {
            SPARCAssembler.Op3Op.emit(masm, op3, asRegister(rs1), asRegister(rs2), asRegister(rd));
        } else {
            throw shouldNotReachHere(String.format("Got values a: %s b: %s", rs1, rs2));
        }
    }
}
