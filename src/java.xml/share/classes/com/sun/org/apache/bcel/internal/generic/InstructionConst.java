/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.generic;

import com.sun.org.apache.bcel.internal.Const;

/**
 * Contains shareable instruction objects.
 * <p>
 * In order to save memory you can use some instructions multiply, since they have an immutable state and are directly derived from Instruction. I.e. they have
 * no instance fields that could be changed. Since some of these instructions like ICONST_0 occur very frequently this can save a lot of time and space. This
 * feature is an adaptation of the FlyWeight design pattern, we just use an array instead of a factory.
 * </p>
 * <p>
 * The Instructions can also accessed directly under their names, so it's possible to write il.append(Instruction.ICONST_0);
 * </p>
 */
public final class InstructionConst {

    /**
     * Predefined instruction objects. NOTE these are not currently immutable, because Instruction has mutable protected fields opcode and length.
     */

    /** NOP instruction. */
    public static final Instruction NOP = new NOP();

    /** ACONST_NULL instruction. */
    public static final Instruction ACONST_NULL = new ACONST_NULL();

    /** ICONST_M1 instruction. */
    public static final Instruction ICONST_M1 = new ICONST(-1);

    /** ICONST_0 instruction. */
    public static final Instruction ICONST_0 = new ICONST(0);

    /** ICONST_1 instruction. */
    public static final Instruction ICONST_1 = new ICONST(1);

    /** ICONST_2 instruction. */
    public static final Instruction ICONST_2 = new ICONST(2);

    /** ICONST_3 instruction. */
    public static final Instruction ICONST_3 = new ICONST(3);

    /** ICONST_4 instruction. */
    public static final Instruction ICONST_4 = new ICONST(4);

    /** ICONST_5 instruction. */
    public static final Instruction ICONST_5 = new ICONST(5);

    /** LCONST_0 instruction. */
    public static final Instruction LCONST_0 = new LCONST(0);

    /** LCONST_1 instruction. */
    public static final Instruction LCONST_1 = new LCONST(1);

    /** FCONST_0 instruction. */
    public static final Instruction FCONST_0 = new FCONST(0);

    /** FCONST_1 instruction. */
    public static final Instruction FCONST_1 = new FCONST(1);

    /** FCONST_2 instruction. */
    public static final Instruction FCONST_2 = new FCONST(2);

    /** DCONST_0 instruction. */
    public static final Instruction DCONST_0 = new DCONST(0);

    /** DCONST_1 instruction. */
    public static final Instruction DCONST_1 = new DCONST(1);

    /** IALOAD instruction. */
    public static final ArrayInstruction IALOAD = new IALOAD();

    /** LALOAD instruction. */
    public static final ArrayInstruction LALOAD = new LALOAD();

    /** FALOAD instruction. */
    public static final ArrayInstruction FALOAD = new FALOAD();

    /** DALOAD instruction. */
    public static final ArrayInstruction DALOAD = new DALOAD();

    /** AALOAD instruction. */
    public static final ArrayInstruction AALOAD = new AALOAD();

    /** BALOAD instruction. */
    public static final ArrayInstruction BALOAD = new BALOAD();

    /** CALOAD instruction. */
    public static final ArrayInstruction CALOAD = new CALOAD();

    /** SALOAD instruction. */
    public static final ArrayInstruction SALOAD = new SALOAD();

    /** IASTORE instruction. */
    public static final ArrayInstruction IASTORE = new IASTORE();

    /** LASTORE instruction. */
    public static final ArrayInstruction LASTORE = new LASTORE();

    /** FASTORE instruction. */
    public static final ArrayInstruction FASTORE = new FASTORE();

    /** DASTORE instruction. */
    public static final ArrayInstruction DASTORE = new DASTORE();

    /** AASTORE instruction. */
    public static final ArrayInstruction AASTORE = new AASTORE();

    /** BASTORE instruction. */
    public static final ArrayInstruction BASTORE = new BASTORE();

    /** CASTORE instruction. */
    public static final ArrayInstruction CASTORE = new CASTORE();

    /** SASTORE instruction. */
    public static final ArrayInstruction SASTORE = new SASTORE();

    /** POP instruction. */
    public static final StackInstruction POP = new POP();

