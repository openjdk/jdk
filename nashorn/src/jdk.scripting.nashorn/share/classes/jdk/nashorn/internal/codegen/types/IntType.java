/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen.types;

import static jdk.internal.org.objectweb.asm.Opcodes.BIPUSH;
import static jdk.internal.org.objectweb.asm.Opcodes.I2D;
import static jdk.internal.org.objectweb.asm.Opcodes.I2L;
import static jdk.internal.org.objectweb.asm.Opcodes.IADD;
import static jdk.internal.org.objectweb.asm.Opcodes.IAND;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_1;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_2;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_3;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_4;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_5;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_M1;
import static jdk.internal.org.objectweb.asm.Opcodes.ILOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.IMUL;
import static jdk.internal.org.objectweb.asm.Opcodes.INEG;
import static jdk.internal.org.objectweb.asm.Opcodes.IOR;
import static jdk.internal.org.objectweb.asm.Opcodes.IRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ISHL;
import static jdk.internal.org.objectweb.asm.Opcodes.ISHR;
import static jdk.internal.org.objectweb.asm.Opcodes.ISTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.ISUB;
import static jdk.internal.org.objectweb.asm.Opcodes.IUSHR;
import static jdk.internal.org.objectweb.asm.Opcodes.IXOR;
import static jdk.internal.org.objectweb.asm.Opcodes.SIPUSH;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.runtime.JSType.UNDEFINED_INT;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.runtime.JSType;

/**
 * Type class: INT
 */
class IntType extends BitwiseType {
    private static final long serialVersionUID = 1L;

    private static final CompilerConstants.Call TO_STRING = staticCallNoLookup(Integer.class, "toString", String.class, int.class);
    private static final CompilerConstants.Call VALUE_OF  = staticCallNoLookup(Integer.class, "valueOf", Integer.class, int.class);

    protected IntType() {
        super("int", int.class, 2, 1);
    }

    @Override
    public Type nextWider() {
        return NUMBER;
    }

    @Override
    public Class<?> getBoxedType() {
        return Integer.class;
    }

    @Override
    public char getBytecodeStackType() {
        return 'I';
    }

    @Override
    public Type ldc(final MethodVisitor method, final Object c) {
        assert c instanceof Integer;

        final int value = ((Integer) c);

        switch (value) {
        case -1:
            method.visitInsn(ICONST_M1);
            break;
        case 0:
            method.visitInsn(ICONST_0);
            break;
        case 1:
            method.visitInsn(ICONST_1);
            break;
        case 2:
            method.visitInsn(ICONST_2);
            break;
        case 3:
            method.visitInsn(ICONST_3);
            break;
        case 4:
            method.visitInsn(ICONST_4);
            break;
        case 5:
            method.visitInsn(ICONST_5);
            break;
        default:
            if (value == (byte) value) {
                method.visitIntInsn(BIPUSH, value);
            } else if (value == (short) value) {
                method.visitIntInsn(SIPUSH, value);
            } else {
                method.visitLdcInsn(c);
            }
            break;
        }

        return Type.INT;
    }

    @Override
    public Type convert(final MethodVisitor method, final Type to) {
        if (to.isEquivalentTo(this)) {
            return to;
        }

        if (to.isNumber()) {
            method.visitInsn(I2D);
        } else if (to.isLong()) {
            method.visitInsn(I2L);
        } else if (to.isBoolean()) {
            //nop
        } else if (to.isString()) {
            invokestatic(method, TO_STRING);
        } else if (to.isObject()) {
            invokestatic(method, VALUE_OF);
        } else {
            throw new UnsupportedOperationException("Illegal conversion " + this + " -> " + to);
        }

        return to;
    }

    @Override
    public Type add(final MethodVisitor method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.visitInsn(IADD);
        } else {
            method.visitInvokeDynamicInsn("iadd", "(II)I", MATHBOOTSTRAP, programPoint);
        }
        return INT;
    }

    @Override
    public Type shr(final MethodVisitor method) {
        method.visitInsn(IUSHR);
        return INT;
    }

    @Override
    public Type sar(final MethodVisitor method) {
        method.visitInsn(ISHR);
        return INT;
    }

    @Override
    public Type shl(final MethodVisitor method) {
        method.visitInsn(ISHL);
        return INT;
    }

    @Override
    public Type and(final MethodVisitor method) {
        method.visitInsn(IAND);
        return INT;
    }

    @Override
    public Type or(final MethodVisitor method) {
        method.visitInsn(IOR);
        return INT;
    }

    @Override
    public Type xor(final MethodVisitor method) {
        method.visitInsn(IXOR);
        return INT;
    }

    @Override
    public Type load(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(ILOAD, slot);
        return INT;
    }

    @Override
    public void store(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(ISTORE, slot);
    }

    @Override
    public Type sub(final MethodVisitor method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.visitInsn(ISUB);
        } else {
            method.visitInvokeDynamicInsn("isub", "(II)I", MATHBOOTSTRAP, programPoint);
        }
        return INT;
    }

    @Override
    public Type mul(final MethodVisitor method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.visitInsn(IMUL);
        } else {
            method.visitInvokeDynamicInsn("imul", "(II)I", MATHBOOTSTRAP, programPoint);
        }
        return INT;
    }

    @Override
    public Type div(final MethodVisitor method, final int programPoint) {
        if (programPoint == INVALID_PROGRAM_POINT) {
            JSType.DIV_ZERO.invoke(method);
        } else {
            method.visitInvokeDynamicInsn("idiv", "(II)I", MATHBOOTSTRAP, programPoint);
        }
        return INT;
    }

    @Override
    public Type rem(final MethodVisitor method, final int programPoint) {
        if (programPoint == INVALID_PROGRAM_POINT) {
            JSType.REM_ZERO.invoke(method);
        } else {
            method.visitInvokeDynamicInsn("irem", "(II)I", MATHBOOTSTRAP, programPoint);
        }
        return INT;
    }

    @Override
    public Type neg(final MethodVisitor method, final int programPoint) {
        if(programPoint == INVALID_PROGRAM_POINT) {
            method.visitInsn(INEG);
        } else {
            method.visitInvokeDynamicInsn("ineg", "(I)I", MATHBOOTSTRAP, programPoint);
        }
        return INT;
    }

    @Override
    public void _return(final MethodVisitor method) {
        method.visitInsn(IRETURN);
    }

    @Override
    public Type loadUndefined(final MethodVisitor method) {
        method.visitLdcInsn(UNDEFINED_INT);
        return INT;
    }

    @Override
    public Type loadForcedInitializer(final MethodVisitor method) {
        method.visitInsn(ICONST_0);
        return INT;
    }

    @Override
    public Type cmp(final MethodVisitor method, final boolean isCmpG) {
        throw new UnsupportedOperationException("cmp" + (isCmpG ? 'g' : 'l'));
    }

    @Override
    public Type cmp(final MethodVisitor method) {
        throw new UnsupportedOperationException("cmp");
    }

}
