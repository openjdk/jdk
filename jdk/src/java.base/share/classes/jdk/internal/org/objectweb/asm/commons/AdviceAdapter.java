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
package jdk.internal.org.objectweb.asm.commons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

/**
 * A {@link jdk.internal.org.objectweb.asm.MethodVisitor} to insert before, after and around
 * advices in methods and constructors.
 * <p>
 * The behavior for constructors is like this:
 * <ol>
 *
 * <li>as long as the INVOKESPECIAL for the object initialization has not been
 * reached, every bytecode instruction is dispatched in the ctor code visitor</li>
 *
 * <li>when this one is reached, it is only added in the ctor code visitor and a
 * JP invoke is added</li>
 *
 * <li>after that, only the other code visitor receives the instructions</li>
 *
 * </ol>
 *
 * @author Eugene Kuleshov
 * @author Eric Bruneton
 */
public abstract class AdviceAdapter extends GeneratorAdapter implements Opcodes {

    private static final Object THIS = new Object();

    private static final Object OTHER = new Object();

    protected int methodAccess;

    protected String methodDesc;

    private boolean constructor;

    private boolean superInitialized;

    private List<Object> stackFrame;

    private Map<Label, List<Object>> branches;

    /**
     * Creates a new {@link AdviceAdapter}.
     *
     * @param api
     *            the ASM API version implemented by this visitor. Must be one
     *            of {@link Opcodes#ASM4} or {@link Opcodes#ASM5}.
     * @param mv
     *            the method visitor to which this adapter delegates calls.
     * @param access
     *            the method's access flags (see {@link Opcodes}).
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link Type Type}).
     */
    protected AdviceAdapter(final int api, final MethodVisitor mv,
            final int access, final String name, final String desc) {
        super(api, mv, access, name, desc);
        methodAccess = access;
        methodDesc = desc;
        constructor = "<init>".equals(name);
    }

    @Override
    public void visitCode() {
        mv.visitCode();
        if (constructor) {
            stackFrame = new ArrayList<Object>();
            branches = new HashMap<Label, List<Object>>();
        } else {
            superInitialized = true;
            onMethodEnter();
        }
    }

    @Override
    public void visitLabel(final Label label) {
        mv.visitLabel(label);
        if (constructor && branches != null) {
            List<Object> frame = branches.get(label);
            if (frame != null) {
                stackFrame = frame;
                branches.remove(label);
            }
        }
    }