    /** POP2 instruction. */
    public static final StackInstruction POP2 = new POP2();

    /** DUP instruction. */
    public static final StackInstruction DUP = new DUP();

    /** DUP_X1 instruction. */
    public static final StackInstruction DUP_X1 = new DUP_X1();

    /** DUP_X2 instruction. */
    public static final StackInstruction DUP_X2 = new DUP_X2();

    /** DUP2 instruction. */
    public static final StackInstruction DUP2 = new DUP2();

    /** DUP2_X1 instruction. */
    public static final StackInstruction DUP2_X1 = new DUP2_X1();

    /** DUP2_X2 instruction. */
    public static final StackInstruction DUP2_X2 = new DUP2_X2();

    /** SWAP instruction. */
    public static final StackInstruction SWAP = new SWAP();

    /** IADD instruction. */
    public static final ArithmeticInstruction IADD = new IADD();

    /** LADD instruction. */
    public static final ArithmeticInstruction LADD = new LADD();

    /** FADD instruction. */
    public static final ArithmeticInstruction FADD = new FADD();

    /** DADD instruction. */
    public static final ArithmeticInstruction DADD = new DADD();

    /** ISUB instruction. */
    public static final ArithmeticInstruction ISUB = new ISUB();

    /** LSUB instruction. */
    public static final ArithmeticInstruction LSUB = new LSUB();

    /** FSUB instruction. */
    public static final ArithmeticInstruction FSUB = new FSUB();

    /** DSUB instruction. */
    public static final ArithmeticInstruction DSUB = new DSUB();

    /** IMUL instruction. */
    public static final ArithmeticInstruction IMUL = new IMUL();

    /** LMUL instruction. */
    public static final ArithmeticInstruction LMUL = new LMUL();

    /** FMUL instruction. */
    public static final ArithmeticInstruction FMUL = new FMUL();

    /** DMUL instruction. */
    public static final ArithmeticInstruction DMUL = new DMUL();

    /** IDIV instruction. */
    public static final ArithmeticInstruction IDIV = new IDIV();

    /** LDIV instruction. */
    public static final ArithmeticInstruction LDIV = new LDIV();

    /** FDIV instruction. */
    public static final ArithmeticInstruction FDIV = new FDIV();

    /** DDIV instruction. */
    public static final ArithmeticInstruction DDIV = new DDIV();

    /** IREM instruction. */
    public static final ArithmeticInstruction IREM = new IREM();

    /** LREM instruction. */
    public static final ArithmeticInstruction LREM = new LREM();

    /** FREM instruction. */
    public static final ArithmeticInstruction FREM = new FREM();

    /** DREM instruction. */
    public static final ArithmeticInstruction DREM = new DREM();

    /** INEG instruction. */
    public static final ArithmeticInstruction INEG = new INEG();

    /** LNEG instruction. */
    public static final ArithmeticInstruction LNEG = new LNEG();

    /** FNEG instruction. */
    public static final ArithmeticInstruction FNEG = new FNEG();

    /** DNEG instruction. */
    public static final ArithmeticInstruction DNEG = new DNEG();

    /** ISHL instruction. */
    public static final ArithmeticInstruction ISHL = new ISHL();

    /** LSHL instruction. */
    public static final ArithmeticInstruction LSHL = new LSHL();

    /** ISHR instruction. */
    public static final ArithmeticInstruction ISHR = new ISHR();

    /** LSHR instruction. */
    public static final ArithmeticInstruction LSHR = new LSHR();

    /** IUSHR instruction. */
    public static final ArithmeticInstruction IUSHR = new IUSHR();

    /** LUSHR instruction. */
    public static final ArithmeticInstruction LUSHR = new LUSHR();

    /** IAND instruction. */
    public static final ArithmeticInstruction IAND = new IAND();

    /** LAND instruction. */
    public static final ArithmeticInstruction LAND = new LAND();

    /** IOR instruction. */
    public static final ArithmeticInstruction IOR = new IOR();

    /** LOR instruction. */
    public static final ArithmeticInstruction LOR = new LOR();

    /** IXOR instruction. */
    public static final ArithmeticInstruction IXOR = new IXOR();

