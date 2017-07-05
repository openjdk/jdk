/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2007 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.sun.xml.internal.ws.org.objectweb.asm;

/**
 * A visitor to visit a Java method. The methods of this interface must be
 * called in the following order: [ <tt>visitAnnotationDefault</tt> ] (
 * <tt>visitAnnotation</tt> | <tt>visitParameterAnnotation</tt> |
 * <tt>visitAttribute</tt> )* [ <tt>visitCode</tt> ( <tt>visitFrame</tt> |
 * <tt>visit</tt><i>X</i>Insn</tt> | <tt>visitLabel</tt> | <tt>visitTryCatchBlock</tt> |
 * <tt>visitLocalVariable</tt> | <tt>visitLineNumber</tt>)* <tt>visitMaxs</tt> ]
 * <tt>visitEnd</tt>. In addition, the <tt>visit</tt><i>X</i>Insn</tt>
 * and <tt>visitLabel</tt> methods must be called in the sequential order of
 * the bytecode instructions of the visited code, <tt>visitTryCatchBlock</tt>
 * must be called <i>before</i> the labels passed as arguments have been
 * visited, and the <tt>visitLocalVariable</tt> and <tt>visitLineNumber</tt>
 * methods must be called <i>after</i> the labels passed as arguments have been
 * visited.
 *
 * @author Eric Bruneton
 */
public interface MethodVisitor {

    // -------------------------------------------------------------------------
    // Annotations and non standard attributes
    // -------------------------------------------------------------------------

    /**
     * Visits the default value of this annotation interface method.
     *
     * @return a visitor to the visit the actual default value of this
     *         annotation interface method, or <tt>null</tt> if this visitor
     *         is not interested in visiting this default value. The 'name'
     *         parameters passed to the methods of this annotation visitor are
     *         ignored. Moreover, exacly one visit method must be called on this
     *         annotation visitor, followed by visitEnd.
     */
    AnnotationVisitor visitAnnotationDefault();

    /**
     * Visits an annotation of this method.
     *
     * @param desc the class descriptor of the annotation class.
     * @param visible <tt>true</tt> if the annotation is visible at runtime.
     * @return a visitor to visit the annotation values, or <tt>null</tt> if
     *         this visitor is not interested in visiting this annotation.
     */
    AnnotationVisitor visitAnnotation(String desc, boolean visible);

    /**
     * Visits an annotation of a parameter this method.
     *
     * @param parameter the parameter index.
     * @param desc the class descriptor of the annotation class.
     * @param visible <tt>true</tt> if the annotation is visible at runtime.
     * @return a visitor to visit the annotation values, or <tt>null</tt> if
     *         this visitor is not interested in visiting this annotation.
     */
    AnnotationVisitor visitParameterAnnotation(
        int parameter,
        String desc,
        boolean visible);

    /**
     * Visits a non standard attribute of this method.
     *
     * @param attr an attribute.
     */
    void visitAttribute(Attribute attr);

    /**
     * Starts the visit of the method's code, if any (i.e. non abstract method).
     */
    void visitCode();

