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
package jdk.vm.ci.meta.annotation;

import jdk.internal.vm.VMSupport;
import jdk.internal.vm.VMSupport.AnnotationDecoder;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import sun.reflect.annotation.TypeAnnotation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link AnnotationDecoder} that resolves type names to {@link JavaType} values
 * and employs {@link AnnotationValue} to represent decoded annotations and enum
 * constants respectively.
 */
public final class AnnotationValueDecoder implements AnnotationDecoder<ResolvedJavaType, AnnotationValue, TypeAnnotationValue, EnumElement, EnumArrayElement, MissingType, ElementTypeMismatch> {

    private final ResolvedJavaType accessingClass;

    public Map<ResolvedJavaType, AnnotationValue> decode(byte[] encoded) {
        List<AnnotationValue> annotationValues = VMSupport.decodeAnnotations(encoded, this);
        return annotationValues.stream().collect(Collectors.toMap(AnnotationValue::getAnnotationType, Function.identity()));
    }

    public AnnotationValueDecoder(ResolvedJavaType accessingClass) {
        this.accessingClass = accessingClass;
    }

    @Override
    public ResolvedJavaType resolveType(String name) {
        String internalName = MetaUtil.toInternalName(name);
        return UnresolvedJavaType.create(internalName).resolve(accessingClass);
    }

    @Override
    public AnnotationValue newAnnotation(ResolvedJavaType type, Map.Entry<String, Object>[] elements) {
        return new AnnotationValue(type, elements);
    }

    @Override
    public TypeAnnotationValue newTypeAnnotation(TypeAnnotation.TypeAnnotationTargetInfo targetInfo, TypeAnnotation.LocationInfo locationInfo, AnnotationValue annotation) {
        return new TypeAnnotationValue(targetInfo, locationInfo, annotation);
    }

    @Override
    public EnumElement newEnum(ResolvedJavaType enumType, String name) {
        return new EnumElement(enumType, name);
    }

    @Override
    public EnumArrayElement newEnumArray(ResolvedJavaType enumType, List<String> names) {
        return new EnumArrayElement(enumType, names);
    }

    @Override
    public MissingType newMissingType(String typeName) {
        return new MissingType(typeName);
    }

    @Override
    public ElementTypeMismatch newElementTypeMismatch(String foundType) {
        return new ElementTypeMismatch(foundType);
    }
}
