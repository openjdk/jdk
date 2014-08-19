/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
class PrimitiveType implements com.sun.javadoc.Type {

    private final String name;

    static final PrimitiveType voidType = new PrimitiveType("void");
    static final PrimitiveType booleanType = new PrimitiveType("boolean");
    static final PrimitiveType byteType = new PrimitiveType("byte");
    static final PrimitiveType charType = new PrimitiveType("char");
    static final PrimitiveType shortType = new PrimitiveType("short");
    static final PrimitiveType intType = new PrimitiveType("int");
    static final PrimitiveType longType = new PrimitiveType("long");
    static final PrimitiveType floatType = new PrimitiveType("float");
    static final PrimitiveType doubleType = new PrimitiveType("double");

    // error type, should never actually be used
    static final PrimitiveType errorType = new PrimitiveType("");

    PrimitiveType(String name) {
        this.name = name;
    }

    /**
     * Return unqualified name of type excluding any dimension information.
     * <p>
     * For example, a two dimensional array of String returns 'String'.
     */
    public String typeName() {
        return name;
    }

    public com.sun.javadoc.Type getElementType() {
        return null;
    }

    /**
     * Return qualified name of type excluding any dimension information.
     *<p>
     * For example, a two dimensional array of String
     * returns 'java.lang.String'.
     */
    public String qualifiedTypeName() {
        return name;
    }

    /**
     * Return the simple name of this type.
     */
    public String simpleTypeName() {
        return name;
    }

    /**
     * Return the type's dimension information, as a string.
     * <p>
     * For example, a two dimensional array of String returns '[][]'.
     */
    public String dimension() {
        return "";
    }

    /**
     * Return this type as a class.  Array dimensions are ignored.
     *
     * @return a ClassDocImpl if the type is a Class.
     * Return null if it is a primitive type..
     */
    public ClassDoc asClassDoc() {
        return null;
    }

    /**
     * Return null, as this is not an annotation type.
     */
    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return null;
    }

    /**
     * Return null, as this is not an instantiation.
     */
    public ParameterizedType asParameterizedType() {
        return null;
    }

    /**
     * Return null, as this is not a type variable.
     */
    public TypeVariable asTypeVariable() {
        return null;
    }

    /**
     * Return null, as this is not a wildcard type.
     */
    public WildcardType asWildcardType() {
        return null;
    }

    /**
     * Return null, as this is not an annotated type.
     */
    public AnnotatedType asAnnotatedType() {
        return null;
    }

    /**
     * Returns a string representation of the type.
     *
     * Return name of type including any dimension information.
     * <p>
     * For example, a two dimensional array of String returns
     * <code>String[][]</code>.
     *
     * @return name of type including any dimension information.
     */
    public String toString() {
        return qualifiedTypeName();
    }

    /**
     * Return true if this is a primitive type.
     */
    public boolean isPrimitive() {
        return true;
    }
}
