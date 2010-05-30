/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.List;

import static com.sun.tools.javac.code.TypeTags.*;


public class TypeMaker {

    public static com.sun.javadoc.Type getType(DocEnv env, Type t) {
        return getType(env, t, true);
    }

    /**
     * @param errToClassDoc  if true, ERROR type results in a ClassDoc;
     *          false preserves legacy behavior
     */
    @SuppressWarnings("fallthrough")
    public static com.sun.javadoc.Type getType(DocEnv env, Type t,
                                               boolean errToClassDoc) {
        if (env.legacyDoclet) {
            t = env.types.erasure(t);
        }
        switch (t.tag) {
        case CLASS:
            if (ClassDocImpl.isGeneric((ClassSymbol)t.tsym)) {
                return env.getParameterizedType((ClassType)t);
            } else {
                return env.getClassDoc((ClassSymbol)t.tsym);
            }
        case WILDCARD:
            Type.WildcardType a = (Type.WildcardType)t;
            return new WildcardTypeImpl(env, a);
        case TYPEVAR: return new TypeVariableImpl(env, (TypeVar)t);
        case ARRAY: return new ArrayTypeImpl(env, t);
        case BYTE: return PrimitiveType.byteType;
        case CHAR: return PrimitiveType.charType;
        case SHORT: return PrimitiveType.shortType;
        case INT: return PrimitiveType.intType;
        case LONG: return PrimitiveType.longType;
        case FLOAT: return PrimitiveType.floatType;
        case DOUBLE: return PrimitiveType.doubleType;
        case BOOLEAN: return PrimitiveType.booleanType;
        case VOID: return PrimitiveType.voidType;
        case ERROR:
            if (errToClassDoc)
                return env.getClassDoc((ClassSymbol)t.tsym);
            // FALLTHRU
        default:
            return new PrimitiveType(t.tsym.getQualifiedName().toString());
        }
    }

    /**
     * Convert a list of javac types into an array of javadoc types.
     */
    public static com.sun.javadoc.Type[] getTypes(DocEnv env, List<Type> ts) {
        return getTypes(env, ts, new com.sun.javadoc.Type[ts.length()]);
    }

    /**
     * Like the above version, but use and return the array given.
     */
    public static com.sun.javadoc.Type[] getTypes(DocEnv env, List<Type> ts,
                                                  com.sun.javadoc.Type res[]) {
        int i = 0;
        for (Type t : ts) {
            res[i++] = getType(env, t);
        }
        return res;
    }

    public static String getTypeName(Type t, boolean full) {
        switch (t.tag) {
        case ARRAY:
            StringBuffer dimension = new StringBuffer();
            while (t.tag == ARRAY) {
                dimension = dimension.append("[]");
                t = ((ArrayType)t).elemtype;
            }
            return getTypeName(t, full) + dimension;
        case CLASS:
            return ClassDocImpl.getClassName((ClassSymbol)t.tsym, full);
        default:
            return t.tsym.getQualifiedName().toString();
        }
    }

    /**
     * Return the string representation of a type use.  Bounds of type
     * variables are not included; bounds of wildcard types are.
     * Class names are qualified if "full" is true.
     */
    static String getTypeString(DocEnv env, Type t, boolean full) {
        switch (t.tag) {
        case ARRAY:
            StringBuffer dimension = new StringBuffer();
            while (t.tag == ARRAY) {
                dimension = dimension.append("[]");
                t = env.types.elemtype(t);
            }
            return getTypeString(env, t, full) + dimension;
        case CLASS:
            return ParameterizedTypeImpl.
                        parameterizedTypeToString(env, (ClassType)t, full);
        case WILDCARD:
            Type.WildcardType a = (Type.WildcardType)t;
            return WildcardTypeImpl.wildcardTypeToString(env, a, full);
        default:
            return t.tsym.getQualifiedName().toString();
        }
    }

    /**
     * Return the formal type parameters of a class or method as an
     * angle-bracketed string.  Each parameter is a type variable with
     * optional bounds.  Class names are qualified if "full" is true.
     * Return "" if there are no type parameters or we're hiding generics.
     */
    static String typeParametersString(DocEnv env, Symbol sym, boolean full) {
        if (env.legacyDoclet || sym.type.getTypeArguments().isEmpty()) {
            return "";
        }
        StringBuffer s = new StringBuffer();
        for (Type t : sym.type.getTypeArguments()) {
            s.append(s.length() == 0 ? "<" : ", ");
            s.append(TypeVariableImpl.typeVarToString(env, (TypeVar)t, full));
        }
        s.append(">");
        return s.toString();
    }

