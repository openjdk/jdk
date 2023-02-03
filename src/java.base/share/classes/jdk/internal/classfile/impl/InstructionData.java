/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.classfile.impl;

import java.util.List;

import jdk.internal.classfile.Instruction;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.instruction.ArrayLoadInstruction;
import jdk.internal.classfile.instruction.ArrayStoreInstruction;
import jdk.internal.classfile.instruction.ConstantInstruction;
import jdk.internal.classfile.instruction.ConvertInstruction;
import jdk.internal.classfile.instruction.LoadInstruction;
import jdk.internal.classfile.instruction.MonitorInstruction;
import jdk.internal.classfile.instruction.NopInstruction;
import jdk.internal.classfile.instruction.OperatorInstruction;
import jdk.internal.classfile.instruction.ReturnInstruction;
import jdk.internal.classfile.instruction.StackInstruction;
import jdk.internal.classfile.instruction.StoreInstruction;
import jdk.internal.classfile.instruction.ThrowInstruction;

/**
 * InstructionData
 */
public class InstructionData {
    static final Instruction[] singletonInstructions = new Instruction[256];

    static {
        for (Opcode o : List.of(Opcode.NOP))
            singletonInstructions[o.bytecode()] = NopInstruction.of();
        for (Opcode o : List.of(Opcode.ACONST_NULL,
                                Opcode.ICONST_M1,
                                Opcode.ICONST_0, Opcode.ICONST_1, Opcode.ICONST_2, Opcode.ICONST_3, Opcode.ICONST_4, Opcode.ICONST_5,
                                Opcode.LCONST_0, Opcode.LCONST_1,
                                Opcode.FCONST_0, Opcode.FCONST_1, Opcode.FCONST_2,
                                Opcode.DCONST_0, Opcode.DCONST_1))
            singletonInstructions[o.bytecode()] = ConstantInstruction.ofIntrinsic(o);
        for (Opcode o : List.of(Opcode.ILOAD_0, Opcode.ILOAD_1, Opcode.ILOAD_2, Opcode.ILOAD_3,
                                Opcode.LLOAD_0, Opcode.LLOAD_1, Opcode.LLOAD_2, Opcode.LLOAD_3,
                                Opcode.FLOAD_0, Opcode.FLOAD_1, Opcode.FLOAD_2, Opcode.FLOAD_3,
                                Opcode.DLOAD_0, Opcode.DLOAD_1, Opcode.DLOAD_2, Opcode.DLOAD_3,
                                Opcode.ALOAD_0, Opcode.ALOAD_1, Opcode.ALOAD_2, Opcode.ALOAD_3))
            singletonInstructions[o.bytecode()] = LoadInstruction.of(o, o.slot());
        for (Opcode o : List.of(Opcode.ISTORE_0, Opcode.ISTORE_1, Opcode.ISTORE_2, Opcode.ISTORE_3,
                                Opcode.LSTORE_0, Opcode.LSTORE_1, Opcode.LSTORE_2, Opcode.LSTORE_3,
                                Opcode.FSTORE_0, Opcode.FSTORE_1, Opcode.FSTORE_2, Opcode.FSTORE_3,
                                Opcode.DSTORE_0, Opcode.DSTORE_1, Opcode.DSTORE_2, Opcode.DSTORE_3,
                                Opcode.ASTORE_0, Opcode.ASTORE_1, Opcode.ASTORE_2, Opcode.ASTORE_3))
            singletonInstructions[o.bytecode()] = StoreInstruction.of(o, o.slot());
        for (Opcode o : List.of(Opcode.IALOAD, Opcode.LALOAD, Opcode.FALOAD, Opcode.DALOAD, Opcode.AALOAD, Opcode.BALOAD, Opcode.CALOAD, Opcode.SALOAD))
            singletonInstructions[o.bytecode()] = ArrayLoadInstruction.of(o);
        for (Opcode o : List.of(Opcode.IASTORE, Opcode.LASTORE, Opcode.FASTORE, Opcode.DASTORE, Opcode.AASTORE, Opcode.BASTORE, Opcode.CASTORE, Opcode.SASTORE))
            singletonInstructions[o.bytecode()] = ArrayStoreInstruction.of(o);
        for (Opcode o : List.of(Opcode.POP, Opcode.POP2, Opcode.DUP, Opcode.DUP_X1, Opcode.DUP_X2, Opcode.DUP2, Opcode.DUP2_X1, Opcode.DUP2_X2, Opcode.SWAP))
            singletonInstructions[o.bytecode()] = StackInstruction.of(o);
        for (Opcode o : List.of(Opcode.IADD, Opcode.LADD, Opcode.FADD, Opcode.DADD, Opcode.ISUB,
                                Opcode.LSUB, Opcode.FSUB, Opcode.DSUB,
                                Opcode.IMUL, Opcode.LMUL, Opcode.FMUL, Opcode.DMUL,
                                Opcode.IDIV, Opcode.LDIV, Opcode.FDIV, Opcode.DDIV,
                                Opcode.IREM, Opcode.LREM, Opcode.FREM, Opcode.DREM,
                                Opcode.INEG, Opcode.LNEG, Opcode.FNEG, Opcode.DNEG,
                                Opcode.ISHL, Opcode.LSHL, Opcode.ISHR, Opcode.LSHR, Opcode.IUSHR, Opcode.LUSHR,
                                Opcode.IAND, Opcode.LAND, Opcode.IOR, Opcode.LOR, Opcode.IXOR, Opcode.LXOR,
                                Opcode.LCMP, Opcode.FCMPL, Opcode.FCMPG, Opcode.DCMPL, Opcode.DCMPG,
                                Opcode.ARRAYLENGTH))
            singletonInstructions[o.bytecode()] = OperatorInstruction.of(o);

        for (Opcode o : List.of(Opcode.I2L, Opcode.I2F, Opcode.I2D,
                                Opcode.L2I, Opcode.L2F, Opcode.L2D,
                                Opcode.F2I, Opcode.F2L, Opcode.F2D,
                                Opcode.D2I, Opcode.D2L, Opcode.D2F,
                                Opcode.I2B, Opcode.I2C, Opcode.I2S))
            singletonInstructions[o.bytecode()] = ConvertInstruction.of(o);
        for (Opcode o : List.of(Opcode.IRETURN, Opcode.LRETURN, Opcode.FRETURN, Opcode.DRETURN, Opcode.ARETURN, Opcode.RETURN))
            singletonInstructions[o.bytecode()] = ReturnInstruction.of(o);
        for (Opcode o : List.of(Opcode.ATHROW))
            singletonInstructions[o.bytecode()] = ThrowInstruction.of();
        for (Opcode o : List.of(Opcode.MONITORENTER, Opcode.MONITOREXIT))
            singletonInstructions[o.bytecode()] = MonitorInstruction.of(o);
    }

    private InstructionData() {
    }
}
