/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.istack.internal.tools;

import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.type.ArrayType;
import com.sun.mirror.type.ClassType;
import com.sun.mirror.type.InterfaceType;
import com.sun.mirror.type.TypeVariable;
import com.sun.mirror.type.VoidType;
import com.sun.mirror.type.WildcardType;
import com.sun.mirror.type.PrimitiveType;

/**
 * Visitor that works on APT {@link TypeMirror} and computes a value.
 *
 * <p>
 * This visitor takes a parameter 'P' so that visitor code can be made stateless.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class APTTypeVisitor<T,P> {
    public final T apply(TypeMirror type, P param) {
        if( type instanceof ArrayType)
            return onArrayType((ArrayType)type,param);
        if( type instanceof PrimitiveType)
            return onPrimitiveType((PrimitiveType)type,param);
        if (type instanceof ClassType )
            return onClassType((ClassType)type,param);
        if (type instanceof InterfaceType )
            return onInterfaceType((InterfaceType)type,param);
        if (type instanceof TypeVariable )
            return onTypeVariable((TypeVariable)type,param);
        if (type instanceof VoidType )
            return onVoidType((VoidType)type,param);
        if(type instanceof WildcardType)
            return onWildcard((WildcardType) type,param);
        assert false;
        throw new IllegalArgumentException();
    }

    protected abstract T onPrimitiveType(PrimitiveType type, P param);
    protected abstract T onArrayType(ArrayType type, P param);
    protected abstract T onClassType(ClassType type, P param);
    protected abstract T onInterfaceType(InterfaceType type, P param);
    protected abstract T onTypeVariable(TypeVariable type, P param);
    protected abstract T onVoidType(VoidType type, P param);
    protected abstract T onWildcard(WildcardType type, P param);

}
