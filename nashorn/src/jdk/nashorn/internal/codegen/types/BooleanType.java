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

import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_1;
import static jdk.internal.org.objectweb.asm.Opcodes.ILOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.IRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ISTORE;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;

import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.runtime.JSType;

/**
 * The boolean type class
 */
public final class BooleanType extends Type {

    private static final CompilerConstants.Call VALUE_OF = staticCallNoLookup(Boolean.class, "valueOf", Boolean.class, boolean.class);

    /**
     * Constructor
     */
    protected BooleanType() {
        super("boolean", boolean.class, 1, 1);
    }

    @Override
    public Type nextWider() {
        return INT;
    }

    @Override
    public Class<?> getBoxedType() {
        return Boolean.class;
    }

    @Override
    public Type loadUndefined(final MethodVisitor method) {
        method.visitLdcInsn(ObjectClassGenerator.UNDEFINED_INT);
        return BOOLEAN;
    }

    @Override
    public void _return(final MethodVisitor method) {
        method.visitInsn(IRETURN);
    }

    @Override
    public Type load(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(ILOAD, slot);
        return BOOLEAN;
    }

    @Override
    public void store(final MethodVisitor method, final int slot) {
        assert slot != -1;
        method.visitVarInsn(ISTORE, slot);
    }

    @Override
    public Type ldc(final MethodVisitor method, final Object c) {
        assert c instanceof Boolean;
        method.visitInsn((Boolean) c ? ICONST_1 : ICONST_0);
        return BOOLEAN;
    }

    @Override
    public Type convert(final MethodVisitor method, final Type to) {
        if (isEquivalentTo(to)) {
            return to;
        }

        if (to.isNumber()) {
            convert(method, OBJECT);
            invokeStatic(method, JSType.TO_NUMBER);
        } else if (to.isInteger()) {
            return to; // do nothing.
        } else if (to.isLong()) {
            convert(method, OBJECT);
            invokeStatic(method, JSType.TO_UINT32);
        } else if (to.isLong()) {
            convert(method, OBJECT);
            invokeStatic(method, JSType.TO_LONG);
        } else if (to.isString()) {
            invokeStatic(method, VALUE_OF);
            invokeStatic(method, JSType.TO_PRIMITIVE_TO_STRING);
        } else if (to.isObject()) {
            invokeStatic(method, VALUE_OF);
        } else {
            assert false : "Illegal conversion " + this + " -> " + to;
        }

        return to;
    }

    @Override
    public Type add(final MethodVisitor method) {
        assert false : "unsupported operation";
        return null;
    }
}
