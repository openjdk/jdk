/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.annotation;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An instance of this class is stored in an AnnotationInvocationHandler's
 * "memberValues" map to defer reification of an enum constant until the
 * dynamic proxy is queried for the enum member.
 */
public final class EnumValueArray implements java.io.Serializable, ResolvableValue {

    /**
     * The type of the enum constant.
     */
    @SuppressWarnings("rawtypes")
    public final Class<? extends Enum> enumType;

    /**
     * The names of the enum constants.
     */
    @SuppressWarnings("serial")
    public final List<String> constNames;

    /**
     * The lazily resolved array of enum constants.
     */
    transient Object[] constValues;

    @SuppressWarnings("rawtypes")
    EnumValueArray(Class<? extends Enum> enumType, List<String> constNames) {
        if (!(constNames instanceof Serializable)) {
            throw new IllegalArgumentException(constNames.getClass() + " is not serializable");
        }
        this.enumType = enumType;
        this.constNames = constNames;
    }

    /**
     * Gets the array of enum constants.
     */
    @SuppressWarnings("unchecked")
    public Object get() {
        if (constValues == null) {
            int length = constNames.size();
            constValues = (Object[]) Array.newInstance(enumType, length);
            for (int i = 0; i < length; i++) {
                try {
                    constValues[i] = Enum.valueOf(enumType, constNames.get(i));
                } catch (IllegalArgumentException e) {
                    throw new EnumConstantNotPresentException(enumType, constNames.get(i));
                }
            }
        }
        return constValues.length == 0 ? constValues : constValues.clone();
    }

    @Override
    public boolean isResolved() {
        return constValues != null;
    }

    @Override
    public String toString() {
        return constNames.toString();
    }

    @Override
    public int hashCode() {
        return constNames.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof EnumValueArray eva) {
            return this.enumType.equals(eva.enumType) &&
                   this.constNames.equals(eva.constNames);
        }
        return false;
    }

    @java.io.Serial
    private static final long serialVersionUID = 5762566221979761881L;
}
