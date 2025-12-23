/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import jdk.vm.ci.meta.AnnotationData;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class HotSpotResolvedJavaType extends HotSpotJavaType implements ResolvedJavaType {

    HotSpotResolvedObjectType arrayOfType;

    protected HotSpotResolvedJavaType(String name) {
        super(name);
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    /**
     * Gets the runtime representation of the {@link Class} object of this type.
     */
    public abstract JavaConstant getJavaMirror();

    /**
     * Gets the array type of this type without caching the result.
     */
    protected abstract HotSpotResolvedObjectType getArrayType();

    @Override
    public HotSpotResolvedObjectType getArrayClass() {
        if (arrayOfType == null) {
            arrayOfType = getArrayType();
        }
        return arrayOfType;
    }

    /**
     * Checks whether this type is currently being initialized. If a type is being initialized it
     * implies that it was {@link #isLinked() linked} and that the static initializer is currently
     * being run.
     *
     * @return {@code true} if this type is being initialized
     */
    protected abstract boolean isBeingInitialized();

    static void checkIsAnnotation(ResolvedJavaType type) {
        if (!type.isAnnotation()) {
            throw new IllegalArgumentException(type.toJavaName() + " is not an annotation interface");
        }
    }

    static void checkAreAnnotations(ResolvedJavaType... types) {
        for (ResolvedJavaType type : types) {
            checkIsAnnotation(type);
        }
    }

    static AnnotationData getFirstAnnotationOrNull(List<AnnotationData> list) {
        return list.isEmpty() ? null : list.get(0);
    }
}