    /**
     * Visits the current state of the local variables and operand stack
     * elements. This method must(*) be called <i>just before</i> any
     * instruction <b>i</b> that follows an unconditionnal branch instruction
     * such as GOTO or THROW, that is the target of a jump instruction, or that
     * starts an exception handler block. The visited types must describe the
     * values of the local variables and of the operand stack elements <i>just
     * before</i> <b>i</b> is executed. <br> <br> (*) this is mandatory only
     * for classes whose version is greater than or equal to
     * {@link Opcodes#V1_6 V1_6}. <br> <br> Packed frames are basically
     * "deltas" from the state of the previous frame (very first frame is
     * implicitly defined by the method's parameters and access flags): <ul>
     * <li>{@link Opcodes#F_SAME} representing frame with exactly the same
     * locals as the previous frame and with the empty stack.</li> <li>{@link Opcodes#F_SAME1}
     * representing frame with exactly the same locals as the previous frame and
     * with single value on the stack (<code>nStack</code> is 1 and
     * <code>stack[0]</code> contains value for the type of the stack item).</li>
     * <li>{@link Opcodes#F_APPEND} representing frame with current locals are
     * the same as the locals in the previous frame, except that additional
     * locals are defined (<code>nLocal</code> is 1, 2 or 3 and
     * <code>local</code> elements contains values representing added types).</li>
     * <li>{@link Opcodes#F_CHOP} representing frame with current locals are
     * the same as the locals in the previous frame, except that the last 1-3
     * locals are absent and with the empty stack (<code>nLocals</code> is 1,
     * 2 or 3). </li> <li>{@link Opcodes#F_FULL} representing complete frame
     * data.</li> </li> </ul>
     *
     * @param type the type of this stack map frame. Must be
     *        {@link Opcodes#F_NEW} for expanded frames, or
     *        {@link Opcodes#F_FULL}, {@link Opcodes#F_APPEND},
     *        {@link Opcodes#F_CHOP}, {@link Opcodes#F_SAME} or
     *        {@link Opcodes#F_APPEND}, {@link Opcodes#F_SAME1} for compressed
     *        frames.
     * @param nLocal the number of local variables in the visited frame.
     * @param local the local variable types in this frame. This array must not
     *        be modified. Primitive types are represented by
     *        {@link Opcodes#TOP}, {@link Opcodes#INTEGER},
     *        {@link Opcodes#FLOAT}, {@link Opcodes#LONG},
     *        {@link Opcodes#DOUBLE},{@link Opcodes#NULL} or
     *        {@link Opcodes#UNINITIALIZED_THIS} (long and double are
     *        represented by a single element). Reference types are represented
     *        by String objects (representing internal names), and uninitialized
     *        types by Label objects (this label designates the NEW instruction
     *        that created this uninitialized value).
     * @param nStack the number of operand stack elements in the visited frame.
     * @param stack the operand stack types in this frame. This array must not
     *        be modified. Its content has the same format as the "local" array.
     */
    void visitFrame(
        int type,
        int nLocal,
        Object[] local,
        int nStack,
        Object[] stack);

    // -------------------------------------------------------------------------
    // Normal instructions
    // -------------------------------------------------------------------------

    /**
     * Visits a zero operand instruction.
     *
     * @param opcode the opcode of the instruction to be visited. This opcode is
     *        either NOP, ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2,
     *        ICONST_3, ICONST_4, ICONST_5, LCONST_0, LCONST_1, FCONST_0,
     *        FCONST_1, FCONST_2, DCONST_0, DCONST_1, IALOAD, LALOAD, FALOAD,
     *        DALOAD, AALOAD, BALOAD, CALOAD, SALOAD, IASTORE, LASTORE, FASTORE,
     *        DASTORE, AASTORE, BASTORE, CASTORE, SASTORE, POP, POP2, DUP,
     *        DUP_X1, DUP_X2, DUP2, DUP2_X1, DUP2_X2, SWAP, IADD, LADD, FADD,
     *        DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL, FMUL, DMUL, IDIV, LDIV,
     *        FDIV, DDIV, IREM, LREM, FREM, DREM, INEG, LNEG, FNEG, DNEG, ISHL,
     *        LSHL, ISHR, LSHR, IUSHR, LUSHR, IAND, LAND, IOR, LOR, IXOR, LXOR,
     *        I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B,
     *        I2C, I2S, LCMP, FCMPL, FCMPG, DCMPL, DCMPG, IRETURN, LRETURN,
     *        FRETURN, DRETURN, ARETURN, RETURN, ARRAYLENGTH, ATHROW,
     *        MONITORENTER, or MONITOREXIT.
     */
    void visitInsn(int opcode);

