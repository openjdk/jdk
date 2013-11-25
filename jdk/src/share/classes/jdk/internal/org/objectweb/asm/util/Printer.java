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
     * must be one of {@link Opcodes#ASM4} or {@link Opcodes#ASM5}.
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
     */
    protected Printer(final int api) {
        this.api = api;
        this.buf = new StringBuffer();
        this.text = new ArrayList<Object>();
    }

    /**
     * Class header. See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visit}.
     */
    public abstract void visit(final int version, final int access,
            final String name, final String signature, final String superName,
            final String[] interfaces);

    /**
     * Class source. See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitSource}.
     */
    public abstract void visitSource(final String file, final String debug);

    /**
     * Class outer class. See
     * {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitOuterClass}.
     */
    public abstract void visitOuterClass(final String owner, final String name,
            final String desc);

    /**
     * Class annotation. See
     * {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitAnnotation}.
     */
    public abstract Printer visitClassAnnotation(final String desc,
            final boolean visible);

    /**
     * Class type annotation. See
     * {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitTypeAnnotation}.
     */
    public Printer visitClassTypeAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Class attribute. See
     * {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitAttribute}.
     */
    public abstract void visitClassAttribute(final Attribute attr);

    /**
     * Class inner name. See
     * {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitInnerClass}.
     */
    public abstract void visitInnerClass(final String name,
            final String outerName, final String innerName, final int access);

    /**
     * Class field. See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitField}.
     */
    public abstract Printer visitField(final int access, final String name,
            final String desc, final String signature, final Object value);

    /**
     * Class method. See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitMethod}.
     */
    public abstract Printer visitMethod(final int access, final String name,
            final String desc, final String signature, final String[] exceptions);

    /**
     * Class end. See {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitEnd}.
     */
    public abstract void visitClassEnd();

    // ------------------------------------------------------------------------
    // Annotations
    // ------------------------------------------------------------------------

    /**
     * Annotation value. See {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visit}.
     */
    public abstract void visit(final String name, final Object value);

    /**
     * Annotation enum value. See
     * {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visitEnum}.
     */
    public abstract void visitEnum(final String name, final String desc,
            final String value);

    /**
     * Nested annotation value. See
     * {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visitAnnotation}.
     */
    public abstract Printer visitAnnotation(final String name, final String desc);

    /**
     * Annotation array value. See
     * {@link jdk.internal.org.objectweb.asm.AnnotationVisitor#visitArray}.
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
     * Field annotation. See
     * {@link jdk.internal.org.objectweb.asm.FieldVisitor#visitAnnotation}.
     */
    public abstract Printer visitFieldAnnotation(final String desc,
            final boolean visible);

    /**
     * Field type annotation. See
     * {@link jdk.internal.org.objectweb.asm.FieldVisitor#visitTypeAnnotation}.
     */
    public Printer visitFieldTypeAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Field attribute. See
     * {@link jdk.internal.org.objectweb.asm.FieldVisitor#visitAttribute}.
     */
    public abstract void visitFieldAttribute(final Attribute attr);

    /**
     * Field end. See {@link jdk.internal.org.objectweb.asm.FieldVisitor#visitEnd}.
     */
    public abstract void visitFieldEnd();

    // ------------------------------------------------------------------------
    // Methods
    // ------------------------------------------------------------------------

    /**
     * Method parameter. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitParameter(String, int)}.
     */
    public void visitParameter(String name, int access) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method default annotation. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitAnnotationDefault}.
     */
    public abstract Printer visitAnnotationDefault();

    /**
     * Method annotation. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitAnnotation}.
     */
    public abstract Printer visitMethodAnnotation(final String desc,
            final boolean visible);

    /**
     * Method type annotation. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTypeAnnotation}.
     */
    public Printer visitMethodTypeAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method parameter annotation. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitParameterAnnotation}.
     */
    public abstract Printer visitParameterAnnotation(final int parameter,
            final String desc, final boolean visible);

    /**
     * Method attribute. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitAttribute}.
     */
    public abstract void visitMethodAttribute(final Attribute attr);

    /**
     * Method start. See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitCode}.
     */
    public abstract void visitCode();

    /**
     * Method stack frame. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitFrame}.
     */
    public abstract void visitFrame(final int type, final int nLocal,
            final Object[] local, final int nStack, final Object[] stack);

    /**
     * Method instruction. See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitInsn}
     * .
     */
    public abstract void visitInsn(final int opcode);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitIntInsn}.
     */
    public abstract void visitIntInsn(final int opcode, final int operand);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitVarInsn}.
     */
    public abstract void visitVarInsn(final int opcode, final int var);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTypeInsn}.
     */
    public abstract void visitTypeInsn(final int opcode, final String type);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitFieldInsn}.
     */
    public abstract void visitFieldInsn(final int opcode, final String owner,
            final String name, final String desc);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitMethodInsn}.
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
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitMethodInsn}.
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
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitInvokeDynamicInsn}.
     */
    public abstract void visitInvokeDynamicInsn(String name, String desc,
            Handle bsm, Object... bsmArgs);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitJumpInsn}.
     */
    public abstract void visitJumpInsn(final int opcode, final Label label);

    /**
     * Method label. See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLabel}.
     */
    public abstract void visitLabel(final Label label);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLdcInsn}.
     */
    public abstract void visitLdcInsn(final Object cst);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitIincInsn}.
     */
    public abstract void visitIincInsn(final int var, final int increment);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTableSwitchInsn}.
     */
    public abstract void visitTableSwitchInsn(final int min, final int max,
            final Label dflt, final Label... labels);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLookupSwitchInsn}.
     */
    public abstract void visitLookupSwitchInsn(final Label dflt,
            final int[] keys, final Label[] labels);

    /**
     * Method instruction. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitMultiANewArrayInsn}.
     */
    public abstract void visitMultiANewArrayInsn(final String desc,
            final int dims);

    /**
     * Instruction type annotation. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitInsnAnnotation}.
     */
    public Printer visitInsnAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method exception handler. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTryCatchBlock}.
     */
    public abstract void visitTryCatchBlock(final Label start, final Label end,
            final Label handler, final String type);

    /**
     * Try catch block type annotation. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTryCatchAnnotation}.
     */
    public Printer visitTryCatchAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method debug info. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLocalVariable}.
     */
    public abstract void visitLocalVariable(final String name,
            final String desc, final String signature, final Label start,
            final Label end, final int index);

    /**
     * Local variable type annotation. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitTryCatchAnnotation}.
     */
    public Printer visitLocalVariableAnnotation(final int typeRef,
            final TypePath typePath, final Label[] start, final Label[] end,
            final int[] index, final String desc, final boolean visible) {
        throw new RuntimeException("Must be overriden");
    }

    /**
     * Method debug info. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLineNumber}.
     */
    public abstract void visitLineNumber(final int line, final Label start);

    /**
     * Method max stack and max locals. See
     * {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitMaxs}.
     */
    public abstract void visitMaxs(final int maxStack, final int maxLocals);

    /**
     * Method end. See {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitEnd}.
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
