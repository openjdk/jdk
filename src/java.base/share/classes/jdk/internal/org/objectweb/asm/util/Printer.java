/*
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
 * Copyright (c) 2000-2011 INRIA, France Telecom
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
package jdk.internal.org.objectweb.asm.util;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.TypePath;

/**
 * An abstract converter from visit events to text.
 *
 * @author Eric Bruneton
 */
public abstract class Printer {

    /**
     * The names of the Java Virtual Machine opcodes.
     */
    public static final String[] OPCODES;

    /**
     * The names of the for <code>operand</code> parameter values of the
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitIntInsn} method when
     * <code>opcode</code> is <code>NEWARRAY</code>.
     */
    public static final String[] TYPES;

    /**
     * The names of the <code>tag</code> field values for
     * {@link jdk.internal.org.objectweb.asm.Handle}.
     */
    public static final String[] HANDLE_TAG;

    static {
        String s = "NOP,ACONST_NULL,ICONST_M1,ICONST_0,ICONST_1,ICONST_2,"
                + "ICONST_3,ICONST_4,ICONST_5,LCONST_0,LCONST_1,FCONST_0,"
                + "FCONST_1,FCONST_2,DCONST_0,DCONST_1,BIPUSH,SIPUSH,LDC,,,"
                + "ILOAD,LLOAD,FLOAD,DLOAD,ALOAD,,,,,,,,,,,,,,,,,,,,,IALOAD,"
                + "LALOAD,FALOAD,DALOAD,AALOAD,BALOAD,CALOAD,SALOAD,ISTORE,"
                + "LSTORE,FSTORE,DSTORE,ASTORE,,,,,,,,,,,,,,,,,,,,,IASTORE,"
                + "LASTORE,FASTORE,DASTORE,AASTORE,BASTORE,CASTORE,SASTORE,POP,"
                + "POP2,DUP,DUP_X1,DUP_X2,DUP2,DUP2_X1,DUP2_X2,SWAP,IADD,LADD,"
                + "FADD,DADD,ISUB,LSUB,FSUB,DSUB,IMUL,LMUL,FMUL,DMUL,IDIV,LDIV,"
                + "FDIV,DDIV,IREM,LREM,FREM,DREM,INEG,LNEG,FNEG,DNEG,ISHL,LSHL,"
                + "ISHR,LSHR,IUSHR,LUSHR,IAND,LAND,IOR,LOR,IXOR,LXOR,IINC,I2L,"
                + "I2F,I2D,L2I,L2F,L2D,F2I,F2L,F2D,D2I,D2L,D2F,I2B,I2C,I2S,LCMP,"
                + "FCMPL,FCMPG,DCMPL,DCMPG,IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE,"
                + "IF_ICMPEQ,IF_ICMPNE,IF_ICMPLT,IF_ICMPGE,IF_ICMPGT,IF_ICMPLE,"
                + "IF_ACMPEQ,IF_ACMPNE,GOTO,JSR,RET,TABLESWITCH,LOOKUPSWITCH,"
                + "IRETURN,LRETURN,FRETURN,DRETURN,ARETURN,RETURN,GETSTATIC,"
                + "PUTSTATIC,GETFIELD,PUTFIELD,INVOKEVIRTUAL,INVOKESPECIAL,"
                + "INVOKESTATIC,INVOKEINTERFACE,INVOKEDYNAMIC,NEW,NEWARRAY,"
                + "ANEWARRAY,ARRAYLENGTH,ATHROW,CHECKCAST,INSTANCEOF,"
                + "MONITORENTER,MONITOREXIT,,MULTIANEWARRAY,IFNULL,IFNONNULL,";
        OPCODES = new String[200];
        int i = 0;
        int j = 0;
        int l;
        while ((l = s.indexOf(',', j)) > 0) {
            OPCODES[i++] = j + 1 == l ? null : s.substring(j, l);
            j = l + 1;
        }

        s = "T_BOOLEAN,T_CHAR,T_FLOAT,T_DOUBLE,T_BYTE,T_SHORT,T_INT,T_LONG,";
        TYPES = new String[12];
        j = 0;
        i = 4;
        while ((l = s.indexOf(',', j)) > 0) {
            TYPES[i++] = s.substring(j, l);
            j = l + 1;
        }

        s = "H_GETFIELD,H_GETSTATIC,H_PUTFIELD,H_PUTSTATIC,"
                + "H_INVOKEVIRTUAL,H_INVOKESTATIC,H_INVOKESPECIAL,"
                + "H_NEWINVOKESPECIAL,H_INVOKEINTERFACE,";
        HANDLE_TAG = new String[10];
        j = 0;
        i = 1;
        while ((l = s.indexOf(',', j)) > 0) {
            HANDLE_TAG[i++] = s.substring(j, l);
            j = l + 1;
        }
    }

