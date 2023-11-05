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
package jdk.internal.classfile;

import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;

/**
 * Describes the opcodes of the JVM instruction set, as described in {@jvms 6.5}.
 * As well as a number of pseudo-instructions that may be encountered when
 * traversing the instructions of a method.
 *
 * @see Instruction
 * @see PseudoInstruction
 */
public enum Opcode {

    /** Do nothing */
    NOP(Classfile.NOP, 1, Kind.NOP),

    /** Push null */
    ACONST_NULL(Classfile.ACONST_NULL, 1, Kind.CONSTANT, TypeKind.ReferenceType, 0, ConstantDescs.NULL),

    /** Push int constant -1 */
    ICONST_M1(Classfile.ICONST_M1, 1, Kind.CONSTANT, TypeKind.IntType, 0, -1),

    /** Push int constant 0 */
    ICONST_0(Classfile.ICONST_0, 1, Kind.CONSTANT, TypeKind.IntType, 0, 0),

    /** Push int constant 1 */
    ICONST_1(Classfile.ICONST_1, 1, Kind.CONSTANT, TypeKind.IntType, 0, 1),

    /** Push int constant 2 */
    ICONST_2(Classfile.ICONST_2, 1, Kind.CONSTANT, TypeKind.IntType, 0, 2),

    /** Push int constant 3 */
    ICONST_3(Classfile.ICONST_3, 1, Kind.CONSTANT, TypeKind.IntType, 0, 3),

    /** Push int constant 4 */
    ICONST_4(Classfile.ICONST_4, 1, Kind.CONSTANT, TypeKind.IntType, 0, 4),

    /** Push int constant 5 */
    ICONST_5(Classfile.ICONST_5, 1, Kind.CONSTANT, TypeKind.IntType, 0, 5),

    /** Push long constant 0 */
    LCONST_0(Classfile.LCONST_0, 1, Kind.CONSTANT, TypeKind.LongType, 0, 0L),

    /** Push long constant  1 */
    LCONST_1(Classfile.LCONST_1, 1, Kind.CONSTANT, TypeKind.LongType, 0, 1L),

    /** Push float constant 0 */
    FCONST_0(Classfile.FCONST_0, 1, Kind.CONSTANT, TypeKind.FloatType, 0, 0.0f),

    /** Push float constant 1 */
    FCONST_1(Classfile.FCONST_1, 1, Kind.CONSTANT, TypeKind.FloatType, 0, 1.0f),

    /** Push float constant 2 */
    FCONST_2(Classfile.FCONST_2, 1, Kind.CONSTANT, TypeKind.FloatType, 0, 2.0f),

    /** Push double constant 0 */
    DCONST_0(Classfile.DCONST_0, 1, Kind.CONSTANT, TypeKind.DoubleType, 0, 0.0d),

    /** Push double constant 1 */
    DCONST_1(Classfile.DCONST_1, 1, Kind.CONSTANT, TypeKind.DoubleType, 0, 1.0d),

    /** Push byte */
    BIPUSH(Classfile.BIPUSH, 2, Kind.CONSTANT, TypeKind.ByteType),

    /** Push short */
    SIPUSH(Classfile.SIPUSH, 3, Kind.CONSTANT, TypeKind.ShortType),

    /** Push item from run-time constant pool */
    LDC(Classfile.LDC, 2, Kind.CONSTANT),

    /** Push item from run-time constant pool (wide index) */
    LDC_W(Classfile.LDC_W, 3, Kind.CONSTANT),

    /** Push long or double from run-time constant pool (wide index) */
    LDC2_W(Classfile.LDC2_W, 3, Kind.CONSTANT),

    /** Load int from local variable */
    ILOAD(Classfile.ILOAD, 2, Kind.LOAD, TypeKind.IntType, -1),

    /** Load long from local variable */
    LLOAD(Classfile.LLOAD, 2, Kind.LOAD, TypeKind.LongType, -1),

    /** Load float from local variable */
    FLOAD(Classfile.FLOAD, 2, Kind.LOAD, TypeKind.FloatType, -1),

    /** Load double from local variable */
    DLOAD(Classfile.DLOAD, 2, Kind.LOAD, TypeKind.DoubleType, -1),

    /** Load reference from local variable */
    ALOAD(Classfile.ALOAD, 2, Kind.LOAD, TypeKind.ReferenceType, -1),

    /** Load int from local variable 0 */
    ILOAD_0(Classfile.ILOAD_0, 1, Kind.LOAD, TypeKind.IntType, 0),

    /** Load int from local variable 1 */
    ILOAD_1(Classfile.ILOAD_1, 1, Kind.LOAD, TypeKind.IntType, 1),

    /** Load int from local variable 2 */
    ILOAD_2(Classfile.ILOAD_2, 1, Kind.LOAD, TypeKind.IntType, 2),

    /** Load int from local variable3  */
    ILOAD_3(Classfile.ILOAD_3, 1, Kind.LOAD, TypeKind.IntType, 3),

    /** Load long from local variable 0 */
    LLOAD_0(Classfile.LLOAD_0, 1, Kind.LOAD, TypeKind.LongType, 0),

    /** Load long from local variable 1 */
    LLOAD_1(Classfile.LLOAD_1, 1, Kind.LOAD, TypeKind.LongType, 1),

