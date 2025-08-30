/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import sun.reflect.annotation.TypeAnnotation;

import java.util.Objects;
import java.util.stream.Collectors;

public final class TypeAnnotationValue {

    private final TypeAnnotation.TypeAnnotationTargetInfo targetInfo;
    private final TypeAnnotation.LocationInfo locationInfo;
    private final AnnotationValue annotation;

    public TypeAnnotationValue(TypeAnnotation.TypeAnnotationTargetInfo targetInfo,
                          TypeAnnotation.LocationInfo locationInfo,
                          AnnotationValue annotation) {
        this.targetInfo = targetInfo;
        this.locationInfo = locationInfo;
        this.annotation = annotation;
    }

    public AnnotationValue getAnnotation() {
        return annotation;
    }

    public TypeAnnotation.TypeAnnotationTargetInfo getTargetInfo() {
        return targetInfo;
    }

    public TypeAnnotation.LocationInfo getLocationInfo() {
        return locationInfo;
    }

    @Override
    public String toString() {
        String li = locationInfo.getLocations().stream().map(loc -> loc.tag + "@" + loc.index).collect(Collectors.joining(", "));
        return annotation + "<TargetInfo: " + targetInfo + ", LocationInfo: " + li + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TypeAnnotationValue that) {
            return this.targetInfo.equals(that.targetInfo) &&
                    this.locationInfo.equals(that.locationInfo) &&
                    this.annotation.equals(that.annotation);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetInfo, locationInfo, annotation);
    }
}