    /**
     * The ASM API version implemented by this class. The value of this field
     * must be one of {@link Opcodes#ASM4}, {@link Opcodes#ASM5} or {@link Opcodes#ASM6}.
     */
    protected final int api;

    /**
     * A buffer that can be used to create strings.
     */
    protected final StringBuffer buf;

    /**
     * The text to be printed. Since the code of methods is not necessarily
     * visited in sequential order, one method after the other, but can be
     * interlaced (some instructions from method one, then some instructions
     * from method two, then some instructions from method one again...), it is
     * not possible to print the visited instructions directly to a sequential
     * stream. A class is therefore printed in a two steps process: a string
     * tree is constructed during the visit, and printed to a sequential stream
     * at the end of the visit. This string tree is stored in this field, as a
     * string list that can contain other string lists, which can themselves
     * contain other string lists, and so on.
     */
    public final List<Object> text;

    /**
     * Constructs a new {@link Printer}.
     *
     * @param api
     *            the ASM API version implemented by this printer. Must be one
     *            of {@link Opcodes#ASM4}, {@link Opcodes#ASM5} or {@link Opcodes#ASM6}.
     */
    protected Printer(final int api) {
        this.api = api;
        this.buf = new StringBuffer();
        this.text = new ArrayList<Object>();
    }

    /**
     * Class header.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visit}.
     *
     * @param version
     *            the class version.
     * @param access
     *            the class's access flags (see {@link Opcodes}). This parameter
     *            also indicates if the class is deprecated.
     * @param name
     *            the internal name of the class (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     * @param signature
     *            the signature of this class. May be <tt>null</tt> if the class
     *            is not a generic one, and does not extend or implement generic
     *            classes or interfaces.
     * @param superName
     *            the internal of name of the super class (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     *            For interfaces, the super class is {@link Object}. May be
     *            <tt>null</tt>, but only for the {@link Object} class.
     * @param interfaces
     *            the internal names of the class's interfaces (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     *            May be <tt>null</tt>.
     */
    public abstract void visit(final int version, final int access,
            final String name, final String signature, final String superName,
            final String[] interfaces);

    /**
     * Class source.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitSource}.
     *
     * @param source
     *            the name of the source file from which the class was compiled.
     *            May be <tt>null</tt>.
     * @param debug
     *            additional debug information to compute the correspondance
     *            between source and compiled elements of the class. May be
     *            <tt>null</tt>.
     */
    public abstract void visitSource(final String source, final String debug);