    @Override
    public void visitInsn(final int opcode) {
        if (constructor) {
            int s;
            switch (opcode) {
            case RETURN: // empty stack
                onMethodExit(opcode);
                break;
            case IRETURN: // 1 before n/a after
            case FRETURN: // 1 before n/a after
            case ARETURN: // 1 before n/a after
            case ATHROW: // 1 before n/a after
                popValue();
                onMethodExit(opcode);
                break;
            case LRETURN: // 2 before n/a after
            case DRETURN: // 2 before n/a after
                popValue();
                popValue();
                onMethodExit(opcode);
                break;
            case NOP:
            case LALOAD: // remove 2 add 2
            case DALOAD: // remove 2 add 2
            case LNEG:
            case DNEG:
            case FNEG:
            case INEG:
            case L2D:
            case D2L:
            case F2I:
            case I2B:
            case I2C:
            case I2S:
            case I2F:
            case ARRAYLENGTH:
                break;
            case ACONST_NULL:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case F2L: // 1 before 2 after
            case F2D:
            case I2L:
            case I2D:
                pushValue(OTHER);
                break;
            case LCONST_0:
            case LCONST_1:
            case DCONST_0:
            case DCONST_1:
                pushValue(OTHER);
                pushValue(OTHER);
                break;
            case IALOAD: // remove 2 add 1
            case FALOAD: // remove 2 add 1
            case AALOAD: // remove 2 add 1
            case BALOAD: // remove 2 add 1
            case CALOAD: // remove 2 add 1
            case SALOAD: // remove 2 add 1
            case POP:
            case IADD:
            case FADD:
            case ISUB:
            case LSHL: // 3 before 2 after
            case LSHR: // 3 before 2 after
            case LUSHR: // 3 before 2 after
            case L2I: // 2 before 1 after
            case L2F: // 2 before 1 after
            case D2I: // 2 before 1 after
            case D2F: // 2 before 1 after
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
            case FCMPL: // 2 before 1 after
            case FCMPG: // 2 before 1 after
            case IMUL:
            case IDIV:
            case IREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
            case MONITORENTER:
            case MONITOREXIT:
                popValue();
                break;
            case POP2:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LADD:
            case LAND:
            case LOR:
            case LXOR:
            case DADD:
            case DMUL:
            case DSUB:
            case DDIV:
            case DREM:
                popValue();
                popValue();
                break;
            case IASTORE:
            case FASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
            case LCMP: // 4 before 1 after
            case DCMPL:
            case DCMPG:
                popValue();
                popValue();
                popValue();
                break;
            case LASTORE:
            case DASTORE:
                popValue();
                popValue();
                popValue();
                popValue();
                break;
            case DUP:
                pushValue(peekValue());
                break;
            case DUP_X1:
                s = stackFrame.size();
                stackFrame.add(s - 2, stackFrame.get(s - 1));
                break;
            case DUP_X2:
                s = stackFrame.size();
                stackFrame.add(s - 3, stackFrame.get(s - 1));
                break;
            case DUP2:
                s = stackFrame.size();
                stackFrame.add(s - 2, stackFrame.get(s - 1));
                stackFrame.add(s - 2, stackFrame.get(s - 1));
                break;
            case DUP2_X1:
                s = stackFrame.size();
                stackFrame.add(s - 3, stackFrame.get(s - 1));
                stackFrame.add(s - 3, stackFrame.get(s - 1));
                break;
            case DUP2_X2:
                s = stackFrame.size();
                stackFrame.add(s - 4, stackFrame.get(s - 1));
                stackFrame.add(s - 4, stackFrame.get(s - 1));
                break;
            case SWAP:
                s = stackFrame.size();
                stackFrame.add(s - 2, stackFrame.get(s - 1));
                stackFrame.remove(s);
                break;
            }
        } else {
            switch (opcode) {
            case RETURN:
            case IRETURN:
            case FRETURN:
            case ARETURN:
            case LRETURN:
            case DRETURN:
            case ATHROW:
                onMethodExit(opcode);
                break;
            }
        }
        mv.visitInsn(opcode);
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        super.visitVarInsn(opcode, var);
        if (constructor) {
            switch (opcode) {
            case ILOAD:
            case FLOAD:
                pushValue(OTHER);
                break;
            case LLOAD:
            case DLOAD:
                pushValue(OTHER);
                pushValue(OTHER);
                break;
            case ALOAD:
                pushValue(var == 0 ? THIS : OTHER);
                break;
            case ASTORE:
            case ISTORE:
            case FSTORE:
                popValue();
                break;
            case LSTORE:
            case DSTORE:
                popValue();
                popValue();
                break;
            }
        }
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner,
            final String name, final String desc) {
        mv.visitFieldInsn(opcode, owner, name, desc);
        if (constructor) {
            char c = desc.charAt(0);
            boolean longOrDouble = c == 'J' || c == 'D';
            switch (opcode) {
            case GETSTATIC:
                pushValue(OTHER);
                if (longOrDouble) {
                    pushValue(OTHER);
                }
                break;
            case PUTSTATIC:
                popValue();
                if (longOrDouble) {
                    popValue();
                }
                break;
            case PUTFIELD:
                popValue();
                popValue();
                if (longOrDouble) {
                    popValue();
                }
                break;
            // case GETFIELD:
            default:
                if (longOrDouble) {
                    pushValue(OTHER);
                }
            }
        }
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        mv.visitIntInsn(opcode, operand);
        if (constructor && opcode != NEWARRAY) {
            pushValue(OTHER);
        }
    }

    @Override
    public void visitLdcInsn(final Object cst) {
        mv.visitLdcInsn(cst);
        if (constructor) {
            pushValue(OTHER);
            if (cst instanceof Double || cst instanceof Long) {
                pushValue(OTHER);
            }
        }
    }

