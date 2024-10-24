/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile;

import jdk.internal.classfile.impl.RawBytecodeHelper;

/**
 * Describes the opcodes of the JVM instruction set, as described in JVMS {@jvms 6.5}.
 * As well as a number of pseudo-instructions that may be encountered when
 * traversing the instructions of a method.
 *
 * @see Instruction
 * @see PseudoInstruction
 *
 * @since 24
 */
public enum Opcode {

    /** Do nothing */
    NOP(RawBytecodeHelper.NOP, 1, Kind.NOP),

    /** Push null */
    ACONST_NULL(RawBytecodeHelper.ACONST_NULL, 1, Kind.CONSTANT),

    /** Push int constant -1 */
    ICONST_M1(RawBytecodeHelper.ICONST_M1, 1, Kind.CONSTANT),

    /** Push int constant 0 */
    ICONST_0(RawBytecodeHelper.ICONST_0, 1, Kind.CONSTANT),

    /** Push int constant 1 */
    ICONST_1(RawBytecodeHelper.ICONST_1, 1, Kind.CONSTANT),

    /** Push int constant 2 */
    ICONST_2(RawBytecodeHelper.ICONST_2, 1, Kind.CONSTANT),

    /** Push int constant 3 */
    ICONST_3(RawBytecodeHelper.ICONST_3, 1, Kind.CONSTANT),

    /** Push int constant 4 */
    ICONST_4(RawBytecodeHelper.ICONST_4, 1, Kind.CONSTANT),

    /** Push int constant 5 */
    ICONST_5(RawBytecodeHelper.ICONST_5, 1, Kind.CONSTANT),

    /** Push long constant 0 */
    LCONST_0(RawBytecodeHelper.LCONST_0, 1, Kind.CONSTANT),

    /** Push long constant  1 */
    LCONST_1(RawBytecodeHelper.LCONST_1, 1, Kind.CONSTANT),

    /** Push float constant 0 */
    FCONST_0(RawBytecodeHelper.FCONST_0, 1, Kind.CONSTANT),

    /** Push float constant 1 */
    FCONST_1(RawBytecodeHelper.FCONST_1, 1, Kind.CONSTANT),

    /** Push float constant 2 */
    FCONST_2(RawBytecodeHelper.FCONST_2, 1, Kind.CONSTANT),

    /** Push double constant 0 */
    DCONST_0(RawBytecodeHelper.DCONST_0, 1, Kind.CONSTANT),

    /** Push double constant 1 */
    DCONST_1(RawBytecodeHelper.DCONST_1, 1, Kind.CONSTANT),

    /** Push byte */
    BIPUSH(RawBytecodeHelper.BIPUSH, 2, Kind.CONSTANT),

    /** Push short */
    SIPUSH(RawBytecodeHelper.SIPUSH, 3, Kind.CONSTANT),

    /** Push item from run-time constant pool */
    LDC(RawBytecodeHelper.LDC, 2, Kind.CONSTANT),

    /** Push item from run-time constant pool (wide index) */
    LDC_W(RawBytecodeHelper.LDC_W, 3, Kind.CONSTANT),

    /** Push long or double from run-time constant pool (wide index) */
    LDC2_W(RawBytecodeHelper.LDC2_W, 3, Kind.CONSTANT),

    /** Load int from local variable */
    ILOAD(RawBytecodeHelper.ILOAD, 2, Kind.LOAD),

    /** Load long from local variable */
    LLOAD(RawBytecodeHelper.LLOAD, 2, Kind.LOAD),

    /** Load float from local variable */
    FLOAD(RawBytecodeHelper.FLOAD, 2, Kind.LOAD),

    /** Load double from local variable */
    DLOAD(RawBytecodeHelper.DLOAD, 2, Kind.LOAD),

    /** Load reference from local variable */
    ALOAD(RawBytecodeHelper.ALOAD, 2, Kind.LOAD),

    /** Load int from local variable 0 */
    ILOAD_0(RawBytecodeHelper.ILOAD_0, 1, Kind.LOAD),

    /** Load int from local variable 1 */
    ILOAD_1(RawBytecodeHelper.ILOAD_1, 1, Kind.LOAD),

    /** Load int from local variable 2 */
    ILOAD_2(RawBytecodeHelper.ILOAD_2, 1, Kind.LOAD),

