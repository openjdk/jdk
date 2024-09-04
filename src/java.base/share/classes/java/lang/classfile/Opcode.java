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

import jdk.internal.javac.PreviewFeature;

/**
 * Describes the opcodes of the JVM instruction set, as described in JVMS {@jvms 6.5}.
 * As well as a number of pseudo-instructions that may be encountered when
 * traversing the instructions of a method.
 *
 * @see Instruction
 * @see PseudoInstruction
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public enum Opcode {

    /** Do nothing */
    NOP(OpcodeValues.NOP, 1, Kind.NOP),

    /** Push null */
    ACONST_NULL(OpcodeValues.ACONST_NULL, 1, Kind.CONSTANT),

    /** Push int constant -1 */
    ICONST_M1(OpcodeValues.ICONST_M1, 1, Kind.CONSTANT),

    /** Push int constant 0 */
    ICONST_0(OpcodeValues.ICONST_0, 1, Kind.CONSTANT),

    /** Push int constant 1 */
    ICONST_1(OpcodeValues.ICONST_1, 1, Kind.CONSTANT),

    /** Push int constant 2 */
    ICONST_2(OpcodeValues.ICONST_2, 1, Kind.CONSTANT),

    /** Push int constant 3 */
    ICONST_3(OpcodeValues.ICONST_3, 1, Kind.CONSTANT),

    /** Push int constant 4 */
    ICONST_4(OpcodeValues.ICONST_4, 1, Kind.CONSTANT),

    /** Push int constant 5 */
    ICONST_5(OpcodeValues.ICONST_5, 1, Kind.CONSTANT),

    /** Push long constant 0 */
    LCONST_0(OpcodeValues.LCONST_0, 1, Kind.CONSTANT),

    /** Push long constant  1 */
    LCONST_1(OpcodeValues.LCONST_1, 1, Kind.CONSTANT),

    /** Push float constant 0 */
    FCONST_0(OpcodeValues.FCONST_0, 1, Kind.CONSTANT),

    /** Push float constant 1 */
    FCONST_1(OpcodeValues.FCONST_1, 1, Kind.CONSTANT),

    /** Push float constant 2 */
    FCONST_2(OpcodeValues.FCONST_2, 1, Kind.CONSTANT),

    /** Push double constant 0 */
    DCONST_0(OpcodeValues.DCONST_0, 1, Kind.CONSTANT),

    /** Push double constant 1 */
    DCONST_1(OpcodeValues.DCONST_1, 1, Kind.CONSTANT),

    /** Push byte */
    BIPUSH(OpcodeValues.BIPUSH, 2, Kind.CONSTANT),

    /** Push short */
    SIPUSH(OpcodeValues.SIPUSH, 3, Kind.CONSTANT),

    /** Push item from run-time constant pool */
    LDC(OpcodeValues.LDC, 2, Kind.CONSTANT),

    /** Push item from run-time constant pool (wide index) */
    LDC_W(OpcodeValues.LDC_W, 3, Kind.CONSTANT),

    /** Push long or double from run-time constant pool (wide index) */
    LDC2_W(OpcodeValues.LDC2_W, 3, Kind.CONSTANT),

    /** Load int from local variable */
    ILOAD(OpcodeValues.ILOAD, 2, Kind.LOAD),

    /** Load long from local variable */
    LLOAD(OpcodeValues.LLOAD, 2, Kind.LOAD),

    /** Load float from local variable */
    FLOAD(OpcodeValues.FLOAD, 2, Kind.LOAD),

    /** Load double from local variable */
    DLOAD(OpcodeValues.DLOAD, 2, Kind.LOAD),

    /** Load reference from local variable */
    ALOAD(OpcodeValues.ALOAD, 2, Kind.LOAD),

    /** Load int from local variable 0 */
    ILOAD_0(OpcodeValues.ILOAD_0, 1, Kind.LOAD),

    /** Load int from local variable 1 */
    ILOAD_1(OpcodeValues.ILOAD_1, 1, Kind.LOAD),

    /** Load int from local variable 2 */
    ILOAD_2(OpcodeValues.ILOAD_2, 1, Kind.LOAD),

    /** Load int from local variable3  */
    ILOAD_3(OpcodeValues.ILOAD_3, 1, Kind.LOAD),

    /** Load long from local variable 0 */
    LLOAD_0(OpcodeValues.LLOAD_0, 1, Kind.LOAD),

    /** Load long from local variable 1 */
    LLOAD_1(OpcodeValues.LLOAD_1, 1, Kind.LOAD),

    /** Load long from local variable 2 */
    LLOAD_2(OpcodeValues.LLOAD_2, 1, Kind.LOAD),

    /** Load long from local variable 3 */
    LLOAD_3(OpcodeValues.LLOAD_3, 1, Kind.LOAD),

    /** Load float from local variable 0 */
    FLOAD_0(OpcodeValues.FLOAD_0, 1, Kind.LOAD),

    /** Load float from local variable 1 */
    FLOAD_1(OpcodeValues.FLOAD_1, 1, Kind.LOAD),

    /** Load float from local variable 2 */
    FLOAD_2(OpcodeValues.FLOAD_2, 1, Kind.LOAD),

    /** Load float from local variable 3 */
    FLOAD_3(OpcodeValues.FLOAD_3, 1, Kind.LOAD),

    /** Load double from local variable 0 */
    DLOAD_0(OpcodeValues.DLOAD_0, 1, Kind.LOAD),

    /** Load double from local variable 1 */
    DLOAD_1(OpcodeValues.DLOAD_1, 1, Kind.LOAD),

    /** Load double from local variable 2 */
    DLOAD_2(OpcodeValues.DLOAD_2, 1, Kind.LOAD),

    /** Load double from local variable 3 */
    DLOAD_3(OpcodeValues.DLOAD_3, 1, Kind.LOAD),

    /**  Load reference from local variable 0 */
    ALOAD_0(OpcodeValues.ALOAD_0, 1, Kind.LOAD),

    /** Load reference from local variable 1 */
    ALOAD_1(OpcodeValues.ALOAD_1, 1, Kind.LOAD),

    /** Load reference from local variable 2 */
    ALOAD_2(OpcodeValues.ALOAD_2, 1, Kind.LOAD),

    /** Load reference from local variable 3 */
    ALOAD_3(OpcodeValues.ALOAD_3, 1, Kind.LOAD),

    /** Load int from array */
    IALOAD(OpcodeValues.IALOAD, 1, Kind.ARRAY_LOAD),

    /** Load long from array */
    LALOAD(OpcodeValues.LALOAD, 1, Kind.ARRAY_LOAD),

    /** Load float from array */
    FALOAD(OpcodeValues.FALOAD, 1, Kind.ARRAY_LOAD),

    /** Load double from array */
    DALOAD(OpcodeValues.DALOAD, 1, Kind.ARRAY_LOAD),

    /** Load reference from array */
    AALOAD(OpcodeValues.AALOAD, 1, Kind.ARRAY_LOAD),

    /** Load byte from array */
    BALOAD(OpcodeValues.BALOAD, 1, Kind.ARRAY_LOAD),

    /** Load char from array */
    CALOAD(OpcodeValues.CALOAD, 1, Kind.ARRAY_LOAD),

    /** Load short from array */
    SALOAD(OpcodeValues.SALOAD, 1, Kind.ARRAY_LOAD),

    /** Store int into local variable */
    ISTORE(OpcodeValues.ISTORE, 2, Kind.STORE),

    /** Store long into local variable */
    LSTORE(OpcodeValues.LSTORE, 2, Kind.STORE),

    /** Store float into local variable */
    FSTORE(OpcodeValues.FSTORE, 2, Kind.STORE),

    /** Store double into local variable */
    DSTORE(OpcodeValues.DSTORE, 2, Kind.STORE),

    /** Store reference into local variable */
    ASTORE(OpcodeValues.ASTORE, 2, Kind.STORE),

    /** Store int into local variable 0 */
    ISTORE_0(OpcodeValues.ISTORE_0, 1, Kind.STORE),

    /** Store int into local variable 1 */
    ISTORE_1(OpcodeValues.ISTORE_1, 1, Kind.STORE),

    /** Store int into local variable 2 */
    ISTORE_2(OpcodeValues.ISTORE_2, 1, Kind.STORE),

    /** Store int into local variable 3 */
    ISTORE_3(OpcodeValues.ISTORE_3, 1, Kind.STORE),

    /** Store long into local variable 0 */
    LSTORE_0(OpcodeValues.LSTORE_0, 1, Kind.STORE),

    /** Store long into local variable 1 */
    LSTORE_1(OpcodeValues.LSTORE_1, 1, Kind.STORE),

    /** Store long into local variable 2 */
    LSTORE_2(OpcodeValues.LSTORE_2, 1, Kind.STORE),

    /** Store long into local variable 3 */
    LSTORE_3(OpcodeValues.LSTORE_3, 1, Kind.STORE),

    /** Store float into local variable 0 */
    FSTORE_0(OpcodeValues.FSTORE_0, 1, Kind.STORE),

    /** Store float into local variable 1 */
    FSTORE_1(OpcodeValues.FSTORE_1, 1, Kind.STORE),

    /** Store float into local variable 2 */
    FSTORE_2(OpcodeValues.FSTORE_2, 1, Kind.STORE),

    /** Store float into local variable 3 */
    FSTORE_3(OpcodeValues.FSTORE_3, 1, Kind.STORE),

    /** Store double into local variable 0 */
    DSTORE_0(OpcodeValues.DSTORE_0, 1, Kind.STORE),

    /** Store double into local variable 1 */
    DSTORE_1(OpcodeValues.DSTORE_1, 1, Kind.STORE),

    /** Store double into local variable 2 */
    DSTORE_2(OpcodeValues.DSTORE_2, 1, Kind.STORE),

    /** Store double into local variable 3 */
    DSTORE_3(OpcodeValues.DSTORE_3, 1, Kind.STORE),

    /** Store reference into local variable 0 */
    ASTORE_0(OpcodeValues.ASTORE_0, 1, Kind.STORE),

    /** Store reference into local variable 1 */
    ASTORE_1(OpcodeValues.ASTORE_1, 1, Kind.STORE),

    /** Store reference into local variable 2 */
    ASTORE_2(OpcodeValues.ASTORE_2, 1, Kind.STORE),

    /** Store reference into local variable 3 */
    ASTORE_3(OpcodeValues.ASTORE_3, 1, Kind.STORE),

    /** Store into int array */
    IASTORE(OpcodeValues.IASTORE, 1, Kind.ARRAY_STORE),

    /** Store into long array */
    LASTORE(OpcodeValues.LASTORE, 1, Kind.ARRAY_STORE),

    /** Store into float array */
    FASTORE(OpcodeValues.FASTORE, 1, Kind.ARRAY_STORE),

    /** Store into double array */
    DASTORE(OpcodeValues.DASTORE, 1, Kind.ARRAY_STORE),

    /** Store into reference array */
    AASTORE(OpcodeValues.AASTORE, 1, Kind.ARRAY_STORE),

    /** Store into byte array */
    BASTORE(OpcodeValues.BASTORE, 1, Kind.ARRAY_STORE),

    /** Store into char array */
    CASTORE(OpcodeValues.CASTORE, 1, Kind.ARRAY_STORE),

    /** Store into short array */
    SASTORE(OpcodeValues.SASTORE, 1, Kind.ARRAY_STORE),

    /** Pop the top operand stack value */
    POP(OpcodeValues.POP, 1, Kind.STACK),

    /** Pop the top one or two operand stack values */
    POP2(OpcodeValues.POP2, 1, Kind.STACK),

    /** Duplicate the top operand stack value */
    DUP(OpcodeValues.DUP, 1, Kind.STACK),

    /** Duplicate the top operand stack value and insert two values down */
    DUP_X1(OpcodeValues.DUP_X1, 1, Kind.STACK),

    /** Duplicate the top operand stack value and insert two or three values down */
    DUP_X2(OpcodeValues.DUP_X2, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values */
    DUP2(OpcodeValues.DUP2, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values and insert two or three values down */
    DUP2_X1(OpcodeValues.DUP2_X1, 1, Kind.STACK),

    /** Duplicate the top one or two operand stack values and insert two, three, or four values down */
    DUP2_X2(OpcodeValues.DUP2_X2, 1, Kind.STACK),

    /** Swap the top two operand stack values */
    SWAP(OpcodeValues.SWAP, 1, Kind.STACK),

    /** Add int */
    IADD(OpcodeValues.IADD, 1, Kind.OPERATOR),

    /** Add long */
    LADD(OpcodeValues.LADD, 1, Kind.OPERATOR),

    /** Add float */
    FADD(OpcodeValues.FADD, 1, Kind.OPERATOR),

    /** Add double */
    DADD(OpcodeValues.DADD, 1, Kind.OPERATOR),

    /** Subtract int */
    ISUB(OpcodeValues.ISUB, 1, Kind.OPERATOR),

    /** Subtract long */
    LSUB(OpcodeValues.LSUB, 1, Kind.OPERATOR),

    /** Subtract float */
    FSUB(OpcodeValues.FSUB, 1, Kind.OPERATOR),

    /** Subtract double */
    DSUB(OpcodeValues.DSUB, 1, Kind.OPERATOR),

    /** Multiply int */
    IMUL(OpcodeValues.IMUL, 1, Kind.OPERATOR),

    /** Multiply long */
    LMUL(OpcodeValues.LMUL, 1, Kind.OPERATOR),

    /** Multiply float */
    FMUL(OpcodeValues.FMUL, 1, Kind.OPERATOR),

    /** Multiply double */
    DMUL(OpcodeValues.DMUL, 1, Kind.OPERATOR),

    /** Divide int */
    IDIV(OpcodeValues.IDIV, 1, Kind.OPERATOR),

    /** Divide long */
    LDIV(OpcodeValues.LDIV, 1, Kind.OPERATOR),

    /** Divide float */
    FDIV(OpcodeValues.FDIV, 1, Kind.OPERATOR),

    /** Divide double */
    DDIV(OpcodeValues.DDIV, 1, Kind.OPERATOR),

    /** Remainder int */
    IREM(OpcodeValues.IREM, 1, Kind.OPERATOR),

    /** Remainder long */
    LREM(OpcodeValues.LREM, 1, Kind.OPERATOR),

    /** Remainder float */
    FREM(OpcodeValues.FREM, 1, Kind.OPERATOR),

    /** Remainder double */
    DREM(OpcodeValues.DREM, 1, Kind.OPERATOR),

    /** Negate int */
    INEG(OpcodeValues.INEG, 1, Kind.OPERATOR),

    /** Negate long */
    LNEG(OpcodeValues.LNEG, 1, Kind.OPERATOR),

    /** Negate float */
    FNEG(OpcodeValues.FNEG, 1, Kind.OPERATOR),

    /** Negate double */
    DNEG(OpcodeValues.DNEG, 1, Kind.OPERATOR),

    /** Shift left int */
    ISHL(OpcodeValues.ISHL, 1, Kind.OPERATOR),

    /** Shift left long */
    LSHL(OpcodeValues.LSHL, 1, Kind.OPERATOR),

    /** Shift right int */
    ISHR(OpcodeValues.ISHR, 1, Kind.OPERATOR),

    /** Shift right long */
    LSHR(OpcodeValues.LSHR, 1, Kind.OPERATOR),

    /** Logical shift right int */
    IUSHR(OpcodeValues.IUSHR, 1, Kind.OPERATOR),

    /** Logical shift right long */
    LUSHR(OpcodeValues.LUSHR, 1, Kind.OPERATOR),

    /** Boolean AND int */
    IAND(OpcodeValues.IAND, 1, Kind.OPERATOR),

    /** Boolean AND long */
    LAND(OpcodeValues.LAND, 1, Kind.OPERATOR),

    /** Boolean OR int */
    IOR(OpcodeValues.IOR, 1, Kind.OPERATOR),

    /** Boolean OR long */
    LOR(OpcodeValues.LOR, 1, Kind.OPERATOR),

    /** Boolean XOR int */
    IXOR(OpcodeValues.IXOR, 1, Kind.OPERATOR),

    /** Boolean XOR long */
    LXOR(OpcodeValues.LXOR, 1, Kind.OPERATOR),

    /** Increment local variable by constant */
    IINC(OpcodeValues.IINC, 3, Kind.INCREMENT),

    /** Convert int to long */
    I2L(OpcodeValues.I2L, 1, Kind.CONVERT),

    /** Convert int to float */
    I2F(OpcodeValues.I2F, 1, Kind.CONVERT),

    /** Convert int to double */
    I2D(OpcodeValues.I2D, 1, Kind.CONVERT),

    /** Convert long to int */
    L2I(OpcodeValues.L2I, 1, Kind.CONVERT),

    /** Convert long to float */
    L2F(OpcodeValues.L2F, 1, Kind.CONVERT),

    /** Convert long to double */
    L2D(OpcodeValues.L2D, 1, Kind.CONVERT),

    /** Convert float to int */
    F2I(OpcodeValues.F2I, 1, Kind.CONVERT),

    /** Convert float to long */
    F2L(OpcodeValues.F2L, 1, Kind.CONVERT),

    /** Convert float to double */
    F2D(OpcodeValues.F2D, 1, Kind.CONVERT),

    /** Convert double to int */
    D2I(OpcodeValues.D2I, 1, Kind.CONVERT),

    /** Convert double to long */
    D2L(OpcodeValues.D2L, 1, Kind.CONVERT),

    /** Convert double to float */
    D2F(OpcodeValues.D2F, 1, Kind.CONVERT),

    /** Convert int to byte */
    I2B(OpcodeValues.I2B, 1, Kind.CONVERT),

    /** Convert int to char */
    I2C(OpcodeValues.I2C, 1, Kind.CONVERT),

    /** Convert int to short */
    I2S(OpcodeValues.I2S, 1, Kind.CONVERT),

    /** Compare long */
    LCMP(OpcodeValues.LCMP, 1, Kind.OPERATOR),

    /** Compare float */
    FCMPL(OpcodeValues.FCMPL, 1, Kind.OPERATOR),

    /** Compare float */
    FCMPG(OpcodeValues.FCMPG, 1, Kind.OPERATOR),

    /** Compare double */
    DCMPL(OpcodeValues.DCMPL, 1, Kind.OPERATOR),

    /** Compare double */
    DCMPG(OpcodeValues.DCMPG, 1, Kind.OPERATOR),

    /** Branch if int comparison with zero succeeds */
    IFEQ(OpcodeValues.IFEQ, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFNE(OpcodeValues.IFNE, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFLT(OpcodeValues.IFLT, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFGE(OpcodeValues.IFGE, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFGT(OpcodeValues.IFGT, 3, Kind.BRANCH),

    /** Branch if int comparison with zero succeeds */
    IFLE(OpcodeValues.IFLE, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPEQ(OpcodeValues.IF_ICMPEQ, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPNE(OpcodeValues.IF_ICMPNE, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPLT(OpcodeValues.IF_ICMPLT, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPGE(OpcodeValues.IF_ICMPGE, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPGT(OpcodeValues.IF_ICMPGT, 3, Kind.BRANCH),

    /** Branch if int comparison succeeds */
    IF_ICMPLE(OpcodeValues.IF_ICMPLE, 3, Kind.BRANCH),

    /** Branch if reference comparison succeeds */
    IF_ACMPEQ(OpcodeValues.IF_ACMPEQ, 3, Kind.BRANCH),

    /** Branch if reference comparison succeeds */
    IF_ACMPNE(OpcodeValues.IF_ACMPNE, 3, Kind.BRANCH),

    /** Branch always */
    GOTO(OpcodeValues.GOTO, 3, Kind.BRANCH),

    /**
     * Jump subroutine is discontinued opcode
     * @see java.lang.classfile.instruction.DiscontinuedInstruction
     */
    JSR(OpcodeValues.JSR, 3, Kind.DISCONTINUED_JSR),

    /**
     * Return from subroutine is discontinued opcode
     * @see java.lang.classfile.instruction.DiscontinuedInstruction
     */
    RET(OpcodeValues.RET, 2, Kind.DISCONTINUED_RET),

    /** Access jump table by index and jump */
    TABLESWITCH(OpcodeValues.TABLESWITCH, -1, Kind.TABLE_SWITCH),

    /** Access jump table by key match and jump */
    LOOKUPSWITCH(OpcodeValues.LOOKUPSWITCH, -1, Kind.LOOKUP_SWITCH),

    /** Return int from method */
    IRETURN(OpcodeValues.IRETURN, 1, Kind.RETURN),

    /** Return long from method */
    LRETURN(OpcodeValues.LRETURN, 1, Kind.RETURN),

    /** Return float from method */
    FRETURN(OpcodeValues.FRETURN, 1, Kind.RETURN),

    /** Return double from method */
    DRETURN(OpcodeValues.DRETURN, 1, Kind.RETURN),

    /** Return reference from method */
    ARETURN(OpcodeValues.ARETURN, 1, Kind.RETURN),

    /** Return void from method */
    RETURN(OpcodeValues.RETURN, 1, Kind.RETURN),

    /** Get static field from class */
    GETSTATIC(OpcodeValues.GETSTATIC, 3, Kind.FIELD_ACCESS),

    /** Set static field in class */
    PUTSTATIC(OpcodeValues.PUTSTATIC, 3, Kind.FIELD_ACCESS),

    /** Fetch field from object */
    GETFIELD(OpcodeValues.GETFIELD, 3, Kind.FIELD_ACCESS),

    /** Set field in object */
    PUTFIELD(OpcodeValues.PUTFIELD, 3, Kind.FIELD_ACCESS),

    /** Invoke instance method; dispatch based on class */
    INVOKEVIRTUAL(OpcodeValues.INVOKEVIRTUAL, 3, Kind.INVOKE),

    /**
     * Invoke instance method; direct invocation of instance initialization
     * methods and methods of the current class and its supertypes
     */
    INVOKESPECIAL(OpcodeValues.INVOKESPECIAL, 3, Kind.INVOKE),

    /** Invoke a class (static) method */
    INVOKESTATIC(OpcodeValues.INVOKESTATIC, 3, Kind.INVOKE),

    /** Invoke interface method */
    INVOKEINTERFACE(OpcodeValues.INVOKEINTERFACE, 5, Kind.INVOKE),

    /** Invoke a dynamically-computed call site */
    INVOKEDYNAMIC(OpcodeValues.INVOKEDYNAMIC, 5, Kind.INVOKE_DYNAMIC),

    /** Create new object */
    NEW(OpcodeValues.NEW, 3, Kind.NEW_OBJECT),

    /** Create new array */
    NEWARRAY(OpcodeValues.NEWARRAY, 2, Kind.NEW_PRIMITIVE_ARRAY),

    /** Create new array of reference */
    ANEWARRAY(OpcodeValues.ANEWARRAY, 3, Kind.NEW_REF_ARRAY),

    /** Get length of array */
    ARRAYLENGTH(OpcodeValues.ARRAYLENGTH, 1, Kind.OPERATOR),

    /** Throw exception or error */
    ATHROW(OpcodeValues.ATHROW, 1, Kind.THROW_EXCEPTION),

    /** Check whether object is of given type */
    CHECKCAST(OpcodeValues.CHECKCAST, 3, Kind.TYPE_CHECK),

    /** Determine if object is of given type */
    INSTANCEOF(OpcodeValues.INSTANCEOF, 3, Kind.TYPE_CHECK),

    /** Enter monitor for object */
    MONITORENTER(OpcodeValues.MONITORENTER, 1, Kind.MONITOR),

    /** Exit monitor for object */
    MONITOREXIT(OpcodeValues.MONITOREXIT, 1, Kind.MONITOR),

    /** Create new multidimensional array */
    MULTIANEWARRAY(OpcodeValues.MULTIANEWARRAY, 4, Kind.NEW_MULTI_ARRAY),

    /** Branch if reference is null */
    IFNULL(OpcodeValues.IFNULL, 3, Kind.BRANCH),

    /** Branch if reference not null */
    IFNONNULL(OpcodeValues.IFNONNULL, 3, Kind.BRANCH),

    /** Branch always (wide index) */
    GOTO_W(OpcodeValues.GOTO_W, 5, Kind.BRANCH),

    /**
     * Jump subroutine (wide index) is discontinued opcode
     * @see java.lang.classfile.instruction.DiscontinuedInstruction
     */
    JSR_W(OpcodeValues.JSR_W, 5, Kind.DISCONTINUED_JSR),

    /** Load int from local variable (wide index) */
    ILOAD_W((OpcodeValues.WIDE << 8) | OpcodeValues.ILOAD, 4, Kind.LOAD),

    /** Load long from local variable (wide index) */
    LLOAD_W((OpcodeValues.WIDE << 8) | OpcodeValues.LLOAD, 4, Kind.LOAD),

    /** Load float from local variable (wide index) */
    FLOAD_W((OpcodeValues.WIDE << 8) | OpcodeValues.FLOAD, 4, Kind.LOAD),

    /** Load double from local variable (wide index) */
    DLOAD_W((OpcodeValues.WIDE << 8) | OpcodeValues.DLOAD, 4, Kind.LOAD),

    /** Load reference from local variable (wide index) */
    ALOAD_W((OpcodeValues.WIDE << 8) | OpcodeValues.ALOAD, 4, Kind.LOAD),

    /** Store int into local variable (wide index) */
    ISTORE_W((OpcodeValues.WIDE << 8) | OpcodeValues.ISTORE, 4, Kind.STORE),

    /** Store long into local variable (wide index) */
    LSTORE_W((OpcodeValues.WIDE << 8) | OpcodeValues.LSTORE, 4, Kind.STORE),

    /** Store float into local variable (wide index) */
    FSTORE_W((OpcodeValues.WIDE << 8) | OpcodeValues.FSTORE, 4, Kind.STORE),

    /** Store double into local variable (wide index) */
    DSTORE_W((OpcodeValues.WIDE << 8) | OpcodeValues.DSTORE, 4, Kind.STORE),

    /** Store reference into local variable (wide index) */
    ASTORE_W((OpcodeValues.WIDE << 8) | OpcodeValues.ASTORE, 4, Kind.STORE),

    /**
     * Return from subroutine (wide index) is discontinued opcode
     * @see java.lang.classfile.instruction.DiscontinuedInstruction
     */
    RET_W((OpcodeValues.WIDE << 8) | OpcodeValues.RET, 4, Kind.DISCONTINUED_RET),

    /** Increment local variable by constant (wide index) */
    IINC_W((OpcodeValues.WIDE << 8) | OpcodeValues.IINC, 6, Kind.INCREMENT);

    /**
     * Kinds of opcodes.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
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
     * first 2 bytes of the instruction, which are the {@code wide} opcode and the functional
     * local variable opcode, as a U2 value.
     */
    public int bytecode() { return bytecode; }

    /**
     * {@return true if this is a pseudo-opcode modified by {@code wide}}
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

    /**
     * Holds the constant values of the Opcodes.
     *
     * @since 24
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    public static final class OpcodeValues {

        /** The integer value used to encode the {@link Opcode#NOP nop} instruction. */
        public static final int NOP             = 0;

        /** The integer value used to encode the {@link Opcode#ACONST_NULL aconst_null} instruction. */
        public static final int ACONST_NULL     = 1;

        /** The integer value used to encode the {@link Opcode#ICONST_M1 iconst_m1} instruction. */
        public static final int ICONST_M1       = 2;

        /** The integer value used to encode the {@link Opcode#ICONST_0 iconst_0} instruction. */
        public static final int ICONST_0        = 3;

        /** The integer value used to encode the {@link Opcode#ICONST_1 iconst_1} instruction. */
        public static final int ICONST_1        = 4;

        /** The integer value used to encode the {@link Opcode#ICONST_2 iconst_2} instruction. */
        public static final int ICONST_2        = 5;

        /** The integer value used to encode the {@link Opcode#ICONST_3 iconst_3} instruction. */
        public static final int ICONST_3        = 6;

        /** The integer value used to encode the {@link Opcode#ICONST_4 iconst_4} instruction. */
        public static final int ICONST_4        = 7;

        /** The integer value used to encode the {@link Opcode#ICONST_5 iconst_5} instruction. */
        public static final int ICONST_5        = 8;

        /** The integer value used to encode the {@link Opcode#LCONST_0 lconst_0} instruction. */
        public static final int LCONST_0        = 9;

        /** The integer value used to encode the {@link Opcode#LCONST_1 lconst_1} instruction. */
        public static final int LCONST_1        = 10;

        /** The integer value used to encode the {@link Opcode#FCONST_0 fconst_0} instruction. */
        public static final int FCONST_0        = 11;

        /** The integer value used to encode the {@link Opcode#FCONST_1 fconst_1} instruction. */
        public static final int FCONST_1        = 12;

        /** The integer value used to encode the {@link Opcode#FCONST_2 fconst_2} instruction. */
        public static final int FCONST_2        = 13;

        /** The integer value used to encode the {@link Opcode#DCONST_0 dconst_0} instruction. */
        public static final int DCONST_0        = 14;

        /** The integer value used to encode the {@link Opcode#DCONST_1 dconst_1} instruction. */
        public static final int DCONST_1        = 15;

        /** The integer value used to encode the {@link Opcode#BIPUSH bipush} instruction. */
        public static final int BIPUSH          = 16;

        /** The integer value used to encode the {@link Opcode#SIPUSH sipush} instruction. */
        public static final int SIPUSH          = 17;

        /** The integer value used to encode the {@link Opcode#LDC ldc} instruction. */
        public static final int LDC             = 18;

        /** The integer value used to encode the {@link Opcode#LDC_W ldc_w} instruction. */
        public static final int LDC_W           = 19;

        /** The integer value used to encode the {@link Opcode#LDC2_W ldc2_w} instruction. */
        public static final int LDC2_W          = 20;

        /** The integer value used to encode the {@link Opcode#ILOAD iload} instruction. */
        public static final int ILOAD           = 21;

        /** The integer value used to encode the {@link Opcode#LLOAD lload} instruction. */
        public static final int LLOAD           = 22;

        /** The integer value used to encode the {@link Opcode#FLOAD fload} instruction. */
        public static final int FLOAD           = 23;

        /** The integer value used to encode the {@link Opcode#DLOAD dload} instruction. */
        public static final int DLOAD           = 24;

        /** The integer value used to encode the {@link Opcode#ALOAD aload} instruction. */
        public static final int ALOAD           = 25;

        /** The integer value used to encode the {@link Opcode#ILOAD_0 iload_0} instruction. */
        public static final int ILOAD_0         = 26;

        /** The integer value used to encode the {@link Opcode#ILOAD_1 iload_1} instruction. */
        public static final int ILOAD_1         = 27;

        /** The integer value used to encode the {@link Opcode#ILOAD_2 iload_2} instruction. */
        public static final int ILOAD_2         = 28;

        /** The integer value used to encode the {@link Opcode#ILOAD_3 iload_3} instruction. */
        public static final int ILOAD_3         = 29;

        /** The integer value used to encode the {@link Opcode#LLOAD_0 lload_0} instruction. */
        public static final int LLOAD_0         = 30;

        /** The integer value used to encode the {@link Opcode#LLOAD_1 lload_1} instruction. */
        public static final int LLOAD_1         = 31;

        /** The integer value used to encode the {@link Opcode#LLOAD_2 lload_2} instruction. */
        public static final int LLOAD_2         = 32;

        /** The integer value used to encode the {@link Opcode#LLOAD_3 lload_3} instruction. */
        public static final int LLOAD_3         = 33;

        /** The integer value used to encode the {@link Opcode#FLOAD_0 fload_0} instruction. */
        public static final int FLOAD_0         = 34;

        /** The integer value used to encode the {@link Opcode#FLOAD_1 fload_1} instruction. */
        public static final int FLOAD_1         = 35;

        /** The integer value used to encode the {@link Opcode#FLOAD_2 fload_2} instruction. */
        public static final int FLOAD_2         = 36;

        /** The integer value used to encode the {@link Opcode#FLOAD_3 fload_3} instruction. */
        public static final int FLOAD_3         = 37;

        /** The integer value used to encode the {@link Opcode#DLOAD_0 dload_0} instruction. */
        public static final int DLOAD_0         = 38;

        /** The integer value used to encode the {@link Opcode#DLOAD_1 dload_1} instruction. */
        public static final int DLOAD_1         = 39;

        /** The integer value used to encode the {@link Opcode#DLOAD_2 dload_2} instruction. */
        public static final int DLOAD_2         = 40;

        /** The integer value used to encode the {@link Opcode#DLOAD_3 dload_3} instruction. */
        public static final int DLOAD_3         = 41;

        /** The integer value used to encode the {@link Opcode#ALOAD_0 aload_0} instruction. */
        public static final int ALOAD_0         = 42;

        /** The integer value used to encode the {@link Opcode#ALOAD_1 aload_1} instruction. */
        public static final int ALOAD_1         = 43;

        /** The integer value used to encode the {@link Opcode#ALOAD_2 aload_2} instruction. */
        public static final int ALOAD_2         = 44;

        /** The integer value used to encode the {@link Opcode#ALOAD_3 aload_3} instruction. */
        public static final int ALOAD_3         = 45;

        /** The integer value used to encode the {@link Opcode#IALOAD iaload} instruction. */
        public static final int IALOAD          = 46;

        /** The integer value used to encode the {@link Opcode#LALOAD laload} instruction. */
        public static final int LALOAD          = 47;

        /** The integer value used to encode the {@link Opcode#FALOAD faload} instruction. */
        public static final int FALOAD          = 48;

        /** The integer value used to encode the {@link Opcode#DALOAD daload} instruction. */
        public static final int DALOAD          = 49;

        /** The integer value used to encode the {@link Opcode#AALOAD aaload} instruction. */
        public static final int AALOAD          = 50;

        /** The integer value used to encode the {@link Opcode#BALOAD baload} instruction. */
        public static final int BALOAD          = 51;

        /** The integer value used to encode the {@link Opcode#CALOAD caload} instruction. */
        public static final int CALOAD          = 52;

        /** The integer value used to encode the {@link Opcode#SALOAD saload} instruction. */
        public static final int SALOAD          = 53;

        /** The integer value used to encode the {@link Opcode#ISTORE istore} instruction. */
        public static final int ISTORE          = 54;

        /** The integer value used to encode the {@link Opcode#LSTORE lstore} instruction. */
        public static final int LSTORE          = 55;

        /** The integer value used to encode the {@link Opcode#FSTORE fstore} instruction. */
        public static final int FSTORE          = 56;

        /** The integer value used to encode the {@link Opcode#DSTORE dstore} instruction. */
        public static final int DSTORE          = 57;

        /** The integer value used to encode the {@link Opcode#ASTORE astore} instruction. */
        public static final int ASTORE          = 58;

        /** The integer value used to encode the {@link Opcode#ISTORE_0 istore_0} instruction. */
        public static final int ISTORE_0        = 59;

        /** The integer value used to encode the {@link Opcode#ISTORE_1 istore_1} instruction. */
        public static final int ISTORE_1        = 60;

        /** The integer value used to encode the {@link Opcode#ISTORE_2 istore_2} instruction. */
        public static final int ISTORE_2        = 61;

        /** The integer value used to encode the {@link Opcode#ISTORE_3 istore_3} instruction. */
        public static final int ISTORE_3        = 62;

        /** The integer value used to encode the {@link Opcode#LSTORE_0 lstore_0} instruction. */
        public static final int LSTORE_0        = 63;

        /** The integer value used to encode the {@link Opcode#LSTORE_1 lstore_1} instruction. */
        public static final int LSTORE_1        = 64;

        /** The integer value used to encode the {@link Opcode#LSTORE_2 lstore_2} instruction. */
        public static final int LSTORE_2        = 65;

        /** The integer value used to encode the {@link Opcode#LSTORE_3 lstore_3} instruction. */
        public static final int LSTORE_3        = 66;

        /** The integer value used to encode the {@link Opcode#FSTORE_0 fstore_0} instruction. */
        public static final int FSTORE_0        = 67;

        /** The integer value used to encode the {@link Opcode#FSTORE_1 fstore_1} instruction. */
        public static final int FSTORE_1        = 68;

        /** The integer value used to encode the {@link Opcode#FSTORE_2 fstore_2} instruction. */
        public static final int FSTORE_2        = 69;

        /** The integer value used to encode the {@link Opcode#FSTORE_3 fstore_3} instruction. */
        public static final int FSTORE_3        = 70;

        /** The integer value used to encode the {@link Opcode#DSTORE_0 dstore_0} instruction. */
        public static final int DSTORE_0        = 71;

        /** The integer value used to encode the {@link Opcode#DSTORE_1 dstore_1} instruction. */
        public static final int DSTORE_1        = 72;

        /** The integer value used to encode the {@link Opcode#DSTORE_2 dstore_2} instruction. */
        public static final int DSTORE_2        = 73;

        /** The integer value used to encode the {@link Opcode#DSTORE_3 dstore_3} instruction. */
        public static final int DSTORE_3        = 74;

        /** The integer value used to encode the {@link Opcode#ASTORE_0 astore_0} instruction. */
        public static final int ASTORE_0        = 75;

        /** The integer value used to encode the {@link Opcode#ASTORE_1 astore_1} instruction. */
        public static final int ASTORE_1        = 76;

        /** The integer value used to encode the {@link Opcode#ASTORE_2 astore_2} instruction. */
        public static final int ASTORE_2        = 77;

        /** The integer value used to encode the {@link Opcode#ASTORE_3 astore_3} instruction. */
        public static final int ASTORE_3        = 78;

        /** The integer value used to encode the {@link Opcode#IASTORE iastore} instruction. */
        public static final int IASTORE         = 79;

        /** The integer value used to encode the {@link Opcode#LASTORE lastore} instruction. */
        public static final int LASTORE         = 80;

        /** The integer value used to encode the {@link Opcode#FASTORE fastore} instruction. */
        public static final int FASTORE         = 81;

        /** The integer value used to encode the {@link Opcode#DASTORE dastore} instruction. */
        public static final int DASTORE         = 82;

        /** The integer value used to encode the {@link Opcode#AASTORE aastore} instruction. */
        public static final int AASTORE         = 83;

        /** The integer value used to encode the {@link Opcode#BASTORE bastore} instruction. */
        public static final int BASTORE         = 84;

        /** The integer value used to encode the {@link Opcode#CASTORE castore} instruction. */
        public static final int CASTORE         = 85;

        /** The integer value used to encode the {@link Opcode#SASTORE sastore} instruction. */
        public static final int SASTORE         = 86;

        /** The integer value used to encode the {@link Opcode#POP pop} instruction. */
        public static final int POP             = 87;

        /** The integer value used to encode the {@link Opcode#POP2 pop2} instruction. */
        public static final int POP2            = 88;

        /** The integer value used to encode the {@link Opcode#DUP dup} instruction. */
        public static final int DUP             = 89;

        /** The integer value used to encode the {@link Opcode#DUP_X1 dup_x1} instruction. */
        public static final int DUP_X1          = 90;

        /** The integer value used to encode the {@link Opcode#DUP_X2 dup_x2} instruction. */
        public static final int DUP_X2          = 91;

        /** The integer value used to encode the {@link Opcode#DUP2 dup2} instruction. */
        public static final int DUP2            = 92;

        /** The integer value used to encode the {@link Opcode#DUP2_X1 dup2_x1} instruction. */
        public static final int DUP2_X1         = 93;

        /** The integer value used to encode the {@link Opcode#DUP2_X2 dup2_x2} instruction. */
        public static final int DUP2_X2         = 94;

        /** The integer value used to encode the {@link Opcode#SWAP swap} instruction. */
        public static final int SWAP            = 95;

        /** The integer value used to encode the {@link Opcode#IADD iadd} instruction. */
        public static final int IADD            = 96;

        /** The integer value used to encode the {@link Opcode#LADD ladd} instruction. */
        public static final int LADD            = 97;

        /** The integer value used to encode the {@link Opcode#FADD fadd} instruction. */
        public static final int FADD            = 98;

        /** The integer value used to encode the {@link Opcode#DADD dadd} instruction. */
        public static final int DADD            = 99;

        /** The integer value used to encode the {@link Opcode#ISUB isub} instruction. */
        public static final int ISUB            = 100;

        /** The integer value used to encode the {@link Opcode#LSUB lsub} instruction. */
        public static final int LSUB            = 101;

        /** The integer value used to encode the {@link Opcode#FSUB fsub} instruction. */
        public static final int FSUB            = 102;

        /** The integer value used to encode the {@link Opcode#DSUB dsub} instruction. */
        public static final int DSUB            = 103;

        /** The integer value used to encode the {@link Opcode#IMUL imul} instruction. */
        public static final int IMUL            = 104;

        /** The integer value used to encode the {@link Opcode#LMUL lmul} instruction. */
        public static final int LMUL            = 105;

        /** The integer value used to encode the {@link Opcode#FMUL fmul} instruction. */
        public static final int FMUL            = 106;

        /** The integer value used to encode the {@link Opcode#DMUL dmul} instruction. */
        public static final int DMUL            = 107;

        /** The integer value used to encode the {@link Opcode#IDIV idiv} instruction. */
        public static final int IDIV            = 108;

        /** The integer value used to encode the {@link Opcode#LDIV ldiv} instruction. */
        public static final int LDIV            = 109;

        /** The integer value used to encode the {@link Opcode#FDIV fdiv} instruction. */
        public static final int FDIV            = 110;

        /** The integer value used to encode the {@link Opcode#DDIV ddiv} instruction. */
        public static final int DDIV            = 111;

        /** The integer value used to encode the {@link Opcode#IREM irem} instruction. */
        public static final int IREM            = 112;

        /** The integer value used to encode the {@link Opcode#LREM lrem} instruction. */
        public static final int LREM            = 113;

        /** The integer value used to encode the {@link Opcode#FREM frem} instruction. */
        public static final int FREM            = 114;

        /** The integer value used to encode the {@link Opcode#DREM drem} instruction. */
        public static final int DREM            = 115;

        /** The integer value used to encode the {@link Opcode#INEG ineg} instruction. */
        public static final int INEG            = 116;

        /** The integer value used to encode the {@link Opcode#LNEG lneg} instruction. */
        public static final int LNEG            = 117;

        /** The integer value used to encode the {@link Opcode#FNEG fneg} instruction. */
        public static final int FNEG            = 118;

        /** The integer value used to encode the {@link Opcode#DNEG dneg} instruction. */
        public static final int DNEG            = 119;

        /** The integer value used to encode the {@link Opcode#ISHL ishl} instruction. */
        public static final int ISHL            = 120;

        /** The integer value used to encode the {@link Opcode#LSHL lshl} instruction. */
        public static final int LSHL            = 121;

        /** The integer value used to encode the {@link Opcode#ISHR ishr} instruction. */
        public static final int ISHR            = 122;

        /** The integer value used to encode the {@link Opcode#LSHR lshr} instruction. */
        public static final int LSHR            = 123;

        /** The integer value used to encode the {@link Opcode#IUSHR iushr} instruction. */
        public static final int IUSHR           = 124;

        /** The integer value used to encode the {@link Opcode#LUSHR lushr} instruction. */
        public static final int LUSHR           = 125;

        /** The integer value used to encode the {@link Opcode#IAND iand} instruction. */
        public static final int IAND            = 126;

        /** The integer value used to encode the {@link Opcode#LAND land} instruction. */
        public static final int LAND            = 127;

        /** The integer value used to encode the {@link Opcode#IOR ior} instruction. */
        public static final int IOR             = 128;

        /** The integer value used to encode the {@link Opcode#LOR lor} instruction. */
        public static final int LOR             = 129;

        /** The integer value used to encode the {@link Opcode#IXOR ixor} instruction. */
        public static final int IXOR            = 130;

        /** The integer value used to encode the {@link Opcode#LXOR lxor} instruction. */
        public static final int LXOR            = 131;

        /** The integer value used to encode the {@link Opcode#IINC iinc} instruction. */
        public static final int IINC            = 132;

        /** The integer value used to encode the {@link Opcode#I2L i2l} instruction. */
        public static final int I2L             = 133;

        /** The integer value used to encode the {@link Opcode#I2F i2f} instruction. */
        public static final int I2F             = 134;

        /** The integer value used to encode the {@link Opcode#I2D i2d} instruction. */
        public static final int I2D             = 135;

        /** The integer value used to encode the {@link Opcode#L2I l2i} instruction. */
        public static final int L2I             = 136;

        /** The integer value used to encode the {@link Opcode#L2F l2f} instruction. */
        public static final int L2F             = 137;

        /** The integer value used to encode the {@link Opcode#L2D l2d} instruction. */
        public static final int L2D             = 138;

        /** The integer value used to encode the {@link Opcode#F2I f2i} instruction. */
        public static final int F2I             = 139;

        /** The integer value used to encode the {@link Opcode#F2L f2l} instruction. */
        public static final int F2L             = 140;

        /** The integer value used to encode the {@link Opcode#F2D f2d} instruction. */
        public static final int F2D             = 141;

        /** The integer value used to encode the {@link Opcode#D2I d2i} instruction. */
        public static final int D2I             = 142;

        /** The integer value used to encode the {@link Opcode#D2L d2l} instruction. */
        public static final int D2L             = 143;

        /** The integer value used to encode the {@link Opcode#D2F d2f} instruction. */
        public static final int D2F             = 144;

        /** The integer value used to encode the {@link Opcode#I2B i2b} instruction. */
        public static final int I2B             = 145;

        /** The integer value used to encode the {@link Opcode#I2C i2c} instruction. */
        public static final int I2C             = 146;

        /** The integer value used to encode the {@link Opcode#I2S i2s} instruction. */
        public static final int I2S             = 147;

        /** The integer value used to encode the {@link Opcode#LCMP lcmp} instruction. */
        public static final int LCMP            = 148;

        /** The integer value used to encode the {@link Opcode#FCMPL fcmpl} instruction. */
        public static final int FCMPL           = 149;

        /** The integer value used to encode the {@link Opcode#FCMPG fcmpg} instruction. */
        public static final int FCMPG           = 150;

        /** The integer value used to encode the {@link Opcode#DCMPL dcmpl} instruction. */
        public static final int DCMPL           = 151;

        /** The integer value used to encode the {@link Opcode#DCMPG dcmpg} instruction. */
        public static final int DCMPG           = 152;

        /** The integer value used to encode the {@link Opcode#IFEQ ifeq} instruction. */
        public static final int IFEQ            = 153;

        /** The integer value used to encode the {@link Opcode#IFNE ifne} instruction. */
        public static final int IFNE            = 154;

        /** The integer value used to encode the {@link Opcode#IFLT iflt} instruction. */
        public static final int IFLT            = 155;

        /** The integer value used to encode the {@link Opcode#IFGE ifge} instruction. */
        public static final int IFGE            = 156;

        /** The integer value used to encode the {@link Opcode#IFGT ifgt} instruction. */
        public static final int IFGT            = 157;

        /** The integer value used to encode the {@link Opcode#IFLE ifle} instruction. */
        public static final int IFLE            = 158;

        /** The integer value used to encode the {@link Opcode#IF_ICMPEQ if_icmpeq} instruction. */
        public static final int IF_ICMPEQ       = 159;

        /** The integer value used to encode the {@link Opcode#IF_ICMPNE if_icmpne} instruction. */
        public static final int IF_ICMPNE       = 160;

        /** The integer value used to encode the {@link Opcode#IF_ICMPLT if_icmplt} instruction. */
        public static final int IF_ICMPLT       = 161;

        /** The integer value used to encode the {@link Opcode#IF_ICMPGE if_icmpge} instruction. */
        public static final int IF_ICMPGE       = 162;

        /** The integer value used to encode the {@link Opcode#IF_ICMPGT if_icmpgt} instruction. */
        public static final int IF_ICMPGT       = 163;

        /** The integer value used to encode the {@link Opcode#IF_ICMPLE if_icmple} instruction. */
        public static final int IF_ICMPLE       = 164;

        /** The integer value used to encode the {@link Opcode#IF_ACMPEQ if_acmpeq} instruction. */
        public static final int IF_ACMPEQ       = 165;

        /** The integer value used to encode the {@link Opcode#IF_ACMPNE if_acmpne} instruction. */
        public static final int IF_ACMPNE       = 166;

        /** The integer value used to encode the {@link Opcode#GOTO goto} instruction. */
        public static final int GOTO            = 167;

        /** The integer value used to encode the {@link Opcode#JSR jsr} instruction. */
        public static final int JSR             = 168;

        /** The integer value used to encode the {@link Opcode#RET ret} instruction. */
        public static final int RET             = 169;

        /** The integer value used to encode the {@link Opcode#TABLESWITCH tableswitch} instruction. */
        public static final int TABLESWITCH     = 170;

        /** The integer value used to encode the {@link Opcode#LOOKUPSWITCH lookupswitch} instruction. */
        public static final int LOOKUPSWITCH    = 171;

        /** The integer value used to encode the {@link Opcode#IRETURN ireturn} instruction. */
        public static final int IRETURN         = 172;

        /** The integer value used to encode the {@link Opcode#LRETURN lreturn} instruction. */
        public static final int LRETURN         = 173;

        /** The integer value used to encode the {@link Opcode#FRETURN freturn} instruction. */
        public static final int FRETURN         = 174;

        /** The integer value used to encode the {@link Opcode#DRETURN dreturn} instruction. */
        public static final int DRETURN         = 175;

        /** The integer value used to encode the {@link Opcode#ARETURN areturn} instruction. */
        public static final int ARETURN         = 176;

        /** The integer value used to encode the {@link Opcode#RETURN return} instruction. */
        public static final int RETURN          = 177;

        /** The integer value used to encode the {@link Opcode#GETSTATIC getstatic} instruction. */
        public static final int GETSTATIC       = 178;

        /** The integer value used to encode the {@link Opcode#PUTSTATIC putstatic} instruction. */
        public static final int PUTSTATIC       = 179;

        /** The integer value used to encode the {@link Opcode#GETFIELD getfield} instruction. */
        public static final int GETFIELD        = 180;

        /** The integer value used to encode the {@link Opcode#PUTFIELD putfield} instruction. */
        public static final int PUTFIELD        = 181;

        /** The integer value used to encode the {@link Opcode#INVOKEVIRTUAL invokevirtual} instruction. */
        public static final int INVOKEVIRTUAL   = 182;

        /** The integer value used to encode the {@link Opcode#INVOKESPECIAL invokespecial} instruction. */
        public static final int INVOKESPECIAL   = 183;

        /** The integer value used to encode the {@link Opcode#INVOKESTATIC invokestatic} instruction. */
        public static final int INVOKESTATIC    = 184;

        /** The integer value used to encode the {@link Opcode#INVOKEINTERFACE invokeinterface} instruction. */
        public static final int INVOKEINTERFACE = 185;

        /** The integer value used to encode the {@link Opcode#INVOKEDYNAMIC invokedynamic} instruction. */
        public static final int INVOKEDYNAMIC   = 186;

        /** The integer value used to encode the {@link Opcode#NEW new} instruction. */
        public static final int NEW             = 187;

        /** The integer value used to encode the {@link Opcode#NEWARRAY newarray} instruction. */
        public static final int NEWARRAY        = 188;

        /** The integer value used to encode the {@link Opcode#ANEWARRAY anewarray} instruction. */
        public static final int ANEWARRAY       = 189;

        /** The integer value used to encode the {@link Opcode#ARRAYLENGTH arraylength} instruction. */
        public static final int ARRAYLENGTH     = 190;

        /** The integer value used to encode the {@link Opcode#ATHROW athrow} instruction. */
        public static final int ATHROW          = 191;

        /** The integer value used to encode the {@link Opcode#CHECKCAST checkcast} instruction. */
        public static final int CHECKCAST       = 192;

        /** The integer value used to encode the {@link Opcode#INSTANCEOF instanceof} instruction. */
        public static final int INSTANCEOF      = 193;

        /** The integer value used to encode the {@link Opcode#MONITORENTER monitorenter} instruction. */
        public static final int MONITORENTER    = 194;

        /** The integer value used to encode the {@link Opcode#MONITOREXIT monitorexit} instruction. */
        public static final int MONITOREXIT     = 195;

        /** The integer value used to encode the {@link Opcode#isWide() wide} instruction. */
        public static final int WIDE            = 196;

        /** The integer value used to encode the {@link Opcode#MULTIANEWARRAY multianewarray} instruction. */
        public static final int MULTIANEWARRAY  = 197;

        /** The integer value used to encode the {@link Opcode#IFNULL ifnull} instruction. */
        public static final int IFNULL          = 198;

        /** The integer value used to encode the {@link Opcode#IFNONNULL ifnonnull} instruction. */
        public static final int IFNONNULL       = 199;

        /** The integer value used to encode the {@link Opcode#GOTO_W goto_w} instruction. */
        public static final int GOTO_W          = 200;

        /** The integer value used to encode the {@link Opcode#JSR_W jsr_w} instruction. */
        public static final int JSR_W           = 201;

        private OpcodeValues() {}
    }
}
