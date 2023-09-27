/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.classfile.attribute;

import jdk.internal.classfile.*;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

import java.util.List;

/**
 * Models the {@code RuntimeInvisibleAnnotations} attribute {@jvms 4.7.17}, which
 * can appear on classes, methods, and fields. Delivered as a
 * {@link jdk.internal.classfile.ClassElement}, {@link jdk.internal.classfile.FieldElement}, or
 * {@link jdk.internal.classfile.MethodElement} when traversing the corresponding model type.
 * <p>
 * The attribute does not permit multiple instances in a given location.
 * Subsequent occurrence of the attribute takes precedence during the attributed
 * element build or transformation.
 */
public sealed interface RuntimeInvisibleAnnotationsAttribute
        extends Attribute<RuntimeInvisibleAnnotationsAttribute>,
                ClassElement, MethodElement, FieldElement
        permits BoundAttribute.BoundRuntimeInvisibleAnnotationsAttribute,
                UnboundAttribute.UnboundRuntimeInvisibleAnnotationsAttribute {

    /**
     * {@return the non-runtime-visible annotations on this class, field, or method}
     */
    List<Annotation> annotations();

    /**
     * {@return a {@code RuntimeInvisibleAnnotations} attribute}
     * @param annotations the annotations
     */
    static RuntimeInvisibleAnnotationsAttribute of(List<Annotation> annotations) {
        return new UnboundAttribute.UnboundRuntimeInvisibleAnnotationsAttribute(annotations);
    }

    /**
     * {@return a {@code RuntimeInvisibleAnnotations} attribute}
     * @param annotations the annotations
     */
    static RuntimeInvisibleAnnotationsAttribute of(Annotation... annotations) {
        return of(List.of(annotations));
    }
}