    /** Load int from local variable3  */
    ILOAD_3(RawBytecodeHelper.ILOAD_3, 1, Kind.LOAD),

    /** Load long from local variable 0 */
    LLOAD_0(RawBytecodeHelper.LLOAD_0, 1, Kind.LOAD),

    /** Load long from local variable 1 */
    LLOAD_1(RawBytecodeHelper.LLOAD_1, 1, Kind.LOAD),

    /** Load long from local variable 2 */
    LLOAD_2(RawBytecodeHelper.LLOAD_2, 1, Kind.LOAD),

    /** Load long from local variable 3 */
    LLOAD_3(RawBytecodeHelper.LLOAD_3, 1, Kind.LOAD),

    /** Load float from local variable 0 */
    FLOAD_0(RawBytecodeHelper.FLOAD_0, 1, Kind.LOAD),

    /** Load float from local variable 1 */
    FLOAD_1(RawBytecodeHelper.FLOAD_1, 1, Kind.LOAD),

    /** Load float from local variable 2 */
    FLOAD_2(RawBytecodeHelper.FLOAD_2, 1, Kind.LOAD),

    /** Load float from local variable 3 */
    FLOAD_3(RawBytecodeHelper.FLOAD_3, 1, Kind.LOAD),

    /** Load double from local variable 0 */
    DLOAD_0(RawBytecodeHelper.DLOAD_0, 1, Kind.LOAD),

    /** Load double from local variable 1 */
    DLOAD_1(RawBytecodeHelper.DLOAD_1, 1, Kind.LOAD),

    /** Load double from local variable 2 */
    DLOAD_2(RawBytecodeHelper.DLOAD_2, 1, Kind.LOAD),

    /** Load double from local variable 3 */
    DLOAD_3(RawBytecodeHelper.DLOAD_3, 1, Kind.LOAD),

    /**  Load reference from local variable 0 */
    ALOAD_0(RawBytecodeHelper.ALOAD_0, 1, Kind.LOAD),

    /** Load reference from local variable 1 */
    ALOAD_1(RawBytecodeHelper.ALOAD_1, 1, Kind.LOAD),

    /** Load reference from local variable 2 */
    ALOAD_2(RawBytecodeHelper.ALOAD_2, 1, Kind.LOAD),

    /** Load reference from local variable 3 */
    ALOAD_3(RawBytecodeHelper.ALOAD_3, 1, Kind.LOAD),

    /** Load int from array */
    IALOAD(RawBytecodeHelper.IALOAD, 1, Kind.ARRAY_LOAD),

    /** Load long from array */
    LALOAD(RawBytecodeHelper.LALOAD, 1, Kind.ARRAY_LOAD),

    /** Load float from array */
    FALOAD(RawBytecodeHelper.FALOAD, 1, Kind.ARRAY_LOAD),

    /** Load double from array */
    DALOAD(RawBytecodeHelper.DALOAD, 1, Kind.ARRAY_LOAD),

    /** Load reference from array */
    AALOAD(RawBytecodeHelper.AALOAD, 1, Kind.ARRAY_LOAD),

    /** Load byte from array */
    BALOAD(RawBytecodeHelper.BALOAD, 1, Kind.ARRAY_LOAD),

    /** Load char from array */
    CALOAD(RawBytecodeHelper.CALOAD, 1, Kind.ARRAY_LOAD),

    /** Load short from array */
    SALOAD(RawBytecodeHelper.SALOAD, 1, Kind.ARRAY_LOAD),

    /** Store int into local variable */
    ISTORE(RawBytecodeHelper.ISTORE, 2, Kind.STORE),

    /** Store long into local variable */
    LSTORE(RawBytecodeHelper.LSTORE, 2, Kind.STORE),

    /** Store float into local variable */
    FSTORE(RawBytecodeHelper.FSTORE, 2, Kind.STORE),

    /** Store double into local variable */
    DSTORE(RawBytecodeHelper.DSTORE, 2, Kind.STORE),

    /** Store reference into local variable */
    ASTORE(RawBytecodeHelper.ASTORE, 2, Kind.STORE),

    /** Store int into local variable 0 */
    ISTORE_0(RawBytecodeHelper.ISTORE_0, 1, Kind.STORE),

