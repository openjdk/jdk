/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.classfile.*;
import java.lang.classfile.AttributeMapper.AttributeStability;
import java.lang.reflect.AnnotatedElement;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#runtimeInvisibleAnnotations()
 * RuntimeInvisibleAnnotations} attribute (JVMS {@jvms 4.7.17}), which stores
 * declaration annotations on this structure that are visible to {@code
 * class} file consumers but are not visible to {@linkplain AnnotatedElement
 * core reflection}.
 * <p>
 * This attribute appears on classes, fields, methods, and record components,
 * and does not permit {@linkplain AttributeMapper#allowMultiple multiple
 * instances} in one structure.  It has a data dependency on the {@linkplain
 * AttributeStability#CP_REFS constant pool}.
 * <p>
 * The attribute was introduced in the Java SE Platform version 5.0, major
 * version {@value ClassFile#JAVA_5_VERSION}.
 *
 * @see Attributes#runtimeInvisibleAnnotations()
 * @see java.compiler/javax.lang.model.element.Element
 * @see ElementType
 * @see RetentionPolicy#CLASS
 * @jvms 4.7.17 The {@code RuntimeInvisibleAnnotations} Attribute
 * @since 24
 */
@SuppressWarnings("doclint:reference")
public sealed interface RuntimeInvisibleAnnotationsAttribute
        extends Attribute<RuntimeInvisibleAnnotationsAttribute>,
                ClassElement, MethodElement, FieldElement
        permits BoundAttribute.BoundRuntimeInvisibleAnnotationsAttribute,
                UnboundAttribute.UnboundRuntimeInvisibleAnnotationsAttribute {

    /**
     * {@return the run-time invisible declaration annotations on this structure}
     */
    List<Annotation> annotations();

    /**
     * {@return a {@code RuntimeInvisibleAnnotations} attribute}
     *
     * @param annotations the annotations
     */
    static RuntimeInvisibleAnnotationsAttribute of(List<Annotation> annotations) {
        return new UnboundAttribute.UnboundRuntimeInvisibleAnnotationsAttribute(annotations);
    }

    /**
     * {@return a {@code RuntimeInvisibleAnnotations} attribute}
     *
     * @param annotations the annotations
     */
    static RuntimeInvisibleAnnotationsAttribute of(Annotation... annotations) {
        return of(List.of(annotations));
    }
}