    /**
     * Visits an instruction with a single int operand.
     *
     * @param opcode the opcode of the instruction to be visited. This opcode is
     *        either BIPUSH, SIPUSH or NEWARRAY.
     * @param operand the operand of the instruction to be visited.<br> When
     *        opcode is BIPUSH, operand value should be between Byte.MIN_VALUE
     *        and Byte.MAX_VALUE.<br> When opcode is SIPUSH, operand value
     *        should be between Short.MIN_VALUE and Short.MAX_VALUE.<br> When
     *        opcode is NEWARRAY, operand value should be one of
     *        {@link Opcodes#T_BOOLEAN}, {@link Opcodes#T_CHAR},
     *        {@link Opcodes#T_FLOAT}, {@link Opcodes#T_DOUBLE},
     *        {@link Opcodes#T_BYTE}, {@link Opcodes#T_SHORT},
     *        {@link Opcodes#T_INT} or {@link Opcodes#T_LONG}.
     */
    void visitIntInsn(int opcode, int operand);

    /**
     * Visits a local variable instruction. A local variable instruction is an
     * instruction that loads or stores the value of a local variable.
     *
     * @param opcode the opcode of the local variable instruction to be visited.
     *        This opcode is either ILOAD, LLOAD, FLOAD, DLOAD, ALOAD, ISTORE,
     *        LSTORE, FSTORE, DSTORE, ASTORE or RET.
     * @param var the operand of the instruction to be visited. This operand is
     *        the index of a local variable.
     */
    void visitVarInsn(int opcode, int var);

    /**
     * Visits a type instruction. A type instruction is an instruction that
     * takes the internal name of a class as parameter.
     *
     * @param opcode the opcode of the type instruction to be visited. This
     *        opcode is either NEW, ANEWARRAY, CHECKCAST or INSTANCEOF.
     * @param type the operand of the instruction to be visited. This operand
     *        must be the internal name of an object or array class (see {@link
     *        Type#getInternalName() getInternalName}).
     */
    void visitTypeInsn(int opcode, String type);

    /**
     * Visits a field instruction. A field instruction is an instruction that
     * loads or stores the value of a field of an object.
     *
     * @param opcode the opcode of the type instruction to be visited. This
     *        opcode is either GETSTATIC, PUTSTATIC, GETFIELD or PUTFIELD.
     * @param owner the internal name of the field's owner class (see {@link
     *        Type#getInternalName() getInternalName}).
     * @param name the field's name.
     * @param desc the field's descriptor (see {@link Type Type}).
     */
    void visitFieldInsn(int opcode, String owner, String name, String desc);

    /**
     * Visits a method instruction. A method instruction is an instruction that
     * invokes a method.
     *
     * @param opcode the opcode of the type instruction to be visited. This
     *        opcode is either INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or
     *        INVOKEINTERFACE.
     * @param owner the internal name of the method's owner class (see {@link
     *        Type#getInternalName() getInternalName}).
     * @param name the method's name.
     * @param desc the method's descriptor (see {@link Type Type}).
     */
    void visitMethodInsn(int opcode, String owner, String name, String desc);

    /**
     * Visits a jump instruction. A jump instruction is an instruction that may
     * jump to another instruction.
     *
     * @param opcode the opcode of the type instruction to be visited. This
     *        opcode is either IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ,
     *        IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ,
     *        IF_ACMPNE, GOTO, JSR, IFNULL or IFNONNULL.
     * @param label the operand of the instruction to be visited. This operand
     *        is a label that designates the instruction to which the jump
     *        instruction may jump.
     */
    void visitJumpInsn(int opcode, Label label);

    /**
     * Visits a label. A label designates the instruction that will be visited
     * just after it.
     *
     * @param label a {@link Label Label} object.
     */
    void visitLabel(Label label);

    // -------------------------------------------------------------------------
    // Special instructions
    // -------------------------------------------------------------------------

