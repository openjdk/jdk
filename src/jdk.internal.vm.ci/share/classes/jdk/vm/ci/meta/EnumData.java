/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.meta;

/**
 * Represents an enum constant within {@link AnnotationData}.
 */
public final class EnumData {
    private final JavaType type;
    private final String name;

    /**
     * Creates an enum constant.
     *
     * @param type the {@linkplain Enum enum type}
     * @param name the {@linkplain Enum#name() name} of the enum
     */
    public EnumData(JavaType type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * Gets the {@linkplain Enum enum type}.
     */
    public JavaType getEnumType() {
        return type;
    }

    /**
     * Gets the {@linkplain Enum#name() name} of the enum.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof EnumData) {
            EnumData that = (EnumData) obj;
            return this.type.equals(that.type) && this.name.equals(that.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.type.hashCode() ^ this.name.hashCode();
    }
}
