/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaType;

/**
 * A implementation of {@link JavaField} for an unresolved field.
 */
class HotSpotUnresolvedField implements JavaField {

    private final String name;
    private final JavaType holder;
    private final JavaType type;

    HotSpotUnresolvedField(JavaType holder, String name, JavaType type) {
        this.name = name;
        this.type = type;
        this.holder = holder;
    }

    public String getName() {
        return name;
    }

    public JavaType getType() {
        return type;
    }

    public JavaType getDeclaringClass() {
        return holder;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof HotSpotUnresolvedField)) {
            return false;
        }
        HotSpotUnresolvedField that = (HotSpotUnresolvedField) obj;
        return this.holder.equals(that.holder) && this.name.equals(that.name) && this.type.equals(that.type);
    }

    /**
     * Converts this compiler interface field to a string.
     */
    @Override
    public String toString() {
        return format("HotSpotField<%H.%n %t, unresolved>");
    }
}
