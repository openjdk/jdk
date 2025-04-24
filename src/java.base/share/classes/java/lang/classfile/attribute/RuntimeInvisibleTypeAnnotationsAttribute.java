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
import java.lang.reflect.AnnotatedType;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#runtimeInvisibleTypeAnnotations()
 * RuntimeInvisibleTypeAnnotations} attribute (JVMS {@jvms 4.7.21}), which
 * stores type-use annotations for the annotated uses of types in this
 * structure that are visible to {@code class} file consumers but are not
 * visible to {@linkplain AnnotatedType core reflection}.  Its delivery in the
 * traversal of a {@link CodeModel} may be toggled by {@link
 * ClassFile.DebugElementsOption}.
 * <p>
 * This attribute appears on classes, fields, methods, {@code Code} attributes,
 * and record components, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in one structure.  It has a
 * data dependency on {@linkplain AttributeStability#UNSTABLE arbitrary indices}
 * in the {@code class} file format, so users must take great care to ensure
 * this attribute is still correct after a {@code class} file has been transformed.
 * <p>
 * The attribute was introduced in the Java SE Platform version 8, major version
 * {@value ClassFile#JAVA_8_VERSION}.
 *
 * @see Attributes#runtimeInvisibleTypeAnnotations()
 * @see java.compiler/javax.lang.model.type.TypeMirror
 * @see ElementType#TYPE_PARAMETER
 * @see ElementType#TYPE_USE
 * @see RetentionPolicy#CLASS
 * @jvms 4.7.21 The {@code RuntimeInvisibleTypeAnnotations} Attribute
 * @since 24
 */
@SuppressWarnings("doclint:reference")
public sealed interface RuntimeInvisibleTypeAnnotationsAttribute
        extends Attribute<RuntimeInvisibleTypeAnnotationsAttribute>,
                ClassElement, MethodElement, FieldElement, CodeElement
        permits BoundAttribute.BoundRuntimeInvisibleTypeAnnotationsAttribute,
                UnboundAttribute.UnboundRuntimeInvisibleTypeAnnotationsAttribute {

    /**
     * {@return the run-time invisible annotations on uses of types in this
     * structure}
     */
    List<TypeAnnotation> annotations();

    /**
     * {@return a {@code RuntimeInvisibleTypeAnnotations} attribute}
     *
     * @param annotations the annotations
     */
    static RuntimeInvisibleTypeAnnotationsAttribute of(List<TypeAnnotation> annotations) {
        return new UnboundAttribute.UnboundRuntimeInvisibleTypeAnnotationsAttribute(annotations);
    }

    /**
     * {@return a {@code RuntimeInvisibleTypeAnnotations} attribute}
     *
     * @param annotations the annotations
     */
    static RuntimeInvisibleTypeAnnotationsAttribute of(TypeAnnotation... annotations) {
        return of(List.of(annotations));
    }
}
