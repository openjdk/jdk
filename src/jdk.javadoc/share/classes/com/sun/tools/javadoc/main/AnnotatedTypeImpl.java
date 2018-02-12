/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Attribute.TypeCompound;
import com.sun.tools.javac.util.List;

/**
 * Implementation of <code>AnnotatedType</code>, which
 * represents an annotated type.
 *
 * @author Mahmood Ali
 * @since 1.8
 */
@Deprecated(since="9", forRemoval=true)
@SuppressWarnings("removal")
public class AnnotatedTypeImpl
        extends AbstractTypeImpl implements AnnotatedType {

    AnnotatedTypeImpl(DocEnv env, com.sun.tools.javac.code.Type type) {
        super(env, type);
    }

    /**
     * Get the annotations of this program element.
     * Return an empty array if there are none.
     */
    @Override
    public AnnotationDesc[] annotations() {
        List<? extends TypeCompound> tas = type.getAnnotationMirrors();
        if (tas == null ||
                tas.isEmpty()) {
            return new AnnotationDesc[0];
        }
        AnnotationDesc res[] = new AnnotationDesc[tas.length()];
        int i = 0;
        for (Attribute.Compound a : tas) {
            res[i++] = new AnnotationDescImpl(env, a);
        }
        return res;
    }

    @Override
    public com.sun.javadoc.Type underlyingType() {
        return TypeMaker.getType(env, type, true, false);
    }

    @Override
    public AnnotatedType asAnnotatedType() {
        return this;
    }

    @Override
    public String toString() {
        return typeName();
    }

    @Override
    public String typeName() {
        return this.underlyingType().typeName();
    }

    @Override
    public String qualifiedTypeName() {
        return this.underlyingType().qualifiedTypeName();
    }

    @Override
    public String simpleTypeName() {
        return this.underlyingType().simpleTypeName();
    }

    @Override
    public String dimension() {
        return this.underlyingType().dimension();
    }

    @Override
    public boolean isPrimitive() {
        return this.underlyingType().isPrimitive();
    }

    @Override
    public ClassDoc asClassDoc() {
        return this.underlyingType().asClassDoc();
    }

    @Override
    public TypeVariable asTypeVariable() {
        return this.underlyingType().asTypeVariable();
    }

    @Override
    public WildcardType asWildcardType() {
        return this.underlyingType().asWildcardType();
    }

    @Override
    public ParameterizedType asParameterizedType() {
        return this.underlyingType().asParameterizedType();
    }
}
