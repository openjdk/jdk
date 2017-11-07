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

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.TypePath;

/**
 * A {@link MethodVisitor} that renumbers local variables in their order of
 * appearance. This adapter allows one to easily add new local variables to a
 * method. It may be used by inheriting from this class, but the preferred way
 * of using it is via delegation: the next visitor in the chain can indeed add
 * new locals when needed by calling {@link #newLocal} on this adapter (this
 * requires a reference back to this {@link LocalVariablesSorter}).
 *
 * @author Chris Nokleberg
 * @author Eugene Kuleshov
 * @author Eric Bruneton
 */
public class LocalVariablesSorter extends MethodVisitor {

    private static final Type OBJECT_TYPE = Type
            .getObjectType("java/lang/Object");

    /**
     * Mapping from old to new local variable indexes. A local variable at index
     * i of size 1 is remapped to 'mapping[2*i]', while a local variable at
     * index i of size 2 is remapped to 'mapping[2*i+1]'.
     */
    private int[] mapping = new int[40];

    /**
     * Array used to store stack map local variable types after remapping.
     */
    private Object[] newLocals = new Object[20];

    /**
     * Index of the first local variable, after formal parameters.
     */
    protected final int firstLocal;

    /**
     * Index of the next local variable to be created by {@link #newLocal}.
     */
    protected int nextLocal;

    /**
     * Creates a new {@link LocalVariablesSorter}. <i>Subclasses must not use
     * this constructor</i>. Instead, they must use the
     * {@link #LocalVariablesSorter(int, int, String, MethodVisitor)} version.
     *
     * @param access
     *            access flags of the adapted method.
     * @param desc
     *            the method's descriptor (see {@link Type Type}).
     * @param mv
     *            the method visitor to which this adapter delegates calls.
     * @throws IllegalStateException
     *             If a subclass calls this constructor.
     */
    public LocalVariablesSorter(final int access, final String desc,
            final MethodVisitor mv) {
        this(Opcodes.ASM6, access, desc, mv);
        if (getClass() != LocalVariablesSorter.class) {
            throw new IllegalStateException();
        }
    }

    /**
     * Creates a new {@link LocalVariablesSorter}.
     *
     * @param api
     *            the ASM API version implemented by this visitor. Must be one
     *            of {@link Opcodes#ASM4}, {@link Opcodes#ASM5} or {@link Opcodes#ASM6}.
     * @param access
     *            access flags of the adapted method.
     * @param desc
     *            the method's descriptor (see {@link Type Type}).
     * @param mv
     *            the method visitor to which this adapter delegates calls.
     */
    protected LocalVariablesSorter(final int api, final int access,
            final String desc, final MethodVisitor mv) {
        super(api, mv);
        Type[] args = Type.getArgumentTypes(desc);
        nextLocal = (Opcodes.ACC_STATIC & access) == 0 ? 1 : 0;
        for (int i = 0; i < args.length; i++) {
            nextLocal += args[i].getSize();
        }
        firstLocal = nextLocal;
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        Type type;
        switch (opcode) {
        case Opcodes.LLOAD:
        case Opcodes.LSTORE:
            type = Type.LONG_TYPE;
            break;

        case Opcodes.DLOAD:
        case Opcodes.DSTORE:
            type = Type.DOUBLE_TYPE;
            break;

        case Opcodes.FLOAD:
        case Opcodes.FSTORE:
            type = Type.FLOAT_TYPE;
            break;

        case Opcodes.ILOAD:
        case Opcodes.ISTORE:
            type = Type.INT_TYPE;
            break;

        default:
            // case Opcodes.ALOAD:
            // case Opcodes.ASTORE:
            // case RET:
            type = OBJECT_TYPE;
            break;
        }
        mv.visitVarInsn(opcode, remap(var, type));
    }

    @Override
    public void visitIincInsn(final int var, final int increment) {
        mv.visitIincInsn(remap(var, Type.INT_TYPE), increment);
    }

    @Override
    public void visitMaxs(final int maxStack, final int maxLocals) {
        mv.visitMaxs(maxStack, nextLocal);
    }

    @Override
    public void visitLocalVariable(final String name, final String desc,
            final String signature, final Label start, final Label end,
            final int index) {
        int newIndex = remap(index, Type.getType(desc));
        mv.visitLocalVariable(name, desc, signature, start, end, newIndex);
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef,
            TypePath typePath, Label[] start, Label[] end, int[] index,
            String desc, boolean visible) {
        Type t = Type.getType(desc);
        int[] newIndex = new int[index.length];
        for (int i = 0; i < newIndex.length; ++i) {
            newIndex[i] = remap(index[i], t);
        }
        return mv.visitLocalVariableAnnotation(typeRef, typePath, start, end,
                newIndex, desc, visible);
    }

