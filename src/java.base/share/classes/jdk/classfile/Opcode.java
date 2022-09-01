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
package jdk.classfile;

import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;

/**
 * Describes the opcodes of the JVM instruction set, as well as a number of
 * pseudo-instructions that may be encountered when traversing the instructions
 * of a method.
 *
 * @see Instruction
 * @see PseudoInstruction
 */
public enum Opcode {
    NOP(Classfile.NOP, 1, CodeElement.Kind.NOP),
    ACONST_NULL(Classfile.ACONST_NULL, 1, CodeElement.Kind.CONSTANT, TypeKind.ReferenceType, 0, ConstantDescs.NULL),
    ICONST_M1(Classfile.ICONST_M1, 1, CodeElement.Kind.CONSTANT, TypeKind.IntType, 0, -1),
    ICONST_0(Classfile.ICONST_0, 1, CodeElement.Kind.CONSTANT, TypeKind.IntType, 0, 0),
    ICONST_1(Classfile.ICONST_1, 1, CodeElement.Kind.CONSTANT, TypeKind.IntType, 0, 1),
    ICONST_2(Classfile.ICONST_2, 1, CodeElement.Kind.CONSTANT, TypeKind.IntType, 0, 2),
    ICONST_3(Classfile.ICONST_3, 1, CodeElement.Kind.CONSTANT, TypeKind.IntType, 0, 3),
    ICONST_4(Classfile.ICONST_4, 1, CodeElement.Kind.CONSTANT, TypeKind.IntType, 0, 4),
    ICONST_5(Classfile.ICONST_5, 1, CodeElement.Kind.CONSTANT, TypeKind.IntType, 0, 5),
    LCONST_0(Classfile.LCONST_0, 1, CodeElement.Kind.CONSTANT, TypeKind.LongType, 0, 0L),
    LCONST_1(Classfile.LCONST_1, 1, CodeElement.Kind.CONSTANT, TypeKind.LongType, 0, 1L),
    FCONST_0(Classfile.FCONST_0, 1, CodeElement.Kind.CONSTANT, TypeKind.FloatType, 0, 0.0f),
    FCONST_1(Classfile.FCONST_1, 1, CodeElement.Kind.CONSTANT, TypeKind.FloatType, 0, 1.0f),
    FCONST_2(Classfile.FCONST_2, 1, CodeElement.Kind.CONSTANT, TypeKind.FloatType, 0, 2.0f),
    DCONST_0(Classfile.DCONST_0, 1, CodeElement.Kind.CONSTANT, TypeKind.DoubleType, 0, 0.0d),
    DCONST_1(Classfile.DCONST_1, 1, CodeElement.Kind.CONSTANT, TypeKind.DoubleType, 0, 1.0d),
    BIPUSH(Classfile.BIPUSH, 2, CodeElement.Kind.CONSTANT, TypeKind.ByteType),
    SIPUSH(Classfile.SIPUSH, 3, CodeElement.Kind.CONSTANT, TypeKind.ShortType),
    LDC(Classfile.LDC, 2, CodeElement.Kind.CONSTANT),
    LDC_W(Classfile.LDC_W, 3, CodeElement.Kind.CONSTANT),
    LDC2_W(Classfile.LDC2_W, 3, CodeElement.Kind.CONSTANT),
    ILOAD(Classfile.ILOAD, 2, CodeElement.Kind.LOAD, TypeKind.IntType, -1),
    LLOAD(Classfile.LLOAD, 2, CodeElement.Kind.LOAD, TypeKind.LongType, -1),
    FLOAD(Classfile.FLOAD, 2, CodeElement.Kind.LOAD, TypeKind.FloatType, -1),
    DLOAD(Classfile.DLOAD, 2, CodeElement.Kind.LOAD, TypeKind.DoubleType, -1),
    ALOAD(Classfile.ALOAD, 2, CodeElement.Kind.LOAD, TypeKind.ReferenceType, -1),
    ILOAD_0(Classfile.ILOAD_0, 1, CodeElement.Kind.LOAD, TypeKind.IntType, 0),
    ILOAD_1(Classfile.ILOAD_1, 1, CodeElement.Kind.LOAD, TypeKind.IntType, 1),
    ILOAD_2(Classfile.ILOAD_2, 1, CodeElement.Kind.LOAD, TypeKind.IntType, 2),
    ILOAD_3(Classfile.ILOAD_3, 1, CodeElement.Kind.LOAD, TypeKind.IntType, 3),
    LLOAD_0(Classfile.LLOAD_0, 1, CodeElement.Kind.LOAD, TypeKind.LongType, 0),
    LLOAD_1(Classfile.LLOAD_1, 1, CodeElement.Kind.LOAD, TypeKind.LongType, 1),
    LLOAD_2(Classfile.LLOAD_2, 1, CodeElement.Kind.LOAD, TypeKind.LongType, 2),
    LLOAD_3(Classfile.LLOAD_3, 1, CodeElement.Kind.LOAD, TypeKind.LongType, 3),
    FLOAD_0(Classfile.FLOAD_0, 1, CodeElement.Kind.LOAD, TypeKind.FloatType, 0),
    FLOAD_1(Classfile.FLOAD_1, 1, CodeElement.Kind.LOAD, TypeKind.FloatType, 1),
    FLOAD_2(Classfile.FLOAD_2, 1, CodeElement.Kind.LOAD, TypeKind.FloatType, 2),
    FLOAD_3(Classfile.FLOAD_3, 1, CodeElement.Kind.LOAD, TypeKind.FloatType, 3),
    DLOAD_0(Classfile.DLOAD_0, 1, CodeElement.Kind.LOAD, TypeKind.DoubleType, 0),
    DLOAD_1(Classfile.DLOAD_1, 1, CodeElement.Kind.LOAD, TypeKind.DoubleType, 1),
    DLOAD_2(Classfile.DLOAD_2, 1, CodeElement.Kind.LOAD, TypeKind.DoubleType, 2),
    DLOAD_3(Classfile.DLOAD_3, 1, CodeElement.Kind.LOAD, TypeKind.DoubleType, 3),
    ALOAD_0(Classfile.ALOAD_0, 1, CodeElement.Kind.LOAD, TypeKind.ReferenceType, 0),
    ALOAD_1(Classfile.ALOAD_1, 1, CodeElement.Kind.LOAD, TypeKind.ReferenceType, 1),
    ALOAD_2(Classfile.ALOAD_2, 1, CodeElement.Kind.LOAD, TypeKind.ReferenceType, 2),
    ALOAD_3(Classfile.ALOAD_3, 1, CodeElement.Kind.LOAD, TypeKind.ReferenceType, 3),
    IALOAD(Classfile.IALOAD, 1, CodeElement.Kind.ARRAY_LOAD, TypeKind.IntType),
    LALOAD(Classfile.LALOAD, 1, CodeElement.Kind.ARRAY_LOAD, TypeKind.LongType),
    FALOAD(Classfile.FALOAD, 1, CodeElement.Kind.ARRAY_LOAD, TypeKind.FloatType),
    DALOAD(Classfile.DALOAD, 1, CodeElement.Kind.ARRAY_LOAD, TypeKind.DoubleType),
    AALOAD(Classfile.AALOAD, 1, CodeElement.Kind.ARRAY_LOAD, TypeKind.ReferenceType),
    BALOAD(Classfile.BALOAD, 1, CodeElement.Kind.ARRAY_LOAD, TypeKind.ByteType),
    CALOAD(Classfile.CALOAD, 1, CodeElement.Kind.ARRAY_LOAD, TypeKind.CharType),
    SALOAD(Classfile.SALOAD, 1, CodeElement.Kind.ARRAY_LOAD, TypeKind.ShortType),
    ISTORE(Classfile.ISTORE, 2, CodeElement.Kind.STORE, TypeKind.IntType, -1),
    LSTORE(Classfile.LSTORE, 2, CodeElement.Kind.STORE, TypeKind.LongType, -1),
    FSTORE(Classfile.FSTORE, 2, CodeElement.Kind.STORE, TypeKind.FloatType, -1),
    DSTORE(Classfile.DSTORE, 2, CodeElement.Kind.STORE, TypeKind.DoubleType, -1),
    ASTORE(Classfile.ASTORE, 2, CodeElement.Kind.STORE, TypeKind.ReferenceType, -1),
    ISTORE_0(Classfile.ISTORE_0, 1, CodeElement.Kind.STORE, TypeKind.IntType, 0),
    ISTORE_1(Classfile.ISTORE_1, 1, CodeElement.Kind.STORE, TypeKind.IntType, 1),
    ISTORE_2(Classfile.ISTORE_2, 1, CodeElement.Kind.STORE, TypeKind.IntType, 2),
    ISTORE_3(Classfile.ISTORE_3, 1, CodeElement.Kind.STORE, TypeKind.IntType, 3),
    LSTORE_0(Classfile.LSTORE_0, 1, CodeElement.Kind.STORE, TypeKind.LongType, 0),
    LSTORE_1(Classfile.LSTORE_1, 1, CodeElement.Kind.STORE, TypeKind.LongType, 1),
    LSTORE_2(Classfile.LSTORE_2, 1, CodeElement.Kind.STORE, TypeKind.LongType, 2),
    LSTORE_3(Classfile.LSTORE_3, 1, CodeElement.Kind.STORE, TypeKind.LongType, 3),
    FSTORE_0(Classfile.FSTORE_0, 1, CodeElement.Kind.STORE, TypeKind.FloatType, 0),
    FSTORE_1(Classfile.FSTORE_1, 1, CodeElement.Kind.STORE, TypeKind.FloatType, 1),
    FSTORE_2(Classfile.FSTORE_2, 1, CodeElement.Kind.STORE, TypeKind.FloatType, 2),
    FSTORE_3(Classfile.FSTORE_3, 1, CodeElement.Kind.STORE, TypeKind.FloatType, 3),
    DSTORE_0(Classfile.DSTORE_0, 1, CodeElement.Kind.STORE, TypeKind.DoubleType, 0),
    DSTORE_1(Classfile.DSTORE_1, 1, CodeElement.Kind.STORE, TypeKind.DoubleType, 1),
    DSTORE_2(Classfile.DSTORE_2, 1, CodeElement.Kind.STORE, TypeKind.DoubleType, 2),
    DSTORE_3(Classfile.DSTORE_3, 1, CodeElement.Kind.STORE, TypeKind.DoubleType, 3),
    ASTORE_0(Classfile.ASTORE_0, 1, CodeElement.Kind.STORE, TypeKind.ReferenceType, 0),
    ASTORE_1(Classfile.ASTORE_1, 1, CodeElement.Kind.STORE, TypeKind.ReferenceType, 1),
    ASTORE_2(Classfile.ASTORE_2, 1, CodeElement.Kind.STORE, TypeKind.ReferenceType, 2),
    ASTORE_3(Classfile.ASTORE_3, 1, CodeElement.Kind.STORE, TypeKind.ReferenceType, 3),
    IASTORE(Classfile.IASTORE, 1, CodeElement.Kind.ARRAY_STORE, TypeKind.IntType),
    LASTORE(Classfile.LASTORE, 1, CodeElement.Kind.ARRAY_STORE, TypeKind.LongType),
    FASTORE(Classfile.FASTORE, 1, CodeElement.Kind.ARRAY_STORE, TypeKind.FloatType),
    DASTORE(Classfile.DASTORE, 1, CodeElement.Kind.ARRAY_STORE, TypeKind.DoubleType),
    AASTORE(Classfile.AASTORE, 1, CodeElement.Kind.ARRAY_STORE, TypeKind.ReferenceType),
    BASTORE(Classfile.BASTORE, 1, CodeElement.Kind.ARRAY_STORE, TypeKind.ByteType),
    CASTORE(Classfile.CASTORE, 1, CodeElement.Kind.ARRAY_STORE, TypeKind.CharType),
    SASTORE(Classfile.SASTORE, 1, CodeElement.Kind.ARRAY_STORE, TypeKind.ShortType),
    POP(Classfile.POP, 1, CodeElement.Kind.STACK),
    POP2(Classfile.POP2, 1, CodeElement.Kind.STACK),
    DUP(Classfile.DUP, 1, CodeElement.Kind.STACK),
    DUP_X1(Classfile.DUP_X1, 1, CodeElement.Kind.STACK),
    DUP_X2(Classfile.DUP_X2, 1, CodeElement.Kind.STACK),
    DUP2(Classfile.DUP2, 1, CodeElement.Kind.STACK),
    DUP2_X1(Classfile.DUP2_X1, 1, CodeElement.Kind.STACK),
    DUP2_X2(Classfile.DUP2_X2, 1, CodeElement.Kind.STACK),
    SWAP(Classfile.SWAP, 1, CodeElement.Kind.STACK),
    IADD(Classfile.IADD, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LADD(Classfile.LADD, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    FADD(Classfile.FADD, 1, CodeElement.Kind.OPERATOR, TypeKind.FloatType),
    DADD(Classfile.DADD, 1, CodeElement.Kind.OPERATOR, TypeKind.DoubleType),
    ISUB(Classfile.ISUB, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LSUB(Classfile.LSUB, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    FSUB(Classfile.FSUB, 1, CodeElement.Kind.OPERATOR, TypeKind.FloatType),
    DSUB(Classfile.DSUB, 1, CodeElement.Kind.OPERATOR, TypeKind.DoubleType),
    IMUL(Classfile.IMUL, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LMUL(Classfile.LMUL, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    FMUL(Classfile.FMUL, 1, CodeElement.Kind.OPERATOR, TypeKind.FloatType),
    DMUL(Classfile.DMUL, 1, CodeElement.Kind.OPERATOR, TypeKind.DoubleType),
    IDIV(Classfile.IDIV, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LDIV(Classfile.LDIV, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    FDIV(Classfile.FDIV, 1, CodeElement.Kind.OPERATOR, TypeKind.FloatType),
    DDIV(Classfile.DDIV, 1, CodeElement.Kind.OPERATOR, TypeKind.DoubleType),
    IREM(Classfile.IREM, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LREM(Classfile.LREM, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    FREM(Classfile.FREM, 1, CodeElement.Kind.OPERATOR, TypeKind.FloatType),
    DREM(Classfile.DREM, 1, CodeElement.Kind.OPERATOR, TypeKind.DoubleType),
    INEG(Classfile.INEG, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LNEG(Classfile.LNEG, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    FNEG(Classfile.FNEG, 1, CodeElement.Kind.OPERATOR, TypeKind.FloatType),
    DNEG(Classfile.DNEG, 1, CodeElement.Kind.OPERATOR, TypeKind.DoubleType),
    ISHL(Classfile.ISHL, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LSHL(Classfile.LSHL, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    ISHR(Classfile.ISHR, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LSHR(Classfile.LSHR, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    IUSHR(Classfile.IUSHR, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LUSHR(Classfile.LUSHR, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    IAND(Classfile.IAND, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LAND(Classfile.LAND, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    IOR(Classfile.IOR, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LOR(Classfile.LOR, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    IXOR(Classfile.IXOR, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    LXOR(Classfile.LXOR, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    IINC(Classfile.IINC, 3, CodeElement.Kind.INCREMENT, TypeKind.IntType, -1),
    I2L(Classfile.I2L, 1, CodeElement.Kind.CONVERT, TypeKind.IntType, TypeKind.LongType),
    I2F(Classfile.I2F, 1, CodeElement.Kind.CONVERT, TypeKind.IntType, TypeKind.FloatType),
    I2D(Classfile.I2D, 1, CodeElement.Kind.CONVERT, TypeKind.IntType, TypeKind.DoubleType),
    L2I(Classfile.L2I, 1, CodeElement.Kind.CONVERT, TypeKind.LongType, TypeKind.IntType),
    L2F(Classfile.L2F, 1, CodeElement.Kind.CONVERT, TypeKind.LongType, TypeKind.FloatType),
    L2D(Classfile.L2D, 1, CodeElement.Kind.CONVERT, TypeKind.LongType, TypeKind.DoubleType),
    F2I(Classfile.F2I, 1, CodeElement.Kind.CONVERT, TypeKind.FloatType, TypeKind.IntType),
    F2L(Classfile.F2L, 1, CodeElement.Kind.CONVERT, TypeKind.FloatType, TypeKind.LongType),
    F2D(Classfile.F2D, 1, CodeElement.Kind.CONVERT, TypeKind.FloatType, TypeKind.DoubleType),
    D2I(Classfile.D2I, 1, CodeElement.Kind.CONVERT, TypeKind.DoubleType, TypeKind.IntType),
    D2L(Classfile.D2L, 1, CodeElement.Kind.CONVERT, TypeKind.DoubleType, TypeKind.LongType),
    D2F(Classfile.D2F, 1, CodeElement.Kind.CONVERT, TypeKind.DoubleType, TypeKind.FloatType),
    I2B(Classfile.I2B, 1, CodeElement.Kind.CONVERT, TypeKind.IntType, TypeKind.ByteType),
    I2C(Classfile.I2C, 1, CodeElement.Kind.CONVERT, TypeKind.IntType, TypeKind.CharType),
    I2S(Classfile.I2S, 1, CodeElement.Kind.CONVERT, TypeKind.IntType, TypeKind.ShortType),
    LCMP(Classfile.LCMP, 1, CodeElement.Kind.OPERATOR, TypeKind.LongType),
    FCMPL(Classfile.FCMPL, 1, CodeElement.Kind.OPERATOR, TypeKind.FloatType),
    FCMPG(Classfile.FCMPG, 1, CodeElement.Kind.OPERATOR, TypeKind.FloatType),
    DCMPL(Classfile.DCMPL, 1, CodeElement.Kind.OPERATOR, TypeKind.DoubleType),
    DCMPG(Classfile.DCMPG, 1, CodeElement.Kind.OPERATOR, TypeKind.DoubleType),
    IFEQ(Classfile.IFEQ, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IFNE(Classfile.IFNE, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IFLT(Classfile.IFLT, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IFGE(Classfile.IFGE, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IFGT(Classfile.IFGT, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IFLE(Classfile.IFLE, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IF_ICMPEQ(Classfile.IF_ICMPEQ, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IF_ICMPNE(Classfile.IF_ICMPNE, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IF_ICMPLT(Classfile.IF_ICMPLT, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IF_ICMPGE(Classfile.IF_ICMPGE, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IF_ICMPGT(Classfile.IF_ICMPGT, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IF_ICMPLE(Classfile.IF_ICMPLE, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    IF_ACMPEQ(Classfile.IF_ACMPEQ, 3, CodeElement.Kind.BRANCH, TypeKind.ReferenceType),
    IF_ACMPNE(Classfile.IF_ACMPNE, 3, CodeElement.Kind.BRANCH, TypeKind.ReferenceType),
    GOTO(Classfile.GOTO, 3, CodeElement.Kind.BRANCH, TypeKind.VoidType),
    JSR(Classfile.JSR, 3, CodeElement.Kind.UNSUPPORTED),
    RET(Classfile.RET, 2, CodeElement.Kind.UNSUPPORTED),
    TABLESWITCH(Classfile.TABLESWITCH, -1, CodeElement.Kind.TABLE_SWITCH),
    LOOKUPSWITCH(Classfile.LOOKUPSWITCH, -1, CodeElement.Kind.LOOKUP_SWITCH),
    IRETURN(Classfile.IRETURN, 1, CodeElement.Kind.RETURN, TypeKind.IntType),
    LRETURN(Classfile.LRETURN, 1, CodeElement.Kind.RETURN, TypeKind.LongType),
    FRETURN(Classfile.FRETURN, 1, CodeElement.Kind.RETURN, TypeKind.FloatType),
    DRETURN(Classfile.DRETURN, 1, CodeElement.Kind.RETURN, TypeKind.DoubleType),
    ARETURN(Classfile.ARETURN, 1, CodeElement.Kind.RETURN, TypeKind.ReferenceType),
    RETURN(Classfile.RETURN, 1, CodeElement.Kind.RETURN, TypeKind.VoidType),
    GETSTATIC(Classfile.GETSTATIC, 3, CodeElement.Kind.FIELD_ACCESS),
    PUTSTATIC(Classfile.PUTSTATIC, 3, CodeElement.Kind.FIELD_ACCESS),
    GETFIELD(Classfile.GETFIELD, 3, CodeElement.Kind.FIELD_ACCESS),
    PUTFIELD(Classfile.PUTFIELD, 3, CodeElement.Kind.FIELD_ACCESS),
    INVOKEVIRTUAL(Classfile.INVOKEVIRTUAL, 3, CodeElement.Kind.INVOKE),
    INVOKESPECIAL(Classfile.INVOKESPECIAL, 3, CodeElement.Kind.INVOKE),
    INVOKESTATIC(Classfile.INVOKESTATIC, 3, CodeElement.Kind.INVOKE),
    INVOKEINTERFACE(Classfile.INVOKEINTERFACE, 5, CodeElement.Kind.INVOKE),
    INVOKEDYNAMIC(Classfile.INVOKEDYNAMIC, 5, CodeElement.Kind.INVOKE_DYNAMIC),
    NEW(Classfile.NEW, 3, CodeElement.Kind.NEW_OBJECT),
    NEWARRAY(Classfile.NEWARRAY, 2, CodeElement.Kind.NEW_PRIMITIVE_ARRAY),
    ANEWARRAY(Classfile.ANEWARRAY, 3, CodeElement.Kind.NEW_REF_ARRAY),
    ARRAYLENGTH(Classfile.ARRAYLENGTH, 1, CodeElement.Kind.OPERATOR, TypeKind.IntType),
    ATHROW(Classfile.ATHROW, 1, CodeElement.Kind.THROW_EXCEPTION),
    CHECKCAST(Classfile.CHECKCAST, 3, CodeElement.Kind.TYPE_CHECK),
    INSTANCEOF(Classfile.INSTANCEOF, 3, CodeElement.Kind.TYPE_CHECK),
    MONITORENTER(Classfile.MONITORENTER, 1, CodeElement.Kind.MONITOR),
    MONITOREXIT(Classfile.MONITOREXIT, 1, CodeElement.Kind.MONITOR),
    MULTIANEWARRAY(Classfile.MULTIANEWARRAY, 4, CodeElement.Kind.NEW_MULTI_ARRAY),
    IFNULL(Classfile.IFNULL, 3, CodeElement.Kind.BRANCH, TypeKind.ReferenceType),
    IFNONNULL(Classfile.IFNONNULL, 3, CodeElement.Kind.BRANCH, TypeKind.IntType),
    GOTO_W(Classfile.GOTO_W, 5, CodeElement.Kind.BRANCH, TypeKind.VoidType),
    JSR_W(Classfile.JSR_W, 5, CodeElement.Kind.UNSUPPORTED),
    ILOAD_W(0xc400 | Classfile.ILOAD, 4, CodeElement.Kind.LOAD, TypeKind.IntType, -1),
    LLOAD_W(0xc400 | Classfile.LLOAD, 4, CodeElement.Kind.LOAD, TypeKind.LongType, -1),
    FLOAD_W(0xc400 | Classfile.FLOAD, 4, CodeElement.Kind.LOAD, TypeKind.FloatType, -1),
    DLOAD_W(0xc400 | Classfile.DLOAD, 4, CodeElement.Kind.LOAD, TypeKind.DoubleType, -1),
    ALOAD_W(0xc400 | Classfile.ALOAD, 4, CodeElement.Kind.LOAD, TypeKind.ReferenceType, -1),
    ISTORE_W(0xc400 | Classfile.ISTORE, 4, CodeElement.Kind.STORE, TypeKind.IntType, -1),
    LSTORE_W(0xc400 | Classfile.LSTORE, 4, CodeElement.Kind.STORE, TypeKind.LongType, -1),
    FSTORE_W(0xc400 | Classfile.FSTORE, 4, CodeElement.Kind.STORE, TypeKind.FloatType, -1),
    DSTORE_W(0xc400 | Classfile.DSTORE, 4, CodeElement.Kind.STORE, TypeKind.DoubleType, -1),
    ASTORE_W(0xc400 | Classfile.ASTORE, 4, CodeElement.Kind.STORE, TypeKind.ReferenceType, -1),
    RET_W(0xc400 | Classfile.RET, 4, CodeElement.Kind.UNSUPPORTED),
    IINC_W(0xc400 | Classfile.IINC, 6, CodeElement.Kind.INCREMENT, TypeKind.IntType, -1),
    // PSEUDO INSTRUCTIONS
    LABEL_TARGET(0xFF01, 0, CodeElement.Kind.LABEL_TARGET),
    EXCEPTION_CATCH(0xFF02, 0, CodeElement.Kind.EXCEPTION_CATCH),
    CHARACTER_RANGE(0xFF03, 0, CodeElement.Kind.CHARACTER_RANGE),
    LOCAL_VARIABLE(0xFF04, 0, CodeElement.Kind.LOCAL_VARIABLE),
    LINE_NUMBER(0xFF05, 0, CodeElement.Kind.LINE_NUMBER),
    PARAMETER_ANNO(0xFF06, 0, CodeElement.Kind.PARAMETER_ANNOTATION),
    TYPE_ANNO(0xFF07, 0, CodeElement.Kind.TYPE_ANNOTATION),
    LOCAL_VARIABLE_TYPE(0xFF08, 0, CodeElement.Kind.LOCAL_VARIABLE_TYPE),
    STACK_MAP(0xFF09, 0, CodeElement.Kind.STACK_MAP),
    END(0xFF0A, 0, CodeElement.Kind.END),
    ;

    private final int bytecode;
    private final int sizeIfFixed;
    private final CodeElement.Kind kind;
    private final TypeKind primaryTypeKind;
    private final TypeKind secondaryTypeKind;
    private final int slot;
    private final ConstantDesc constantValue;

    Opcode(int bytecode, int sizeIfFixed, CodeElement.Kind kind) {
        this(bytecode, sizeIfFixed, kind, null, null, 0, null);
    }

    Opcode(int bytecode, int sizeIfFixed, CodeElement.Kind kind, TypeKind typeKind) {
        this(bytecode, sizeIfFixed, kind, typeKind, null, 0, null);
    }

    Opcode(int bytecode, int sizeIfFixed, CodeElement.Kind kind, TypeKind typeKind, int slot) {
        this(bytecode, sizeIfFixed, kind, typeKind, null, slot, null);
    }

    Opcode(int bytecode, int sizeIfFixed, CodeElement.Kind kind, TypeKind typeKind, int slot, ConstantDesc constantValue) {
        this(bytecode, sizeIfFixed, kind, typeKind, null, slot, constantValue);
    }

    Opcode(int bytecode, int sizeIfFixed, CodeElement.Kind kind, TypeKind primaryTypeKind, TypeKind secondaryTypeKind) {
        this(bytecode, sizeIfFixed, kind, primaryTypeKind, secondaryTypeKind, 0, null);
    }

    Opcode(int bytecode,
           int sizeIfFixed,
           CodeElement.Kind kind,
           TypeKind primaryTypeKind,
           TypeKind secondaryTypeKind,
           int slot,
           ConstantDesc constantValue) {
        this.bytecode = bytecode;
        this.sizeIfFixed = sizeIfFixed;
        this.kind = kind;
        this.primaryTypeKind = primaryTypeKind;
        this.secondaryTypeKind = secondaryTypeKind;
        this.slot = slot;
        this.constantValue = constantValue;
    }

    public int bytecode() { return bytecode; }

    public boolean isWide() { return bytecode > 255 && !isPseudo(); }

    public boolean isPseudo() { return bytecode >= 0xFF00; }

    public int sizeIfFixed() { return sizeIfFixed; }

    public CodeElement.Kind kind() { return kind; }

    public TypeKind primaryTypeKind() {
        return primaryTypeKind;
    }

    public TypeKind secondaryTypeKind() {
        return secondaryTypeKind;
    }

    public int slot() {
        return slot;
    }

    public ConstantDesc constantValue() {
        return constantValue;
    }

    public boolean isUnconditionalBranch() {
        return switch (this) {
            case GOTO, ATHROW, GOTO_W, LOOKUPSWITCH, TABLESWITCH -> true;
            default -> kind() == CodeElement.Kind.RETURN;
        };
    }
}