    /**
     * Visits a LDC instruction.
     *
     * @param cst the constant to be loaded on the stack. This parameter must be
     *        a non null {@link Integer}, a {@link Float}, a {@link Long}, a
     *        {@link Double} a {@link String} (or a {@link Type} for
     *        <tt>.class</tt> constants, for classes whose version is 49.0 or
     *        more).
     */
    void visitLdcInsn(Object cst);

    /**
     * Visits an IINC instruction.
     *
     * @param var index of the local variable to be incremented.
     * @param increment amount to increment the local variable by.
     */
    void visitIincInsn(int var, int increment);

    /**
     * Visits a TABLESWITCH instruction.
     *
     * @param min the minimum key value.
     * @param max the maximum key value.
     * @param dflt beginning of the default handler block.
     * @param labels beginnings of the handler blocks. <tt>labels[i]</tt> is
     *        the beginning of the handler block for the <tt>min + i</tt> key.
     */
    void visitTableSwitchInsn(int min, int max, Label dflt, Label[] labels);

    /**
     * Visits a LOOKUPSWITCH instruction.
     *
     * @param dflt beginning of the default handler block.
     * @param keys the values of the keys.
     * @param labels beginnings of the handler blocks. <tt>labels[i]</tt> is
     *        the beginning of the handler block for the <tt>keys[i]</tt> key.
     */
    void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels);

    /**
     * Visits a MULTIANEWARRAY instruction.
     *
     * @param desc an array type descriptor (see {@link Type Type}).
     * @param dims number of dimensions of the array to allocate.
     */
    void visitMultiANewArrayInsn(String desc, int dims);

    // -------------------------------------------------------------------------
    // Exceptions table entries, debug information, max stack and max locals
    // -------------------------------------------------------------------------

    /**
     * Visits a try catch block.
     *
     * @param start beginning of the exception handler's scope (inclusive).
     * @param end end of the exception handler's scope (exclusive).
     * @param handler beginning of the exception handler's code.
     * @param type internal name of the type of exceptions handled by the
     *        handler, or <tt>null</tt> to catch any exceptions (for "finally"
     *        blocks).
     * @throws IllegalArgumentException if one of the labels has already been
     *         visited by this visitor (by the {@link #visitLabel visitLabel}
     *         method).
     */
    void visitTryCatchBlock(Label start, Label end, Label handler, String type);

    /**
     * Visits a local variable declaration.
     *
     * @param name the name of a local variable.
     * @param desc the type descriptor of this local variable.
     * @param signature the type signature of this local variable. May be
     *        <tt>null</tt> if the local variable type does not use generic
     *        types.
     * @param start the first instruction corresponding to the scope of this
     *        local variable (inclusive).
     * @param end the last instruction corresponding to the scope of this local
     *        variable (exclusive).
     * @param index the local variable's index.
     * @throws IllegalArgumentException if one of the labels has not already
     *         been visited by this visitor (by the
     *         {@link #visitLabel visitLabel} method).
     */
    void visitLocalVariable(
        String name,
        String desc,
        String signature,
        Label start,
        Label end,
        int index);

    /**
     * Visits a line number declaration.
     *
     * @param line a line number. This number refers to the source file from
     *        which the class was compiled.
     * @param start the first instruction corresponding to this line number.
     * @throws IllegalArgumentException if <tt>start</tt> has not already been
     *         visited by this visitor (by the {@link #visitLabel visitLabel}
     *         method).
     */
    void visitLineNumber(int line, Label start);

    /**
     * Visits the maximum stack size and the maximum number of local variables
     * of the method.
     *
     * @param maxStack maximum stack size of the method.
     * @param maxLocals maximum number of local variables for the method.
     */
    void visitMaxs(int maxStack, int maxLocals);

    /**
     * Visits the end of the method. This method, which is the last one to be
     * called, is used to inform the visitor that all the annotations and
     * attributes of the method have been visited.
     */
    void visitEnd();
}