    /** LXOR instruction. */
    public static final ArithmeticInstruction LXOR = new LXOR();

    /** I2L instruction. */
    public static final ConversionInstruction I2L = new I2L();

    /** I2F instruction. */
    public static final ConversionInstruction I2F = new I2F();

    /** I2D instruction. */
    public static final ConversionInstruction I2D = new I2D();

    /** L2I instruction. */
    public static final ConversionInstruction L2I = new L2I();

    /** L2F instruction. */
    public static final ConversionInstruction L2F = new L2F();

    /** L2D instruction. */
    public static final ConversionInstruction L2D = new L2D();

    /** F2I instruction. */
    public static final ConversionInstruction F2I = new F2I();

    /** F2L instruction. */
    public static final ConversionInstruction F2L = new F2L();

    /** F2D instruction. */
    public static final ConversionInstruction F2D = new F2D();

    /** D2I instruction. */
    public static final ConversionInstruction D2I = new D2I();

    /** D2L instruction. */
    public static final ConversionInstruction D2L = new D2L();

    /** D2F instruction. */
    public static final ConversionInstruction D2F = new D2F();

    /** I2B instruction. */
    public static final ConversionInstruction I2B = new I2B();

    /** I2C instruction. */
    public static final ConversionInstruction I2C = new I2C();

    /** I2S instruction. */
    public static final ConversionInstruction I2S = new I2S();

    /** LCMP instruction. */
    public static final Instruction LCMP = new LCMP();

    /** FCMPL instruction. */
    public static final Instruction FCMPL = new FCMPL();

    /** FCMPG instruction. */
    public static final Instruction FCMPG = new FCMPG();

    /** DCMPL instruction. */
    public static final Instruction DCMPL = new DCMPL();

    /** DCMPG instruction. */
    public static final Instruction DCMPG = new DCMPG();

    /** IRETURN instruction. */
    public static final ReturnInstruction IRETURN = new IRETURN();

    /** LRETURN instruction. */
    public static final ReturnInstruction LRETURN = new LRETURN();

    /** FRETURN instruction. */
    public static final ReturnInstruction FRETURN = new FRETURN();

    /** DRETURN instruction. */
    public static final ReturnInstruction DRETURN = new DRETURN();

    /** ARETURN instruction. */
    public static final ReturnInstruction ARETURN = new ARETURN();

    /** RETURN instruction. */
    public static final ReturnInstruction RETURN = new RETURN();

    /** ARRAYLENGTH instruction. */
    public static final Instruction ARRAYLENGTH = new ARRAYLENGTH();

    /** ATHROW instruction. */
    public static final Instruction ATHROW = new ATHROW();

    /** MONITORENTER instruction. */
    public static final Instruction MONITORENTER = new MONITORENTER();

    /** MONITOREXIT instruction. */
    public static final Instruction MONITOREXIT = new MONITOREXIT();

    /**
     * You can use these constants in multiple places safely, if you can guarantee that you will never alter their internal values, for example call setIndex().
     * THIS instruction (ALOAD_0)
     */
    public static final LocalVariableInstruction THIS = new ALOAD(0);

    /** ALOAD_0 instruction (same as THIS). */
    public static final LocalVariableInstruction ALOAD_0 = THIS;

    /** ALOAD_1 instruction. */
    public static final LocalVariableInstruction ALOAD_1 = new ALOAD(1);

    /** ALOAD_2 instruction. */
    public static final LocalVariableInstruction ALOAD_2 = new ALOAD(2);

    /** ILOAD_0 instruction. */
    public static final LocalVariableInstruction ILOAD_0 = new ILOAD(0);

    /** ILOAD_1 instruction. */
    public static final LocalVariableInstruction ILOAD_1 = new ILOAD(1);

    /** ILOAD_2 instruction. */
    public static final LocalVariableInstruction ILOAD_2 = new ILOAD(2);

    /** ASTORE_0 instruction. */
    public static final LocalVariableInstruction ASTORE_0 = new ASTORE(0);

    /** ASTORE_1 instruction. */
    public static final LocalVariableInstruction ASTORE_1 = new ASTORE(1);

    /** ASTORE_2 instruction. */
    public static final LocalVariableInstruction ASTORE_2 = new ASTORE(2);