    @Override
    public void visitMultiANewArrayInsn(final String desc, final int dims) {
        mv.visitMultiANewArrayInsn(desc, dims);
        if (constructor) {
            for (int i = 0; i < dims; i++) {
                popValue();
            }
            pushValue(OTHER);
        }
    }

    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        mv.visitTypeInsn(opcode, type);
        // ANEWARRAY, CHECKCAST or INSTANCEOF don't change stack
        if (constructor && opcode == NEW) {
            pushValue(OTHER);
        }
    }

    @Deprecated
    @Override
    public void visitMethodInsn(final int opcode, final String owner,
            final String name, final String desc) {
        if (api >= Opcodes.ASM5) {
            super.visitMethodInsn(opcode, owner, name, desc);
            return;
        }
        doVisitMethodInsn(opcode, owner, name, desc,
                opcode == Opcodes.INVOKEINTERFACE);
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner,
            final String name, final String desc, final boolean itf) {
        if (api < Opcodes.ASM5) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            return;
        }
        doVisitMethodInsn(opcode, owner, name, desc, itf);
    }

    private void doVisitMethodInsn(int opcode, final String owner,
            final String name, final String desc, final boolean itf) {
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
        if (constructor) {
            Type[] types = Type.getArgumentTypes(desc);
            for (int i = 0; i < types.length; i++) {
                popValue();
                if (types[i].getSize() == 2) {
                    popValue();
                }
            }
            switch (opcode) {
            // case INVOKESTATIC:
            // break;
            case INVOKEINTERFACE:
            case INVOKEVIRTUAL:
                popValue(); // objectref
                break;
            case INVOKESPECIAL:
                Object type = popValue(); // objectref
                if (type == THIS && !superInitialized) {
                    onMethodEnter();
                    superInitialized = true;
                    // once super has been initialized it is no longer
                    // necessary to keep track of stack state
                    constructor = false;
                }
                break;
            }

            Type returnType = Type.getReturnType(desc);
            if (returnType != Type.VOID_TYPE) {
                pushValue(OTHER);
                if (returnType.getSize() == 2) {
                    pushValue(OTHER);
                }
            }
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
            Object... bsmArgs) {
        mv.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        if (constructor) {
            Type[] types = Type.getArgumentTypes(desc);
            for (int i = 0; i < types.length; i++) {
                popValue();
                if (types[i].getSize() == 2) {
                    popValue();
                }
            }

            Type returnType = Type.getReturnType(desc);
            if (returnType != Type.VOID_TYPE) {
                pushValue(OTHER);
                if (returnType.getSize() == 2) {
                    pushValue(OTHER);
                }
            }
        }
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        mv.visitJumpInsn(opcode, label);
        if (constructor) {
            switch (opcode) {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IFNULL:
            case IFNONNULL:
                popValue();
                break;
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
                popValue();
                popValue();
                break;
            case JSR:
                pushValue(OTHER);
                break;
            }
            addBranch(label);
        }
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys,
            final Label[] labels) {
        mv.visitLookupSwitchInsn(dflt, keys, labels);
        if (constructor) {
            popValue();
            addBranches(dflt, labels);
        }
    }

    @Override
    public void visitTableSwitchInsn(final int min, final int max,
            final Label dflt, final Label... labels) {
        mv.visitTableSwitchInsn(min, max, dflt, labels);
        if (constructor) {
            popValue();
            addBranches(dflt, labels);
        }
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler,
            String type) {
        super.visitTryCatchBlock(start, end, handler, type);
        if (constructor && !branches.containsKey(handler)) {
            List<Object> stackFrame = new ArrayList<Object>();
            stackFrame.add(OTHER);
            branches.put(handler, stackFrame);
        }
    }

    private void addBranches(final Label dflt, final Label[] labels) {
        addBranch(dflt);
        for (int i = 0; i < labels.length; i++) {
            addBranch(labels[i]);
        }
    }

    private void addBranch(final Label label) {
        if (branches.containsKey(label)) {
            return;
        }
        branches.put(label, new ArrayList<Object>(stackFrame));
    }

    private Object popValue() {
        return stackFrame.remove(stackFrame.size() - 1);
    }

    private Object peekValue() {
        return stackFrame.get(stackFrame.size() - 1);
    }

    private void pushValue(final Object o) {
        stackFrame.add(o);
    }

    /**
     * Called at the beginning of the method or after super class call in
     * the constructor. <br>
     * <br>
     *
     * <i>Custom code can use or change all the local variables, but should not
     * change state of the stack.</i>
     */
    protected void onMethodEnter() {
    }

    /**
     * Called before explicit exit from the method using either return or throw.
     * Top element on the stack contains the return value or exception instance.
     * For example:
     *
     * <pre>
     *   public void onMethodExit(int opcode) {
     *     if(opcode==RETURN) {
     *         visitInsn(ACONST_NULL);
     *     } else if(opcode==ARETURN || opcode==ATHROW) {
     *         dup();
     *     } else {
     *         if(opcode==LRETURN || opcode==DRETURN) {
     *             dup2();
     *         } else {
     *             dup();
     *         }
     *         box(Type.getReturnType(this.methodDesc));
     *     }
     *     visitIntInsn(SIPUSH, opcode);
     *     visitMethodInsn(INVOKESTATIC, owner, "onExit", "(Ljava/lang/Object;I)V");
     *   }
     *
     *   // an actual call back method
     *   public static void onExit(Object param, int opcode) {
     *     ...
     * </pre>
     *
     * <br>
     * <br>
     *
     * <i>Custom code can use or change all the local variables, but should not
     * change state of the stack.</i>
     *
     * @param opcode
     *            one of the RETURN, IRETURN, FRETURN, ARETURN, LRETURN, DRETURN
     *            or ATHROW
     *
     */
    protected void onMethodExit(int opcode) {
    }

    // TODO onException, onMethodCall
}
