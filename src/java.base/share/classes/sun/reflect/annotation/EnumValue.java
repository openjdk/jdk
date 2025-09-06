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

/**
 * An instance of this class is stored in an AnnotationInvocationHandler's
 * "memberValues" map to defer reification of an enum constant until the
 * dynamic proxy is queried for the enum member.
 */
public final class EnumValue implements java.io.Serializable, ResolvableValue {

    /**
     * The type of the enum constant.
     */
    @SuppressWarnings("rawtypes")
    public final Class<? extends Enum> enumType;

    /**
     * The name of the enum constant.
     */
    public final String constName;

    /**
     * The lazily retrived value of the enum constant.
     */
    transient Object constValue;

    @SuppressWarnings("rawtypes")
    EnumValue(Class<? extends Enum> enumType, String constName) {
        this.enumType = enumType;
        this.constName = constName;
    }

    /**
     * Gets the enum constant.
     */
    @SuppressWarnings("unchecked")
    public Object get() {
        if (constValue == null) {
            try {
                constValue = Enum.valueOf(enumType, constName);
            } catch (IllegalArgumentException e) {
                throw new EnumConstantNotPresentException(enumType, constName);
            }
        }
        return constValue;
    }

    @Override
    public boolean isResolved() {
        return constValue != null;
    }

    @Override
    public String toString() {
        return constName;
    }

    @Override
    public int hashCode() {
        return constName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof EnumValue ev) {
            return this.constName.equals(ev.constName) &&
                   this.enumType.equals(ev.enumType);
        }
        return false;
    }

    @java.io.Serial
    private static final long serialVersionUID = 5762566221979761881L;
}
