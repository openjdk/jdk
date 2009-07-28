/*
 * Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.apt.mirror.type;


import java.util.Collection;

import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.*;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.ClassSymbol;


/**
 * Implementation of DeclaredType
 */
@SuppressWarnings("deprecation")
abstract class DeclaredTypeImpl extends TypeMirrorImpl
                                implements DeclaredType {

    protected Type.ClassType type;


    protected DeclaredTypeImpl(AptEnv env, Type.ClassType type) {
        super(env, type);
        this.type = type;
    }


    /**
     * Returns a string representation of this declared type.
     * This includes the type's name and any actual type arguments.
     * Type names are qualified.
     */
    public String toString() {
        return toString(env, type);
    }

    /**
     * {@inheritDoc}
     */
    public TypeDeclaration getDeclaration() {
        return env.declMaker.getTypeDeclaration((ClassSymbol) type.tsym);
    }

    /**
     * {@inheritDoc}
     */
    public DeclaredType getContainingType() {
        if (type.getEnclosingType().tag == TypeTags.CLASS) {
            // This is the type of an inner class.
            return (DeclaredType) env.typeMaker.getType(type.getEnclosingType());
        }
        ClassSymbol enclosing = type.tsym.owner.enclClass();
        if (enclosing != null) {
            // Nested but not inner.  Return the raw type of the enclosing
            // class or interface.
            // See java.lang.reflect.ParameterizedType.getOwnerType().
            return (DeclaredType) env.typeMaker.getType(
                                        env.jctypes.erasure(enclosing.type));
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<TypeMirror> getActualTypeArguments() {
        return env.typeMaker.getTypes(type.getTypeArguments());
    }

    /**
     * {@inheritDoc}
     */
    public Collection<InterfaceType> getSuperinterfaces() {
        return env.typeMaker.getTypes(env.jctypes.interfaces(type),
                                      InterfaceType.class);
    }


    /**
     * Returns a string representation of this declared type.
     * See {@link #toString()} for details.
     */
    static String toString(AptEnv env, Type.ClassType c) {
        return c.toString();
    }
}