    @Override
    public void visitFrame(final int type, final int nLocal,
            final Object[] local, final int nStack, final Object[] stack) {
        if (type != Opcodes.F_NEW) { // uncompressed frame
            throw new IllegalStateException(
                    "ClassReader.accept() should be called with EXPAND_FRAMES flag");
        }

        // creates a copy of newLocals
        Object[] oldLocals = new Object[newLocals.length];
        System.arraycopy(newLocals, 0, oldLocals, 0, oldLocals.length);

        updateNewLocals(newLocals);

        // copies types from 'local' to 'newLocals'
        // 'newLocals' already contains the variables added with 'newLocal'

        int index = 0; // old local variable index
        int number = 0; // old local variable number
        for (; number < nLocal; ++number) {
            Object t = local[number];
            int size = t == Opcodes.LONG || t == Opcodes.DOUBLE ? 2 : 1;
            if (t != Opcodes.TOP) {
                Type typ = OBJECT_TYPE;
                if (t == Opcodes.INTEGER) {
                    typ = Type.INT_TYPE;
                } else if (t == Opcodes.FLOAT) {
                    typ = Type.FLOAT_TYPE;
                } else if (t == Opcodes.LONG) {
                    typ = Type.LONG_TYPE;
                } else if (t == Opcodes.DOUBLE) {
                    typ = Type.DOUBLE_TYPE;
                } else if (t instanceof String) {
                    typ = Type.getObjectType((String) t);
                }
                setFrameLocal(remap(index, typ), t);
            }
            index += size;
        }

        // removes TOP after long and double types as well as trailing TOPs

        index = 0;
        number = 0;
        for (int i = 0; index < newLocals.length; ++i) {
            Object t = newLocals[index++];
            if (t != null && t != Opcodes.TOP) {
                newLocals[i] = t;
                number = i + 1;
                if (t == Opcodes.LONG || t == Opcodes.DOUBLE) {
                    index += 1;
                }
            } else {
                newLocals[i] = Opcodes.TOP;
            }
        }

        // visits remapped frame
        mv.visitFrame(type, number, newLocals, nStack, stack);

        // restores original value of 'newLocals'
        newLocals = oldLocals;
    }

    // -------------

    /**
     * Creates a new local variable of the given type.
     *
     * @param type
     *            the type of the local variable to be created.
     * @return the identifier of the newly created local variable.
     */
    public int newLocal(final Type type) {
        Object t;
        switch (type.getSort()) {
        case Type.BOOLEAN:
        case Type.CHAR:
        case Type.BYTE:
        case Type.SHORT:
        case Type.INT:
            t = Opcodes.INTEGER;
            break;
        case Type.FLOAT:
            t = Opcodes.FLOAT;
            break;
        case Type.LONG:
            t = Opcodes.LONG;
            break;
        case Type.DOUBLE:
            t = Opcodes.DOUBLE;
            break;
        case Type.ARRAY:
            t = type.getDescriptor();
            break;
        // case Type.OBJECT:
        default:
            t = type.getInternalName();
            break;
        }
        int local = newLocalMapping(type);
        setLocalType(local, type);
        setFrameLocal(local, t);
        return local;
    }

    /**
     * Notifies subclasses that a new stack map frame is being visited. The
     * array argument contains the stack map frame types corresponding to the
     * local variables added with {@link #newLocal}. This method can update
     * these types in place for the stack map frame being visited. The default
     * implementation of this method does nothing, i.e. a local variable added
     * with {@link #newLocal} will have the same type in all stack map frames.
     * But this behavior is not always the desired one, for instance if a local
     * variable is added in the middle of a try/catch block: the frame for the
     * exception handler should have a TOP type for this new local.
     *
     * @param newLocals
     *            the stack map frame types corresponding to the local variables
     *            added with {@link #newLocal} (and null for the others). The
     *            format of this array is the same as in
     *            {@link MethodVisitor#visitFrame}, except that long and double
     *            types use two slots. The types for the current stack map frame
     *            must be updated in place in this array.
     */
    protected void updateNewLocals(Object[] newLocals) {
    }

    /**
     * Notifies subclasses that a local variable has been added or remapped. The
     * default implementation of this method does nothing.
     *
     * @param local
     *            a local variable identifier, as returned by {@link #newLocal
     *            newLocal()}.
     * @param type
     *            the type of the value being stored in the local variable.
     */
    protected void setLocalType(final int local, final Type type) {
    }

    private void setFrameLocal(final int local, final Object type) {
        int l = newLocals.length;
        if (local >= l) {
            Object[] a = new Object[Math.max(2 * l, local + 1)];
            System.arraycopy(newLocals, 0, a, 0, l);
            newLocals = a;
        }
        newLocals[local] = type;
    }

    private int remap(final int var, final Type type) {
        if (var + type.getSize() <= firstLocal) {
            return var;
        }
        int key = 2 * var + type.getSize() - 1;
        int size = mapping.length;
        if (key >= size) {
            int[] newMapping = new int[Math.max(2 * size, key + 1)];
            System.arraycopy(mapping, 0, newMapping, 0, size);
            mapping = newMapping;
        }
        int value = mapping[key];
        if (value == 0) {
            value = newLocalMapping(type);
            setLocalType(value, type);
            mapping[key] = value + 1;
        } else {
            value--;
        }
        return value;
    }

    protected int newLocalMapping(final Type type) {
        int local = nextLocal;
        nextLocal += type.getSize();
        return local;
    }
}
