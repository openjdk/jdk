/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import com.sun.javadoc.*;

import com.sun.tools.javac.code.Type;


/**
 * Abstract implementation of <code>Type</code>, with useful
 * defaults for the methods in <code>Type</code> (and a couple from
 * <code>ProgramElementDoc</code>).
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
abstract class AbstractTypeImpl implements com.sun.javadoc.Type {

    protected final DocEnv env;
    protected final Type type;

    protected AbstractTypeImpl(DocEnv env, Type type) {
        this.env = env;
        this.type = type;
    }

    public String typeName() {
        return type.tsym.name.toString();
    }

    public String qualifiedTypeName() {
        return type.tsym.getQualifiedName().toString();
    }

    public com.sun.javadoc.Type getElementType() {
        return null;
    }

    public String simpleTypeName() {
        return type.tsym.name.toString();
    }

    public String name() {
        return typeName();
    }

    public String qualifiedName() {
        return qualifiedTypeName();
    }

    public String toString() {
        return qualifiedTypeName();
    }

    public String dimension() {
        return "";
    }

    public boolean isPrimitive() {
        return false;
    }

    public ClassDoc asClassDoc() {
        return null;
    }

    public TypeVariable asTypeVariable() {
        return null;
    }

    public WildcardType asWildcardType() {
        return null;
    }

    public ParameterizedType asParameterizedType() {
        return null;
    }

    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return null;
    }

    public AnnotatedType asAnnotatedType() {
        return null;
    }
}
