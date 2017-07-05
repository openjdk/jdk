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

import static jdk.internal.org.objectweb.asm.Opcodes.L2D;
import static jdk.internal.org.objectweb.asm.Opcodes.L2I;
import static jdk.internal.org.objectweb.asm.Opcodes.LADD;
import static jdk.internal.org.objectweb.asm.Opcodes.LAND;
import static jdk.internal.org.objectweb.asm.Opcodes.LCMP;
import static jdk.internal.org.objectweb.asm.Opcodes.LCONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.LCONST_1;
import static jdk.internal.org.objectweb.asm.Opcodes.LDIV;
import static jdk.internal.org.objectweb.asm.Opcodes.LLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.LMUL;
import static jdk.internal.org.objectweb.asm.Opcodes.LNEG;
import static jdk.internal.org.objectweb.asm.Opcodes.LOR;
import static jdk.internal.org.objectweb.asm.Opcodes.LREM;
import static jdk.internal.org.objectweb.asm.Opcodes.LRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.LSHL;
import static jdk.internal.org.objectweb.asm.Opcodes.LSHR;
import static jdk.internal.org.objectweb.asm.Opcodes.LSTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.LSUB;
import static jdk.internal.org.objectweb.asm.Opcodes.LUSHR;
import static jdk.internal.org.objectweb.asm.Opcodes.LXOR;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;

import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;

/**
 * Type class: LONG
 */
class LongType extends BitwiseType {

    private static final CompilerConstants.Call VALUE_OF = staticCallNoLookup(Long.class, "valueOf", Long.class, long.class);

    protected LongType(final String name) {
        super(name, long.class, 3, 2);
    }

    protected LongType() {
        this("long");
    }

    @Override
    public Type nextWider() {
        return NUMBER;
    }

    @Override
    public Class<?> getBoxedType() {
        return Long.class;
    }

    @Override
    public Type cmp(final MethodVisitor method) {
        method.visitInsn(LCMP);
        return INT;
    }

    @Override
    public Type load(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(LLOAD, slot);
        return LONG;
    }

    @Override
    public void store(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(LSTORE, slot);
    }

    @Override
    public Type ldc(final MethodVisitor method, final Object c) {
        assert c instanceof Long;

        final long value = (Long) c;

        if (value == 0L) {
            method.visitInsn(LCONST_0);
        } else if (value == 1L) {
            method.visitInsn(LCONST_1);
        } else {
            method.visitLdcInsn(c);
        }

        return Type.LONG;
    }

    @Override
    public Type convert(final MethodVisitor method, final Type to) {
        if (isEquivalentTo(to)) {
            return to;
        }

        if (to.isNumber()) {
            method.visitInsn(L2D);
        } else if (to.isInteger()) {
            method.visitInsn(L2I);
        } else if (to.isBoolean()) {
            method.visitInsn(L2I);
        } else if (to.isObject()) {
            invokeStatic(method, VALUE_OF);
        } else {
            assert false : "Illegal conversion " + this + " -> " + to;
        }

        return to;
    }

    @Override
    public Type add(final MethodVisitor method) {
        method.visitInsn(LADD);
        return LONG;
    }

    @Override
    public Type sub(final MethodVisitor method) {
        method.visitInsn(LSUB);
        return LONG;
    }

    @Override
    public Type mul(final MethodVisitor method) {
        method.visitInsn(LMUL);
        return LONG;
    }

    @Override
    public Type div(final MethodVisitor method) {
        method.visitInsn(LDIV);
        return LONG;
    }

    @Override
    public Type rem(final MethodVisitor method) {
        method.visitInsn(LREM);
        return LONG;
    }

    @Override
    public Type shr(final MethodVisitor method) {
        method.visitInsn(LUSHR);
        return LONG;
    }

    @Override
    public Type sar(final MethodVisitor method) {
        method.visitInsn(LSHR);
        return LONG;
    }

    @Override
    public Type shl(final MethodVisitor method) {
        method.visitInsn(LSHL);
        return LONG;
    }

    @Override
    public Type and(final MethodVisitor method) {
        method.visitInsn(LAND);
        return LONG;
    }

    @Override
    public Type or(final MethodVisitor method) {
        method.visitInsn(LOR);
        return LONG;
    }

    @Override
    public Type xor(final MethodVisitor method) {
        method.visitInsn(LXOR);
        return LONG;
    }

    @Override
    public Type neg(final MethodVisitor method) {
        method.visitInsn(LNEG);
        return LONG;
    }

    @Override
    public void _return(final MethodVisitor method) {
        method.visitInsn(LRETURN);
    }

    @Override
    public Type loadUndefined(final MethodVisitor method) {
        method.visitLdcInsn(ObjectClassGenerator.UNDEFINED_LONG);
        return LONG;
    }

    @Override
    public Type cmp(final MethodVisitor method, final boolean isCmpG) {
        return cmp(method);
    }
}
