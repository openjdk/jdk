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

import static jdk.internal.org.objectweb.asm.Opcodes.AALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.AASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ANEWARRAY;
import static jdk.internal.org.objectweb.asm.Opcodes.ARRAYLENGTH;

import jdk.internal.org.objectweb.asm.MethodVisitor;

/**
 * This is an array type, i.e. OBJECT_ARRAY, NUMBER_ARRAY.
 */
public class ArrayType extends ObjectType implements BytecodeArrayOps {

    /**
     * Constructor
     *
     * @param clazz the Java class representation of the array
     */
    protected ArrayType(final Class<?> clazz) {
        super(clazz);
    }

    /**
     * Get the element type of the array elements e.g. for OBJECT_ARRAY, this is OBJECT
     *
     * @return the element type
     */
    public Type getElementType() {
        return Type.typeFor(getTypeClass().getComponentType());
    }

    @Override
    public void astore(final MethodVisitor method) {
        method.visitInsn(AASTORE);
    }

    @Override
    public Type aload(final MethodVisitor method) {
        method.visitInsn(AALOAD);
        return getElementType();
    }

    @Override
    public Type arraylength(final MethodVisitor method) {
        method.visitInsn(ARRAYLENGTH);
        return INT;
    }

    @Override
    public Type newarray(final MethodVisitor method) {
        method.visitTypeInsn(ANEWARRAY, getElementType().getInternalName());
        return this;
    }

    @Override
    public Type newarray(final MethodVisitor method, final int dims) {
        method.visitMultiANewArrayInsn(getInternalName(), dims);
        return this;
    }

    @Override
    public Type load(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(ALOAD, slot);
        return this;
    }

    @Override
    public String toString() {
        return "array<elementType=" + getElementType().getTypeClass().getSimpleName() + '>';
    }

    @Override
    public Type convert(final MethodVisitor method, final Type to) {
        assert to.isObject();
        assert !to.isArray() || ((ArrayType)to).getElementType() == getElementType();
        return to;
    }

}
