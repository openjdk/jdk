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
package jdk.vm.ci.hotspot;

import jdk.internal.vm.VMSupport.AnnotationDecoder;
import jdk.vm.ci.meta.AnnotationData;
import jdk.vm.ci.meta.EnumData;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Implementation of {@link AnnotationDecoder} that resolves type names to {@link JavaType} values
 * and employs {@link AnnotationData} and {@link EnumData} to represent decoded annotations and enum
 * constants respectively.
 */
class AnnotationDataDecoder implements AnnotationDecoder<JavaType, AnnotationData, EnumData, StringBuilder> {

    public static final AnnotationData[] NO_ANNOTATION_DATA = {};

    @Override
    public JavaType resolveType(String name) {
        String internalName = MetaUtil.toInternalName(name);
        return UnresolvedJavaType.create(internalName);
    }

    @Override
    public AnnotationData newAnnotation(JavaType type, String[] names, Object[] values) {
        return new AnnotationData(type, names, values);
    }

    @Override
    public EnumData newEnumValue(JavaType enumType, String name) {
        return new EnumData(enumType, name);
    }

    @Override
    public JavaType[] newClassArray(int length) {
        return new JavaType[length];
    }

    @Override
    public AnnotationData[] newAnnotationArray(int length) {
        return new AnnotationData[length];
    }

    @Override
    public EnumData[] newEnumValues(int length) {
        return new EnumData[length];
    }

    @Override
    public StringBuilder newErrorValue(String description) {
        return new StringBuilder(description);
    }
}
