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

import static jdk.internal.org.objectweb.asm.Opcodes.ACONST_NULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ARETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.CHECKCAST;
import static jdk.internal.org.objectweb.asm.Opcodes.GETSTATIC;
import static jdk.nashorn.internal.codegen.CompilerConstants.className;
import static jdk.nashorn.internal.codegen.CompilerConstants.typeDescriptor;

import java.lang.invoke.MethodHandle;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * Type class: OBJECT This is the object type, used for all object types. It can
 * contain a class that is a more specialized object
 */
class ObjectType extends Type {

    protected ObjectType() {
        this(Object.class);
    }

    protected ObjectType(final Class<?> clazz) {
        super("object",
                clazz,
                clazz == Object.class ? Type.MAX_WEIGHT : 10,
                1);
    }

    @Override
    public String toString() {
        return "object" + (getTypeClass() != Object.class ? "<type=" + getTypeClass().getSimpleName() + '>' : "");
    }

    @Override
    public Type add(final MethodVisitor method) {
        invokeStatic(method, ScriptRuntime.ADD);
        return Type.OBJECT;
    }

    @Override
    public Type load(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(ALOAD, slot);
        return Type.OBJECT;
    }

    @Override
    public void store(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(ASTORE, slot);
    }

    @Override
    public Type loadUndefined(final MethodVisitor method) {
        method.visitFieldInsn(GETSTATIC, className(ScriptRuntime.class), "UNDEFINED", typeDescriptor(Undefined.class));
        return OBJECT;
    }

    @Override
    public Type loadEmpty(final MethodVisitor method) {
        method.visitFieldInsn(GETSTATIC, className(ScriptRuntime.class), "EMPTY", typeDescriptor(Undefined.class));
        return OBJECT;
    }

    @Override
    public Type ldc(final MethodVisitor method, final Object c) {
        if (c == null) {
            method.visitInsn(ACONST_NULL);
        } else if (c instanceof Undefined) {
            return loadUndefined(method);
        } else if (c instanceof String) {
            method.visitLdcInsn(c);
            return STRING;
        } else if (c instanceof Handle) {
            method.visitLdcInsn(c);
            return Type.typeFor(MethodHandle.class);
        } else {
            assert false : "implementation missing for class " + c.getClass() + " value=" + c;
        }

        return OBJECT;
    }

    @Override
    public Type convert(final MethodVisitor method, final Type to) {
        final boolean toString = to.isString();
        if (!toString) {
            if (to.isArray()) {
                final Type elemType = ((ArrayType)to).getElementType();

                //note that if this an array, things won't work. see {link @ArrayType} subclass.
                //we also have the unpleasant case of NativeArray which looks like an Object, but is
                //an array to the type system. This is treated specially at the known load points

                if (elemType.isString()) {
                    method.visitTypeInsn(CHECKCAST, CompilerConstants.className(String[].class));
                } else if (elemType.isNumber()) {
                    method.visitTypeInsn(CHECKCAST, CompilerConstants.className(double[].class));
                } else if (elemType.isLong()) {
                    method.visitTypeInsn(CHECKCAST, CompilerConstants.className(long[].class));
                } else if (elemType.isInteger()) {
                    method.visitTypeInsn(CHECKCAST, CompilerConstants.className(int[].class));
                } else {
                    method.visitTypeInsn(CHECKCAST, CompilerConstants.className(Object[].class));
                }
                return to;
            } else if (to.isObject()) {
                return to;
            }
        } else if (isString()) {
            return to;
        }

        if (to.isInteger()) {
            invokeStatic(method, JSType.TO_INT32);
        } else if (to.isNumber()) {
            invokeStatic(method, JSType.TO_NUMBER);
        } else if (to.isLong()) {
            invokeStatic(method, JSType.TO_INT64);
        } else if (to.isBoolean()) {
            invokeStatic(method, JSType.TO_BOOLEAN);
        } else if (to.isString()) {
            invokeStatic(method, JSType.TO_PRIMITIVE_TO_STRING);
        } else {
            assert false : "Illegal conversion " + this + " -> " + to + " " + isString() + " " + toString;
        }

        return to;
    }

    @Override
    public void _return(final MethodVisitor method) {
        method.visitInsn(ARETURN);
    }
}