    /** Load long from local variable 2 */
    LLOAD_2(Classfile.LLOAD_2, 1, Kind.LOAD, TypeKind.LongType, 2),

    /** Load long from local variable 3 */
    LLOAD_3(Classfile.LLOAD_3, 1, Kind.LOAD, TypeKind.LongType, 3),

    /** Load float from local variable 0 */
    FLOAD_0(Classfile.FLOAD_0, 1, Kind.LOAD, TypeKind.FloatType, 0),

    /** Load float from local variable 1 */
    FLOAD_1(Classfile.FLOAD_1, 1, Kind.LOAD, TypeKind.FloatType, 1),

    /** Load float from local variable 2 */
    FLOAD_2(Classfile.FLOAD_2, 1, Kind.LOAD, TypeKind.FloatType, 2),

    /** Load float from local variable 3 */
    FLOAD_3(Classfile.FLOAD_3, 1, Kind.LOAD, TypeKind.FloatType, 3),

    /** Load double from local variable 0 */
    DLOAD_0(Classfile.DLOAD_0, 1, Kind.LOAD, TypeKind.DoubleType, 0),

    /** Load double from local variable 1 */
    DLOAD_1(Classfile.DLOAD_1, 1, Kind.LOAD, TypeKind.DoubleType, 1),

    /** Load double from local variable 2 */
    DLOAD_2(Classfile.DLOAD_2, 1, Kind.LOAD, TypeKind.DoubleType, 2),

    /** Load double from local variable 3 */
    DLOAD_3(Classfile.DLOAD_3, 1, Kind.LOAD, TypeKind.DoubleType, 3),

    /**  Load reference from local variable 0 */
    ALOAD_0(Classfile.ALOAD_0, 1, Kind.LOAD, TypeKind.ReferenceType, 0),

    /** Load reference from local variable 1 */
    ALOAD_1(Classfile.ALOAD_1, 1, Kind.LOAD, TypeKind.ReferenceType, 1),

    /** Load reference from local variable 2 */
    ALOAD_2(Classfile.ALOAD_2, 1, Kind.LOAD, TypeKind.ReferenceType, 2),

    /** Load reference from local variable 3 */
    ALOAD_3(Classfile.ALOAD_3, 1, Kind.LOAD, TypeKind.ReferenceType, 3),

    /** Load int from array */
    IALOAD(Classfile.IALOAD, 1, Kind.ARRAY_LOAD, TypeKind.IntType),

    /** Load long from array */
    LALOAD(Classfile.LALOAD, 1, Kind.ARRAY_LOAD, TypeKind.LongType),

    /** Load float from array */
    FALOAD(Classfile.FALOAD, 1, Kind.ARRAY_LOAD, TypeKind.FloatType),

    /** Load double from array */
    DALOAD(Classfile.DALOAD, 1, Kind.ARRAY_LOAD, TypeKind.DoubleType),

    /** Load reference from array */
    AALOAD(Classfile.AALOAD, 1, Kind.ARRAY_LOAD, TypeKind.ReferenceType),

    /** Load byte from array */
    BALOAD(Classfile.BALOAD, 1, Kind.ARRAY_LOAD, TypeKind.ByteType),

    /** Load char from array */
    CALOAD(Classfile.CALOAD, 1, Kind.ARRAY_LOAD, TypeKind.CharType),

    /** Load short from array */
    SALOAD(Classfile.SALOAD, 1, Kind.ARRAY_LOAD, TypeKind.ShortType),

    /** Store int into local variable */
    ISTORE(Classfile.ISTORE, 2, Kind.STORE, TypeKind.IntType, -1),

    /** Store long into local variable */
    LSTORE(Classfile.LSTORE, 2, Kind.STORE, TypeKind.LongType, -1),

    /** Store float into local variable */
    FSTORE(Classfile.FSTORE, 2, Kind.STORE, TypeKind.FloatType, -1),

    /** Store double into local variable */
    DSTORE(Classfile.DSTORE, 2, Kind.STORE, TypeKind.DoubleType, -1),

    /** Store reference into local variable */
    ASTORE(Classfile.ASTORE, 2, Kind.STORE, TypeKind.ReferenceType, -1),

    /** Store int into local variable 0 */
    ISTORE_0(Classfile.ISTORE_0, 1, Kind.STORE, TypeKind.IntType, 0),

    /** Store int into local variable 1 */
    ISTORE_1(Classfile.ISTORE_1, 1, Kind.STORE, TypeKind.IntType, 1),

    /** Store int into local variable 2 */
    ISTORE_2(Classfile.ISTORE_2, 1, Kind.STORE, TypeKind.IntType, 2),

    /** Store int into local variable 3 */
    ISTORE_3(Classfile.ISTORE_3, 1, Kind.STORE, TypeKind.IntType, 3),

    /** Store long into local variable 0 */
    LSTORE_0(Classfile.LSTORE_0, 1, Kind.STORE, TypeKind.LongType, 0),

    /** Store long into local variable 1 */
    LSTORE_1(Classfile.LSTORE_1, 1, Kind.STORE, TypeKind.LongType, 1),

    /** Store long into local variable 2 */
    LSTORE_2(Classfile.LSTORE_2, 1, Kind.STORE, TypeKind.LongType, 2),