    /**
     * Module.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitModule(String, int)}.
     *
     * @param name
     *            module name.
     * @param access
     *            module flags, among {@code ACC_OPEN}, {@code ACC_SYNTHETIC}
     *            and {@code ACC_MANDATED}.
     * @param version
     *            module version or null.
     * @return
     */
    public Printer visitModule(String name, int access, String version) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Class outer class.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitOuterClass}.
     *
     * Visits the enclosing class of the class. This method must be called only
     * if the class has an enclosing class.
     *
     * @param owner
     *            internal name of the enclosing class of the class.
     * @param name
     *            the name of the method that contains the class, or
     *            <tt>null</tt> if the class is not enclosed in a method of its
     *            enclosing class.
     * @param desc
     *            the descriptor of the method that contains the class, or
     *            <tt>null</tt> if the class is not enclosed in a method of its
     *            enclosing class.
     */
    public abstract void visitOuterClass(final String owner, final String name,
            final String desc);

    /**
     * Class annotation.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitAnnotation}.
     *
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public abstract Printer visitClassAnnotation(final String desc,
            final boolean visible);

    /**
     * Class type annotation.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitTypeAnnotation}.
     *
     * @param typeRef
     *            a reference to the annotated type. The sort of this type
     *            reference must be
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#CLASS_TYPE_PARAMETER CLASS_TYPE_PARAMETER},
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#CLASS_TYPE_PARAMETER_BOUND CLASS_TYPE_PARAMETER_BOUND}
     *            or {@link jdk.internal.org.objectweb.asm.TypeReference#CLASS_EXTENDS CLASS_EXTENDS}.
     *            See {@link jdk.internal.org.objectweb.asm.TypeReference}.
     * @param typePath
     *            the path to the annotated type argument, wildcard bound, array
     *            element type, or static inner type within 'typeRef'. May be
     *            <tt>null</tt> if the annotation targets 'typeRef' as a whole.
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public Printer visitClassTypeAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Class attribute.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitAttribute}.
     *
     * @param attr
     *            an attribute.
     */
    public abstract void visitClassAttribute(final Attribute attr);

    /**
     * Class inner name.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitInnerClass}.
     *
     * @param name
     *            the internal name of an inner class (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     * @param outerName
     *            the internal name of the class to which the inner class
     *            belongs (see {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     *            May be <tt>null</tt> for not member classes.
     * @param innerName
     *            the (simple) name of the inner class inside its enclosing
     *            class. May be <tt>null</tt> for anonymous inner classes.
     * @param access
     *            the access flags of the inner class as originally declared in
     *            the enclosing class.
     */
    public abstract void visitInnerClass(final String name,
            final String outerName, final String innerName, final int access);

    /**
     * Class field.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitField}.
     *
     * @param access
     *            the field's access flags (see {@link Opcodes}). This parameter
     *            also indicates if the field is synthetic and/or deprecated.
     * @param name
     *            the field's name.
     * @param desc
     *            the field's descriptor (see {@link jdk.internal.org.objectweb.asm.Type Type}).
     * @param signature
     *            the field's signature. May be <tt>null</tt> if the field's
     *            type does not use generic types.
     * @param value
     *            the field's initial value. This parameter, which may be
     *            <tt>null</tt> if the field does not have an initial value,
     *            must be an {@link Integer}, a {@link Float}, a {@link Long}, a
     *            {@link Double} or a {@link String} (for <tt>int</tt>,
     *            <tt>float</tt>, <tt>long</tt> or <tt>String</tt> fields
     *            respectively). <i>This parameter is only used for static
     *            fields</i>. Its value is ignored for non static fields, which
     *            must be initialized through bytecode instructions in
     *            constructors or methods.
     * @return the printer
     */
    public abstract Printer visitField(final int access, final String name,
            final String desc, final String signature, final Object value);

    /**
     * Class method.
     * See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitMethod}.
     *
     * @param access
     *            the method's access flags (see {@link Opcodes}). This
     *            parameter also indicates if the method is synthetic and/or
     *            deprecated.
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link jdk.internal.org.objectweb.asm.Type Type}).
     * @param signature
     *            the method's signature. May be <tt>null</tt> if the method
     *            parameters, return type and exceptions do not use generic
     *            types.
     * @param exceptions
     *            the internal names of the method's exception classes (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}). May be
     *            <tt>null</tt>.
     * @return the printer
     */
    public abstract Printer visitMethod(final int access, final String name,
            final String desc, final String signature, final String[] exceptions);

    /**
     * Class end. See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitEnd}.
     */
    public abstract void visitClassEnd();

    // ------------------------------------------------------------------------
    // Module
    // ------------------------------------------------------------------------

    public void visitMainClass(String mainClass) {
        throw new RuntimeException("Must be overriden");
    }

    public void visitPackage(String packaze) {
        throw new RuntimeException("Must be overriden");
    }

    public void visitRequire(String module, int access, String version) {
        throw new RuntimeException("Must be overriden");
    }

    public void visitExport(String packaze, int access, String... modules) {
        throw new RuntimeException("Must be overriden");
    }

    public void visitOpen(String packaze, int access, String... modules) {
        throw new RuntimeException("Must be overriden");
    }

    public void visitUse(String service) {
        throw new RuntimeException("Must be overriden");
    }

    public void visitProvide(String service, String... providers) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Module end. See {@link jdk.internal.org.objectweb.asm.ModuleVisitor#visitEnd}.
     */
    public void visitModuleEnd() {
        throw new RuntimeException("Must be overriden");
    }

    // ------------------------------------------------------------------------
    // Annotations
    // ------------------------------------------------------------------------

    /**
     * Annotation value.
     * See {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visit}.
     *
     * @param name
     *            the value name.
     * @param value
     *            the actual value, whose type must be {@link Byte},
     *            {@link Boolean}, {@link Character}, {@link Short},
     *            {@link Integer} , {@link Long}, {@link Float}, {@link Double},
     *            {@link String} or {@link jdk.internal.org.objectweb.asm.Type}
     *            or OBJECT or ARRAY sort.
     *            This value can also be an array of byte, boolean, short, char, int,
     *            long, float or double values (this is equivalent to using
     *            {@link #visitArray visitArray} and visiting each array element
     *            in turn, but is more convenient).
     */
    public abstract void visit(final String name, final Object value);

    /**
     * Annotation enum value.
     * See {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visitEnum}.
     *
     * Visits an enumeration value of the annotation.
     *
     * @param name
     *            the value name.
     * @param desc
     *            the class descriptor of the enumeration class.
     * @param value
     *            the actual enumeration value.
     */
    public abstract void visitEnum(final String name, final String desc,
            final String value);

    /**
     * Nested annotation value.
     * See {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visitAnnotation}.
     *
     * @param name
     *            the value name.
     * @param desc
     *            the class descriptor of the nested annotation class.
     * @return the printer
     */
    public abstract Printer visitAnnotation(final String name, final String desc);

    /**
     * Annotation array value.
     * See {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visitArray}.
     *
     * Visits an array value of the annotation. Note that arrays of primitive
     * types (such as byte, boolean, short, char, int, long, float or double)
     * can be passed as value to {@link #visit visit}. This is what
     * {@link jdk.internal.org.objectweb.asm.ClassReader} does.
     *
     * @param name
     *            the value name.
     * @return the printer
     */
    public abstract Printer visitArray(final String name);

    /**
     * Annotation end. See {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visitEnd}.
     */
    public abstract void visitAnnotationEnd();

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /**
     * Field annotation.
     * See {@link jdk.internal.org.objectweb.asm.FieldVisitor#visitAnnotation}.
     *
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public abstract Printer visitFieldAnnotation(final String desc,
            final boolean visible);

    /**
     * Field type annotation.
     * See {@link jdk.internal.org.objectweb.asm.FieldVisitor#visitTypeAnnotation}.
     *
     * @param typeRef
     *            a reference to the annotated type. The sort of this type
     *            reference must be {@link jdk.internal.org.objectweb.asm.TypeReference#FIELD FIELD}.
     *            See {@link jdk.internal.org.objectweb.asm.TypeReference}.
     * @param typePath
     *            the path to the annotated type argument, wildcard bound, array
     *            element type, or static inner type within 'typeRef'. May be
     *            <tt>null</tt> if the annotation targets 'typeRef' as a whole.
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public Printer visitFieldTypeAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Field attribute.
     * See {@link jdk.internal.org.objectweb.asm.FieldVisitor#visitAttribute}.
     *
     * @param attr
     *            an attribute.
     */
    public abstract void visitFieldAttribute(final Attribute attr);

    /**
     * Field end.
     * See {@link jdk.internal.org.objectweb.asm.FieldVisitor#visitEnd}.
     */
    public abstract void visitFieldEnd();

    // ------------------------------------------------------------------------
    // Methods
    // ------------------------------------------------------------------------

    /**
     * Method parameter.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitParameter(String, int)}.
     *
     * @param name
     *            parameter name or null if none is provided.
     * @param access
     *            the parameter's access flags, only <tt>ACC_FINAL</tt>,
     *            <tt>ACC_SYNTHETIC</tt> or/and <tt>ACC_MANDATED</tt> are
     *            allowed (see {@link Opcodes}).
     */
    public void visitParameter(String name, int access) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method default annotation.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitAnnotationDefault}.
     *
     * @return the printer
     */
    public abstract Printer visitAnnotationDefault();

    /**
     * Method annotation.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitAnnotation}.
     *
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public abstract Printer visitMethodAnnotation(final String desc,
            final boolean visible);

    /**
     * Method type annotation.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTypeAnnotation}.
     *
     * @param typeRef
     *            a reference to the annotated type. The sort of this type
     *            reference must be {@link jdk.internal.org.objectweb.asm.TypeReference#FIELD FIELD}.
     *            See {@link jdk.internal.org.objectweb.asm.TypeReference}.
     * @param typePath
     *            the path to the annotated type argument, wildcard bound, array
     *            element type, or static inner type within 'typeRef'. May be
     *            <tt>null</tt> if the annotation targets 'typeRef' as a whole.
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public Printer visitMethodTypeAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method parameter annotation.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitParameterAnnotation}.
     *
     * @param parameter
     *            the parameter index.
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public abstract Printer visitParameterAnnotation(final int parameter,
            final String desc, final boolean visible);

    /**
     * Method attribute.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitAttribute}.
     *
     * @param attr
     *            an attribute.
     */
    public abstract void visitMethodAttribute(final Attribute attr);

    /**
     * Method start.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitCode}.
     */
    public abstract void visitCode();

    /**
     * Method stack frame.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitFrame}.
     *
     * Visits the current state of the local variables and operand stack
     * elements. This method must(*) be called <i>just before</i> any
     * instruction <b>i</b> that follows an unconditional branch instruction
     * such as GOTO or THROW, that is the target of a jump instruction, or that
     * starts an exception handler block. The visited types must describe the
     * values of the local variables and of the operand stack elements <i>just
     * before</i> <b>i</b> is executed.<br>
     * <br>
     * (*) this is mandatory only for classes whose version is greater than or
     * equal to {@link Opcodes#V1_6 V1_6}. <br>
     * <br>
     * The frames of a method must be given either in expanded form, or in
     * compressed form (all frames must use the same format, i.e. you must not
     * mix expanded and compressed frames within a single method):
     * <ul>
     * <li>In expanded form, all frames must have the F_NEW type.</li>
     * <li>In compressed form, frames are basically "deltas" from the state of
     * the previous frame:
     * <ul>
     * <li>{@link Opcodes#F_SAME} representing frame with exactly the same
     * locals as the previous frame and with the empty stack.</li>
     * <li>{@link Opcodes#F_SAME1} representing frame with exactly the same
     * locals as the previous frame and with single value on the stack (
     * <code>nStack</code> is 1 and <code>stack[0]</code> contains value for the
     * type of the stack item).</li>
     * <li>{@link Opcodes#F_APPEND} representing frame with current locals are
     * the same as the locals in the previous frame, except that additional
     * locals are defined (<code>nLocal</code> is 1, 2 or 3 and
     * <code>local</code> elements contains values representing added types).</li>
     * <li>{@link Opcodes#F_CHOP} representing frame with current locals are the
     * same as the locals in the previous frame, except that the last 1-3 locals
     * are absent and with the empty stack (<code>nLocals</code> is 1, 2 or 3).</li>
     * <li>{@link Opcodes#F_FULL} representing complete frame data.</li>
     * </ul>
     * </li>
     * </ul>
     * <br>
     * In both cases the first frame, corresponding to the method's parameters
     * and access flags, is implicit and must not be visited. Also, it is
     * illegal to visit two or more frames for the same code location (i.e., at
     * least one instruction must be visited between two calls to visitFrame).
     *
     * @param type
     *            the type of this stack map frame. Must be
     *            {@link Opcodes#F_NEW} for expanded frames, or
     *            {@link Opcodes#F_FULL}, {@link Opcodes#F_APPEND},
     *            {@link Opcodes#F_CHOP}, {@link Opcodes#F_SAME} or
     *            {@link Opcodes#F_APPEND}, {@link Opcodes#F_SAME1} for
     *            compressed frames.
     * @param nLocal
     *            the number of local variables in the visited frame.
     * @param local
     *            the local variable types in this frame. This array must not be
     *            modified. Primitive types are represented by
     *            {@link Opcodes#TOP}, {@link Opcodes#INTEGER},
     *            {@link Opcodes#FLOAT}, {@link Opcodes#LONG},
     *            {@link Opcodes#DOUBLE},{@link Opcodes#NULL} or
     *            {@link Opcodes#UNINITIALIZED_THIS} (long and double are
     *            represented by a single element). Reference types are
     *            represented by String objects (representing internal names),
     *            and uninitialized types by Label objects (this label
     *            designates the NEW instruction that created this uninitialized
     *            value).
     * @param nStack
     *            the number of operand stack elements in the visited frame.
     * @param stack
     *            the operand stack types in this frame. This array must not be
     *            modified. Its content has the same format as the "local"
     *            array.
     * @throws IllegalStateException
     *             if a frame is visited just after another one, without any
     *             instruction between the two (unless this frame is a
     *             Opcodes#F_SAME frame, in which case it is silently ignored).
     */
    public abstract void visitFrame(final int type, final int nLocal,
            final Object[] local, final int nStack, final Object[] stack);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitInsn}
     *
     * @param opcode
     *            the opcode of the instruction to be visited. This opcode is
     *            either NOP, ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1,
     *            ICONST_2, ICONST_3, ICONST_4, ICONST_5, LCONST_0, LCONST_1,
     *            FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1, IALOAD,
     *            LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD,
     *            IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE,
     *            SASTORE, POP, POP2, DUP, DUP_X1, DUP_X2, DUP2, DUP2_X1,
     *            DUP2_X2, SWAP, IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB,
     *            IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV, IREM, LREM,
     *            FREM, DREM, INEG, LNEG, FNEG, DNEG, ISHL, LSHL, ISHR, LSHR,
     *            IUSHR, LUSHR, IAND, LAND, IOR, LOR, IXOR, LXOR, I2L, I2F, I2D,
     *            L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C, I2S,
     *            LCMP, FCMPL, FCMPG, DCMPL, DCMPG, IRETURN, LRETURN, FRETURN,
     *            DRETURN, ARETURN, RETURN, ARRAYLENGTH, ATHROW, MONITORENTER,
     *            or MONITOREXIT.
     */
    public abstract void visitInsn(final int opcode);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitIntInsn}.
     *
     * @param opcode
     *            the opcode of the instruction to be visited. This opcode is
     *            either BIPUSH, SIPUSH or NEWARRAY.
     * @param operand
     *            the operand of the instruction to be visited.<br>
     *            When opcode is BIPUSH, operand value should be between
     *            Byte.MIN_VALUE and Byte.MAX_VALUE.<br>
     *            When opcode is SIPUSH, operand value should be between
     *            Short.MIN_VALUE and Short.MAX_VALUE.<br>
     *            When opcode is NEWARRAY, operand value should be one of
     *            {@link Opcodes#T_BOOLEAN}, {@link Opcodes#T_CHAR},
     *            {@link Opcodes#T_FLOAT}, {@link Opcodes#T_DOUBLE},
     *            {@link Opcodes#T_BYTE}, {@link Opcodes#T_SHORT},
     *            {@link Opcodes#T_INT} or {@link Opcodes#T_LONG}.
     */
    public abstract void visitIntInsn(final int opcode, final int operand);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitVarInsn}.
     *
     * @param opcode
     *            the opcode of the local variable instruction to be visited.
     *            This opcode is either ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
     *            ISTORE, LSTORE, FSTORE, DSTORE, ASTORE or RET.
     * @param var
     *            the operand of the instruction to be visited. This operand is
     *            the index of a local variable.
     */
    public abstract void visitVarInsn(final int opcode, final int var);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTypeInsn}.
     *
    /**
     * Visits a type instruction. A type instruction is an instruction that
     * takes the internal name of a class as parameter.
     *
     * @param opcode
     *            the opcode of the type instruction to be visited. This opcode
     *            is either NEW, ANEWARRAY, CHECKCAST or INSTANCEOF.
     * @param type
     *            the operand of the instruction to be visited. This operand
     *            must be the internal name of an object or array class (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     */
    public abstract void visitTypeInsn(final int opcode, final String type);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitFieldInsn}.
     *
     * @param opcode
     *            the opcode of the type instruction to be visited. This opcode
     *            is either GETSTATIC, PUTSTATIC, GETFIELD or PUTFIELD.
     * @param owner
     *            the internal name of the field's owner class (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     * @param name
     *            the field's name.
     * @param desc
     *            the field's descriptor (see {@link jdk.internal.org.objectweb.asm.Type Type}).
     */
    public abstract void visitFieldInsn(final int opcode, final String owner,
            final String name, final String desc);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitMethodInsn}.
     *
     * @param opcode
     *            the opcode of the type instruction to be visited. This opcode
     *            is either INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or
     *            INVOKEINTERFACE.
     * @param owner
     *            the internal name of the method's owner class (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link jdk.internal.org.objectweb.asm.Type Type}).
     */
    @Deprecated
    public void visitMethodInsn(final int opcode, final String owner,
            final String name, final String desc) {
        if (api >= Opcodes.ASM5) {
            boolean itf = opcode == Opcodes.INVOKEINTERFACE;
            visitMethodInsn(opcode, owner, name, desc, itf);
            return;
        }
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitMethodInsn}.
     *
     * @param opcode
     *            the opcode of the type instruction to be visited. This opcode
     *            is either INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or
     *            INVOKEINTERFACE.
     * @param owner
     *            the internal name of the method's owner class (see
     *            {@link jdk.internal.org.objectweb.asm.Type#getInternalName() getInternalName}).
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link jdk.internal.org.objectweb.asm.Type Type}).
     * @param itf
     *            if the method's owner class is an interface.
     */
    public void visitMethodInsn(final int opcode, final String owner,
            final String name, final String desc, final boolean itf) {
        if (api < Opcodes.ASM5) {
            if (itf != (opcode == Opcodes.INVOKEINTERFACE)) {
                throw new IllegalArgumentException(
                        "INVOKESPECIAL/STATIC on interfaces require ASM 5");
            }
            visitMethodInsn(opcode, owner, name, desc);
            return;
        }
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitInvokeDynamicInsn}.
     *
     * Visits an invokedynamic instruction.
     *
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link jdk.internal.org.objectweb.asm.Type Type}).
     * @param bsm
     *            the bootstrap method.
     * @param bsmArgs
     *            the bootstrap method constant arguments. Each argument must be
     *            an {@link Integer}, {@link Float}, {@link Long},
     *            {@link Double}, {@link String}, {@link jdk.internal.org.objectweb.asm.Type} or {@link Handle}
     *            value. This method is allowed to modify the content of the
     *            array so a caller should expect that this array may change.
     */
    public abstract void visitInvokeDynamicInsn(String name, String desc,
            Handle bsm, Object... bsmArgs);

    /**
     * Method jump instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitJumpInsn}.
     *
     * @param opcode
     *            the opcode of the type instruction to be visited. This opcode
     *            is either IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IF_ICMPEQ,
     *            IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
     *            IF_ACMPEQ, IF_ACMPNE, GOTO, JSR, IFNULL or IFNONNULL.
     * @param label
     *            the operand of the instruction to be visited. This operand is
     *            a label that designates the instruction to which the jump
     *            instruction may jump.
     */
    public abstract void visitJumpInsn(final int opcode, final Label label);

    /**
     * Method label.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLabel}.
     *
     * @param label
     *            a {@link Label Label} object.
     */
    public abstract void visitLabel(final Label label);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLdcInsn}.
     *
     * Visits a LDC instruction. Note that new constant types may be added in
     * future versions of the Java Virtual Machine. To easily detect new
     * constant types, implementations of this method should check for
     * unexpected constant types, like this:
     *
     * <pre>
     * if (cst instanceof Integer) {
     *     // ...
     * } else if (cst instanceof Float) {
     *     // ...
     * } else if (cst instanceof Long) {
     *     // ...
     * } else if (cst instanceof Double) {
     *     // ...
     * } else if (cst instanceof String) {
     *     // ...
     * } else if (cst instanceof Type) {
     *     int sort = ((Type) cst).getSort();
     *     if (sort == Type.OBJECT) {
     *         // ...
     *     } else if (sort == Type.ARRAY) {
     *         // ...
     *     } else if (sort == Type.METHOD) {
     *         // ...
     *     } else {
     *         // throw an exception
     *     }
     * } else if (cst instanceof Handle) {
     *     // ...
     * } else {
     *     // throw an exception
     * }
     * </pre>
     *
     * @param cst
     *            the constant to be loaded on the stack. This parameter must be
     *            a non null {@link Integer}, a {@link Float}, a {@link Long}, a
     *            {@link Double}, a {@link String}, a {@link jdk.internal.org.objectweb.asm.Type}
     *            of OBJECT or ARRAY sort for <tt>.class</tt> constants, for classes whose
     *            version is 49.0, a {@link jdk.internal.org.objectweb.asm.Type} of METHOD sort or a
     *            {@link Handle} for MethodType and MethodHandle constants, for
     *            classes whose version is 51.0.
     */
    public abstract void visitLdcInsn(final Object cst);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitIincInsn}.
     *
     * @param var
     *            index of the local variable to be incremented.
     * @param increment
     *            amount to increment the local variable by.
     */
    public abstract void visitIincInsn(final int var, final int increment);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTableSwitchInsn}.
     *
     * @param min
     *            the minimum key value.
     * @param max
     *            the maximum key value.
     * @param dflt
     *            beginning of the default handler block.
     * @param labels
     *            beginnings of the handler blocks. <tt>labels[i]</tt> is the
     *            beginning of the handler block for the <tt>min + i</tt> key.
     */
    public abstract void visitTableSwitchInsn(final int min, final int max,
            final Label dflt, final Label... labels);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLookupSwitchInsn}.
     *
     * @param dflt
     *            beginning of the default handler block.
     * @param keys
     *            the values of the keys.
     * @param labels
     *            beginnings of the handler blocks. <tt>labels[i]</tt> is the
     *            beginning of the handler block for the <tt>keys[i]</tt> key.
     */
    public abstract void visitLookupSwitchInsn(final Label dflt,
            final int[] keys, final Label[] labels);

    /**
     * Method instruction.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitMultiANewArrayInsn}.
     *
     * @param desc
     *            an array type descriptor (see {@link jdk.internal.org.objectweb.asm.Type Type}).
     * @param dims
     *            number of dimensions of the array to allocate.
     */
    public abstract void visitMultiANewArrayInsn(final String desc,
            final int dims);

    /**
     * Instruction type annotation.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitInsnAnnotation}.
     *
     * @param typeRef
     *            a reference to the annotated type. The sort of this type
     *            reference must be {@link jdk.internal.org.objectweb.asm.TypeReference#INSTANCEOF INSTANCEOF},
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#NEW NEW},
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#CONSTRUCTOR_REFERENCE CONSTRUCTOR_REFERENCE},
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#METHOD_REFERENCE METHOD_REFERENCE},
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#CAST CAST},
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT},
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#METHOD_INVOCATION_TYPE_ARGUMENT METHOD_INVOCATION_TYPE_ARGUMENT},
     *            {@link jdk.internal.org.objectweb.asm.TypeReference#CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT},
     *            or {@link jdk.internal.org.objectweb.asm.TypeReference#METHOD_REFERENCE_TYPE_ARGUMENT METHOD_REFERENCE_TYPE_ARGUMENT}.
     *            See {@link jdk.internal.org.objectweb.asm.TypeReference}.
     * @param typePath
     *            the path to the annotated type argument, wildcard bound, array
     *            element type, or static inner type within 'typeRef'. May be
     *            <tt>null</tt> if the annotation targets 'typeRef' as a whole.
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public Printer visitInsnAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method exception handler.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTryCatchBlock}.
     *
     * @param start
     *            beginning of the exception handler's scope (inclusive).
     * @param end
     *            end of the exception handler's scope (exclusive).
     * @param handler
     *            beginning of the exception handler's code.
     * @param type
     *            internal name of the type of exceptions handled by the
     *            handler, or <tt>null</tt> to catch any exceptions (for
     *            "finally" blocks).
     * @throws IllegalArgumentException
     *             if one of the labels has already been visited by this visitor
     *             (by the {@link #visitLabel visitLabel} method).
     */
    public abstract void visitTryCatchBlock(final Label start, final Label end,
            final Label handler, final String type);

    /**
     * Try catch block type annotation.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTryCatchAnnotation}.
     *
     * @param typeRef
     *            a reference to the annotated type. The sort of this type
     *            reference must be {@link jdk.internal.org.objectweb.asm.TypeReference#EXCEPTION_PARAMETER
     *            EXCEPTION_PARAMETER}.
     *            See {@link jdk.internal.org.objectweb.asm.TypeReference}.
     * @param typePath
     *            the path to the annotated type argument, wildcard bound, array
     *            element type, or static inner type within 'typeRef'. May be
     *            <tt>null</tt> if the annotation targets 'typeRef' as a whole.
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public Printer visitTryCatchAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method debug info.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLocalVariable}.
     *
     * @param name
     *            the name of a local variable.
     * @param desc
     *            the type descriptor of this local variable.
     * @param signature
     *            the type signature of this local variable. May be
     *            <tt>null</tt> if the local variable type does not use generic
     *            types.
     * @param start
     *            the first instruction corresponding to the scope of this local
     *            variable (inclusive).
     * @param end
     *            the last instruction corresponding to the scope of this local
     *            variable (exclusive).
     * @param index
     *            the local variable's index.
     * @throws IllegalArgumentException
     *             if one of the labels has not already been visited by this
     *             visitor (by the {@link #visitLabel visitLabel} method).
     */
    public abstract void visitLocalVariable(final String name,
            final String desc, final String signature, final Label start,
            final Label end, final int index);

    /**
     * Local variable type annotation.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTryCatchAnnotation}.
     *
     * @param typeRef
     *            a reference to the annotated type. The sort of this type
     *            reference must be {@link jdk.internal.org.objectweb.asm.TypeReference#LOCAL_VARIABLE
     *            LOCAL_VARIABLE} or {@link jdk.internal.org.objectweb.asm.TypeReference#RESOURCE_VARIABLE
     *            RESOURCE_VARIABLE}.
     *            See {@link jdk.internal.org.objectweb.asm.TypeReference}.
     * @param typePath
     *            the path to the annotated type argument, wildcard bound, array
     *            element type, or static inner type within 'typeRef'. May be
     *            <tt>null</tt> if the annotation targets 'typeRef' as a whole.
     * @param start
     *            the fist instructions corresponding to the continuous ranges
     *            that make the scope of this local variable (inclusive).
     * @param end
     *            the last instructions corresponding to the continuous ranges
     *            that make the scope of this local variable (exclusive). This
     *            array must have the same size as the 'start' array.
     * @param index
     *            the local variable's index in each range. This array must have
     *            the same size as the 'start' array.
     * @param desc
     *            the class descriptor of the annotation class.
     * @param visible
     *            <tt>true</tt> if the annotation is visible at runtime.
     * @return the printer
     */
    public Printer visitLocalVariableAnnotation(final int typeRef,
            final TypePath typePath, final Label[] start, final Label[] end,
            final int[] index, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method debug info.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLineNumber}.
     *
     * @param line
     *            a line number. This number refers to the source file from
     *            which the class was compiled.
     * @param start
     *            the first instruction corresponding to this line number.
     * @throws IllegalArgumentException
     *             if <tt>start</tt> has not already been visited by this
     *             visitor (by the {@link #visitLabel visitLabel} method).
     */
    public abstract void visitLineNumber(final int line, final Label start);

    /**
     * Method max stack and max locals.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitMaxs}.
     *
     * @param maxStack
     *            maximum stack size of the method.
     * @param maxLocals
     *            maximum number of local variables for the method.
     */
    public abstract void visitMaxs(final int maxStack, final int maxLocals);

    /**
     * Method end.
     * See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitEnd}.
     */
    public abstract void visitMethodEnd();

    /**
     * Returns the text constructed by this visitor.
     *
     * @return the text constructed by this visitor.
     */
    public List<Object> getText() {
        return text;
    }

    /**
     * Prints the text constructed by this visitor.
     *
     * @param pw
     *            the print writer to be used.
     */
    public void print(final PrintWriter pw) {
        printList(pw, text);
    }

    /**
     * Appends a quoted string to a given buffer.
     *
     * @param buf
     *            the buffer where the string must be added.
     * @param s
     *            the string to be added.
     */
    public static void appendString(final StringBuffer buf, final String s) {
        buf.append('\"');
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == '\n') {
                buf.append("\\n");
            } else if (c == '\r') {
                buf.append("\\r");
            } else if (c == '\\') {
                buf.append("\\\\");
            } else if (c == '"') {
                buf.append("\\\"");
            } else if (c < 0x20 || c > 0x7f) {
                buf.append("\\u");
                if (c < 0x10) {
                    buf.append("000");
                } else if (c < 0x100) {
                    buf.append("00");
                } else if (c < 0x1000) {
                    buf.append('0');
                }
                buf.append(Integer.toString(c, 16));
            } else {
                buf.append(c);
            }
        }
        buf.append('\"');
    }

    /**
     * Prints the given string tree.
     *
     * @param pw
     *            the writer to be used to print the tree.
     * @param l
     *            a string tree, i.e., a string list that can contain other
     *            string lists, and so on recursively.
     */
    static void printList(final PrintWriter pw, final List<?> l) {
        for (int i = 0; i < l.size(); ++i) {
            Object o = l.get(i);
            if (o instanceof List) {
                printList(pw, (List<?>) o);
            } else {
                pw.print(o.toString());
            }
        }
    }
}
