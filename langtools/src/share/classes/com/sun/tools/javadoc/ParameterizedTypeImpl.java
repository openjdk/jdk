/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;


import com.sun.javadoc.*;

import static com.sun.javadoc.LanguageVersion.*;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.util.List;

import static com.sun.tools.javac.code.TypeTags.*;


/**
 * Implementation of <code>ParameterizedType</code>, which
 * represents an invocation of a generic class or interface.
 *
 * @author Scott Seligman
 * @since 1.5
 */
public class ParameterizedTypeImpl
        extends AbstractTypeImpl implements ParameterizedType {

    ParameterizedTypeImpl(DocEnv env, Type type) {
        super(env, type);
    }

    /**
     * Return the generic class or interface that declared this type.
     */
    public ClassDoc asClassDoc() {
        return env.getClassDoc((ClassSymbol)type.tsym);
    }

    /**
     * Return the actual type arguments of this type.
     */
    public com.sun.javadoc.Type[] typeArguments() {
        return TypeMaker.getTypes(env, type.getTypeArguments());
    }

    /**
     * Return the class type that is a direct supertype of this one.
     * Return null if this is an interface type.
     */
    public com.sun.javadoc.Type superclassType() {
        if (asClassDoc().isInterface()) {
            return null;
        }
        Type sup = env.types.supertype(type);
        return TypeMaker.getType(env,
                                 (sup != type) ? sup : env.syms.objectType);
    }

    /**
     * Return the interface types directly implemented by or extended by this
     * parameterized type.
     * Return an empty array if there are no interfaces.
     */
    public com.sun.javadoc.Type[] interfaceTypes() {
        return TypeMaker.getTypes(env, env.types.interfaces(type));
    }

    /**
     * Return the type that contains this type as a member.
     * Return null is this is a top-level type.
     */
    public com.sun.javadoc.Type containingType() {
        if (type.getEnclosingType().tag == CLASS) {
            // This is the type of an inner class.
            return TypeMaker.getType(env, type.getEnclosingType());
        }
        ClassSymbol enclosing = type.tsym.owner.enclClass();
        if (enclosing != null) {
            // Nested but not inner.  Return the ClassDoc of the enclosing
            // class or interface.
            // See java.lang.reflect.ParameterizedType.getOwnerType().
            return env.getClassDoc(enclosing);
        }
        return null;
    }


    // Asking for the "name" of a parameterized type doesn't exactly make
    // sense.  It's a type expression.  Return the name of its generic
    // type.
    public String typeName() {
        return TypeMaker.getTypeName(type, false);
    }

    public ParameterizedType asParameterizedType() {
        return this;
    }

    public String toString() {
        return parameterizedTypeToString(env, (ClassType)type, true);
    }

    static String parameterizedTypeToString(DocEnv env, ClassType cl,
                                            boolean full) {
        if (env.legacyDoclet) {
            return TypeMaker.getTypeName(cl, full);
        }
        StringBuffer s = new StringBuffer();
        if (cl.getEnclosingType().tag != CLASS) {               // if not an inner class...
            s.append(TypeMaker.getTypeName(cl, full));
        } else {
            ClassType encl = (ClassType)cl.getEnclosingType();
            s.append(parameterizedTypeToString(env, encl, full))
             .append('.')
             .append(cl.tsym.name.toString());
        }
        s.append(TypeMaker.typeArgumentsString(env, cl, full));
        return s.toString();
    }
}