    /** Store long into local variable 3 */
    LSTORE_3(Classfile.LSTORE_3, 1, Kind.STORE, TypeKind.LongType, 3),

    /** Store float into local variable 0 */
    FSTORE_0(Classfile.FSTORE_0, 1, Kind.STORE, TypeKind.FloatType, 0),

    /** Store float into local variable 1 */
    FSTORE_1(Classfile.FSTORE_1, 1, Kind.STORE, TypeKind.FloatType, 1),

    /** Store float into local variable 2 */
    FSTORE_2(Classfile.FSTORE_2, 1, Kind.STORE, TypeKind.FloatType, 2),

    /** Store float into local variable 3 */
    FSTORE_3(Classfile.FSTORE_3, 1, Kind.STORE, TypeKind.FloatType, 3),

    /** Store double into local variable 0 */
    DSTORE_0(Classfile.DSTORE_0, 1, Kind.STORE, TypeKind.DoubleType, 0),

    /** Store double into local variable 1 */
    DSTORE_1(Classfile.DSTORE_1, 1, Kind.STORE, TypeKind.DoubleType, 1),

    /** Store double into local variable 2 */
    DSTORE_2(Classfile.DSTORE_2, 1, Kind.STORE, TypeKind.DoubleType, 2),

    /** Store double into local variable 3 */
    DSTORE_3(Classfile.DSTORE_3, 1, Kind.STORE, TypeKind.DoubleType, 3),

    /** Store reference into local variable 0 */
    ASTORE_0(Classfile.ASTORE_0, 1, Kind.STORE, TypeKind.ReferenceType, 0),

    /** Store reference into local variable 1 */
    ASTORE_1(Classfile.ASTORE_1, 1, Kind.STORE, TypeKind.ReferenceType, 1),

    /** Store reference into local variable 2 */
    ASTORE_2(Classfile.ASTORE_2, 1, Kind.STORE, TypeKind.ReferenceType, 2),

    /** Store reference into local variable 3 */
    ASTORE_3(Classfile.ASTORE_3, 1, Kind.STORE, TypeKind.ReferenceType, 3),

    /** Store into int array */
    IASTORE(Classfile.IASTORE, 1, Kind.ARRAY_STORE, TypeKind.IntType),

    /** Store into long array */
    LASTORE(Classfile.LASTORE, 1, Kind.ARRAY_STORE, TypeKind.LongType),

    /** Store into float array */
    FASTORE(Classfile.FASTORE, 1, Kind.ARRAY_STORE, TypeKind.FloatType),

    /** Store into double array */
    DASTORE(Classfile.DASTORE, 1, Kind.ARRAY_STORE, TypeKind.DoubleType),

    /** Store into reference array */
    AASTORE(Classfile.AASTORE, 1, Kind.ARRAY_STORE, TypeKind.ReferenceType),

    /** Store into byte array */
    BASTORE(Classfile.BASTORE, 1, Kind.ARRAY_STORE, TypeKind.ByteType),

    /** Store into char array */
    CASTORE(Classfile.CASTORE, 1, Kind.ARRAY_STORE, TypeKind.CharType),

    /** Store into short array */
    SASTORE(Classfile.SASTORE, 1, Kind.ARRAY_STORE, TypeKind.ShortType),

    /** Pop the top operand stack value */
    POP(Classfile.POP, 1, Kind.STACK),

    /** Pop the top one or two operand stack values */
    POP2(Classfile.POP2, 1, Kind.STACK),

    /** Duplicate the top operand stack value */
    DUP(Classfile.DUP, 1, Kind.STACK),

    /** Duplicate the top operand stack value and insert two values down */
    DUP_X1(Classfile.DUP_X1, 1, Kind.STACK),

