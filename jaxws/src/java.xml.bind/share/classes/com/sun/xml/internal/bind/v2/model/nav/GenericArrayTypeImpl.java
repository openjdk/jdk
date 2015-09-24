/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.nav;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

/**
 * Implementation of GenericArrayType interface for core reflection.
 */
final class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    GenericArrayTypeImpl(Type ct) {
        assert ct!=null;
        genericComponentType = ct;
    }

    /**
     * Returns  a {@code Type} object representing the component type
     * of this array.
     *
     * @return a {@code Type} object representing the component type
     *         of this array
     * @since 1.5
     */
    public Type getGenericComponentType() {
        return genericComponentType; // return cached component type
    }

    public String toString() {
        Type componentType = getGenericComponentType();
        StringBuilder sb = new StringBuilder();

        if (componentType instanceof Class)
            sb.append(((Class) componentType).getName());
        else
            sb.append(componentType.toString());
        sb.append("[]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GenericArrayType) {
            GenericArrayType that = (GenericArrayType) o;

            Type thatComponentType = that.getGenericComponentType();
            return genericComponentType.equals(thatComponentType);
        } else
            return false;
    }

    @Override
    public int hashCode() {
        return genericComponentType.hashCode();
    }
}