    /** ISTORE_0 instruction. */
    public static final LocalVariableInstruction ISTORE_0 = new ISTORE(0);

    /** ISTORE_1 instruction. */
    public static final LocalVariableInstruction ISTORE_1 = new ISTORE(1);

    /** ISTORE_2 instruction. */
    public static final LocalVariableInstruction ISTORE_2 = new ISTORE(2);

    /**
     * Gets object via its opcode, for immutable instructions like branch instructions entries are set to null.
     */
    static final Instruction[] INSTRUCTIONS = new Instruction[256];

    static {
        INSTRUCTIONS[Const.NOP] = NOP;
        INSTRUCTIONS[Const.ACONST_NULL] = ACONST_NULL;
        INSTRUCTIONS[Const.ICONST_M1] = ICONST_M1;
        INSTRUCTIONS[Const.ICONST_0] = ICONST_0;
        INSTRUCTIONS[Const.ICONST_1] = ICONST_1;
        INSTRUCTIONS[Const.ICONST_2] = ICONST_2;
        INSTRUCTIONS[Const.ICONST_3] = ICONST_3;
        INSTRUCTIONS[Const.ICONST_4] = ICONST_4;
        INSTRUCTIONS[Const.ICONST_5] = ICONST_5;
        INSTRUCTIONS[Const.LCONST_0] = LCONST_0;
        INSTRUCTIONS[Const.LCONST_1] = LCONST_1;
        INSTRUCTIONS[Const.FCONST_0] = FCONST_0;
        INSTRUCTIONS[Const.FCONST_1] = FCONST_1;
        INSTRUCTIONS[Const.FCONST_2] = FCONST_2;
        INSTRUCTIONS[Const.DCONST_0] = DCONST_0;
        INSTRUCTIONS[Const.DCONST_1] = DCONST_1;
        INSTRUCTIONS[Const.IALOAD] = IALOAD;
        INSTRUCTIONS[Const.LALOAD] = LALOAD;
        INSTRUCTIONS[Const.FALOAD] = FALOAD;
        INSTRUCTIONS[Const.DALOAD] = DALOAD;
        INSTRUCTIONS[Const.AALOAD] = AALOAD;
        INSTRUCTIONS[Const.BALOAD] = BALOAD;
        INSTRUCTIONS[Const.CALOAD] = CALOAD;
        INSTRUCTIONS[Const.SALOAD] = SALOAD;
        INSTRUCTIONS[Const.IASTORE] = IASTORE;
        INSTRUCTIONS[Const.LASTORE] = LASTORE;
        INSTRUCTIONS[Const.FASTORE] = FASTORE;
        INSTRUCTIONS[Const.DASTORE] = DASTORE;
        INSTRUCTIONS[Const.AASTORE] = AASTORE;
        INSTRUCTIONS[Const.BASTORE] = BASTORE;
        INSTRUCTIONS[Const.CASTORE] = CASTORE;
        INSTRUCTIONS[Const.SASTORE] = SASTORE;
        INSTRUCTIONS[Const.POP] = POP;
        INSTRUCTIONS[Const.POP2] = POP2;
        INSTRUCTIONS[Const.DUP] = DUP;
        INSTRUCTIONS[Const.DUP_X1] = DUP_X1;
        INSTRUCTIONS[Const.DUP_X2] = DUP_X2;
        INSTRUCTIONS[Const.DUP2] = DUP2;
        INSTRUCTIONS[Const.DUP2_X1] = DUP2_X1;
        INSTRUCTIONS[Const.DUP2_X2] = DUP2_X2;
        INSTRUCTIONS[Const.SWAP] = SWAP;
        INSTRUCTIONS[Const.IADD] = IADD;
        INSTRUCTIONS[Const.LADD] = LADD;
        INSTRUCTIONS[Const.FADD] = FADD;
        INSTRUCTIONS[Const.DADD] = DADD;
        INSTRUCTIONS[Const.ISUB] = ISUB;
        INSTRUCTIONS[Const.LSUB] = LSUB;
        INSTRUCTIONS[Const.FSUB] = FSUB;
        INSTRUCTIONS[Const.DSUB] = DSUB;
        INSTRUCTIONS[Const.IMUL] = IMUL;
        INSTRUCTIONS[Const.LMUL] = LMUL;
        INSTRUCTIONS[Const.FMUL] = FMUL;
        INSTRUCTIONS[Const.DMUL] = DMUL;
        INSTRUCTIONS[Const.IDIV] = IDIV;
        INSTRUCTIONS[Const.LDIV] = LDIV;
        INSTRUCTIONS[Const.FDIV] = FDIV;
        INSTRUCTIONS[Const.DDIV] = DDIV;
        INSTRUCTIONS[Const.IREM] = IREM;
        INSTRUCTIONS[Const.LREM] = LREM;
        INSTRUCTIONS[Const.FREM] = FREM;
        INSTRUCTIONS[Const.DREM] = DREM;
        INSTRUCTIONS[Const.INEG] = INEG;
        INSTRUCTIONS[Const.LNEG] = LNEG;
        INSTRUCTIONS[Const.FNEG] = FNEG;
        INSTRUCTIONS[Const.DNEG] = DNEG;
        INSTRUCTIONS[Const.ISHL] = ISHL;
        INSTRUCTIONS[Const.LSHL] = LSHL;
        INSTRUCTIONS[Const.ISHR] = ISHR;
        INSTRUCTIONS[Const.LSHR] = LSHR;
        INSTRUCTIONS[Const.IUSHR] = IUSHR;
        INSTRUCTIONS[Const.LUSHR] = LUSHR;
        INSTRUCTIONS[Const.IAND] = IAND;
        INSTRUCTIONS[Const.LAND] = LAND;
        INSTRUCTIONS[Const.IOR] = IOR;
        INSTRUCTIONS[Const.LOR] = LOR;
        INSTRUCTIONS[Const.IXOR] = IXOR;
        INSTRUCTIONS[Const.LXOR] = LXOR;
        INSTRUCTIONS[Const.I2L] = I2L;
        INSTRUCTIONS[Const.I2F] = I2F;
        INSTRUCTIONS[Const.I2D] = I2D;
        INSTRUCTIONS[Const.L2I] = L2I;
        INSTRUCTIONS[Const.L2F] = L2F;
        INSTRUCTIONS[Const.L2D] = L2D;
        INSTRUCTIONS[Const.F2I] = F2I;
        INSTRUCTIONS[Const.F2L] = F2L;
        INSTRUCTIONS[Const.F2D] = F2D;
        INSTRUCTIONS[Const.D2I] = D2I;
        INSTRUCTIONS[Const.D2L] = D2L;
        INSTRUCTIONS[Const.D2F] = D2F;
        INSTRUCTIONS[Const.I2B] = I2B;
        INSTRUCTIONS[Const.I2C] = I2C;
        INSTRUCTIONS[Const.I2S] = I2S;
        INSTRUCTIONS[Const.LCMP] = LCMP;
        INSTRUCTIONS[Const.FCMPL] = FCMPL;
        INSTRUCTIONS[Const.FCMPG] = FCMPG;
        INSTRUCTIONS[Const.DCMPL] = DCMPL;
        INSTRUCTIONS[Const.DCMPG] = DCMPG;
        INSTRUCTIONS[Const.IRETURN] = IRETURN;
        INSTRUCTIONS[Const.LRETURN] = LRETURN;
        INSTRUCTIONS[Const.FRETURN] = FRETURN;
        INSTRUCTIONS[Const.DRETURN] = DRETURN;
        INSTRUCTIONS[Const.ARETURN] = ARETURN;
        INSTRUCTIONS[Const.RETURN] = RETURN;
        INSTRUCTIONS[Const.ARRAYLENGTH] = ARRAYLENGTH;
        INSTRUCTIONS[Const.ATHROW] = ATHROW;
        INSTRUCTIONS[Const.MONITORENTER] = MONITORENTER;
        INSTRUCTIONS[Const.MONITOREXIT] = MONITOREXIT;
    }

    /**
     * Gets the Instruction.
     *
     * @param index The index, for example {@link Const#RETURN}.
     * @return The entry from the private INSTRUCTIONS table.
     */
    public static Instruction getInstruction(final int index) {
        return INSTRUCTIONS[index];
    }

    private InstructionConst() {
    } // non-instantiable
}
