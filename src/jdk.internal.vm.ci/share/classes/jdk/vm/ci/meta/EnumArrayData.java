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

import java.util.List;

/**
 * Represents an array of enum constants within {@link AnnotationData}.
 */
public final class EnumArrayData {
    public final ResolvedJavaType enumType;
    public final List<String> names;

    /**
     * Creates an array of enum constants.
     *
     * @param type the {@linkplain Enum enum type}
     * @param name the names of the enum constants
     */
    public EnumArrayData(ResolvedJavaType enumType, List<String> names) {
        this.enumType = enumType;
        this.names = names;
    }

    @Override
    public String toString() {
        return names.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EnumArrayData that) {
            return this.enumType.equals(that.enumType) && this.names.equals(that.names);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.enumType.hashCode() ^ this.names.hashCode();
    }
}
