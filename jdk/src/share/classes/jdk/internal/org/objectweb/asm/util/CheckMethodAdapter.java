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
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.TypePath;
import jdk.internal.org.objectweb.asm.TypeReference;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * A {@link MethodVisitor} that checks that its methods are properly used. More
 * precisely this method adapter checks each instruction individually, i.e.,
 * each visit method checks some preconditions based <i>only</i> on its
 * arguments - such as the fact that the given opcode is correct for a given
 * visit method. This adapter can also perform some basic data flow checks (more
 * precisely those that can be performed without the full class hierarchy - see
 * {@link jdk.internal.org.objectweb.asm.tree.analysis.BasicVerifier}). For instance in a
 * method whose signature is <tt>void m ()</tt>, the invalid instruction
 * IRETURN, or the invalid sequence IADD L2I will be detected if the data flow
 * checks are enabled. These checks are enabled by using the
 * {@link #CheckMethodAdapter(int,String,String,MethodVisitor,Map)} constructor.
 * They are not performed if any other constructor is used.
 *
 * @author Eric Bruneton
 */
public class CheckMethodAdapter extends MethodVisitor {

    /**
     * The class version number.
     */
    public int version;

    /**
     * The access flags of the method.
     */
    private int access;

    /**
     * <tt>true</tt> if the visitCode method has been called.
     */
    private boolean startCode;

    /**
     * <tt>true</tt> if the visitMaxs method has been called.
     */
    private boolean endCode;

    /**
     * <tt>true</tt> if the visitEnd method has been called.
     */
    private boolean endMethod;

    /**
     * Number of visited instructions.
     */
    private int insnCount;

    /**
     * The already visited labels. This map associate Integer values to pseudo
     * code offsets.
     */
    private final Map<Label, Integer> labels;

    /**
     * The labels used in this method. Every used label must be visited with
     * visitLabel before the end of the method (i.e. should be in #labels).
     */
    private Set<Label> usedLabels;

    /**
     * Number of visited frames in expanded form.
     */
    private int expandedFrames;

    /**
     * Number of visited frames in compressed form.
     */
    private int compressedFrames;

    /**
     * Number of instructions before the last visited frame.
     */
    private int lastFrame = -1;

    /**
     * The exception handler ranges. Each pair of list element contains the
     * start and end labels of an exception handler block.
     */
    private List<Label> handlers;

    /**
     * Code of the visit method to be used for each opcode.
     */
    private static final int[] TYPE;

    /**
     * The Label.status field.
     */
    private static Field labelStatusField;

    static {
        String s = "BBBBBBBBBBBBBBBBCCIAADDDDDAAAAAAAAAAAAAAAAAAAABBBBBBBBDD"
                + "DDDAAAAAAAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
                + "BBBBBBBBBBBBBBBBBBBJBBBBBBBBBBBBBBBBBBBBHHHHHHHHHHHHHHHHD"
                + "KLBBBBBBFFFFGGGGAECEBBEEBBAMHHAA";
        TYPE = new int[s.length()];
        for (int i = 0; i < TYPE.length; ++i) {
            TYPE[i] = s.charAt(i) - 'A' - 1;
        }
    }

    // code to generate the above string
    // public static void main (String[] args) {
    // int[] TYPE = new int[] {
    // 0, //NOP
    // 0, //ACONST_NULL
    // 0, //ICONST_M1
    // 0, //ICONST_0
    // 0, //ICONST_1
    // 0, //ICONST_2
    // 0, //ICONST_3
    // 0, //ICONST_4
    // 0, //ICONST_5
    // 0, //LCONST_0
    // 0, //LCONST_1
    // 0, //FCONST_0
    // 0, //FCONST_1
    // 0, //FCONST_2
    // 0, //DCONST_0
    // 0, //DCONST_1
    // 1, //BIPUSH
    // 1, //SIPUSH
    // 7, //LDC
    // -1, //LDC_W
    // -1, //LDC2_W
    // 2, //ILOAD
    // 2, //LLOAD
    // 2, //FLOAD
    // 2, //DLOAD
    // 2, //ALOAD
    // -1, //ILOAD_0
    // -1, //ILOAD_1
    // -1, //ILOAD_2
    // -1, //ILOAD_3
    // -1, //LLOAD_0
    // -1, //LLOAD_1
    // -1, //LLOAD_2
    // -1, //LLOAD_3
    // -1, //FLOAD_0
    // -1, //FLOAD_1
    // -1, //FLOAD_2
    // -1, //FLOAD_3
    // -1, //DLOAD_0
    // -1, //DLOAD_1
    // -1, //DLOAD_2
    // -1, //DLOAD_3
    // -1, //ALOAD_0
    // -1, //ALOAD_1
    // -1, //ALOAD_2
    // -1, //ALOAD_3
    // 0, //IALOAD
    // 0, //LALOAD
    // 0, //FALOAD
    // 0, //DALOAD
    // 0, //AALOAD
    // 0, //BALOAD
    // 0, //CALOAD
    // 0, //SALOAD
    // 2, //ISTORE
    // 2, //LSTORE
    // 2, //FSTORE
    // 2, //DSTORE
    // 2, //ASTORE
    // -1, //ISTORE_0
    // -1, //ISTORE_1
    // -1, //ISTORE_2
    // -1, //ISTORE_3
    // -1, //LSTORE_0
    // -1, //LSTORE_1
    // -1, //LSTORE_2
    // -1, //LSTORE_3
    // -1, //FSTORE_0
    // -1, //FSTORE_1
    // -1, //FSTORE_2
    // -1, //FSTORE_3
    // -1, //DSTORE_0
    // -1, //DSTORE_1
    // -1, //DSTORE_2
    // -1, //DSTORE_3
    // -1, //ASTORE_0
    // -1, //ASTORE_1
    // -1, //ASTORE_2
    // -1, //ASTORE_3
    // 0, //IASTORE
    // 0, //LASTORE
    // 0, //FASTORE
    // 0, //DASTORE
    // 0, //AASTORE
    // 0, //BASTORE
    // 0, //CASTORE
    // 0, //SASTORE
    // 0, //POP
    // 0, //POP2
    // 0, //DUP
    // 0, //DUP_X1
    // 0, //DUP_X2
    // 0, //DUP2
    // 0, //DUP2_X1
    // 0, //DUP2_X2
    // 0, //SWAP
    // 0, //IADD
    // 0, //LADD
    // 0, //FADD
    // 0, //DADD
    // 0, //ISUB
    // 0, //LSUB
    // 0, //FSUB
    // 0, //DSUB
    // 0, //IMUL
    // 0, //LMUL
    // 0, //FMUL
    // 0, //DMUL
    // 0, //IDIV
    // 0, //LDIV
    // 0, //FDIV
    // 0, //DDIV
    // 0, //IREM
    // 0, //LREM
    // 0, //FREM
    // 0, //DREM
    // 0, //INEG
    // 0, //LNEG
    // 0, //FNEG
    // 0, //DNEG
    // 0, //ISHL
    // 0, //LSHL
    // 0, //ISHR
    // 0, //LSHR
    // 0, //IUSHR
    // 0, //LUSHR
    // 0, //IAND
    // 0, //LAND
    // 0, //IOR
    // 0, //LOR
    // 0, //IXOR
    // 0, //LXOR
    // 8, //IINC
    // 0, //I2L
    // 0, //I2F
    // 0, //I2D
    // 0, //L2I
    // 0, //L2F
    // 0, //L2D
    // 0, //F2I
    // 0, //F2L
    // 0, //F2D
    // 0, //D2I
    // 0, //D2L
    // 0, //D2F
    // 0, //I2B
    // 0, //I2C
    // 0, //I2S
    // 0, //LCMP
    // 0, //FCMPL
    // 0, //FCMPG
    // 0, //DCMPL
    // 0, //DCMPG
    // 6, //IFEQ
    // 6, //IFNE
    // 6, //IFLT
    // 6, //IFGE
    // 6, //IFGT
    // 6, //IFLE
    // 6, //IF_ICMPEQ
    // 6, //IF_ICMPNE
    // 6, //IF_ICMPLT
    // 6, //IF_ICMPGE
    // 6, //IF_ICMPGT
    // 6, //IF_ICMPLE
    // 6, //IF_ACMPEQ
    // 6, //IF_ACMPNE
    // 6, //GOTO
    // 6, //JSR
    // 2, //RET
    // 9, //TABLESWITCH
    // 10, //LOOKUPSWITCH
    // 0, //IRETURN
    // 0, //LRETURN
    // 0, //FRETURN
    // 0, //DRETURN
    // 0, //ARETURN
    // 0, //RETURN
    // 4, //GETSTATIC
    // 4, //PUTSTATIC
    // 4, //GETFIELD
    // 4, //PUTFIELD
    // 5, //INVOKEVIRTUAL
    // 5, //INVOKESPECIAL
    // 5, //INVOKESTATIC
    // 5, //INVOKEINTERFACE
    // -1, //INVOKEDYNAMIC
    // 3, //NEW
    // 1, //NEWARRAY
    // 3, //ANEWARRAY
    // 0, //ARRAYLENGTH
    // 0, //ATHROW
    // 3, //CHECKCAST
    // 3, //INSTANCEOF
    // 0, //MONITORENTER
    // 0, //MONITOREXIT
    // -1, //WIDE
    // 11, //MULTIANEWARRAY
    // 6, //IFNULL
    // 6, //IFNONNULL
    // -1, //GOTO_W
    // -1 //JSR_W
    // };
    // for (int i = 0; i < TYPE.length; ++i) {
    // System.out.print((char)(TYPE[i] + 1 + 'A'));
    // }
    // System.out.println();
    // }

    /**
     * Constructs a new {@link CheckMethodAdapter} object. This method adapter
     * will not perform any data flow check (see
     * {@link #CheckMethodAdapter(int,String,String,MethodVisitor,Map)}).
     * <i>Subclasses must not use this constructor</i>. Instead, they must use
     * the {@link #CheckMethodAdapter(int, MethodVisitor, Map)} version.
     *
     * @param mv
     *            the method visitor to which this adapter must delegate calls.
     */
    public CheckMethodAdapter(final MethodVisitor mv) {
        this(mv, new HashMap<Label, Integer>());
    }

    /**
     * Constructs a new {@link CheckMethodAdapter} object. This method adapter
     * will not perform any data flow check (see
     * {@link #CheckMethodAdapter(int,String,String,MethodVisitor,Map)}).
     * <i>Subclasses must not use this constructor</i>. Instead, they must use
     * the {@link #CheckMethodAdapter(int, MethodVisitor, Map)} version.
     *
     * @param mv
     *            the method visitor to which this adapter must delegate calls.
     * @param labels
     *            a map of already visited labels (in other methods).
     * @throws IllegalStateException
     *             If a subclass calls this constructor.
     */
    public CheckMethodAdapter(final MethodVisitor mv,
            final Map<Label, Integer> labels) {
        this(Opcodes.ASM5, mv, labels);
        if (getClass() != CheckMethodAdapter.class) {
            throw new IllegalStateException();
        }
    }

    /**
     * Constructs a new {@link CheckMethodAdapter} object. This method adapter
     * will not perform any data flow check (see
     * {@link #CheckMethodAdapter(int,String,String,MethodVisitor,Map)}).
     *
     * @param mv
     *            the method visitor to which this adapter must delegate calls.
     * @param labels
     *            a map of already visited labels (in other methods).
     */
    protected CheckMethodAdapter(final int api, final MethodVisitor mv,
            final Map<Label, Integer> labels) {
        super(api, mv);
        this.labels = labels;
        this.usedLabels = new HashSet<Label>();
        this.handlers = new ArrayList<Label>();
    }

    /**
     * Constructs a new {@link CheckMethodAdapter} object. This method adapter
     * will perform basic data flow checks. For instance in a method whose
     * signature is <tt>void m ()</tt>, the invalid instruction IRETURN, or the
     * invalid sequence IADD L2I will be detected.
     *
     * @param access
     *            the method's access flags.
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link Type Type}).
     * @param cmv
     *            the method visitor to which this adapter must delegate calls.
     * @param labels
     *            a map of already visited labels (in other methods).
     */
    public CheckMethodAdapter(final int access, final String name,
            final String desc, final MethodVisitor cmv,
            final Map<Label, Integer> labels) {
        this(new MethodNode(Opcodes.ASM5, access, name, desc, null, null) {
            @Override
            public void visitEnd() {
                Analyzer<BasicValue> a = new Analyzer<BasicValue>(
                        new BasicVerifier());
                try {
                    a.analyze("dummy", this);
                } catch (Exception e) {
                    if (e instanceof IndexOutOfBoundsException
                            && maxLocals == 0 && maxStack == 0) {
                        throw new RuntimeException(
                                "Data flow checking option requires valid, non zero maxLocals and maxStack values.");
                    }
                    e.printStackTrace();
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw, true);
                    CheckClassAdapter.printAnalyzerResult(this, a, pw);
                    pw.close();
                    throw new RuntimeException(e.getMessage() + ' '
                            + sw.toString());
                }
                accept(cmv);
            }
        }, labels);
        this.access = access;
    }

    @Override
    public void visitParameter(String name, int access) {
        if (name != null) {
            checkUnqualifiedName(version, name, "name");
        }
        CheckClassAdapter.checkAccess(access, Opcodes.ACC_FINAL
                + Opcodes.ACC_MANDATED + Opcodes.ACC_SYNTHETIC);
        super.visitParameter(name, access);
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc,
            final boolean visible) {
        checkEndMethod();
        checkDesc(desc, false);
        return new CheckAnnotationAdapter(super.visitAnnotation(desc, visible));
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        checkEndMethod();
        int sort = typeRef >>> 24;
        if (sort != TypeReference.METHOD_TYPE_PARAMETER
                && sort != TypeReference.METHOD_TYPE_PARAMETER_BOUND
                && sort != TypeReference.METHOD_RETURN
                && sort != TypeReference.METHOD_RECEIVER
                && sort != TypeReference.METHOD_FORMAL_PARAMETER
                && sort != TypeReference.THROWS) {
            throw new IllegalArgumentException("Invalid type reference sort 0x"
                    + Integer.toHexString(sort));
        }
        CheckClassAdapter.checkTypeRefAndPath(typeRef, typePath);
        CheckMethodAdapter.checkDesc(desc, false);
        return new CheckAnnotationAdapter(super.visitTypeAnnotation(typeRef,
                typePath, desc, visible));
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        checkEndMethod();
        return new CheckAnnotationAdapter(super.visitAnnotationDefault(), false);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(final int parameter,
            final String desc, final boolean visible) {
        checkEndMethod();
        checkDesc(desc, false);
        return new CheckAnnotationAdapter(super.visitParameterAnnotation(
                parameter, desc, visible));
    }

    @Override
    public void visitAttribute(final Attribute attr) {
        checkEndMethod();
        if (attr == null) {
            throw new IllegalArgumentException(
                    "Invalid attribute (must not be null)");
        }
        super.visitAttribute(attr);
    }

    @Override
    public void visitCode() {
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            throw new RuntimeException("Abstract methods cannot have code");
        }
        startCode = true;
        super.visitCode();
    }

    @Override
    public void visitFrame(final int type, final int nLocal,
            final Object[] local, final int nStack, final Object[] stack) {
        if (insnCount == lastFrame) {
            throw new IllegalStateException(
                    "At most one frame can be visited at a given code location.");
        }
        lastFrame = insnCount;
        int mLocal;
        int mStack;
        switch (type) {
        case Opcodes.F_NEW:
        case Opcodes.F_FULL:
            mLocal = Integer.MAX_VALUE;
            mStack = Integer.MAX_VALUE;
            break;

        case Opcodes.F_SAME:
            mLocal = 0;
            mStack = 0;
            break;

        case Opcodes.F_SAME1:
            mLocal = 0;
            mStack = 1;
            break;

        case Opcodes.F_APPEND:
        case Opcodes.F_CHOP:
            mLocal = 3;
            mStack = 0;
            break;

        default:
            throw new IllegalArgumentException("Invalid frame type " + type);
        }

        if (nLocal > mLocal) {
            throw new IllegalArgumentException("Invalid nLocal=" + nLocal
                    + " for frame type " + type);
        }
        if (nStack > mStack) {
            throw new IllegalArgumentException("Invalid nStack=" + nStack
                    + " for frame type " + type);
        }

        if (type != Opcodes.F_CHOP) {
            if (nLocal > 0 && (local == null || local.length < nLocal)) {
                throw new IllegalArgumentException(
                        "Array local[] is shorter than nLocal");
            }
            for (int i = 0; i < nLocal; ++i) {
                checkFrameValue(local[i]);
            }
        }
        if (nStack > 0 && (stack == null || stack.length < nStack)) {
            throw new IllegalArgumentException(
                    "Array stack[] is shorter than nStack");
        }
        for (int i = 0; i < nStack; ++i) {
            checkFrameValue(stack[i]);
        }
        if (type == Opcodes.F_NEW) {
            ++expandedFrames;
        } else {
            ++compressedFrames;
        }
        if (expandedFrames > 0 && compressedFrames > 0) {
            throw new RuntimeException(
                    "Expanded and compressed frames must not be mixed.");
        }
        super.visitFrame(type, nLocal, local, nStack, stack);
    }

    @Override
    public void visitInsn(final int opcode) {
        checkStartCode();
        checkEndCode();
        checkOpcode(opcode, 0);
        super.visitInsn(opcode);
        ++insnCount;
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        checkStartCode();
        checkEndCode();
        checkOpcode(opcode, 1);
        switch (opcode) {
        case Opcodes.BIPUSH:
            checkSignedByte(operand, "Invalid operand");
            break;
        case Opcodes.SIPUSH:
            checkSignedShort(operand, "Invalid operand");
            break;
        // case Constants.NEWARRAY:
        default:
            if (operand < Opcodes.T_BOOLEAN || operand > Opcodes.T_LONG) {
                throw new IllegalArgumentException(
                        "Invalid operand (must be an array type code T_...): "
                                + operand);
            }
        }
        super.visitIntInsn(opcode, operand);
        ++insnCount;
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        checkStartCode();
        checkEndCode();
        checkOpcode(opcode, 2);
        checkUnsignedShort(var, "Invalid variable index");
        super.visitVarInsn(opcode, var);
        ++insnCount;
    }

    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        checkStartCode();
        checkEndCode();
        checkOpcode(opcode, 3);
        checkInternalName(type, "type");
        if (opcode == Opcodes.NEW && type.charAt(0) == '[') {
            throw new IllegalArgumentException(
                    "NEW cannot be used to create arrays: " + type);
        }
        super.visitTypeInsn(opcode, type);
        ++insnCount;
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner,
            final String name, final String desc) {
        checkStartCode();
        checkEndCode();
        checkOpcode(opcode, 4);
        checkInternalName(owner, "owner");
        checkUnqualifiedName(version, name, "name");
        checkDesc(desc, false);
        super.visitFieldInsn(opcode, owner, name, desc);
        ++insnCount;
    }

    @Deprecated
    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc) {
        if (api >= Opcodes.ASM5) {
            super.visitMethodInsn(opcode, owner, name, desc);
            return;
        }
        doVisitMethodInsn(opcode, owner, name, desc,
                opcode == Opcodes.INVOKEINTERFACE);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (api < Opcodes.ASM5) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            return;
        }
        doVisitMethodInsn(opcode, owner, name, desc, itf);
    }

    private void doVisitMethodInsn(int opcode, final String owner,
            final String name, final String desc, final boolean itf) {
        checkStartCode();
        checkEndCode();
        checkOpcode(opcode, 5);
        if (opcode != Opcodes.INVOKESPECIAL || !"<init>".equals(name)) {
            checkMethodIdentifier(version, name, "name");
        }
        checkInternalName(owner, "owner");
        checkMethodDesc(desc);
        if (opcode == Opcodes.INVOKEVIRTUAL && itf) {
            throw new IllegalArgumentException(
                    "INVOKEVIRTUAL can't be used with interfaces");
        }
        if (opcode == Opcodes.INVOKEINTERFACE && !itf) {
            throw new IllegalArgumentException(
                    "INVOKEINTERFACE can't be used with classes");
        }
        // Calling super.visitMethodInsn requires to call the correct version
        // depending on this.api (otherwise infinite loops can occur). To
        // simplify and to make it easier to automatically remove the backward
        // compatibility code, we inline the code of the overridden method here.
        if (mv != null) {
            mv.visitMethodInsn(opcode, owner, name, desc, itf);
        }
        ++insnCount;
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
            Object... bsmArgs) {
        checkStartCode();
        checkEndCode();
        checkMethodIdentifier(version, name, "name");
        checkMethodDesc(desc);
        if (bsm.getTag() != Opcodes.H_INVOKESTATIC
                && bsm.getTag() != Opcodes.H_NEWINVOKESPECIAL) {
            throw new IllegalArgumentException("invalid handle tag "
                    + bsm.getTag());
        }
        for (int i = 0; i < bsmArgs.length; i++) {
            checkLDCConstant(bsmArgs[i]);
        }
        super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        ++insnCount;
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        checkStartCode();
        checkEndCode();
        checkOpcode(opcode, 6);
        checkLabel(label, false, "label");
        checkNonDebugLabel(label);
        super.visitJumpInsn(opcode, label);
        usedLabels.add(label);
        ++insnCount;
    }

    @Override
    public void visitLabel(final Label label) {
        checkStartCode();
        checkEndCode();
        checkLabel(label, false, "label");
        if (labels.get(label) != null) {
            throw new IllegalArgumentException("Already visited label");
        }
        labels.put(label, new Integer(insnCount));
        super.visitLabel(label);
    }

    @Override
    public void visitLdcInsn(final Object cst) {
        checkStartCode();
        checkEndCode();
        checkLDCConstant(cst);
        super.visitLdcInsn(cst);
        ++insnCount;
    }

    @Override
    public void visitIincInsn(final int var, final int increment) {
        checkStartCode();
        checkEndCode();
        checkUnsignedShort(var, "Invalid variable index");
        checkSignedShort(increment, "Invalid increment");
        super.visitIincInsn(var, increment);
        ++insnCount;
    }

    @Override
    public void visitTableSwitchInsn(final int min, final int max,
            final Label dflt, final Label... labels) {
        checkStartCode();
        checkEndCode();
        if (max < min) {
            throw new IllegalArgumentException("Max = " + max
                    + " must be greater than or equal to min = " + min);
        }
        checkLabel(dflt, false, "default label");
        checkNonDebugLabel(dflt);
        if (labels == null || labels.length != max - min + 1) {
            throw new IllegalArgumentException(
                    "There must be max - min + 1 labels");
        }
        for (int i = 0; i < labels.length; ++i) {
            checkLabel(labels[i], false, "label at index " + i);
            checkNonDebugLabel(labels[i]);
        }
        super.visitTableSwitchInsn(min, max, dflt, labels);
        for (int i = 0; i < labels.length; ++i) {
            usedLabels.add(labels[i]);
        }
        ++insnCount;
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys,
            final Label[] labels) {
        checkEndCode();
        checkStartCode();
        checkLabel(dflt, false, "default label");
        checkNonDebugLabel(dflt);
        if (keys == null || labels == null || keys.length != labels.length) {
            throw new IllegalArgumentException(
                    "There must be the same number of keys and labels");
        }
        for (int i = 0; i < labels.length; ++i) {
            checkLabel(labels[i], false, "label at index " + i);
            checkNonDebugLabel(labels[i]);
        }
        super.visitLookupSwitchInsn(dflt, keys, labels);
        usedLabels.add(dflt);
        for (int i = 0; i < labels.length; ++i) {
            usedLabels.add(labels[i]);
        }
        ++insnCount;
    }

    @Override
    public void visitMultiANewArrayInsn(final String desc, final int dims) {
        checkStartCode();
        checkEndCode();
        checkDesc(desc, false);
        if (desc.charAt(0) != '[') {
            throw new IllegalArgumentException(
                    "Invalid descriptor (must be an array type descriptor): "
                            + desc);
        }
        if (dims < 1) {
            throw new IllegalArgumentException(
                    "Invalid dimensions (must be greater than 0): " + dims);
        }
        if (dims > desc.lastIndexOf('[') + 1) {
            throw new IllegalArgumentException(
                    "Invalid dimensions (must not be greater than dims(desc)): "
                            + dims);
        }
        super.visitMultiANewArrayInsn(desc, dims);
        ++insnCount;
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        checkStartCode();
        checkEndCode();
        int sort = typeRef >>> 24;
        if (sort != TypeReference.INSTANCEOF && sort != TypeReference.NEW
                && sort != TypeReference.CONSTRUCTOR_REFERENCE
                && sort != TypeReference.METHOD_REFERENCE
                && sort != TypeReference.CAST
                && sort != TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT
                && sort != TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT
                && sort != TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT
                && sort != TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT) {
            throw new IllegalArgumentException("Invalid type reference sort 0x"
                    + Integer.toHexString(sort));
        }
        CheckClassAdapter.checkTypeRefAndPath(typeRef, typePath);
        CheckMethodAdapter.checkDesc(desc, false);
        return new CheckAnnotationAdapter(super.visitInsnAnnotation(typeRef,
                typePath, desc, visible));
    }

    @Override
    public void visitTryCatchBlock(final Label start, final Label end,
            final Label handler, final String type) {
        checkStartCode();
        checkEndCode();
        checkLabel(start, false, "start label");
        checkLabel(end, false, "end label");
        checkLabel(handler, false, "handler label");
        checkNonDebugLabel(start);
        checkNonDebugLabel(end);
        checkNonDebugLabel(handler);
        if (labels.get(start) != null || labels.get(end) != null
                || labels.get(handler) != null) {
            throw new IllegalStateException(
                    "Try catch blocks must be visited before their labels");
        }
        if (type != null) {
            checkInternalName(type, "type");
        }
        super.visitTryCatchBlock(start, end, handler, type);
        handlers.add(start);
        handlers.add(end);
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(final int typeRef,
            final TypePath typePath, final String desc, final boolean visible) {
        checkStartCode();
        checkEndCode();
        int sort = typeRef >>> 24;
        if (sort != TypeReference.EXCEPTION_PARAMETER) {
            throw new IllegalArgumentException("Invalid type reference sort 0x"
                    + Integer.toHexString(sort));
        }
        CheckClassAdapter.checkTypeRefAndPath(typeRef, typePath);
        CheckMethodAdapter.checkDesc(desc, false);
        return new CheckAnnotationAdapter(super.visitTryCatchAnnotation(
                typeRef, typePath, desc, visible));
    }

    @Override
    public void visitLocalVariable(final String name, final String desc,
            final String signature, final Label start, final Label end,
            final int index) {
        checkStartCode();
        checkEndCode();
        checkUnqualifiedName(version, name, "name");
        checkDesc(desc, false);
        checkLabel(start, true, "start label");
        checkLabel(end, true, "end label");
        checkUnsignedShort(index, "Invalid variable index");
        int s = labels.get(start).intValue();
        int e = labels.get(end).intValue();
        if (e < s) {
            throw new IllegalArgumentException(
                    "Invalid start and end labels (end must be greater than start)");
        }
        super.visitLocalVariable(name, desc, signature, start, end, index);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,
            TypePath typePath, Label[] start, Label[] end, int[] index,
            String desc, boolean visible) {
        checkStartCode();
        checkEndCode();
        int sort = typeRef >>> 24;
        if (sort != TypeReference.LOCAL_VARIABLE
                && sort != TypeReference.RESOURCE_VARIABLE) {
            throw new IllegalArgumentException("Invalid type reference sort 0x"
                    + Integer.toHexString(sort));
        }
        CheckClassAdapter.checkTypeRefAndPath(typeRef, typePath);
        checkDesc(desc, false);
        if (start == null || end == null || index == null
                || end.length != start.length || index.length != start.length) {
            throw new IllegalArgumentException(
                    "Invalid start, end and index arrays (must be non null and of identical length");
        }
        for (int i = 0; i < start.length; ++i) {
            checkLabel(start[i], true, "start label");
            checkLabel(end[i], true, "end label");
            checkUnsignedShort(index[i], "Invalid variable index");
            int s = labels.get(start[i]).intValue();
            int e = labels.get(end[i]).intValue();
            if (e < s) {
                throw new IllegalArgumentException(
                        "Invalid start and end labels (end must be greater than start)");
            }
        }
        return super.visitLocalVariableAnnotation(typeRef, typePath, start,
                end, index, desc, visible);
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        checkStartCode();
        checkEndCode();
        checkUnsignedShort(line, "Invalid line number");
        checkLabel(start, true, "start label");
        super.visitLineNumber(line, start);
    }

    @Override
    public void visitMaxs(final int maxStack, final int maxLocals) {
        checkStartCode();
        checkEndCode();
        endCode = true;
        for (Label l : usedLabels) {
            if (labels.get(l) == null) {
                throw new IllegalStateException("Undefined label used");
            }
        }
        for (int i = 0; i < handlers.size();) {
            Integer start = labels.get(handlers.get(i++));
            Integer end = labels.get(handlers.get(i++));
            if (start == null || end == null) {
                throw new IllegalStateException(
                        "Undefined try catch block labels");
            }
            if (end.intValue() <= start.intValue()) {
                throw new IllegalStateException(
                        "Emty try catch block handler range");
            }
        }
        checkUnsignedShort(maxStack, "Invalid max stack");
        checkUnsignedShort(maxLocals, "Invalid max locals");
        super.visitMaxs(maxStack, maxLocals);
    }

    @Override
    public void visitEnd() {
        checkEndMethod();
        endMethod = true;
        super.visitEnd();
    }

    // -------------------------------------------------------------------------

    /**
     * Checks that the visitCode method has been called.
     */
    void checkStartCode() {
        if (!startCode) {
            throw new IllegalStateException(
                    "Cannot visit instructions before visitCode has been called.");
        }
    }

    /**
     * Checks that the visitMaxs method has not been called.
     */
    void checkEndCode() {
        if (endCode) {
            throw new IllegalStateException(
                    "Cannot visit instructions after visitMaxs has been called.");
        }
    }

    /**
     * Checks that the visitEnd method has not been called.
     */
    void checkEndMethod() {
        if (endMethod) {
            throw new IllegalStateException(
                    "Cannot visit elements after visitEnd has been called.");
        }
    }

    /**
     * Checks a stack frame value.
     *
     * @param value
     *            the value to be checked.
     */
    void checkFrameValue(final Object value) {
        if (value == Opcodes.TOP || value == Opcodes.INTEGER
                || value == Opcodes.FLOAT || value == Opcodes.LONG
                || value == Opcodes.DOUBLE || value == Opcodes.NULL
                || value == Opcodes.UNINITIALIZED_THIS) {
            return;
        }
        if (value instanceof String) {
            checkInternalName((String) value, "Invalid stack frame value");
            return;
        }
        if (!(value instanceof Label)) {
            throw new IllegalArgumentException("Invalid stack frame value: "
                    + value);
        } else {
            usedLabels.add((Label) value);
        }
    }

    /**
     * Checks that the type of the given opcode is equal to the given type.
     *
     * @param opcode
     *            the opcode to be checked.
     * @param type
     *            the expected opcode type.
     */
    static void checkOpcode(final int opcode, final int type) {
        if (opcode < 0 || opcode > 199 || TYPE[opcode] != type) {
            throw new IllegalArgumentException("Invalid opcode: " + opcode);
        }
    }

    /**
     * Checks that the given value is a signed byte.
     *
     * @param value
     *            the value to be checked.
     * @param msg
     *            an message to be used in case of error.
     */
    static void checkSignedByte(final int value, final String msg) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(msg
                    + " (must be a signed byte): " + value);
        }
    }

    /**
     * Checks that the given value is a signed short.
     *
     * @param value
     *            the value to be checked.
     * @param msg
     *            an message to be used in case of error.
     */
    static void checkSignedShort(final int value, final String msg) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(msg
                    + " (must be a signed short): " + value);
        }
    }

    /**
     * Checks that the given value is an unsigned short.
     *
     * @param value
     *            the value to be checked.
     * @param msg
     *            an message to be used in case of error.
     */
    static void checkUnsignedShort(final int value, final String msg) {
        if (value < 0 || value > 65535) {
            throw new IllegalArgumentException(msg
                    + " (must be an unsigned short): " + value);
        }
    }

    /**
     * Checks that the given value is an {@link Integer}, a{@link Float}, a
     * {@link Long}, a {@link Double} or a {@link String}.
     *
     * @param cst
     *            the value to be checked.
     */
    static void checkConstant(final Object cst) {
        if (!(cst instanceof Integer) && !(cst instanceof Float)
                && !(cst instanceof Long) && !(cst instanceof Double)
                && !(cst instanceof String)) {
            throw new IllegalArgumentException("Invalid constant: " + cst);
        }
    }

    void checkLDCConstant(final Object cst) {
        if (cst instanceof Type) {
            int s = ((Type) cst).getSort();
            if (s != Type.OBJECT && s != Type.ARRAY && s != Type.METHOD) {
                throw new IllegalArgumentException("Illegal LDC constant value");
            }
            if (s != Type.METHOD && (version & 0xFFFF) < Opcodes.V1_5) {
                throw new IllegalArgumentException(
                        "ldc of a constant class requires at least version 1.5");
            }
            if (s == Type.METHOD && (version & 0xFFFF) < Opcodes.V1_7) {
                throw new IllegalArgumentException(
                        "ldc of a method type requires at least version 1.7");
            }
        } else if (cst instanceof Handle) {
            if ((version & 0xFFFF) < Opcodes.V1_7) {
                throw new IllegalArgumentException(
                        "ldc of a handle requires at least version 1.7");
            }
            int tag = ((Handle) cst).getTag();
            if (tag < Opcodes.H_GETFIELD || tag > Opcodes.H_INVOKEINTERFACE) {
                throw new IllegalArgumentException("invalid handle tag " + tag);
            }
        } else {
            checkConstant(cst);
        }
    }

    /**
     * Checks that the given string is a valid unqualified name.
     *
     * @param version
     *            the class version.
     * @param name
     *            the string to be checked.
     * @param msg
     *            a message to be used in case of error.
     */
    static void checkUnqualifiedName(int version, final String name,
            final String msg) {
        if ((version & 0xFFFF) < Opcodes.V1_5) {
            checkIdentifier(name, msg);
        } else {
            for (int i = 0; i < name.length(); ++i) {
                if (".;[/".indexOf(name.charAt(i)) != -1) {
                    throw new IllegalArgumentException("Invalid " + msg
                            + " (must be a valid unqualified name): " + name);
                }
            }
        }
    }

    /**
     * Checks that the given string is a valid Java identifier.
     *
     * @param name
     *            the string to be checked.
     * @param msg
     *            a message to be used in case of error.
     */
    static void checkIdentifier(final String name, final String msg) {
        checkIdentifier(name, 0, -1, msg);
    }

    /**
     * Checks that the given substring is a valid Java identifier.
     *
     * @param name
     *            the string to be checked.
     * @param start
     *            index of the first character of the identifier (inclusive).
     * @param end
     *            index of the last character of the identifier (exclusive). -1
     *            is equivalent to <tt>name.length()</tt> if name is not
     *            <tt>null</tt>.
     * @param msg
     *            a message to be used in case of error.
     */
    static void checkIdentifier(final String name, final int start,
            final int end, final String msg) {
        if (name == null || (end == -1 ? name.length() <= start : end <= start)) {
            throw new IllegalArgumentException("Invalid " + msg
                    + " (must not be null or empty)");
        }
        if (!Character.isJavaIdentifierStart(name.charAt(start))) {
            throw new IllegalArgumentException("Invalid " + msg
                    + " (must be a valid Java identifier): " + name);
        }
        int max = end == -1 ? name.length() : end;
        for (int i = start + 1; i < max; ++i) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                throw new IllegalArgumentException("Invalid " + msg
                        + " (must be a valid Java identifier): " + name);
            }
        }
    }

    /**
     * Checks that the given string is a valid Java identifier.
     *
     * @param version
     *            the class version.
     * @param name
     *            the string to be checked.
     * @param msg
     *            a message to be used in case of error.
     */
    static void checkMethodIdentifier(int version, final String name,
            final String msg) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Invalid " + msg
                    + " (must not be null or empty)");
        }
        if ((version & 0xFFFF) >= Opcodes.V1_5) {
            for (int i = 0; i < name.length(); ++i) {
                if (".;[/<>".indexOf(name.charAt(i)) != -1) {
                    throw new IllegalArgumentException("Invalid " + msg
                            + " (must be a valid unqualified name): " + name);
                }
            }
            return;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            throw new IllegalArgumentException(
                    "Invalid "
                            + msg
                            + " (must be a '<init>', '<clinit>' or a valid Java identifier): "
                            + name);
        }
        for (int i = 1; i < name.length(); ++i) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                throw new IllegalArgumentException(
                        "Invalid "
                                + msg
                                + " (must be '<init>' or '<clinit>' or a valid Java identifier): "
                                + name);
            }
        }
    }

    /**
     * Checks that the given string is a valid internal class name.
     *
     * @param name
     *            the string to be checked.
     * @param msg
     *            a message to be used in case of error.
     */
    static void checkInternalName(final String name, final String msg) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Invalid " + msg
                    + " (must not be null or empty)");
        }
        if (name.charAt(0) == '[') {
            checkDesc(name, false);
        } else {
            checkInternalName(name, 0, -1, msg);
        }
    }

    /**
     * Checks that the given substring is a valid internal class name.
     *
     * @param name
     *            the string to be checked.
     * @param start
     *            index of the first character of the identifier (inclusive).
     * @param end
     *            index of the last character of the identifier (exclusive). -1
     *            is equivalent to <tt>name.length()</tt> if name is not
     *            <tt>null</tt>.
     * @param msg
     *            a message to be used in case of error.
     */
    static void checkInternalName(final String name, final int start,
            final int end, final String msg) {
        int max = end == -1 ? name.length() : end;
        try {
            int begin = start;
            int slash;
            do {
                slash = name.indexOf('/', begin + 1);
                if (slash == -1 || slash > max) {
                    slash = max;
                }
                checkIdentifier(name, begin, slash, null);
                begin = slash + 1;
            } while (slash != max);
        } catch (IllegalArgumentException unused) {
            throw new IllegalArgumentException(
                    "Invalid "
                            + msg
                            + " (must be a fully qualified class name in internal form): "
                            + name);
        }
    }

    /**
     * Checks that the given string is a valid type descriptor.
     *
     * @param desc
     *            the string to be checked.
     * @param canBeVoid
     *            <tt>true</tt> if <tt>V</tt> can be considered valid.
     */
    static void checkDesc(final String desc, final boolean canBeVoid) {
        int end = checkDesc(desc, 0, canBeVoid);
        if (end != desc.length()) {
            throw new IllegalArgumentException("Invalid descriptor: " + desc);
        }
    }

    /**
     * Checks that a the given substring is a valid type descriptor.
     *
     * @param desc
     *            the string to be checked.
     * @param start
     *            index of the first character of the identifier (inclusive).
     * @param canBeVoid
     *            <tt>true</tt> if <tt>V</tt> can be considered valid.
     * @return the index of the last character of the type decriptor, plus one.
     */
    static int checkDesc(final String desc, final int start,
            final boolean canBeVoid) {
        if (desc == null || start >= desc.length()) {
            throw new IllegalArgumentException(
                    "Invalid type descriptor (must not be null or empty)");
        }
        int index;
        switch (desc.charAt(start)) {
        case 'V':
            if (canBeVoid) {
                return start + 1;
            } else {
                throw new IllegalArgumentException("Invalid descriptor: "
                        + desc);
            }
        case 'Z':
        case 'C':
        case 'B':
        case 'S':
        case 'I':
        case 'F':
        case 'J':
        case 'D':
            return start + 1;
        case '[':
            index = start + 1;
            while (index < desc.length() && desc.charAt(index) == '[') {
                ++index;
            }
            if (index < desc.length()) {
                return checkDesc(desc, index, false);
            } else {
                throw new IllegalArgumentException("Invalid descriptor: "
                        + desc);
            }
        case 'L':
            index = desc.indexOf(';', start);
            if (index == -1 || index - start < 2) {
                throw new IllegalArgumentException("Invalid descriptor: "
                        + desc);
            }
            try {
                checkInternalName(desc, start + 1, index, null);
            } catch (IllegalArgumentException unused) {
                throw new IllegalArgumentException("Invalid descriptor: "
                        + desc);
            }
            return index + 1;
        default:
            throw new IllegalArgumentException("Invalid descriptor: " + desc);
        }
    }

    /**
     * Checks that the given string is a valid method descriptor.
     *
     * @param desc
     *            the string to be checked.
     */
    static void checkMethodDesc(final String desc) {
        if (desc == null || desc.length() == 0) {
            throw new IllegalArgumentException(
                    "Invalid method descriptor (must not be null or empty)");
        }
        if (desc.charAt(0) != '(' || desc.length() < 3) {
            throw new IllegalArgumentException("Invalid descriptor: " + desc);
        }
        int start = 1;
        if (desc.charAt(start) != ')') {
            do {
                if (desc.charAt(start) == 'V') {
                    throw new IllegalArgumentException("Invalid descriptor: "
                            + desc);
                }
                start = checkDesc(desc, start, false);
            } while (start < desc.length() && desc.charAt(start) != ')');
        }
        start = checkDesc(desc, start + 1, true);
        if (start != desc.length()) {
            throw new IllegalArgumentException("Invalid descriptor: " + desc);
        }
    }

    /**
     * Checks that the given label is not null. This method can also check that
     * the label has been visited.
     *
     * @param label
     *            the label to be checked.
     * @param checkVisited
     *            <tt>true</tt> to check that the label has been visited.
     * @param msg
     *            a message to be used in case of error.
     */
    void checkLabel(final Label label, final boolean checkVisited,
            final String msg) {
        if (label == null) {
            throw new IllegalArgumentException("Invalid " + msg
                    + " (must not be null)");
        }
        if (checkVisited && labels.get(label) == null) {
            throw new IllegalArgumentException("Invalid " + msg
                    + " (must be visited first)");
        }
    }

    /**
     * Checks that the given label is not a label used only for debug purposes.
     *
     * @param label
     *            the label to be checked.
     */
    private static void checkNonDebugLabel(final Label label) {
        Field f = getLabelStatusField();
        int status = 0;
        try {
            status = f == null ? 0 : ((Integer) f.get(label)).intValue();
        } catch (IllegalAccessException e) {
            throw new Error("Internal error");
        }
        if ((status & 0x01) != 0) {
            throw new IllegalArgumentException(
                    "Labels used for debug info cannot be reused for control flow");
        }
    }

    /**
     * Returns the Field object corresponding to the Label.status field.
     *
     * @return the Field object corresponding to the Label.status field.
     */
    private static Field getLabelStatusField() {
        if (labelStatusField == null) {
            labelStatusField = getLabelField("a");
            if (labelStatusField == null) {
                labelStatusField = getLabelField("status");
            }
        }
        return labelStatusField;
    }

    /**
     * Returns the field of the Label class whose name is given.
     *
     * @param name
     *            a field name.
     * @return the field of the Label class whose name is given, or null.
     */
    private static Field getLabelField(final String name) {
        try {
            Field f = Label.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