    /** Store int into local variable 1 */
    ISTORE_1(RawBytecodeHelper.ISTORE_1, 1, Kind.STORE),

    /** Store int into local variable 2 */
    ISTORE_2(RawBytecodeHelper.ISTORE_2, 1, Kind.STORE),

    /** Store int into local variable 3 */
    ISTORE_3(RawBytecodeHelper.ISTORE_3, 1, Kind.STORE),

    /** Store long into local variable 0 */
    LSTORE_0(RawBytecodeHelper.LSTORE_0, 1, Kind.STORE),

    /** Store long into local variable 1 */
    LSTORE_1(RawBytecodeHelper.LSTORE_1, 1, Kind.STORE),

    /** Store long into local variable 2 */
    LSTORE_2(RawBytecodeHelper.LSTORE_2, 1, Kind.STORE),

    /** Store long into local variable 3 */
    LSTORE_3(RawBytecodeHelper.LSTORE_3, 1, Kind.STORE),

    /** Store float into local variable 0 */
    FSTORE_0(RawBytecodeHelper.FSTORE_0, 1, Kind.STORE),

    /** Store float into local variable 1 */
    FSTORE_1(RawBytecodeHelper.FSTORE_1, 1, Kind.STORE),

    /** Store float into local variable 2 */
    FSTORE_2(RawBytecodeHelper.FSTORE_2, 1, Kind.STORE),

    /** Store float into local variable 3 */
    FSTORE_3(RawBytecodeHelper.FSTORE_3, 1, Kind.STORE),

    /** Store double into local variable 0 */
    DSTORE_0(RawBytecodeHelper.DSTORE_0, 1, Kind.STORE),

    /** Store double into local variable 1 */
    DSTORE_1(RawBytecodeHelper.DSTORE_1, 1, Kind.STORE),

    /** Store double into local variable 2 */
    DSTORE_2(RawBytecodeHelper.DSTORE_2, 1, Kind.STORE),

    /** Store double into local variable 3 */
    DSTORE_3(RawBytecodeHelper.DSTORE_3, 1, Kind.STORE),

    /** Store reference into local variable 0 */
    ASTORE_0(RawBytecodeHelper.ASTORE_0, 1, Kind.STORE),

    /** Store reference into local variable 1 */
    ASTORE_1(RawBytecodeHelper.ASTORE_1, 1, Kind.STORE),

    /** Store reference into local variable 2 */
    ASTORE_2(RawBytecodeHelper.ASTORE_2, 1, Kind.STORE),

    /** Store reference into local variable 3 */
    ASTORE_3(RawBytecodeHelper.ASTORE_3, 1, Kind.STORE),

    /** Store into int array */
    IASTORE(RawBytecodeHelper.IASTORE, 1, Kind.ARRAY_STORE),

    /** Store into long array */
    LASTORE(RawBytecodeHelper.LASTORE, 1, Kind.ARRAY_STORE),

    /** Store into float array */
    FASTORE(RawBytecodeHelper.FASTORE, 1, Kind.ARRAY_STORE),

    /** Store into double array */
    DASTORE(RawBytecodeHelper.DASTORE, 1, Kind.ARRAY_STORE),

    /** Store into reference array */
    AASTORE(RawBytecodeHelper.AASTORE, 1, Kind.ARRAY_STORE),

    /** Store into byte array */
    BASTORE(RawBytecodeHelper.BASTORE, 1, Kind.ARRAY_STORE),

    /** Store into char array */
    CASTORE(RawBytecodeHelper.CASTORE, 1, Kind.ARRAY_STORE),

    /** Store into short array */
    SASTORE(RawBytecodeHelper.SASTORE, 1, Kind.ARRAY_STORE),

    /** Pop the top operand stack value */
    POP(RawBytecodeHelper.POP, 1, Kind.STACK),

    /** Pop the top one or two operand stack values */
    POP2(RawBytecodeHelper.POP2, 1, Kind.STACK),

    /** Duplicate the top operand stack value */
    DUP(RawBytecodeHelper.DUP, 1, Kind.STACK),

    /** Duplicate the top operand stack value and insert two values down */
    DUP_X1(RawBytecodeHelper.DUP_X1, 1, Kind.STACK),

