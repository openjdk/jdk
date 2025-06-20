/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

import jdk.internal.vm.VMSupport.AnnotationDecoder;
import jdk.vm.ci.meta.AnnotationData;
import jdk.vm.ci.meta.EnumData;
import jdk.vm.ci.meta.ErrorData;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Implementation of {@link AnnotationDecoder} that resolves type names to {@link JavaType} values
 * and employs {@link AnnotationData} and {@link EnumData} to represent decoded annotations and enum
 * constants respectively.
 */
final class AnnotationDataDecoder implements AnnotationDecoder<JavaType, AnnotationData, EnumData, ErrorData> {

    static final AnnotationDataDecoder INSTANCE = new AnnotationDataDecoder();

    @Override
    public JavaType resolveType(String name) {
        String internalName = MetaUtil.toInternalName(name);
        return UnresolvedJavaType.create(internalName);
    }

    @Override
    public AnnotationData newAnnotation(JavaType type, Map.Entry<String, Object>[] elements) {
        return new AnnotationData(type, elements);
    }

    @Override
    public EnumData newEnumValue(JavaType enumType, String name) {
        return new EnumData(enumType, name);
    }

    @Override
    public ErrorData newErrorValue(String description) {
        return new ErrorData(description);
    }

    /**
     * Aggregate {@link ResolvedJavaType} inputs in an array as argument to varargs method.
     */
    static ResolvedJavaType[] asArray(ResolvedJavaType type1, ResolvedJavaType type2, ResolvedJavaType... types) {
        ResolvedJavaType[] filter = new ResolvedJavaType[2 + types.length];
        filter[0] = type1;
        filter[1] = type2;
        System.arraycopy(types, 0, filter, 2, types.length);
        return filter;
    }
}
