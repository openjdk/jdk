/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.classfile.attribute;

import java.lang.classfile.Attribute;
import java.lang.classfile.ClassElement;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldElement;
import java.lang.classfile.MethodElement;
import java.lang.classfile.TypeAnnotation;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@code RuntimeInvisibleTypeAnnotations} attribute (JVMS {@jvms 4.7.21}), which
 * can appear on classes, methods, fields, and code attributes. Delivered as a
 * {@link java.lang.classfile.ClassElement}, {@link java.lang.classfile.FieldElement},
 * {@link java.lang.classfile.MethodElement}, or {@link CodeElement} when traversing
 * the corresponding model type.
 * <p>
 * The attribute does not permit multiple instances in a given location.
 * Subsequent occurrence of the attribute takes precedence during the attributed
 * element build or transformation.
 * <p>
 * The attribute was introduced in the Java SE Platform version 8.
 *
 * @since 24
 */
public sealed interface RuntimeInvisibleTypeAnnotationsAttribute
        extends Attribute<RuntimeInvisibleTypeAnnotationsAttribute>,
                ClassElement, MethodElement, FieldElement, CodeElement
        permits BoundAttribute.BoundRuntimeInvisibleTypeAnnotationsAttribute,
                UnboundAttribute.UnboundRuntimeInvisibleTypeAnnotationsAttribute {

    /**
     * {@return the non-runtime-visible type annotations on parts of this class, field, or method}
     */
    List<TypeAnnotation> annotations();

    /**
     * {@return a {@code RuntimeInvisibleTypeAnnotations} attribute}
     * @param annotations the annotations
     */
    static RuntimeInvisibleTypeAnnotationsAttribute of(List<TypeAnnotation> annotations) {
        return new UnboundAttribute.UnboundRuntimeInvisibleTypeAnnotationsAttribute(annotations);
    }

    /**
     * {@return a {@code RuntimeInvisibleTypeAnnotations} attribute}
     * @param annotations the annotations
     */
    static RuntimeInvisibleTypeAnnotationsAttribute of(TypeAnnotation... annotations) {
        return of(List.of(annotations));
    }
}