    /**
     * Return the actual type arguments of a parameterized type as an
     * angle-bracketed string.  Class name are qualified if "full" is true.
     * Return "" if there are no type arguments or we're hiding generics.
     */
    static String typeArgumentsString(DocEnv env, ClassType cl, boolean full) {
        if (env.legacyDoclet || cl.getTypeArguments().isEmpty()) {
            return "";
        }
        StringBuffer s = new StringBuffer();
        for (Type t : cl.getTypeArguments()) {
            s.append(s.length() == 0 ? "<" : ", ");
            s.append(getTypeString(env, t, full));
        }
        s.append(">");
        return s.toString();
    }


    private static class ArrayTypeImpl implements com.sun.javadoc.Type {

        Type arrayType;

        DocEnv env;

        ArrayTypeImpl(DocEnv env, Type arrayType) {
            this.env = env;
            this.arrayType = arrayType;
        }

        private com.sun.javadoc.Type skipArraysCache = null;

        private com.sun.javadoc.Type skipArrays() {
            if (skipArraysCache == null) {
                Type t;
                for (t = arrayType; t.tag == ARRAY; t = env.types.elemtype(t)) { }
                skipArraysCache = TypeMaker.getType(env, t);
            }
            return skipArraysCache;
        }

        /**
         * Return the type's dimension information, as a string.
         * <p>
         * For example, a two dimensional array of String returns '[][]'.
         */
        public String dimension() {
            StringBuffer dimension = new StringBuffer();
            for (Type t = arrayType; t.tag == ARRAY; t = env.types.elemtype(t)) {
                dimension = dimension.append("[]");
            }
            return dimension.toString();
        }

        /**
         * Return unqualified name of type excluding any dimension information.
         * <p>
         * For example, a two dimensional array of String returns 'String'.
         */
        public String typeName() {
            return skipArrays().typeName();
        }

        /**
         * Return qualified name of type excluding any dimension information.
         *<p>
         * For example, a two dimensional array of String
         * returns 'java.lang.String'.
         */
        public String qualifiedTypeName() {
            return skipArrays().qualifiedTypeName();
        }

        /**
         * Return the simple name of this type excluding any dimension information.
         */
        public String simpleTypeName() {
            return skipArrays().simpleTypeName();
        }

        /**
         * Return this type as a class.  Array dimensions are ignored.
         *
         * @return a ClassDocImpl if the type is a Class.
         * Return null if it is a primitive type..
         */
        public ClassDoc asClassDoc() {
            return skipArrays().asClassDoc();
        }

        /**
         * Return this type as a <code>ParameterizedType</code> if it
         * represents a parameterized type.  Array dimensions are ignored.
         */
        public ParameterizedType asParameterizedType() {
            return skipArrays().asParameterizedType();
        }

        /**
         * Return this type as a <code>TypeVariable</code> if it represents
         * a type variable.  Array dimensions are ignored.
         */
        public TypeVariable asTypeVariable() {
            return skipArrays().asTypeVariable();
        }

        /**
         * Return null, as there are no arrays of wildcard types.
         */
        public WildcardType asWildcardType() {
            return null;
        }

        /**
         * Return this type as an <code>AnnotationTypeDoc</code> if it
         * represents an annotation type.  Array dimensions are ignored.
         */
        public AnnotationTypeDoc asAnnotationTypeDoc() {
            return skipArrays().asAnnotationTypeDoc();
        }

        /**
         * Return true if this is an array of a primitive type.
         */
        public boolean isPrimitive() {
            return skipArrays().isPrimitive();
        }

        /**
         * Return a string representation of the type.
         *
         * Return name of type including any dimension information.
         * <p>
         * For example, a two dimensional array of String returns
         * <code>String[][]</code>.
         *
         * @return name of type including any dimension information.
         */
        public String toString() {
            return qualifiedTypeName() + dimension();
        }
    }
}