    /** Duplicate the top operand stack value and insert two or three values down */
    DUP_X2(Classfile.DUP_X2, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values */
    DUP2(Classfile.DUP2, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values and insert two or three values down */
    DUP2_X1(Classfile.DUP2_X1, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values and insert two, three, or four values down */
    DUP2_X2(Classfile.DUP2_X2, 1, Kind.STACK),

    /** Swap the top two operand stack values */
    SWAP(Classfile.SWAP, 1, Kind.STACK),

    /** Add int */
    IADD(Classfile.IADD, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Add long */
    LADD(Classfile.LADD, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Add float */
    FADD(Classfile.FADD, 1, Kind.OPERATOR, TypeKind.FloatType),

    /** Add double */
    DADD(Classfile.DADD, 1, Kind.OPERATOR, TypeKind.DoubleType),

    /** Subtract int */
    ISUB(Classfile.ISUB, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Subtract long */
    LSUB(Classfile.LSUB, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Subtract float */
    FSUB(Classfile.FSUB, 1, Kind.OPERATOR, TypeKind.FloatType),

    /** Subtract double */
    DSUB(Classfile.DSUB, 1, Kind.OPERATOR, TypeKind.DoubleType),

    /** Multiply int */
    IMUL(Classfile.IMUL, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Multiply long */
    LMUL(Classfile.LMUL, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Multiply float */
    FMUL(Classfile.FMUL, 1, Kind.OPERATOR, TypeKind.FloatType),

    /** Multiply double */
    DMUL(Classfile.DMUL, 1, Kind.OPERATOR, TypeKind.DoubleType),

    /** Divide int */
    IDIV(Classfile.IDIV, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Divide long */
    LDIV(Classfile.LDIV, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Divide float */
    FDIV(Classfile.FDIV, 1, Kind.OPERATOR, TypeKind.FloatType),

    /** Divide double */
    DDIV(Classfile.DDIV, 1, Kind.OPERATOR, TypeKind.DoubleType),

    /** Remainder int */
    IREM(Classfile.IREM, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Remainder long */
    LREM(Classfile.LREM, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Remainder float */
    FREM(Classfile.FREM, 1, Kind.OPERATOR, TypeKind.FloatType),

    /** Remainder double */
    DREM(Classfile.DREM, 1, Kind.OPERATOR, TypeKind.DoubleType),

    /** Negate int */
    INEG(Classfile.INEG, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Negate long */
    LNEG(Classfile.LNEG, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Negate float */
    FNEG(Classfile.FNEG, 1, Kind.OPERATOR, TypeKind.FloatType),

    /** Negate double */
    DNEG(Classfile.DNEG, 1, Kind.OPERATOR, TypeKind.DoubleType),

    /** Shift left int */
    ISHL(Classfile.ISHL, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Shift left long */
    LSHL(Classfile.LSHL, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Shift right int */
    ISHR(Classfile.ISHR, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Shift right long */
    LSHR(Classfile.LSHR, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Logical shift right int */
    IUSHR(Classfile.IUSHR, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Logical shift right long */
    LUSHR(Classfile.LUSHR, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Boolean AND int */
    IAND(Classfile.IAND, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Boolean AND long */
    LAND(Classfile.LAND, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Boolean OR int */
    IOR(Classfile.IOR, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Boolean OR long */
    LOR(Classfile.LOR, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Boolean XOR int */
    IXOR(Classfile.IXOR, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Boolean XOR long */
    LXOR(Classfile.LXOR, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Increment local variable by constant */
    IINC(Classfile.IINC, 3, Kind.INCREMENT, TypeKind.IntType, -1),

    /** Convert int to long */
    I2L(Classfile.I2L, 1, Kind.CONVERT, TypeKind.IntType, TypeKind.LongType),

    /** Convert int to float */
    I2F(Classfile.I2F, 1, Kind.CONVERT, TypeKind.IntType, TypeKind.FloatType),

    /** Convert int to double */
    I2D(Classfile.I2D, 1, Kind.CONVERT, TypeKind.IntType, TypeKind.DoubleType),

    /** Convert long to int */
    L2I(Classfile.L2I, 1, Kind.CONVERT, TypeKind.LongType, TypeKind.IntType),

    /** Convert long to float */
    L2F(Classfile.L2F, 1, Kind.CONVERT, TypeKind.LongType, TypeKind.FloatType),

    /** Convert long to double */
    L2D(Classfile.L2D, 1, Kind.CONVERT, TypeKind.LongType, TypeKind.DoubleType),

    /** Convert float to int */
    F2I(Classfile.F2I, 1, Kind.CONVERT, TypeKind.FloatType, TypeKind.IntType),

    /** Convert float to long */
    F2L(Classfile.F2L, 1, Kind.CONVERT, TypeKind.FloatType, TypeKind.LongType),

    /** Convert float to double */
    F2D(Classfile.F2D, 1, Kind.CONVERT, TypeKind.FloatType, TypeKind.DoubleType),

    /** Convert double to int */
    D2I(Classfile.D2I, 1, Kind.CONVERT, TypeKind.DoubleType, TypeKind.IntType),

    /** Convert double to long */
    D2L(Classfile.D2L, 1, Kind.CONVERT, TypeKind.DoubleType, TypeKind.LongType),

    /** Convert double to float */
    D2F(Classfile.D2F, 1, Kind.CONVERT, TypeKind.DoubleType, TypeKind.FloatType),

    /** Convert int to byte */
    I2B(Classfile.I2B, 1, Kind.CONVERT, TypeKind.IntType, TypeKind.ByteType),

    /** Convert int to char */
    I2C(Classfile.I2C, 1, Kind.CONVERT, TypeKind.IntType, TypeKind.CharType),

    /** Convert int to short */
    I2S(Classfile.I2S, 1, Kind.CONVERT, TypeKind.IntType, TypeKind.ShortType),

    /** Compare long */
    LCMP(Classfile.LCMP, 1, Kind.OPERATOR, TypeKind.LongType),

    /** Compare float */
    FCMPL(Classfile.FCMPL, 1, Kind.OPERATOR, TypeKind.FloatType),

    /** Compare float */
    FCMPG(Classfile.FCMPG, 1, Kind.OPERATOR, TypeKind.FloatType),

    /** Compare double */
    DCMPL(Classfile.DCMPL, 1, Kind.OPERATOR, TypeKind.DoubleType),

    /** Compare double */
    DCMPG(Classfile.DCMPG, 1, Kind.OPERATOR, TypeKind.DoubleType),

    /** Branch if int comparison with zero succeeds */
    IFEQ(Classfile.IFEQ, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison with zero succeeds */
    IFNE(Classfile.IFNE, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison with zero succeeds */
    IFLT(Classfile.IFLT, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison with zero succeeds */
    IFGE(Classfile.IFGE, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison with zero succeeds */
    IFGT(Classfile.IFGT, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison with zero succeeds */
    IFLE(Classfile.IFLE, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison succeeds */
    IF_ICMPEQ(Classfile.IF_ICMPEQ, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison succeeds */
    IF_ICMPNE(Classfile.IF_ICMPNE, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison succeeds */
    IF_ICMPLT(Classfile.IF_ICMPLT, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison succeeds */
    IF_ICMPGE(Classfile.IF_ICMPGE, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison succeeds */
    IF_ICMPGT(Classfile.IF_ICMPGT, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if int comparison succeeds */
    IF_ICMPLE(Classfile.IF_ICMPLE, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch if reference comparison succeeds */
    IF_ACMPEQ(Classfile.IF_ACMPEQ, 3, Kind.BRANCH, TypeKind.ReferenceType),

    /** Branch if reference comparison succeeds */
    IF_ACMPNE(Classfile.IF_ACMPNE, 3, Kind.BRANCH, TypeKind.ReferenceType),

    /** Branch always */
    GOTO(Classfile.GOTO, 3, Kind.BRANCH, TypeKind.VoidType),

    /** Jump subroutine */
    JSR(Classfile.JSR, 3, Kind.DISCONTINUED_JSR),

    /** Return from subroutine */
    RET(Classfile.RET, 2, Kind.DISCONTINUED_RET),

    /** Access jump table by index and jump */
    TABLESWITCH(Classfile.TABLESWITCH, -1, Kind.TABLE_SWITCH),

    /** Access jump table by key match and jump */
    LOOKUPSWITCH(Classfile.LOOKUPSWITCH, -1, Kind.LOOKUP_SWITCH),

    /** Return int from method */
    IRETURN(Classfile.IRETURN, 1, Kind.RETURN, TypeKind.IntType),

    /** Return long from method */
    LRETURN(Classfile.LRETURN, 1, Kind.RETURN, TypeKind.LongType),

    /** Return float from method */
    FRETURN(Classfile.FRETURN, 1, Kind.RETURN, TypeKind.FloatType),

    /** Return double from method */
    DRETURN(Classfile.DRETURN, 1, Kind.RETURN, TypeKind.DoubleType),

    /** Return reference from method */
    ARETURN(Classfile.ARETURN, 1, Kind.RETURN, TypeKind.ReferenceType),

    /** Return void from method */
    RETURN(Classfile.RETURN, 1, Kind.RETURN, TypeKind.VoidType),

    /** Get static field from class */
    GETSTATIC(Classfile.GETSTATIC, 3, Kind.FIELD_ACCESS),

    /** Set static field in class */
    PUTSTATIC(Classfile.PUTSTATIC, 3, Kind.FIELD_ACCESS),

    /** Fetch field from object */
    GETFIELD(Classfile.GETFIELD, 3, Kind.FIELD_ACCESS),

    /** Set field in object */
    PUTFIELD(Classfile.PUTFIELD, 3, Kind.FIELD_ACCESS),

    /** Invoke instance method; dispatch based on class */
    INVOKEVIRTUAL(Classfile.INVOKEVIRTUAL, 3, Kind.INVOKE),

    /**
     * Invoke instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     */
    INVOKESPECIAL(Classfile.INVOKESPECIAL, 3, Kind.INVOKE),

    /** Invoke a class (static) method */
    INVOKESTATIC(Classfile.INVOKESTATIC, 3, Kind.INVOKE),

    /** Invoke interface method */
    INVOKEINTERFACE(Classfile.INVOKEINTERFACE, 5, Kind.INVOKE),

    /** Invoke a dynamically-computed call site */
    INVOKEDYNAMIC(Classfile.INVOKEDYNAMIC, 5, Kind.INVOKE_DYNAMIC),

    /** Create new object */
    NEW(Classfile.NEW, 3, Kind.NEW_OBJECT),

    /** Create new array */
    NEWARRAY(Classfile.NEWARRAY, 2, Kind.NEW_PRIMITIVE_ARRAY),

    /** Create new array of reference */
    ANEWARRAY(Classfile.ANEWARRAY, 3, Kind.NEW_REF_ARRAY),

    /** Get length of array */
    ARRAYLENGTH(Classfile.ARRAYLENGTH, 1, Kind.OPERATOR, TypeKind.IntType),

    /** Throw exception or error */
    ATHROW(Classfile.ATHROW, 1, Kind.THROW_EXCEPTION),

    /** Check whether object is of given type */
    CHECKCAST(Classfile.CHECKCAST, 3, Kind.TYPE_CHECK),

    /** Determine if object is of given type */
    INSTANCEOF(Classfile.INSTANCEOF, 3, Kind.TYPE_CHECK),

    /** Enter monitor for object */
    MONITORENTER(Classfile.MONITORENTER, 1, Kind.MONITOR),

    /** Exit monitor for object */
    MONITOREXIT(Classfile.MONITOREXIT, 1, Kind.MONITOR),

    /** Create new multidimensional array */
    MULTIANEWARRAY(Classfile.MULTIANEWARRAY, 4, Kind.NEW_MULTI_ARRAY),

    /** Branch if reference is null */
    IFNULL(Classfile.IFNULL, 3, Kind.BRANCH, TypeKind.ReferenceType),

    /** Branch if reference not null */
    IFNONNULL(Classfile.IFNONNULL, 3, Kind.BRANCH, TypeKind.IntType),

    /** Branch always (wide index) */
    GOTO_W(Classfile.GOTO_W, 5, Kind.BRANCH, TypeKind.VoidType),

    /** Jump subroutine (wide index) */
    JSR_W(Classfile.JSR_W, 5, Kind.DISCONTINUED_JSR),

    /** Load int from local variable (wide index) */
    ILOAD_W((Classfile.WIDE << 8) | Classfile.ILOAD, 4, Kind.LOAD, TypeKind.IntType, -1),

    /** Load long from local variable (wide index) */
    LLOAD_W((Classfile.WIDE << 8) | Classfile.LLOAD, 4, Kind.LOAD, TypeKind.LongType, -1),

    /** Load float from local variable (wide index) */
    FLOAD_W((Classfile.WIDE << 8) | Classfile.FLOAD, 4, Kind.LOAD, TypeKind.FloatType, -1),

    /** Load double from local variable (wide index) */
    DLOAD_W((Classfile.WIDE << 8) | Classfile.DLOAD, 4, Kind.LOAD, TypeKind.DoubleType, -1),

    /** Load reference from local variable (wide index) */
    ALOAD_W((Classfile.WIDE << 8) | Classfile.ALOAD, 4, Kind.LOAD, TypeKind.ReferenceType, -1),

    /** Store int into local variable (wide index) */
    ISTORE_W((Classfile.WIDE << 8) | Classfile.ISTORE, 4, Kind.STORE, TypeKind.IntType, -1),

    /** Store long into local variable (wide index) */
    LSTORE_W((Classfile.WIDE << 8) | Classfile.LSTORE, 4, Kind.STORE, TypeKind.LongType, -1),

    /** Store float into local variable (wide index) */
    FSTORE_W((Classfile.WIDE << 8) | Classfile.FSTORE, 4, Kind.STORE, TypeKind.FloatType, -1),

    /** Store double into local variable (wide index) */
    DSTORE_W((Classfile.WIDE << 8) | Classfile.DSTORE, 4, Kind.STORE, TypeKind.DoubleType, -1),

    /** Store reference into local variable (wide index) */
    ASTORE_W((Classfile.WIDE << 8) | Classfile.ASTORE, 4, Kind.STORE, TypeKind.ReferenceType, -1),

    /** Return from subroutine (wide index) */
    RET_W((Classfile.WIDE << 8) | Classfile.RET, 4, Kind.DISCONTINUED_RET),

    /** Increment local variable by constant (wide index) */
    IINC_W((Classfile.WIDE << 8) | Classfile.IINC, 6, Kind.INCREMENT, TypeKind.IntType, -1);

    /**
     * Kinds of opcodes.
     */
    public static enum Kind {

        /**
         * Load from local variable
         *
         * @see Opcode#ILOAD
         * @see Opcode#LLOAD
         * @see Opcode#FLOAD
         * @see Opcode#DLOAD
         * @see Opcode#ALOAD
         * @see Opcode#ILOAD_0
         * @see Opcode#ILOAD_1
         * @see Opcode#ILOAD_2
         * @see Opcode#ILOAD_3
         * @see Opcode#LLOAD_0
         * @see Opcode#LLOAD_1
         * @see Opcode#LLOAD_2
         * @see Opcode#LLOAD_3
         * @see Opcode#FLOAD_0
         * @see Opcode#FLOAD_1
         * @see Opcode#FLOAD_2
         * @see Opcode#FLOAD_3
         * @see Opcode#DLOAD_0
         * @see Opcode#DLOAD_1
         * @see Opcode#DLOAD_2
         * @see Opcode#DLOAD_3
         * @see Opcode#ALOAD_0
         * @see Opcode#ALOAD_1
         * @see Opcode#ALOAD_2
         * @see Opcode#ALOAD_3
         * @see Opcode#ILOAD_W
         * @see Opcode#LLOAD_W
         * @see Opcode#FLOAD_W
         * @see Opcode#DLOAD_W
         * @see Opcode#ALOAD_W
         */
        LOAD,

        /**
         * Store into local variable
         *
         * @see Opcode#ISTORE
         * @see Opcode#LSTORE
         * @see Opcode#FSTORE
         * @see Opcode#DSTORE
         * @see Opcode#ASTORE
         * @see Opcode#ISTORE_0
         * @see Opcode#ISTORE_1
         * @see Opcode#ISTORE_2
         * @see Opcode#ISTORE_3
         * @see Opcode#LSTORE_0
         * @see Opcode#LSTORE_1
         * @see Opcode#LSTORE_2
         * @see Opcode#LSTORE_3
         * @see Opcode#FSTORE_0
         * @see Opcode#FSTORE_1
         * @see Opcode#FSTORE_2
         * @see Opcode#FSTORE_3
         * @see Opcode#DSTORE_0
         * @see Opcode#DSTORE_1
         * @see Opcode#DSTORE_2
         * @see Opcode#DSTORE_3
         * @see Opcode#ASTORE_0
         * @see Opcode#ASTORE_1
         * @see Opcode#ASTORE_2
         * @see Opcode#ASTORE_3
         * @see Opcode#ISTORE_W
         * @see Opcode#LSTORE_W
         * @see Opcode#FSTORE_W
         * @see Opcode#DSTORE_W
         * @see Opcode#ASTORE_W
         */
        STORE,

        /**
         * Increment local variable
         *
         * @see Opcode#IINC
         * @see Opcode#IINC_W
         */
        INCREMENT,

        /**
         * Branch
         *
         * @see Opcode#IFEQ
         * @see Opcode#IFNE
         * @see Opcode#IFLT
         * @see Opcode#IFGE
         * @see Opcode#IFGT
         * @see Opcode#IFLE
         * @see Opcode#IF_ICMPEQ
         * @see Opcode#IF_ICMPNE
         * @see Opcode#IF_ICMPLT
         * @see Opcode#IF_ICMPGE
         * @see Opcode#IF_ICMPGT
         * @see Opcode#IF_ICMPLE
         * @see Opcode#IF_ACMPEQ
         * @see Opcode#IF_ACMPNE
         * @see Opcode#GOTO
         * @see Opcode#IFNULL
         * @see Opcode#IFNONNULL
         * @see Opcode#GOTO_W
         */
        BRANCH,

        /**
         * Access jump table by key match and jump
         *
         * @see Opcode#LOOKUPSWITCH
         */
        LOOKUP_SWITCH,

        /**
         * Access jump table by index and jump
         *
         * @see Opcode#TABLESWITCH
         */
        TABLE_SWITCH,

        /**
         * Return from method
         *
         * @see Opcode#IRETURN
         * @see Opcode#LRETURN
         * @see Opcode#FRETURN
         * @see Opcode#DRETURN
         * @see Opcode#ARETURN
         * @see Opcode#RETURN
         */
        RETURN,

        /**
         * Throw exception or error
         *
         * @see Opcode#ATHROW
         */
        THROW_EXCEPTION,

        /**
         * Access field
         *
         * @see Opcode#GETSTATIC
         * @see Opcode#PUTSTATIC
         * @see Opcode#GETFIELD
         * @see Opcode#PUTFIELD
         */
        FIELD_ACCESS,

        /**
         * Invoke method or constructor
         *
         * @see Opcode#INVOKEVIRTUAL
         * @see Opcode#INVOKESPECIAL
         * @see Opcode#INVOKESTATIC
         * @see Opcode#INVOKEINTERFACE
         */
        INVOKE,

        /**
         * Invoke a dynamically-computed call site
         *
         * @see Opcode#INVOKEDYNAMIC
         */
        INVOKE_DYNAMIC,

        /**
         * Create new object
         *
         * @see Opcode#NEW
         */
        NEW_OBJECT,

        /**
         * Create new array
         *
         * @see Opcode#NEWARRAY
         */
        NEW_PRIMITIVE_ARRAY,

        /**
         * Create new reference array
         *
         * @see Opcode#ANEWARRAY
         */
        NEW_REF_ARRAY,

        /**
         * Create new multidimensional array
         *
         * @see Opcode#MULTIANEWARRAY
         */
        NEW_MULTI_ARRAY,

        /**
         * Check whether object is of given type
         *
         * @see Opcode#CHECKCAST
         * @see Opcode#INSTANCEOF
         */
        TYPE_CHECK,

        /**
         * Load from array
         *
         * @see Opcode#IALOAD
         * @see Opcode#LALOAD
         * @see Opcode#FALOAD
         * @see Opcode#DALOAD
         * @see Opcode#AALOAD
         * @see Opcode#BALOAD
         * @see Opcode#CALOAD
         * @see Opcode#SALOAD
         */
        ARRAY_LOAD,

        /**
         * Store into array
         *
         * @see Opcode#IASTORE
         * @see Opcode#LASTORE
         * @see Opcode#FASTORE
         * @see Opcode#DASTORE
         * @see Opcode#AASTORE
         * @see Opcode#BASTORE
         * @see Opcode#CASTORE
         * @see Opcode#SASTORE
         */
        ARRAY_STORE,

        /**
         * Stack operations
         *
         * @see Opcode#POP
         * @see Opcode#POP2
         * @see Opcode#DUP
         * @see Opcode#DUP_X1
         * @see Opcode#DUP_X2
         * @see Opcode#DUP2
         * @see Opcode#DUP2_X1
         * @see Opcode#DUP2_X2
         * @see Opcode#SWAP
         */
        STACK,

        /**
         * Type conversions
         *
         * @see Opcode#I2L
         * @see Opcode#I2F
         * @see Opcode#I2D
         * @see Opcode#L2I
         * @see Opcode#L2F
         * @see Opcode#L2D
         * @see Opcode#F2I
         * @see Opcode#F2L
         * @see Opcode#F2D
         * @see Opcode#D2I
         * @see Opcode#D2L
         * @see Opcode#D2F
         * @see Opcode#I2B
         * @see Opcode#I2C
         * @see Opcode#I2S
         */
        CONVERT,

        /**
         * Operators
         *
         * @see Opcode#IADD
         * @see Opcode#LADD
         * @see Opcode#FADD
         * @see Opcode#DADD
         * @see Opcode#ISUB
         * @see Opcode#LSUB
         * @see Opcode#FSUB
         * @see Opcode#DSUB
         * @see Opcode#IMUL
         * @see Opcode#LMUL
         * @see Opcode#FMUL
         * @see Opcode#DMUL
         * @see Opcode#IDIV
         * @see Opcode#LDIV
         * @see Opcode#FDIV
         * @see Opcode#DDIV
         * @see Opcode#IREM
         * @see Opcode#LREM
         * @see Opcode#FREM
         * @see Opcode#DREM
         * @see Opcode#INEG
         * @see Opcode#LNEG
         * @see Opcode#FNEG
         * @see Opcode#DNEG
         * @see Opcode#ISHL
         * @see Opcode#LSHL
         * @see Opcode#ISHR
         * @see Opcode#LSHR
         * @see Opcode#IUSHR
         * @see Opcode#LUSHR
         * @see Opcode#IAND
         * @see Opcode#LAND
         * @see Opcode#IOR
         * @see Opcode#LOR
         * @see Opcode#IXOR
         * @see Opcode#LXOR
         * @see Opcode#LCMP
         * @see Opcode#FCMPL
         * @see Opcode#FCMPG
         * @see Opcode#DCMPL
         * @see Opcode#DCMPG
         * @see Opcode#ARRAYLENGTH
         */
        OPERATOR,

        /**
         * Constants
         *
         * @see Opcode#ACONST_NULL
         * @see Opcode#ICONST_M1
         * @see Opcode#ICONST_0
         * @see Opcode#ICONST_1
         * @see Opcode#ICONST_2
         * @see Opcode#ICONST_3
         * @see Opcode#ICONST_4
         * @see Opcode#ICONST_5
         * @see Opcode#LCONST_0
         * @see Opcode#LCONST_1
         * @see Opcode#FCONST_0
         * @see Opcode#FCONST_1
         * @see Opcode#FCONST_2
         * @see Opcode#DCONST_0
         * @see Opcode#DCONST_1
         * @see Opcode#BIPUSH
         * @see Opcode#SIPUSH
         * @see Opcode#LDC
         * @see Opcode#LDC_W
         * @see Opcode#LDC2_W
         */
        CONSTANT,

        /**
         * Monitor
         *
         * @see Opcode#MONITORENTER
         * @see Opcode#MONITOREXIT
         */
        MONITOR,

        /**
         * Do nothing
         *
         * @see Opcode#NOP
         */
        NOP,

        /**
         * Discontinued jump subroutine
         *
         * @see Opcode#JSR
         * @see Opcode#JSR_W
         */
        DISCONTINUED_JSR,

        /**
         * Discontinued return from subroutine
         *
         * @see Opcode#RET
         * @see Opcode#RET_W
         */
        DISCONTINUED_RET;
    }

    private final int bytecode;
    private final int sizeIfFixed;
    private final Kind kind;
    private final TypeKind primaryTypeKind;
    private final TypeKind secondaryTypeKind;
    private final int slot;
    private final ConstantDesc constantValue;

    Opcode(int bytecode, int sizeIfFixed, Kind kind) {
        this(bytecode, sizeIfFixed, kind, null, null, -1, null);
    }

    Opcode(int bytecode, int sizeIfFixed, Kind kind, TypeKind typeKind) {
        this(bytecode, sizeIfFixed, kind, typeKind, null, -1, null);
    }

    Opcode(int bytecode, int sizeIfFixed, Kind kind, TypeKind typeKind, int slot) {
        this(bytecode, sizeIfFixed, kind, typeKind, null, slot, null);
    }

    Opcode(int bytecode, int sizeIfFixed, Kind kind, TypeKind typeKind, int slot, ConstantDesc constantValue) {
        this(bytecode, sizeIfFixed, kind, typeKind, null, slot, constantValue);
    }

    Opcode(int bytecode, int sizeIfFixed, Kind kind, TypeKind primaryTypeKind, TypeKind secondaryTypeKind) {
        this(bytecode, sizeIfFixed, kind, primaryTypeKind, secondaryTypeKind, 0, null);
    }

    Opcode(int bytecode,
           int sizeIfFixed,
           Kind kind,
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

    /**
     * {@return bytecode}
     */
    public int bytecode() { return bytecode; }

    /**
     * {@return true if the instruction has extended local variable index by additional bytes}
     */
    public boolean isWide() { return bytecode > 255; }

    /**
     * {@return size of the instruction if fixed, or -1 otherwise}
     */
    public int sizeIfFixed() { return sizeIfFixed; }

    /**
     * {@return instruction kind}
     */
    public Kind kind() { return kind; }

    /**
     * {@return primary type kind for instructions operating with at least one type, or null otherwise}
     */
    public TypeKind primaryTypeKind() {
        return primaryTypeKind;
    }

    /**
     * {@return secondary type kind for instructions operating with two types, or null otherwise}
     */
    public TypeKind secondaryTypeKind() {
        return secondaryTypeKind;
    }

    /**
     * {@return local variable slot for instructions operating with local variable, or -1 otherwise}
     */
    public int slot() {
        return slot;
    }

    /**
     * {@return constant value for constant instructions, or null otherwise}
     */
    public ConstantDesc constantValue() {
        return constantValue;
    }

    /**
     * {@return true if the instruction represents an unconditional branch}
     */
    public boolean isUnconditionalBranch() {
        return switch (this) {
            case GOTO, ATHROW, GOTO_W, LOOKUPSWITCH, TABLESWITCH -> true;
            default -> kind() == Kind.RETURN;
        };
    }
}