    /** Duplicate the top operand stack value and insert two or three values down */
    DUP_X2(RawBytecodeHelper.DUP_X2, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values */
    DUP2(RawBytecodeHelper.DUP2, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values and insert two or three values down */
    DUP2_X1(RawBytecodeHelper.DUP2_X1, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values and insert two, three, or four values down */
    DUP2_X2(RawBytecodeHelper.DUP2_X2, 1, Kind.STACK),

    /** Swap the top two operand stack values */
    SWAP(RawBytecodeHelper.SWAP, 1, Kind.STACK),

    /** Add int */
    IADD(RawBytecodeHelper.IADD, 1, Kind.OPERATOR),

    /** Add long */
    LADD(RawBytecodeHelper.LADD, 1, Kind.OPERATOR),

    /** Add float */
    FADD(RawBytecodeHelper.FADD, 1, Kind.OPERATOR),

    /** Add double */
    DADD(RawBytecodeHelper.DADD, 1, Kind.OPERATOR),

    /** Subtract int */
    ISUB(RawBytecodeHelper.ISUB, 1, Kind.OPERATOR),

    /** Subtract long */
    LSUB(RawBytecodeHelper.LSUB, 1, Kind.OPERATOR),

    /** Subtract float */
    FSUB(RawBytecodeHelper.FSUB, 1, Kind.OPERATOR),

    /** Subtract double */
    DSUB(RawBytecodeHelper.DSUB, 1, Kind.OPERATOR),

    /** Multiply int */
    IMUL(RawBytecodeHelper.IMUL, 1, Kind.OPERATOR),

    /** Multiply long */
    LMUL(RawBytecodeHelper.LMUL, 1, Kind.OPERATOR),

    /** Multiply float */
    FMUL(RawBytecodeHelper.FMUL, 1, Kind.OPERATOR),

    /** Multiply double */
    DMUL(RawBytecodeHelper.DMUL, 1, Kind.OPERATOR),

    /** Divide int */
    IDIV(RawBytecodeHelper.IDIV, 1, Kind.OPERATOR),

    /** Divide long */
    LDIV(RawBytecodeHelper.LDIV, 1, Kind.OPERATOR),

    /** Divide float */
    FDIV(RawBytecodeHelper.FDIV, 1, Kind.OPERATOR),

    /** Divide double */
    DDIV(RawBytecodeHelper.DDIV, 1, Kind.OPERATOR),

    /** Remainder int */
    IREM(RawBytecodeHelper.IREM, 1, Kind.OPERATOR),

    /** Remainder long */
    LREM(RawBytecodeHelper.LREM, 1, Kind.OPERATOR),

    /** Remainder float */
    FREM(RawBytecodeHelper.FREM, 1, Kind.OPERATOR),

    /** Remainder double */
    DREM(RawBytecodeHelper.DREM, 1, Kind.OPERATOR),

    /** Negate int */
    INEG(RawBytecodeHelper.INEG, 1, Kind.OPERATOR),

    /** Negate long */
    LNEG(RawBytecodeHelper.LNEG, 1, Kind.OPERATOR),

    /** Negate float */
    FNEG(RawBytecodeHelper.FNEG, 1, Kind.OPERATOR),

    /** Negate double */
    DNEG(RawBytecodeHelper.DNEG, 1, Kind.OPERATOR),

    /** Shift left int */
    ISHL(RawBytecodeHelper.ISHL, 1, Kind.OPERATOR),

    /** Shift left long */
    LSHL(RawBytecodeHelper.LSHL, 1, Kind.OPERATOR),

    /** Shift right int */
    ISHR(RawBytecodeHelper.ISHR, 1, Kind.OPERATOR),

    /** Shift right long */
    LSHR(RawBytecodeHelper.LSHR, 1, Kind.OPERATOR),

    /** Logical shift right int */
    IUSHR(RawBytecodeHelper.IUSHR, 1, Kind.OPERATOR),

    /** Logical shift right long */
    LUSHR(RawBytecodeHelper.LUSHR, 1, Kind.OPERATOR),

    /** Boolean AND int */
    IAND(RawBytecodeHelper.IAND, 1, Kind.OPERATOR),

    /** Boolean AND long */
    LAND(RawBytecodeHelper.LAND, 1, Kind.OPERATOR),

    /** Boolean OR int */
    IOR(RawBytecodeHelper.IOR, 1, Kind.OPERATOR),

    /** Boolean OR long */
    LOR(RawBytecodeHelper.LOR, 1, Kind.OPERATOR),

    /** Boolean XOR int */
    IXOR(RawBytecodeHelper.IXOR, 1, Kind.OPERATOR),

    /** Boolean XOR long */
    LXOR(RawBytecodeHelper.LXOR, 1, Kind.OPERATOR),

    /** Increment local variable by constant */
    IINC(RawBytecodeHelper.IINC, 3, Kind.INCREMENT),

    /** Convert int to long */
    I2L(RawBytecodeHelper.I2L, 1, Kind.CONVERT),

    /** Convert int to float */
    I2F(RawBytecodeHelper.I2F, 1, Kind.CONVERT),

    /** Convert int to double */
    I2D(RawBytecodeHelper.I2D, 1, Kind.CONVERT),

    /** Convert long to int */
    L2I(RawBytecodeHelper.L2I, 1, Kind.CONVERT),

    /** Convert long to float */
    L2F(RawBytecodeHelper.L2F, 1, Kind.CONVERT),

    /** Convert long to double */
    L2D(RawBytecodeHelper.L2D, 1, Kind.CONVERT),

    /** Convert float to int */
    F2I(RawBytecodeHelper.F2I, 1, Kind.CONVERT),

    /** Convert float to long */
    F2L(RawBytecodeHelper.F2L, 1, Kind.CONVERT),

    /** Convert float to double */
    F2D(RawBytecodeHelper.F2D, 1, Kind.CONVERT),

    /** Convert double to int */
    D2I(RawBytecodeHelper.D2I, 1, Kind.CONVERT),

    /** Convert double to long */
    D2L(RawBytecodeHelper.D2L, 1, Kind.CONVERT),

    /** Convert double to float */
    D2F(RawBytecodeHelper.D2F, 1, Kind.CONVERT),

    /** Convert int to byte */
    I2B(RawBytecodeHelper.I2B, 1, Kind.CONVERT),

    /** Convert int to char */
    I2C(RawBytecodeHelper.I2C, 1, Kind.CONVERT),

    /** Convert int to short */
    I2S(RawBytecodeHelper.I2S, 1, Kind.CONVERT),

    /** Compare long */
    LCMP(RawBytecodeHelper.LCMP, 1, Kind.OPERATOR),

    /** Compare float */
    FCMPL(RawBytecodeHelper.FCMPL, 1, Kind.OPERATOR),

    /** Compare float */
    FCMPG(RawBytecodeHelper.FCMPG, 1, Kind.OPERATOR),

    /** Compare double */
    DCMPL(RawBytecodeHelper.DCMPL, 1, Kind.OPERATOR),

    /** Compare double */
    DCMPG(RawBytecodeHelper.DCMPG, 1, Kind.OPERATOR),

    /** Branch if int comparison with zero succeeds */
    IFEQ(RawBytecodeHelper.IFEQ, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFNE(RawBytecodeHelper.IFNE, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFLT(RawBytecodeHelper.IFLT, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFGE(RawBytecodeHelper.IFGE, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFGT(RawBytecodeHelper.IFGT, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFLE(RawBytecodeHelper.IFLE, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPEQ(RawBytecodeHelper.IF_ICMPEQ, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPNE(RawBytecodeHelper.IF_ICMPNE, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPLT(RawBytecodeHelper.IF_ICMPLT, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPGE(RawBytecodeHelper.IF_ICMPGE, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPGT(RawBytecodeHelper.IF_ICMPGT, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPLE(RawBytecodeHelper.IF_ICMPLE, 3, Kind.BRANCH),

    /** Branch if reference comparison succeeds */
    IF_ACMPEQ(RawBytecodeHelper.IF_ACMPEQ, 3, Kind.BRANCH),

    /** Branch if reference comparison succeeds */
    IF_ACMPNE(RawBytecodeHelper.IF_ACMPNE, 3, Kind.BRANCH),

    /** Branch always */
    GOTO(RawBytecodeHelper.GOTO, 3, Kind.BRANCH),

    /**
     * Jump subroutine is discontinued opcode
     * @see java.lang.classfile.instruction.DiscontinuedInstruction
     */
    JSR(RawBytecodeHelper.JSR, 3, Kind.DISCONTINUED_JSR),

    /**
     * Return from subroutine is discontinued opcode
     * @see java.lang.classfile.instruction.DiscontinuedInstruction
     */
    RET(RawBytecodeHelper.RET, 2, Kind.DISCONTINUED_RET),

    /** Access jump table by index and jump */
    TABLESWITCH(RawBytecodeHelper.TABLESWITCH, -1, Kind.TABLE_SWITCH),

    /** Access jump table by key match and jump */
    LOOKUPSWITCH(RawBytecodeHelper.LOOKUPSWITCH, -1, Kind.LOOKUP_SWITCH),

    /** Return int from method */
    IRETURN(RawBytecodeHelper.IRETURN, 1, Kind.RETURN),

    /** Return long from method */
    LRETURN(RawBytecodeHelper.LRETURN, 1, Kind.RETURN),

    /** Return float from method */
    FRETURN(RawBytecodeHelper.FRETURN, 1, Kind.RETURN),

    /** Return double from method */
    DRETURN(RawBytecodeHelper.DRETURN, 1, Kind.RETURN),

    /** Return reference from method */
    ARETURN(RawBytecodeHelper.ARETURN, 1, Kind.RETURN),

    /** Return void from method */
    RETURN(RawBytecodeHelper.RETURN, 1, Kind.RETURN),

    /** Get static field from class */
    GETSTATIC(RawBytecodeHelper.GETSTATIC, 3, Kind.FIELD_ACCESS),

    /** Set static field in class */
    PUTSTATIC(RawBytecodeHelper.PUTSTATIC, 3, Kind.FIELD_ACCESS),

    /** Fetch field from object */
    GETFIELD(RawBytecodeHelper.GETFIELD, 3, Kind.FIELD_ACCESS),

    /** Set field in object */
    PUTFIELD(RawBytecodeHelper.PUTFIELD, 3, Kind.FIELD_ACCESS),

    /** Invoke instance method; dispatch based on class */
    INVOKEVIRTUAL(RawBytecodeHelper.INVOKEVIRTUAL, 3, Kind.INVOKE),

    /**
     * Invoke instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     */
    INVOKESPECIAL(RawBytecodeHelper.INVOKESPECIAL, 3, Kind.INVOKE),

    /** Invoke a class (static) method */
    INVOKESTATIC(RawBytecodeHelper.INVOKESTATIC, 3, Kind.INVOKE),

    /** Invoke interface method */
    INVOKEINTERFACE(RawBytecodeHelper.INVOKEINTERFACE, 5, Kind.INVOKE),

    /** Invoke a dynamically-computed call site */
    INVOKEDYNAMIC(RawBytecodeHelper.INVOKEDYNAMIC, 5, Kind.INVOKE_DYNAMIC),

    /** Create new object */
    NEW(RawBytecodeHelper.NEW, 3, Kind.NEW_OBJECT),

    /** Create new array */
    NEWARRAY(RawBytecodeHelper.NEWARRAY, 2, Kind.NEW_PRIMITIVE_ARRAY),

    /** Create new array of reference */
    ANEWARRAY(RawBytecodeHelper.ANEWARRAY, 3, Kind.NEW_REF_ARRAY),

    /** Get length of array */
    ARRAYLENGTH(RawBytecodeHelper.ARRAYLENGTH, 1, Kind.OPERATOR),

    /** Throw exception or error */
    ATHROW(RawBytecodeHelper.ATHROW, 1, Kind.THROW_EXCEPTION),

    /** Check whether object is of given type */
    CHECKCAST(RawBytecodeHelper.CHECKCAST, 3, Kind.TYPE_CHECK),

    /** Determine if object is of given type */
    INSTANCEOF(RawBytecodeHelper.INSTANCEOF, 3, Kind.TYPE_CHECK),

    /** Enter monitor for object */
    MONITORENTER(RawBytecodeHelper.MONITORENTER, 1, Kind.MONITOR),

    /** Exit monitor for object */
    MONITOREXIT(RawBytecodeHelper.MONITOREXIT, 1, Kind.MONITOR),

    /** Create new multidimensional array */
    MULTIANEWARRAY(RawBytecodeHelper.MULTIANEWARRAY, 4, Kind.NEW_MULTI_ARRAY),

    /** Branch if reference is null */
    IFNULL(RawBytecodeHelper.IFNULL, 3, Kind.BRANCH),

    /** Branch if reference not null */
    IFNONNULL(RawBytecodeHelper.IFNONNULL, 3, Kind.BRANCH),

    /** Branch always (wide index) */
    GOTO_W(RawBytecodeHelper.GOTO_W, 5, Kind.BRANCH),

    /**
     * Jump subroutine (wide index) is discontinued opcode
     * @see java.lang.classfile.instruction.DiscontinuedInstruction
     */
    JSR_W(RawBytecodeHelper.JSR_W, 5, Kind.DISCONTINUED_JSR),

    /** Load int from local variable (wide index) */
    ILOAD_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.ILOAD, 4, Kind.LOAD),

    /** Load long from local variable (wide index) */
    LLOAD_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.LLOAD, 4, Kind.LOAD),

    /** Load float from local variable (wide index) */
    FLOAD_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.FLOAD, 4, Kind.LOAD),

    /** Load double from local variable (wide index) */
    DLOAD_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.DLOAD, 4, Kind.LOAD),

    /** Load reference from local variable (wide index) */
    ALOAD_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.ALOAD, 4, Kind.LOAD),

    /** Store int into local variable (wide index) */
    ISTORE_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.ISTORE, 4, Kind.STORE),

    /** Store long into local variable (wide index) */
    LSTORE_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.LSTORE, 4, Kind.STORE),

    /** Store float into local variable (wide index) */
    FSTORE_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.FSTORE, 4, Kind.STORE),

    /** Store double into local variable (wide index) */
    DSTORE_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.DSTORE, 4, Kind.STORE),

    /** Store reference into local variable (wide index) */
    ASTORE_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.ASTORE, 4, Kind.STORE),

    /**
     * Return from subroutine (wide index) is discontinued opcode
     * @see java.lang.classfile.instruction.DiscontinuedInstruction
     */
    RET_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.RET, 4, Kind.DISCONTINUED_RET),

    /** Increment local variable by constant (wide index) */
    IINC_W((RawBytecodeHelper.WIDE << 8) | RawBytecodeHelper.IINC, 6, Kind.INCREMENT);

    /**
     * Kinds of opcodes.
     *
     * @since 24
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
         * @see java.lang.classfile.instruction.DiscontinuedInstruction
         */
        DISCONTINUED_JSR,

        /**
         * Discontinued return from subroutine
         *
         * @see Opcode#RET
         * @see Opcode#RET_W
         * @see java.lang.classfile.instruction.DiscontinuedInstruction
         */
        DISCONTINUED_RET;
    }

    private final int bytecode;
    private final int sizeIfFixed;
    private final Kind kind;

    Opcode(int bytecode, int sizeIfFixed, Kind kind) {
        this.bytecode = bytecode;
        this.sizeIfFixed = sizeIfFixed;
        this.kind = kind;
    }

    /**
     * {@return the opcode value} For {@linkplain #isWide() wide} pseudo-opcodes, returns the
     * first 2 bytes of the instruction, which are the wide opcode {@code 196} ({@code 0xC4})
     * and the functional opcode, as a U2 value.
     */
    public int bytecode() { return bytecode; }

    /**
     * {@return true if this is a pseudo-opcode modified by wide opcode}
     *
     * @see #ILOAD_W
     * @see #LLOAD_W
     * @see #FLOAD_W
     * @see #DLOAD_W
     * @see #ALOAD_W
     * @see #ISTORE_W
     * @see #LSTORE_W
     * @see #FSTORE_W
     * @see #DSTORE_W
     * @see #ASTORE_W
     * @see #RET_W
     * @see #IINC_W
     */
    public boolean isWide() { return bytecode > 255; }

    /**
     * {@return size of the instruction in bytes if fixed, or -1 otherwise} This size includes
     * the opcode itself.
     */
    public int sizeIfFixed() { return sizeIfFixed; }

    /**
     * {@return instruction kind}
     */
    public Kind kind() { return kind; }
}
