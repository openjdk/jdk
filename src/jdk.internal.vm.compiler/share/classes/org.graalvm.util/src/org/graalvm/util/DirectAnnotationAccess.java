/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.util;

//Checkstyle: allow reflection
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * Wrapper class for annotation access. The purpose of this class is to encapsulate the
 * AnnotatedElement.getAnnotation() to avoid the use of the "Checkstyle: allow direct annotation
 * access " and "Checkstyle: disallow direct annotation access" comments for situations where the
 * annotation access doesn't need to guarded, i.e., in runtime code or code that accesses annotation
 * on non-user types. See {@link GuardedAnnotationAccess} for details on these checkstyle rules.
 */
public class DirectAnnotationAccess {

    public static <T extends Annotation> boolean isAnnotationPresent(AnnotatedElement element, Class<T> annotationClass) {
        return element.getAnnotation(annotationClass) != null;
    }

    public static <T extends Annotation> T getAnnotation(AnnotatedElement element, Class<T> annotationType) {
        return element.getAnnotation(annotationType);
    }
}
